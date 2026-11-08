# Spec Delta

## MODIFIED Requirements

### Requirement: Writing a completed drag

Releasing the mouse after a drag that changed the object SHALL write the new transform to that entity in the scene's own
`.scene` file as one undoable edit, keep the file's formatting, and change nothing else. A move SHALL write
`PositionComponent.localPosition`; a rotation SHALL write `PositionComponent.localRotation`. For a child entity, both
SHALL be written as values local to its parent (or to its parent's model node), and its parent and its own children
SHALL NOT be written. For a camera, a move SHALL
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
- **THEN** both its `PositionComponent.localPosition.y` and its `CameraComponent.camera.position.y` grow by 1, and its
  child `'Direction' handle` (entity `3`) is unchanged in the file and drawn 1 higher

#### Scenario: Missing fields are added

- **WHEN** an entity's `PositionComponent` has no `localRotation` (identity) and the user rotates it
- **THEN** a `localRotation` with `x`, `y`, `z` and `w` is added to that `PositionComponent`

#### Scenario: Turning a light aimed at its handle moves the handle

- **WHEN** in `Lights/scenes/Abyssus Lights.scene` the user turns directional light `1` (at (0, 10, 0), facing down at
  its child handle `0`, whose `localPosition` is (0, -10, 0)) about X until it faces along -Z, and releases
- **THEN** handle `0`'s `PositionComponent` gets a `localPosition` of about (0, 0, -10), so it sits at about
  (0, 10, -10) in world space; light `1`'s `PositionComponent` is unchanged, and every other entity is unchanged

#### Scenario: Turning a light without a look-at target

- **WHEN** the user rotates a directional light that has no `lookAtId` and releases
- **THEN** the light's `PositionComponent.localRotation` holds the new rotation and no other entity changes

#### Scenario: Moving a child writes its local position

- **WHEN** a dummy with `localPosition` (0, 1, 0) is the child of `Model 9` (no rotation, no scale) and the user moves
  the dummy along X by 2 and releases
- **THEN** the dummy's `localPosition` is (2, 1, 0) and `Model 9` is unchanged

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
- **THEN** in one undoable edit copy `14` gets its new world position as its `localPosition`, loses its
  `ParentComponent`, and slot `2` of `Path 11` becomes `-1`; no other entity changes
