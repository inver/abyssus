# Spec Delta

## ADDED Requirements

### Requirement: A model entity lists its parts

The row of an entity that draws a model SHALL have a `Parts` row after its child entity rows and before its component
rows. Under it the model's parts SHALL be listed, nested as in the model, each named by its node name. A part with a
pivot SHALL show a pivot mark and a part with a hinge a hinge mark. The `Parts` row SHALL read the number of parts as
its value, and SHALL show nothing below it while the model is read or when it cannot be read. Selecting a part row
SHALL select that part of that entity. Part rows SHALL have no eye toggle and SHALL NOT be removable from the tree.

#### Scenario: Parts of the trainer

- **WHEN** the user expands the `Trainer` row of Control Line's `Field.scene` and then its `Parts` row
- **THEN** the row `Trainer` is listed, and under `Trainer` > `Wings` > `LeftWing` the row `LeftFlap` shows the pivot
  and hinge marks after this change's data is authored

#### Scenario: Selecting a part row

- **WHEN** the user selects `flap_L01_Cube.012` under the `Parts` row of `Model 6` in `Untitled`'s `Main Scene`
- **THEN** `Model 6` is selected in the Scene view and that flap is outlined
