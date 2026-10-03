# Spec Delta

## ADDED Requirements

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
