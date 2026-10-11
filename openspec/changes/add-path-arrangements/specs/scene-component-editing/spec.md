# Spec Delta

## MODIFIED Requirements

### Requirement: Update a component field

The plugin SHALL change one field of a modeled or schema-declared component of an entity and write only that change
into the file. A value that does not fit the field, including one outside a schema field's declared limits or not
among its choices, SHALL be rejected with a message and leave the file unchanged. Two edits SHALL change more
in the same undoable edit: a field of a path arrangement also regenerates its copies, and a `PositionComponent` field
of a copy placed by a path arrangement also detaches that copy, as `scene-path-arrangements` describes.

#### Scenario: Change a camera field

- **WHEN** the `fieldOfView` of an entity's camera is set to `60`
- **THEN** the file holds `60` for it and every other value of the entity is unchanged

#### Scenario: Not a number

- **WHEN** a numeric field is set to `abc`
- **THEN** the file is unchanged and a message names the field and the problem

#### Scenario: Value equals the default

- **WHEN** a position's `localScale.y` is set back to `1`
- **THEN** the file follows the Abyssus version 1 default omission rules, leaving no field that only repeats a default

#### Scenario: No change

- **WHEN** a field is set to the value it already has
- **THEN** the file is not modified

#### Scenario: Change a schema-declared field

- **WHEN** entity `0`'s `PlaneComponent` `lineLength` in the `Custom` scene is set to `25`
- **THEN** the file holds `25` for it and the plane's other fields are unchanged

#### Scenario: Outside a declared limit

- **WHEN** `lineLength`, declared with a minimum of `5`, is set to `2`
- **THEN** the file is unchanged and a message names the field and its minimum

#### Scenario: Change an arrangement's spacing

- **WHEN** the `spacing` of path arrangement `Path 11` is set to `5`
- **THEN** in one undoable edit the file holds `5` for it and the arrangement's copies and `slots` follow the new spacing

#### Scenario: Edit an arranged copy's position

- **WHEN** the `localPosition.x` of copy `12` of `Path 11` is set to `1`
- **THEN** in one undoable edit the file holds `1` for it, copy `12` has no `ParentComponent`, and its slot in `Path 11`
  is `-1`
