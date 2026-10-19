# Spec Delta

## ADDED Requirements

### Requirement: Disabled physics components create nothing

A disabled `RigidBodyComponent` or `ColliderComponent`, or any physics component of a disabled entity, SHALL create no body or shape in the physics world. The values SHALL still be validated and kept in the file.

#### Scenario: Disabled collider
- **WHEN** an entity's collider is disabled
- **THEN** no shape is created and the body, if any, has no collision geometry

#### Scenario: Disabled entity
- **WHEN** an entity with a rigid body and a collider is disabled
- **THEN** the physics world holds no body for it
