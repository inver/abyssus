# Ray tracing foundation and Metal/Vulkan feasibility slices

Plain JVM module without IntelliJ, Swing or libGDX dependencies. Its internal backend
protocol has no extension point or binary compatibility promise. A provider probes
on the backend's serial worker and returns a backend or an unavailable reason code
(the plugin localizes it). Construction loads no native libraries. Expected native
load/init errors become unavailability; cancellation still propagates.

The application composition root will inject one provider/backend. The Metal backend
owns one device, queue and pipeline; each view owns an independent session containing
its geometry, instance structures and readback buffers. All backend/session methods
run on the same owner worker. Closing a view does not dispose another view's session.
The module is a plugin dependency. The developer-only feasibility preview is wired
into the Scene view; project asset/material rendering remains unimplemented.

`RayFrame` copies linear RGBA floats and depth on input and output. HDR values above
1 survive; exposure/tone/sRGB conversion belongs to presentation. Buffers are row-major
from the bottom-left. Depth uses GL window depth in [0,1], with sky misses at 1.

## Metal build and verification

Building the module's resources on macOS invokes Xcode's `metal`, `metallib` and
`clang++`. Install Xcode, select the intended toolchain with `xcode-select`/`DEVELOPER_DIR`,
and install its Metal compiler component (`xcodebuild -downloadComponent MetalToolchain`)
if needed. The bridge uses the Gradle JVM's JNI headers. The deployment target is macOS
13.0 and the shader language is Metal 3.0. No shader compilation happens at runtime.

```sh
./gradlew :raytracing:compileKotlin :raytracing:test
./gradlew :raytracing:buildMetalNative -Pabyssus.metalArch=arm64
./gradlew :raytracing:buildMetalNative -Pabyssus.metalArch=x86_64
./gradlew :raytracing:verifyNativePackaging
./gradlew :raytracing:test -Dabyssus.metalTests=true
./gradlew :raytracing:test --tests '*MetalRayBackendTimingTest' -Dabyssus.metalTests=true -Dabyssus.metalTimingTests=true
```

The default native architecture is the build JVM's architecture. Cross-compiling
x86_64 on arm64 does not verify loading or GPU behavior on an Intel Mac. Resources
are packaged under `native/macos-<architecture>/` as `libabyssus_ray.dylib` and
`slice.metallib`. Loading is deferred until a probe. Extracted library code shares
one content-addressed path per JVM/classloader; session/device state is constructor-owned.
The JNI bridge has no registered Objective-C classes or process-global device state.

`verifyNativePackaging` currently verifies the Metal jar, not the final plugin zip or
Vulkan dependencies. It loads the actual library/shader from the jar, probes the GPU,
creates two sessions and renders from the second after disposing the first. It requires
a compatible Mac. Ordinary tests skip GPU cases unless `abyssus.metalTests=true`.

## Slice scope and bounds

The feasibility representation accepts triangle meshes with 32-bit indices, affine
instance transforms, flat colors, one directional light and perfect mirrors. Static
mesh acceleration structures survive transform submissions. Instances rebuild a
top-level structure. Camera rays produce projected depth; visibility rays shadow
only direct light; a reflection hit receives direct shading and misses use the fixed
slice background. Only one native frame may be submitted per session. `submit` and
`poll` never wait for the GPU; geometry preparation and disposal currently wait on
the owner worker. Queued request replacement, stale-frame rejection and bounded
failure/disposal for the complete editor remain pending the scheduler/lifecycle tasks.
The common `RayQueuedSession` already bounds pending requests, rejects incompatible
structural/resize results and propagates injected device loss to every owned session.

Initial caps: 128 meshes/instances, 32 MiB of input geometry, 4096 per dimension,
4,194,304 pixels per frame, and native geometry builds bounded by the smaller of
512 MiB and one quarter of the device's recommended working set. These are slice
bounds; complete per-view/application budgets remain part of the asset/resource work.

Full materials/textures, terrain splats, roughness, cutouts/blending, sky/fog, animation,
Vulkan and complete editor integration are not implemented. The staged conformance
kit runs 11 feasibility/lifecycle cases on the fake and opt-in Metal backends;
full shading references remain tasks 3.1–3.5. The optional
30-second timing test reports native submission/readback throughput; it does not
measure GL presentation, EDT upload cost or input-to-present latency and cannot pass
the mandatory runIde gate.

## Experimental GL presentation

Use a copy of the fixture project, and run:

```sh
./gradlew runIde -PideProject=/path/to/copy -PrayExperiment=true
./gradlew :test --tests '*RayFrameCompositionGlTest' -Dabyssus.glTests=true
./gradlew :test --tests '*RayFeasibilityPresentationTimingGlTest' -Dabyssus.glTests=true -Dabyssus.rayTimingTests=true
```

Open a scene's Scene view and enable **Metal feasibility preview**. This developer
control shows a synthetic moving occluder, floor and mirror, rather than project
content. Left-drag orbits, right-drag pans, and the wheel zooms. Disable it to return
to the scene's existing camera/selection. Scene transform controls are disabled
during the experiment. The property is off by default; raster startup loads no JNI.

The experiment uses fixed 640x360 internal frames. For the gate, size the canvas to
1280x720 framebuffer pixels (account for HiDPI). Native probe/preparation/submission,
polling and disposal run on one dedicated worker; EDT only offers immutable requests
and draws completed frames. One request is in flight and one pending request is
replaceable. The GL pass uploads linear float color and projected depth and restores
the caller's GL state. It does not yet implement scene grid/overlay/fog composition.

Every 30 seconds, `idea.log` records distinct-frame throughput, p95 CPU offer and
upload/draw cost, and offer-to-draw latency. These measurements exclude swap and
physical input/display latency. The opt-in 30-second GL timing test uses the same
preview on a 1280x720 canvas, continuously moving its camera. Neither automated
timing test completes the manual runIde gate. Hide/close stops the native worker;
GL resources are released only with the canvas context current, or abandoned with
the old context. This temporary experiment owns its own device, rather than the
application service required for the completed feature.

## Vulkan backend (Windows, Linux; macOS through MoltenVK)

`VulkanRayBackendFactory` is the Vulkan counterpart of the Metal provider, built on LWJGL
(`lwjgl-vulkan`, `lwjgl-vma`) with compute-shader ray queries. It is headless: no GLFW, surface or
swapchain, and the instance enables no window extensions. Nothing Vulkan-related loads until `probe`.
`lwjgl-vulkan` publishes natives only for macOS (MoltenVK); Windows and Linux use the system loader
(`vulkan-1.dll` / `libvulkan.so.1`). A missing loader is reported as `RUNTIME_NOT_FOUND`, never thrown.

A device qualifies from measured features, never from its name: Vulkan 1.2, `bufferDeviceAddress`,
`timelineSemaphore`, descriptor indexing, `VK_KHR_acceleration_structure`, `VK_KHR_deferred_host_operations`,
`VK_KHR_ray_query`, a compute queue, and storage-image support for `R16G16B16A16_SFLOAT` (color) and
`R32_SFLOAT` (depth). The per-device reason is in the `Unavailable.detail` text.

Memory comes from VMA. Bottom-level structures are built once per mesh and survive transform-only
submissions; the top-level structure is rebuilt in each frame's command buffer. Frames are split into
dispatches of at most 2^19 rays, each in its own command buffer, so no single submission approaches a
driver timeout. Completion is polled with a fence from the owner worker; `submit` and `poll` never wait.
Geometry upload and session disposal do wait on the owner worker. After `VK_ERROR_DEVICE_LOST` teardown skips
every wait. A loss injected through `RayDeviceHealth` leaves the device healthy, so teardown still waits.

The shader is `src/main/glsl/slice.comp`, compiled to SPIR-V at build time by the `compileSpirv` task
into `native/vulkan/slice.spv`. No `lwjgl-shaderc` is packaged. The task uses `glslangValidator` or `glslc`
from `PATH` (or `-Pabyssus.glslc=/path/to/tool`). Without either it warns and ships no SPIR-V, and the backend
then reports `INITIALIZATION_FAILED`; `-Pabyssus.requireShaders=true` (used by the release and CI builds) turns
that into a build error.

```sh
./gradlew :raytracing:test                                    # Vulkan cases skip without a device flag
./gradlew :raytracing:test --tests '*VulkanRayBackendTest' -Dabyssus.vulkanTests=true
./gradlew :raytracing:test -Dabyssus.vulkanTests=true -Dabyssus.raytracing.validation=true
./gradlew :raytracing:verifyVulkanPackaging                   # jar-based; add -Dabyssus.vulkanTests=true to render
./gradlew :raytracing:verifyNativePackaging                   # Vulkan, plus Metal on a Mac
```

`-Dabyssus.raytracing.validation=true` enables the Khronos validation layer (and `VK_EXT_debug_utils`) and
makes `VulkanRayBackendTest` fail on any validation error. It is a developer flag and is never on by default.
`verifyVulkanPackaging` checks the SPIR-V in the jar, that no shaderc is on the runtime classpath, the
`lwjgl-vma` natives for every target, MoltenVK for macOS only, and that a probe without a loader returns
`RUNTIME_NOT_FOUND` (one JVM per test class, because LWJGL's library choice is process-global). Metal
packaging moved to `verifyMetalPackaging`; `verifyNativePackaging` runs both where they apply.

On a machine without a GPU, Mesa's software driver (lavapipe, `mesa-vulkan-drivers`) exposes ray queries and
runs the whole suite: `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json`.
