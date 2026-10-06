# editor-core

The editing engine of Abyssus without the IDE: a plain JVM library on `core`, `runtime`, `raytracing` and `gdx-model`.
The plugin bundles it (as `editor-core.jar` in its `lib` folder) and keeps only IDE glue: `editSceneJson`'s undoable command,
the tree, tool windows, dialogs, the GL canvas and renderer, actions, file types and VFS wiring. Abyssus Physics takes
it compile-only from Abyssus's classloader and never bundles it.

## Rules

- **No IDE:** no `com.intellij`, Swing or AWT import in `src/main`. The Gradle classpath has no platform artifact, and
  `NoPlatformClasspathTest` checks the test classpath and the imports.
- **Wired by constructors:** no `object` or `companion object` with behavior (`./gradlew :editor-core:checkNoSingletons`).
  Pure constant holders (`Placements.kt`, `SceneRenderParams.kt`) are listed in `abyssusSingletonExcludes`.
- **Messages:** user-facing text goes in `src/main/resources/messages/AbyssusEditorBundle.properties` and is read
  through an injected `EditorMessages`. The plugin passes `EditorBundle` (a `DynamicBundle` over the same file); a
  caller without the IDE uses `ResourceEditorMessages`. `EditorMessagesParityTest` in the plugin checks both give
  the same text.
- **Documents:** validation stays in `core.format`; this module only adds the editor-facing aliases.

## Packages (`net.nevinsky.abyssus.editor`)

| Package | Holds |
|---|---|
| (root) | `EditorMessages`, `ResourceEditorMessages` |
| `document` | `SceneJson` (key order and number text kept), `JsonFormat`, `DocumentParsing`, `AssetMetaReader`, the format aliases, `SceneDocument` / `EntityView` / `SceneEntityTree` (the only code that knows `ecs[.entities].<id>.components`), `DocumentTextEditor` (parse, admit, mutate, admit, print in style), `SceneRaySettings` and `RayMaterialOverrides` (the `rayTracing` blocks) |
| `components` | `ComponentEditor` (add, update, remove a modeled component), `ComponentCodec`, `BuiltInComponentKinds`, `SchemaCodec`, `ComponentReader`, `LightEntities`, `AssetEntities`, `SchemaMerge` |
| `content` | Leaf value types: `Vec3`, `Quat`, `Pose`, the placements, `RenderAsset`, `PlacementTransform.toMatrix` |
| `scene` | The read model: `SceneContent` / `sceneContentOf`, `SceneRenderParams` / `renderParamsOf`, `PlacementMapper`, `CameraFrustum`, `LightSet`, `ModelEntity`, `AssetRevisions` |
| `pick` | `ScenePicker`, `SnapshotSceneQueries`, `TerrainRestHeight`, `OrbitCamera`, gizmo math (`GizmoDrag`, `GizmoHit`, `GizmoHandles`), `SceneInteraction`, `ScenePreview`, `SceneTransformWriter`, `SceneMarkers` / `LineSink` |
| `terrain` | Terrain generation, noise, recipe, new-terrain files and checks |
| `meta` | `AssetMetaEditor` and field descriptions, `MetaRows`, `AssetReferenceChoices`, the panel's entity sections and asset field states |
| `ray` | The ray tracing bridge: `RayBackendService`, `RayBackendSelector`, `RayViewRuntime`, `RayViewFeed`, `RaySceneSnapshots`, `RaySkyBaker`, `RayModeState`, diagnostics |
| `headless` | `HeadlessEditing` (spec `headless-scene-editing`) |

The package graph has no cycle (`./gradlew checkPackageCycles`): `content` is a leaf, `document` and `components`
build on it, `scene` reads the document through the component codecs, and `pick` and `ray` build on `scene`.

## Scene documents

The editor keeps the parsed JSON tree as its model; games and Play keep the Ashley `SceneEngine`; both bind components
through `runtime`'s codecs (design D3 of `restructure-editor-modules`). Readers use `SceneDocument` (admitted on
construction) and `EntityView`; writers address and insert entities through `SceneEntityTree` on a tree that
`editSceneJson` has admitted.

## Components

`ComponentEditor` is built from component schemas (the plugin takes it from
`ComponentSchemas.of(project).editorFor(sceneFile)`). Each schema component becomes a kind whose codec is
`SchemaCodec` over `SchemaValues`, read and written through `runtime`'s `SchemaJson`. Vectors and colors are dotted
decimal fields (`leadout.x`, `paint.r`). Updates check declared limits and choices and entity references; an asset
reference must name a folder of the declared asset type (or be empty). Adding one writes only `"<ShortName>": {}`. A
component with no known schema stays read-only JSON, and an edit to another component keeps it.

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

`./gradlew :editor-core:test` (one class: `--tests 'net.nevinsky.abyssus.editor.pick.OrbitCameraTest'`). Tests run
from the repository root, so they read fixtures under `src/test/testData/project/` by the same paths as the plugin's
tests. The test
fixtures (`parseScene`, `testProject`, `testAsset`, `terrainData`, `rayTestModel`) are shared with the plugin's tests.
