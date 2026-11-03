# Spec Delta

## ADDED Requirements

### Requirement: Headless editing works on a scene pair

The editing library SHALL validate and edit a scene given the text of its `.scene` and its ECS file, without the IDE,
and report which of the two texts changed.

#### Scenario: Entity edit

- **WHEN** a library caller moves an entity given both texts
- **THEN** only the ECS text changes and the `.scene` text is returned unchanged

#### Scenario: Missing ECS text

- **WHEN** a library caller adds the first entity given only a `.scene` text
- **THEN** the result contains a new ECS text holding that entity
