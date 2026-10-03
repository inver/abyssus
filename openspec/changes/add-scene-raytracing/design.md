# Design

## Context

See proposal.md for motivation and the delta specs for the behavior contract. SceneViewPanel owns an OpenGL 3.2 core AWT canvas driven by a Swing timer. SceneRenderer renders sky, grid, terrain, models and overlays. ScenePreview supplies in-progress transforms; SceneModels advances each entity's animation. Picking and gizmo calculations already use CPU data independently of rendering.

ModelLoader prepares Assimp ModelData and decoded images before building GPU resources; PreparedModel disposes decoded images during upload/build. PreparedTerrain already computes triangle arrays, but also disposes images after upload. A ray renderer cannot recover these assets by sharing OpenGL handles with Metal/Vulkan. CPU geometry and texture retention must be deliberately introduced with bounded ownership.

The archived add-scene-shadows change built raster shadow maps and spot cone controls. The `scene-shadows` main spec requires animation/preview updates, independent light contributions and cutout/blended shadow rules. This design keeps those behaviors. The environment-lighting main spec currently mandates sky-only specular reflections, so this change explicitly modifies that requirement for the optional mode.

## Goals / Non-Goals

**Goals:** Preserve the editor's existing surface and interaction model; add an isolated GPU rendering service using immutable snapshots and bounded work; make unsupported hardware and recoverable failures ordinary mode transitions.

**Non-Goals:** A new native viewport window, shared GL/Metal/Vulkan objects, a universal CPU tracer, a replacement asset importer, a global native/Gdx singleton, or promises of identical pixels across different rendering methods. The initial mode uses direct lighting plus one scene-reflection bounce, rather than general multi-bounce transport.

## Decisions

### 1. Offscreen native rendering, existing GL presentation

Introduce a proposed `raytracing` plain JVM module for API, scene representation, scheduling and backend implementations. Use LWJGL Vulkan bindings on Windows/Linux and a small constructor-owned JNI Objective-C++ Metal bridge on macOS, both behind the backend interface of decision 7. Neither the API nor native bridge owns IntelliJ/Swing objects. The plugin creates one backend service per application through constructors (held by the `AbyssusCore` composition root). It owns one native device per backend, shared by every open view; each view gets its own session with command pools, scene structures and output images (decision 8). Keep core and gdx-model reusable; core's existing no-singletons rule remains intact.

Native backends render offscreen color and primary-hit depth, then publish a completed frame in host memory. On the AWT render thread, SceneRenderer uploads a bounded completed frame and presents its color/depth on the existing GL canvas; grid, markers, highlights and gizmos then use the existing camera and occlusion conventions. Encode depth in the same projection/range convention as the GL frame; sky misses use far depth. Preserve the existing order of grid versus content through a dedicated composition pass rather than drawing a fullscreen image over an already-rendered grid. Apply existing fog to world-space hit distances and preserve sky orientation/exposure.

This avoids replacing the canvas and duplicating input, HiDPI and macOS surface-lifecycle handling. CPU transfer introduces latency/bandwidth costs. Start with asynchronous staging/readback and adaptive resolution, not a synchronous GPU wait on the EDT. Direct GPU interop and an entirely native canvas were considered but add platform-dependent lifetime and surface work before the feature can be evaluated.

### 2. Platform backend contracts and a mandatory feasibility gate

Use Metal acceleration structures/intersection support on macOS and Vulkan acceleration structures plus ray queries on Windows/Linux (the exact Vulkan requirements are in decision 8). Probe actual device features, native library loadability, memory/format support and shader creation rather than GPU brand strings. Compatible means this concrete renderer can initialize and meet its declared resource requirements; operating-system support does not mean every GPU qualifies.

Metal is a direct macOS route; Vulkan has JVM bindings and a common Windows/Linux implementation. Do not assume the packaged MoltenVK version exposes usable ray tracing: a single Vulkan implementation across all platforms is an alternative only after proving the required extension/shader/presentation path. Primary API references: [Metal acceleration structures](https://developer.apple.com/documentation/metal/ray-tracing-with-acceleration-structures), [Vulkan ray tracing](https://docs.vulkan.org/guide/latest/extensions/ray_tracing.html), [LWJGL Vulkan](https://javadoc.lwjgl.org/org/lwjgl/vulkan/package-summary.html).

Before full integration, create the smallest vertical slice with an occluder, a reflective surface, a moving instance and completed-frame upload in runIde on each platform. Record device, driver, OS, plugin/runtime versions, color/depth correctness, transfer cost, GPU time and input-to-present latency. Proposed acceptance target: at least 30 presented frames/second and 95th-percentile preview latency below 100 ms over a 30-second continuous drag at 1280x720 output, with internal resolution allowed to drop to 640x360, on a documented compatible reference device per OS. EDT frame submission/upload should stay within an 8 ms budget and never wait for native fences. This is a reference-scene target, not a guarantee for arbitrary scene complexity.

The gate includes native build/load/packaging on macOS arm64 and x86_64 and Windows/Linux x86_64, matching the repository's packaged targets. On Windows and Linux there is no Vulkan native to package: `lwjgl-vulkan` publishes natives only for macOS (MoltenVK), and the backend loads the system's `vulkan-1.dll` / `libvulkan.so.1`. Packaging for those targets therefore means the Java bindings, the `lwjgl-vma` natives and the prebuilt SPIR-V, plus a clean "runtime not found" result on machines without a loader. Only compatible devices enable the mode; Intel Macs without the required features use raster. If the gate fails, record measured causes and revise the design/tasks before proceeding; do not quietly remove a platform or reinterpret the feature as a still preview.

### 3. Shared immutable scene snapshots

Proposed platform-free classes `RaySceneSnapshot`, `RayMaterial`, `RayLight`, `RaySceneDiff`, `RayFrameKey`, `RayQualityPolicy` and `RayModeState` hold data and pure decisions. Include instance ids, transforms, camera lens/matrices, geometry/texture asset revisions, material parameters, animation pose, light selection, ambient/HDR state and fog. Derive content from the same SceneContent, LightSet and ScenePreview as raster rendering, including existing component defaults and local-as-world transform semantics. Do not separately parse scene JSON or write render preferences into it.

Retain immutable CPU arrays and decoded texture bytes through an optional, reference-counted asset companion requested by the ray mode. Capture during preparation before Pixmaps are disposed, with no GL calls on preparation workers. Validate 32-bit indices, mesh-part offsets, vertex channels, tangents, texture orientation, samplers, color space, terrain layer blending and skin weights against the existing runtime. Share immutable asset bytes between repeated instances and views in the same project with constructor-owned lifetime; release them when no ray view needs them. Cancel stale project/asset preparations.

Use shared per-asset bottom-level acceleration structures for static meshes and per-instance top-level transforms. Updates to an instance transform refit/update the top level. Animated node transforms and skin deformation must match the raster animation pose; copy pose data on the render thread, then calculate deformed CPU vertices on a worker and update the affected geometry structures. No ModelInstance, Pixmap or mutable Gdx object crosses worker boundaries. Avoid GPU-to-CPU mesh/texture readbacks each frame. If a valid displayed asset exceeds supported representation/resource limits, fall back for the view with a reason instead of hiding it silently.

### 4. Direct shading and one reflection bounce

The native renderer traces camera rays through the displayed geometry and evaluates default materials, PBR materials and terrain splats using existing textures and lighting parameters. Use the existing deterministic LightSet limits/order initially, so switching modes does not silently pick different lights. Cast visibility rays for selected directional, point and spot contributions, honoring range/cone attenuation. Exclude editor decorations entirely from acceleration structures. Alpha-test intersection rejection must use the same UV/alpha threshold as raster rendering.

For PBR primary hits, sample one reflection direction using roughness, apply the existing metallic/roughness energy weighting, and shade the secondary hit with direct lights, emission and existing ambient/HDR diffuse lighting. Secondary surfaces do not recursively trace scene reflections; their existing environment specular is the terminal approximation. Sample the appropriate current sky/background on misses, including HDR exposure and procedural-sky direction. Default materials and terrain retain their existing diffuse lighting behavior and do not gain new reflective properties. Replace the primary PBR environment specular with the ray result to avoid double counting; preserve existing diffuse HDR replacement of ambient color.

Alpha-blended geometry remains visible to primary camera rays with ordered alpha compositing and shadowed direct shading, but is excluded from shadow/reflection intersection masks. This preserves current shadow rules without promising refraction or reflected transparent objects. Bound transparent layers and other resource counts; if limits cannot represent a displayed scene correctly, use explicit whole-view raster fallback. Define tolerance-based image tests for both backends rather than bitwise image equality.

### 5. Live scheduling and bounded quality

EDT snapshots the current camera, preview transform and animation pose, and offers the newest request without blocking. A backend-owned serial worker performs native preparation, submissions and fence polling. Maintain at most one submitted render and one replaceable pending request per view. Completed frames carry scene generation, pose/transform revision, camera, dimensions and context generation.

Present only compatible frames. A modestly older camera/pose/preview frame may be shown to keep motion continuous, with overlays using that frame's geometry/camera metadata; asset deletion, project change, resize or switching the active camera invalidate incompatible results. Ordinary orbit/pan updates reset accumulation but do not require exact current-camera equality for presentation. Never wait for exact equality with a continuously advancing camera or animation revision, which would starve presentation. Use raster rendering for the current state until the first suitable native frame arrives or while a structural rebuild cannot supply a compatible frame. Picking remains based on current CPU preview data; enforce the measured latency bound so display and interaction do not drift excessively.

During interaction use a small fixed ray/sample budget and reduced internal resolution; adapt from recent timings within fixed minimum/maximum dimensions and memory budgets. Increase samples/temporal accumulation only when scene, camera and pose remain stable. Any change resets affected history; continually animated scenes keep live low-sample rendering. Quality adaptation, accumulation invalidation, request replacement and compatibility decisions are headless-testable. Keep jittered render camera separate from the unjittered picking/gizmo camera.

### 6. UI state, failures and lifetime

The per-view state is Off, Checking, Preparing, Active, Unavailable or Failed. Off is the default. The localized toolbar shows the requested/active state and an inline preparation or failure reason. Missing features disable the toggle; recoverable failure clears activation and restores raster immediately. An explicit retry is allowed after failure; do not retry failing native initialization every frame. Keep camera/selection and drag state across transitions. No persisted project preference is introduced.

Preparation/native GPU work runs on workers. Swing controls, state publication, animation pose capture and completed-frame GL upload/composition run on EDT. Every libGDX/GL call stays inside GdxRuntime.withContext and only while GuardedGLCanvas.glSafe. Cancellation uses runCatchingKeepingCancellation. Hidden views stop scheduling; closing cancels pending work and disposes native resources on their owner worker after outstanding commands complete. Scene/context generation prevents late publication into a disposed or replaced view. GL resources follow the existing safe canvas-disposal rules. Bound waiting during shutdown without blocking EDT; test delayed completions and several simultaneous views.

### 7. Internal backend interface, selection and conformance kit

Backends implement one internal, constructor-wired interface in the `raytracing` module:
- `RayBackendProvider.probe(): RayCapability`: available or unavailable with a localized reason. Never throws, and
  loads native code only when called.
- `RayBackend.openSession(viewId, limits): RaySession`
- `RaySession.submit(request)`, `RaySession.poll(): RayFrame?` and `RaySession.dispose()`.
- `RayBackend.dispose()`.

`RayFrame` carries linear RGBA, primary-hit depth and the request's version metadata (decision 5). The interface is
internal: it has no IntelliJ extension point and makes no binary-compatibility promise. A new backend is added in the
module and registered in the provider list.

Selection is a pure, headless-tested `RayBackendSelector`:
- The `abyssus.raytracing.backend` system property is read once at startup. `off` loads no native code.
- A forced backend is probed alone, and its reason is reported if it is unusable.
- `auto` probes in OS order: macOS tries Metal, then Vulkan (MoltenVK only when it reports every required feature);
  Windows and Linux try Vulkan.
- Probes run once per IDE session on a worker thread. A device loss marks the backend for re-probing on the next
  explicit retry.

`RayBackendConformanceKit` is an abstract test suite each backend's test class extends. It covers:
- probe behavior without a device;
- the reference scenes of tasks 3.1–3.5, with per-pixel and aggregate tolerances against shared reference images;
- depth convention;
- instance motion;
- request replacement and stale-frame rejection;
- dispose with work in flight, and with several sessions sharing one device;
- device-loss injection, through a test hook that makes the next poll report a lost device.

A fake in-memory backend passes the kit headlessly. The real backends run it as opt-in device tests.
*Alternative:* a public IntelliJ extension point. Rejected for now because of the API surface and compatibility cost.

### 8. Vulkan through LWJGL

The Vulkan backend follows the structure of vulkanbook's ray-tracing sample (`lwjglgamedev/vulkanbook`, chapter 21:
`Instance`, `PhysDevice`, `Device`, `MemAlloc`, `BLAS`/`TLAS`), with these differences for an IDE plugin.

**Headless.**
- No GLFW, no `VK_KHR_surface` and no swapchain.
- The instance enables no window extensions. On macOS it enables `VK_KHR_portability_enumeration` (with the
  portability flag) only to probe MoltenVK.
- Output goes to storage images that are copied to host-visible buffers.

**Requirements**, all probed through `vkGetPhysicalDeviceFeatures2` / extension enumeration, never by vendor name:
- Vulkan 1.2 or later, with `bufferDeviceAddress`, `descriptorIndexing` (`runtimeDescriptorArray`,
  `shaderSampledImageArrayNonUniformIndexing`) and `timelineSemaphore`.
- `VK_KHR_acceleration_structure` and `VK_KHR_deferred_host_operations`.
- `VK_KHR_ray_query` (`rayQuery`).
- A compute queue, and storage-image support for the chosen formats.
vulkanbook uses `VK_KHR_ray_tracing_pipeline` with shader binding tables. This design uses ray queries in compute
shaders instead: one-bounce reflections and shadow rays don't need a pipeline or binding tables, and ray queries
appear on more devices. The pipeline path remains a later option behind the same interface.

**Memory and shaders.**
- Memory comes from `lwjgl-vma` (vulkanbook chapter 10), pinned to the project's `lwjglVersion`, which must also be
  the core version used by `lwjgl3-awt`.
- GLSL is compiled to SPIR-V at build time (a Gradle task invoking `glslangValidator` / `glslc`) and shipped as
  resources. `lwjgl-shaderc` is not packaged.
- Acceleration-structure and scratch buffers use buffer device addresses (vulkanbook chapter 18).
- BLAS builds use device-local memory; TLAS instance buffers are rewritten per transform change.

**Formats.**
- Color is written to an `R16G16B16A16_SFLOAT` storage image (linear; sRGB formats are rarely valid storage images).
- Primary-hit depth is written to an `R32_SFLOAT` storage image, already in the GL projection's depth convention.
- Composition uploads both and writes `gl_FragDepth`. The exposure, tone and sRGB encoding match the raster path.

**Threading and synchronization.**
- One worker thread per backend owns the device and every queue submission (vulkanbook chapter 20: `VkQueue` access
  is externally synchronized).
- Each session has its own command pool.
- Completion is observed by polling timeline semaphores or fences from the worker. The EDT never waits on Vulkan.
- LWJGL `MemoryStack` use stays thread-local.

**Device loss** (vulkanbook appendix 01).
- Each dispatch is bounded to a fixed ray count, so that no submission can approach the Windows driver timeout (about
  2 s). A frame is split into several dispatches if needed.
- `VK_ERROR_DEVICE_LOST` from any call marks the device lost, fails every session (recoverable, decision 6) and
  destroys the device on the worker without further waits.
- Validation layers and debug utils are enabled only by the developer flag `-Dabyssus.raytracing.validation=true`
  and in the opt-in backend tests. They are never enabled in a shipped default.

## Risks / Trade-offs

- Host frame transfer may dominate latency → mandatory measured gate, asynchronous staging, bounded resolution and no EDT fence waits.
- Two backends can differ in shading and alpha handling → shared material math/reference vectors, common synthetic scenes and image tolerances on both native paths.
- CPU retention and acceleration structures increase memory → explicit ownership, shared static geometry, fixed budgets and whole-view fallback rather than silent omission.
- Skinned updates can be expensive → per-pose dirty updates and timing-driven quality policy; animation correctness remains required.
- Native packaging introduces toolchain/driver variation → deterministic platform builds, plugin-zip load smoke tests and explicit capability probing.
- Windows/Linux availability depends on a system Vulkan loader and driver the plugin cannot ship → a clear "runtime not found" reason, and no crash when the loader is absent (task 5.4 checks a loader-less machine).
- A long dispatch or driver fault can reset the GPU → bounded dispatches, device-loss handling, and the kit's loss-injection tests.
- `design-review-refactor` restructures `SceneRenderer` → compose ray frames through its view state and per-frame snapshot; whichever change lands second rebases, with no spec conflict between them.
- Raster shadows and water evolve concurrently → re-read their deltas before implementation, amend contradictory deltas in a named task, and keep water outside the initial ray scene contract.

## Migration Plan

No scene/project/asset migration. Add the packaged optional backends with the mode off by default. Verify ordinary plugin startup and raster use without loading ray libraries. Ship only after the three-platform gate and manual matrix pass. Rolling back removes the mode and native resources without changing project data.
