# Resolved baseline failures — 2026-10-06

The authorized continuation now passes `./gradlew check --continue --console=plain`: editor 917 tests,
zero failures, 40 skipped; runtime 99 tests, zero failures. All module checks pass. Test-owned fixture repairs
and test-runtime native binaries belong to this change; the four approved production repairs belong to
`repair-ray-editor-regressions`. Log: `/private/tmp/abyssus-solid-continuation-check.log`.

The following list is historical, retained to trace the failures rather than represent the current result.

Final command: `./gradlew check --continue --console=plain`.

Editor: 916 tests, 41 failures, 40 skipped. Runtime: 99 tests, 6 failures.

These assertions were retained; no current production logic was changed to force a pass.

## Editor

- `net.nevinsky.abyssus.AbyssusViewTest.testNodeTree`: junit.framework.AssertionFailedError: expected:<[ambientLight, fog, skybox: skybox_physical, rayTracing: null, ecs  9 entities]> but was:<[ambientLight, fog, skybox: skybox_physical, rayTracing: null, ecs  11 entities]>
- `net.nevinsky.abyssus.dto.ProjectAssetsTest.testFixtureProjectListsNineAssetsInNameOrderWithTypes`: junit.framework.AssertionFailedError: expected:<9> but was:<10>
- `net.nevinsky.abyssus.ecs.scene.AssetEntitiesTest.aModelBecomesTheNextEntityAndNothingElseChanges`: org.junit.ComparisonFailure: expected:<[9]> but was:<[11]>
- `net.nevinsky.abyssus.ecs.scene.AssetEntitiesTest.aTerrainIsCentredOnThePoint`: org.junit.ComparisonFailure: expected:<Terrain [9]> but was:<Terrain [11]>
- `net.nevinsky.abyssus.ecs.scene.SceneEntitiesTest.theNextIdIsOneAboveTheHighestAndAnEmptySceneStartsAtZero`: org.junit.ComparisonFailure: expected:<[9]> but was:<[11]>
- `net.nevinsky.abyssus.projectView.AddAssetActionTest.testAddingAModelWritesTheNextEntityAndUndoRestoresTheScene`: junit.framework.ComparisonFailure: expected:<[9]> but was:<[11]>
- `net.nevinsky.abyssus.projectView.AddAssetActionTest.testATerrainFromTheTreeIsCentredOnTheOrigin`: junit.framework.AssertionFailedError: expected:<-800.0> but was:<0.0>
- `net.nevinsky.abyssus.projectView.AddComponentOnEcsTest.testRenderOffersTheProjectsAssets`: junit.framework.ComparisonFailure: expected:<[tree]> but was:<[model_extra500]>
- `net.nevinsky.abyssus.projectView.AddComponentOnEcsTest.testAnEntityRowStillAddsToThatEntity`: junit.framework.AssertionFailedError: no new entity
- `net.nevinsky.abyssus.projectView.AddComponentOnEcsTest.testCameraCreatesANamedEntityAndUndoRestoresTheFile`: junit.framework.ComparisonFailure: expected:<[Entity] 9> but was:<[Model] 9>
- `net.nevinsky.abyssus.projectView.SkyboxChoicesTest.testListsTheHdrFixture`: junit.framework.AssertionFailedError: expected:<HdrSkyInfo(file=sky.exr, width=1024, height=512)> but was:<HdrSkyInfo(file=sky.exr, width=0, height=0)>
- `net.nevinsky.abyssus.projectView.SkyboxChooserDialogTest.testHdrEntryShowsOneThumbnail`: java.lang.NullPointerException
- `net.nevinsky.abyssus.properties.AbyssusSelectionTest.testEveryAssetRowResolves`: junit.framework.AssertionFailedError: expected:<9> but was:<10>
- `net.nevinsky.abyssus.properties.AssetPropertiesPanelTest.testUnreadableHdrShowsAPlaceholderAndRows`: junit.framework.AssertionFailedError: Cannot read sky.exr: Could not initialize class org.lwjgl.util.tinyexr.TinyEXR
- `net.nevinsky.abyssus.properties.AssetPropertiesPanelTest.testSceneRaySettingsUndoAndRedoFromThePanelFollowTheSavedScene`: java.lang.NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.JsonNode.get(String)" because the return value of "com.fasterxml.jackson.databind.JsonNode.get(String)" is null
- `net.nevinsky.abyssus.properties.AssetPropertiesPanelTest.testUndoAndRedoFromThePanelRestoreAndReapplyAnEdit`: junit.framework.AssertionFailedError: nothing to undo yet
- `net.nevinsky.abyssus.properties.AssetPropertiesPanelTest.testHdrShowsOnePreviewLabelledWithSize`: junit.framework.ComparisonFailure: expected:<[sky.exr · 1024 × 512]> but was:<[Cannot read sky.exr: Could not initialize class org.lwjgl.util.tinyexr.TinyEXR]>
- `net.nevinsky.abyssus.properties.RayMaterialPropertiesTest.testOnlyEntitiesOwnInstanceChangesAndTheModelStaysUntouched`: java.lang.NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.JsonNode.get(String)" because the return value of "net.nevinsky.abyssus.properties.RayMaterialPropertiesTest.materials(com.intellij.openapi.vfs.VirtualFile, String)" is null
- `net.nevinsky.abyssus.properties.RayMaterialPropertiesTest.testTransmissionIsEditedInPercentAndStoredAsAFractionWithDefaultIorOmitted`: java.lang.NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.JsonNode.get(String)" because the return value of "net.nevinsky.abyssus.properties.RayMaterialPropertiesTest.materials(com.intellij.openapi.vfs.VirtualFile, String)" is null
- `net.nevinsky.abyssus.properties.RayMaterialPropertiesTest.testExternalChangesAndUndoRedoAreReadBack`: junit.framework.ComparisonFailure: expected:<[10]0> but was:<[]0>
- `net.nevinsky.abyssus.properties.RayMaterialPropertiesTest.testOverridesForMaterialsTheModelNoLongerHasAreShownAndKept`: java.lang.NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.JsonNode.get(String)" because the return value of "com.fasterxml.jackson.databind.JsonNode.get(String)" is null
- `net.nevinsky.abyssus.properties.SceneRaySettingsPanelTest.testInvalidAndSupersededEditsShowErrorsWithoutOverwritingTheDocument`: java.lang.NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.JsonNode.get(String)" because the return value of "com.fasterxml.jackson.databind.JsonNode.get(String)" is null
- `net.nevinsky.abyssus.properties.SceneRaySettingsPanelTest.testDefaultsAndValidEditsWorkWithoutAViewAndSelectionDoesNotWrite`: java.lang.NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.JsonNode.get(String)" because the return value of "com.fasterxml.jackson.databind.JsonNode.get(String)" is null
- `net.nevinsky.abyssus.properties.SceneRaySettingsPanelTest.testMalformedSettingsAreVisibleAndExternalChangesAreRead`: junit.framework.ComparisonFailure: expected:<[2]> but was:<[null]>
- `net.nevinsky.abyssus.properties.SceneRaySwitchTest.testUnavailableHardwareDisablesTheSwitchAndExplainsWhy`: java.lang.NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.JsonNode.get(String)" because the return value of "com.fasterxml.jackson.databind.JsonNode.get(String)" is null
- `net.nevinsky.abyssus.sceneview.RayRealSceneTest.theFixtureSceneIsInsideEveryRayTracingBound`: java.lang.AssertionError: the scene's assets must load: Failed(failures={model:model_extra500=java.lang.IllegalArgumentException: CPU model snapshot exceeds its 134217728 byte limit})
- `net.nevinsky.abyssus.sceneview.RaySceneSnapshotTest.malformedSettingsPreventRayConversionButKeepOrdinarySceneParsing`: java.lang.AssertionError: expected null, but was:<SceneRaySettings(targetSamplesPerPixel=256, maxRaysPerFrame=2097152, maxReflectionBounces=1, maxRefractionBounces=0)>
- `net.nevinsky.abyssus.sceneview.RaySettingsRevisionTest.offFutureAndTwoViewsUseTheirCurrentSavedSettingsWithoutEnablingEachOther`: java.lang.AssertionError: Timed out waiting for ray state
- `net.nevinsky.abyssus.sceneview.RaySettingsRevisionTest.settingsClearOldPublicationBeforeAStalledConversionAndReuseTheSession`: java.lang.AssertionError: Timed out waiting for ray state
- `net.nevinsky.abyssus.sceneview.RayViewFeedTest.resetDiscardsOlderFramesAndTurningOffReleasesTheCompanions`: java.lang.AssertionError: Timed out waiting for a ray frame
- `net.nevinsky.abyssus.sceneview.RayViewFeedTest.anActiveViewPresentsFramesCarryingTheCameraAndContentTheyWereRenderedFor`: java.lang.AssertionError: Timed out waiting for a ray frame
- `net.nevinsky.abyssus.sceneview.RayViewFeedTest.editsAndPreviewsReachTheRendererThroughContentAndOnlyChangeTransforms`: java.lang.AssertionError: Timed out waiting for a ray frame
- `net.nevinsky.abyssus.sceneview.RayViewFeedTest.theScenesHdrSkyAndItsAmbientColoursReachTheRenderer`: java.lang.AssertionError: Timed out waiting for a scene with the transferred sky
- `net.nevinsky.abyssus.sceneview.SceneContentTest.mainSceneHasThreeModelsAndOneTerrain`: java.lang.AssertionError: expected:<[model_29e9be61-6594-4f82-a6cf-44ccf09f71fb, model_fc33e1f1-015b-4524-9b10-aa417acd273c, model_900f6f61-6384-434a-be81-56ce303fbb56]> but was:<[model_29e9be61-6594-4f82-a6cf-44ccf09f71fb, model_fc33e1f1-015b-4524-9b10-aa417acd273c, model_900f6f61-6384-434a-be81-56ce303fbb56, model_extra500]>
- `net.nevinsky.abyssus.sceneview.SceneContentTest.mainSceneHasTheFixtureCamera`: java.lang.AssertionError: expected:<[7, 8]> but was:<[7, 8, 10]>
- `net.nevinsky.abyssus.sceneview.SceneEntityParityTest.lightPlacementsEqualThePanelValues`: java.lang.AssertionError: expected:<[7, 8]> but was:<[7, 8, 10]>
- `net.nevinsky.abyssus.sceneview.SceneMarkersTest.theViewCameraHasNoMarkerTarget`: java.lang.AssertionError: expected:<[7, 8]> but was:<[7, 8, 10]>
- `net.nevinsky.abyssus.sceneview.SceneMarkersTest.aCameraDrawsABodyAndAFrustum`: java.lang.AssertionError: expected:<26> but was:<39>
- `net.nevinsky.abyssus.sceneview.SceneRaySettingsEditTest.testOpticalOverrideHasExactUndoAndExpectedValueChecks`: junit.framework.AssertionFailedError: expected:<Changed> but was:<Rejected(error=OBJECT)>
- `net.nevinsky.abyssus.sceneview.SceneRaySettingsEditTest.testSceneEditsAreOneCommandWithExactUndoRedoAndNoOtherWrites`: junit.framework.AssertionFailedError: expected:<Changed> but was:<Rejected(error=OBJECT)>
- `net.nevinsky.abyssus.sceneview.SceneRaySettingsEditTest.testStaleInvalidEqualAndUnsupportedEditsWriteNothing`: junit.framework.AssertionFailedError: expected:<Conflict> but was:<Rejected(error=OBJECT)>

## Runtime

- `net.nevinsky.abyssus.runtime.RuntimeSceneLoaderTest.unsavedTextLoadsWithoutReadingTheFile`: java.lang.AssertionError: expected:<[0, 1, 2, 3, 4, 5, 6, 7, 8]> but was:<[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]>
- `net.nevinsky.abyssus.runtime.RuntimeSceneLoaderTest.twoLoadsShareNothing`: java.lang.AssertionError: expected:<[0, 1, 2, 3, 4, 5, 6, 7, 8]> but was:<[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]>
- `net.nevinsky.abyssus.runtime.RuntimeSceneLoaderTest.mainScenesEntitiesLoadOutsideTheIde`: java.lang.AssertionError: expected:<[0, 1, 2, 3, 4, 5, 6, 7, 8]> but was:<[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]>
- `net.nevinsky.abyssus.runtime.ecs.EcsLoaderTest.mainSceneLoads`: java.lang.AssertionError: expected:<9> but was:<11>
- `net.nevinsky.abyssus.runtime.ecs.EcsLoaderTest.unmodeledComponentsAreLoggedOnceToTheCallersLog`: java.lang.AssertionError: expected:<9> but was:<11>
- `net.nevinsky.abyssus.runtime.ecs.EcsWriterTest.mainSceneWritesBackEqual`: java.lang.AssertionError: the block is the entity map, in ascending id order expected:<[0, 1, 2, 3, 4, 5, 6, 7, 8]> but was:<[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]>
