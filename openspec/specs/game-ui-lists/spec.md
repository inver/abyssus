# game-ui-lists Specification

## Purpose

Lets authored game UI show repeated rows from game data (score tables, results sheets, debrief rows), style a single value from game data, and be driven by keyboard menus and by a game's own screen state, so a game's real screens can be authored instead of coded.

## Requirements

### Requirement: List widget
The UI model SHALL provide a list widget that repeats one authored template row for each item of a list binding, in item order, inside a row or column layout of its own. A list SHALL support an optional maximum number of rows and an optional empty-state widget shown when the list has no items. Widgets inside the template row SHALL bind to fields of the current item; they SHALL NOT bind to the item of another row. Visual editing SHALL treat the template as one editable subtree, and unknown list members SHALL be preserved.

#### Scenario: Rows follow the data
- **WHEN** a list is bound to a binding that currently holds three items
- **THEN** three rows are laid out, each showing its own item's field values, and a fourth item added by the host appears on the next update without rebuilding the screen

#### Scenario: Empty list
- **WHEN** the list has no items and an empty-state widget is authored
- **THEN** only the empty-state widget is shown

#### Scenario: Row limit
- **WHEN** a list has a maximum of ten rows and the host publishes twelve items
- **THEN** the first ten rows are shown

#### Scenario: Template edited in the editor
- **WHEN** a user adds a label to the template row and binds it to an item field
- **THEN** the canvas shows it in every row of the sample data, and undo restores the previous document text exactly

### Requirement: List bindings
A UI asset SHALL be able to declare a binding of type list with a declared record of typed item fields (number, boolean, string) and a sample list. A host SHALL publish a list value as items of those fields. A list binding SHALL be read-only. A missing or wrongly typed list, or an item missing a declared field, SHALL show the sample or the field's zero value with a diagnostic and SHALL NOT stop unrelated widgets. Widgets whose binding names a field that the record does not declare SHALL be reported when the asset is opened.

#### Scenario: Wrong item field type
- **WHEN** the host publishes an item whose number field holds text
- **THEN** that field shows its zero value with a diagnostic naming the field and the other fields and rows are unaffected

#### Scenario: Undeclared item field
- **WHEN** a template widget binds to a field the record does not declare
- **THEN** the asset opens with a diagnostic and the widget shows nothing for that property

### Requirement: Bound styles
A widget's `style` SHALL be bindable to a string binding or list item field. The named style SHALL be resolved each update with the same priority as an authored style; an unknown style name SHALL fall back to the bundled look with a diagnostic and SHALL NOT change the document.

#### Scenario: Highlighted row
- **WHEN** a row's style field holds `highlight` and the asset declares a `highlight` style
- **THEN** that row's widgets draw with the highlight style and other rows do not

#### Scenario: Unknown style from the game
- **WHEN** the host publishes a style name the asset does not declare
- **THEN** the widget uses the bundled look and a diagnostic names the style

### Requirement: Keyboard menu navigation
A screen SHALL declare how keyboard focus moves: through Tab and Shift-Tab (the default) or through Up and Down. With Up and Down, focus moves through the screen's visible enabled interactive widgets in tree order and wraps, Enter or Space activates a focused button, Escape keeps its overlay-or-back rule, and Tab remains available. Disabled and hidden widgets SHALL be skipped. The first enabled widget SHALL have focus when the screen opens.

#### Scenario: Arrow menu
- **WHEN** a main menu declares Up/Down navigation and its first button is disabled
- **THEN** the second button has focus on open, Down moves to the third, Up from the second wraps to the last, and Enter presses the focused button once

#### Scenario: Default unchanged
- **WHEN** a screen declares no key navigation
- **THEN** Up and Down do not move focus and Tab behaves as before

### Requirement: Host-driven screens
A host SHALL be able to show exactly one named screen or overlay of a UI asset at a time, replacing what was shown, without navigation history, so a game's own state machine can decide the screen. Showing a screen SHALL NOT change the document, and showing an unknown screen SHALL be reported and leave the current UI unchanged. Authored navigation (open screen, back, overlays) SHALL keep working for assets that use it.

#### Scenario: Game shows the pause screen
- **WHEN** the game state changes to paused and the host shows the `paused` overlay over the HUD
- **THEN** the HUD stays visible, the pause screen takes keyboard input and Escape or its Resume button returns the game to flying

#### Scenario: Unknown screen
- **WHEN** the host asks for a screen the asset does not have
- **THEN** a diagnostic names it and the previous screen stays

### Requirement: Lists in preview
Interactive preview SHALL let the user edit a list binding's sample items (add, remove, change a field) with the type and constraints of the declared record, and SHALL show list rows, bound styles and arrow-key navigation exactly as a game would. Sample items SHALL stay session-local and reset on request.

#### Scenario: Edit sample rows
- **WHEN** a user adds a sample row in preview and then resets
- **THEN** the row appears while previewing and the authored sample list returns after reset, with the document unchanged

### Requirement: List limits
The existing depth and widget-count limits SHALL apply to the expanded rows of a list as well as the authored widgets. A list that would exceed them SHALL show the rows that fit and a diagnostic, and an authored template SHALL be refused when it exceeds them by itself.

#### Scenario: Too many rows
- **WHEN** the host publishes more items than the widget limit allows
- **THEN** rows stop at the limit, a diagnostic is reported and rendering continues

### Requirement: Clickable rows
A panel, row or column SHALL be able to carry a click action, so that a whole row of a list is one target. A click action on a widget inside a list row that emits a named game action SHALL reach the host as `<name>#<row>`, with the zero-based index of the row it was clicked in, so the host knows which item was chosen. A clickable container SHALL show hover and pressed looks from its styles, SHALL consume the click (it does not reach the game), and SHALL NOT take keyboard focus.

#### Scenario: Choose a row
- **WHEN** the third row of a list whose row container emits `openRow` is clicked
- **THEN** the host receives exactly one action named `openRow#2`

#### Scenario: Not in the focus order
- **WHEN** Tab or an arrow key moves focus on a screen with clickable rows
- **THEN** focus visits only buttons, sliders, checkboxes and text fields

### Requirement: Select on focus
A text field MAY declare that its text is selected when it gains focus; for such a field the first character typed (or Backspace) SHALL replace the whole text instead of editing it, and typing after that SHALL edit normally.

#### Scenario: Replace the last name
- **WHEN** a name field with select-on-focus holds "Ann" and gains focus and the player types "B"
- **THEN** the field holds "B"