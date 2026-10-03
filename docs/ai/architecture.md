# Architecture

## Modules

| Module | What | Depends on |
|---|---|---|
| root (`src/`) | The IntelliJ plugin (IC 2025.2.4+, since-build 252, Java 21, Kotlin 2.4.10) | `:core`, `:gdx-model`, Jackson, libGDX, LWJGL3-AWT |
| `core/` | Plain JVM library: asset folders and `meta.json`, the asset loading pipeline, and the models, terrains and skies it builds | `:gdx-model`, Jackson, libGDX |
| `gdx-model/` | Plain JVM library: libGDX model runtime with 32-bit mesh indices and an Assimp importer | libGDX, LWJGL Assimp |

`gdx-model` and `core` must not import IntelliJ or plugin code (see their READMEs). `core` is wired by constructors:
its composition root `AssetLoading` takes a `JsonProcessor`, an `AssetLog`, an executor and the sky `ShaderSource`; in
the IDE the light application service `AbyssusCore` builds one (IDE log, IDE pool) and hands it to every scene view.
The plugin does not depend on Mundus.

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
2. `AssetReadCache` reads each through a `ConfigFileReader` chosen by extension (`SceneReader`, `ProjectReader` in
   `dto/`). It re-reads when the reader's `stamp` changes. A project's stamp folds in its scene files
   and asset folders.
3. `ProjectReader` builds a `ProjectDto`: the scenes from the `scenes` folder, and the assets from the `assets`
   folder with their `unused` flag (`ProjectReader.usedAssets`, see `docs/ai/file-formats.md`).
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
    A light's or camera's direction resolves its `PositionComponent.lookAtId` to an entity's `localPosition` when that
    target exists and is not at the entity itself; otherwise it uses the entity's `localRotation`. `handleIds` records
    the `HANDLE` entities that a light may be aimed at.
3. `SceneViewPanel` hosts a `GuardedGLCanvas`. A Swing `Timer` renders frames through
   `SceneRenderer.render`, which loads assets through `SceneModels` / `SceneTerrains` / `SceneSkybox` (each holding a
   `core` `SceneAssets` from `AssetLoading`, backed by an `AssetCache`) and draws markers (`SceneMarkers`) and gizmos
   (`sceneview/gizmo/`).
4. An HDR sky also lights the content. `core`'s `HdrSkyLoader` decodes the `.hdr` on the pool thread, then
   `HdrEnvironmentBuild` builds a specular cube, an irradiance cube and six axis colors on the GPU, one step per
   frame. Once built, `SceneSkybox.environment` hands them to `SceneRenderer`, which (`SceneAmbient.of`) swaps
   `ColorAttribute.AmbientLight` for `gdx-model`'s `EnvironmentLightAttribute` after drawing the grid: the PBR shader
   samples both cubes, the default shader takes the six colors as its ambient cubemap, and `TerrainShader` samples the
   irradiance cube. Without a built HDR sky the content is lit by the ambient color exactly as before.
5. `SceneShadows` captures model and terrain renderables after their single animation/transform update, then draws
   bounded depth tiles before the color passes. Its per-canvas `ShadowResources` restores framebuffer and render
   state and falls back to direct lighting if allocation or depth rendering fails. Default/PBR models and terrain
   apply atlas visibility per light; ambient, HDR and emissive terms remain independent. The 4096-square atlas supports sixteen views,
   shared by one directional, two six-face point and three spot shadows; small sets use larger tiles. Resources use the same
   safe AWT context lifecycle as assets, including CPU-only abandonment after context loss. See
   `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md` for fitting, budgets, sampler units and material limits.

### Clicks, drags and writes

- **Mouse and keys:** `SceneInteraction` handles them. A click picks (`ScenePicker` over model bounds, terrain
  heights and marker bounds). It selects in the view, and `selectEntityInAbyssusView` selects the entity's row in
  the tree.
- **Drags:** a drag on a gizmo handle runs a `GizmoDrag`, and `ScenePreview` shows the result live. Esc cancels.
  Any other drag orbits or pans `OrbitCamera`.
- **Drop:** the toolbar button or D calls `SceneInteraction.drop`. `ScenePicker.restHeight` queries the highest
  surface under the selection's oriented-box footprint, using box tops and transformed bilinear terrain cells.
  It moves only Y, previews the result, and uses the same `applyTransform` callback and Move Entity command as a
  move drag. No surface or an already-resting object produces no edit. `drawnVersion` triggers an availability
  re-check after loading changes, outside the GL context. New scene params also invalidate the next frame's query
  so changed transforms and Undo update availability even when the drawn entity ids stay the same.
- **On release:** `SceneFileEditor.applyTransform` calls `editSceneJson` with
  `SceneTransformWriter.apply`, which writes only the changed `localPosition` / `localRotation`, plus the camera's
  `position` / `viewPointPosition`. The document change triggers the re-read above.
- **Undo:** `SceneFileEditor` is a `DocumentReferenceProvider`, so Undo in the scene view tab reaches these edits.
- **Look-through:** the camera selector in the toolbar renders from a camera entity instead of the orbit camera.
  Orbit, pan and zoom pause while it is active.

### Every write

The eye toggle, Rename Scene, the skybox chooser, gizmo drags, Drop and component add, edit and remove (`SceneComponentEdits`) all go through `editSceneJson`
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
  objects happens on the render thread in `pump`, sliced per frame for big textures and for an HDR sky's
  environment passes (`HdrEnvironmentBuild`, which restores the framebuffer, viewport and state it changes).
- **GL safety:** `GuardedGLCanvas` refuses GL until the canvas has been on screen with a non-zero size for 250 ms.
  When disposed while hidden, it drops the context without making it current, because on macOS that would abort the
  JVM.
- **Off the EDT:** properties panel reads (`readAssetState`), `AssetReadCache` reads in the background tree builder,
  and asset `prepare`.

## Extension points

- **A new asset file format:** implement `ConfigFileReader` and return it from `AssetReadCache.readerFor`
  (`src/main/kotlin/net/nevinsky/abyssus/dto/ConfigFileReader.kt`). Add the extension to `ProjectLayout.ASSET_EXTENSIONS`.
- **A new ECS component:** write a `ComponentCodec` and add it to `ComponentCodecs`
  (`src/main/kotlin/net/nevinsky/abyssus/ecs/scene/ComponentCodecs.kt`).
- **A new asset kind drawn in the scene view:** an `AssetLoader` in `core` (built in `AssetLoading`), and a placement in
  `SceneContent`.
