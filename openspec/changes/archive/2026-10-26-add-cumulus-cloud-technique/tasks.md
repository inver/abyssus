# Tasks

## 1. Technique plumbing (lib-core)

- [x] 1.1 Add `CUMULUS("cumulus")` to `CloudTechnique` and route `simplerThan` as volumetric, shells, cumulus, layered; extend the `CloudTechniques` unit tests and run `./gradlew :lib-core:test --tests '*CloudTechniques*'`.
- [x] 1.2 Make `CloudSettingsReader` read `"technique": "cumulus"` and add a case to `CloudSettingsReaderTest`; run `./gradlew :lib-core:test --tests '*CloudSettingsReaderTest'`.
- [x] 1.3 Add a `CumulusClouds` renderer (a stub drawing the `layered` shader) wired in `ProceduralSky`'s technique `when`, so every enum value is handled; run `./gradlew :lib-core:test`.

## 2. Cumulus shader (lib-core)

- [x] 2.1 Write `clouds_cumulus.frag` per design decisions 2 and 3 and use it in `CumulusClouds`; verify with `CloudTechniqueGlTest.cumulusHasBrightTopsGreyBasesAndABlueSky` (`./gradlew :lib-core:test --tests "*CloudTechniqueGlTest" -Dabyssus.glTests=true`) asserting a near-white sunlit top, a darker base below it and a blue sky outside clouds.
- [x] 2.2 Add a compile-failure case: a broken shader makes the sky draw layered clouds and logs once; verify in the existing fallback test class (`./gradlew :lib-core:test --tests '*ProceduralSky*'`).
- [x] 2.3 Document `cumulus` in `projects/lib-core/README.md` and `docs/ai/` where techniques are listed; run `scripts/check-docs.sh`.

## 3. Editor toolbar (plugin-abyssus)

- [x] 3.1 Add *Cumulus* to `CloudControls`' choices with a `sceneViewCloudsCumulus` key in `AbyssusBundle.properties`; extend the existing cloud-controls test and run `./gradlew :plugin-abyssus:test --tests '*CloudControls*'`.

## 4. Control Line (app-game-control-line)

- [x] 4.1 Measure the game's frame rate with `layered` and `shells` in the same scene and note both in the README; verify the numbers are recorded.
- [x] 4.2 Set `clouds_fair` to `"technique": "cumulus"`, remove `GAME_CLOUDS` from `FieldRenderer` (or fix it to `CUMULUS` per design decision 5) and retune `skybox_physical/meta.json`; run `./gradlew :app-game-control-line:test` including `CloudsAssetTest`.
- [x] 4.3 Render a screenshot with `GameScreenshotsGlTest` (`-Dabyssus.glTests=true`), compare it with `Cumulus_AMayes.jpg` and adjust the shader and sky values until they match by eye; verify the screenshot is saved and the frame rate stays clearly above the `shells` figure.
- [x] 4.4 Update the game README's cloud paragraph (the `GAME_CLOUDS` note) and run `scripts/check-docs.sh`.

## 5. Integration

- [x] 5.1 Run `./gradlew check` and `scripts/check-docs.sh`, plus `openspec validate add-cumulus-cloud-technique --strict`, and confirm all pass.
