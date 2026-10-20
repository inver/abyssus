# Spec Delta

## ADDED Requirements

### Requirement: Disabled entities and components load but do not act

A disabled entity SHALL load with its components and contribute nothing to the scene. A disabled component SHALL load as data without its behavior, while the entity's other components stay active. The flag SHALL round-trip: a scene loaded and written by the engine SHALL keep `enabled: false` where it was.

#### Scenario: Disabled render component
- **WHEN** an entity has `RenderComponent` with `enabled: false` and an active `PositionComponent`
- **THEN** the entity exists and is positioned, and nothing is rendered for it

#### Scenario: Disabled entity
- **WHEN** an entity has `enabled: false`
- **THEN** none of its components renders, lights, collides or moves

#### Scenario: Round trip
- **WHEN** a scene with a disabled component is loaded and written back
- **THEN** the component keeps `enabled: false`
