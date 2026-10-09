# editor-core

The editing engine of Abyssus without the IDE: a plain JVM library on `core`, `raytracing` and `lib-gdx`.
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
| `document` | `SceneJson` (key order and number text kept), `JsonFormat`, `DocumentParsing`, `AssetMetaReader`, the format aliases, `SceneDocument` / `EntityView` / `SceneEntityTree` (the only code that knows `ecs[.entities].<id>.components`), `DocumentTextEditor` (parse, admit, mutate, admit, print in style), `SceneRaySettings` and `RayMaterialOverrides` (the `rayTracing` blocks) |
| `components` | `ComponentEditor` (add, update, remove a modeled component), `ComponentCodec`, `BuiltInComponentKinds`, `ComponentReader`, `LightEntities`, `AssetEntities` |
| `ecs` | `EcsWriter` (Jackson serialization of components and engines) |
| `content` | Leaf value types: `Vec3`, `Quat`, `Pose`, the placements, `RenderAsset`, `PlacementTransform.toMatrix` |
| `scene` | The read model: `SceneContent` / `sceneContentOf`, `SceneRenderParams` / `renderParamsOf`, `PlacementMapper`, `CameraFrustum`, `LightSet`, `ModelEntity`, `AssetRevisions` |
| `pick` | `ScenePicker`, `SnapshotSceneQueries`, `TerrainRestHeight`, `OrbitCamera`, gizmo math (`GizmoDrag`, `GizmoHit`, `GizmoHandles`), `SceneInteraction`, `ScenePreview`, `SceneTransformWriter`, `SceneMarkers` / `LineSink` |
| `terrain` | Terrain generation, noise, recipe, new-terrain files and checks |
| `meta` | `AssetMetaEditor` and field descriptions, `MetaRows`, `AssetReferenceChoices`, the panel's entity sections and asset field states |
| `ray` | The ray tracing bridge: `RayBackendService`, `RayBackendSelector`, `RayViewRuntime`, `RayViewFeed`, `RaySceneSnapshots`, `RaySkyBaker`, `RayModeState`, diagnostics |
| `headless` | `HeadlessEditing` (spec `headless-scene-editing`) |
| `flightgear` | The FlightGear aircraft import: archive, model XML, AC3D and SGI readers, `FlightGearImport` |
| `modelimport` | The model import: `ModelSource`, `ImportSettings`, `ImportTransform`, `TextureGather`, `ModelImport` |

The package graph has no cycle (`./gradlew checkPackageCycles`): `content` is a leaf, `document` and `components`
build on it, `scene` reads the document through the component codecs, and `pick` and `ray` build on `scene`.

## Imports

Both imports stage the files of a new asset folder, without GL or the IDE; the plugin writes them as one undoable
command.

`flightgear` converts a FlightGear aircraft archive into the files of one native `MODEL` asset, without GL:
`FlightGearArchive` (the zip, read lazily; `<archive>/Aircraft/` paths, zip-slip and size limits), `FlightGearModelXmlReader`
(model XML: AC3D path, offsets, nested models, panels, `select` animations), `RestState` (conditions with every
property 0), `Ac3dReader`, `SgiImage` (SGI to PNG, encoded with `Deflater`: no AWT) and
`FlightGearImport` (inspection, then staging: frame, scale, textures, `meta.json`, `source.json`), which builds a
`ModelData` and writes it with `lib-gdx`'s `GltfWriter` (glTF 2.0 binary with external images). The plugin's Import
FlightGear Aircraft and the Control Line trainer tool use it.

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

## Without the IDE

`HeadlessEditing` validates `.scene`, `.abss` and `meta.json` text and applies the transform, component-field and
asset-property edits, returning the edited text or a `Refusal` with the reason the plugin shows. It runs the same
`DocumentTextEditor` that `editSceneJson` runs, so the same input gives byte-identical output
(`HeadlessSceneEditingTest` in the plugin compares them).

## Tests

`./gradlew :lib-core-editor:test` (one class: `--tests 'net.nevinsky.abyssus.lib.core.editor.pick.OrbitCameraTest'`). Tests use
`projects/plugin-abyssus` as their working directory, so `src/test/testData/project/` resolves to the shared fixtures.
The test fixtures (`parseScene`, `testProject`, `testAsset`, `terrainData`, `rayTestModel`) are shared with the plugin's tests.

## Weather snapshots

`weather/WeatherPresetDraft` validates native sky and cloud metadata and snapshots the cloud UUID the sky names.
It uses core's cloud settings binder, writes canonical `CLOUDS` metadata with every known band default explicit,
and retains unknown extension members and unchanged numeric literals. Source documents retain omitted defaults
and are never edited. The caller supplies the new UUID and timestamp; this class performs no file IO or GL work.
