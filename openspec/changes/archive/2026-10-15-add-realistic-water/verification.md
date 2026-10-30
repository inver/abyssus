# Task 5.2 verification — 2026-10-15

Ran `./gradlew check -Dabyssus.glTests=true --console=plain` on the macOS host display. The command finished in 11m 58s with exit status 1 because seven plugin tests failed. Full local log: `/private/tmp/abyssus-water-task-5-2.log`.

## Water GL results

All 57 tests below passed, with zero errors or skips.

| Module | Suite | Passed |
|---|---|---|
| lib-gdx | ModelWaterPassGlTest | 9 |
| lib-core | WaterShaderGlTest | 4 |
| plugin-abyssus | SceneFrameSnapshotTest | 4 |
| plugin-abyssus | SceneWaterFallbackGlTest | 3 |
| plugin-abyssus | SceneWaterFoamGlTest | 6 |
| plugin-abyssus | SceneWaterPassGlTest | 5 |
| plugin-abyssus | SceneWaterReflectionGlTest | 9 |
| plugin-abyssus | SceneWaterResourcesGlTest | 8 |
| plugin-abyssus | SceneWaterShallowsGlTest | 5 |
| plugin-abyssus | WaterFailureTest | 4 |

## Failures outside the water suites

The plugin suite reported 1,070 tests, seven failures and 15 skips. These failures were recorded separately; no fixes or claims about their cause are included in this archival verification.

- `RaySkyBakerGlTest.bakedSkyPutsEveryWorldDirectionWhereTheRasterSkyShowsIt`: java.lang.AssertionError: expected:<1024> but was:<2048>
- `SceneRenderGlTest.hdrHighlightsAreDistinct`: java.lang.AssertionError: 1, 2, 4 drawn at 26, 26, 26
- `SceneRenderGlTest.aBlueSkyTintsTheTerrain`: java.lang.NullPointerException: null cannot be cast to non-null type com.fasterxml.jackson.databind.node.ObjectNode
- `SceneRenderGlTest.lightEntitiesChangeTheShading`: java.lang.NullPointerException: null cannot be cast to non-null type com.fasterxml.jackson.databind.node.ObjectNode
- `SceneRenderGlTest.aRedSkyReplacesTheAmbientOnAModel`: java.lang.NullPointerException: null cannot be cast to non-null type com.fasterxml.jackson.databind.node.ObjectNode
- `SceneRenderGlTest.hdrMidGreyDrawsAt140`: java.lang.AssertionError: mid grey drawn at 26
- `SceneRenderGlTest.pbrModelIsBrighterUnderAnUpperSkyThanUnderALowerOne`: java.lang.NullPointerException: null cannot be cast to non-null type com.fasterxml.jackson.databind.node.ObjectNode

## Scope

Task 5.2 is complete as a test-execution and result-recording task. The full GL-enabled check is **not passing**. Previously checked manual tasks were left unchanged; no manual checks were performed or newly marked complete in this session.
