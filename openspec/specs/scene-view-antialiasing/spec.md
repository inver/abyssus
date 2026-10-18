# scene-view-antialiasing Specification

## Purpose

TBD: Describe what scene-view antialiasing is for. Update Purpose after archive.

## Requirements

### Requirement: Scene views are antialiased by default

A scene view SHALL draw with 4× multisample antialiasing when the antialiasing setting has never been changed. Grid
lines, gizmos, camera and light markers, selection highlights, overlays from other plugins and model and terrain edges
SHALL be smoothed. Antialiasing SHALL NOT change what a click selects or what is written to the scene file.

#### Scenario: Open a scene with the default setting
- **WHEN** the user opens `Main Scene` of the Untitled project whose project omits `sceneViewAntialiasing`
- **THEN** the grid lines and the model edges in the scene view are drawn smoothed rather than stair-stepped

#### Scenario: Picking is unaffected
- **WHEN** antialiasing is on and the user clicks the same point on an object as with antialiasing off
- **THEN** the same entity is selected

### Requirement: Antialiasing setting

A native project's Abyssus Properties panel SHALL offer **Scene view antialiasing** with Off, 2×, 4× and 8×,
showing 4× by default. The IDE Settings dialog SHALL NOT contain this control. The project's `.abss` SHALL persist
`sceneViewAntialiasing` as `OFF`, `X2`, `X4` or `X8`, with the default omitted. Each native project SHALL be independent.

Reading and editing SHALL validate native markers first. Unsupported projects SHALL be read-only. Missing values
SHALL use 4×. Invalid present values SHALL report a localized problem and use 4× without rewriting the document.
Opening a project SHALL NOT insert defaults or migrate an old application preference.

Changing the value SHALL be one undoable edit through `editSceneJson`, touching only the setting and preserving
unknown data, key order, unrelated number text and formatting. `.scene` and asset `meta.json` files SHALL stay unchanged.

#### Scenario: Default choice
- **WHEN** the user selects a native project whose `.abss` omits the field
- **THEN** Scene view antialiasing shows 4× without writing any file

#### Scenario: Choice is remembered
- **WHEN** the user selects Off in project properties and reopens the project
- **THEN** its `.abss` contains `"sceneViewAntialiasing":"OFF"` and its views draw without antialiasing

#### Scenario: Projects are independent
- **WHEN** the user selects 2× for one native project
- **THEN** a second native project, including one open in the same IDE project, keeps its own choice

#### Scenario: Undo and external editing
- **WHEN** the user changes the field in an unsaved text document or invokes Undo/Redo after a properties edit
- **THEN** the project control and its open scene views reflect the resulting value

#### Scenario: Only the project preference changes
- **WHEN** the user chooses 8× and then 4×
- **THEN** the first edit writes `X8` and the second removes the field, while unrelated `.abss` text, scenes and assets stay unchanged

#### Scenario: Unsupported native project
- **WHEN** a project has unsupported format markers
- **THEN** the setting cannot edit it and its text stays unchanged

### Requirement: Changes apply to open views

Applying a new antialiasing choice SHALL take effect in every open scene view of that native project without closing or reopening it. Each
view SHALL keep its camera, selection, chosen look-through camera, Move/Rotate mode, Play state and Ray Tracing mode.
A view that is hidden when the choice changes SHALL use the new choice when it is shown again.

#### Scenario: Turn antialiasing off in an open view
- **WHEN** `Main Scene` is open with an object selected and the camera orbited, and the user applies Off
- **THEN** the view draws without antialiasing, with the same object selected and the same camera view

#### Scenario: Hidden view
- **WHEN** `Main Scene` is open in a background editor tab and the user applies 8×
- **THEN** switching to that tab shows the scene drawn with the new choice

### Requirement: Unsupported sample counts fall back

When the graphics driver refuses the chosen sample count, the scene view SHALL try each lower choice in turn down to
Off and SHALL keep drawing the scene with the first one accepted. The view SHALL NOT stay blank or report a failure
because of antialiasing, and the setting SHALL keep the user's choice.

#### Scenario: Driver refuses 8×
- **WHEN** the setting is 8× and the driver accepts at most 4×
- **THEN** `Main Scene` is shown with 4× antialiasing and the setting still shows 8×

#### Scenario: Driver refuses multisampling
- **WHEN** the driver accepts no multisampled format
- **THEN** `Main Scene` is shown without antialiasing and no scene view error is displayed
