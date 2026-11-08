# Spec Delta

## Purpose

Lets the user see the separate parts (nodes with geometry) of a placed model, select one in the tree or in the Scene
view, and see which part is selected, as the basis for editing a part's pivot and hinge.

## ADDED Requirements

### Requirement: A model's parts are the nodes that carry geometry

The parts of a model asset SHALL be the nodes of its model file that draw geometry, together with the nodes on the way
from the root to them, in the model's node order and nested as in the model. A part SHALL be named by its node name. A
node without geometry and without such descendants SHALL NOT be a part. When an entity draws only one mesh of the model
(`meshId`), its parts SHALL be the nodes that draw that mesh and their ancestors.

#### Scenario: Trainer parts

- **WHEN** the parts of Control Line's `model_trainer` are listed
- **THEN** they are `Trainer` with children `Fuselage`, `VTail`, `Hub`, `Propeller`, `Wings`, `Tail`, `NoseWheel`,
  `LeftWheel`, `RightWheel`; `Wings` has `LeftWing` and `RightWing`; `LeftWing` has `LeftFlap` and `LeftAileron`

#### Scenario: A model with one flat level

- **WHEN** the parts of `Untitled`'s `model_900f6f61-6384-434a-be81-56ce303fbb56` are listed
- **THEN** there is one root part `piper_pa18.obj` with 192 children, among them `flap_L01_Cube.012`

### Requirement: Selecting a part

The user SHALL be able to select one part of one model entity: from its row in the tree, or in the Scene view with
Alt+click, which selects the nearest part of a model entity whose triangles are under the cursor (not its bounding
box). A part selection SHALL also select its entity. A plain click SHALL select entities as before and clear the part
selection. Alt+click on nothing SHALL clear the part selection and keep the entity selection. Disabled entities SHALL
NOT be hit, and Alt+click SHALL NOT write any file.

#### Scenario: Alt+click on a flap

- **WHEN** the user Alt+clicks the left flap of `Model 6` in `Untitled`'s `Main Scene`
- **THEN** `Model 6` is selected, its part `flap_L01_Cube.012` is selected and its row is revealed in the tree

#### Scenario: Through the gap of a bounding box

- **WHEN** the user Alt+clicks inside `Model 6`'s bounding box but beside its geometry, with `Terrain` behind it
- **THEN** no part is selected and the part selection is cleared

#### Scenario: Plain click clears the part

- **WHEN** a part of `Model 6` is selected and the user clicks `Model 0` without Alt
- **THEN** `Model 0` is selected and no part is selected

### Requirement: The selected part is outlined

The Scene view SHALL outline the selected part with a box around that part's own geometry, posed with the part, in a
color different from the entity highlight, while the entity highlight stays drawn. The outline SHALL follow edits to
the entity, the part's pivot and the hinge preview. Without a part selection no part outline SHALL be drawn.

#### Scenario: Outline follows the entity

- **WHEN** `flap_L01_Cube.012` of `Model 6` is selected and the user moves `Model 6` with the gizmo
- **THEN** the part outline moves with the flap

#### Scenario: Selecting the entity row

- **WHEN** the user selects the `Model 6` row in the tree
- **THEN** only the entity highlight is drawn
