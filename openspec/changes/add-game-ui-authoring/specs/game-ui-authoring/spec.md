# Spec Delta

## Purpose

Lets creators build reusable game menus and overlays visually, with responsive layouts and custom styling stored in native project assets.

## ADDED Requirements

### Requirement: Native UI assets
The editor SHALL create UI assets under project assets with native markers, type UI and additional.ui containing schemaVersion 1, entryScreen, screens, bindings, resources and styles. It SHALL register created assets in the project's uiAssetNames list. Missing optional values SHALL remain omitted on read.

#### Scenario: Create a menu
- **WHEN** a user creates a UI asset named menus in a copy of Untitled
- **THEN** assets/menus/meta.json contains native version 1 metadata and an editable initial screen, and Untitled.abss registers menus in uiAssetNames
- **AND** no existing scene is changed

### Requirement: UI admission and preservation
Unsupported native or UI versions SHALL be refused before editing or previewing, without rewriting input. Unknown extension fields and widget kinds SHALL be preserved; unknown widgets SHALL have a visible unsupported placeholder. Edits SHALL preserve unrelated key order, number text and omitted defaults.

#### Scenario: Future UI schema
- **WHEN** additional.ui.schemaVersion is 2
- **THEN** a version reason is shown and neither opening nor preview changes the document

#### Scenario: Extension data survives an edit
- **WHEN** a label is edited beside an unknown widget and extension data containing 1.000
- **THEN** only the requested label field changes and the unknown content and number spelling remain intact

### Requirement: Visual widget editing
The editor SHALL offer a widget tree, selectable canvas and properties for panels, rows, columns, labels, images, buttons, progress bars, sliders, checkboxes and text fields. It SHALL support adding, duplicating, reparenting, reordering and deleting widgets with stable unique IDs and undo/redo. A drag SHALL produce one undoable edit.

#### Scenario: Reparent and undo
- **WHEN** a button is moved into a column and the operation is undone
- **THEN** its original parent, order and document text are restored

#### Scenario: Invalid hierarchy
- **WHEN** a user tries to parent a panel beneath its own descendant
- **THEN** the edit is rejected with an explanation and no document changes

### Requirement: Responsive layout
Widgets SHALL support anchored placement and explicit sizes inside panels, and automatic row/column sizing with padding, spacing, alignment and grow behavior. Layouts SHALL use a shared reference size and uniform fit scaling. Row/column children SHALL be positioned by their parent rather than contradictory free offsets.

#### Scenario: HUD and menu resize
- **WHEN** a top-right anchored HUD and a centered column of buttons are previewed at 1280 by 720 and 1024 by 768
- **THEN** the HUD retains its reference-space edge offsets and the column stays centered with ordered, spaced children

### Requirement: Custom styling
A bundled default theme SHALL be usable without custom resources. Authors SHALL be able to override fonts, font sizes, colors, images, backgrounds, padding and normal, hover, focused, pressed and disabled appearances. Missing resources SHALL show diagnostics and a fallback without rewriting the style.

#### Scenario: Styled button
- **WHEN** a button uses a custom font and distinct hover and disabled backgrounds
- **THEN** the preview uses those overrides in the corresponding states

#### Scenario: Missing image
- **WHEN** a referenced image file is missing
- **THEN** its widget shows a placeholder and a diagnostic naming the resource while other widgets remain usable

