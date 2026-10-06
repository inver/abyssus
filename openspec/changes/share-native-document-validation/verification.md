# Verification — 2026-10-06

All four tasks are complete. The prerequisite is not archived. The blocked results below are historical;
the authorized continuation now passes the full integration gate.

## Regression evidence

- Core admission regression: three tests failed against the original readers (project/scene accepted unsupported headers; saved metadata accepted missing identity). After adding shared admission, `./gradlew :core:test` passed: 176 tests, 13 skipped, zero failures.
- Raw ECS regression: reserved-field admission and writer rejection failed against the original implementation. `./gradlew :runtime:test` passes after validation precedes engine mutation and output is checked. Opaque extension content round-trips; both short and fully qualified built-in render component names reject legacy fields.
- Unsaved metadata regression failed before the editor guard. The final test also checks missing/foreign/fractional/future headers, identical saved/unsaved format reasons, no asset preparation, no fallback and unchanged disk text.
- Final focused command passed: `./gradlew :physics-plugin:clean :physics-plugin:test :runtime:test :test --tests '*AssetLoadingTest' --tests '*NativeDocumentReadTest' --tests '*NativeDocumentWriteGuardTest' --tests '*AssetMetaReaderTest' --tests '*ProjectAssetsTest'`. It includes all runtime and physics-plugin tests plus 23 targeted editor tests. Clean builds resolved stale constructor calls in compiled plugin/extension tests.
- `scripts/check-docs.sh` passed: 188 paths in 6 files.
- Strict validation of both `share-native-document-validation` and `refactor-solid-dedup` passed. `git diff --check` passed.
- Fresh-context review found no important correctness regressions. Its two coverage suggestions (matching format reasons and carried legacy renderable rejection) were added and verified.

## Historical blocked integration gate

`./gradlew check --continue` failed. Log: `/private/tmp/abyssus-native-validation-check.log`.

- `checkNoRunCatching`: existing UUID calls in `AssetIndex`, `AssetMetaLoader`, `TerrainLoader` and `UnsavedMetaLoader`. These are already planned in refactor-solid-dedup phase 2 and were left outside this prerequisite's behavior change.
- Control Line `AirfieldEnvironmentTest`: missing `model_airfield_apartment_block/textures/apartment_plinth_color_512.jpg` and `terrain_airfield_surroundings/source.json`. The ongoing airfield assets and scene changes were not altered.
- Root plugin suite: 880 tests, 69 failures, 40 skipped. Examples include expectations for nine scene fields despite existing ray fields, equality of randomly generated scene IDs, and tests expecting wrapped ECS entity maps from an unwrapped fixture. Other failures need separate diagnosis; no clean baseline checkout was run to classify every failure.
- Initial physics-plugin failures called an obsolete `FileLoader` constructor from cached test classes. Rebuilding that module resolved all five failures; its 11 tests now pass.

The user subsequently instructed continuation. The four UUID runCatching violations were fixed under refactor phase 2.

## Passing integration after authorized prerequisites

`./gradlew check --continue --console=plain` passes with 1,644 tests, zero failures/errors and 177 skipped.
Editor: 917/40 skipped; core: 216/13; runtime: 99/0; physics: 39/0; physics-plugin: 11/0;
raytracing: 229/114; gdx-model: 27/9; Control Line: 106/1. Counts include skipped tests.
Source checks and coverage verification pass. Log: `/private/tmp/abyssus-solid-continuation-check.log`.
Docs checking passes for 199 paths; strict OpenSpec validation passes. Task 3.1 is now complete.
