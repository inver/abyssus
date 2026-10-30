# Tasks

No behavior or file format change. Re-checked against the sources on 2026-10-12: phases 2 and 5 are dropped and keep their
numbers so that references hold. Existing tests pass unchanged, except for moves and renames a task names. Each
numbered phase is one PR and ends with `./gradlew check` green. Module tests: `./gradlew :<module>:test`; a single class:
`./gradlew :plugin-abyssus:test --tests '<fqcn>'` (always module-qualified). Tasks marked (runIde) need a sandbox IDE
opened on a **copy** of `projects/plugin-abyssus/src/test/testData/project/Untitled`.

## 1. Baseline and build repair (B2, B3, D1) — PR 1

- [x] 1.1 Record the baseline before any change: `./gradlew check --continue` (pass/fail and test counts per module),
  `./gradlew :plugin-abyssus:verifyPlugin`, `scripts/check-docs.sh` (passes today). Reproduce each B2 item: the verifier finalizer's
  missing allowlist input, `./gradlew :verifyPlugin` failing at the root. Save the output summary in
  `verification.md`. If `check` already fails for unrelated reasons, list the failures and their causes and stop for
  the user's decision (config `apply` guidance).
- [x] 1.2 In `gradle/plugin-verification.gradle.kts`, resolve the allowlist with `rootProject.layout.projectDirectory`.
  Verify: `./gradlew :plugin-abyssus:verifyPlugin` runs `checkPluginInternalApis`
  to success; adding a dummy line to a copy of a report makes it fail (then revert).
- [x] 1.3 Update `.github/workflows/build.yml`: `:plugin-abyssus:verifyPlugin`, report, Kover
  and distribution paths under `projects/plugin-abyssus/build/`, and the verifier cache key's `hashFiles` (it names a
  root `app-game-control-line.gradle.kts` that does not exist; use `gradle/libs.versions.toml`). Update `.run/Run Plugin Verification.run.xml` to
  `:plugin-abyssus:verifyPlugin`, and qualify the other `.run` tasks. Verify: each path in the workflow exists after
  `./gradlew check buildPlugin :plugin-abyssus:verifyPlugin` locally (`ls` each). A workflow run on the branch is green,
  or its exact steps are listed for the user if it cannot run.
- [x] 1.4 Delete `projects/lib-raytracing/src/main/kotlin/net/nevinsky/abyssus/lib/raytracing/MetalSlice.kt` (D1).
  Verify: `rg -n 'MetalSlice' projects` prints nothing; `./gradlew :lib-raytracing:test`.
- [x] 1.5 `git rm` the five tracked `.DS_Store` files and the empty root `src/test/testData` tree. Add `.DS_Store` to
  `.gitignore` if it is not there. Verify: `git ls-files | grep -c DS_Store` prints `0`; `./gradlew check`;
  `scripts/check-docs.sh`.

## 2. (Dropped) Docs and process truth

Dropped on 2026-10-12: `scripts/check-docs.sh` passes (248 paths), `AGENTS.md` and `docs/ai/*.md` describe the as-built
tree, and `restructure-gradle-modules` was archived on 2026-10-08. The `.DS_Store` cleanup is task 1.5. Docs that this
change makes wrong are updated in task 9.4.

## 3. Library-level de-duplication (D4, K1, D7, D8) — PR 3

- [x] 3.1 Pin bytes first: a test in `lib-core-editor` that formats every `.scene`, `.abss` and `meta.json` under
  `projects/plugin-abyssus/src/test/testData/project/` through `SceneJson.pretty(text)` and `SceneJson.compact`, and
  writes a bound value through `JsonProcessor.pretty`. Each output must equal a stored copy produced by the current
  code. Verify:
  `./gradlew :lib-core-editor:test --tests '*FormatBytesTest'` green before 3.2.
- [x] 3.2 Move `JsonFormat` to `lib-core` `io/`; `JsonProcessor` builds its mapper and printer from it; `SceneJson`
  imports it from there. Delete the `lib-core-editor` copy and correct its KDoc claim. Verify: 3.1 test,
  `JsonProcessorTest`, `SceneJsonTest`, `HeadlessEditingTest`, `./gradlew :lib-core:checkNoSingletons`.
- [x] 3.3 Replace the 23 imports of `net.nevinsky.abyssus.lib.core.editor.document.{AbyssusDocumentFormat,
  DocumentKind, FormatProblem, FormatRejection, UnsupportedDocumentFormat}` with `net.nevinsky.abyssus.lib.core.format.*`
  and delete `DocumentFormat.kt` (K1). Verify: `rg -n 'typealias' projects/lib-core-editor/src/main` prints nothing;
  `./gradlew :lib-core-editor:test :plugin-abyssus:test`.
- [x] 3.4 Use `parseUuidOrNull` in `AssetMetaEditor` (D8). Verify: `AssetMetaEditorTest` cases for a valid UUID, an
  invalid one and empty text (add the missing ones first).
- [x] 3.5 Move `testProject()` into `lib-core`'s `testFixtures` and delete the copies in `lib-physics`,
  `lib-core` `src/test` and `lib-core-editor` `TestScenes.kt`. Set `abyssus.testData` once in the root
  `subprojects { tasks.withType<Test> { ... } }` block and remove the four per-module lines (`lib-core`,
  `lib-core-editor`, `lib-physics`, `plugin-abyssus`) (D7). Verify:
  `rg -n 'fun testProject' projects` shows one hit; `rg -n 'abyssus.testData' projects/*/*.gradle.kts` shows none;
  `./gradlew check`.

## 4. One asset loader table (D2; spec `asset-loading`) — PR 4

- [x] 4.1 Pin behavior: a test in `app-game-control-line` that loads every Untitled asset kind (`model_29e9be61-...`,
  `terrain_2cf70bf7-...`, `skybox_default`, `skybox_physical`, `skybox_hdr`) through the current game wiring
  (`fieldAssets`) and records each prepared type. (GL) Its build step runs under `-Dabyssus.glTests=true`. Verify:
  `./gradlew :app-game-control-line:test --tests '*FieldAssetsTest'`.
- [x] 4.2 Add `standardAssetLoaders(...)` in `lib-core` `assets/loading/` (design decision 1) with
  `StandardAssetLoadersTest`: every `MetaType` that has a loader today is present, and each entry is the expected loader
  type. Verify: `./gradlew :lib-core:test --tests '*StandardAssetLoadersTest'`, `:lib-core:checkNoSingletons`.
- [x] 4.3 Switch `AssetLoading.ProjectAssets`, `FieldRenderer.fieldAssets` and `BaseCtx` to it (`PhysicsAssets` needs no
  change). Verify: 4.1 test unchanged; `AssetLoadingTest`, `AssetStorageTest`, `./gradlew :lib-core:test`; the spec
  scenarios "Same sky…" and "Same terrain…" as cases in `AssetLoadingTest` (editor side) and `FieldAssetsTest` (game
  side); `rg -c 'MetaType.SKYBOX_HDR to' projects/*/src/main` shows only the table and `AssetMetaBinder`'s meta-class map.
- [ ] 4.4 (optional, open question 1) Move `SkyRaySnapshotLoader`'s `when` to a `MetaType` map next to the table (G2).
  Verify: `RaySkySnapshotTest`, `SceneViewPanelRayTest`.

## 5. (Dropped) One sky resampler

Dropped on 2026-10-12 (D3 in `design.md`): `SkyboxRaySnapshotLoader` and `RaySkyBaker` differ in width, sampling and
output type, so sharing code would change pixels.

## 6. Tree actions on the template (D5, G1) — PR 6

- [x] 6.1 Pin behavior: `RenameSceneActionTest`, `ImportFlightGearActionTest`, `ImportModelActionTest` and
  `UiAssetActionsTest` (new where missing; check `NewTerrainActionTest` and `NewFoliageActionTest` for the same cases). For
  each of a scene row, the Assets row and an unrelated row, assert visibility and enablement, overriding `selected` as
  `AbyssusTreeAction` tests do. Verify:
  `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.*ActionTest'`.
- [x] 6.2 Make `RenameSceneAction`, `NewTerrainAction`, `NewFoliageAction`, `ImportModelAction`, `ImportFlightGearAction`
  and `gameui/UiAssetActions` extend `AbyssusTreeAction` (design decision 2); an action with extra enablement rules
  overrides `isEnabled`. Verify: 6.1 tests, `NewTerrainActionTest`, `NewFoliageActionTest`, `ComponentActionsTest`,
  `AbyssusViewTest`; `rg -n 'currentProjectViewPane' projects/plugin-abyssus/src/main` shows only `selectedNode` and the
  pane itself.

## 7. Properties field rows (D6, S2, S3; spec `object-properties-panel`) — PR 7

- [x] 7.1 Move light field labels and the range tooltip into the field descriptions in `lib-core-editor`
  `components/`, read through `EditorMessages` with the same English text (S3). Verify: a `lib-core-editor` test that
  `Spot Light 8`'s range, cone angle and edge softness fields carry the expected labels and tooltip;
  `rg -n '"LightComponent"' projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/properties` prints
  nothing.
- [x] 7.2 Add `plugin/ui/FieldRow.kt` (design decision 4) and use it in `AssetPropertiesPanel` and
  `EntityDetailsView`. Move previews and `Thumbnail` to `properties/AssetPreviews.kt`. Component names stay the same.
  Verify: `AssetPropertiesPanelTest`, `EntityPropertiesPanelTest` unchanged; `wc -l` of `AssetPropertiesPanel.kt`
  under 520.
- [x] 7.3 Add spec scenarios as tests: confirm by focus loss, refused value (both panels), equal value writes nothing,
  using Untitled's terrain `uv` and `Spot Light 8` `intensity`. Verify: the new cases in `AssetPropertiesPanelTest` and
  `EntityPropertiesPanelTest`.
- [ ] 7.4 (runIde) Manual check 1, on a copy of Untitled: (a) select the terrain, set `uv` to `30`, press Tab: the file
  shows `30`, Undo restores `60.0`; (b) enter `abc`: the old value returns with a red reason; (c) select `Spot Light 8`:
  the range label and tooltip read as before; (d) set `intensity` to `2`: the scene view brightens. If not run, list
  these steps for the user.

## 8. Split `SceneViewPanel` (S1) — PR 8

- [x] 8.1 Record the size of `SceneViewPanel` before the split in `verification.md`. The Swing-free toolbar state
  already exists (`toolbarState`, `playControls`, `cameraChoices` in `SceneViewControls.kt`); extend
  `SceneToolbarStateTest` to cover: no lights or assets menu, play running and paused, a camera
  entity present (Untitled entity `4`). Verify: `./gradlew :plugin-abyssus:test --tests '*SceneToolbarStateTest'`.
- [x] 8.2 Extract `SceneInputForwarder`, `RayControlBinding`, `CloudControls`, `PlayControlsStrip` and
  `FoliagePaintControls` (design decision 5; these replace the one `SceneToolbar` of the first plan). Each collaborator
  is passed by constructor, with no shared mutable fields. Verify: `SceneViewPanelTest`, `SceneViewPanelRayTest`,
  `SceneInteractionTest` unchanged; (GL) `SceneRenderGlTest`.
- [x] 8.3 Replace the `lightActions`/`canAddLight` and `assetActions`/`canAddAsset` pairs with one `PlacementMenu` each,
  and update `SceneFileEditor` and the tests that build the panel (13 constructor parameters today). Verify: as 8.2, plus
  `AddLightActionTest`, `AddAssetActionTest`.
- [x] 8.4 Check sizes: `SceneViewPanel.kt` under 550 lines, no new file over 250. Verify: `wc -l`.
- [ ] 8.5 (runIde) Manual check 2, on a copy of Untitled: orbit, click-select `Model 0`, drag the Move gizmo and Undo;
  switch to the camera of entity `4`; Add Light and Add Asset menus open at the orbit target; with
  `:plugin-abyssus:runIde`, Play/Pause/Step/Stop and key forwarding work; resize the view to zero height and
  back without a crash (macOS). If not run, list these steps for the user.

## 9. Follow-ups in other changes and docs — PR 9 (docs only)

- [x] 9.1 Amend the task lists of the open changes that add a loader or touch the sky loaders (today `add-realistic-water`
  and `add-remote-asset-library`; check them first) so that they name `standardAssetLoaders` (one entry) instead of the
  four hosts. Verify: `rg -n 'FieldRenderer|fieldAssets|CompositeAssetLoader\(' openspec/changes/*/tasks.md` shows only
  intended mentions (`FieldRenderer` in the two `add-f2b-*` changes is drawing code, not loading); `scripts/check-docs.sh`.
- [x] 9.2 Add a task to `add-scene-raytracing`, after 1.8: remove `abyssus.raytracing.experiment`,
  `RayFeasibilityPreview` and `RayFeasibilityLoop` once the feasibility check is recorded (K3). Verify: the task
  exists; no code change here.
- [x] 9.3 Record the decision on open question 2 (K3) in this design.
- [x] 9.4 Update `docs/ai/architecture.md` (loader table, field rows, toolbar split, tree-action template), `docs/ai/conventions.md`
  (a new asset kind is one table entry; tree actions extend `AbyssusTreeAction`), and the `sceneview/` and
  `projectView/` READMEs. Verify: `scripts/check-docs.sh`.

## 10. Finish

- [x] 10.1 Run `./gradlew check` and `scripts/check-docs.sh`. Check every acceptance criterion in `design.md`, and record
  the result in `verification.md`. Report anything failing for reasons outside this change, with its cause.
- [ ] 10.2 Archive the change, which syncs the `asset-loading` and `object-properties-panel` deltas into
  `openspec/specs/`.
