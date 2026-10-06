# Ray tracing

Plain JVM module without IntelliJ, Swing or libGDX dependencies. Its internal backend
protocol has no extension point or binary compatibility promise. A provider probes
on the backend's serial worker and returns a backend or an unavailable reason code
(the plugin localizes it). Construction loads no native libraries. Expected native
load/init errors become unavailability; cancellation still propagates.

The plugin's application service lazily selects and injects one backend through `RayBackendService`. The Metal backend
owns one device, queue and pipeline; each view owns an independent session containing
its geometry, instance structures and readback buffers. All backend/session methods
run on the same owner worker. Closing a view does not dispose another view's session.
The module is a plugin dependency. The Scene view renders project models, terrain, materials, lights, sky and fog
through immutable CPU snapshots. Ray Tracing is enabled from scene Properties, per view and off by default.
See [scene view integration](../src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md) for conversion, lifecycle,
GL presentation, saved settings and raster fallback. A separate synthetic feasibility preview remains available
under the developer experiment flag.

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

`verifyMetalPackaging` verifies the Metal jar, not the final plugin zip. It loads
the actual library/shader from the jar, probes the GPU,
creates two sessions and renders from the second after disposing the first. It requires
a compatible Mac. Ordinary tests skip GPU cases unless `abyssus.metalTests=true`.

## Synthetic feasibility slice

The feasibility representation accepts triangle meshes with 32-bit indices, affine
instance transforms, flat colors, one directional light and perfect mirrors. Static
mesh acceleration structures survive transform submissions. Instances rebuild a
top-level structure. Camera rays produce projected depth; visibility rays shadow
only direct light; a reflection hit receives direct shading and misses use the fixed
slice background. Only one native frame may be submitted per session. `submit` and
`poll` never wait for the GPU; geometry preparation and disposal currently wait on
the owner worker. The complete scene renderer uses the scheduler and quality policy documented below.
The common `RayQueuedSession` bounds pending requests, rejects incompatible
structural/resize results and propagates injected device loss to every owned session.

Initial caps: 128 meshes/instances, 32 MiB of input geometry, 4096 per dimension,
4,194,304 pixels per frame, and native geometry builds bounded by the smaller of
512 MiB and one quarter of the device's recommended working set. These are synthetic slice
bounds; project scene capacities and snapshot budgets are documented below and in `core/README.md`.

The staged conformance kit runs the feasibility/lifecycle cases on the fake and opt-in Metal backends. The optional
30-second timing test reports native submission/readback throughput; it does not
measure GL presentation, EDT upload cost or input-to-present latency and cannot pass
the mandatory runIde gate.

## Scene shading and optical paths

`RaySceneRequest` carries an immutable `RaySceneSnapshot`: meshes, instances, materials, textures, lights, environment
and fog. The Metal backend renders it with the `rayScene` kernel and the Vulkan backend with the equivalent compute
shader `src/main/glsl/scene.comp`; both read the same payload (`RaySceneEncoding`). The fake backend renders the same
semantics on the CPU (`RaySceneReferenceRenderer`, test-only). Each documented rule below is pinned by a case in
`RayBackendConformanceKit` (run by `FakeRayBackendTest` and the opt-in `MetalRayBackendTest` and `VulkanRayBackendTest`)
or `RayMaterialTest`.

- **Materials and lights.** Default, PBR and terrain-splat materials use the raster shaders' linear conventions, and
  point/spot lights keep their range and cone attenuation (`defaultSceneMaterialMatchesRasterReference`,
  `pbrSceneMaterialMatchesRasterReference`, `terrainSplatSceneMatchesOrderedRasterMixes`,
  `pointAndSpotSceneLightsKeepRangeAndConeAttenuation`, `RayMaterialTest`, `RayLightTest`).
- **Shadows.** Every light that casts shadows traces its own visibility ray, so one blocked light never removes
  ambient, emission or another light (`oneShadowKeepsAmbientEmissionAndTheOtherLight`). Models and terrain cast onto
  each other (`terrainReceivesModelShadowsAndRidgesShadowTerrain`); point and spot lights are covered by
  `pointLightShadowCoversOnlyTheOccludedFloor` and `spotConeLimitsLightAndShadowToItsCone`.
- **Cutouts.** An alpha-test hole (`alpha * opacity` below the cutoff) is invisible to shadow, reflection and primary
  rays (`cutoutHolesStayOpenInShadows`). Rays pass through `RAY_CUTOUT_HOLE_DEPTH` (8) holes in a row; the next one
  counts as solid (`cutoutHolesAreSkippedUpToAFixedDepthAndThenCountAsSolid`).
- **Reflections.** A PBR surface traces GGX-sampled reflection rays (roughness floor 0.04, a per-pixel/sample/event
  hash, the mirror direction when the sample points below the surface) up to the request's `maxReflectionBounces`
  (0–16, default 1). The rays see opaque and alpha-tested models and terrain, including geometry outside the camera
  image (`smoothReflectionsIncludeOffscreenModelsAndSkyMisses`, `reflectionsIncludeOffscreenTerrainAndRoughnessChangesTheResult`).
  When the limit is reached, the last hit gets direct light, emission and ambient, with the ambient colour as its
  terminal specular, and traces nothing further (`reflectedPbrSurfacesAreShadedWithoutAFurtherBounce`,
  `secondMirrorRevealsTheHiddenObjectOnlyFromReflectionDepthTwo`); limit 0 uses that approximation on the primary hit
  (`configurableReflectionZeroAndOnePreserveBoundedEnvironmentAndSceneHits`). A miss shows the sky. The traced radiance
  replaces the ambient colour in the PBR specular term only; diffuse ambient is unchanged. Default and terrain materials
  gain no reflections.
- **Glass.** A PBR material with `transmission` above 0 (from a scene-instance override, never inferred from alpha) is a
  dielectric with `ior` (1–3, default 1.5) against air. Each event picks one continuation, reflected or refracted (Snell),
  by Fresnel weight with probability-compensated throughput, so a uniform environment keeps its energy
  (`fresnelSplitConservesEnergyInAUniformEnvironment`). Every surface crossing spends one of `maxRefractionBounces`
  (0–16, default 0) and every reflection, including total internal reflection, one of `maxReflectionBounces`
  (`closedSlabRefractionAtLimitsZeroOneAndTwo`, `totalInternalReflectionReflectsInsteadOfTransmitting`,
  `mixedReflectionAndTransmissionPathsMatchTheReferenceAtEveryLimitPair`). An exhausted continuation shows the sky in its
  direction. Refraction limit 0 shades the material as ordinary opaque PBR. Supported geometry: per entity, one
  connected, closed, consistently outward-wound manifold whose parts all carry the same transmission material on opaque
  PBR (`RayOpticalEligibility`, checked on the CPU before upload). The kernels track air or one solid: a camera inside
  glass is inferred from the first backface; entering a second solid, or leaving into nothing while inside, fails the
  whole frame (alpha -2), as does a path that would exceed its query bound (alpha -1); `requireNativeOpticalFrame`
  turns both into exceptions at poll (`unsupportedTransmissionIsAnExplicitFailure`). Glass casts straight opaque shadows:
  no coloured transmission shadows or caustics (`transmissiveSolidsCastStraightOpaqueShadows`). A backend whose
  `RayCapabilities.sceneOptics` is false rejects requests with non-default depths or transmission (`requireOptics`).
- **Sky.** A miss, primary or reflected, shows the equirectangular environment texture times its intensity (linear
  HDR values above 1 survive), oriented like the raster sky: the centre column faces -Z, the top row is +Y, and
  `rotation` turns it about +Y in degrees. With no texture, a miss shows the background colour
  (`skyMissesShowTheEnvironmentWithExposureAndOrientation`, `disabledOrMissingSkyShowsTheBackground`,
  `RayMaterialTest`). An HDR sky (`RayEnvironment.hdr`) is tone mapped (ACES fit, gamma 1/2.2) when a camera ray sees it,
  like the raster sky, while reflections of it stay linear radiance (`hdrSkyIsToneMappedWhenSeenDirectlyAndLinearWhenReflected`).
  The plugin produces the texture: `core`'s `RaySkySnapshot` for HDR and cube skies, and a GL bake of a procedural sky.
  HDR diffuse lighting arrives as `RayEnvironment.ambientCube` (the sky's six axis colours, blended by the squared normal
  exactly like the raster model shaders, `hdrAmbientCubeLightsSurfacesByNormalLikeTheRasterModelShader`); without it the
  flat `ambient` colour applies.
- **Fog.** Primary hits use the raster quadratic ramp `min(d^2 * (1 - 1/e) * density^2, 1)` on the world-space hit
  distance; the sky is not fogged, and reflected hits are not fogged (`fogFollowsHitDistanceAndLeavesTheSkyUnfogged`).
  Fog `gradient` shapes nothing, as in raster.
- **Transparency.** Alpha-blended surfaces are composited front to back over the opaque surface behind them, are
  shadowed like any receiver, and are invisible to shadow and reflection rays (`blendedSurfacesReceiveButDoNotCastShadows`,
  `overlappingBlendedLayersCompositeFrontToBackOverTheOpaqueSurface`, `blendedGeometryIsNotReflected`). They do not
  write depth: the frame's depth is that of the first opaque surface, or 1. They never refract; masked or blended
  materials with transmission are rejected.
- **Explicit fallback.** `RaySceneSnapshot.unsupportedReason()` reports a scene the backends reject, and `submit`
  throws `IllegalArgumentException` for it, so the view falls back to raster: more than `RAY_MAX_BLENDED_INSTANCES` (32)
  blended instances, more than 128 materials or textures, or more than 12 lights (`tooManyBlendedLayersAreAnExplicitFallback`,
  `tooManyLightsMaterialsOrTexturesAreAnExplicitFallback`). A primary ray composites at most 16 blended layers and
  8 holes in a row; anything deeper is dropped and the remainder shows the sky. (`aDozenBlendedLayersCompositeWithinTheLimit`
  pins twelve stacked panes, the count of the fixture Main Scene.)

Instances carry a ray visibility mask (`RaySliceInstance.primaryOnly`): bit 1 for shadow/reflection rays, bit 2 for
primary rays; blended instances use only bit 2. Metal accepts the 21-float instance record this needs. A scene request
evaluates `samples` (1–8) independent camera samples starting at `sampleOffset` and returns their mean; the session's
`RayFrameAccumulator` weights consecutive batches of the same key, epoch, revision and limits by sample count.

## Geometry reuse and resource bounds

A session keeps one bottom-level structure per `RayMesh`; every instance of a mesh shares it, and a transform-only
update only rebuilds the top-level structure (`staticGeometryIsReusedWhileOnlyTransformsChange`,
`repeatedInstancesShareOneMeshAndRemovedInstancesDisappear`). `dirtyMeshes` compares consecutive scenes: a mesh whose
positions changed but whose vertex count and indices did not (a re-skinned model) is refit alone, overwriting its
vertex range and rebuilding only its structure (`aReSkinnedMeshRefitsOnlyItsOwnStructureAndMovesTheHit`). Any other
change (a different mesh count, vertex count or indices) rebuilds every structure. `RaySession.geometryBuilds` counts
structures built, so tests and diagnostics can see reuse. Shading data (materials, textures, lights, normals, UVs) is
re-encoded only when it, or the geometry, changed (`shadingUnchanged`). The plugin's `RaySceneSnapshots` keeps the same
`RayMesh`/`RayTexture` objects for an unchanged asset, which makes these comparisons identity checks.

Bounds, all explicit fallbacks through `RaySceneSnapshot.unsupportedReason()` rather than silent omission:
32 MiB of triangle input per session (`RAY_MAX_GEOMETRY_BYTES`, positions plus 32-bit indices;
`geometryOverTheMemoryBudgetIsRejectedAndTheSessionStaysUsable`), 128 materials, 128 textures, 12 lights and 32 blended
instances, and the backend's own instance and mesh capacity (`RayCapabilities.maxInstances`: 1024 on Metal and on
Vulkan; `scenesPastTheOldInstanceCapRenderWithTheBackendsOwnCapacity`, `manyDistinctMeshesPastTheOldCapAreBuiltAndHit`). Native builds are also bounded by the smaller of 512 MiB and a quarter of the Metal device's recommended
working set. A scene's shading payload is up to 16M floats (64 MiB: materials, normals, UVs, float textures such as an HDR
sky) followed by up to 16M RGBA8 texels (64 MiB). Ordinary image textures are `RayTexture`s built from bytes and stay
8-bit from the plugin to the shader, a quarter of the size of float texels (`byteTexturesRenderLikeTheSameFloatTextures`,
`aLargeByteTextureIsAcceptedWithoutExpandingToFloats`). The plugin additionally bounds instances (1024), triangles (2M)
and bytes (512 MB) in `RaySnapshotLimits`, and its fallback message says which bound a scene exceeded and by how much. Removed instances simply disappear from the next top-level structure. These bounds
are per session, so one view's scene never consumes another view's budget.

## Scheduler and quality policy

`RayRenderScheduler` is a constructor-wired mailbox and serial-worker pump.
`offer`, `latest` and `cancel` invoke no native callbacks. `pump` calls the supplied
submit/poll adapter on one owner worker, with one batch in flight and one replaceable
pending input. Cancellation clears queued/display state immediately; the owner still
drains already selected work and performs eventual backend/session disposal.

Inputs carry structural scene/context generations, output framebuffer size, active
camera identity, camera/pose revisions and a content revision for transforms, lights,
materials, environment and geometry. Structural changes invalidate completed frames
immediately. Modestly older motion frames may display for at most 100 ms from offer,
using their own batch camera/geometry metadata; unchanged still frames do not expire.
The caller must advance revisions whenever those inputs change. There is no GL/Swing
or project file access in this scheduler.

`RayQualityPolicy` starts interaction at half output dimensions with one sample.
Stable rendering raises resolution from recent worker timing and increases samples.
Defaults cap dimensions at 4096, pixels at 4,194,304, retained frame/presentation
payload at 128 MiB (80 bytes/pixel), samples at 8 per batch and 256 per accumulation
epoch, and worst-case ray count at 2,097,152 per batch. The adapter supplies primary,
visibility and reflection ray cost per sample; scene/native allocations are budgeted
separately. All limits, including the intersection-query budget, are hard. If one sample at the half-resolution
floor exceeds the query, dimension, pixel or memory bounds, the policy throws `RayQualityLimitException` for explicit
view fallback. `RayWorkBudget` accounts for traversal retries and allowed secondary/shadow work (see below).
It never silently lowers that floor. Scene/camera/pose/content changes or internal resolution changes start a new
accumulation epoch. At its sample cap, unchanged work stops until quality or inputs
change.

The immutable `RayRenderBatch` carries internal dimensions, samples, accumulation
epoch/offset and matching display input to the worker adapter. The plugin's `RayViewRuntime` / `RayViewFeed`
and `RayBackendService` connect it to full scene shading and safe GL presentation. The separate synthetic
feasibility shader remains available for its dedicated tests.

```sh
./gradlew :raytracing:test --tests '*RayRenderSchedulerTest' --tests '*RayQualityPolicyTest'
```

## Developer feasibility preview

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

Memory comes from VMA. A scene frame uploads the payload only when materials, lights, environment, fog, mesh
attributes or textures changed, and rebuilds only the structures of meshes whose vertices changed (a re-skinned model keeps
its other structures); transform-only frames rewrite the top-level instances alone. Instance masks keep alpha-blended
surfaces out of shadow and reflection rays. Color is written as `R16G16B16A16_SFLOAT`, so the conformance kit allows
`VulkanRayBackendTest.colorStorageError` (2^-10 of the value) on top of its absolute color tolerances; depth stays 32-bit. Bottom-level structures are built once per mesh and survive transform-only
submissions; the top-level structure is rebuilt in each frame's command buffer. Frames are split into
dispatches of at most 2^19 rays, each in its own command buffer, so no single submission approaches a
driver timeout. Completion is polled with a fence from the owner worker; `submit` and `poll` never wait.
Geometry upload and session disposal do wait on the owner worker. After `VK_ERROR_DEVICE_LOST` teardown skips
every wait. A loss injected through `RayDeviceHealth` leaves the device healthy, so teardown still waits.

There are two shaders. `src/main/glsl/slice.comp` is the feasibility slice (primary visibility, one directional shadow ray,
one mirror bounce); `src/main/glsl/scene.comp` is the full scene renderer of `RaySceneRequest`, a line-for-line port of the
Metal `rayScene` kernel. The `compileSpirv` task builds them into jar resources *native/vulkan/slice.spv* and *native/vulkan/scene.spv*,
unoptimized (`-O` inlines the shading code tenfold and drivers optimize SPIR-V themselves). Both pipelines share one
descriptor layout: the acceleration structure, instance, vertex and index buffers, the two output images, the camera
uniform and, for the scene shader, the scene payload bound twice (bindings 7 and 8: the floats, and the RGBA8 texels that
follow them in the same buffer). The slice shader ignores bindings 7 and 8. No `lwjgl-shaderc` is packaged. The task uses `glslangValidator` or `glslc` from `PATH`, then from the `VULKAN_SDK` environment variable's `bin` directory, then from the Android NDK's
`shader-tools` (under `ANDROID_HOME`, `ANDROID_SDK_ROOT` or the default Android SDK directory in the user's home), or `-Pabyssus.glslc=/path/to/tool`. Without either it warns and ships no SPIR-V, and the backend
then reports `INITIALIZATION_FAILED`; `-Pabyssus.requireShaders=true` (used by the release and CI builds) turns
that into a build error.

```sh
./gradlew :raytracing:test                                    # Vulkan cases skip without a device flag
./gradlew :raytracing:test --tests '*VulkanRayBackendTest' -Dabyssus.vulkanTests=true
./gradlew :raytracing:test -Dabyssus.vulkanTests=true -Dabyssus.raytracing.validation=true
./gradlew :raytracing:verifyVulkanPackaging                   # jar-based; add -Dabyssus.vulkanTests=true to render
./gradlew :raytracing:verifyNativePackaging                   # Vulkan, plus Metal on a Mac
./gradlew :raytracing:test --tests '*VulkanRayBackendTimingTest' -Dabyssus.vulkanTests=true -Dabyssus.vulkanTimingTests=true
./gradlew :test --tests '*RayRealSceneTest' -Dabyssus.vulkanTests=true   # the fixture's Main Scene through the real backend
```

`-Dabyssus.raytracing.validation=true` enables the Khronos validation layer (and `VK_EXT_debug_utils`) and
makes `VulkanRayBackendTest` fail on any validation error. It is a developer flag and is never on by default. It needs
the `VK_LAYER_KHRONOS_validation` layer (Ubuntu: `vulkan-validationlayers`); a layer unpacked outside the system paths is
found with `VK_LAYER_PATH` plus `LD_LIBRARY_PATH` pointing at its directory.
`verifyVulkanPackaging` checks the SPIR-V in the jar, that no shaderc is on the runtime classpath, the
`lwjgl-vma` natives for every target, MoltenVK for macOS only, and that a probe without a loader returns
`RUNTIME_NOT_FOUND` (one JVM per test class, because LWJGL's library choice is process-global). Metal
packaging moved to `verifyMetalPackaging`; `verifyNativePackaging` runs both where they apply. When they render, both
packaging tests also draw a glass slab through the packaged scene shader (`assertPackagedSceneOptics`), so a jar whose
shader predates the version 3 scene payload or the 28-float camera fails there. Metal and Vulkan both report
`sceneOptics`; the Metal kernel and `scene.comp` change together with `RaySceneEncoding` and `RaySceneRequest.nativeCamera`.

On a machine without a GPU, Mesa's software driver (lavapipe, `mesa-vulkan-drivers`) exposes ray queries and
runs the whole suite: `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json`. The first scene frame on a cold lavapipe
compiles the shader on the CPU and can exceed the kit's 5 second wait once; rerun it.

## Saved quality limits and query accounting

`RayRenderInput` can carry a frozen `RayRenderSettings` target (1–4096 camera samples per pixel) and frame budget
(1–67108864 intersection queries). Scheduler inputs without saved settings retain their constructor limits.
Submissions contain at most eight samples; the last batch is clamped to the remaining target. A different internal
resolution begins a new accumulation epoch. Backends evaluate each batch's samples independently and accumulate them
in the session (`RayFrameAccumulator`).

`RayWorkBudget` replaces the former primary + lights + one reflection estimate. Both current native primary loops
allow 16 blended layers plus 8 cutout queries; secondary and shadow traversal allow 8 cutout retries. With opaque
materials only, traversal needs one query per segment. For each potentially shaded primary layer, the conservative
bound includes every allowed reflection/refraction event and each shadow-casting light at each shaded vertex,
including the terminal surface. Reflection and refraction share one sampled continuation, never a binary tree.
Frame multiplication uses checked Long arithmetic. The policy intersects this query budget with device dimensions,
pixel and memory bounds. When one sample at half resolution does not fit, it reports `RayQualityLimitException`;
it no longer exceeds the ray budget to preserve a large preview. Saved preferences are not rewritten by fallback.

Changed settings carry an explicit input revision. They clear pending/completed publication and history while the
same native session drains its in-flight work. The scheduler rejects older revisions even within its ordinary
motion grace interval; stable unchanged content stops submitting at the target.
