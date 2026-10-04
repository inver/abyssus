# scene-object-transform Specification

## Purpose

Defines how the user selects models, terrains, cameras and lights in the scene view and moves or rotates them with
gizmo handles, with each completed drag written to the scene file.

## Requirements

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

- **WHEN** directional light `1` of `Lights/scenes/Abyssus Lights.scene` (look-at handle `0`) is selected in Rotate mode
- **THEN** the X, Y and Z rings are shown

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

### Requirement: Cancelling a drag

Pressing Esc during a drag SHALL put the object back where it was before the drag, end the drag, and write nothing.

#### Scenario: Esc during a move

- **WHEN** the user drags a move arrow, presses Esc, then releases
- **THEN** the object is back at its old place and the file is unchanged

### Requirement: The drop action

The scene view SHALL offer a Drop action, reachable from a toolbar button and from the `D` key while the view has
focus. The action SHALL be available for a selected model, camera or light that the view draws. It SHALL NOT be
available for a terrain, which is ground rather than something placed on it, nor for the camera the view is looking
through, whose marker is not drawn. The button SHALL be disabled whenever the action is not available, the selection has
no surface to rest on, or the object already rests on its surface, and pressing `D` in any of those states SHALL do
nothing. Pressing `D` while a gizmo drag is in progress SHALL do nothing.

#### Scenario: Drop with the key

- **WHEN** an object with a surface below it is selected and the user presses D
- **THEN** the object is dropped onto that surface

#### Scenario: Drop with the toolbar

- **WHEN** an object with a surface below it is selected and the user clicks the Drop button
- **THEN** the object is dropped onto that surface

#### Scenario: The button follows the selection

- **WHEN** the user selects an object that has a surface below it, then selects one that has none
- **THEN** the Drop button is enabled, then disabled

#### Scenario: The button follows loading

- **WHEN** an object is selected while the assets of the scene are still loading, and a model it is over finishes
  loading
- **THEN** the Drop button becomes enabled without the user selecting the object again

#### Scenario: Nothing below

- **WHEN** an object with no surface below it is selected and the user presses D
- **THEN** the object does not move, no edit is written, and the Drop button is disabled

#### Scenario: A light can be dropped

- **WHEN** the user drops a light whose marker is over a model
- **THEN** the light marker moves along Y to rest on the top of that model's box

#### Scenario: The looked-through camera is not a surface

- **WHEN** the view looks through `Camera 4` and the user drops an object whose footprint is under that camera's position
- **THEN** the object does not rest on a camera marker, because none is drawn for it

#### Scenario: Already resting disables the button

- **WHEN** the selected object's lowest point is within 0.0001 world units of the surface under it
- **THEN** the Drop button is disabled, pressing D does nothing and no edit is written

#### Scenario: Nothing is selected

- **WHEN** no object is selected
- **THEN** the Drop button is disabled and pressing D does nothing

#### Scenario: A terrain cannot be dropped

- **WHEN** the user selects the `Terrain` entity of `Main Scene`
- **THEN** the Drop button is disabled and pressing D does nothing

#### Scenario: D during a drag

- **WHEN** the user is dragging a move arrow and presses D
- **THEN** the drag continues and nothing is dropped

### Requirement: Dropping rests an object on the surface below

A drop SHALL move the selected object along world Y only, leaving its X, Z and rotation unchanged, so that its lowest
point rests on the highest surface under it. Surfaces and footprints are bounding boxes, not meshes:

- The object's footprint is the outline, seen from above, of its own bounding box under its position, rotation and
  scale. Its lowest point is the lowest corner of that box.
- A model, camera or light marker is a surface when its own rotated bounding box overlaps the footprint seen from above
  and the bottom of that box is below the object's lowest point, or within 0.0001 world units above it. It offers the
  top of that box. A surface whose bottom is higher than that is not under the object and is ignored. A camera or
  light's box is the marker the view draws around it. The camera the view looks through has no marker and is not a
  surface, and the object being dropped is never its own surface.
- A terrain is a surface wherever the footprint is over it. It offers its highest height under the footprint, to the
  precision of its own height grid.

The object rests on the highest surface offered. When that surface is above the object's lowest point, because the
object is sunk into it, the drop raises the object onto it. Two heights within 0.0001 world units of each other SHALL
count as equal, so an object already resting on its surface SHALL NOT move and no edit SHALL be written.

#### Scenario: Drop a model onto the terrain

- **WHEN** the user drops `Model 0` in `Main Scene`, whose terrain is flat at height 0
- **THEN** `Model 0` moves along Y until its lowest point is at height 0, the terrain's highest height under its
  footprint, and its X, Z and rotation are unchanged

#### Scenario: The highest surface wins

- **WHEN** the user drops an object whose footprint is over both a model and the terrain
- **THEN** the object comes to rest on the higher of the two

#### Scenario: A rotated object uses its own box

- **WHEN** the user drops a rotated object next to a model that only the full extent of the object's axis-aligned
  bounding box would reach, with the terrain below both
- **THEN** it comes to rest on the terrain, not on that model; and when its own rotated box is over the model, it
  comes to rest on the model

#### Scenario: A wide object over a narrow surface

- **WHEN** the user drops an object whose footprint covers a smaller model below it while none of its corners is over
  that model
- **THEN** the object comes to rest on that model

#### Scenario: A bump between the corners

- **WHEN** the user drops an object over a terrain that rises higher between the footprint's corners than at them
- **THEN** the object comes to rest on the highest point of the terrain under its footprint, not sunk into the bump

#### Scenario: A tilted terrain uses the actual surface

- **WHEN** the user drops an object onto terrain with pitch or roll and non-uniform scale
- **THEN** the object rests on the highest transformed height-field point under its world footprint, rather than a
  height sampled at the terrain's zero-height plane

#### Scenario: An object sunk into a surface

- **WHEN** the user drops an object whose lowest point is inside a model's box, below its top and above its bottom
- **THEN** the object rises to rest on that model's top and does not fall through it

#### Scenario: A surface entirely above is ignored

- **WHEN** the user drops an object that sits under a model whose box is wholly above it, with the terrain below
- **THEN** the object rests on the terrain

#### Scenario: A camera can be dropped

- **WHEN** the user drops `Camera 4` in `Main Scene` while the view uses the free camera
- **THEN** the camera marker moves along Y to the surface under it

#### Scenario: Already resting

- **WHEN** the user drops an object whose lowest point is within 0.0001 world units of the surface under it
- **THEN** the object does not move and no edit is written

#### Scenario: Repeated drops

- **WHEN** the user drops the same object twice in a row
- **THEN** the second drop leaves it where the first put it and writes no edit

### Requirement: Writing a completed drop

A drop that moves an object SHALL write the new position to that entity in the scene's own `.scene` file as one
undoable edit, keep the file's formatting, and change nothing else. It SHALL write `PositionComponent.localPosition.y`
and, for a camera entity, `CameraComponent.camera.position`, exactly as a move along Y does.

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

### Requirement: Turning a light aimed at a direction handle

Dragging a rotate ring of a directional or spot light that looks at a direction handle SHALL turn the light's direction
about that world axis, as for any light, and SHALL keep the handle at the same distance from the light along the turned
direction. During the drag, the light's direction line, shading and spot cone SHALL follow the turned direction. After
release, the light SHALL keep facing the turned direction, both in the scene view and when Mundus opens the file.

#### Scenario: The turn is shown while dragging

- **WHEN** the user drags the X ring of spot light `4` in `Lights/scenes/Abyssus Lights.scene`
- **THEN** its cone and direction line turn with the cursor during the drag

#### Scenario: The turn stays after release

- **WHEN** the user turns directional light `1` in `Lights/scenes/Abyssus Lights.scene` and releases
- **THEN** the light keeps facing the turned direction and doesn't jump back to its old one

#### Scenario: Esc puts the handle back

- **WHEN** the user presses Esc while turning a light aimed at its handle
- **THEN** the light faces its old direction and nothing is written
