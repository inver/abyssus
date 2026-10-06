# Tasks

No behavior or file format change. Existing tests pass unchanged, except for moves and renames a task names. Each
numbered phase is one PR and ends with `./gradlew check` green. Module tests: `./gradlew :<module>:test`; a single class:
`./gradlew :plugin-abyssus:test --tests '<fqcn>'` (always module-qualified). Tasks marked (runIde) need a sandbox IDE
opened on a **copy** of `projects/plugin-abyssus/src/test/testData/project/Untitled`.

## 1. Baseline and build repair (B2, B3 part, D1) — PR 1

- [ ] 1.1 Record the baseline before any change: `./gradlew check --continue` (pass/fail and test counts per module),
  `./gradlew :plugin-abyssus:verifyPlugin`, `scripts/check-docs.sh`. Reproduce each B2 item: the verifier finalizer's
  missing allowlist input, `./gradlew :verifyPlugin` failing at the root. Save the output summary in
  `verification.md`. If `check` already fails for unrelated reasons, list the failures and their causes and stop for
  the user's decision (config `apply` guidance).
- [ ] 1.2 In `gradle/plugin-verification.gradle.kts`, resolve the allowlist with `rootProject.layout.projectDirectory`.
  Verify: `./gradlew :plugin-abyssus:verifyPlugin :plugin-abyssus-physics:verifyPlugin` runs `checkPluginInternalApis`
  to success; adding a dummy line to a copy of a report makes it fail (then revert).
- [ ] 1.3 Update `.github/workflows/build.yml`: `:plugin-abyssus:verifyPlugin` (and the physics plugin), report, Kover
  and distribution paths under `projects/plugin-abyssus/build/`. Update `.run/Run Plugin Verification.run.xml` to
  `:plugin-abyssus:verifyPlugin`, and qualify the other `.run` tasks. Verify: each path in the workflow exists after
  `./gradlew check buildPlugin :plugin-abyssus:verifyPlugin` locally (`ls` each). A workflow run on the branch is green,
  or its exact steps are listed for the user if it cannot run.
- [ ] 1.4 Delete `projects/lib-raytracing/src/main/kotlin/net/nevinsky/abyssus/lib/raytracing/MetalSlice.kt` (D1).
  Verify: `rg -n 'MetalSlice' projects` prints nothing; `./gradlew :lib-raytracing:test`.
- [ ] 1.5 `git rm` the five tracked `.DS_Store` files and the empty root `src/test/testData` tree. Add `.DS_Store` to
  `.gitignore`. Verify: `git ls-files | grep -c DS_Store` prints `0`; `./gradlew check`.

## 2. Docs and process truth (B1, B3) — PR 2

- [ ] 2.1 Rewrite `AGENTS.md` for the as-built tree: command table (module-qualified tasks, `:plugin-abyssus:runIde`,
  `:plugin-abyssus-physics:runIde`, `:app-game-control-line:run`), layout section (`projects/<module>/`, real package
  roots), hard rules (paths of `src/main/gen`, `SceneDocumentWriter.kt`, bundles), and the fixture path. Verify: run
  each command in the table with `--dry-run`, and `scripts/check-docs.sh AGENTS.md` exits 0.
- [ ] 2.2 Fix every path that `scripts/check-docs.sh` reports in `docs/ai/architecture.md`, `conventions.md`,
  `file-formats.md` and `testing.md`, and the module and package READMEs they link. Verify: `scripts/check-docs.sh`
  exits 0 (85 → 0).
- [ ] 2.3 Update `openspec/config.yaml`: fixture path, `./gradlew :plugin-abyssus:test --tests` rule, and the "plain
  `test` also runs other modules" note. Amend `openspec/changes/restructure-gradle-modules/` to the as-built names (open
  question 1: default **drop** the root `testData/` and Control Line flattening, with the reason), tick what landed,
  then archive it. Verify: `openspec validate --all` if the CLI is installed, otherwise every task box in that change
  is ticked or has a written reason; `scripts/check-docs.sh`.
- [ ] 2.4 Run `./gradlew check` and `scripts/check-docs.sh`. Both pass.

## 3. Library-level de-duplication (D4, K1, D7, D8) — PR 3

- [ ] 3.1 Pin bytes first: a test in `lib-core-editor` that formats every `.scene`, `.abss` and `meta.json` under
  `projects/plugin-abyssus/src/test/testData/project/` through `SceneJson.pretty(text)` and `SceneJson.compact`, and
  writes a bound value through `JsonProcessor.pretty`. Each output must equal a stored copy produced by the current
  code. Verify:
  `./gradlew :lib-core-editor:test --tests '*FormatBytesTest'` green before 3.2.
- [ ] 3.2 Move `JsonFormat` to `lib-core` `io/`; `JsonProcessor` builds its mapper and printer from it; `SceneJson`
  imports it from there. Delete the `lib-core-editor` copy and correct its KDoc claim. Verify: 3.1 test,
  `JsonProcessorTest`, `SceneJsonTest`, `HeadlessEditingTest`, `./gradlew :lib-core:checkNoSingletons`.
- [ ] 3.3 Replace the 15 imports of `net.nevinsky.abyssus.lib.core.editor.document.{AbyssusDocumentFormat,
  DocumentKind, FormatProblem, FormatRejection, UnsupportedDocumentFormat}` with `net.nevinsky.abyssus.lib.core.format.*`
  and delete `DocumentFormat.kt` (K1). Verify: `rg -n 'typealias' projects/lib-core-editor/src/main` prints nothing;
  `./gradlew :lib-core-editor:test :plugin-abyssus:test`.
- [ ] 3.4 Use `parseUuidOrNull` in `AssetMetaEditor` (D8). Verify: `AssetMetaEditorTest` cases for a valid UUID, an
  invalid one and empty text (add the missing ones first).
- [ ] 3.5 Move `testProject()` into `lib-core`'s `testFixtures` and delete the copies in `lib-runtime`, `lib-physics`,
  `lib-core` `src/test` and `lib-core-editor` `TestScenes.kt`. Set `abyssus.testData` once in the root
  `subprojects { tasks.withType<Test> { ... } }` block and remove the five per-module lines (D7). Verify:
  `rg -n 'fun testProject' projects` shows one hit; `rg -n 'abyssus.testData' projects/*/*.gradle.kts` shows none;
  `./gradlew check`.

## 4. One asset loader table (D2; spec `asset-loading`) — PR 4

- [ ] 4.1 Pin behavior: a test in `app-game-control-line` that loads every Untitled asset kind (`model_29e9be61-...`,
  `terrain_2cf70bf7-...`, `skybox_default`, `skybox_physical`, `skybox_hdr`) through the current game wiring
  (`fieldAssets`) and records each prepared type. (GL) Its build step runs under `-Dabyssus.glTests=true`. Verify:
  `./gradlew :app-game-control-line:test --tests '*FieldAssetsTest'`.
- [ ] 4.2 Add `standardAssetLoaders(...)` in `lib-core` `assets/loading/` (design decision 1) with
  `StandardAssetLoadersTest`: every `MetaType` that has a loader today is present, and each entry is the expected loader
  type. Verify: `./gradlew :lib-core:test --tests '*StandardAssetLoadersTest'`, `:lib-core:checkNoSingletons`.
- [ ] 4.3 Switch `AssetLoading.ProjectAssets` and `FieldRenderer.fieldAssets` to it, and point `PhysicsAssets`' KDoc at
  it. Verify: 4.1 test unchanged; `AssetLoadingTest`, `AssetStorageTest`, `./gradlew :lib-physics:test`; the spec
  scenarios "Same sky…" and "Same terrain…" as cases in `AssetLoadingTest` (editor side) and `FieldAssetsTest` (game
  side); `rg -c 'MetaType.SKYBOX_HDR to' projects/*/src/main` totals 1.
- [ ] 4.4 (optional, open question 3) Move `SkyRaySnapshotLoader`'s `when` to a `MetaType` map next to the table (G2).
  Verify: `RaySkySnapshotTest`, `SceneViewPanelRayTest`.

## 5. One sky resampler (D3) — PR 5

- [ ] 5.1 Pin pixels: capture `SkyboxRaySnapshotLoader` output for Untitled `skybox_default`, and `RaySkyBaker`'s
  equirect step for a synthetic six-color face set, as stored checksums plus 16 sampled texels. Verify:
  `./gradlew :lib-core:test --tests '*SkyResampleTest'`; (GL) `RaySkyBakerGlTest`.
- [ ] 5.2 Extract the shared grid walk and face choice into `lib-core` `assets/sky/` (design decision 3); both callers
  pass their sampler. Stop and report if 5.1 cannot stay byte-identical. Verify: 5.1 tests unchanged,
  `RaySkySnapshotTest`; (GL) `RaySkyBakerGlTest` with `-Dabyssus.glTests=true`.

## 6. Tree actions on the template (D5, G1) — PR 6

- [ ] 6.1 Pin behavior: `RenameSceneActionTest` and `ImportFlightGearActionTest` (new). For each of a scene row, the
  Assets row and an unrelated row, assert visibility and enablement, overriding `selected` as `AbyssusTreeAction`
  tests do. Verify: `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.*ActionTest'`.
- [ ] 6.2 Make `RenameSceneAction`, `NewTerrainAction` and `ImportFlightGearAction` extend `AbyssusTreeAction` (design
  decision 2). Verify: 6.1 tests, `NewTerrainActionTest`, `ComponentActionsTest`, `AbyssusViewTest`;
  `rg -n 'currentProjectViewPane' projects/plugin-abyssus/src/main` shows only `selectedNode` and the pane itself.

## 7. Properties field rows (D6, S2, S3; spec `object-properties-panel`) — PR 7

- [ ] 7.1 Move light field labels and the range tooltip into the field descriptions in `lib-core-editor`
  `components/`, read through `EditorMessages` with the same English text (S3). Verify: a `lib-core-editor` test that
  `Spot Light 8`'s range, cone angle and edge softness fields carry the expected labels and tooltip;
  `rg -n '"LightComponent"' projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/properties` prints
  nothing.
- [ ] 7.2 Add `plugin/ui/FieldRow.kt` (design decision 4) and use it in `AssetPropertiesPanel` and
  `EntityDetailsView`. Move previews and `Thumbnail` to `properties/AssetPreviews.kt`. Component names stay the same.
  Verify: `AssetPropertiesPanelTest`, `EntityPropertiesPanelTest` unchanged; `wc -l` of `AssetPropertiesPanel.kt`
  under 450.
- [ ] 7.3 Add spec scenarios as tests: confirm by focus loss, refused value (both panels), equal value writes nothing,
  using Untitled's terrain `uv` and `Spot Light 8` `intensity`. Verify: the new cases in `AssetPropertiesPanelTest` and
  `EntityPropertiesPanelTest`.
- [ ] 7.4 (runIde) Manual check 1, on a copy of Untitled: (a) select the terrain, set `uv` to `30`, press Tab: the file
  shows `30`, Undo restores `60.0`; (b) enter `abc`: the old value returns with a red reason; (c) select `Spot Light 8`:
  the range label and tooltip read as before; (d) set `intensity` to `2`: the scene view brightens. If not run, list
  these steps for the user.

## 8. Split `SceneViewPanel` (S1) — PR 8

- [ ] 8.1 Add a Swing-free `SceneToolbarState` (button enablement, camera choices from `SceneRenderParams`, play state
  and ray mode) with `SceneToolbarStateTest` covering: no lights or assets menu, play running and paused, a camera
  entity present (Untitled entity `4`). Verify: `./gradlew :plugin-abyssus:test --tests '*SceneToolbarStateTest'`.
- [ ] 8.2 Extract `SceneToolbar`, `SceneInputForwarder` and `RayControlBinding` (design decision 5). Each collaborator
  is passed by constructor, with no shared mutable fields. Verify: `SceneViewPanelTest`, `SceneViewPanelRayTest`,
  `SceneInteractionTest` unchanged; (GL) `SceneRenderGlTest`.
- [ ] 8.3 Replace the `lightActions`/`canAddLight` and `assetActions`/`canAddAsset` pairs with one `PlacementMenu` each,
  and update `SceneFileEditor` and the tests that build the panel. Verify: as 8.2, plus `AddLightActionTest`.
- [ ] 8.4 Check sizes: `SceneViewPanel.kt` under 300 lines, no new file over 250. Verify: `wc -l`.
- [ ] 8.5 (runIde) Manual check 2, on a copy of Untitled: orbit, click-select `Model 0`, drag the Move gizmo and Undo;
  switch to the camera of entity `4`; Add Light and Add Asset menus open at the orbit target; with
  `:plugin-abyssus-physics:runIde`, Play/Pause/Step/Stop and key forwarding work; resize the view to zero height and
  back without a crash (macOS). If not run, list these steps for the user.

## 9. Follow-ups in other changes and docs — PR 9 (docs only)

- [ ] 9.1 Amend the task lists of `add-realistic-water`, `add-sky-clouds`, `add-model-import` and
  `add-remote-asset-library` where they add a loader or touch the sky loaders, so that they name
  `standardAssetLoaders` (one entry) instead of the three hosts. Verify: `rg -n 'FieldRenderer|fieldAssets|CompositeAssetLoader\('
  openspec/changes/*/tasks.md` shows only intended mentions; `scripts/check-docs.sh`.
- [ ] 9.2 Add a task to `add-scene-raytracing`, after 1.8: remove `abyssus.raytracing.experiment`,
  `RayFeasibilityPreview` and `RayFeasibilityLoop` once the feasibility check is recorded (K3). Verify: the task
  exists; no code change here.
- [ ] 9.3 Record decisions on open questions 2 (K2) and 4 in this design. If the user approves K2, propose it as its
  own change; do not do it here.
- [ ] 9.4 Update `docs/ai/architecture.md` (loader table, field rows, toolbar split), `docs/ai/conventions.md`
  (a new asset kind is one table entry; tree actions extend `AbyssusTreeAction`), and the `sceneview/` and
  `projectView/` READMEs. Verify: `scripts/check-docs.sh`.

## 10. Finish

- [ ] 10.1 Run `./gradlew check` and `scripts/check-docs.sh`. Check every acceptance criterion in `design.md`, and record
  the result in `verification.md`. Report anything failing for reasons outside this change, with its cause.
- [ ] 10.2 Archive the change, which syncs the `asset-loading` and `object-properties-panel` deltas into
  `openspec/specs/`.
