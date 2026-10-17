# scene-view-toolbar Specification

## Purpose

Defines the layout, icons and states of the scene view's toolbar and the editor tab's Preview / source switch, so the view reads as one IntelliJ-style tool strip while every action stays reachable.

## Requirements

### Requirement: Grouped icon toolbar

The scene toolbar SHALL show, left to right, the tool group (Select, Move, Rotate), the space and snap controls, the display controls (shading, Shadows, Fog, Grid), the camera chooser and Frame selection, then the view's other actions (Drop, Add menus, Regenerate, foliage paint, Clouds, Play, Pause, Step, Stop, and overlay actions) in their existing order. Groups SHALL be separated by a divider. Tool, toggle and Frame controls SHALL be icon buttons whose tooltip names the action and its key; drop-down controls SHALL show their current text and a chevron.

#### Scenario: Default toolbar order
- **WHEN** a user opens `Main Scene` of the `Untitled` fixture
- **THEN** the toolbar shows Select, Move (pressed), Rotate, a divider, Local and Snap, a divider, the shading choice and the Shadows, Fog and Grid toggles, a divider, the camera chooser and Frame selection

#### Scenario: Tooltip names the key
- **WHEN** the pointer rests on the Move button
- **THEN** its tooltip reads `Move (W)`

#### Scenario: Existing actions stay reachable
- **WHEN** the scene has models, a terrain with foliage and an available simulation
- **THEN** Drop, Add Light, Add Asset, Add Foliage, Play, Pause, Step and Stop are all on the toolbar

### Requirement: Shared icon set

Toolbar and tab icons SHALL be the Abyssus stroke icons (24 px grid, 1.8 stroke, round caps and joins), drawn at 16 px, in the IDE's icon color, with the accent color for a pressed toggle. Each icon SHALL have a dark and a light variant so it is legible in both themes. A control SHALL NOT fall back to a text label when its icon is missing from the plugin.

#### Scenario: Every control has an icon
- **WHEN** the toolbar is built
- **THEN** every icon button resolves an icon resource and none shows a blank or default placeholder

#### Scenario: Theme change
- **WHEN** the IDE switches from the Dark to the Light theme
- **THEN** the toolbar icons are drawn in the light variant without reopening the view

### Requirement: Pressed and enabled states

A toggle SHALL show a filled accent background when pressed and none when not. A control that is unavailable SHALL be disabled and drawn dimmed, and it SHALL NOT be hidden. While Play runs or the ray experiment is active, the editing controls (tool group, Snap, Local/World, Drop, Add menus) SHALL be disabled and the display controls and camera chooser SHALL stay enabled except where the ray experiment disables the camera chooser.

#### Scenario: Pressed tool
- **WHEN** the user presses `E`
- **THEN** Rotate shows the pressed background and Move does not

#### Scenario: Editing off while playing
- **WHEN** Play is running
- **THEN** Select, Move, Rotate, Snap, Local/World and Drop are disabled, and Shadows, Fog and Grid still toggle

### Requirement: Preview and source tabs

A `.scene` file SHALL open as two editors of one tab, chosen with the IDE's own editor tabs at the bottom of the editor: `Text` (the scene's JSON in the text editor) and `Scene View` (the rendered preview with the toolbar). Opening a scene from Abyssus SHALL select Scene View. Both editors SHALL share one document, so unsaved text and Undo are shared. Switching tabs SHALL write nothing of its own: the text editor's existing formatting of an unformatted scene is unchanged.

#### Scenario: Open from Abyssus
- **WHEN** the user opens `Main Scene` from the Abyssus tree
- **THEN** the tab shows Scene View with its toolbar, and `Text` is the other tab of the same file

#### Scenario: Edit in text then preview
- **WHEN** the user changes a model's name in the `Text` tab and returns to `Scene View`
- **THEN** the preview reflects the edit and Undo reverts it

#### Scenario: Nothing is written by switching
- **WHEN** the user switches between `Text` and `Scene View` repeatedly without typing
- **THEN** the document's text does not change after the text editor first showed it

### Requirement: Toolbar fits the editor width

At an editor width of 900 px the toolbar controls from Select through Frame selection SHALL be fully visible. Controls that do not fit SHALL wrap to a second row, not be clipped or overlap.

#### Scenario: Narrow editor
- **WHEN** the editor area is 900 px wide
- **THEN** no toolbar control overlaps another and the first row ends with Frame selection or wraps cleanly before it

### Requirement: Toolbar stays when the view cannot render

When the scene view cannot render (for example no OpenGL context can be created), the viewport SHALL show the OpenGL-unavailable message in place of the scene and the toolbar SHALL stay above it with every control disabled. Nothing SHALL be written to the scene.

#### Scenario: No OpenGL
- **WHEN** rendering fails for good because no OpenGL context can be created
- **THEN** the viewport shows the message `OpenGL scene is unavailable: <reason>`, the toolbar is still shown with all controls disabled, and the scene file is unchanged
