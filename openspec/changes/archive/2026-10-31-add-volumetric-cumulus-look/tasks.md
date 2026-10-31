# Tasks

## 1. Measure and prepare

- [x] 1.1 Add `VOLUMETRIC` to `FieldSkyGlTest`'s techniques, print its frame time for both views, and record the numbers beside the others; verify with `./gradlew :app-game-control-line:test --tests '*FieldSkyGlTest' -Dabyssus.glTests=true` and a saved `volumetric-sky.png`.
- [x] 1.2 Set `clouds_fair` to `"technique": "VOLUMETRIC"` and extend `CloudsAssetTest` to assert it; run `./gradlew :app-game-control-line:test --tests '*CloudsAssetTest'`.

## 2. Heaped volumetric look (lib-core)

- [x] 2.1 Shape heaped bands in `clouds_volumetric.frag` (flat base, heads lifted by the base noise, crisper edge erosion) without touching other profiles; verify with a GL test in `CloudTechniqueGlTest` that a heaped band has a bright top, a darker base, and that a cirrus band's image is unchanged (`./gradlew :lib-core:test --tests '*CloudTechniqueGlTest' -Dabyssus.glTests=true`).
- [x] 2.2 Strengthen the silver rim and the grey base lighting for heaped bands; verify with a GL test that the pixels around a cloud edge toward the sun are brighter than its interior.
- [x] 2.3 Tune against `Cumulus_AMayes.jpg` using the game sky screenshots until the heads, bases and edges match by eye; verify the saved screenshots and note the final coverage and density in the game README.
- [x] 2.4 Document the heaped volumetric look in `projects/lib-core/README.md` and `docs/ai/` where volumetric is described; run `scripts/check-docs.sh`.

## 3. Game step-down (app-game-control-line)

- [x] 3.1 Add a plain `CloudFrameBudget` class (frame times in, a step-down decision out) with a unit test covering fast frames, two seconds over 33 ms, and the chain to layered; run `./gradlew :app-game-control-line:test --tests '*CloudFrameBudgetTest'`.
- [x] 3.2 Use it in `FieldRenderer`: step down through `simplerThan`, log once, never go back; verify with a test that feeds slow frame times and sees the next technique requested (`./gradlew :app-game-control-line:test --tests '*FieldRenderer*'`).
- [x] 3.3 Measure the sky's frame time with volumetric and update the game README's cloud paragraph with the numbers and the step-down; run `scripts/check-docs.sh`.

## 4. Integration

- [x] 4.1 Run `./gradlew check`, `scripts/check-docs.sh` and `openspec validate add-volumetric-cumulus-look --strict`, and confirm all pass.
