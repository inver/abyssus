# Verification — 2026-10-06

Implementation tasks 1.1, 1.2 and 2.1 are complete. Integration task 3.1 remains open; the prerequisite is not archived. After the user instructed continuation, refactor-solid-dedup began its scoped fixes with integration gates retained as open.

## Regression evidence

- Core admission regression: three tests failed against the original readers (project/scene accepted unsupported headers; saved metadata accepted missing identity). After adding shared admission, `./gradlew :core:test` passed: 176 tests, 13 skipped, zero failures.
- Raw ECS regression: reserved-field admission and writer rejection failed against the original implementation. `./gradlew :runtime:test` passes after validation precedes engine mutation and output is checked. Opaque extension content round-trips; both short and fully qualified built-in render component names reject legacy fields.
- Unsaved metadata regression failed before the editor guard. The final test also checks missing/foreign/fractional/future headers, identical saved/unsaved format reasons, no asset preparation, no fallback and unchanged disk text.
- Final focused command passed: `./gradlew :physics-plugin:clean :physics-plugin:test :runtime:test :test --tests '*AssetLoadingTest' --tests '*NativeDocumentReadTest' --tests '*NativeDocumentWriteGuardTest' --tests '*AssetMetaReaderTest' --tests '*ProjectAssetsTest'`. It includes all runtime and physics-plugin tests plus 23 targeted editor tests. Clean builds resolved stale constructor calls in compiled plugin/extension tests.
- `scripts/check-docs.sh` passed: 188 paths in 6 files.
- Strict validation of both `share-native-document-validation` and `refactor-solid-dedup` passed. `git diff --check` passed.
- Fresh-context review found no important correctness regressions. Its two coverage suggestions (matching format reasons and carried legacy renderable rejection) were added and verified.

## Full integration gate remains blocked

`./gradlew check --continue` failed. Log: `/private/tmp/abyssus-native-validation-check.log`.

- `checkNoRunCatching`: existing UUID calls in `AssetIndex`, `AssetMetaLoader`, `TerrainLoader` and `UnsavedMetaLoader`. These are already planned in refactor-solid-dedup phase 2 and were left outside this prerequisite's behavior change.
- Control Line `AirfieldEnvironmentTest`: missing `model_airfield_apartment_block/textures/apartment_plinth_color_512.jpg` and `terrain_airfield_surroundings/source.json`. The ongoing airfield assets and scene changes were not altered.
- Root plugin suite: 880 tests, 69 failures, 40 skipped. Examples include expectations for nine scene fields despite existing ray fields, equality of randomly generated scene IDs, and tests expecting wrapped ECS entity maps from an unwrapped fixture. Other failures need separate diagnosis; no clean baseline checkout was run to classify every failure.
- Initial physics-plugin failures called an obsolete `FileLoader` constructor from cached test classes. Rebuilding that module resolved all five failures; its 11 tests now pass.

The unchecked integration task requires a passing full check. The user subsequently instructed continuation; the refactor tasks/design now record that baseline continuation, while integration gates stay open. The four UUID runCatching violations have been fixed under refactor phase 2.
