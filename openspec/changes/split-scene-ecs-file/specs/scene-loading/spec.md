# Spec Delta

## ADDED Requirements

### Requirement: A scene loads from its scene file and its ECS file

Loading a supported scene SHALL read its environment from the `.scene` and its entities from the sibling ECS array
file, with the same result for files and for unsaved editor text of either document.

#### Scenario: Unsaved entity text

- **WHEN** the editor buffer of `Field.ecs.json` holds an unsaved edit
- **THEN** the loaded entities reflect the buffer and the file on disk is not read or written

#### Scenario: Unsaved scene text

- **WHEN** the buffer of `Field.scene` changes fog and `Field.ecs.json` is unchanged
- **THEN** the loaded scene has the new fog and the same entities

#### Scenario: Independent loads

- **WHEN** two scenes load at the same time
- **THEN** each reads only its own pair and shares no state
