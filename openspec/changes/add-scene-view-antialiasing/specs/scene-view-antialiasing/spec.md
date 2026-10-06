# Spec Delta

## ADDED Requirements

### Requirement: Scene views are antialiased by default

A scene view SHALL draw with 4× multisample antialiasing when the antialiasing setting has never been changed. Grid
lines, gizmos, camera and light markers, selection highlights, overlays from other plugins and model and terrain edges
SHALL be smoothed. Antialiasing SHALL NOT change what a click selects or what is written to the scene file.

#### Scenario: Open a scene with the default setting
- **WHEN** the user opens `Main Scene` of the Untitled project in an IDE where the antialiasing setting was never changed
- **THEN** the grid lines and the model edges in the scene view are drawn smoothed rather than stair-stepped

#### Scenario: Picking is unaffected
- **WHEN** antialiasing is on and the user clicks the same point on an object as with antialiasing off
- **THEN** the same entity is selected

### Requirement: Antialiasing setting

The IDE settings SHALL offer **Scene view antialiasing** under Tools | Abyssus with the choices Off, 2×, 4× and 8×,
showing 4× until the user changes it. The choice SHALL be remembered across IDE restarts and SHALL be shared by every
project open in that IDE. Changing it SHALL leave `.abss`, `.scene` and asset `meta.json` files unchanged.

#### Scenario: Default choice
- **WHEN** the user opens Settings | Tools | Abyssus in an IDE where the setting was never changed
- **THEN** Scene view antialiasing shows 4×

#### Scenario: Choice is remembered
- **WHEN** the user selects Off, applies, and restarts the IDE
- **THEN** the setting shows Off and scene views draw without antialiasing

#### Scenario: Shared by projects
- **WHEN** the user selects 2× while one project is open and then opens another project
- **THEN** the setting shows 2× there and its scene views draw with 2× antialiasing

#### Scenario: No game files change
- **WHEN** the user changes the setting with `Main Scene` open
- **THEN** `Untitled.abss`, `Main Scene.scene` and every asset `meta.json` keep their exact text

### Requirement: Changes apply to open views

Applying a new antialiasing choice SHALL take effect in every open scene view without closing or reopening it. Each
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
