# Design

## Context

See proposal.md for motivation and the specs for required behavior. What exists today:

- **Terrain data** (`lib-core` `assets/terrain/TerrainData`):
  - a square height grid (resolution ≤ 255) over `size` world units;
  - `heightAt(x, z)` gives a bilinear terrain-local height, and the normals come from central differences.
  - `TerrainLoader` reads it off the GL thread; splat textures are dependencies.
- **Asset pipeline** (`lib-core` `assets/loading`):
  - `AssetLoader` / `CompositeAssetLoader` / `AssetStorage`. A loader has an off-thread `prepare` step (no GL), a GL
    `build` step, and declared dependencies on other assets.
  - `MetaType` lists the asset types, and `AssetMetaBinder` binds `additional` per type.
- **Scene read model** (`lib-core-editor` `scene/SceneContent`): it turns scene JSON into `AssetPlacement`s, which
  `PlacedAssets` / `SceneModels` / `SceneTerrains` draw in the plugin.
- **Drawing.** Every model entity gets its own `ModelInstance` and is drawn through `lib-gdx-model`'s `ModelBatch` with
  `DefaultShader` / `PbrShader`. Shadows are drawn through `ModelDepthShader`, and fog through `FogShaderProvider`.
  - `lib-gdx-model`'s `Mesh` already has `enableInstancedRendering` and an `InstanceBufferObject`, but no shader uses
    instance attributes.
  - The IDE context has GL 3 (HDR skies require `Gdx.gl30`).
- **Interaction** (`lib-core-editor` `pick/SceneInteraction`): presses, drags and releases form `Gesture`s (idle,
  gizmo dragging, cancelled), and Esc cancels.
  - `ScenePicker.terrainDistance` already intersects a ray with a `TerrainTarget` (entity id, `TerrainData`, world
    matrix).
  - `SceneViewPanel` forwards Swing mouse events, and a Swing timer drives frames on the AWT thread.
- **Undoable file writes** (`plugin-abyssus` `assetfiles`):
  - An `AssetTransaction` stages `FileChange(path, before, after)` snapshots, plus expected files, created folders and
    an Undo guard.
  - `AssetTransactionEngine` verifies, applies and rolls back. `AssetFileCommand` runs it as one command and joins the
    undo stacks of the `affected` files.
  - New Terrain, terrain Apply and FlightGear import use it. `AssetReferenceGuard` refuses Undo of a creation while a
    scene names the asset.
- **Text-preserving JSON edits**: `SceneJson` and `DocumentTextEditor` (`lib-core-editor` `document`), used by
  `editSceneJson` and by asset meta edits.

## Goals / Non-Goals

**Goals:**
- **Painting stays interactive**, with grass at hundreds of thousands of copies and a 1600-unit terrain.
- **One generator decides where copies go.** The full bake, a preview and a partial re-scatter under the brush give
  byte-equal results, so the bake is a cache that can be checked and rebuilt.
- **The drawing and the file readers live in `lib-core`**, so `add-foliage-runtime` reuses them unchanged.
- **The undo history stays small**, even though `foliage.data` can be tens of megabytes.

**Non-Goals:**
- Runtime/game drawing, physics, ray tracing, LOD and wind (see the proposal's out-of-scope list).
- A general-purpose painting framework. The brush paints foliage masks only.

## Decisions

### 1. The settings live in `meta.json`; there is no separate recipe file

Layer settings go in `additional` of the native foliage `meta.json`.

- **Why not a recipe file beside the data, as terrains have?** Terrains keep their generation settings in a separate
  recipe file only because their metadata predates Abyssus generation. Foliage is new, so its settings can sit in its
  own native document.
- **What this buys:**
  - The settings get document validation, unknown-member preservation and text-preserving edits for free.
  - Unused-asset marking can read the model and terrain references from the meta.

References are folder names (`terrain`, `models[].asset`), not uuids. Scenes already name assets by folder, and the
fixture's `tree` has no `uuid`.

### 2. Generation: a jittered grid with an independent random stream per cell

**The grid.** For a layer of density `d`, the terrain square is cut into cells of side `c = 1/sqrt(d)`. Each cell
`(i, j)` has one candidate at `((i + 0.1 + 0.8u) c, (j + 0.1 + 0.8v) c)`. `u` and `v` come from a SplitMix64 stream
seeded by a hash of `(layer seed, layer id, i, j)`.

**Keeping a candidate.** The candidate is kept when a further draw is below the keep factor
`p = mask(x, z) / 255 × [height in range] × [slope ≤ max]`. The mask is sampled bilinearly, and the height and slope come
from `TerrainData`. Further draws pick the model by weight, the yaw in [0, 2π) and the scale in the scale range.

**Why this design:**
- **Cells are independent.** Re-scattering any set of chunks gives exactly the bytes of a full bake, as long as the
  copies of a chunk are written in cell row-major order. That makes the brush preview equal to the bake, and makes a
  partial re-scatter testable against a full bake.
- **The minimum spacing is guaranteed.** Jitter stays inside 80% of the cell, so neighbours are always at least `0.2 c`
  apart. That is the "one fifth" in the spec.
- **No neighbour search across chunk borders**, which Poisson-disk sampling would need.

**Alternatives considered:**
- *Per-chunk Poisson disk sampling*: it bunches up or leaves gaps at chunk borders, or needs neighbour-aware ordering,
  which breaks partial re-scatter.
- *White noise*: it clumps, and has no spacing guarantee.

**Guard.** A layer whose candidate count (`area × d`) exceeds 16,000,000 is refused before generating, with the same
message as the copy limit. This bounds generation time on dense layers whose masks are mostly empty.

The generator lives in `lib-core-editor` `foliage/FoliageScatter`. It is pure Kotlin over `TerrainData` and byte masks,
and runs on a pooled thread.

### 3. `foliage.data` and the fingerprint

The bake is big-endian, like `terrain.data`:

```
"ABFO" u16 version=1  u8[32] fingerprint  f32 chunkSize  u16 layerCount
per layer: i32 id  u16 modelCount  u16 chunksX  u16 chunksZ
  per chunk (z-major): i32 count, then count x { u16 model, f32 x, f32 z, f32 yaw, f32 scale }
```

- **What a copy stores.** Copies are terrain-local, 18 bytes each, so 1,000,000 copies take about 18 MB. Height and tilt
  are not stored: they are computed from the terrain when drawing, which is how copies stay on the surface after a
  terrain regeneration.
- **Chunk size.** It is `max(32, size / 64)` world units, which bounds the chunk count at 64 × 64.
- **The fingerprint.** It is the SHA-256 of:
  - the generator id;
  - the terrain `size` and its height bytes;
  - `maskResolution`;
  - a canonical encoding of each layer's generation fields (kind, models and weights, density, scale, seed, height
    and slope limits);
  - each mask's bytes.

  `alignToNormal` and `drawDistance` are left out: they act only at draw time, so editing them does not make the bake
  stale.
- **Versions.** The reader rejects other magic numbers and versions as "stale" rather than as errors.

Readers and writers live in `lib-core` `assets/foliage` (`FoliageDataFile`, `FoliageMaskFile`). The mask is
`maskResolution²` bytes in z-major order; a missing file means 255 everywhere.

### 4. Loading through the asset pipeline

- **The loader.** `MetaType.FOLIAGE` binds `FoliageMeta`. `FoliageLoader.prepare` runs off the GL thread: it reads
  and validates the meta, the masks and `foliage.data`, and reports whether the bake matches the fingerprint. It
  declares the terrain and every layer model as dependencies.
- **The scene read model.** `SceneContent` gains `FoliagePlacement(entityId, foliageName, terrainName, transform)` for
  terrain entities whose `FoliageComponent.assetName` is a string.
- **Drawing.** The plugin's `SceneFoliage` sits beside `SceneTerrains` and draws those placements.
- **A wrong or missing terrain** is a placement-level failure: it is logged once per revision and skips that foliage.
- **A stale or missing bake**: the editor hands the loaded settings, masks and terrain to `FoliageScatter` on a pooled
  thread and swaps the result in when it finishes. The view never blocks on generation.

### 5. Instanced drawing

`lib-core` `assets/foliage/FoliageDrawable` handles the GL side.

**The instanced mesh.** For each (foliage, layer model), it copies every mesh of the model into a new `Mesh` with
`enableInstancedRendering`. It reads the model's vertex and index buffers, which `VertexBufferObject` keeps on the CPU,
on the GL thread, once per model revision. The shared model `Mesh` cannot be used: instancing changes its vertex layout,
and normal entities still draw that mesh.

**Instance data.** Each instance is a `mat4` world transform in 4 `vec4` attributes. It combines:
- the entity transform;
- the copy's terrain-local x, z and its height from `TerrainData.heightAt`;
- the tilt toward the terrain normal by `alignToNormal`;
- the yaw and the scale.

Matrices are computed per chunk on a pooled thread when a chunk first becomes visible, or when the entity transform,
terrain or bake changes, and are kept in an LRU of chunks. They are uploaded on the GL thread.

**Culling.**
- Every frame tests chunk bounding boxes against the camera frustum.
- DETAIL layers also drop chunks whose nearest point is beyond `drawDistance`, then drop single copies beyond it while
  filling the instance buffer.
- The instance buffer is rebuilt only when the visible chunk set changes or a chunk's matrices change.

**Shaders.** `DefaultShader`, `PbrShader` and `ModelDepthShader` get an `instancedFlag` prefix. With it, the world
matrix is read from the instance attributes instead of `u_worldTrans`, and the normal matrix is derived in the vertex
shader. A renderable whose mesh is instanced gets the instanced variant from the shader providers, `FogShaderProvider`
included, by the same `canRender` rule.

**Shadows.** `SceneShadows` passes OBJECT-layer renderables to the depth pass, and DETAIL-layer renderables only to the
lit pass.

**Animation.** Animated models are drawn from their bind-pose mesh; no `AnimationController` is attached.

**Alternatives considered:**
- *One `ModelInstance` per copy*: about 10k draw calls already stall the view, and grass needs 100k+.
- *Geometry merged per chunk*: memory grows with copies × vertices, and every brush stroke means a rebuild.

### 6. Picking, drops and ray tracing ignore foliage

- **Picking and drops.** Foliage adds no `BoxTarget`s, so clicks and drops reach the terrain behind it with no change to
  `ScenePicker`.
- **Ray tracing.** `RaySceneSnapshot` skips foliage placements, and `RayControl` shows a "foliage is not ray traced"
  note when a scene has any.

### 7. One draft per foliage, shared by the panel, the brush and every view

`FoliageDrafts` is a project service in `plugin-abyssus` `foliage/`. Per foliage folder it holds the uncommitted
settings and masks, if any, plus a revision counter.

- **The panel and the brush write drafts.** The panel writes after each valid edit; the brush writes on every stamp.
- **Views read drafts.** `SceneFoliage` asks for the draft before the stored asset, and re-scatters the affected chunks
  when the revision moves.
- **Apply, stroke release, Cancel and selection changes** clear the draft.

This is how live updates reach every open Scene view without a file write. The pure part (a draft's state, dirty-chunk
tracking, and the merge of a brush stroke into a mask) is `lib-core-editor` `foliage/FoliageDraft` and is unit-tested.

### 8. The brush is a gesture in `SceneInteraction`

- **The mode.** `SceneViewState` gains a paint mode (the target entity id, layer id, radius, strength, and paint or
  erase), and `SceneInteraction` gains a `Gesture.Painting`.
- **Pressing.** A left press in paint mode intersects the pick ray with the selected entity's `TerrainTarget`. It starts
  a stroke only on a hit, so no gizmo or orbit gesture starts.
- **Dragging.**
  - Drags stamp at intervals of a quarter of the radius along the path.
  - Each stamp adds `±strength × 64 × smoothstep(1 − d/r)` to the mask texels within the radius. The mask is in
    terrain-local space through the inverse entity matrix, so rotated or scaled entities paint under the circle.
  - Values are clamped to 0 to 255.
- **Other buttons and the wheel** keep the existing orbit, pan and zoom paths.
- **Esc** turns the gesture into `Cancelled` and reverts the draft to its state at the press.

The stamp math and the stroke's dirty rectangle are pure functions in `lib-core-editor` `foliage/FoliageBrush`. The
gesture and the ray come through the existing `SceneQueries`, so the paint flow is tested headlessly like gizmo drags.

The cursor circle is drawn through `LineBatch` as a polyline of 64 points projected onto `TerrainData` heights. It is
drawn on the AWT thread inside the frame.

### 9. Undo never stores the bake

`foliage.data` can be about 18 MB, and the IDE keeps many undo steps, so snapshots of it would not scale. The bake is
rebuilt instead:

- **`AssetTransaction` gains derived files**: `DerivedFile(path, beforeSha256, afterSha256, rebuild: (forward) -> ByteArray)`.
  - Verify compares the file's SHA-256 with the expected side.
  - Apply calls `rebuild` and checks that the result hashes to the target.
  - Rollback rebuilds the other side.

  This works because generation is deterministic (decision 2).
- **Stroke undo keeps only the changed part of the mask.** A stroke's `FileChange` for `layer-<id>.mask` holds just the
  dirty rectangle, before and after (`MaskPatch`), applied over the current file after a SHA-256 check of the whole
  file. A 20-unit stroke on a 512² mask over 1600 units touches a few hundred bytes.
- **Create, Apply and Re-bake** stage `meta.json` and the masks as ordinary snapshots, and `foliage.data` as a derived
  file.
- **Which undo stacks the operations join.** Apply and Re-bake join the foliage `meta.json`'s stack, so Undo works
  from the panel. A stroke joins both the scene file's and the foliage `meta.json`'s, so Undo works from the Scene view
  and from the panel.
- **Undo of Create** uses `AssetReferenceGuard`, extended to find `FoliageComponent.assetName`; its generic `names()`
  scan already collects `assetName` values.

**Alternatives considered:**
- *Global undo actions*: they would be unreachable from the Scene view's undo context.
- *Storing full masks*: about 8 MB per stroke at 2048².

### 10. Meta writes through the transaction, with text preserved

The foliage panel's Apply must write `meta.json` and the bake together; writing the meta alone would leave the bake
stale until a Re-bake.

So, instead of a separate `editSceneJson`-style document command, the new meta text is computed by
`DocumentTextEditor` and staged in the same `AssetTransaction`. Number text, key order and unknown members are kept
exactly as in other meta edits. The open document is saved first, which is the same precondition terrain Apply uses.
`FoliageMetaEdits` (`lib-core-editor`) produces the edits from a settings diff.

Add Foliage edits the scene only, so it goes through `editSceneJson` like Add Asset.

### 11. Threads

| Piece | Thread |
|---|---|
| Meta/mask/data reading, fingerprint, `FoliageScatter`, chunk matrices, `FoliageMetaEdits` | pooled background (`AssetStorage.prepare` or the foliage executor); no GL, no `Gdx.*` |
| Brush stamps into the draft, gesture handling, cursor polyline | AWT thread (mouse events); pure math |
| Instanced mesh build, instance buffer upload, drawing, depth pass | AWT thread inside `GdxRuntime.withContext`, only while `glSafe` |
| `AssetFileCommand` (Create, Apply, Re-bake, stroke), `editSceneJson` (Add Foliage) | EDT write action; the bake is generated before the command, and only its hash and `rebuild` are kept |

Generation for a stroke's dirty chunks is posted to the pool, and the latest result wins. If a newer stamp has moved
the draft revision, a stale result is dropped.

### 12. Module placement

| Module | New |
|---|---|
| `lib-core` | `MetaType.FOLIAGE`, `assets/foliage/` (`FoliageMeta`, `FoliageDataFile`, `FoliageMaskFile`, `FoliageLoader`, `FoliageDrawable`) |
| `lib-gdx-model` | `instancedFlag` variants in `DefaultShader`, `PbrShader`, `ModelDepthShader` |
| `lib-core-editor` | `foliage/` (`FoliageSettings` + validation, `FoliageScatter`, `FoliageFingerprint`, `FoliageBrush`, `FoliageDraft`, `FoliageMetaEdits`, `NewFoliageFiles`), `FoliagePlacement` in `scene/` |
| `plugin-abyssus` | `foliage/` (`FoliageDrafts`, panel section, Add Foliage), `projectView/NewFoliageAction`, `sceneview/SceneFoliage`, paint strip and cursor, `assetfiles` derived files and mask patches, icon, bundle text |

The rules for every library stay as they are:
- `lib-core` and `lib-core-editor` stay wired by constructors, with no singletons.
- `lib-core-editor` has no Swing, AWT or platform imports. Its messages go through `EditorMessages`.
- `runCatchingKeepingCancellation` wraps everything that can be cancelled.

## Risks / Trade-offs

- **[Risk] Instanced shader variants break existing shaders or shadow output.** → The variants are additive behind
  `instancedFlag`. A GL test compares a single instanced copy with the same model drawn as a `ModelInstance`.
  `./gradlew check` keeps the shader compile tests.
- **[Risk] Reading mesh data back from `VertexBufferObject` fails for some mesh kinds** (static buffers without a CPU
  copy). → An early task verifies the CPU-side buffers for every fixture model. If one has none, fall back to
  reloading the model's `ModelData` through the loader's prepare step, which has CPU geometry.
- **[Risk] Large instance buffers on HiDPI or integrated GPUs.** → Per-chunk caps on the buffer, an LRU on chunk
  matrices, and the DETAIL draw distance. The 1,000,000 copy limit bounds the worst case at about 64 MB of matrices if
  everything is visible, which DETAIL culling prevents in practice.
- **[Risk] Floating-point differences between machines change the bytes of a bake.** Candidate positions and draws use
  integer-seeded SplitMix64, but the keep factor reads bilinear float heights and slopes. → `foliage.data` is a cache:
  a mismatching fingerprint triggers a regenerate rather than a failure, and the rule thresholds compare in `float`
  with fixed operation order. Byte-equality tests run in one JVM.
- **[Trade-off] Copy heights come from the terrain at draw time.** A terrain regeneration moves copies onto the new
  surface, but leaves the height and slope rules unapplied until the user re-bakes. The panel's stale notice covers
  this.
- **[Trade-off] Folder-name references break on renames.** This is the same as scenes today; renaming is out of scope.
- **[Risk] Undo from the Scene view reaching non-document files.** → `AssetFileUndoAction` already joins arbitrary file
  references. A plugin test runs a stroke, then undoes it through `UndoManager` with the scene file's editor context.

## Migration Plan

None. This adds a new asset type and an optional scene component. Existing documents are unchanged, and scenes without
`FoliageComponent` behave as before. A program that does not know `FoliageComponent` keeps it raw and writes it back,
so rolling back the plugin leaves scenes readable.

## Open Questions

- **Default values for a new layer** (density, scale range, DETAIL draw distance). These are tuned by hand in `runIde`
  against the Control Line field. They do not affect the format or the tests' structure.
- **The foliage icon artwork.** A placeholder SVG is acceptable for the first pass.
