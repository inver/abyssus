# Spec Delta

## Purpose
Defines how the Game UI editor tab arranges its parts so that the whole editing workflow is usable in an editor area about 1190 px wide, as on a 14" laptop with the project tree open.

## ADDED Requirements

### Requirement: Compact editor arrangement
The Game UI editor SHALL show a widget tree, a canvas and a side panel at an editor width of 1190 px, with the canvas wider than either side column. Screen management and widget creation SHALL NOT occupy permanent space in the widget tree column.

#### Scenario: Reference width
- **WHEN** a UI asset is open in an editor area 1190 px wide and 880 px tall
- **THEN** the widget tree, canvas and side panel are all visible without horizontal scrolling and the canvas is the widest part

### Requirement: Screens menu
The editor SHALL show the displayed screen's name and marks in a toolbar control that opens a menu of all screens with the entry and overlay marks, and offers Add Screen, Add Overlay, Rename, Delete and the arrow-focus switch. Choosing a screen SHALL display it.

#### Scenario: Switch screen
- **WHEN** the screens menu is opened and another screen is chosen
- **THEN** that screen is displayed and its widgets replace the tree

#### Scenario: Manage screens from the menu
- **WHEN** Add Screen is chosen and a name is entered
- **THEN** one undoable edit adds the screen and the menu lists it

### Requirement: Add widget menu
The widget tree header SHALL offer an Add control that opens a menu of every widget kind and adds the chosen kind to the selected container (or the selected widget's parent when the selection cannot contain widgets). The menu SHALL name the target container. Duplicate and Delete SHALL be one click from the same header, and the reorder and list empty-state actions SHALL remain available.

#### Scenario: Add into selection
- **WHEN** a column is selected and Button is chosen from the Add menu
- **THEN** a button is added as the column's last child as one undoable edit

#### Scenario: Leaf selected
- **WHEN** a button is selected and Label is chosen from the Add menu
- **THEN** the label is added to the button's parent and the menu named that parent

### Requirement: Fitted canvas
The canvas SHALL show the displayed screen scaled to fit its area without stretching, report the zoom, and keep selection, hover, drag and Preview pointer input aligned with what is drawn at any zoom.

#### Scenario: Pick at reduced zoom
- **WHEN** the canvas is narrower than the reference size and a widget is clicked
- **THEN** that widget is selected

### Requirement: Unchanged editing behavior
Rearranging the editor SHALL NOT change which edits exist, their undo granularity, their validation, the properties shown or the Preview behavior.

#### Scenario: Same document result
- **WHEN** the same sequence of add, drag and delete edits is made before and after this change
- **THEN** the resulting document text is identical
