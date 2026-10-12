# Proposal

## Why

Control Line's menus, HUD, results and score screens are 528 lines of hand-built Scene2D in `GameUi` with libGDX's sample skin, while Abyssus now has an editor and a shared renderer for authored UI. As long as the game's own screens stay in code, they cannot be edited visually, previewed, restyled or reused, and the authored-UI feature is only proven on a throwaway demo. Three things stop a straight migration: the UI model has no repeated rows (score tables, the pattern sheet, the debrief rows), no way to colour or style a single value from game data (highlighted row, deduction in red, warnings), and menus are driven with Up/Down/Enter rather than Tab. This change closes those gaps, moves every screen and the HUD into a UI asset of the bundled Control Line project, and then removes the demo app that existed only to prove the runtime.

## What Changes

- Add a **list** widget to the UI model, editor, renderer and preview: a container that repeats one template row for every item of a new typed `list` binding. Items are records of typed fields; row widgets bind to item fields. Lists can be limited in rows and show an authored empty state; a row is marked (highlighted, selected, failed) by binding its style.
- Allow a widget's **`style` to be bound** to a string value, so game data picks a named style (highlighted, failed, selected) per row or per label, together with number precision and prefix/suffix already supported.
- Add a **key navigation** setting per screen (`tab`, the current behaviour, or `arrows`): Up / Down move focus through the screen's enabled controls, Enter presses, and Escape follows the existing back rule. Add a host-driven **`display`** contract so a game can show exactly the screen its state machine is in, and expose `maxLength` on text fields from the binding.
- Author **all Control Line screens** (main menu, assist select, plane select, flying HUD for free and pattern modes, pause, name entry, game over, scores, debrief) in a UI asset of `projects/app-game-control-line/project/ControlLine`, with a game-side binding host (`GameUiHost`) replacing `GameUi`. Flow rules, scoring, the F2B judge, replay and rendering behind the screens do not change.
- **Delete the standalone UI demo**: the `runUiDemo` task, the `uidemo` package and tests, `uiDemo/UiDemo`, and their documentation. Renderer parity is then proven by Control Line itself.
- Out of scope: new game rules or screens, TrueType fonts, animation, gamepad navigation, drag-reorder of list rows, and restyling Control Line (the migrated look is the bundled theme plus the game's existing colours).

## Capabilities

### New Capabilities

- `game-ui-lists`: list widgets and list bindings in authoring, preview and runtime; bound styles; key navigation; host-driven screen display.

### Modified Capabilities

- `control-line-game-flow`: adds the requirement that the game's screens and HUD are an authored UI asset, driven by game state, with the same screens and controls as before.

## Impact

Depends on `add-game-ui-authoring` (its specs are not in `openspec/specs/` until it is archived, so this change adds a new capability and a requirement instead of modifying them). `lib-core`: `ui.model` / `validation` (list widget, `list` binding type, bound style, key navigation, `UiMeta`), `ui.layout` (repeat rows), `ui.state` (list values, selection), `ui.render` (rows, style binding, arrow keys). `lib-core-editor`: edits for list templates and item fields, preview samples for lists. `plugin-abyssus`: palette, properties, declarations and the sample editor for lists. `app-game-control-line`: new `GameUiHost`, UI asset and bundled fonts, removal of `screens/GameUi.kt`, the demo package, its Gradle task and fixture. Touches the same screens the active F2B changes (`add-f2b-pattern-mode`, `add-f2b-pro-debrief`, `add-f2b-noob-guide`) are still completing; their screen tasks must be finished or rebased first. Documentation: `docs/ai/file-formats.md`, `projects/lib-core/README.md`, the Control Line README and the `add-game-ui-authoring` change's demo references.
