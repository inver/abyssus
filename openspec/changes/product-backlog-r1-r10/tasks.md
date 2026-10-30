# Tasks

Group 1 comes first because later groups copy commands from the specs it repairs. Groups 2–4 are the new capability
(R5, R6). Groups 5–7 route work that already has its own change; each task ends when that change's named tasks are checked.

## 1. Repair specs and triage open changes (R2, R1, R10)

- [ ] 1.1 In every open change (outside `openspec/changes/archive`) replace stale commands and paths: `:core:` -> `:lib-core:`, `:raytracing:` -> `:lib-raytracing:`, plain `./gradlew :test` -> `:plugin-abyssus:test`, `:gdx-model:` -> `:lib-gdx:`, and the old `src/main/kotlin/net/nevinsky/abyssus/sceneview` README path -> `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md`. Verify with `grep -rlF` for each old string over `openspec/changes` excluding `archive` printing nothing, and `openspec validate <name> --strict` for each edited change.
- [ ] 1.2 Resolve references to archived changes (`decouple-from-mundus`, `add-scene-shadows`, `add-cloud-scene-lighting`): for each hit, state in that task that the dependency is delivered and archived, or reword it as a check against the main spec in `openspec/specs/`. Verify by reading each edited task and `openspec validate <name> --strict` for each edited change.
- [ ] 1.3 Reword the `abyssus-project-view` sentence "These three actions are the only ones that write to an asset file" by adding `specs/abyssus-project-view/spec.md` as a MODIFIED delta (copying the whole requirement) and listing `abyssus-project-view` under Modified Capabilities in proposal.md, so it matches the actions that exist (Rename Scene, Choose skybox, eye toggles, component actions and the asset-creation actions). Verify with `openspec validate product-backlog-r1-r10 --strict` and a reader check that no remaining requirement contradicts `plugin.xml`.
- [ ] 1.4 Add a note at the top of `docs/reviews/design-review-2026-10.md` saying it describes the layout before the module split and is kept as history, and make sure no AGENTS.md or docs/ai page links to it as current. Verify with `scripts/check-docs.sh`.
- [ ] 1.5 Write a triage table in `openspec/changes/product-backlog-r1-r10/triage.md`: for each of the 12 open changes give status (`finish`, `schedule`, `park`), reason and next action. Decisions to record: `add-realistic-water` and `add-remote-asset-library` are `park`; `add-project-fps-counter`, `add-scene-view-antialiasing` and `redesign-game-ui-editor-layout` are `schedule` (group 7); `add-plugin-ui-tests` is `finish` groups 1–2 (group 5); `add-scene-raytracing` follows group 6; the f2b changes and `add-game-ui-authoring` and `add-scene-raytracing-settings` are `finish` or `archive` with the remaining task named. Verify the table lists all 12 names from `openspec list --json`.
- [ ] 1.6 For each `park` change add `parked.md` with the reason and the restart condition ("after R5 and R9 are done and there is outside demand"). Verify the file exists in both folders and that `openspec list` still lists them.
- [ ] 1.7 Carry the amendment required by `add-project-fps-counter` task 2.3 for `add-realistic-water`'s panel-selection delta into the parked water change, so the two deltas do not contradict. Verify with `openspec validate add-realistic-water --strict` and `openspec validate add-project-fps-counter --strict`.
- [ ] 1.8 Finish or archive the change(s) the triage marks `finish` or `archive`, so that no more than four changes remain open when groups 2–7 are done. Verify by `openspec list --json` showing at most four changes with open tasks at the end of group 7 (record the count in triage.md).

## 2. Starter documents and name rules (R5, headless)

- [ ] 2.1 Add `ProjectTemplate` to `lib-core-editor` that builds the `.abss`, the starter `Main Scene` `.scene` (default ambient light, fog, a camera entity, a `Sun` light entity, `skyboxName` of the starter sky) and the procedural sky `meta.json`, all with `format: "abyssus"` and `formatVersion: 1`, short component names and `kind: "asset"` renderables. Verify with `ProjectTemplateTest` (run `./gradlew :lib-core-editor:test --tests '*ProjectTemplateTest'`): output parses with `SceneJson`, passes `AbyssusDocumentFormat`, and the scene's `skyboxName` equals the sky folder name.
- [ ] 2.2 Add a test that the generated sky `meta.json` and scene load through the same validator and sky-loader entry point the editor uses for `Untitled`'s `skybox_physical`. Verify with `StarterProjectLoadsTest` in `lib-core` or `lib-core-editor` (whichever owns the loader), with `./gradlew :lib-core:test --tests '*StarterProjectLoadsTest'` or the matching `:lib-core-editor:test` command.
- [ ] 2.3 Add `NewNameValidator` (empty, `/` and `\`, reserved characters, only dots or spaces, case-insensitive duplicates) returning a message key. Verify with `NewNameValidatorTest` cases: `a/b`, ` `, `..`, `main scene` against `Main Scene`, and a valid name with spaces and non-ASCII letters.
- [ ] 2.4 Add `SceneIdAllocator` returning an id unused by every scene of the project. Verify with `SceneIdAllocatorTest`: no scenes, scenes with gaps, an id near `Int.MAX_VALUE`, and an unreadable scene being ignored with the others still counted.
- [ ] 2.5 Add the validation message keys to `AbyssusEditorBundle.properties` and read them through `EditorMessages`. Verify with the existing bundle-usage test in `./gradlew :lib-core-editor:test` and `./gradlew :lib-core-editor:checkNoSingletons`.
- [ ] 2.6 Document the starter documents in `docs/ai/file-formats.md` and add the new classes to `projects/lib-core-editor/README.md`. Verify `scripts/check-docs.sh` passes and every documented field name appears in `ProjectTemplateTest`.

## 3. Actions and dialogs (R5, plugin)

- [ ] 3.1 Add `NewProjectAction` and a small dialog (name field, target folder shown, inline error from `NewNameValidator`, Create disabled while invalid). Register it in `plugin.xml` in the Project view's New group. Verify with a headless dialog-model test `NewProjectDialogModelTest` run as `./gradlew :plugin-abyssus:test --tests '*NewProjectDialogModelTest'` covering the three name scenarios in the spec.
- [ ] 3.2 Write the project's files as one `AssetTransaction` through the existing file store, then refresh the VFS and reveal the new project in the Abyssus view. Verify with `NewProjectCommandTest` using the fake file store: success writes `.abss`, sky `meta.json` and scene; a failure on the third write leaves no new folder and a message naming the failure.
- [ ] 3.3 Add `NewSceneAction` on a project row: read the project document, refuse an unsupported one with the format message, build the scene with `SceneIdAllocator`, write `scenes/<name>.scene` in one transaction, leave `.abss` and other scenes byte-identical. Verify with `NewSceneCommandTest` comparing bytes of `Untitled.abss` and `Main Scene.scene` before and after on a temporary copy, and a case for an unsupported `.abss`.
- [ ] 3.4 Add all user-facing strings to `AbyssusBundle.properties` and remove any unused ones. Verify with the bundle-usage check in `./gradlew :plugin-abyssus:test`.
- [ ] 3.5 Update `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/projectView/README.md`, the user section of `README.md` (keeping the `<!-- Plugin description -->` markers) and the `[Unreleased]` section of `CHANGELOG.md`. Verify `scripts/check-docs.sh` passes.
- [ ] 3.6 Perform runIde check 1 on a temporary folder (never `Untitled`): create a project, open `Main Scene`, see the sky and a lit empty scene, add a model with **Import Model...**, add a scene with **New Scene...**, try each refused name, and cancel the dialog. Verify and record the results in `verification.md`; leave this task unchecked if a step could not be run.

## 4. Empty states and first-run observation (R6)

- [ ] 4.1 Show **New Abyssus Project...** in the Abyssus view when no `.abss` is found, and **New Scene...** on a `Scenes` row with no scene. Verify with `AbyssusEmptyStateTest` (tree model contains the entry, activating it calls the same action) run as `./gradlew :plugin-abyssus:test --tests '*AbyssusEmptyStateTest'`, and update `abyssus-project-view`'s package README.
- [ ] 4.2 Run three observed first-run sessions (the author on a clean IDE profile counts as the first; ask two other people if available) from install to a placed asset. Record time taken and every point where the observer had to help, in `verification.md`. Verify that the notes list at least the time to first scene and the time to first placed asset per session; fewer than three sessions is recorded as such.
- [ ] 4.3 Choose at most three discoverability fixes from 4.2 and add them as tasks or as a follow-up change. Verify that each cites the session note that motivated it; do not add hints with no observation behind them.

## 5. Finish the UI test harness (R3)

- [ ] 5.1 Complete tasks 1.3–1.5 and 2.1–2.5 of `add-plugin-ui-tests` and check them off in that change. Verify with the documented runner command from its task 1.5 succeeding, the fixture hashes unchanged after the run, and `scripts/check-docs.sh`.
- [ ] 5.2 Run the full UI suite ten times in a row and record the pass count in `verification.md`. Verify the count; any failure is investigated before group 3 of that change is started, which stays deferred.

## 6. Ray tracing gate (R4)

- [ ] 6.1 On every available OS and GPU, run manual checks 1–4 (tasks 1.8, 5.1–5.4) of `add-scene-raytracing` and record outcomes per OS in its `verification.md`. Verify each OS row is pass, fail or "not verified", with the device and driver.
- [ ] 6.2 Record the decision (keep, narrow to the verified platforms, or park) in `add-scene-raytracing`'s design or `verification.md` and update the user-facing wording in `README.md` and `CHANGELOG.md` to name only verified platforms. Verify `scripts/check-docs.sh` and a reader check that no text claims an unverified platform works.
- [ ] 6.3 After the decision, complete task 1.10 of `add-scene-raytracing`: remove the `abyssus.raytracing.experiment` property, `RayFeasibilityPreview` and `RayFeasibilityLoop` with their toggle text and tests. Verify `rg -n 'raytracing.experiment|RayFeasibility' projects` prints nothing and `./gradlew :plugin-abyssus:test :lib-core-editor:test` passes.

## 7. Scheduled changes and the export decision (R7, R8, R9)

- [ ] 7.1 After group 1, apply `add-project-fps-counter` and `add-scene-view-antialiasing` through their own changes, one at a time. Verify each by its own final task (`./gradlew check`, `scripts/check-docs.sh`) and archive it; record both in `triage.md`.
- [ ] 7.2 Apply `redesign-game-ui-editor-layout` through its own change. Verify by its task 4.2 (tests and the runIde check on a 14" display) and archive it.
- [ ] 7.3 Write `docs/ai/export-decision.md` stating whether the editor builds and packages games or the game's own Gradle project does, using Control Line (`projects/app-game-control-line`) as the example, with the reason and what would reverse the decision. Link it from `docs/README.md`. Verify `scripts/check-docs.sh` passes and that the note names the command a creator runs to get from a scene to a running game.

## 8. Final checks

- [ ] 8.1 Run `./gradlew check`, `scripts/check-docs.sh` and `openspec validate product-backlog-r1-r10 --strict`; record any failure unrelated to this change without widening its scope. Verify all three commands are green or the failures are listed in `verification.md`.
- [ ] 8.2 Confirm the open-change count recorded by task 1.8 and update `triage.md` with the final state. Verify `openspec list --json` agrees with the table.
