# Verification notes

## Stage 3

- Package moves need a clean build (`./gradlew :clean :editor-core:clean :physics-plugin:clean`): stale classes of the
  old packages otherwise fail tests with `NoSuchMethodError` / `NoClassDefFoundError`.
- 3.5 (GL): `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneRenderGlTest' -Dabyssus.glTests=true` runs 27
  tests: 21 pass and 6 fail. The same 6 fail with the same messages at `bc901ea` (before stage 2), so they are not
  caused by this change:
  - `aBlueSkyTintsTheTerrain`, `aRedSkyReplacesTheAmbientOnAModel`, `lightEntitiesChangeTheShading`,
    `pbrModelIsBrighterUnderAnUpperSkyThanUnderALowerOne`: the tests cast `ecs.entities` of the Untitled fixture to an
    object, but that fixture uses the native `ecs.<id>` layout (NullPointerException in the test's own edit).
  - `hdrHighlightsAreDistinct`, `hdrMidGreyDrawsAt140`: the HDR sky draws at 26 where the tests expect distinct
    highlights and 140.
