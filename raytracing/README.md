# Ray tracing foundation and Metal feasibility slice

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
