# Spec Delta

## Purpose

Defines how the user selects models, terrains, cameras and lights in the scene view and moves or rotates them with
gizmo handles, with each completed drag written to the scene file.

## ADDED Requirements

### Requirement: Selecting objects in the view

A left click that doesn't drag SHALL select the nearest model, terrain, camera or light under the cursor, highlight it,
and select it in the Abyssus tree as clicking does today. A click on empty space SHALL clear the view's selection. Light
entities SHALL be drawn as small markers, with a direction line for directional and spot lights, so they can be clicked.

#### Scenario: Select a camera

- **WHEN** the user clicks `Camera 4`'s body in `Main Scene`
- **THEN** `Camera 4` is highlighted, shows the gizmo of the current mode, and entity 4 is selected in the Abyssus tree

#### Scenario: Select a light

- **WHEN** the user clicks a point light's marker
- **THEN** the light is highlighted and shows the gizmo of the current mode

#### Scenario: Click on empty space

- **WHEN** the user clicks where no object is
- **THEN** no object is highlighted and no gizmo is shown

#### Scenario: Selected object leaves the scene

- **WHEN** the selected entity is removed from the scene file
- **THEN** the selection is cleared

### Requirement: Move and rotate modes

The scene view SHALL have a Move and a Rotate mode, switched by a toolbar toggle or by W (Move) and E (Rotate) while
the view has focus; Move SHALL be the initial mode. In Move mode the selected object SHALL show X, Y and Z arrows along
the world axes; in Rotate mode, X, Y and Z rings around them. Handles SHALL keep a constant size on screen and be drawn
on top of the scene.

#### Scenario: Switch with keys

- **WHEN** an object is selected and the user presses E, then W
- **THEN** the rotate rings are shown, then the move arrows

#### Scenario: Handles stay visible

- **WHEN** the selected object is behind a terrain or far from the camera
- **THEN** its handles are still drawn on top, at the same screen size

### Requirement: Objects without rotation

A camera whose `lookAtId` names an existing entity, and a point light, SHALL show no rotate rings in Rotate mode, since
their orientation comes from their target or doesn't matter. They SHALL still show move arrows in Move mode.

#### Scenario: Look-at camera in Rotate mode

- **WHEN** `Camera 4` (look-at entity 3) is selected in Rotate mode
- **THEN** no rings are shown

### Requirement: Dragging a move arrow

Dragging a move arrow SHALL move the object along that world axis only, following the cursor, and the view SHALL show
the object at its new place while dragging. A drag that starts on a handle SHALL NOT orbit or pan the view. A drag that
starts anywhere else SHALL orbit or pan as before.

#### Scenario: Move along X

- **WHEN** the user drags the X arrow of a model at (0, 0, 0) so the cursor follows the axis to x = 5
- **THEN** the model is drawn at about (5, 0, 0) during the drag, and its y and z don't change

#### Scenario: Drag off a handle orbits

- **WHEN** a model is selected and the user drags starting on empty space
- **THEN** the view orbits and the model doesn't move

### Requirement: Dragging a rotate ring

Dragging a rotate ring SHALL rotate the object about that world axis through its own position, by the angle the cursor
sweeps around the ring, and the view SHALL show the rotation while dragging. A directional or spot light's direction,
and a camera's view direction, SHALL turn with it.

#### Scenario: Rotate a model about Y

- **WHEN** the user drags the Y ring of a model through a quarter turn
- **THEN** the model is drawn rotated about 90° about the world Y axis, and its position doesn't change

#### Scenario: Turn a directional light

- **WHEN** the user rotates a directional light about X
- **THEN** its direction line and the scene's shading follow the new direction during the drag

### Requirement: Writing a completed drag

Releasing the mouse after a drag that changed the object SHALL write the new transform to that entity in the scene's own
`.scene` file as one undoable edit, keep the file's formatting, and change nothing else. A move SHALL write
`PositionComponent.localPosition`; a rotation SHALL write `PositionComponent.localRotation`. For a camera, a move SHALL
also write `CameraComponent.camera.position`, and a rotation `CameraComponent.camera.viewPointPosition`.

#### Scenario: Move is written

- **WHEN** the user moves entity 0 of `Main Scene` along X by 2 and releases
- **THEN** that entity's `localPosition.x` in the file grows by 2, its `y`, `z` and every other value are unchanged,
  and the file keeps its indentation

#### Scenario: Camera move keeps both positions

- **WHEN** the user moves `Camera 4` along Y by 1 and releases
- **THEN** both its `PositionComponent.localPosition.y` and its `CameraComponent.camera.position.y` grow by 1

#### Scenario: Missing fields are added

- **WHEN** an entity's `PositionComponent` has no `localRotation` (identity) and the user rotates it
- **THEN** a `localRotation` with `x`, `y`, `z` and `w` is added to that `PositionComponent`

#### Scenario: Undo

- **WHEN** the user presses Undo in the scene view after a move
- **THEN** the file returns to its content before the move and the object is drawn at its old place

#### Scenario: Release without change

- **WHEN** the user presses on a handle and releases without moving the cursor
- **THEN** nothing is written

### Requirement: Cancelling a drag

Pressing Esc during a drag SHALL put the object back where it was before the drag, end the drag, and write nothing.

#### Scenario: Esc during a move

- **WHEN** the user drags a move arrow, presses Esc, then releases
- **THEN** the object is back at its old place and the file is unchanged
