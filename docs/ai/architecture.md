# Architecture

## Modules

| Module | What | Depends on |
|---|---|---|
| root (`src/`) | The IntelliJ plugin (IC 2025.2.4+, since-build 252, Java 21, Kotlin 2.4.10) | `:runtime`, `:core`, `:gdx-model`, `:raytracing`, Jackson, libGDX, LWJGL3-AWT |
| `runtime/` | Plain JVM project and scene parsing, Ashley components, codecs, systems and scene loading | `:core`, Ashley |
| `core/` | Plain JVM library: asset folders and `meta.json`, the asset loading pipeline, and the models, terrains and skies it builds | `:gdx-model`, Jackson, libGDX |
| `gdx-model/` | Plain JVM library: libGDX model runtime with 32-bit mesh indices and an Assimp importer | libGDX, LWJGL Assimp |
| `raytracing/` | Plain JVM ray tracing: backend contracts, immutable scene snapshots and linear host frames, the scheduler and quality policy, and optional native Metal and Vulkan backends | Kotlin stdlib, LWJGL Vulkan and VMA |

`gdx-model`, `core` and `runtime` must not import IntelliJ or plugin code (see their READMEs). `core` is wired by constructors:
its composition root `AssetLoading` takes a `JsonProcessor`, an `AssetLog`, an executor and the sky `ShaderSource`; in
the IDE the light application service `AbyssusCore` builds one (IDE log, IDE pool) and hands it to every scene view.
`AbyssusCore.scenes` builds `SceneLoading(json, log)` with the IDE's `Abyssus.scenes` logger. `SceneReader`
delegates parsing to it; `ProjectReader` delegates project-name parsing while retaining its VFS stamps and listings.
`SceneEntry(file, scene)` keeps editor sources out of the runtime DTO. Filesystem callers use `ProjectFolder` and
`SceneLoading.project` and `SceneLoading.load` with `Path`; every load gets its own engine, resolver and warnings. Parsing and loading
run on the caller's thread without GL. The plugin does not depend on Mundus.

`raytracing` is an optional GPU ray tracing renderer, off by default per view (switched from the **Ray Tracing**
switch in Abyssus Properties; the Scene View has no button for it). It owns nothing global: the plugin's `AbyssusCore` lazily builds one `RayBackendService` (a
`RayBackendSelector` over the Metal and Vulkan providers, and one serial native worker) and one converter thread, and
each Scene view registers a `RayViewRuntime` with it. Nothing native loads at startup: providers are only constructed and
probed when ray tracing is switched on, once per IDE session, and `-Dabyssus.raytracing.backend=off` never loads any.
`auto` tries Metal then Vulkan on macOS and Vulkan on Windows and Linux; a device loss marks the backend for a re-probe
on the next explicit Retry. The backend owns one device and queue, and each view gets its own session (scene structures,
command pools, output images) that is disposed on the owner worker.

### Changed assets to the scene view

`SceneFileEditor` watches the project's `assets` (VFS events and `meta.json` documents). `AssetRefresh` diffs snapshots of
effective asset revisions off the EDT (unsaved metadata text is captured on the EDT first), and a real change reaches
`SceneRenderer.queueAssetRevision` as an `AssetRevisionBatch`. The next safe frame invalidates the changed names in each
`AssetCache` and swaps old assets for new ones as they finish building. See
`src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md`.

### A scene file to the scene view

1. `SceneFileEditor` reads the scene and its project's `mainCamera` through `SceneParamsSource.EDITOR_TEXT`. It uses
   the unsaved editor text when there is any. It re-reads on every document or VFS change of those files.
2. `SceneRenderParams.from` → `SceneContent.of` turns the `ecs` JSON into placements: `models`, `terrains`,
   `lights`, `cameras`, plus the skybox name. The view reads the JSON through runtime component codecs; it does not run the Ashley engine.
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

- **Mouse and keys:** `SceneInteraction` handles them, over the `SceneViewState` (selection, gizmo mode, preview,
  camera looked through; owned by the panel) and `SceneQueries` (`SnapshotSceneQueries` over the `FrameSnapshot` the
  renderer publishes after each frame). A click picks (`ScenePicker` over model bounds, terrain
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

The eye toggle, Rename Scene, the skybox chooser, gizmo drags, Drop and component add, edit and remove (`SceneComponentEdits`) and asset property edits (`AssetMetaEdits`, over `core`'s
`AssetMetaEditor`; reference and face choices come from `properties/AssetReferenceChoices.kt`) all go through `editSceneJson`
(`src/main/kotlin/net/nevinsky/abyssus/filetype/SceneDocumentWriter.kt`):

1. Parse the document with `SceneJson`.
2. Mutate the tree.
3. Re-serialize with `SceneJson.inStyleOf`, which keeps indentation, key order and number text.
4. Replace the text in a `WriteCommandAction` and save.
5. Publish `AbyssusSceneEdited.TOPIC` (the file). The Abyssus pane listens and refreshes itself; the writer knows no UI.

Terrain regeneration and creation are the exception (binary heights, new files and folders cannot be a document edit):
they go through `AssetFileCommand` (`assetfiles/AssetFileCommand.kt`), described in `docs/ai/conventions.md`, with
`AssetTransactionEngine` holding the file logic (checks, ordered writes, rollback) apart from the platform so a test can
fail it between any two writes. No scene or project file is written that way.

### The `ecs` package

`SceneEcsLoader` reads a scene's `ecs` block into an Ashley `SceneEngine`, through one `ComponentCodec` per modeled
component. Components it doesn't model are carried raw. `SceneEcsWriter` writes the engine back in Mundus' format.
Systems are in `runtime/src/main/kotlin/net/nevinsky/abyssus/runtime/ecs/system/Systems.kt`. Only tests use the loader, writer and systems today; `ComponentEditor` is used by
the plugin: it adds, updates and removes a modeled component in the scene JSON (through the codecs, with reference
checks), and `SceneComponentEdits` runs it inside `editSceneJson` for the properties panel and the tree actions. See
`src/main/kotlin/net/nevinsky/abyssus/ecs/README.md`.
Per frame, on the EDT, `RayViewFeed` reads the renderer's current preview-applied content, camera, lights and animation
poses (`RayModelPoses`), freezes them into a job and offers it to a one-slot mailbox; a converter thread turns the newest
job into an immutable `RaySceneSnapshot` (`RaySceneSnapshots`, including CPU skin deformation) and offers it to the view's
`RayRenderScheduler`. The native worker keeps one frame in flight and one replaceable pending request per view and polls
completion, so the EDT never waits on a fence or a conversion. A completed linear RGBA/depth frame returns to the EDT,
where `RayFramePresenter` uploads it inside the safe canvas context and `SceneRenderer` composes the grid and overlays
against its depth. Scenes the backend cannot represent, a failed asset, or a device loss restore raster rendering at
once and surface a reason with a Retry button; no scene file is written by any of this. See `raytracing/README.md` for
toolchains, the opt-in device test commands (`-Dabyssus.metalTests=true`, `-Dabyssus.vulkanTests=true`), shading rules
and bounds, and `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md` for the view's states.

## Threading

- **The EDT:** all tree, properties and editor UI. The scene view's frames also run on the EDT, the AWT thread,
  through a Swing `Timer`.
- **`Gdx.*`:** these statics are process-global. `GdxRuntime.withContext` installs a per-canvas shim
  (`Gdx.app`, `Gdx.graphics`, `Gdx.gl*`, `Gdx.files`) under a lock, then restores the previous values. All libGDX
  calls happen inside it.
- **Asset loading:** `AssetCache.prepare` runs on a pool thread and does file IO and decoding, no GL. Building GPU
  objects happens on the render thread in `pump`, sliced per frame for big textures and for an HDR sky's
  environment passes (`HdrEnvironmentBuild`, which restores the framebuffer, viewport and state it changes).
  Reloading a changed asset follows the same split: `AssetRefresh` reads on the pool and delivers on the EDT, and
  invalidation, disposal, build and upload happen only inside `withContext` on a frame `GuardedGLCanvas` allows.
- **GL safety:** `GuardedGLCanvas` refuses GL until the canvas has been on screen with a non-zero size for 250 ms.
  When disposed while hidden, it drops the context without making it current, because on macOS that would abort the
  JVM.
- **Ray tracing:** the EDT only copies state, offers requests and uploads completed frames (inside the safe canvas
  context). Scene conversion and skin deformation run on one converter thread fed by a latest-wins mailbox; native
  preparation, submission, completion polling and disposal run on the ray service's serial worker. Mode transitions
  publish to the EDT; a late result after hide, close, retry or a replaced context is discarded by its revision.
- **Wiring:** classes get their collaborators through constructors (`PanelServices` for the Properties panel, a view
  factory and `SceneParamsSource` for the scene tab). Service lookups (`service<...>()`) appear only in actions, editor
  and file-type providers, tool window factories, the pane and the `@Service` constructors that build a service from
  others, so the rest can be tested without the IDE.
- **Off the EDT:** properties panel reads (`readAssetState`), `AssetReadCache` reads in the background tree builder,
  and asset `prepare`.

## Extension points

- **A new asset file format:** implement `ConfigFileReader` and return it from `AssetReadCache.readerFor`
  (`src/main/kotlin/net/nevinsky/abyssus/dto/ConfigFileReader.kt`). Add the extension to `ProjectLayout.ASSET_EXTENSIONS`.
- **A new ECS component:** write a `ComponentCodec` and add it to `ComponentCodecs`
  (`runtime/src/main/kotlin/net/nevinsky/abyssus/runtime/ecs/scene/ComponentCodecs.kt`).
- **A new asset kind drawn in the scene view:** an `AssetLoader` in `core` (built in `AssetLoading`), and a placement in
  `SceneContent`.
