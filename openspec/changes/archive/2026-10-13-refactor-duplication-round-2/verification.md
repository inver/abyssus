# Verification

## 1.1 Baseline (2026-10-12, `HEAD` `2297268c`)

`./gradlew check --continue`: BUILD SUCCESSFUL (exit 0), no failures.

| Module                | Tests | Skipped | Failed |
|-----------------------|-------|---------|--------|
| lib-core              | 454   | 65      | 0      |
| lib-core-editor       | 640   | 4       | 0      |
| lib-gdx               | 68    | 17      | 0      |
| lib-physics           | 65    | 0       | 0      |
| lib-raytracing        | 260   | 124     | 0      |
| plugin-abyssus        | 891   | 77      | 0      |
| app-game-control-line | 278   | 8       | 0      |

`scripts/check-docs.sh`: 248 paths OK.

B2 reproduced:

- `./gradlew :verifyPlugin`: `task 'verifyPlugin' not found in root project 'abyssus'`.
- `./gradlew :plugin-abyssus:verifyPlugin`: fails on `:plugin-abyssus:checkPluginInternalApis`, input file
  `projects/plugin-abyssus/gradle/plugin-internal-api-allowlist.txt` does not exist.

## Phase 1 (2026-10-12)

- 1.2: `./gradlew :plugin-abyssus:verifyPlugin` passes. Beyond the allowlist path, two things were needed for
  `checkPluginInternalApis` to pass: the allowlist entries used pre-move package names (renamed to
  `net.nevinsky.abyssus.plugin.*`), and `PhysicsSimulationProvider` called internal `PluginManagerCore.getPlugin` (the
  public-looking `PluginManager.findEnabledPlugin` is internal too). It now finds `play-host/` from its own jar through
  `PathManager.getJarPathForClass` (`<plugin>/lib/<jar>` → `<plugin>/play-host`, matching the built zip).
- 1.3: also qualified the `release.yml` and `run-ui-tests.yml` Gradle tasks and the distribution path. Workflows not run.
- 1.4: `MetalSlice.kt` deleted; no references; `:lib-raytracing:test` passed.
- 1.5: five `.DS_Store` files and root `src/` removed.

## Phase 3 (2026-10-12)

- 3.1: `FormatBytesTest` pins the SHA-256 of 46 fixture documents through `SceneJson.pretty`, `SceneJson.compact` and
  `JsonProcessor.pretty`, in `lib-core-editor/src/test/resources/format-bytes.sha256` (rewrite with
  `ABYSSUS_WRITE_GOLDEN=<absolute path>`). It fails on a changed digest (checked) and passed unchanged after 3.2 and 3.3.
- 3.2: `JsonFormat` now lives in `lib-core` `io/`; `JsonProcessor` and `SceneJson` both build on it.
- 3.3: `DocumentFormat.kt` (five typealiases) removed; its users import `lib.core.format`.
- 3.4: `AssetMetaEditor` uses `parseUuidOrNull`; new test `asset reference text must be a uuid`.
- 3.5: one `testProject` in `lib-core` `testFixtures`; `abyssus.testData` set once in the root `build.gradle.kts`.
- `./gradlew check` green after phase 3.

## Phase 4 (2026-10-12)

- 4.1: `FieldAssetsGlTest` (app-game-control-line, GL opt-in) loads the Untitled model, terrain (size 1600, uv 60.0),
  cube sky, procedural sky and HDR sky through `fieldAssets`; it passed before and after the switch.
- 4.2: `StandardAssetLoaders` (class, `lib-core` `assets/standard/`) with `StandardAssetLoadersTest`.
- 4.3: `AssetLoading.ProjectAssets`, `fieldAssets`, `BaseCtx` and `AssetLoadingGlTest` use it.
  `MetaType.SKYBOX_HDR to` now appears in the table and in `AssetMetaBinder`'s meta-class map only. `PhysicsAssets` needed no change.
  `./gradlew check` green; `:app-game-control-line:test :lib-core:test -Dabyssus.glTests=true` green.
- 4.4 (optional G2) not done; left open (open question 1).
- Pre-existing, unrelated: `:plugin-abyssus:test --tests '*SceneRenderGlTest' -Dabyssus.glTests=true` fails 6 of 29 on the
  untouched baseline as well (checked with the changes stashed), so GL pixel tests in this environment are not a
  usable signal for this change. A full `check -Dabyssus.glTests=true` also hung in the plugin module.

## Phase 6 (2026-10-12)

- 6.1: `TreeActionVisibilityTest` (5 cases) pins, for Rename Scene, Import FlightGear, Import Model, New Terrain and New UI
  Asset, that each is visible and enabled together on its own row only (scene row, or the Assets row), and not on an
  asset row, the project row or no selection. It replaces the separately named `*ActionTest` classes of the task. It
  was written after the refactor, from the old `update` logic (visible = enabled = target found).
  Existing `NewFoliageActionTest` and `NewTerrainActionTest` cover the other two.
- 6.2: those five plus `NewFoliageAction` now extend `AbyssusTreeAction`; `RenameSceneAction`, `NewTerrainAction`,
  `ImportFlightGearAction` and `ImportModelAction` became `open` like the existing tree actions, so tests can override
  `selected`. `NewFoliageActionTest` changed only the signature of its `owningProject` override (event → node).
  `NewFoliageAction` keeps hiding itself when the project has no terrain, by returning no target then.
  `currentProjectViewPane` now appears only in `selectedNode`.

## Phase 7 (2026-10-12)

- 7.1: `ComponentField.labelKey`/`tooltipKey` and `FieldValue.tooltip`; the four light texts moved to
  `AbyssusEditorBundle.properties` (same English); `EntityDetailsView` no longer names `LightComponent`.
  `ComponentEditorTest.spotLightFieldsCarryTheirLabelsAndTheRangeTooltip` covers entity 8 (`Spot Light 8`).
- 7.2: `ui/FieldEditors.kt` used by both panels; previews and `Thumbnail` in `AssetPreviews.kt`, row rendering in
  `AssetRows.kt`. `AssetPropertiesPanel.kt` 668 → 518 lines, `EntityDetailsView.kt` 363 → 330. The existing
  `AssetPropertiesPanelTest` and `EntityPropertiesPanelTest` passed unchanged.
- 7.3: six new cases (confirm by focus loss, refusal, equal value) in the two panel tests, on terrain `uv` and
  `Spot Light 8` `intensity`. The spec scenario was corrected to the real text, `30.0`.
- 7.4 (runIde) not run here. Steps for you, on a **copy** of `projects/plugin-abyssus/src/test/testData/project/Untitled`:
  (a) select the terrain, set `uv` to `30`, press Tab: the file shows `30.0`, Undo restores `60.0`;
  (b) enter `abc`: the old value returns with a red reason; (c) select `Spot Light 8`: the range label and tooltip read
  as before ("Range", "Distance a point or spot light reaches."); (d) set `intensity` to `2`: the scene view brightens.

## Phase 8 (2026-10-12)

- Before: `SceneViewPanel.kt` 882 lines, 13 constructor parameters (59 functions).
- After: 512 lines; new files `SceneInputForwarder` 168, `RayControlBinding` ~90, `CloudControls` ~100, `PlayControls` ~75,
  `FoliagePaintControls` ~130, `PlacementMenu` ~15 lines (none over 250). Constructor: 11 parameters (`lightMenu`,
  `assetMenu`, `pathMenu` replace six).
- `SceneViewPanelTest`, `SceneViewPanelRayTest`, `SceneInteractionTest`, `RayControlTest`, `SceneFoliageControlsTest`,
  `SceneRaySwitchTest` pass; only the three tests that built a panel with the old callback pairs changed (to
  `PlacementMenu`). `SceneToolbarStateTest` gained the no-menu case. The first full `check` after the constructor change
  failed with `NoSuchMethodError` on stale incrementally compiled test classes; `cleanCompileTestKotlin` fixed it, so a
  clean test compile is advisable after pulling this change.
- 8.5 (runIde) not run here. Steps for you, on a copy of Untitled: orbit, click-select `Model 0`, drag the Move gizmo and
  Undo; switch to the camera of entity `4`; Add Light and Add Asset menus open at the orbit target; with
  `:plugin-abyssus:runIde`, Play/Pause/Step/Stop and key forwarding work; paint foliage on a terrain with foliage, then
  press Play (painting stops); change the Clouds choice; resize the view to zero height and back without a crash (macOS).
- The GL-render tests (`SceneRenderGlTest`) fail 6 of 29 on the untouched baseline too, so they could not confirm
  rendering; the changes do not touch rendering code.

## Finish (2026-10-12)

`./gradlew check :plugin-abyssus:verifyPlugin` after a clean test compile: BUILD SUCCESSFUL, 0 failures.
(A full recompile of every module at once needs a bigger Kotlin daemon heap here, `-Pkotlin.daemon.jvmargs=-Xmx4g`;
the default ran out of memory in `compileTestKotlin`. That is the build environment, not this change.)

| Module | Tests | Skipped | Failed | Baseline tests |
|---|---|---|---|---|
| lib-core | 458 | 65 | 0 | 454 |
| lib-core-editor | 643 | 4 | 0 | 640 |
| lib-gdx | 68 | 17 | 0 | 68 |
| lib-physics | 65 | 0 | 0 | 65 |
| lib-raytracing | 260 | 124 | 0 | 260 |
| plugin-abyssus | 903 | 77 | 0 | 891 |
| app-game-control-line | 279 | 9 | 0 | 278 |

Tests added: +4 `lib-core` (`StandardAssetLoadersTest`), +3 `lib-core-editor` (`FormatBytesTest`, UUID, light labels), +12
`plugin-abyssus` (visibility 5, panels 6, toolbar 1), +1 game (`FieldAssetsGlTest`, skipped without `-Dabyssus.glTests=true`).
No test was deleted or weakened.

Acceptance criteria: `scripts/check-docs.sh` 250 paths OK; no `MetalSlice`; no `typealias` in `lib-core-editor`;
`MetaType.SKYBOX_HDR to` only in the table and `AssetMetaBinder`'s meta-class map; `currentProjectViewPane` only in
`selectedNode`; one `fun testProject` (in `testFixtures`); no `"LightComponent"` in the properties package;
`SceneViewPanel.kt` 503 lines (from 882), `AssetPropertiesPanel.kt` 518 (from 668), no new file over 170; fixture
documents format to identical bytes (`FormatBytesTest`); the two new spec requirements have passing scenarios.

Still open: 4.4 (optional G2), and the runIde manual checks 7.4 and 8.5 (steps above).
