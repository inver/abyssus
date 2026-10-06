# Implementation verification

## 1.1 Renderer reconciliation (2026-10-03)

Read all current shadow and water delta specs, their renderer designs, and the main
scene-environment-lighting spec against SceneRenderer. No contradictory delta needs amendment:

- Shadows' ray-tracing exclusion is a proposal scope boundary. Its per-light isolation,
  cutout/blended rules, preview/animation updates and bounded failures apply to the
  new mode too. Raster shadow maps remain the fallback.
- SceneRenderer already updates models/terrains once before SceneShadows.render and
  the color passes. Reuse that posed content; do not advance animation for another pass.
- HDR diffuse lighting replaces ambient only for content, after drawing the grid.
  The ray delta explicitly replaces only primary PBR specular in the optional mode.
- Water is still unimplemented. Its planar reflections exclude water and editor
  decorations, consistent with the ray proposal's exclusion of water rendering.
  Water does not require ray-traced reflection/refraction. Its eventual composition
  must retain visible water; adding water to native ray geometry is outside this plan.
- Spotlight and water property/tree edits do not overlap this read-only toggle.
  Their explicitly authorized extensions are not new writes made by ray tracing.

`openspec validate add-scene-raytracing --strict` passed (exit 0). No other change was amended.


## 1.2 JVM backend foundation

Added `:raytracing` as a plain JVM library and plugin dependency, constructor-wired
backend factory/session ownership, capability rejection and immutable RGBA/depth frames.
No native backend is loaded or implemented by this foundation.

- First contract test run failed at compileTestKotlin because the new API was absent.
- `./gradlew :raytracing:compileKotlin :raytracing:test` passed: 5 tests, no failures,
  errors or skipped tests. Existing Gradle deprecation/native-access warnings remain.
- `scripts/check-docs.sh` passed: 128 paths checked.
- `git diff --check` passed.

## Native verification environment

This host is macOS arm64. `xcrun --find metal` finds Xcode's Metal compiler.
No Windows/Linux or macOS x86_64 runtime/device is available in this workspace.
Tasks 1.3–1.6 require actual native load/device tests and the performance gate on
the corresponding platforms; cross-compilation or filenames alone cannot prove them.
No native packaging, backend slice, performance gate or manual check has passed yet.

## Full project check

`./gradlew check` failed in `:test`: 515 plugin tests, 10 failures, 30 skipped.
`:core:check` and `:gdx-model:check` passed. `:raytracing:check --offline` passed separately.

The existing Untitled fixture has 9 entities rather than the 7 pinned by tests,
entity 0 Y is 3.086434 rather than 0.9123962, and camera/marker data has changed.
No scene fixture or existing renderer implementation was edited by this change.
The following failures depend on those fixture assumptions:

- `net.nevinsky.abyssus.AbyssusViewTest.testNodeTree`: junit.framework.AssertionFailedError: expected:<[ambientLight, fog, skybox: skybox_physical, ecs  7 entities]> but was:<[ambientLight, fog, skybox: skybox_physical, ecs  9 entities]>
- `net.nevinsky.abyssus.ecs.SceneEcsLoaderTest.mainSceneLoads`: java.lang.AssertionError: expected:<7> but was:<9>
- `net.nevinsky.abyssus.projectView.SceneTransformEditTest.testMoveKeepsIndentationAndEveryOtherLine`: junit.framework.AssertionFailedError: [44, 45] expected:<1> but was:<2>
- `net.nevinsky.abyssus.projectView.SceneTransformEditTest.testNothingIsWrittenWhenTheEditChangesNothing`: junit.framework.AssertionFailedError
- `net.nevinsky.abyssus.plugin.sceneview.SceneContentTest.mainSceneHasThreeModelsAndOneTerrain`: java.lang.AssertionError
- `net.nevinsky.abyssus.plugin.sceneview.SceneContentTest.mainSceneHasTheFixtureCamera`: java.lang.AssertionError: expected:<Vec3(x=0.0, y=0.0, z=0.0)> but was:<Vec3(x=0.0, y=5.520455, z=0.0)>
- `net.nevinsky.abyssus.plugin.sceneview.SceneMarkersTest.theViewCameraHasNoMarkerTarget`: java.lang.AssertionError
- `net.nevinsky.abyssus.plugin.sceneview.SceneMarkersTest.aCameraDrawsABodyAndAFrustum`: java.lang.AssertionError: expected:<28> but was:<54>
- `net.nevinsky.abyssus.plugin.sceneview.SceneRendererCameraTest.lookingThroughACameraUsesItsPositionDirectionAndLens`: java.lang.AssertionError: expected:<0.8857895> but was:<0.96025884>
- `net.nevinsky.abyssus.plugin.sceneview.SceneTransformWriterTest.movingAnEntityChangesOnlyItsLocalPositionX`: org.junit.ComparisonFailure: expected:<[3.086434]> but was:<[0.9123962]>

Task 5.5 remains unchecked: the full suite is not green and the platform/manual
checks have not been performed. Implementation is paused before task 1.3 pending
a route for the required macOS x86_64, Windows and Linux artifact/load verification.


## Revised foundation and macOS arm64 native implementation

The revised 31-task plan reopened 1.2 while the files were already in the shared
workspace. Verified `settings.gradle.kts` includes `:raytracing` and the module is
a plugin dependency. Reverified and checked 1.2 after aligning the foundation with
the new `RayBackendProvider` / `RayBackend` / `RaySession` contracts.

- Frames now retain finite linear RGBA floats, including HDR values above 1,
  alongside GL window depth. Producer/consumer arrays are copied.
- Expected native load/init errors become unavailability; cancellation propagates.
- One Metal backend/device/queue serves independent view sessions on its owner worker.
  Probe construction does not load JNI; the active backend is reused until disposed.
- Added build-time Objective-C++ JNI and Metal shader compilation. Xcode initially
  lacked the Metal compiler component; installed Metal Toolchain 27A266a with the
  user's approval. Build tasks support Gradle configuration-cache reuse.
- Library extraction uses a content hash and one path per JVM/classloader, avoiding
  duplicate library loading. Native session ownership uses C++ RAII/ARC, avoiding
  Objective-C class registration collisions during plugin classloader replacement.
- The offscreen slice retains BLAS geometry, rebuilds TLAS instances on transforms,
  traces camera and directional visibility rays plus one mirror bounce, and polls
  completed shared-memory color/depth without waiting in submit or poll. Geometry
  preparation and disposal currently wait on the worker; bounded device-loss
  shutdown remains part of the unfinished lifecycle tasks.

`./gradlew :raytracing:compileKotlin :raytracing:test :raytracing:verifyNativePackaging
-Dabyssus.metalTests=true` passed. The contract suite has 7 tests, library loading
4, Metal rendering 4, and packaging 1 (also executed separately from the built jar).
The 30-second timing test is opt-in and was skipped in this correctness run.

The packaging test loads JNI/metallib from `raytracing.jar`, probes the actual GPU,
opens two sessions, closes one and renders/readbacks a frame from the other.
The device tests check primary visibility/projected depth, moving instances,
directional shadowing and offscreen reflection hit/background miss.

Reference environment: macOS 27.0 build 26A428, Apple M1 Pro (14 GPU cores), arm64;
Apple clang 21.0.0 / Xcode toolchain. No x86_64 Mac or Windows/Linux device checks
have passed. Tasks 1.5 and 1.6 remain open under the revised plan: packaging still
requires both Mac architectures, and the Metal test does not yet extend the new
full conformance kit. No runIde presentation/performance gate has been performed.

## Conformance task ordering issue

Task 1.3 requires the full reference scenes of 3.1–3.5 and requires native suites
to extend the kit before the minimal slices and mandatory feasibility gate. Those
later material/terrain/environment/transparency representations and shaders do not
yet exist. Asked to stage the kit's feasibility/lifecycle cases first and add the
full shading cases with 3.1–3.5, keeping every case mandatory before completion.
No such planning change has been made without the user's answer.


The user approved staging the conformance kit. Updated design decision 7, task 1.3,
tasks 3.1–3.5 and the final verification task to retain every required case with
explicit ownership, without requiring full shading before the feasibility gate.

Native-only timing: 2929 frames in 30.0032 seconds at 1280x720, 97.6229 fps,
p95 submission/readback 11.6443 ms on Apple M1 Pro. This is not the runIde
presentation/EDT/input-latency gate and does not complete task 1.8.

## Resumed feasibility verification (2026-10-03)

`./gradlew :raytracing:test :raytracing:verifyNativePackaging
-Dabyssus.metalTests=true` passed on the macOS arm64 host. The fake and Metal
backends each passed all 11 inherited feasibility/lifecycle conformance cases:
absent-device probe, primary visibility/depth, instance motion, directional
shadow, mirror hit/miss, pending replacement, stale generation/resize rejection,
independent sessions, in-flight disposal and injected device loss.

The contract suite passed 7 cases, library loading passed 4, and packaged Metal
loading/probing/rendering passed 1 in both the ordinary suite and the separate
jar-based packaging task. The timing test was skipped by its separate opt-in flag.
Task 1.6 is now complete; these results cover the minimal slice, not full shading
parity or the runIde performance gate.

Task 1.3 remains open because VulkanRayBackendTest and its Windows/Linux device
runs are absent. Task 1.5 remains open for the x86_64 Mac load/probe. This host
cannot execute the required Windows/Linux or x86_64 Mac device checks. No platform
requirement has been removed, and no unperformed verification has been checked off.

## Metal-only continuation

The user instructed: "Skip vulkan implementation and continue." Vulkan implementation
and its platform checks are skipped in this apply run; their checkboxes remain open.
This does not establish Windows/Linux support or complete the cross-platform change.

Added an opt-in sandbox IDE feasibility preview (`-PrayExperiment=true`) using
synthetic geometry and the existing safe canvas. Project content rendering remains
the later asset/shading/integration work. Native work is on one dedicated worker;
EDT offers requests and uploads/draws host frames without fence waits. The temporary
driver owns its device; application-wide device ownership remains a later integration
requirement. The preview logs bounded 30-second measurement windows and stops on
hide/close; it can be disabled to return to the original view state.

GL transfer first failed because RayFramePresenter was absent. The implemented pass
passed native color/depth readback. A new state/resize/orientation case failed with
nonzero GL_UNPACK_ROW_LENGTH, then passed after saving/resetting/restoring pixel-unpack
state. Worker tests first failed because RayFeasibilityLoop was absent, then passed
for worker ownership, replaceable pending motion, independent asynchronous cleanup,
and no probe on construction/unused close.

Task 1.8 remains open until the sandbox IDE moving-instance/camera check and depth
inspection are actually performed. Automated GL transfer/timing do not substitute
for that check. Tasks 1.3, 1.4, 1.5, 1.7 and 1.9 retain their unperformed platform
requirements; skipping Vulkan does not mark those requirements complete.

## Vulkan backend (tasks 1.3, 1.4, 1.7)

The user chose to implement Vulkan after the Metal-only run. This host is Linux x86_64 with no GPU. Vulkan ran on
Mesa lavapipe (llvmpipe, Vulkan 1.4.318, software), which exposes `VK_KHR_ray_query`,
`VK_KHR_acceleration_structure` and `VK_KHR_deferred_host_operations`. It is a conformance check of the
implementation, not a hardware-device result.

Added `VulkanDevice.kt`, `VulkanRayBackend.kt`, `src/main/glsl/slice.comp` (compiled to SPIR-V by `compileSpirv`),
`VulkanRayBackendTest` (extends `RayBackendConformanceKit`), `VulkanNativePackagingTest` and
`VulkanRuntimeMissingTest`. The Gradle module now depends on `lwjgl`, `lwjgl-vulkan` and `lwjgl-vma`.

Results on lavapipe:
- `VulkanRayBackendTest`: 11/11 kit cases passed (primary visibility and depth, instance motion, directional shadow,
  mirror hit/miss, request replacement, stale generation/resize rejection, two sessions, dispose in flight,
  injected loss).
- With `-Dabyssus.raytracing.validation=true` the Khronos validation layer was confirmed active, and the same 11
  cases passed with no validation errors. The first validation run found a real defect: after a loss injected
  through `RayDeviceHealth`, teardown skipped its fence wait and destroyed in-use resources. Teardown now skips waits
  only after Vulkan itself reports `VK_ERROR_DEVICE_LOST`; the rerun was clean.
- `verifyVulkanPackaging`: SPIR-V present with the right magic number, no shaderc on the classpath, `lwjgl-vma` and
  `lwjgl` natives for linux/windows/macos/macos-arm64, `lwjgl-vulkan` natives for macOS only, and a probe with a
  nonexistent loader returned `RUNTIME_NOT_FOUND` (twice in one JVM). The packaged-jar render case passed with
  `-Dabyssus.vulkanTests=true`. Passed without the flag with the render case skipped.
- `:raytracing:test` without device flags: all Vulkan device cases skipped; the fake backend suite still passes.

Not performed, so these tasks stay open:
- 1.3: `VulkanRayBackendTest` exists and passes on a software device, but the task requires running it on its
  platforms. No Windows run, and no hardware device on Linux.
- 1.4: `verifyNativePackaging` checks the module jar and runtime classpath, not the final plugin zip.
  `./gradlew buildPlugin` could not run here (data.services.jetbrains.com is blocked by the egress proxy). Not run
  on Windows, macOS arm64 or macOS x86_64.
- 1.7: needs compatible Windows and Linux hardware devices. MoltenVK on macOS is unverified (it may not expose
  ray queries; the probe reports the missing feature).
- 1.9 documentation: the module README has a Vulkan section, but docs/ai/architecture.md is not updated and the
  documented commands were verified only on this host.

CI: `build.yml` and `release.yml` install `glslang-tools` and pass `-Pabyssus.requireShaders=true`. The UI-test
workflow does not; without a compiler the shader task warns and the Vulkan backend reports itself unavailable.
Not exercised on GitHub runners.

## Vulkan scene path and Linux hardware run (tasks 1.3, 1.4, 1.7 and the Vulkan clauses of 2.4-4.6), 2026-10-04

The user asked for the Vulkan implementation of every task, so the earlier "Vulkan skipped" notes no longer apply.

Host: Linux x86_64, NVIDIA GeForce RTX 3070 Ti (proprietary driver 610.57.04, Vulkan 1.4.341) plus Mesa llvmpipe. The
backend selects the discrete device; `VulkanRayBackendTest` prints the device it ran on ("Vulkan device: NVIDIA GeForce
RTX 3070 Ti" in the report). Shaders were built with the NDK's `glslc` (`-Pabyssus.glslc=... -Pabyssus.requireShaders=true`)
because the host has no `glslangValidator` on PATH. The Khronos validation layer 1.3.275 came from the Ubuntu
`vulkan-validationlayers` package, extracted outside the system and loaded with `VK_LAYER_PATH` and `LD_LIBRARY_PATH`.

Implemented:
- `src/main/glsl/scene.comp`: a port of Metal's `rayScene` kernel (default/PBR/terrain shading, per-light visibility rays
  with alpha-test holes, one GGX reflection bounce, sky and HDR tone mapping, fog, front-to-back blended layers).
- `VulkanRaySession.submit(RaySceneRequest)`: static geometry reuse, in-place refit of only the changed meshes, payload
  upload only when shading data changed, instance masks (bit 2 only for blended surfaces), and a 1024-instance capacity
  like Metal. The payload encoder was renamed `RaySceneEncoding` because both backends read it.

Defects found only by running on hardware or with the real scene, all fixed:
- Device-extension enumeration allocated on the 64 KB LWJGL `MemoryStack`; the NVIDIA driver overflows it
  (`OutOfMemoryError: Out of stack space`). Driver-sized lists now use the heap.
- SPIR-V of the scene shader exceeds the stack too, and the per-mesh loops accumulated stack across 237 mesh parts. The
  module is copied through the heap and each loop iteration pushes its own stack frame.
- `glslc -O` inlined the shading code into a 572 KB module (57 KB without); the task no longer passes `-O`.

Results with `-Dabyssus.vulkanTests=true -Dabyssus.raytracing.validation=true`:
- `VulkanRayBackendTest`: all 44 kit cases pass (11 slice cases and the 32 scene cases, plus the no-device probe), on the
  RTX 3070 Ti with the validation layer active and no validation messages, repeated twice. All 44 also pass on llvmpipe.
  One cold llvmpipe run exceeded the kit's 5 s wait on its first scene frame (shader compilation on the CPU); it passed on
  both reruns.
- The kit needed one explicit allowance: Vulkan stores color as `R16G16B16A16_SFLOAT` (design decision 8), whose spacing is
  2^-10 of the value, so absolute tolerances of 0.0003 to 0.001 cannot hold at or above 0.5. `colorStorageError` is a kit
  hook (0 for the fake and Metal) that adds 2^-10 of the expected value to the color tolerance of the two reference
  comparisons; depth stays exact. The 32-bit color alternative was not taken because it doubles readback bytes.
- `RayRealSceneTest.theFixtureSceneRendersThroughTheRealVulkanBackend`: the fixture's Main Scene (hundreds of model parts,
  blended panes, 2048x2048 textures) renders through the real Vulkan backend.
- `VulkanNativePackagingTest` 4/4 (both SPIR-V files valid, no shaderc, natives for every target, MoltenVK for macOS only);
  `verifyNativePackaging` passes; the `buildPlugin` zip's `raytracing.jar` holds `native/vulkan/slice.spv` and `scene.spv`,
  and the zip holds the `lwjgl-vma` natives for every target and `lwjgl-vulkan` natives for macOS only.
- Native-only timing (`VulkanRayBackendTimingTest`, 30 s, 1280x720, a moving instance and camera, readback included):
  slice 106 fps with p95 11.0 ms; scene shader on a small PBR/diffuse scene 105 fps with p95 11.4 ms. This is not the
  runIde gate: it excludes GL upload, EDT time and presentation latency.
- `./gradlew check`: 915 tests, 10 failures, none in `raytracing`, `core` or `gdx-model`. The same 10 fail on a clean
  worktree of HEAD (`SceneContentTest`, `SceneMarkersTest`, `SceneEcsLoaderTest`, `SceneTransformWriterTest`,
  `SceneTransformEditTest`, `SceneRendererCameraTest`, `AbyssusViewTest`), all from assertions on the `Untitled` fixture that
  was edited. A stale incremental build also made `SunDirectionTest` fail with a `NoSuchMethodError`; it passes after a clean
  compile.

Still open, so these tasks stay unchecked:
- 1.4: Windows, macOS arm64 and macOS x86_64 packaging runs (Linux and the plugin zip contents are done).
- 1.7: a Windows device. MoltenVK ray-query support on macOS is unverified.
- 1.8 and 5.1-5.3: the manual runIde checks, and 5.4's IDE load of the zip, which need an interactive IDE session on each OS.
  This session has no way to drive the IDE window, so none were performed.

## Preview shadow diagnosis and CPU model companions (2026-10-04)

The user reported a visible reflection but no shadow in the sandbox preview. A
native regression using the actual preview request reproduced zero shadow pixels:
its vertical triangle and straight-up light produced a degenerate floor footprint.
The preview now uses an angled light; the native regression passed at the center
and moved positions (233 and 253 shadow pixels), with unchanged primary floor depth.
This adjusts only the synthetic feasibility scene. User confirmation of the updated
manual gate remains pending; task 1.8 is not complete.

Task 2.1 adds optional constructor-owned immutable CPU model companions. Raster
preparation offers parsed geometry/materials and decoded images before disposal;
late acquisition performs CPU preparation without touching an existing GPU cache.
Leases share one snapshot per project/asset and drop retained bytes on last close.
Cancelled/removed requests cannot publish late results. Snapshot and shared-store
payload bounds are explicit failures; raster preparation retains nothing without
CPU interest.

The initial RayModelSnapshotTest run failed because the API was absent. The nine
cases now pass: copied 32-bit/interleaved geometry/node-material data, decoded image
bytes and UV/linear-color/sampler conventions, repeated-instance/view sharing, late
acquisition, capture before upload/disposal, real Assimp preparation on a copied
fixture asset, cancellation release/propagation and explicit memory-budget failures.
Review found and reproduced an additional stale raster-preparation race. Preparation
now captures request identity before IO, so closing and reacquiring an asset cannot
attach old geometry to the replacement request. The regression passes, and a second
review found no remaining important issues in task 2.1's scope.
`./gradlew :core:checkNoSingletons` and
`./gradlew :core:check :core:test -Dabyssus.glTests=true` passed. Task 2.1 is complete.
The source scene fixture was modified outside this work and is left untouched.

Task 2.2 adds equivalent optional immutable terrain companions, with project/asset
leases, pre-IO preparation identity and bounded retained bytes. They preserve raster
height triangles, normals, repeated layer UVs, local splat coordinates, base/R/G/B/A
bindings and decoded image rows. Splat maps retain Linear/ClampToEdge/no-mipmap
sampling; layers retain Linear magnification, MipMapLinearLinear minification,
Repeat wrapping and the mipmap requirement. Failed/missing images are omitted exactly
as in raster preparation. CPU fallback reuses TerrainLoader and disposes its own
images after copying; it never uploads or invalidates GPU resources.

The first terrain test run failed to compile because the API was absent. After
implementation, one blend fixture failed because Pixmap's default source-over
blending erased its zero-alpha channel weights; writing fixture pixels with blending
disabled corrected that fixture. All five RayTerrainSnapshotTest cases now pass,
including sequential splat reconstruction, transformed terrain/normal parity,
failed/missing layers, a real PNG retained after preparation disposal, late acquisition,
shared ownership, cancellation/reacquisition and explicit payload-budget failures.
Together with the nine model cases, all 14 snapshot tests passed without skips in
`./gradlew :core:check :core:test -Dabyssus.glTests=true`; the broader core checks and
GL tests also passed. `./gradlew :compileKotlin`, `scripts/check-docs.sh` (129 paths),
`git diff --check` and `openspec validate add-scene-raytracing --strict` passed.
Read-only review found no important terrain snapshot issues. Task 2.2 is complete.
Native terrain sampling/mipmap generation/shading remains task 3.1. Live scene
integration remains pending the manual feasibility gate; further Vulkan work is
skipped as the user instructed, and unperformed platform tasks remain unchecked.

The sandbox preview was relaunched on the copied project with `-PrayExperiment=true`
after the user reported the developer button missing. The user then confirmed
“bothvisible”: the updated shadow and reflection are both visible. This confirms
the visual retry on this Mac; it does not by itself establish the complete 30-second
timing/input gate or any other platform, so task 1.8 remains unchecked.

## Scheduler and bounded quality (task 4.1)

Added an independent constructor-wired RayRenderScheduler mailbox/worker pump and
RayQualityPolicy. Offers replace one pending input without native calls; only the
serial worker submits/polls one in-flight batch. Completed frames retain their own
camera/geometry/display metadata. Structural scene/context generations, output size
and active-camera identity reject stale results immediately, while ordinary camera/
pose motion can present modestly older frames without exact-revision starvation.
Motion age is bounded to 100 ms from offer; unchanged still frames remain valid.
Cancellation invalidates publication even after re-enabling identical scene keys.

Quality starts interaction at half output dimensions and one sample, improves stable
work from recent worker timings, and enforces dimension/pixel/frame-payload/sample/
worst-case-ray caps. Inputs include conservative visibility/reflection ray cost.
Unrepresentable minimum resolution reports RayQualityLimitException for explicit
fallback. History epochs reset on camera/pose/content/structural/internal-resolution
changes; stable work stops at its accumulation cap. The worker adapter receives
sample/epoch/offset plans. Full scene/native sample integration remains later tasks;
the native feasibility shader and live Scene renderer were not changed by this task.

The initial tests failed because the APIs were absent. A sample-cap fixture was then
fixed to hold resolution constant: adaptive resizing correctly starts new epochs.
A separate regression verifies those resolution resets. Review identified slow
frames still requesting eight samples at the resolution floor; the new assertion
reproduced that failure, and timing feedback now reduces slow work to one sample.
A follow-up review found no remaining important issues in task 4.1's scope.
`./gradlew :raytracing:test --tests '*RayRenderSchedulerTest' --tests '*RayQualityPolicyTest'`
passed all 13 cases (8 scheduler, 5 quality) without skips. The broader run also
passed these and the opt-in Metal cases. Task 4.1 is complete; progress is 6/31.

`./gradlew check -Dabyssus.metalTests=true --continue` failed outside this task:

- Ray module: 64 tests, 1 failure, 14 skipped. VulkanNativePackagingTest.jarHoldsValidSpirvAndNoShaderCompiler
  failed because `/native/vulkan/slice.spv` is absent; neither glslangValidator nor
  glslc is installed on PATH. Vulkan implementation/toolchain work remains skipped
  per the user's instruction.
- Plugin: 521 tests, 10 failures, 34 skipped. The fixture differs from historical
  test expectations (nine versus seven entities and changed model/camera positions).
  The same failures were reported before the scheduler work, and the independently
  modified source fixture remains untouched:
  AbyssusViewTest.testNodeTree;
  SceneEcsLoaderTest.mainSceneLoads;
  SceneTransformEditTest.testMoveKeepsIndentationAndEveryOtherLine;
  SceneTransformEditTest.testNothingIsWrittenWhenTheEditChangesNothing;
  SceneContentTest.mainSceneHasThreeModelsAndOneTerrain;
  SceneContentTest.mainSceneHasTheFixtureCamera;
  SceneMarkersTest.theViewCameraHasNoMarkerTarget;
  SceneMarkersTest.aCameraDrawsABodyAndAFrustum;
  SceneRendererCameraTest.lookingThroughACameraUsesItsPositionDirectionAndLens;
  SceneTransformWriterTest.movingAnEntityChangesOnlyItsLocalPositionX.
- Core and gdx-model checks passed. Plugin compilation passed. Documentation path
  validation and diff whitespace validation passed.

Task 5.5 remains open, as do unperformed platform/manual gates. The visible shadow/
reflection retry is recorded above; it is not a substitute for complete gate timings.

## Scene snapshot and diff conversion (task 2.3, 2026-10-04)

`RaySceneSnapshots`/`RaySceneDiff` (`sceneview/RaySceneSnapshot.kt`) were already in the tree with
`RaySceneSnapshotTest`, but the test class had never run green: all 9 cases failed with
`UnsatisfiedLinkError` (`Matrix4.prj`) because the `PerspectiveCamera` fixture needs the libGDX natives.
Added `GdxNativesLoader.load()` in the test's `init`, as `SceneRendererCameraTest` does.

`./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.RaySceneSnapshotTest'` now passes 9/9, no skips:
drag preview (transforms only, shared geometry), mesh parts/channels/32-bit indices, selected-light identity and
order, camera look-through, asset deletion and project replacement as structural rebuilds, resource limits as
explicit fallback, pending/failed assets, environment/fog, and companion lease sharing/release.
Task 2.3 is complete; progress is 7/31.

## Animation pose and skin deformation (task 2.4, partial, 2026-10-04)

Added `RayModelSkinning` (core): CPU deformation with the raster shader's weighted joint sum. It deforms positions
with the full matrix and normals/tangents/binormals with the linear part (renormalized), leaves every other channel
and the shared source mesh untouched, keeps unweighted vertices at bind pose, and rejects a short palette, non-finite
matrices or non-integer joint indices before returning geometry. Added `RayModelPoses` (plugin, render thread): copies
each animated or skinned entity's node globals and bone matrices from the live instance, with a revision that advances
only when content changes.

- `RayModelSkinningTest` had been written ahead of its class and broke `:core:compileTestKotlin`. It now passes 3/3;
  `:core:checkNoSingletons` passes.
- New `RayAnimationSnapshotTest` (5): first animation's pose at its current time, two instances of one model with
  independent poses and copy isolation, revision only on change, removed/static entities have no pose, and a skinned
  asset through `RaySceneSnapshots` where only the animated instance's geometry moves (x = 1.5 vs 0). The first
  integration run failed because the test advanced a looping 1 s animation by exactly 1 s, which wraps to 0; fixed the test.
- New kit case `updatedGeometryMovesTheHitPosition` (same mesh index and instance, deformed vertices, then restored).
  Passed 16/16 on the fake backend and 16/16 on Metal (`-Dabyssus.metalTests=true`, M1 Pro), no skips.

Task 2.4 stays open:
- The updated-geometry check has not run on Vulkan. This host is macOS and has no Vulkan device, and
  `VulkanRayBackend` handles only the slice `RayRequest`; it has no `RaySceneRequest` path yet, which tasks 2.5 and
  3.1–3.5 also depend on.
- Nothing yet feeds `RayModelPoses`/`RayModelSkinning` into the live scene view or runs deformation on the worker; the
  `deform` hook is exercised only in tests. That belongs with task 4.4's snapshot publication.
- Metal rebuilds all geometry when the mesh list changes (identity comparison), so there is no per-mesh dirty update
  yet; that is task 2.5's static-geometry reuse.

Unrelated, unchanged: `VulkanNativePackagingTest.jarHoldsValidSpirvAndNoShaderCompiler` fails because neither
`glslangValidator` nor `glslc` is installed here, so `slice.spv` is absent.

## Task 1.3 re-check on macOS arm64 (2026-10-04)

The kit (`RayBackendConformanceKit`) covers every listed area: device-less probe, primary visibility, directional
shadow, mirror hit/miss, depth, instance motion, request replacement, stale-frame rejection, two sessions on one
device, dispose in flight and injected loss, plus the geometry-update case. The fake and `MetalRayBackendTest` pass
16/16 with no skips.

`./gradlew :raytracing:test --tests '*VulkanRayBackendTest' -Dabyssus.vulkanTests=true` on this Mac fails 11 of 16:
the probe reports `Unavailable(ACCELERATION_STRUCTURES, "Apple M1 Pro: acceleration_structures")`, because MoltenVK on
the M1 does not expose the required ray-tracing features. That is the design's expected "unusable device" result, not a
backend defect, and it means this host cannot run the Vulkan device cases. The earlier 11/11 pass was on Linux lavapipe
(software) and is not a hardware run. 1.3 stays open until `VulkanRayBackendTest` runs on a compatible Windows or Linux device.

## Scene shading on Metal and the fake backend (tasks 3.1–3.6, 2026-10-04)

Vulkan is skipped by the user's instruction: `VulkanRayBackend` still has no `RaySceneRequest` path, so every claim
below is for the fake (CPU reference) and Metal backends on macOS arm64 (M1 Pro). `VulkanRayBackendTest` extends the
kit and would exercise these cases on a capable device once the scene path exists.

Implemented in the `rayScene` Metal kernel and the test-only `RaySceneReferenceRenderer`:
- per-light shadow rays with alpha-test hole skipping (`traceMasked`), instance masks so blended surfaces are visible
  to primary rays only (instance record is now 21 floats; the bridge validates the mask);
- one GGX-sampled PBR reflection bounce (same pcg hash in shader and Kotlin reference), sky on misses, reflected hits
  shaded without a further bounce, traced radiance replacing only the primary PBR specular ambient;
- equirectangular sky with intensity and rotation, raster-quadratic fog on primary hit distance;
- front-to-back alpha compositing with bounded layers; `RaySceneSnapshot.unsupportedReason()` as the shared explicit
  fallback (blended instances > 4, materials/textures > 128, lights > 12).

Defects found by the new cases: `traceMasked` (shader and reference) counted a ray that crossed a hole and then hit
nothing as blocked; fixed so a miss after holes is a miss and only exhausting `RAY_CUTOUT_HOLE_DEPTH` counts as solid.
Several first-run failures were wrong hand-computed expectations (a lit column typed as 0.6, a flipped ridge-light
direction, a point-light occluder 0.007 short of a column), identical on both backends, and were corrected in the tests.
`pbrSceneMaterialMatchesRasterReference` now uses a black background because a reflection miss legitimately adds
sky-coloured specular.

Results: `./gradlew :raytracing:test -Dabyssus.metalTests=true` runs `FakeRayBackendTest` 33/33 and
`MetalRayBackendTest` 33/33 with no skips; `RayMaterialTest` (7) covers sky orientation/exposure, the fog ramp and
reflection-sampling determinism. `./gradlew :test --tests '*SceneRenderGlTest' -Dabyssus.glTests=true` passes 27/27
with Ray Tracing off. `scripts/check-docs.sh` (129 paths) and `git diff --check` pass. The only failing test in the
module is the already-recorded `VulkanNativePackagingTest.jarHoldsValidSpirvAndNoShaderCompiler` (no glslang here).

Known gaps, documented in `raytracing/README.md`: no sample accumulation (the kernel ignores `samples`), reflected hits
are not fogged, converting cube/procedural skies to an equirect texture is the caller's job, and the plugin does not
yet publish snapshots to the renderer (tasks 4.2–4.4).

## Animation updates and geometry reuse (tasks 2.4, 2.5; Metal and fake only, 2026-10-04)

Vulkan remains skipped (no scene path), so the "both backends" clauses are met for the fake and Metal backends only.

2.4 completion over the earlier partial entry: the Metal bridge gained `updateGeometry`, which overwrites an existing
mesh's vertex range and rebuilds only that mesh's structure; `dirtyMeshes` decides between refit and full rebuild.
`aReSkinnedMeshRefitsOnlyItsOwnStructureAndMovesTheHit` checks, on both backends, that a mesh whose positions move
(topology unchanged) rebuilds exactly one structure and that the hit depth follows 2 -> 5 -> 2. Capture is
thread-agnostic: `RaySceneSnapshots.capture` takes only immutable `RayModelPose` copies and the `RayModelSkinning`
hook, so the worker-side call is wired in task 4.4.

2.5: sessions compare scenes by content (`sameGeometryAs`, `sameContentAs`, `shadingUnchanged`) instead of object
identity, which previously rebuilt every structure on each recapture. `RaySceneSnapshots` now keeps one converted
`RayMesh`/`RayTexture` per retained asset (weak per asset object). New kit cases:
`staticGeometryIsReusedWhileOnlyTransformsChange`, `repeatedInstancesShareOneMeshAndRemovedInstancesDisappear`,
`geometryOverTheMemoryBudgetIsRejectedAndTheSessionStaysUsable`; new plugin `RaySceneDiffTest` (6) for
rebuild-versus-transform, identity reuse across captures, replaced assets/projects and pose/camera/light/environment
classification. The first repeated-instance run failed because my quads were narrower than the pixel pitch; fixed in the test.

Results: `./gradlew :raytracing:test -Dabyssus.metalTests=true` runs the fake and Metal kits 37/37 each, no skips;
`RaySceneDiffTest` 6/6, `RaySceneSnapshotTest` 9/9, `RayAnimationSnapshotTest` 5/5. Resource bounds are documented in
`raytracing/README.md`.

## Correction: task 3.4 reopened (2026-10-04)

3.4 was checked after only the backend half was done (the kernel shades an equirectangular texture, fog and
orientation). Nothing in the plugin yet converts the scene's actual sky (HDR image, cube skybox or procedural GLSL
sky) into that texture, and the primary-miss tone mapping differs from the raster HDR sky (raster tone-maps the
visible sky; reflections use linear radiance). The task is unchecked again until the transfer and that distinction
are implemented and tested.

## Editor integration (tasks 1.3, 4.2–4.6; Metal, fake and the plugin only, 2026-10-04)

Vulkan is skipped by the user's instruction. These tasks are checked for the fake and Metal backends and the plugin;
no Vulkan device or scene path was exercised.

- **1.3** The kit (`RayBackendConformanceKit`, 39 cases now) runs on the fake and Metal backends with no skips, and
  `VulkanRayBackendTest` extends it; the Vulkan device run is not performed (this Mac's MoltenVK reports no ray tracing).
- **4.2** Composition: `RayFrameCompositionGlTest` 5/5 with `-Dabyssus.glTests=true` (grid occlusion against native
  depth, framebuffer/HiDPI upscaling, fog and sky colours, GL state restoration, colour/depth presentation) and
  `SceneRenderGlTest` 27/27 (raster unchanged with Ray Tracing off). Threading is documented in
  `docs/ai/architecture.md`.
- **4.3** `SceneViewPanelRayTest` (6): default off, two independent views, unsupported hardware (disabled toggle, the
  reasons in its tooltip, no Retry), failure with Retry that checks again, the backend and GPU in an active tooltip.
  `RayModeStateTest` 5/5. README.md, the Scene view README and CHANGELOG.md are updated. Mode transitions now notify the
  panel (it previously refreshed only in the paint loop, which the first panel tests exposed).
- **4.4** `RayViewFeed` reads the renderer's preview-applied content, camera, lights and poses and converts them on a
  worker; it writes no file. `SceneViewPanelRayTest` checks the scene file bytes and document text are identical across
  toggles and that selection, view camera and a drag preview survive on, off and a device loss; `RayViewFeedTest` (10)
  covers edits reaching the renderer as transform-only frames, a stalled converter never blocking the render thread
  (200 frames in under a second, one coalesced drain), asset and limit fallbacks, reset and release on off, the HDR sky
  and ambient colours reaching the renderer and a procedural sky degrading to the background.
- **4.5** `RayBackendSelectorTest` 10/10 and `RayDeviceLossTest` 3/3 (loss clears every view within a second, only an
  explicit retry re-probes, the EDT is never blocked). A new `DISABLED` reason makes `backend=off` explain itself instead
  of silently un-toggling. Dispatch sizes are bounded by `RayQualityPolicy` (at most 2,097,152 worst-case rays per batch
  and 4,194,304 pixels per frame, `RayQualityPolicyTest`); Metal submits one bounded dispatch per frame.
- **4.6** `RayRenderLifecycleTest` (9): a completion after close is never published and the session is disposed,
  a hidden view submits nothing and resumes without stale frames, a replaced scene generation rejects old frames, a render
  failure clears publication and disposes the session and Retry recovers, a preparation failure leaks no session,
  independent sessions per view, ordinary raster startup builds no provider, `backend=off` builds none even when toggled,
  and no native work runs on the EDT.

A stale-class `NoSuchMethodError` appeared when `RaySceneAssetState.Ready` gained a defaulted `sky` parameter; it now has
`@JvmOverloads`, so existing two-argument callers stay binary compatible.

Full run: `./gradlew check -Dabyssus.metalTests=true -Dabyssus.glTests=true --continue` fails only in the same 10
pre-existing fixture-drift plugin tests (`Untitled` has nine entities and moved cameras; listed above) and
`VulkanNativePackagingTest` (no glslang here). The ray plugin suites above and `RayFrameCompositionGlTest` ran with no skips.

## Task 3.4 completed: sky transfer, tone mapping and HDR diffuse (2026-10-04)

Reopened earlier because only the backend half existed. Now implemented and tested (Metal, fake and plugin; Vulkan skipped):

- **Transfer.** `core` reads a sky into a `RaySkySnapshot` (equirect RGBA float, at most 1024 wide) off the GL thread:
  an HDR image decoded again on the CPU and box-filtered (linear radiance above 1 kept), or the six cube faces resampled
  with the GL cube lookup. `RaySkySnapshotTest` (6) pins HDR range, downsampling, every face orientation (-Z centre,
  +X/-X quarter turns, +Z across the seam, +Y top row, -Y bottom row), procedural/missing skies as unavailable, and lease
  sharing/release/budget. A procedural sky is arbitrary asset GLSL, so `RaySkyBaker` renders the built sky into six faces on
  the render thread (once per sky and sun direction) and resamples them; `RaySkyBakerGlTest` (opt-in GL) paints each pixel's
  world direction and checks it lands where the raster sky shows it. A sky that is loading or cannot be transferred shows
  the background colour and never fails the view (`RayViewFeedTest`).
- **Display versus radiance.** Raster tone maps the visible HDR sky but leaves reflected radiance linear, so the kernel
  tone maps only primary misses when `RayEnvironment.hdr` (`hdrSkyIsToneMappedWhenSeenDirectlyAndLinearWhenReflected`,
  plus `RayMaterialTest`).
- **HDR diffuse vs ambient.** `RayEnvironment.ambientCube` carries the HDR sky's six axis colours and the kernel and
  reference blend them exactly as the raster model shaders do (`hdrAmbientCubeLightsSurfacesByNormalLikeTheRasterModelShader`);
  the renderer passes the built sky's ambient to the feed. The raster shader's rule (a non-negative normal component takes
  the odd slot) is reproduced literally so the two modes agree; it was not changed.
- **Fog and disabled sky** are covered by `fogFollowsHitDistanceAndLeavesTheSkyUnfogged` and
  `disabledOrMissingSkyShowsTheBackground`; the raster `SceneRenderGlTest` passes 27/27 with Ray Tracing off.

The task text names `RayEnvironmentBackendTest`; those cases live in `RayBackendConformanceKit` (run by the fake and Metal
suites) rather than a class of that name.

Observed twice: Kotlin's incremental compile left an already-compiled test calling a constructor that gained a defaulted
parameter (`NoSuchMethodError`). It is not a code defect; a forced test recompile (`--rerun-tasks :compileTestKotlin`)
and a clean pass confirmed it. `RaySceneAssetState.Ready` also gained `@JvmOverloads`.

Latest results: `:core:check` 112 tests, 0 failures; `:raytracing:test -Dabyssus.metalTests=true` fake and Metal kits
39/39 each plus the rest, failing only `VulkanNativePackagingTest` (no glslang); plugin ray, panel and GL suites 88 tests,
1 opt-in timing test skipped, 0 failures.

## Plugin zip inspection on macOS arm64 (partial task 5.4, 2026-10-04)

`./gradlew buildPlugin` succeeds here (`build/distributions/abyssus-0.0.1.zip`, 38.7 MB). Contents checked:
`raytracing.jar` holds `native/macos-arm64/` and `native/macos-x86_64/` with `libabyssus_ray.dylib` and `slice.metallib`
(the x86_64 pair is cross-built and has never been loaded); `lwjgl-vma` natives for linux, windows, macos and macos-arm64;
`lwjgl-vulkan` natives for macOS only; no `lwjgl-shaderc`. There is no `native/vulkan/slice.spv` because neither
`glslangValidator` nor `glslc` is installed on this host, so the Vulkan backend would report "initialization failed" from
this zip. Loading the zip on macOS, Windows and Linux, the no-Vulkan-loader start-up, the tooltip per device and the
device/driver matrix were not performed, so 5.4 stays open.

## Open manual checks and exact steps (not performed: they need a person at the GUI)

Use a copy of the fixture, never `Untitled` itself: `cp -R src/test/testData/project/Untitled /tmp/abyssus-copy`, then
`./gradlew runIde -PideProject=/tmp/abyssus-copy`. Record the OS, GPU, driver and outcome per platform here.

- **1.8 (30-second gate).** Run `./gradlew runIde -PideProject=/tmp/abyssus-copy -PrayExperiment=true`, open a scene, size
  the Scene view to 1280x720 framebuffer pixels (mind HiDPI), enable **Metal feasibility preview** and orbit continuously for
  30 s. `idea.log` prints throughput, p95 offer/upload/draw cost and offer-to-draw latency every 30 s. Pass: at least 30
  presented fps, p95 latency below 100 ms, EDT submission/upload within 8 ms, depth correct against the grid. Repeat on each OS.
- **5.1.** Without the flag, open the scene and switch on **Ray Tracing** (status Checking, Preparing, then Active; the
  tooltip names the backend and GPU). Move, rotate and drop an object; add a light and drag it continuously; orbit and pan;
  choose Camera 4 in the camera selector; then Undo and Redo. Shadows and reflections must update during each interaction,
  and the camera, selection and controls must stay usable. Switch it off and confirm raster returns.
- **5.2.** In a synthetic scene (copy first) place a metal PBR floor or sphere with geometry and terrain outside the picture,
  roughness variants, an alpha-tested leaf and a blended pane, then try an HDR sky, a non-HDR (cube) sky, no sky and fog, and
  finally the Animated model with two instances. Compare shadows, reflections and sky against the specs and capture
  comparable images with their tolerances.
- **5.3.** Resize and HiDPI, hide and show the tab, minimize and restore the IDE, two Scene views at once, asset loading and
  deletion, closing the view during preparation, an unsupported device (force one with
  `-Dabyssus.raytracing.backend=vulkan` on a Mac without usable Vulkan ray tracing) and an injected recoverable failure.
  Expect no stale images, editing always available, and the toggle's Retry working.

## Runtime switch in Abyssus Properties (follow-up to 4.3, 2026-10-04)

Requested after 4.3: a Ray Tracing switch in the scene properties that works from the Properties panel. A selected scene row
now shows `SceneDetailsView` (it previously showed an empty "Nothing to show" state, so `AssetPropertiesPanelTest` now expects
`PanelState.SceneDetails`; a project file still shows the empty state). The switch talks to the open Scene View through the
project service `SceneRayControls` and the `RayControl` each `SceneViewPanel` implements; `RayModeState` now has a listener
list, so the toolbar and the Properties switch both follow one state. Nothing is persisted and no file is written.

`SceneRaySwitchTest` (11, all passing): idle defaults, a pending request that opens the Scene View and is applied when it
registers and can be cancelled, flipping and following the mode (Checking, Preparing, Active), toolbar changes reflected,
unavailable hardware (disabled with the reason), failure with Retry, a closed view returning the switch to idle, every open view
of a scene flipped together, a dropped panel no longer updating, and an end-to-end case with a real `SceneViewPanel`,
`SceneFileEditor`, service and Properties panel in which the Properties switch drives the view, the toolbar toggle follows (and
the reverse), and the scene bytes are unchanged. Affected suites: 152 tests, 0 failures, 8 skipped (opt-in GL and timing). Not
run: the GUI itself in `runIde`.

## Fix: real scenes hit the resource limits (2026-10-04)

Reported from the Properties switch: "Ray tracing stopped: RESOURCE_LIMIT: Scene exceeds instance, triangle or byte limits".
Measuring the fixture Main Scene showed the caps were sized for toy scenes, and raising only the one that tripped would have
exposed two more:

| Need | Fixture scene | Old limit |
|---|---|---|
| instances = distinct meshes | 237 | 128 (snapshot cap and the Metal bridge) |
| blended instances | 12 | 4 |
| textures | 10.5M texels (2x 2048², 2x 1024², 256²), 169 MB as floats | 32 MiB payload |

(triangles 700,994 and 15 MB of geometry were already inside their bounds.)

Changes: `RayCapabilities.maxInstances` (Metal 1024, Vulkan unchanged at 128) is used by the service for each session and by
the bridge; `RAY_MAX_BLENDED_INSTANCES` 4 -> 32 with 16 shader blend layers; `RayTexture` can hold RGBA8 bytes, the plugin
converter keeps image textures as bytes, and the Metal payload is floats followed by RGBA8 texels in one buffer (header slot
29), with bounds of 16M floats and 16M texels; the fallback message now names the bound and the amounts, localized, and the
Properties panel no longer repeats a failure's reason on a second line.

New tests: five kit cases on both the fake and Metal (`byteTexturesRenderLikeTheSameFloatTextures`,
`aLargeByteTextureIsAcceptedWithoutExpandingToFloats`, `scenesPastTheOldInstanceCapRenderWithTheBackendsOwnCapacity` with 300
instances, `manyDistinctMeshesPastTheOldCapAreBuiltAndHit` with 237 meshes, `aDozenBlendedLayersCompositeWithinTheLimit`)
and `RayRealSceneTest`, which converts the fixture Main Scene with the default limits and, with `-Dabyssus.metalTests=true`
(now forwarded to the plugin test task), renders it through the real Metal backend. A first run of the new texture cases failed
on my own expectations (ambient 0.1 multiplies the colour; the quad did not cover the outer columns), identical on both
backends. Measured on the M1 Pro at 640x360: first capture 101 ms, a recapture 2.8 ms (cached meshes and textures), first Metal
frame 592 ms (237 structures and the texture upload), then 5-6 ms per frame for a static scene.

Results: kits 44/44 on the fake and on Metal; `RayRealSceneTest` 2/2 with nothing skipped; `SceneRaySwitchTest` 11/11.
Vulkan has no scene path, so none of this applies to it.

## Toolbar Ray Tracing button removed (2026-10-04)

Requested: no Ray Tracing button at the top of the Scene View. The toggle, its inline status label and the Retry button are
gone from `SceneViewPanel`'s toolbar; the Properties switch is now the only control, and it carries the status, the reason and
Retry. `RayControl` and `SceneRayControls` are unchanged. This supersedes the toolbar parts of tasks 4.3 and 4.5 (the toggle,
its tooltip and its states); their behaviour is unchanged and is now reached through the Properties switch and `RayModeText`.
`SceneViewPanelRayTest` (7) drives the view's `RayControl` directly and has a new case asserting the toolbar holds no ray
control (nothing named `ray-*`, no "Ray Tracing" text); `SceneRaySwitchTest` (11) asserts the Scene View has no button and that a
change made through the view's control flips the Properties switch back. Two unused message keys were removed.
