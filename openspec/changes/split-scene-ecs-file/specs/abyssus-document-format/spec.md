# Spec Delta

## ADDED Requirements

### Requirement: The scene document holds no entities

A `.scene` SHALL NOT hold an `ecs` member. A scene that has one is unsupported, with no importer or implicit migration.

#### Scenario: Old inline ecs

- **WHEN** a `.scene` with native markers still has an `ecs` member
- **THEN** it is unsupported, opens as text with an explanation, and plugin edits and formatting leave it unchanged

### Requirement: The ECS array file is admitted with its scene

The ECS array file SHALL be validated with the entity rules before binding, enumerating, editing or formatting, and
only together with a supported `.scene`. Unknown extension data, number text, key order and default omission SHALL be
preserved.

#### Scenario: Unsupported scene

- **WHEN** the `.scene` has no supported markers
- **THEN** its ECS file is not read as entities, edited or formatted

#### Scenario: Preserved text

- **WHEN** a plugin edit changes one entity's position
- **THEN** other entities, unknown members and number text in the ECS file are unchanged
