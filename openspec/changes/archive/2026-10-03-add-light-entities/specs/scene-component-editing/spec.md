# Spec Delta

## ADDED Requirements

### Requirement: Edit a light's range

The plugin SHALL let the user change the `range` of a light component, the distance a point or spot light reaches. A range
equal to the default of `100` SHALL leave no `range` key in the file, and a range that is not a positive number SHALL be
rejected with a message and leave the file unchanged.

#### Scenario: Set a range

- **WHEN** the range of a spot light entity's light is set to `30`
- **THEN** the file holds `range` `30` in its light and every other value of the entity is unchanged

#### Scenario: Back to the default

- **WHEN** a range of `30` is set back to `100`
- **THEN** the light no longer holds a `range` key

#### Scenario: Not a positive number

- **WHEN** the range is set to `0` or to `abc`
- **THEN** the file is unchanged and a message names the field and the problem

#### Scenario: Range follows in the view

- **WHEN** a point light's range is changed while the Scene view is open
- **THEN** the surfaces it lights change to match without reopening the tab
