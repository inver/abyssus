# Spec Delta

## ADDED Requirements

### Requirement: Part properties

When a part of a model entity is selected, Properties SHALL show the entity's breadcrumb followed by the part's node
name, and a part section with: the model asset's name, the pivot (position, rotation and Reset, or the reason a pivot
cannot be applied), and the hinge (Add Hinge, or `min`, `max`, Preview and Remove Hinge). The section SHALL follow
changes of the model's `meta.json`, including Undo and unsaved text, and edits SHALL go to that `meta.json`, not to the
scene.

#### Scenario: A flap without data

- **WHEN** the user selects `flap_L01_Cube.012` of `Model 6` in `Untitled`'s `Main Scene`
- **THEN** Properties shows `Main Scene` > `Model 6` > `flap_L01_Cube.012`, the asset
  `model_900f6f61-6384-434a-be81-56ce303fbb56`, a pivot at 0 with no rotation, and Add Hinge

#### Scenario: Undo is followed

- **WHEN** the user adds a hinge to `LeftFlap` of the trainer and then presses Undo
- **THEN** the part section shows Add Hinge again
