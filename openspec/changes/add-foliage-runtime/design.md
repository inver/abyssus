# Design

## Context

See proposal.md for motivation and the specs for required behavior. This builds on `add-terrain-foliage` (decisions
3, 4 and 5 there). That change gives `lib-core`:
- `FoliageMeta`, `FoliageDataFile`, `FoliageMaskFile`;
- `FoliageLoader`, which declares the terrain and models as dependencies;
- the instanced `FoliageDrawable`, with per-chunk copy matrices computed from the entity transform, terrain heights,
  `alignToNormal`, yaw and scale.

It also gives `lib-core-editor` `FoliageScatter`, `FoliageMetaEdits` and `HeadlessEditing`.

What exists in the runtime, physics and game today:

- **`lib-runtime`**:
  - `EcsLoader` binds each component by its short name with Jackson. Unknown components are carried raw with one
    warning, and `EcsWriter` writes what the loader reads.
  - `BUILT_IN_COMPONENTS` lists the built-in short names, which games cannot take.
  - `FolderAssetResolver` checks asset folder names without GL.
  - `RenderComponentSystem` draws `RenderComponent`s through a `RenderContext`. Control Line does not use it: its
    `FieldRenderer` draws `FieldScene.models` and `terrains` itself.
- **`lib-physics`**:
  - `PhysicsWorld.build()` makes one Jolt body per entity with a `ColliderComponent`, using `ShapeFactory.build`.
  - It validates values before Jolt (`nonFinite`, sizes > 0) and warns with the entity named.
  - It owns every native object in `owned` and releases them in reverse order in `close()`. `removeEntity` and the
    engine listener remove an entity's body.
  - `PhysicsAssets(projectDir)` reads model vertices and terrain heights through `core` without GL.
  - The play host builds the same `PhysicsWorld` from the scene text and the project folder.
- **Control Line**:
  - `FieldScene` lists models and terrains from the ECS. `FieldRenderer` draws them with `ModelBatch` and a
    `DefaultShaderProvider`, and terrains with the game's own `shader/terrain.*`.
  - `FieldShadows` renders model and terrain depth with `ModelDepthShaderProvider` for one sun shadow map.
  - The `tools` source set holds seeded asset generators (`FieldAssets`, `PlaneModels`). They run by hand through
    `JavaExec` tasks, and their output is committed.
  - `AirfieldEnvironmentTest.sceneryKeepsTheFlyingAreaClear` checks entity model bounds against the 28 m radius.
  - Field vegetation today is 215 entities: 52 broadleaf, 40 young and 33 acacia trees, and 48 low and 42 tall bushes.
    Bushes all lie on the site terrain. Many trees lie outside it on the outer terrain (entity `300`, 600 m square,
    centred on the pilot), and the site terrain (entity `0`, 300 m) overlaps the outer one.

## Goals / Non-Goals

**Goals:**
- **One copy transform.** The editor, the game and physics compute a copy's world transform with the same code, so
  drawn and colliding copies coincide.
- **The runtime reads, never writes.** It reads foliage assets only, never generates, and stays constructor-wired with
  no GL in the read path.
- **Physics stays cheap.** Thousands of tree colliders do not cost thousands of separate bodies.
- **Control Line's flight is unchanged**, and its migration can be reproduced.

**Non-Goals:**
- A generic ECS render system for foliage, runtime regeneration, and colliders on Control Line scenery (see the
  proposal).

## Decisions

### 1. `FoliageComponent` is a plain built-in component; drawing stays outside the ECS

**The component.** `FoliageComponent(assetName: String? = null)` is added to `lib-runtime` `ecs/component` and to
`BUILT_IN_COMPONENTS`. Unknown members are carried the way `LightComponent` / `RenderComponent` carry the file's node:
the component keeps its source `ObjectNode`, and a serializer writes it back while `assetName` is unchanged.

**Folder checks.** At load, a deserializer checks the name with the injected `AssetResolver`. An unknown folder adds one
warning through `SceneEcsWarnings` and keeps the value.

**Listing.** `SceneFoliage.of(context)` returns `List<FoliageEntry(entity, foliageName, terrainName)>`. An entity is
listed when its `RenderComponent` resolves to a TERRAIN asset; a component on any other entity gets one warning. The
list is pure ECS reading.

**Alternative considered:** drawing through `RenderComponentSystem` with a foliage `RenderableDelegate`. Foliage needs
the camera for culling, separate shadow passes, and an asset pipeline the runtime's render path doesn't have. Control
Line, the only game, draws outside the ECS. A system can come later on top of the same `FoliageDrawable`.

### 2. One copy transform, in `lib-core`

The per-copy math from `add-terrain-foliage` decision 5 moves out of `FoliageDrawable` into a pure class,
`FoliageCopyTransforms`. It computes the entity matrix × the terrain-local x and z with the height from `heightAt` ×
the tilt toward the normal by `alignToNormal` × the yaw × the scale. Then:
- `FoliageDrawable` uses it for instance matrices;
- `lib-physics` uses it for collider placement;
- the editor's scene view keeps using `FoliageDrawable`.

A test compares the outputs for the same inputs, which covers the spec scenario "Same copies as the editor".

If `add-terrain-foliage` is implemented first with the math inside `FoliageDrawable`, this change extracts it. That is a
refactor with no behavior change, guarded by the existing `FoliageChunkMatricesTest`.

### 3. Runtime bake handling: use as written

The `FoliageLoader` prepare step already reports a stale fingerprint. The runtime only reads that flag and logs one
warning; it never calls `FoliageScatter`, which stays in `lib-core-editor`.

**Why not regenerate?** A game ships what was authored, and the bake is the only input it can trust to be
deterministic across machines (`add-terrain-foliage` design, risks). Moving the generator into `lib-core` would also
widen `lib-core`.

A missing, truncated or unsupported bake loads as an empty foliage with one warning.

### 4. Collider declaration lives on the layer

The layer's `collider` mirrors `ColliderComponent`'s BOX, SPHERE and CAPSULE fields, defaults and limits.
`FoliageMeta` binds it in `lib-core`. `lib-core-editor`'s `FoliageSettings` validates it and reuses the same messages
as the physics component editor. `FoliageMetaEdits` writes it. The plugin panel adds a shape combo (None, Box, Sphere,
Capsule) and the matching fields, on OBJECT layers only.

The collider is not part of the fingerprint inputs (`add-terrain-foliage` decision 3 lists them explicitly), so changing
it leaves the bake valid.

**Alternatives considered:**
- *Per-model collider metadata in the model's `meta.json`.* It would apply to every scene use of the model, including
  hand-placed entities, which already use `ColliderComponent`. That conflicts with the existing rule.
- *Fitting colliders automatically from model bounds.* Bounds include tree crowns, so planes would bounce off air.

### 5. Physics: one static body per (layer, chunk) with a static compound shape

At the end of `build()`, `PhysicsWorld` walks `SceneFoliage.of(engine)`. `PhysicsAssets.foliage(name)` reads the meta,
the bake and the terrain `TerrainData` through `core`, without GL, cached per asset like terrain heights.

For each OBJECT layer with a valid collider, and each chunk with copies:
- `ShapeFactory.foliageChunk(...)` builds a `StaticCompoundShapeSettings` with one sub-shape per copy, placed by
  `FoliageCopyTransforms` plus the collider offset, rotated by the copy's rotation and scaled per the existing rules.
  Capsules and spheres take the largest scale axis, with one warning per layer.
- One static body is made per chunk, in the non-moving layer.

The bodies are tracked in a `foliageBodies: Map<Entity, List<Body>>`, owned like other native objects. `removeEntity`
and the entity listener remove them, and `close()` releases them in reverse order.

**Validation and limits:**
- Validation runs before any shape is created, using the same checks as entity colliders, with one warning per layer.
- The 200,000-copy cap is counted in layer order (by entity id, then layer order). A layer that would cross it is
  skipped whole, with one warning.

**Alternatives considered:**
- *One body per copy*: about 5,000 bodies for a forest swamp the broadphase and the native allocations.
- *One compound per layer*: a wide-area body makes every query touch every copy, whereas chunk bodies keep the
  broadphase useful.

### 6. Control Line draws foliage in its own renderer

**Listing and loading.** `FieldScene` gains `foliage: List<FoliageEntry>`. `fieldAssets` registers `FoliageLoader`, and
`FieldRenderer.loaded` waits for the foliage too.

**Drawing.** `FieldRenderer` keeps one `FoliageDrawable` per entry. It draws them after the terrains and before the other
models, with the same `ModelBatch` and environment. `DefaultShaderProvider` hands out the instanced variants added by
`add-terrain-foliage`.

**Shadows.** `FieldShadows.render` takes the OBJECT-layer renderables of the drawables as extra casters through
`ModelDepthShaderProvider`'s instanced variant. DETAIL layers are not passed in, so grass only receives the shadow
attribute.

**The flying view.** The pilot camera only sees a few hundred metres, so DETAIL culling at the layer's draw distance
(about 60 m for grass) keeps the instance count small.

### 7. The migration tool `tools/FieldFoliage.kt`

A `JavaExec` task, `generateFieldFoliage`, runs with the tools classpath plus `lib-core-editor`. It is seeded, with a
fixed `lastModified`, like `FieldAssets`, so a rerun gives the same bytes.

1. **Read the current layout.** It reads `Field.scene` with `SceneJson` and collects the tree and bush entities by
   model: positions, and the scale range per species.
2. **Grass model.** It writes `model_airfield_grass_tuft`: three crossed quads with an alpha-tested texture generated
   from seeded noise, written through the existing glTF writer, with a `source.json` recording the generation
   instructions. It also writes `texture_airfield_grass_blades`.
3. **Foliage assets.** It writes `foliage_airfield_site` (bound to `terrain_airfield_site`) and `foliage_airfield_outer`
   (bound to `terrain_airfield_outer`) with `NewFoliageFiles`.
4. **Layers.**
   - Each foliage gets one OBJECT layer per species, each with the species' model and its scale range from the old
     entities.
   - Each layer's density is solved so that the expected count over its mask equals the old count on that terrain.
   - The site foliage also gets a DETAIL layer of `model_airfield_grass_tuft`.
5. **Masks.**
   - For each old copy, it stamps a disc at its terrain-local position: radius 6 m for trees and 3 m for bushes, a
     smooth falloff, value 255.
   - Inside the clear flying area, it zeroes every texel within 28 m plus that species' maximum crown radius (taken from
     the model's bounds × the maximum scale) of the pilot.
   - Under the site square, the outer masks are zero.
   - Grass gets 255 everywhere outside the pad, with zero within 25 m + 1 m of the pilot. Where the site splat map
     marks dirt or paths, grass is zero too, if a task confirms that the map has a channel that separates them;
     otherwise paths keep grass, and the environment README says so.
6. **Bake and scene.** It bakes with `FoliageScatter` and checks each species' count against ±25%, nudging density and
   rebaking if needed. Then it rewrites `Field.scene` through `HeadlessEditing`: it removes the 215 entities and sets
   `FoliageComponent` on entities `0` and `300`. Other entities keep their text.

**Why a tool and not hand-painting?** The result must be reviewable and reproducible. The environment docs require
recorded generation instructions, and tests need fixed bytes. The tool is not part of the game classpath.

### 8. Threads

| Piece | Thread |
|---|---|
| `FoliageComponent` load/write, `SceneFoliage` | the caller's (no GL) |
| `FoliageLoader.prepare`, `PhysicsAssets.foliage` | worker pool / physics build thread; no GL |
| `FoliageDrawable` build and draw, `FieldShadows` | the LWJGL3 main thread (game), inside the GL context |
| `PhysicsWorld` foliage bodies | the thread that owns the world (game main thread, play host thread) |
| `FieldFoliage` tool | a one-off JVM, no GL |

## Risks / Trade-offs

- **[Risk] Implementation order with `add-terrain-foliage`.** This change adds a requirement to its capability and
  reuses its classes. → The proposal declares the dependency, and task 1 checks that the other change is archived
  before starting.
- **[Risk] Migrated tree positions differ from today's.** → The specs bound counts per species (±25%), keep the regional
  density through masks stamped at the old positions, and re-check the clear area against actual copies. The
  environment README records the migration as an estimate.
- **[Risk] Big static compound shapes cost memory in the play host.** → Chunking plus the 200,000 cap. The Field has
  about 215 OBJECT copies and no colliders, so it is unaffected.
- **[Risk] Jolt static compound shapes reject sub-shapes with non-uniform scale on spheres/capsules.** → Use the largest
  axis, matching `ShapeFactory`'s existing rule, with a warning.
- **[Trade-off] Grass is instanced but alpha-tested.** That costs overdraw on low-end GPUs. → The DETAIL draw distance
  is tuned in the tool, around 60 m, and grass casts no shadows.
- **[Trade-off] Stale bakes are drawn rather than refused.** A game shows an old forest rather than none, with a
  warning to re-bake.

## Migration Plan

- **Existing scenes and games.** A scene without `FoliageComponent` is unaffected. A scene with one, loaded by an older
  runtime, keeps it raw, as before.
- **Control Line content is replaced in one commit:** the tool output (two foliage assets, the grass model and texture,
  the rewritten `Field.scene`) together with the test updates.
- **Rollback.** Revert that commit to get the 215 entities back.

## Open Questions

- **The exact grass density and draw distance.** These are tuned by hand against the pilot camera's frame time. They do
  not change the structure, and the tool writes whatever is chosen.
