# Spec Delta

## MODIFIED Requirements

### Requirement: Writing a completed drag

Releasing the mouse after a drag that changed the object SHALL write the new transform to that entity in the scene's own
`.scene` file as one undoable edit, keep the file's formatting, and change nothing else. A move SHALL write
`PositionComponent.localPosition`; a rotation SHALL write `PositionComponent.localRotation`. For a camera, a move SHALL
also write `CameraComponent.camera.position`, and a rotation `CameraComponent.camera.viewPointPosition`. For a light that
looks at a direction handle, a rotation SHALL instead write the handle's `PositionComponent.localPosition`, in the same
undoable edit, and leave the light's own `localRotation` unchanged. When the object is a copy placed by a path arrangement, the same
undoable edit SHALL also detach it, as the `scene-path-arrangements` capability describes.

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

- **WHEN** in `Lights/scenes/Abyssus Lights.scene` the user turns directional light `1` (at (0, 10, 0), facing down at
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

#### Scenario: Moving an arranged copy detaches it

- **WHEN** the user moves copy `14`, slot `2` of path arrangement `Path 11`, along Z and releases
- **THEN** in one undoable edit copy `14` gets its new `localPosition`, loses its `ParentComponent`, and slot `2` of
  `Path 11` becomes `-1`; no other entity changes

### Requirement: Writing a completed drop

A drop that moves an object SHALL write the new position to that entity in the scene's own `.scene` file as one
undoable edit, keep the file's formatting, and change nothing else. It SHALL write `PositionComponent.localPosition.y`
and, for a camera entity, `CameraComponent.camera.position`, exactly as a move along Y does. A drop of a copy placed by a path arrangement SHALL also detach it in the same
undoable edit, exactly as a move does.

#### Scenario: Only the height changes

- **WHEN** the user drops `Model 0` in `Main Scene`
- **THEN** only that entity's `localPosition.y` changes in the file, its `x`, `z` and every other value in the file
  are unchanged, and the file keeps its indentation

#### Scenario: Camera drop keeps both positions

- **WHEN** the user drops `Camera 4` in `Main Scene`, whose `localPosition` and `camera.position` are equal
- **THEN** both its `PositionComponent.localPosition.y` and its `CameraComponent.camera.position.y` change by the
  same amount, and their `x` and `z` are unchanged

#### Scenario: Undo a drop

- **WHEN** the user presses Undo in the scene view after a drop
- **THEN** the file returns to its content before the drop and the object is drawn at its old place

#### Scenario: Dropping an arranged copy detaches it

- **WHEN** the user drops copy `13` of path arrangement `Path 11`
- **THEN** copy `13`'s `localPosition.y` changes, it loses its `ParentComponent`, and its slot in `Path 11` becomes `-1`
