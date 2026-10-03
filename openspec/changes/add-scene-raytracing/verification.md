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
