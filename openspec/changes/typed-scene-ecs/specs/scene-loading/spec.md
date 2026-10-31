## ADDED Requirements

### Requirement: A scene loads as typed entities

Loading a supported scene SHALL read its environment and its entities in one step, with the same result for a scene file
and for unsaved editor text. A scene without `ecs` loads with no entities.

#### Scenario: Scene without ecs

- **WHEN** a supported scene has no `ecs` member
- **THEN** it loads with an empty engine and its environment values

#### Scenario: Unsaved text

- **WHEN** the editor buffer of `Main Scene` holds an edit that is not saved
- **THEN** the loaded entities reflect the buffer, and the file on disk is not read or written
