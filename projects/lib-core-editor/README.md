# lib-core-editor

The editing engine of Abyssus without the IDE: a plain JVM library on `lib-core`, `lib-raytracing` and `lib-gdx`.
The plugin bundles it (in its `lib` folder) and keeps only IDE glue: `editSceneJson`'s undoable command,
the tree, tool windows, dialogs, the GL canvas and renderer, actions, file types and VFS wiring. Physics editing kinds are supplied by plugin composition; this library has no physics dependency.

## Rules

- **No IDE:** no `com.intellij`, Swing or AWT import in `src/main`. The Gradle classpath has no platform artifact, and
  `NoPlatformClasspathTest` checks the test classpath and the imports.
- **Wired by constructors:** no `object` or `companion object` with behavior (`./gradlew :lib-core-editor:checkNoSingletons`).
  Pure constant holders (`Placements.kt`, `SceneRenderParams.kt`) are listed in `abyssusSingletonExcludes`.
- **Messages:** user-facing text goes in `src/main/resources/messages/AbyssusEditorBundle.properties` and is read
  through an injected `EditorMessages`. The plugin passes `EditorBundle` (a `DynamicBundle` over the same file); a
  caller without the IDE uses `ResourceEditorMessages`. `EditorMessagesParityTest` in the plugin checks both give
  the same text.
- **Documents:** validation stays in `core.format`; this module only adds the editor-facing aliases.

## Packages (`net.nevinsky.abyssus.lib.core.editor`)

All source and test packages use this namespace. Consumers of the former `net.nevinsky.abyssus.lib.gdx.editor`
namespace, including extension providers that use editor value types, must update their imports and rebuild.

| Package | Holds |
|---|---|
| (root) | `EditorMessages`, `ResourceEditorMessages` |
| `document` | `SceneJson` (key order and number text kept, reading through `lib-core`'s `NativeJsonTree`), `JsonFormat`, `DocumentParsing`, `AssetMetaReader`, the format aliases, `SceneDocument` / `EntityView` / `SceneEntityTree` (the only code that knows `ecs[.entities].<id>.components`), `DocumentTextEditor` (parse, admit, mutate, admit, print in style), `SceneRaySettings` and `RayMaterialOverrides` (the `rayTracing` blocks) |
| `components` | `ComponentEditor` (add, update, remove a modeled component), `ComponentCodec`, `BuiltInComponentKinds`, `ComponentReader`, `LightEntities`, `AssetEntities` |
| `ecs` | `EcsWriter` (Jackson serialization of components and engines; a `SourceBackedComponent` such as `FoliageComponent` is written from its source object) |
| `content` | Leaf value types: `Vector3`, `Quat`, `Pose`, the placements, `RenderAsset`, `PlacementTransform.toMatrix` |
| `scene` | The read model: `SceneContent` / `sceneContentOf`, `FoliagePlacement`, `SceneRenderParams` / `renderParamsOf`, `PlacementMapper`, `CameraFrustum`, `LightSet`, `ModelEntity`, `AssetRevisions` |
| `pick` | `ScenePicker`, `SnapshotSceneQueries` (incl. `terrainOf` / `terrainHit` for painting), `TerrainRestHeight`, `OrbitCamera`, gizmo math (`GizmoDrag`, `GizmoHit`, `GizmoHandles`), `SceneInteraction` (with `GestureDriver` for the press-drag-release gestures, incl. the `Gesture.Painting` stroke and `FoliagePaint`, and `DrawingSession` for path drawing; both plain JVM), `FoliagePaintMode` in `SceneViewState`, `ScenePreview`, `SceneTransformWriter`, `SceneMarkers` / `LineSink` |
| `arrange` | Path arrangements: `PathLayout` (stations along a line on X/Z arc length, sides, offset, yaw, the 1000-slot cap), `ArrangementEdits` (`create`, `regenerate`, `detach` over the scene JSON), the `GroundHeight` interface that gives the copies their heights (`GroundHeights` in `pick` implements it from terrains) |
| `terrain` | Terrain generation, noise, recipe, new-terrain files and checks |
| `foliage` | Terrain foliage authoring: `FoliageSettingsReader` / `FoliageRead` (typed settings from an `additional` node with validation), the field keys and defaults, `FoliageScatter` (deterministic copy generation), `FoliageBrush` / `MaskRect` (the density-mask brush and its dirty rectangles), `FoliageDraft` (uncommitted settings, masks and dirty chunks), `FoliageMetaEdits` (settings diffs as `DocumentTextEditor` edits), `FoliageColliderEdits` (a layer's `collider` member patched from the edited collider, nested unknown members kept, and the collider-only classification of a diff; `FoliageSettingsReader` validates colliders and `transitions` refuses OBJECT to DETAIL with one) and `NewFoliageFiles` / `FoliageAssetWriter` (planning a new `FOLIAGE` asset) |
| `meta` | `AssetMetaEditor` and field descriptions, `MetaRows`, `AssetReferenceChoices`, the panel's entity sections and asset field states |
| `panel` | The Abyssus Properties panel model, with no Swing: `PanelModel` (`Header`, `Section`, the `Row` kinds, `FieldRef`, `EnableToggle`) and `PanelBuilders`, one pure builder per selection (project, folder, scene, scene setting, `ecs`, entity, component, UI screen, asset by type). A row is dim when the file does not write its value (`FieldValue.isDefault` is key absence, never equality with the default); units come from `ComponentField.unit`; a component section is togglable by `SetEnabledAction`'s rule; `CustomRow` marks where the plugin hosts an existing typed editor (terrain, foliage, faces, HDR, antialiasing). `PanelIcons` names the design's icon keys |
| `ray` | The ray tracing bridge: `RayBackendService`, `RayBackendSelector`, `RayViewRuntime`, `RayViewFeed`, `RaySceneSnapshots`, `RaySkyBaker` (a display-value sky bake with clouds at a given sky time and a revision per bake), `RayModeState`, diagnostics. The CPU snapshots of models, terrains and skies are in `ray.snapshot` (see Optional CPU companions below). A `RayFrameContext` carries the sky light (six axis colours), the baked sky and the cloud shade on the sun (`sunVisibility`, a `RaySunVisibility` from `raytracing`); a changed light, bake revision or visibility is an `ENVIRONMENT` change that restarts accumulation |
| `headless` | `HeadlessEditing` (spec `headless-scene-editing`) |
| `flightgear` | The FlightGear aircraft import: archive, model XML, AC3D and SGI readers, `FlightGearImport` |
| `modelimport` | The model import: `ModelSource`, `ImportSettings`, `ImportTransform`, `TextureGather`, `ModelImport` |
| `ui` | Game UI documents (spec `game-ui-authoring`): `UiDocumentEdits`, `UiTextEditor`, `UiRegistryEdits`, `UiAssetTemplate`, `UiTree` / `UiSelection` / `UiHitTest` and `UiLayoutGesture`. See *UI documents* below |

The package graph has no cycle (`./gradlew checkPackageCycles`): `content` is a leaf, `document` and `components`
build on it, `scene` reads the document through the component codecs, `foliage` builds on `terrain` and `document`,
and `pick` and `ray` build on `scene`. `arrange` builds on `document` and `ecs`; `pick` and `components` call it (`SceneTransformWriter` and
`ComponentEditor` detach a moved copy), so it names no type from them.

## Typed document admission

`DocumentParsing.requireSupported(text, kind)` validates header-only reads; `project`, `assetMeta` and `scene` return
typed DTOs after validation. `assetFields` is a narrow projection for editor reads that must ignore unrelated invalid
fields, and `foliageMeta` uses `FoliageSettingsBinding` to preserve the editor's wrong-type defaults, skipped malformed
entries and raw collider payloads. The tree stays inside admission and lossless editing. `FoliageSettingsReader`
validates either these typed settings or an existing edit tree through the same binding. Registry queries retain only
textual `uiAssetNames`, in order, and ignore malformed unrelated project fields.

The plugin's `assetfiles/AssetResultMessages.kt` maps all six `AssetCommandResult` outcomes in one place. Callers
supply their existing cancellation key and, where needed, conflict or exception formatting. This mapper is IDE glue;
the plain editing library does not depend on it. `NoManualJsonBuildingTest` in both modules guards the listed new-asset
writers and known-field read sites; D9's lossless-edit and generic-payload exceptions remain explicit.

## Imports

Both imports stage the files of a new asset folder, without GL or the IDE; the plugin writes them as one undoable
command.

`flightgear` converts a FlightGear aircraft archive into the files of one native `MODEL` asset, without GL:
`FlightGearArchive` (the zip, read lazily; `<archive>/Aircraft/` paths, zip-slip and size limits), `FlightGearModelXmlReader`
(model XML: AC3D path, offsets, nested models, panels, `select` animations), `RestState` (conditions with every
property 0), `Ac3dReader`, `SgiImage` (SGI to PNG, encoded with `Deflater`: no AWT) and
`FlightGearImport` (the public inspection/staging facade). It constructor-wires `FlightGearCollection` (nested
model placements, rest-state parts, texture lookup and licence inspection), `FlightGearGeometry` (frame, scale,
triangles and materials as CPU `ModelData`) and `FlightGearStaging` (textures, GLB, licence files, typed
`AssetMetaDocument` and `FlightGearImportRecord`). Staging keeps all bytes in memory until the complete result is
returned and writes the model with `lib-gdx`'s `GltfWriter` (glTF 2.0 binary with external images). These collaborators
need no GL context, Swing or IntelliJ. The plugin's Import FlightGear Aircraft and the Control Line trainer tool use it.

`modelimport` converts an OBJ, FBX, 3DS, DAE, glTF or GLB file into the files of one native `MODEL` asset, without
GL, so the plugin's Import Model dialog can preview exactly what it writes:
- `ModelSource` (opened by `ModelSourceOpener`) reads the file once through Assimp without applying any unit or axis
  (`normalize = false`), extracting embedded textures to a temp folder that `close()` deletes. It reports the frame the
  file states or its format defines (`SourceFrame`), the animations, the SHA-256, what is left out (cameras, lights,
  points and lines, morph targets, glTF extensions, missing or unreadable textures) and what `PhongToPbr` approximated.
- `ImportSettings` / `ImportSettingsRules`: the folder name (valid, unique among the asset folders, `model_<stem>` by
  default), the unit, the up axis (Y or Z) and the fit (`FitSize`).
- `ImportTransform` rotates the up axis to Y, scales by the unit, measures the rest pose, fits, grounds and centres,
  as one `import_root` node around the source roots; the source data is never changed.
- `TextureGather` writes the used images as `textures/<name>.png` (ImageIO; PNG kept as is, others re-encoded),
  deduplicated by content.
- `ModelImport.stage` produces `meta.json`, `model.glb` (`lib-gdx`'s `GltfWriter`), the textures and `source.json`,
  with an `ImportReport` (size in metres, animations, textures, what was left out or approximated).

## Scene documents

The editor keeps the parsed JSON tree as its model; games and Play keep the Ashley `SceneEngine`; both bind components
through Jackson to `core.ecs.component` classes (`ComponentReader` in the editor, `EcsLoader` in core). Readers use `SceneDocument` (admitted on
construction) and `EntityView`; writers address and insert entities through `SceneEntityTree` on a tree that
`editSceneJson` has admitted.

## Components

`ComponentEditor(EditorMessages)` creates the built-in kinds using `ComponentReader` and `EcsWriter`. The plugin
obtains it through `ComponentSchemas.of(project).editorFor(sceneFile)`, which returns built-in kinds plus injected physics kinds when the native project enables physics. Updates validate built-in values and references, then patch only changed fields. Render additions require
an asset name. Game components and disabled physics components remain read-only JSON; editing another component preserves them.
Project schema files and extension contributions do not currently add editable kinds.

`LightEntities` adds a Name, Type, Position and Light entity: `LightPreset` supplies Directional (white, intensity 1,
-45 degrees X), Sun (warm, intensity 1.2, -30 degrees X) and Spot (white, intensity 1, -90 degrees X, 5 units above
the placement). The new id is one above the highest numeric entity id; in a wrapped scene the matching archetype is
reused or appended.

`ArrangementEdits` edits a path arrangement: an entity of type `GROUP` with a `PathArrangementComponent`, and its copies,
ordinary `OBJECT` entities with a `ParentComponent` to it. `regenerate` rewrites the copies in the arrangement's `slots`
and adds or removes the rest. A moved, turned or position-edited copy is detached in the same edit (`SceneTransformWriter`
and `ComponentEditor` call `detach`), so the headless edits match. Updating a setting of an arrangement through
`ComponentEditor.update` regenerates in the same edit, on the `GroundHeight` passed in (the line's own height by default).

## Entity removal

`EntityRemoval` (in `components`) removes one entity entry from a scene tree and nothing else: assets, other entities and a
wrapped scene's `archetypes` and `metadata` keep their text. It is refused, leaving the tree as it was and naming the
relationship, when the entity is missing; when a remaining entity names it through `ParentComponent.parentEntityId`,
`PositionComponent.lookAtId` or `Point2PointPositionComponent.entity1Id` / `entity2Id` (short or fully qualified component
key); when it is listed in the `slots` of a `PathArrangementComponent`; when it is itself an arrangement that still owns
existing copies; or when a component kind passed to the constructor (a registered schema or physics kind) has an
`ENTITY_REF` field naming it. Unknown components are opaque: no reference is guessed from their numbers, and nothing is
cleared, detached or retargeted for the caller. `HeadlessEditing.removeEntity` runs it as text; the plugin's Remove Entity
tree action runs it through `editSceneJson`. `HeadlessEditing.removeEntities` (the foliage helper's built-in-only check)
remains for removing several entities at once.

## Enabled flag

`SetEnabledAction` (with `EnabledTarget`) writes or removes the optional `enabled: false` on one entity entry or one
component entry of a scene tree: disabling adds the key, enabling removes it, and no other value, key order or number text
changes. A `GROUP` entity and the Name, Parent and Position components are not togglable. `HeadlessEditing.setEnabled` runs it
as text, and the plugin runs it through `editSceneJson` as one undoable command. `sceneContentOf` reads the flag: a disabled
entity has no placements (so it is not drawn, shadowed, picked, gizmoed or put in a ray snapshot, while its position still
answers a look-at), and a disabled `RenderComponent`, `LightComponent`, `CameraComponent`, `FoliageComponent` or
`PathArrangementComponent` leaves out only its own placement. `EcsWriter` writes the flag back from `EnabledState`.

## UI documents

`editor.ui` edits a `UI` asset's `meta.json` (`additional.ui`, schema in [file-formats](../../docs/ai/file-formats.md)) as data:
no Swing, GL or platform classes.

- **`UiDocumentEdits`** works on the parsed tree: add, rename and delete screens; add, duplicate, reparent, reorder and
  delete widgets; set properties, layout fields, styles, bindings and actions; upsert and delete named styles and
  bindings (deleting one clears the widget references to it in the same edit). Each edit returns `Changed`, `Added(id)`,
  `Unchanged` or `Rejected(code, subject)` and leaves the tree untouched on a rejection. Ids are stable; a duplicate gets
  fresh ids for every widget it contains; reparenting converts the widget to the destination's layout policy (free
  placement is dropped under a row or column, `grow` under a panel); an unknown widget kind is kept as it is.
- **`UiTextEditor`** wraps an edit as a text transform: the document must be an admitted UI asset before and after
  (`UiCollectionDecoder`), a stale `expectedText` is refused, and the result is `Edited(before, after, result)`, whose
  `before` is the exact text an undo restores. `transform(root, op)` is the same check for a caller that already holds the
  tree inside its own document write.
- **`UiRegistryEdits`** adds or removes a name in the project file's `uiAssetNames`; `UiAssetTemplate` is the text of a
  new asset.
- **Gestures.** `UiLayoutGesture` previews a drag (panel children move by offsets, row and column children reorder,
  a drop over another container reparents) without writing; `commit()` returns the one edit of the whole gesture. Picking
  (`UiHitTest`) goes through `lib-core`'s layout, so it follows clipping and the letterboxed fit.
- **Persistence boundary.** An edit of an existing document is a text transform and goes through the plugin's
  `editSceneJson`. Creating a UI asset (a new folder, a new `meta.json`, imported images and fonts) is not a document edit;
  it is an `AssetTransaction` in the plugin that also edits the project file's registry, so creation and registration are
  one undoable command. See `docs/ai/architecture.md`.

## Without the IDE

`HeadlessEditing` validates `.scene`, `.abss` and `meta.json` text and applies the transform, component-field and
asset-property edits, returning the edited text or a `Refusal` with the reason the plugin shows. It runs the same
`DocumentTextEditor` that `editSceneJson` runs, so the same input gives byte-identical output
(`HeadlessSceneEditingTest` in the plugin compares them).

## Tests

`./gradlew :lib-core-editor:test` (one class: `--tests 'net.nevinsky.abyssus.lib.core.editor.pick.OrbitCameraTest'`). Tests use
`projects/plugin-abyssus` as their working directory, so `src/test/testData/project/` resolves to the shared fixtures.

The test fixtures (`parseScene`, `testProject`, `testAsset`, `terrainData`, `rayTestModel`) are shared with the plugin's tests.

## Optional CPU companions (ray snapshots)

These live in the `editor.ray.snapshot` package. They are editor-only, so a shipped game links none of them.

`RaySnapshotStore<S, D>(executor, metaLoader, loader, label, maxBytes)` is one shared, reference-counted cache of
immutable CPU snapshots for one asset kind of one project, keyed by asset name. The plugin builds one each for models,
terrains and skies. `S` is the snapshot (a `RaySnapshot` with a `byteSize`), `D` is the source a raster preparation
already holds, and `label` ("model", "terrain", "sky") names the kind in failure messages.

- `acquire(name)` returns a closeable `RaySnapshotLease`: `snapshot` is null while preparation is pending, `failure`
  explains an unreadable or over-budget asset. Close leases on ray-mode disable, asset removal and project/view
  replacement. Repeated instances/views share the same immutable snapshot; the last close removes the cached bytes. A
  closed lease holds no snapshot, and results arriving after release are dropped.
- `invalidate(name)` drops the old revision and any read in flight, then allows a fresh acquisition.
- `RaySnapshotLoader<S, D>` builds the snapshot: `load(meta)` reads the asset afresh (the fallback when ray mode starts
  after the raster asset is cached), and `capture(source)` copies a `D` that raster preparation offers. Kinds with no
  raster capture (skies) leave `capture` at its default and use `D = Nothing`.
- Ordinary raster preparation retains nothing when no lease requests the asset. When one does, `ModelLoader` calls the store through `store.asPreparationTap()` (a `lib-core` `ModelPreparationTap`; the offer is
  `store.preparation(name)?.offer(source)` with a `RayModelSource`) before upload disposes the Pixmaps; an offer after cancellation or reacquisition is a no-op. The fallback `load` disposes its own
  decoded images after copying, including failures. It never reads back, reloads or disposes a GPU resource.
- The terrain store has no raster capture (`D = Nothing`): the raster terrain keeps no pixels once its textures are
  on the GPU, so `TerrainRaySnapshotLoader` reads heights (through a `TerrainLoader`) and splat images (through a
  `TextureLoader`) afresh. It is a CPU companion read, not an asset load, so it uses both loaders directly.

### Optional CPU model companions

Snapshots copy 32-bit indices, interleaved vertex data/layout, mesh parts, material
parameters, node transforms/material assignments/bind transforms, and RGBA8888 image
bytes. Mutable libGDX objects and GL handles never cross this boundary. Image rows
retain Pixmap upload order; texture bindings keep UV transforms and the raster
renderer's Linear/Repeat/no-mipmap sampling. Raster default/PBR shaders sample all
these bytes linearly, including diffuse/emissive images, so snapshots apply no sRGB
conversion. Animation pose/deformation and native material conversion remain later
ray-renderer work.

Default bounds are 128 MiB geometry/image payload per model (`ModelRaySnapshotLoader`) and 256 MiB retained
payload across the store. Exceeding them reports a CPU companion failure without
breaking raster loading. These account for retained array payload, not native/GPU
allocations or temporary decoder memory; backend/application budgets are separate.

### Optional CPU terrain companions

The terrain store has the same lease lifecycle, with separate default bounds of 128 MiB per terrain
(`TerrainRaySnapshotLoader`) and 256 MiB retained across the store. It copies heights, the raster mesh's 32-bit triangle indices, interleaved
positions/normals/layer UVs, and decoded splat image bytes before upload/disposal.
Late acquisition loads the terrain through the plain `TerrainLoader` without touching cached GPU resources.
Snapshots stay terrain-local and can be shared across transformed instances; the
scene conversion supplies world transforms and inverse-transpose normal transforms.

Splat-map coordinates are local xz divided by terrain size, with linear filtering
and clamp-to-edge wrapping. Layers use the terrain's repeated UVs, Repeat wrapping,
linear magnification and trilinear mipmap minification; snapshots retain base-level
bytes and the mipmap requirement for later backend upload. All images follow the
raster shader's linear color convention and Pixmap row order. Layer bindings retain
base/R/G/B/A order: the shader mixes each present channel sequentially rather than
normalizing the weights. Missing or unreadable images are omitted, matching raster
loading: absent base means neutral gray, absent channel means skip that mix. Native
sampling/shading remains later ray-renderer work. Cancellation drops late results,
including raster preparations that began before a request was closed and reacquired.

### Optional CPU sky companions

The sky store hands out leases of an immutable `RaySkySnapshot`: the scene's sky as an equirectangular RGBA float image
(centre column faces -Z, top row is +Y), at most `RAY_SKY_MAX_WIDTH` (1024) wide (a procedural sky baked by the view is 2048 wide, bilinear, as 8-bit `rgba8()` data). `HdrSkyRaySnapshotLoader` decodes an
HDR sky again on the CPU and box-filters it down, keeping linear radiance far above 1; `SkyboxRaySnapshotLoader`
resamples a cube skybox with the GL cube lookup the raster sky uses (faces are back, front, left, right, bottom, top =
+X, -X, +Y, -Y, +Z, -Z) and keeps its display values. `ProceduralSkyRaySnapshotLoader` returns null: a procedural sky is
the asset's own GLSL and has no CPU form, so it reads as unavailable and the caller falls back to the background colour.
Leases share one read per asset, drop the bytes with the last reference (default bound 128 MiB in the plugin's wiring),
and never touch the GPU caches or GL.

## Weather snapshots

`weather/WeatherPresetDraft.kt` validates native sky and cloud metadata and snapshots the cloud UUID the sky names.
It uses core's cloud settings binder, writes canonical `CLOUDS` metadata with every known band default explicit,
and retains unknown extension members and unchanged numeric literals. Source documents retain omitted defaults
and are never edited. The caller supplies the new UUID and timestamp; this class performs no file IO or GL work.
