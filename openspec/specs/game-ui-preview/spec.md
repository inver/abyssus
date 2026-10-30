# game-ui-preview Specification

## Purpose

Lets creators exercise authored menus and overlays at multiple resolutions with isolated sample state and scene backdrops before running a game.

## Requirements

### Requirement: Edit and preview modes
The visual editor SHALL distinguish editing from interactive preview. Preview SHALL consume current unsaved UI text and allow pointer and keyboard interaction, while edit mode SHALL select and manipulate widgets. Returning to editing SHALL restore authoring selection and leave document text and undo history unchanged by preview interactions.

#### Scenario: Button versus selection
- **WHEN** the user clicks a button in edit mode and then clicks it in preview mode
- **THEN** the first click selects the widget and the second executes its preview action without moving it

### Requirement: Isolated sample bindings
Preview SHALL supply editable typed sample values and validate write-back against binding types and declared constraints. Changes SHALL remain session-local, update all bound widgets and reset on request. Game actions SHALL be logged instead of executed; preview SHALL NOT launch game code, save game settings or alter authored defaults.

#### Scenario: Adjust volume in preview
- **WHEN** a slider updates the writable sample volume and a startGame button is activated
- **THEN** sample volume updates across the preview and startGame appears in the action log
- **AND** no game starts and no document or game setting is written

#### Scenario: Reset samples
- **WHEN** the user resets preview
- **THEN** values return to authored samples, navigation returns to the selected initial screen and the action log clears

### Requirement: Resolution and scene backdrop
Preview SHALL offer selectable and custom positive viewport dimensions, standalone backgrounds, and an optional selected native scene backdrop for overlays. A scene backdrop SHALL be read-only and SHALL NOT require physics Play. The selected resolution SHALL control UI layout independently of the editor panel's display size.

#### Scenario: Preview on Untitled
- **WHEN** an overlay is previewed against Main Scene in a copy of Untitled at 1280 by 720
- **THEN** the authored scene appears behind the UI at the chosen aspect ratio without running physics or modifying Main Scene

### Requirement: Refresh and failure recovery
Preview SHALL refresh when UI text or referenced styling resources change. Invalid edits SHALL replace interaction with a diagnostic until corrected, rather than silently running stale controls. Closing the editor SHALL release preview resources and input capture.

#### Scenario: Invalid unsaved edit
- **WHEN** the user makes UI text invalid and then corrects it
- **THEN** preview first reports the error with interaction stopped, then rebuilds from the corrected text

#### Scenario: Close preview
- **WHEN** a preview editor is closed while a text field has focus
- **THEN** it no longer consumes input or retains its scene and graphics resources