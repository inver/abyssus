# Architecture

## Modules

| Module | What | Depends on |
|---|---|---|
| `projects/plugin-abyssus/` | The IntelliJ plugin (IC 2025.2.4+, since-build 252, Java 21, Kotlin 2.4.20): IDE glue over `editor-core` (tree, tool windows, dialogs, the GL canvas and renderer, actions, file types, VFS) | `:lib-core-editor`, `:lib-core`, `:lib-physics` (non-transitive), `:lib-raytracing` (with `:lib-gdx` transitively), Jackson, libGDX, LWJGL3-AWT |
| `projects/lib-core-editor/` | Plain JVM editing engine: scene documents and the write transform, component editing, the scene read model, picking and gizmo math, terrain generation, asset meta editing, the ray tracing bridge, `HeadlessEditing` | `:lib-core`, `:lib-raytracing`, `:lib-gdx` |
| `projects/lib-physics/` | Plain JVM physics: the physics components and `PhysicsWorld` (Jolt through jolt-jni), run in a game or the play host, never in the IDE | `:lib-core`, jolt-jni |
| `projects/app-game-control-line/` | **Control Line**, a libGDX desktop game (LWJGL3): flight on Jolt lines, scoring, screens, its bundled native project and its `PlayModule` for Play in Abyssus | `:lib-physics`, libGDX LWJGL3 backend, jolt-jni natives of the build machine |
| `projects/lib-core/` | Plain JVM library: asset folders and `meta.json`, the asset loading pipeline (`AssetStorage`), CPU ray snapshots (`RaySnapshotStore`), the models, terrains and skies it builds, native DTOs, Ashley components and scene loading | `:lib-gdx`, Jackson, libGDX |
| `projects/lib-gdx` | Plain JVM library: libGDX model runtime with 32-bit mesh indices, an Assimp importer and a binary glTF writer | libGDX, LWJGL Assimp |
| `projects/lib-raytracing/` | Plain JVM ray tracing: backend contracts, immutable scene snapshots and linear host frames, the scheduler and quality policy, and optional native Metal and Vulkan backends | Kotlin stdlib, LWJGL Vulkan and VMA |

```text
lib-gdx <- lib-core <- lib-physics <- app-game-control-line
lib-core <- lib-core-editor <- plugin-abyssus
lib-raytracing <- lib-core-editor
lib-raytracing <- plugin-abyssus
lib-physics <- plugin-abyssus
```

Arrows point toward dependencies. `lib-core-editor` also directly depends on `lib-gdx` and `lib-raytracing`;
`plugin-abyssus` directly depends on `lib-core`, `lib-core-editor` and `lib-raytracing`.
All library modules must stay free of IntelliJ and plugin imports; `editor-core` also has no Swing or AWT.

Inside every module the package graph is acyclic (`checkPackageCycles`). `core` is wired by constructors:
`BaseCtx` is its standalone composition root, creating JSON, file/meta loaders, an asset executor and storage,
shaders, component registration and ECS/scene loaders. The plugin uses its own `AssetLoading`, which takes a
`JsonProcessor`, SLF4J `Logger`, executor and `ShaderStorage`; `AbyssusCore.assets` builds it and each view gets a
project loading graph. Hosts own the standalone executor and asset-storage lifecycle.
`AbyssusCore` creates four groups independently on first access: `documents` owns JSON, native validation and parsing;
`assets` owns metadata, loading, property descriptions and previews; `terrain` owns generation and file staging;
`ray` owns the backend service and conversion worker. Actions, providers and factories pass the group collaborators
they need. Disposal closes only initialized ray resources; reading a document does not create native workers.
The plugin's `SceneReader` and `ProjectReader` use `DocumentParsing` and `JsonProcessor` to validate and bind
scene and project text while retaining their VFS stamps and listings. Filesystem callers use `core`'s `SceneLoader`.
`SceneEntry(file, scene)` keeps editor sources out of `SceneDto`. Filesystem callers use `ProjectLoader` to bind
`ProjectDto` (`file()`, `sceneFiles()`) and `RuntimeSceneLoader(sceneLoader, ecsLoader, log)` to load scenes by file
name or supplied text; every load gets its own engine and warnings. Parsing and loading
run on the caller's thread without GL.

**Built-in physics** (`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/physics/`) runs Play outside the IDE. On Play, `PhysicsSimulationProvider` picks what
to launch (`PlayLaunch`): the game's `<project>/abyssus/play.json` classpath and module, or the bundled `play-host` jars with
`PhysicsOnlyPlayModule`. `PlayProcessLauncher` starts `<java.home>/bin/java ... PlayHostMain --port --token` on a
loopback port, waits 20 s for the hello, and keeps the last 200 output lines. `PlayClient` sends `load` (the editor's
scene text), then `play`. A reader thread publishes the latest poses, which the Scene view shows through the
`sceneSimulation` extension point. The process exits on `bye` or when the socket closes. The protocol is in
`projects/lib-physics/src/main/kotlin/net/nevinsky/abyssus/lib/physics/play/PlayProtocol.kt`.

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
`AssetStorage` and swaps old assets for new ones as they finish building. See
`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md`.

### A scene file to the scene view

1. `SceneFileEditor` receives tree integration through the pane's `SceneViewHost` and reads the scene and its project's `mainCamera` through `SceneParamsSource.EDITOR_TEXT`. It uses
   the unsaved editor text when there is any. It re-reads on every document or VFS change of those files.
2. `renderParamsOf` → `sceneContentOf` (the scene package of `editor-core`) reads the `ecs` JSON through `SceneDocument` into placements: `models`, `terrains`,
   `lights`, `cameras`, plus the skybox name. The view binds JSON through `ComponentReader` to `core.ecs.component` classes; it does not run the Ashley engine.
   A light's or camera's direction resolves its `PositionComponent.lookAtId` to an entity's `localPosition` when that
   target exists and is not at the entity itself; otherwise it uses the entity's `localRotation`. `handleIds` records
   the `HANDLE` entities that a light may be aimed at.
3. `SceneViewPanel` hosts a `GuardedGLCanvas`. A Swing `Timer` renders frames through
   `SceneRenderer.render`, which loads assets through `SceneModels` / `SceneTerrains` / `SceneSkybox` (each holding a
   `core` `AssetStorage` built by `AssetLoading`) and draws markers (`editor-core`'s `SceneMarkers` through a
   `LineSink`) and gizmos (`sceneview/gizmo/GizmoDraw.kt` over `editor-core`'s `GizmoHandles`).
4. An HDR sky also lights the content. `core`'s `HdrSkyLoader` decodes the `.exr` through TinyEXR on the pool thread, then
   `HdrEnvironmentBuild` builds a specular cube, an irradiance cube and six axis colors on the GPU, one step per
   frame. Once built, `SceneSkybox.environment` hands them to `SceneRenderer`, which (`SceneAmbient.of`) swaps
   `ColorAttribute.AmbientLight` for `lib-gdx`'s `EnvironmentLightAttribute` after drawing the grid: the PBR shader
   samples both cubes, the default shader takes the six colors as its ambient cubemap, and `TerrainShader` samples the
   irradiance cube. Without a built HDR sky the content is lit by the ambient color exactly as before.
   A procedural sky may have **clouds** (`core`'s `assets.sky.clouds`): a `CLOUDS` asset its `additional.clouds` names
   by `uuid`. `ProceduralSkyLoader` resolves the `uuid` to a folder (`AssetIndex`) and names it in `dependencies`, so the
   one `AssetStorage` loads it first; `CloudsLoader.prepare` uses its bound `CloudMeta` and makes the volumetric 3D
   noise (FastNoiseLite) on the pool thread, and its build uploads the noise. The sky reads the built `Clouds` from
   `BuiltAssets` on every draw, as `TerrainMesh` reads its splat textures, and never owns it. Every `SkyRenderer.draw` takes a `SkyFrame(sun, timeSeconds, technique, clouds)`;
   `ProceduralSky` draws the asset's atmosphere, then a core-owned cloud pass blended premultiplied over it through a
   `CloudRenderer` strategy (`LayeredClouds`, `ShellClouds`, `VolumetricClouds`, built lazily by `CloudTechniques`, which
   steps down volumetric to shells to layered to none when one cannot be built). All techniques and `SunOcclusion` share
   one coverage field, `CloudField` in Kotlin and `clouds_common.glsl` (kept equal by `CloudFieldParityGlTest`).
   `SceneRenderer` advances the view's `SkyClock`, passes `SceneViewState.cloudTechnique` (the toolbar's per-view
   override, `CloudViewState`), and scales the sun light (`SunDirection.sunLight`) by `SunOcclusion` each frame for the
   environment and `TerrainShader`; shadows, other lights and ray tracing use the unscaled set.
5. `SceneShadows` captures model and terrain renderables after their single animation/transform update, then draws
   bounded depth tiles before the color passes. Its per-canvas `ShadowResources` restores framebuffer and render
   state and falls back to direct lighting if allocation or depth rendering fails. Default/PBR models and terrain
   apply atlas visibility per light; ambient, HDR and emissive terms remain independent. The 4096-square atlas supports sixteen views,
   shared by one directional, two six-face point and three spot shadows; small sets use larger tiles. Resources use the same
   safe AWT context lifecycle as assets, including CPU-only abandonment after context loss. See
   `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md` for fitting, budgets, sampler units and material limits.

### Clicks, drags and writes

- **Mouse and keys:** `SceneInteraction` (`editor-core`, `editor.pick`, like everything in this list but the panel and
  the renderer) handles them, over the `SceneViewState` (selection, gizmo mode, preview,
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
  `SceneTransformWriter().apply`, which writes only the changed `localPosition` / `localRotation`, plus the camera's
  `position` / `viewPointPosition`. The document change triggers the re-read above.
- **Undo:** `SceneFileEditor` is a `DocumentReferenceProvider`, so Undo in the scene view tab reaches these edits.
- **Look-through:** the camera selector in the toolbar renders from a camera entity instead of the orbit camera.
  Orbit, pan and zoom pause while it is active.

### Every write

The eye toggle, Rename Scene, the skybox chooser, gizmo drags, Drop and component add, edit and remove (`SceneComponentEdits`) and asset property edits (`AssetMetaEdits`, over `editor-core`'s
`AssetMetaEditor`; reference and face choices come from `projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/meta/AssetReferenceChoices.kt`) all go through `editSceneJson`
(`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/filetype/SceneDocumentWriter.kt`):

1. `DocumentTextEditor` (`projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/document/DocumentTextEditor.kt`)
   parses the document with `SceneJson` and admits it, lets the caller mutate the tree, admits the result and
   re-serializes it with `SceneJson().inStyleOf`, which keeps indentation, key order and number text.
2. Unless the document changed meanwhile, replace the text in a `WriteCommandAction` and save.
3. Publish `AbyssusSceneEdited.TOPIC` (the file). The Abyssus pane listens and refreshes itself; the writer knows no UI.

`HeadlessEditing` (`editor-core`, `editor.headless`) runs the same `DocumentTextEditor` on text with no IDE, so a
caller without the plugin validates and edits a scene, project or `meta.json` with byte-identical results and the same
refusals (spec `headless-scene-editing`).

Terrain regeneration and creation are the exception (binary heights, new files and folders cannot be a document edit):
they go through `AssetFileCommand` (`assetfiles/AssetFileCommand.kt`), described in `docs/ai/conventions.md`, with
`AssetTransactionEngine` holding the file logic (checks, ordered writes, rollback) apart from the platform so a test can
fail it between any two writes. No scene or project file is written that way.

### The `ecs` package

`core.ecs.ComponentRegistry` maps built-in and explicitly registered names to Ashley component classes. Keys may be
short or fully qualified; scene data never triggers arbitrary class loading. `EcsLoader` binds the wrapped
`ecs.entities` map through `JsonProcessor.ecsReader()` and Jackson. Unregistered or unbindable component entries
are carried as raw JSON in `EcsLoadingWarns`, with warnings. Entity ids are numeric; invalid references become
`NO_ENTITY`. `SceneEngine` and `SceneEntityIds` live in `core.ecs`; the look-at and camera synchronization systems
are in `projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/ecs/system`.

`RuntimeSceneLoader` combines `SceneLoader` and `EcsLoader`, installs those systems and returns a `SceneContext`.
The editor view reads JSON directly instead of updating an Ashley engine. `editor-core` owns `EcsWriter`, which
serializes registered components with Jackson into a wrapped `entities` map, and the built-in editing codecs.
It is a serialization API, not an IDE file writer; plugin document edits still go through `editSceneJson`.

### The scene document layer

The editor keeps the parsed JSON tree as its model (unknown keys, key order and number text stay the file's);
games and Play keep an Ashley `SceneEngine`. Both use Jackson binding to `core`'s component classes. In the editor,
readers use `SceneDocument` / `EntityView`; writers use `SceneEntityTree`, supporting both a flat entity map and
`ecs.entities`. The current runtime loader only enumerates `ecs.entities`; see the current-source review for
compatibility gaps.

**Component editing.** `ComponentEditor` supplies built-in kinds plus constructor-injected contributions. The plugin's
`ComponentSchemas` project service (`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/schema/ComponentSchemas.kt`)
chooses an editor per scene: admitted `physicsEnabled: true` adds three typed physics kinds, otherwise physics stays
read-only JSON. Project schema files and `componentSchemas` contributions do not extend it; custom game components
remain read-only and are preserved by unrelated edits. Settings changes refresh the editor and panel.
`reload()` publishes `ComponentSchemasListener.TOPIC`, but there is no schema merge or export implementation in
the current source set. See `projects/lib-core-editor/README.md`.

Per frame, on the EDT, `RayViewFeed` reads the renderer's current preview-applied content, camera, lights and animation
poses (`RayModelPoses`), freezes them into a job and offers it to a one-slot mailbox; a converter thread turns the newest
job into an immutable `RaySceneSnapshot` (`RaySceneSnapshots`, including CPU skin deformation) and offers it to the view's
`RayRenderScheduler`. The native worker keeps one frame in flight and one replaceable pending request per view and polls
completion, so the EDT never waits on a fence or a conversion. A completed linear RGBA/depth frame returns to the EDT,
where `RayFramePresenter` uploads it inside the safe canvas context and `SceneRenderer` composes the grid and overlays
against its depth. Scenes the backend cannot represent, a failed asset, or a device loss restore raster rendering at
once and surface a reason with a Retry button; no scene file is written by any of this. See `projects/lib-raytracing/README.md` for
toolchains, the opt-in device test commands (`-Dabyssus.metalTests=true`, `-Dabyssus.vulkanTests=true`), shading rules
and bounds, and `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md` for the view's states.

## Native document validation

The shared `AbyssusDocumentFormat` (`projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/format/AbyssusDocumentFormat.kt`)
is a constructor-built validator over parsed `JsonNode`s with no Swing, IntelliJ or GL dependencies. It checks the
`.abss` / `.scene` / `meta.json` header (`format: "abyssus"`, integral `formatVersion: 1`) and reserved scene fields
(`ecs.componentIdentifiers`, renderable `class`), returning a `FormatRejection` or null. Extension payloads are opaque.

`DocumentParsing` guards editor project/scene binding; `AssetMetaReader` guards editor metadata reads (both in
`editor-core`, `editor.document`, which also holds the editor-facing aliases of the `core.format` types).
`editSceneJson` validates current and candidate document text on the EDT, and `SceneFormatListener` guards formatting.
Rejections surface through `documentDisplayMessage` and localized `unsupportedFormat.*` messages.

`ProjectLoader`, `SceneLoader` and `AssetMetaLoader` validate parsed trees before binding.
Saved metadata and editor unsaved metadata both use `AssetMetaBinder` after admission; adding a typed asset kind
requires one settings-class registration in its constructor map. Runtime raw ECS loading
checks reserved fields before engine mutation; ECS writing and direct render binding use the same validator.
The editor retains source aliases in its `format` package. None of these checks writes or normalizes input files.

## Threading

- **Play:** `PlayState` and the play toolbar run on the EDT. A provider's callbacks are brought there with `invokeLater`.
  The panel reads `SceneSimulation.poses()` on the render thread each frame.
- **The EDT:** all tree, properties and editor UI. The scene view's frames also run on the EDT, the AWT thread,
  through a Swing `Timer`.
- **`Gdx.*`:** these statics are process-global. `GdxRuntime.withContext` installs a per-canvas shim
  (`Gdx.app`, `Gdx.graphics`, `Gdx.gl*`, `Gdx.files`) under a lock, then restores the previous values. All libGDX
  calls happen inside it.
- **Asset loading:** `AssetLoader.prepare` runs on a pool thread of the `AssetStorage` and does file IO and decoding, no GL. Building GPU
  objects happens on the render thread in `AssetStorage.update`, sliced per frame for big textures and for an HDR sky's
  environment passes (`HdrEnvironmentBuild`, which restores the framebuffer, viewport and state it changes).
  Cloud assets (bands and noise) are read and made in `prepare`; cloud shaders, 3D noise textures and the volumetric
  half-resolution targets are created on the render thread. `CloudViewState`, `CloudFrameBudget`, `SunOcclusion` and
  `SkyClock` are pure and run on the EDT with the frame.
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
  (`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/dto/ConfigFileReader.kt`). Add the extension to `ProjectLayout.ASSET_EXTENSIONS`.
- **A new built-in ECS component:** write a Jackson-bindable Ashley component, register it in
  `projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/ecs/ComponentRegistry.kt`, and add its editor
  kind/codec in `projects/lib-core-editor/src/main/kotlin/net/nevinsky/abyssus/lib/core/editor/components/BuiltInComponentKinds.kt`.
- **A game component:** provide a Jackson-bindable Ashley class, register it through `ComponentRegistry.register`
  or `registerAll` before `EcsLoader` loads the scene. A `PlayModule.components()` map lets the play host register
  the same classes. The editor keeps these components opaque; no schema annotation/export API currently exists.
- **`net.nevinsky.abyssus.componentSchemas` (IDE extension point, dynamic):** the resource bean remains registered,
  but `ComponentSchemas` currently does not consume contributed or project schema files.
- **`net.nevinsky.abyssus.sceneOverlay` (IDE extension point, interface `SceneOverlayProvider`):** another plugin
  draws lines and markers in every Scene view. `create(project, file)` makes one `SceneOverlay` per view, disposed
  with it. `draw(view, lines)` runs on the render thread inside `GdxRuntime.withContext`, twice a frame (depth-tested,
  then on top), and sees the shown poses and the scene's `ecs`. An overlay that throws is switched off for that view
  with one logged error. `isAvailable(project, file)` defaults to true, including for already compiled providers.
  Unavailable overlays supply no toolbar actions or draws. Built-in physics uses the scene's admitted native project
  switch and decodes colliders/constraints with typed defaults; its asset lookup uses that project's directory.
- **`net.nevinsky.abyssus.sceneSimulation` (IDE extension point, interface `SceneSimulationProvider`):** Play in the
  Scene view. With an available provider, the toolbar shows Play, Pause, Step and Stop. `start(request, listener)` gets the
  scene text, project folder and selection, and returns a `SceneSimulation`. Its `poses()` replace the authored
  placements as transient overrides (`ScenePreview.withPoses`); nothing is written. Any edit of the scene stops play
  first. Physics-only project edits avoid a render reload so they do not stop unrelated providers. `isAvailable(project, file)` defaults to true. Project settings and structural VFS events refresh provider selection and overlays
  on the EDT without reopening; settings are cached, with no parsing or IO in the draw path. Disabling physics stops
  its STARTING, PLAYING or PAUSED session once, drops late callbacks/poses, and restores authored content. Other
  available providers remain usable. See `PlayState` and `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md`.
- **A new asset kind drawn in the scene view:** an `AssetLoader` in `core` (built in `AssetLoading`), and a placement in
  `SceneContent`.

### Saved ray settings revisions

Scene DTOs live in `core.dto`: `SceneDto.rayTracing` binds to the typed `RayTracingDto`. Plugin
`SceneRenderParams` re-serializes it before decoding the four preferences with `SceneRaySettingsCodec`. Properties reads
the original JSON. These paths do not preserve the same malformed inputs; see the documentation audit for that gap.
`RayMaterialOverrides` reads pure JSON from each Render component, and `RaySceneSnapshots` resolves unique PBR
material IDs on the converter thread. Per-instance copied materials retain shared meshes and textures.
Malformed settings or unresolved optical overrides cause explicit ray conversion fallback.

The render-thread `RayViewFeed` detects changed settings/optical data before posting a frozen conversion job and
invalidates the existing scheduler's CPU publication immediately. Jobs carry the settings revision, so a delayed
converter cannot republish earlier settings. `RayRenderInput` freezes the target and query budget; only the serial
native worker chooses resolution and sample count. No preference edit requires a device probe, view opening or
session reconstruction. `RayWorkBudget` includes intersection retries and secondary direct-shadow work, and the
quality policy falls back when the saved budget cannot fit a sample at its minimum resolution.

`RayBackendService` rejects a scene request that uses non-default depths or transmission on a backend whose
`RayCapabilities.sceneOptics` is false, so saved settings are never silently ignored. Native kernels evaluate each batch's
samples independently, and each session's `RayFrameAccumulator` merges batches of the same key, epoch, revision and limits.
A frame that fails its per-path query bound or meets an unsupported dielectric medium is rejected whole at poll;
`RayQueuedSession` then accepts new work at once. In Properties, `SceneDetailsView` edits the four settings and
`EntityDetailsView` the per-material optics through `SceneRayEdits` (one `editSceneJson` command each); see the
scene view README.

The properties panel reads live view state through the read-only `editor.facts.SceneFacts` interface. Its generic
content type lets the contract stay independent of render code. `SceneRayControls` in the plugin root implements
content, selection and ray-mode queries, removes facts on editor disposal, and retains the separate ray commands.
Pure ray settings, optical overrides and diagnostic records live in `editor.ray`; their localized wording lives
in `ui.RayModeText`, and `filetype.SceneRayEdits` owns the IDE document-command adapter.
