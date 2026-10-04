# Architecture

## Modules

| Module | What | Depends on |
|---|---|---|
| root (`src/`) | The IntelliJ plugin (IC 2025.2.4+, since-build 252, Java 21, Kotlin 2.4.10) | `:core`, `:gdx-model`, Jackson, libGDX, LWJGL3-AWT |
| `core/` | Plain JVM library: asset folders and `meta.json`, the asset loading pipeline, and the models, terrains and skies it builds | `:gdx-model`, Jackson, libGDX |
| `gdx-model/` | Plain JVM library: libGDX model runtime with 32-bit mesh indices and an Assimp importer | libGDX, LWJGL Assimp |
| `raytracing/` | Plain JVM ray tracing: backend contracts, immutable scene snapshots and linear host frames, the scheduler and quality policy, and optional native Metal and Vulkan backends | Kotlin stdlib, LWJGL Vulkan and VMA |

`gdx-model` and `core` must not import IntelliJ or plugin code (see their READMEs). `core` is wired by constructors:
its composition root `AssetLoading` takes a `JsonProcessor`, an SLF4J `Logger`, an executor and the sky `ShaderSource`; in
the IDE the light application service `AbyssusCore` builds one (IDE log, IDE pool) and hands it to every scene view.
The plugin does not depend on Mundus.

`raytracing` is an optional GPU ray tracing renderer, off by default per view (switched from the **Ray Tracing**
switch in Abyssus Properties; the Scene View has no button for it). It owns nothing global: the plugin's `AbyssusCore` lazily builds one `RayBackendService` (a
`RayBackendSelector` over the Metal and Vulkan providers, and one serial native worker) and one converter thread, and
each Scene view registers a `RayViewRuntime` with it. Nothing native loads at startup: providers are only constructed and
probed when ray tracing is switched on, once per IDE session, and `-Dabyssus.raytracing.backend=off` never loads any.
`auto` tries Metal then Vulkan on macOS and Vulkan on Windows and Linux; a device loss marks the backend for a re-probe
on the next explicit Retry. The backend owns one device and queue, and each view gets its own session (scene structures,
command pools, output images) that is disposed on the owner worker.

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
- **GL safety:** `GuardedGLCanvas` refuses GL until the canvas has been on screen with a non-zero size for 250 ms.
  When disposed while hidden, it drops the context without making it current, because on macOS that would abort the
  JVM.
- **Ray tracing:** the EDT only copies state, offers requests and uploads completed frames (inside the safe canvas
  context). Scene conversion and skin deformation run on one converter thread fed by a latest-wins mailbox; native
  preparation, submission, completion polling and disposal run on the ray service's serial worker. Mode transitions
  publish to the EDT; a late result after hide, close, retry or a replaced context is discarded by its revision.
- **Off the EDT:** properties panel reads (`readAssetState`), `AssetReadCache` reads in the background tree builder,
  and asset `prepare`.

## Extension points

- **A new asset file format:** implement `ConfigFileReader` and return it from `AssetReadCache.readerFor`
  (`src/main/kotlin/net/nevinsky/abyssus/dto/ConfigFileReader.kt`). Add the extension to `ProjectLayout.ASSET_EXTENSIONS`.
- **A new ECS component:** write a `ComponentCodec` and add it to `ComponentCodecs`
  (`src/main/kotlin/net/nevinsky/abyssus/ecs/scene/ComponentCodecs.kt`).
- **A new asset kind drawn in the scene view:** an `AssetLoader` in `core` (built in `AssetLoading`), and a placement in
  `SceneContent`.
