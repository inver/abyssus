# Spec Delta

## ADDED Requirements

### Requirement: Entities load from the ECS file

Entities SHALL be loaded from the scene's ECS array file, in array order, with unmodeled components kept as raw data
so the file writes back unchanged.

#### Scenario: Order and raw components

- **WHEN** the array holds an entity with an unregistered component
- **THEN** the entity loads in array order and the component's payload is written back unchanged
