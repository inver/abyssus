## 1. Preconditions

- [x] 1.1 Confirm the base is quiet: `add-f2b-pattern-mode`, `add-f2b-pro-debrief` and `add-f2b-noob-guide` have no unfinished tasks touching `GameUi` / `GameFlow` screens (finish, archive or rebase them), and `add-game-ui-authoring` is archived or its four manual `runIde` tasks are decided. Record the result; stop if screens are still changing.

## 2. List model and validation (lib-core)

- [x] 2.1 Add the `list` binding type (record `fields`, `sample`), the `list` widget (`template`, `empty`, `maxRows`, `items` binding), the bindable widget `style`, the screen `navigation` setting and `UiValue.Rows`; extend admission and reference warnings (undeclared item field, template ids unique, `item.` binding outside a list). Verify `UiListAdmissionTest` (valid, unknown members kept, bad record, duplicate template ids, binding to an undeclared field, template depth) with `./gradlew :lib-core:test --tests '*UiListAdmissionTest'`.
- [x] 2.2 Implement list value handling and diagnostics in `ui.state`: typed rows, missing / mistyped list and item fields, bound style resolution, row-count and widget limits. Verify `UiListBindingTest` with `./gradlew :lib-core:test --tests '*UiListBindingTest'`.
- [x] 2.3 Expand lists in the presenter before layout (runtime ids `<id>#<row>`, empty state, `maxRows`), make `publish` invalidate only on a changed snapshot, and add arrow-key navigation, first-focus on open and the host `display` diagnostics. Verify `UiListRendererTest` (GL pixels for rows, empty, bound style, row limit, arrow focus, Enter once, unknown screen) with `./gradlew :lib-core:test --tests '*UiListRendererTest' -Dabyssus.glTests=true`.

## 3. Editing and preview (lib-core-editor, plugin-abyssus)

- [x] 3.1 Add pure edits for list widgets (add list, edit template / empty, `maxRows`, declare and change record fields, bind `item.` fields and `style`) and list samples in `UiPreviewState` (add / remove / change rows with the record's types, reset). Verify `UiListEditsTest` and `UiPreviewListTest` with `./gradlew :lib-core-editor:test --tests '*UiListEditsTest' --tests '*UiPreviewListTest'`.
- [x] 3.2 Add the list widget to the palette, tree (template subtree), properties, declarations (list bindings with fields) and the preview sample editor. Verify `UiListEditorTest` with `./gradlew :plugin-abyssus:test --tests '*UiListEditorTest'` and the canvas rows in `UiCanvasViewGlTest` with `-Dabyssus.glTests=true`.
- [x] 3.3 Document lists, bound styles, key navigation and host display in `docs/ai/file-formats.md`, `projects/lib-core/README.md` (host contract example stays compiled by `UiHostContractTest`) and the README Game UI section; verify `scripts/check-docs.sh`.

## 4. Control Line screens as authored UI (app-game-control-line)

- [x] 4.0 (added during 4.1) Add clickable containers with row-indexed action names and `selectOnFocus` text fields to `lib-core`'s presenter and expansion, with `UiRowActionTest` (GL: click on a row emits `name#row` once, containers are not in the Tab order, hover / pressed styles, select-on-focus replaces text) and a line in `docs/ai/file-formats.md`. Verify `./gradlew :lib-core:test --tests '*UiRowActionTest' -Dabyssus.glTests=true`.

- [x] 4.1 Before changing anything, write `GameScreensCharacterizationTest`: for each `Screen` and each HUD mode, record the strings and ordered rows the current `GameUi` builds (labels, buttons, enabled state, table cells, colours by role, deduction lines). Verify it passes against the current code with `./gradlew :app-game-control-line:test --tests '*GameScreensCharacterizationTest'`.
- [x] 4.2 Author the `ControlLine` UI asset (all screens, HUD, pause overlay, styles for highlight / failure / selection / grey, list templates for the score table, boards, sheet and debrief rows) in the bundled project and register it in `ControlLine.abss`. Verify `ControlLineUiAssetTest`: it admits with no warnings, every published binding is declared, every declared binding is published and every action name has a handler (`./gradlew :app-game-control-line:test --tests '*ControlLineUiAssetTest'`).
- [x] 4.3 Implement `GameUiHost` (snapshot from `GameFlow`, `FlightSession` and the judge; `display` on screen change; HUD plus pause overlay; action handlers; the keys the host keeps; name entry text and its limit; click-to-open board rows). Port the characterization test to the host's published data and rendered strings. Verify `./gradlew :app-game-control-line:test --tests '*GameUiHostTest' --tests '*GameScreensCharacterizationTest'`.
- [x] 4.4 Switch `ControlLineGame` to `GameUiHost`, delete `screens/GameUi.kt`, the uiskin dependency it alone used and `GameUiGlTest`'s old expectations, and add per-screen GL tests of the real asset (main menu, plane select, free and pattern HUD, pause over HUD, game over with sheet, scores with highlight, debrief rows). Verify `./gradlew :app-game-control-line:test` and the GL tests with `-Dabyssus.glTests=true`.
- [x] 4.5 Update the Control Line README, `control-line-game-flow` documentation and `docs/ai/architecture.md` for authored screens and how to edit them (open a copy of the project). Verify `scripts/check-docs.sh`.

## 5. Remove the demo

- [x] 5.1 Delete the `runUiDemo` task and `uiDemo.project` property from `app-game-control-line.gradle.kts`, the `uidemo` main and test packages, `uiDemo/UiDemo`, the README UI-demo section, the AGENTS / docs / CHANGELOG / `lib-core` README mentions, and add a note to `add-game-ui-authoring` (tasks 3.3 and its verification record, design) saying the demo was replaced by Control Line. Verify `rg -i "runUiDemo|uidemo|uiDemo"` finds nothing outside `openspec/changes/archive` and this change, and `scripts/check-docs.sh` passes.

## 6. Verification

- [x] 6.1 Run `./gradlew check`, the GL tests of `lib-core`, `plugin-abyssus` and `app-game-control-line` with `-Dabyssus.glTests=true`, `./gradlew :plugin-abyssus:buildPlugin :plugin-abyssus:checkNoJoltInZip` and `scripts/check-docs.sh`; record results and any check that could not run.
- [ ] 6.2 Perform runIde check on a copy of `project/ControlLine`: open the UI asset, edit the main menu title and a list template, preview the scores screen with sample rows, then run the game on the copy and confirm the change shows, plus a full flight from menu to scores and debrief by hand. Record results separately from headless tests.

## Notes

- 1.1 (2026-10-11): the screens of `add-f2b-pattern-mode`, `add-f2b-pro-debrief` and `add-f2b-noob-guide` are implemented and committed (`GameUi` has the Noob and debrief layouts; no uncommitted source changes); their open boxes are manual checks, rendering tasks and verification. `add-game-ui-authoring` still has its four manual `runIde` boxes open; this change proceeds without waiting for them, and its own `runIde` check (6.2) covers the same editor.

- 6.1 (2026-10-11): `./gradlew check` passes. With `-Dabyssus.glTests=true`: `lib-core` and `app-game-control-line` GL tests pass (incl. `GameUiGlTest`, `GameScreensCharacterizationTest`). `plugin-abyssus` GL run: 7 failures, all outside the Game UI code: `SceneRenderGlTest` (6: HDR pixel values, and an `ObjectNode` null cast in scene fixtures) and `RaySkyBakerGlTest` (1: 2048 vs 1024 size). These were not run on the base commit, so they are not shown to be pre-existing. `buildPlugin` and `checkNoJoltInZip` pass; `scripts/check-docs.sh` passes.
- 6.2: not performed (interactive `runIde` check); the game window was never looked at by eye.
