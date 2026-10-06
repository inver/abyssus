# Design

## Context

See proposal.md for motivation. What exists today:

- **Assimp import in `gdx-model`.**
  - `AssimpModelDataLoader.load(id, file, flags, embeddedTextureDir)` turns any Assimp-readable file into `ModelData`
    without GL.
  - `ModelData` holds libGDX `ModelNode`s; a node part's `bones` maps node ids to inverse bind matrices.
  - It also holds `ModelAnimation`s with per-node translation, rotation and scale keyframes in seconds, plus
    `PbrModelMaterial`s.
- **`SceneNormalizer`** (`internal object`) reads FBX metadata (`UpAxis`, `CoordAxis`, `FrontAxis`, `UnitScaleFactor`).
  - It always rotates the root to Y up.
  - It converts units only when `convertUnits` is set; the default is off, because FBX units are used inconsistently.
  - OBJ and 3DS report no such metadata. Assimp applies a 3DS file's master scale itself.
  - Assimp's Collada importer applies a DAE file's `<asset>` unit and `up_axis` to the root itself, unless the import
    properties `IMPORT_COLLADA_IGNORE_UNIT_SIZE` and `IMPORT_COLLADA_IGNORE_UP_DIRECTION` are set. It does not report
    the stated values in the scene metadata.
  - glTF is metres and +Y up by definition. Assimp's glTF 2 importer reads metallic-roughness into
    `PbrModelMaterial` directly and drops morph targets and the extensions it does not know.
- **Embedded textures.** `TextureProcessor` writes them to `embeddedTextureDir`. `AssimpModelLoader.loadData` passes
  `<model folder>/embedded`, which would write next to an import's source file.
- **The FlightGear glTF writer.** `core.flightgear.GlbWriter` writes static glTF from its own `GltfNode` /
  `GltfPrimitive` / `GltfMaterial` types, using Jackson. `gdx-model` has no JSON dependency besides libGDX's
  `JsonWriter`.
- **The undoable write.** New Terrain and Import FlightGear Aircraft stage files off the EDT and write them as one
  `AssetFileCommand` (`assetfiles`), an `AssetTransaction` with `createdDirs` and `FileChange(Absent -> Bytes)`.
  `AssetReferenceGuard` refuses Undo once a scene uses the asset. `ImportFlightGearAction.projectRefusal` validates
  the `.abss` file.
- **The GL canvas.** `SceneViewPanel.newCanvas` builds a `GuardedGLCanvas` (an `AWTGLCanvas` that renders only while
  `glSafe`).
  - Its `initGL` creates a `GdxRuntime` context, and `disposeGL` releases resources inside it. A canvas disposed while
    hidden is abandoned and leaks its GL objects.
  - `OrbitCamera` is plain math. `SceneModels` drives `AnimationController` per entity.
- **Placement.** `SceneComponentEdits.addAsset(project, file, asset, position, cache, metaFiles)` adds the entity
  through `editSceneJson`, itself a `WriteCommandAction`. A scene view's Add Asset passes `orbit.target` of its
  `SceneViewPanel`, and the panel disables adding while it plays. `AssetFileCommand.execute` is also a
  `WriteCommandAction`, and registers a global `AssetFileUndoAction`.

## Goals / Non-Goals

**Goals:**
- One conversion pipeline, from source to `ModelData` to transform to GLB, whose `ModelData` is also what the preview
  draws.
- Everything except the dialog and the canvas is plain JVM and tested headless.
- The glTF writer is reusable outside the plugin.

**Non-Goals:**
- No change to how model assets load at runtime.
- No new `ModelMeta.Format`.
- No glTF features the renderer doesn't use: morph targets, cameras, lights or extensions (the writer declares none).
- No PBR export beyond what `PbrModelMaterial` holds.
- No Blender input, and no external converter process.

## Decisions

### 1. Convert to GLB from `ModelData`, not from Assimp's scene

The writer takes the `ModelData` that the renderer already consumes. This gives three things:
- **The preview matches the output:** it builds a `Model` from the same `ModelData` object that is written.
- **A round trip is testable:** write the GLB, read it back with `AssimpModelDataLoader`, and compare node tree, vertex
  data, bones and keyframes within tolerance.
- **One writer serves both importers:** FlightGear builds `ModelData` instead of `GltfNode`s.

Rejected alternatives:
- Assimp's own glTF2 exporter (`aiExportScene`, `glb2`). It is available in lwjgl-assimp, but skinned FBX exports are
  known to drop or duplicate inverse bind matrices. It also writes Assimp's view, not the one we preview, and its
  materials are Phong-derived.
- Keeping the source file (option A in exploration). It needs per-load unit and axis fields in `meta.json`, writes
  `embedded/` on load, and ties correctness to the Assimp version a game ships.

### 2. `GltfWriter` in `gdx-model` (`net.nevinsky.abyssus.lib.core.gltf`, beside `core.assimp`)

The writer is plain Kotlin, constructor-built, and uses libGDX `JsonWriter` so `gdx-model` keeps its dependencies.
`write(data: ModelData, images: Map<String, String>, generator: String): ByteArray` produces one GLB: JSON chunk, then
one BIN chunk.

- **Nodes:** written recursively, keeping ids as names, with translation, rotation and scale. A node part becomes a
  mesh primitive of that node's mesh.
- **Meshes:** each `ModelMesh` is split by attribute into accessors: POSITION, NORMAL, TANGENT (vec4), TEXCOORD_0/1,
  COLOR_0, JOINTS_0 (unsigned short) and WEIGHTS_0.
  - Part indices are written as unsigned int, matching 32-bit indices, and as unsigned short when they fit.
  - Each POSITION accessor gets min and max.
- **Skins:** one skin per distinct bone set, holding the joints (node indices) and an inverse-bind-matrices accessor
  taken from `bones`. The skeleton root is the joints' common ancestor.
- **Animations:** one glTF animation per `ModelAnimation`, with LINEAR samplers for translation, rotation and scale.
- **Materials:** from `PbrModelMaterial`: `baseColorFactor`, `metallicFactor`, `roughnessFactor`, `alphaMode` and
  `alphaCutoff`, `doubleSided`, base colour, normal, metallic-roughness and occlusion textures.
  - A plain `ModelMaterial` (diffuse and specular from Assimp's Phong path) is mapped by `PhongToPbr`.
  - Images are external URIs taken from the `images` map, so textures stay PNG files beside `model.glb`.
- **Determinism:** the output depends only on the input (stable order, no timestamps), so Redo and tests can compare
  bytes.
- **Its own validation:** accessor bounds, joint indices below the joint count, weights summing to 1 ± 1e-3.
  `GltfWriterTest` checks these.

`PhongToPbr` is a pure function:
- base colour = diffuse colour × diffuse texture;
- metallic = 0;
- roughness = `1 - sqrt(clamp(shininess / 1000, 0, 1))`, or 0.8 when no shininess is given;
- opacity < 1 gives `BLEND`;
- specular colour and specular maps are dropped and reported as approximated.

### 3. Detection and an un-normalized load

`SceneNormalizer` gains `stated(scene): StatedFrame(unitMetres: Float?, upAxis: Axis?)`. `AssimpModelDataLoader`
gains a `normalize: Boolean = true` option.
- The import loads with `normalize = false`, so the user's chosen unit and up axis are the whole transform. For a DAE
  file, `normalize = false` also sets both Collada ignore properties, so Assimp does not apply the stated frame either.
- The stated frame by format:
  - FBX: from the metadata, as above;
  - DAE: from `ColladaAsset.read(file)`, a small StAX reader in `gdx-model` (`core.assimp`) that reads only
    `COLLADA/asset/unit@meter` and `COLLADA/asset/up_axis`. `X_UP` is returned as stated but unsupported, and the
    form pre-fills Y with a note;
  - glTF and GLB: a constant 1 m and Y, marked as defined by the format;
  - OBJ: nothing; 3DS: Z up.
- The detected values only pre-fill the dialog.
- The existing behaviour of runtime loads is unchanged.

FBX `$AssimpFbx$` pivot nodes are kept as ordinary nodes: animations target them, and collapsing them is where
Assimp's FBX importer is known to break animation.

### 4. `modelimport` (in `editor-core`): plain JVM and constructor-wired

None of these classes has an `object`, so `checkNoSingletons` holds. All are tested headless.
- **`ModelSource`:**
  - Opens the file with `AssimpModelDataLoader(normalize = false)`, extracting embedded textures to a temp folder it
    owns and deletes on `close()`.
  - Returns the `ModelData` with the format, stated frame, animation names and durations, and the source SHA-256.
  - Records what was left out: Assimp cameras and lights, point/line meshes (already skipped by `MeshProcessor`, now
    counted) and missing texture files.
- **`ImportTransform`:** `(StatedFrame, ImportSettings) -> Matrix4`, plus the application to `ModelData`.
  1. Rotate the up axis to Y: for Z up, -90° about X, keeping handedness. Then scale by the unit.
  2. Compute rest-pose bounds by walking the node tree with each node part's mesh part bounds (rest pose, bones
     ignored).
  3. Apply the fit scale: largest extent, or height (Y extent).
  4. Translate so the min Y is 0 and the X/Z centre is 0.

  The result is applied as a new root node, `import_root`, wrapping the source roots. Vertices, inverse bind matrices
  and keyframes therefore stay untouched, and skins remain valid. `ImportTransformTest` covers the crate and
  fit-to-height cases.
- **`TextureGather`:**
  - Collects every texture `fileName` of kept materials.
  - Reads each image with `javax.imageio` (PNG, JPEG, BMP, GIF), so no libGDX natives are needed. It deduplicates by
    content hash and re-encodes each image as `textures/<name>.png`. TGA, DDS and other formats are reported as
    unsupported.
  - Returns the rename map for the writer. Unsupported or missing images are reported, and that material slot is
    cleared.
- **`ModelImport.stage(source, settings): StagedImport`:** produces the file map (`meta.json`, `model.glb`,
  `textures/*.png`, `source.json`) and the report.
  - `meta.json` is written with native markers first, then `version`, `lastModified`, a fresh `uuid`, `type` and
    `additional`, as `terrain.TerrainAssetWriter` does.
  - It is validated with `AbyssusDocumentFormat` before it is returned.
- **`ImportSettings`:** a value type (`folderName`, `unit`, `upAxis`, `fit: None | LargestExtent(m) | Height(m)`)
  with validation.
  - Folder-name rules: non-empty, no path separators or reserved names, unique among `assets/*`.
  - The default name is `model_<file stem>`, sanitised.

### 5. FlightGear moves onto the shared writer

`FlightGearImport` builds `ModelData` (one node per kept part, one mesh part per material and texture pair) and calls
`GltfWriter`. `core.flightgear.GlbWriter` and its `Gltf*` types are deleted. The FlightGear import and the model import
both live in `editor-core` (`net.nevinsky.abyssus.lib.core.editor.flightgear` / `.modelimport`), not in `core`; its
`SgiImage` writes PNG with `java.util.zip`, since `editor-core` imports no AWT.
- `FlightGearImportTest`'s assertions on nodes, materials, frame and size must keep passing.
- The Control Line Trainer's committed `model.glb` is re-imported with `importTrainer`, and the game tests are re-run.
- The archived `flightgear-aircraft-import` spec names no writer, so it needs no change. `docs/ai/` and `core/README.md`
  mentions of `GlbWriter` are updated.

### 6. Plugin: action, settings model, dialog, preview

- **`ImportModelAction`** (`projectView`): appears on the Assets node like the FlightGear action, and is registered in
  `plugin.xml` beside it.
  - It refuses a non-native `.abss` through the shared `projectRefusal`, moved to a small helper both actions call.
  - A file chooser with a `.obj` / `.fbx` / `.3ds` filter picks the source.
- **`ModelImportForm`:** a Swing-free model of the dialog state, unit-tested in `ModelImportFormTest`.
  - It holds the settings, which values came from the file, validation messages, the animation list and the
    Create-enabled state.
- **`ImportModelDialog`** (`DialogWrapper`):
  - the form fields, the "left out / approximated" list and the status line;
  - the preview panel and the animation drop-down.
- **Conversion runs off the EDT and only once per source.**
  - Opening the source runs on `AppExecutorUtil` under a progress indicator, cancellable.
  - Each settings change recomputes only the transform (cheap) on the pool and hands the result to the preview. The
    source is not re-read.
- **`ModelPreviewCanvas`** (`projectView.preview`): a `GuardedGLCanvas` subclass modelled on `SceneViewPanel.newCanvas`.
  - `initGL` creates its own `GdxRuntime` context. It draws inside `GdxRuntime.withContext` on the AWT thread, driven
    by a Swing `Timer`, only while `glSafe`.
  - It shows a `Model` built from the transformed `ModelData`, a grid (`GridModel`), a 1 m reference post and an
    `OrbitCamera`. The camera is framed on the bounds, with mouse orbit and wheel zoom.
  - An `AnimationController` plays the chosen animation in a loop. Textures are uploaded by the same queue
    `PreparedModel` uses.
  - When settings change, the old `Model` is disposed and a new one built, inside the context.
  - **Release order:** `doOKAction`, `doCancelAction` and `dispose` stop the timer and call the canvas's `disposeCanvas()`
    first, while the dialog is still showing, so `disposeGL` runs with a current context. The window closes only after
    that.
  - A canvas that never became `glSafe` has created nothing, so nothing leaks.
  - On a failed context the preview shows the reason as text; Create still works.
- **Create:**
  - It stages on the pool from the same `ModelSource` and settings that the preview shows.
  - It writes on the EDT through `AssetFileCommand` (`AssetTransaction` with `createdDirs = [folder]`), with
    `AssetReferenceGuard` as the undo guard.
  - It then selects the new asset in the Abyssus view, as `importFlightGear` does.
- User-facing text goes in `AbyssusBundle.properties`.

### 7. glTF and GLB input is converted, not copied

A `.gltf` or `.glb` source goes through the same pipeline as every other format: Assimp, `ModelData`, the transform,
`GltfWriter`.
- The preview still shows what is written, and grounding, centring, fit-to-size and `source.json` apply as for any
  other source.
- Its PBR materials arrive as `PbrModelMaterial`, so `PhongToPbr` is not used and nothing is reported as approximated.
- What the writer cannot hold is left out and listed: morph targets, the `KHR_materials_*` and `KHR_texture_transform`
  extensions, and KTX2 or WebP images (ImageIO cannot read them). `ModelSource` finds the extensions from the source's
  `extensionsUsed` (the JSON chunk of a GLB), because Assimp drops them silently.
- Textures in a GLB's BIN chunk or in data URIs come out through the embedded-texture folder, like FBX's.

Rejected: copying a conforming GLB as is. It skips grounding and centring, needs a second "is it conforming" check,
and makes the preview show something other than the written bytes. Converting loses nothing the renderer uses.

### 8. Placement in the same command

With Add to scene on, Create runs one outer `CommandProcessor.executeCommand` named after the action. The two nested
`WriteCommandAction`s, `AssetFileCommand.execute` and then `SceneComponentEdits.addAsset`, join it, so the platform
records one undo step.
- **Which scene:** the scene file of the selected scene view (`FileEditorManager.selectedEditors`, the first
  `SceneFileEditor`), taken when the dialog opens. Its `SceneViewPanel` exposes the orbit target and whether it plays.
  Both are read again on the EDT at Create.
- **The form:** `ModelImportForm` holds the placement target (scene name, or the reason it is disabled), so the
  disabled states are tested headless.
- **Before any write:** Create checks that the scene still reads as a native scene through `SceneDocumentCache`, and
  that the view is not playing. If either fails, it reports the reason and writes nothing.
- **Order and rollback:** the folder is written first, then the entity. `AssetFileCommand.execute` gains an option
  to hand back its `AssetFileUndoAction` instead of registering it, and the outer command registers it only after
  both writes succeed. If `addAsset` returns anything other than `Changed`, the outer command applies the folder
  transaction in reverse within the same command and registers nothing. The reason is reported, and no undo step is
  recorded.
- **Undo:** the platform undoes the group in reverse, so the entity goes first. When `AssetReferenceGuard` then checks
  the folder's Undo, it sees the scene without that entity. A reference added by any other edit still blocks the
  Undo, as before.
- **Afterwards:** the new entity is selected, as Add Asset does, instead of the asset folder.

### `source.json` of a model import

```
{ "importer": "model", "source": "hero.fbx", "sourcePath": "sources/hero.fbx", "sourceSha256": "...",
  "sourceFormat": "FBX",
  "stated": { "unit": "cm", "upAxis": "Z" }, "chosen": { "unit": "cm", "upAxis": "Z" },
  "size": "original" | { "largestExtent": 2.0 } | { "height": 1.8 },
  "animations": ["Idle", "Run"],
  "skipped": [ { "item": "Camera001", "reason": "camera" }, { "item": "wood.png", "reason": "missing" } ],
  "approximated": [ { "item": "Body", "reason": "specular colour dropped" } ] }
```

`sourcePath` is relative to the folder of the `.abss` file, with `/` separators, when the source is under it.
Otherwise it is the absolute path. `sourceFormat` is one of `OBJ`, `FBX`, `3DS`, `DAE`, `GLTF` and `GLB`.

It is documented in `docs/ai/file-formats.md` beside the FlightGear one.

### Threads

| Piece | Thread |
|---|---|
| Opening the source, the transform, texture gathering, GLB writing, staging | Pooled background thread, cancellable (`runCatchingKeepingCancellation`) |
| Dialog, form model, `AssetFileCommand`, placement | EDT; both writes run in one command, each in a write action |
| Preview `Model` build, texture upload, drawing, disposal | AWT thread, inside `GdxRuntime.withContext` with the canvas's own context, only while `glSafe` |

`ModelData` is handed from the pool to the AWT thread as an immutable snapshot. A new transform produces a new
`ModelData` copy and never mutates the one being drawn.

### Fixtures

All fixtures are small and committed under `projects/lib-core-editor/src/test/resources/modelimport/`:
- `crate.obj` + `crate.mtl` + `wood.png`: a 100-unit cube, hand-written;
- `crate_missing.obj`: its `.mtl` names a missing texture;
- `box.3ds`: Z-up, textured;
- `rig.fbx`: two bones, two animations (`Idle`, `Run`), one embedded texture, UnitScaleFactor 1 (cm) and Z up;
- `crate.dae`: the 100-unit cube, hand-written, `<unit meter="0.01"/>`, `Z_UP`, using `wood.png`;
- `crate_xup.dae`: the same with `X_UP`;
- `crate.glb`: a textured cube with metallic 1 and roughness 0.3, the texture in the BIN chunk;
- `animated.gltf`: a copy of `gdx-model`'s test resource (animations kept), plus `morph.gltf`, a hand-written triangle
  with one morph target and `KHR_texture_transform` in `extensionsUsed`.

The 3DS, FBX and GLB files are produced once by `MakeImportFixtures`, a test class that runs only with
`-Dabyssus.makeFixtures=true`. It builds the scene in code and writes it through Assimp's exporters (`3ds`, `fbx`, `glb2`).
Their SHA-256s are pinned in `ModelSourceTest`, so a regenerated fixture is noticed. If Assimp's FBX exporter cannot
write the skin and animations faithfully, a small CC0 FBX (with its licence noted beside it) replaces the generated
`rig.fbx`.

## Risks / Trade-offs

- **[Assimp FBX skinning quirks: pivots, bind-pose mismatches]** → Pivots are kept as nodes. The round-trip test
  compares the posed vertex positions of `rig.fbx` at several keyframe times, before and after writing. Real-world
  failures appear in the preview before Create.
- **[Phong to PBR looks different]** → It is reported as approximated in the dialog and in `source.json`. The preview
  shows the result.
- **[A GL canvas in a modal dialog on macOS]** → The dialog uses the same `GuardedGLCanvas` gate and releases GL before
  hiding. runIde checks cover open, resize, cancel and OK on macOS, with a scene view open.
- **[Two GL contexts at once]** → `GdxRuntime.withContext` already serialises several scene view tabs. The preview is
  one more context, and its resources never cross into another.
- **[Large sources (hundreds of MB, 4K textures)]** → Loading is cancellable with progress. The preview builds
  textures one per frame like `PreparedModel`. Staging streams textures one at a time.
- **[FlightGear output changes when its writer is replaced]** → `FlightGearImportTest` keeps its assertions, and the
  Trainer is re-imported, with the game's flight and parking tests as the gate.
- **[The Collada ignore-unit-size property]** → `IMPORT_COLLADA_IGNORE_UNIT_SIZE` may have no constant in
  lwjgl-assimp. It is set by its string name. `ModelSourceTest` checks that `crate.dae` loads at 100 units, not 1, with
  `normalize = false`.
- **[Undo order of the merged command]** → If the platform does not undo the entity before the folder, the reference
  guard would block the import's own Undo. `ImportModelTest` checks a single Undo and Redo with placement on.
  Otherwise, the guard is given the entity id the import added, and ignores that one reference.
- **[Absolute paths in `source.json`]** → A source outside the project records the user's local folder layout in a
  file that may be committed. This is accepted for re-import. A source copied into the project gives a relative path.
- **[A hostile source file]** → Assimp runs in-process, as it does for every model the editor loads today. The import
  adds no new parser.

## Migration Plan

No format migration. Existing assets are untouched. Rollback: revert the change. The FlightGear `GlbWriter` returns
with it, and the Trainer's `model.glb` is restored by the revert.

## Open Questions

- Whether to collapse FBX pivot nodes (`AI_CONFIG_IMPORT_FBX_PRESERVE_PIVOTS = false`) once the round-trip test shows
  it is safe. It changes node names in the output but no requirement.
