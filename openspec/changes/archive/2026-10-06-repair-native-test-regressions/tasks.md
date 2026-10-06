# Tasks

## 1. Test repairs

- [x] 1.1 Repair project/tree, ECS component and action tests to current native JSON and DTO expectations; verify AbyssusViewTest, ComponentEditorTest, ComponentActionsTest, NewTerrainActionTest and SkyboxChooserDialogTest.
- [x] 1.2 Repair scene-view regression test setup and exact expectations; verify `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.*'`.
- [x] 1.3 Repair properties and terrain regression tests; verify `./gradlew :test --tests 'net.nevinsky.abyssus.properties.*' --tests 'net.nevinsky.abyssus.terrain.*'`.
- [x] 1.4 Isolate runtime ECS/scene regression inputs from the concurrently edited Untitled scene, retaining every entity, warning and round-trip assertion; verify `./gradlew :runtime:test`.

## 2. Integration

- [x] 2.1 Review the test-only diff for unchanged production logic and retained assertions; run `./gradlew check` and `scripts/check-docs.sh`, recording counts and any remaining failures.
