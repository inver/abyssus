# scene-view-tools Specification

## Purpose

Defines the scene view's Select tool, the Local / World gizmo space and Snap, which control how the user picks objects and how gizmo drags change them.

## Requirements

### Requirement: Select tool

The scene view SHALL offer a Select tool, activated by the toolbar button or `Q` while the view has focus. In Select mode a click that does not drag SHALL select as in the other modes, and the selected object SHALL be highlighted but SHALL show no move arrows and no rotate rings. A drag SHALL orbit or pan the camera. Select SHALL NOT write the scene.

#### Scenario: Select with the key
- **WHEN** the user presses Q with `Model 0` selected
- **THEN** the object stays selected and highlighted and no handles are drawn

#### Scenario: Drag does not move
- **WHEN** the user drags on `Model 0` in Select mode
- **THEN** the camera orbits, the object does not move and the file is unchanged

#### Scenario: Back to Move
- **WHEN** the user presses W after Q
- **THEN** the move arrows are shown on the selected object

### Requirement: Local and World space

The scene view SHALL have a Local / World choice for the gizmo, World by default. In World the handles follow the world axes. In Local, move arrows and rotate rings SHALL follow the selected object's own rotation. An object with no rotation SHALL show the same handles in both. The choice SHALL apply to the view only and SHALL NOT be written to the scene.

#### Scenario: Rotated model in Local
- **WHEN** a model rotated 35 degrees about Y is selected in Move mode with Local chosen
- **THEN** the arrows are turned 35 degrees about Y from the world axes

#### Scenario: Same handles when unrotated
- **WHEN** an unrotated object is selected and the user switches between Local and World
- **THEN** the handles do not change

#### Scenario: Space is not saved
- **WHEN** the user chooses Local and then closes and reopens the scene
- **THEN** the view starts in World and the file is unchanged by the choice

### Requirement: Local drags write world values

A move drag along a Local axis SHALL change the object's position along that axis in object space and the finished drag SHALL be written as the same position fields a World drag writes. A rotate drag in Local SHALL rotate about the object's own axis. Numbers the drag did not change SHALL keep their text.

#### Scenario: Move along a local axis
- **WHEN** a model rotated 90 degrees about Y is dragged along its local X arrow by 2 units
- **THEN** the position changes by 2 units along the world Z direction and one edit is written

### Requirement: Snap

The scene view SHALL have a Snap toggle with a step, off by default and 0.5 when first turned on. While on, a move drag SHALL round the dragged object's position along the dragged axis to a multiple of the step, and a rotate drag SHALL round the angle to a multiple of 15 degrees. The finished value is written as ordinary position or rotation fields; the toggle and step SHALL NOT be written. Escape cancels a drag exactly as without Snap.

#### Scenario: Move snaps to the step
- **WHEN** Snap is on at 0.5 and a model is dragged along X to 2.31
- **THEN** the preview and the written position X are 2.5

#### Scenario: Rotate snaps to 15 degrees
- **WHEN** Snap is on and a model is rotated about Y to 37 degrees
- **THEN** the preview and the written rotation are 30 degrees

#### Scenario: Snap off
- **WHEN** Snap is off and a model is dragged to 2.31
- **THEN** the written position X is 2.31

#### Scenario: Cancel a snapped drag
- **WHEN** Snap is on, a drag is in progress and the user presses Escape
- **THEN** the object returns to its old place and the file is unchanged

### Requirement: Tool state in the stats strip

The stats strip of the viewport SHALL show the current tool, space and snap step, such as `Move · Local · Snap 0.5`, and SHALL update as they change. No separate status row is shown under the viewport.

#### Scenario: Status follows tools
- **WHEN** the user presses E and turns Snap off
- **THEN** the stats strip ends with `Rotate · World`
