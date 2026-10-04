## MODIFIED Requirements

### Requirement: Objects without rotation

A camera whose `lookAtId` names an existing entity, a light whose `lookAtId` names an existing entity that is not a
direction handle (an entity whose `TypeComponent.type` is `HANDLE`), and a point light, SHALL show no rotate rings in
Rotate mode, since their orientation comes from their target or doesn't matter. They SHALL still show move arrows in
Move mode. A directional or spot light that looks at a direction handle SHALL show rotate rings.

#### Scenario: Look-at camera in Rotate mode

- **WHEN** `Camera 4` (look-at entity 3) is selected in Rotate mode
- **THEN** no rings are shown

#### Scenario: Light aimed at a model in Rotate mode

- **WHEN** a directional light whose `lookAtId` names a model entity is selected in Rotate mode
- **THEN** no rings are shown, and move arrows are shown in Move mode

#### Scenario: Light aimed at its handle in Rotate mode

- **WHEN** directional light `1` of `Lights/scenes/Mundus Lights.scene` (look-at handle `0`) is selected in Rotate mode
- **THEN** the X, Y and Z rings are shown

### Requirement: Writing a completed drag

Releasing the mouse after a drag that changed the object SHALL write the new transform to that entity in the scene's own
`.scene` file as one undoable edit, keep the file's formatting, and change nothing else. A move SHALL write
`PositionComponent.localPosition`; a rotation SHALL write `PositionComponent.localRotation`. For a camera, a move SHALL
also write `CameraComponent.camera.position`, and a rotation `CameraComponent.camera.viewPointPosition`. For a light that
looks at a direction handle, a rotation SHALL instead write the handle's `PositionComponent.localPosition`, in the same
undoable edit, and leave the light's own `localRotation` unchanged.

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

#### Scenario: Turning a light aimed at its handle moves the handle

- **WHEN** in `Lights/scenes/Mundus Lights.scene` the user turns directional light `1` (at (0, 10, 0), facing down at
  handle `0` at (0, 0, 0)) about X until it faces along -Z, and releases
- **THEN** handle `0`'s `PositionComponent` gets a `localPosition` of about (0, 10, -10), light `1`'s
  `PositionComponent` is unchanged, and every other entity is unchanged

#### Scenario: Turning a light without a look-at target

- **WHEN** the user rotates a directional light that has no `lookAtId` and releases
- **THEN** the light's `PositionComponent.localRotation` holds the new rotation and no other entity changes

#### Scenario: Undo

- **WHEN** the user presses Undo in the scene view after a move
- **THEN** the file returns to its content before the move and the object is drawn at its old place

#### Scenario: Undo a turned look-at light

- **WHEN** the user presses Undo after turning a light aimed at its handle
- **THEN** the handle's `PositionComponent` returns to its content before the turn and the light faces its old
  direction

#### Scenario: Release without change

- **WHEN** the user presses on a handle and releases without moving the cursor
- **THEN** nothing is written

## ADDED Requirements

### Requirement: Turning a light aimed at a direction handle

Dragging a rotate ring of a directional or spot light that looks at a direction handle SHALL turn the light's direction
about that world axis, as for any light, and SHALL keep the handle at the same distance from the light along the turned
direction. During the drag, the light's direction line, shading and spot cone SHALL follow the turned direction. After
release, the light SHALL keep facing the turned direction, both in the scene view and when Mundus opens the file.

#### Scenario: The turn is shown while dragging

- **WHEN** the user drags the X ring of spot light `4` in `Lights/scenes/Mundus Lights.scene`
- **THEN** its cone and direction line turn with the cursor during the drag

#### Scenario: The turn stays after release

- **WHEN** the user turns directional light `1` in `Lights/scenes/Mundus Lights.scene` and releases
- **THEN** the light keeps facing the turned direction and doesn't jump back to its old one

#### Scenario: Esc puts the handle back

- **WHEN** the user presses Esc while turning a light aimed at its handle
- **THEN** the light faces its old direction and nothing is written
