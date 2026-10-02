# Architecture

## Modules

| Module | What | Depends on |
|---|---|---|
| root (`src/`) | The IntelliJ plugin (IC 2025.2.4+, since-build 252, Java 21, Kotlin 2.4.10) | `:gdx-model`, Jackson, libGDX, LWJGL3-AWT |
| `gdx-model/` | Plain JVM library: libGDX model runtime with 32-bit mesh indices and an Assimp importer | libGDX, LWJGL Assimp |

`gdx-model` must not import IntelliJ or plugin code (see `gdx-model/README.md`). The plugin does not depend on Mundus.

## What the plugin registers

All in `src/main/resources/META-INF/plugin.xml`:

- **File types:** `.scene` (`SceneFileType`) and `.abss` (`AbyssusProjectFileType`), both JSON; `.gltf` with its
  own PSI (`src/main/kotlin/net/nevinsky/abyssus/language/`).
- **Abyssus view:** a pane of the Project tool window (`AbyssusProjectViewPane`), opened on startup by
  `OpenAbyssusViewActivity`.
- **Scene view:** a second editor tab for `.scene` files (`SceneFileEditorProvider`).
- **Abyssus Properties:** a tool window (`AbyssusPropertiesToolWindowFactory`).
- **Rename Scene...:** a tree popup action (`RenameSceneAction`).
- **`SceneFormatListener`:** pretty-prints `.scene` / `.abss` text when opened in the text editor.

## Data flow

### Files to the tree

1. `findTopLevelAssets` (`projectView/AbyssusNodes.kt`) lists every `.abss`, and every `.scene` outside a project,
   under the content roots.
2. `AssetReadCache` reads each through `AssetReader.forExtension` (`SceneReader`, `ProjectReader` in
   `dto/AssetReader.kt`). It re-reads when the reader's `stamp` changes. A project's stamp folds in its scene files
   and asset folders.
3. `ProjectReader` builds a `ProjectDto`: the scenes from the `scenes` folder, and the assets from the `assets`
   folder with their `unused` flag (`ProjectAssets.usedAssets`, see `docs/ai/file-formats.md`).
4. `childrenOf` / `foldToggles` (`projectView/DtoTree.kt`) turn DTOs into rows. `DtoEntryNode` renders them, and
   `RowActions.kt` paints the eye, the scene "View" icon and the skybox "Choose" button.

### Tree selection to the properties panel

`AbyssusSelection` publishes the selected node on `AbyssusSelectionListener.TOPIC`. `AssetPropertiesPanel` reads
the selected asset folder's `meta.json` off the EDT (`readAssetState`) and shows it. It never writes.

### A scene file to the scene view

1. `SceneFileEditor` reads the scene and its project's `mainCamera` through `SceneParamsSource.EDITOR_TEXT`. It uses
   the unsaved editor text when there is any. It re-reads on every document or VFS change of those files.
2. `SceneRenderParams.from` → `SceneContent.of` turns the `ecs` JSON into placements: `models`, `terrains`,
   `lights`, `cameras`, plus the skybox name. The view reads the JSON directly; it does not use the `ecs` package.
3. `SceneViewPanel` hosts a `GuardedGLCanvas`. A Swing `Timer` renders frames through
   `SceneRenderer.render`, which loads assets through `SceneModels` / `SceneTerrains` / `SceneSkybox` (each backed
   by an `AssetCache`) and draws markers (`SceneMarkers`) and gizmos (`sceneview/gizmo/`).

### Clicks, drags and writes

- **Mouse and keys:** `SceneInteraction` handles them. A click picks (`ScenePicker` over model bounds, terrain
  heights and marker bounds). It selects in the view, and `selectEntityInAbyssusView` selects the entity's row in
  the tree.
- **Drags:** a drag on a gizmo handle runs a `GizmoDrag`, and `ScenePreview` shows the result live. Esc cancels.
  Any other drag orbits or pans `OrbitCamera`.
- **On release:** `SceneFileEditor.applyTransform` calls `editSceneJson` with
  `SceneTransformWriter.apply`, which writes only the changed `localPosition` / `localRotation`, plus the camera's
  `position` / `viewPointPosition`. The document change triggers the re-read above.
- **Undo:** `SceneFileEditor` is a `DocumentReferenceProvider`, so Undo in the scene view tab reaches these edits.
- **Look-through:** the camera selector in the toolbar renders from a camera entity instead of the orbit camera.
  Orbit, pan and zoom pause while it is active.

### Every write

The eye toggle, Rename Scene, the skybox chooser, gizmo drags and component add, edit and remove (`SceneComponentEdits`) all go through `editSceneJson`
(`projectView/EnabledToggle.kt`):

1. Parse the document with `SceneJson`.
2. Mutate the tree.
3. Re-serialize with `SceneJson.inStyleOf`, which keeps indentation, key order and number text.
4. Replace the text in a `WriteCommandAction` and save.
5. Refresh the Abyssus pane.

### The `ecs` package

`SceneEcsLoader` reads a scene's `ecs` block into an Ashley `SceneEngine`, through one `ComponentCodec` per modeled
component. Components it doesn't model are carried raw. `SceneEcsWriter` writes the engine back in Mundus' format.
Systems are in `ecs/system/Systems.kt`. Only tests use the loader, writer and systems today; `ComponentEditor` is used by
the plugin: it adds, updates and removes a modeled component in the scene JSON (through the codecs, with reference
checks), and `SceneComponentEdits` runs it inside `editSceneJson` for the properties panel and the tree actions. See
`src/main/kotlin/net/nevinsky/abyssus/ecs/README.md`.

## Threading

- **The EDT:** all tree, properties and editor UI. The scene view's frames also run on the EDT, the AWT thread,
  through a Swing `Timer`.
- **`Gdx.*`:** these statics are process-global. `GdxRuntime.withContext` installs a per-canvas shim
  (`Gdx.app`, `Gdx.graphics`, `Gdx.gl*`, `Gdx.files`) under a lock, then restores the previous values. All libGDX
  calls happen inside it.
- **Asset loading:** `AssetCache.prepare` runs on a pool thread and does file IO and decoding, no GL. Building GPU
  objects happens on the render thread in `pump`, sliced per frame for big textures.
- **GL safety:** `GuardedGLCanvas` refuses GL until the canvas has been on screen with a non-zero size for 250 ms.
  When disposed while hidden, it drops the context without making it current, because on macOS that would abort the
  JVM.
- **Off the EDT:** properties panel reads (`readAssetState`), `AssetReadCache` reads in the background tree builder,
  and asset `prepare`.

## Extension points

- **A new asset file format:** implement `AssetReader` and return it from `AssetReader.forExtension`
  (`../../src/main/kotlin/net/nevinsky/abyssus/dto/ConfigFileReader.kt`). Add the extension to `ProjectLayout.ASSET_EXTENSIONS`.
- **A new ECS component:** write a `ComponentCodec` and add it to `ComponentCodecs`
  (`src/main/kotlin/net/nevinsky/abyssus/ecs/scene/ComponentCodecs.kt`).
- **A new asset kind drawn in the scene view:** an `AssetLoader` for `SceneAssets`, and a placement in `SceneContent`.
