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
- `net.nevinsky.abyssus.sceneview.SceneContentTest.mainSceneHasThreeModelsAndOneTerrain`: java.lang.AssertionError
- `net.nevinsky.abyssus.sceneview.SceneContentTest.mainSceneHasTheFixtureCamera`: java.lang.AssertionError: expected:<Vec3(x=0.0, y=0.0, z=0.0)> but was:<Vec3(x=0.0, y=5.520455, z=0.0)>
- `net.nevinsky.abyssus.sceneview.SceneMarkersTest.theViewCameraHasNoMarkerTarget`: java.lang.AssertionError
- `net.nevinsky.abyssus.sceneview.SceneMarkersTest.aCameraDrawsABodyAndAFrustum`: java.lang.AssertionError: expected:<28> but was:<54>
- `net.nevinsky.abyssus.sceneview.SceneRendererCameraTest.lookingThroughACameraUsesItsPositionDirectionAndLens`: java.lang.AssertionError: expected:<0.8857895> but was:<0.96025884>
- `net.nevinsky.abyssus.sceneview.SceneTransformWriterTest.movingAnEntityChangesOnlyItsLocalPositionX`: org.junit.ComparisonFailure: expected:<[3.086434]> but was:<[0.9123962]>

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
