# Spec Delta

## MODIFIED Requirements

### Requirement: Poses are written to the entities

After each step, every dynamic or kinematic body's position and rotation SHALL be written to its entity's position
component. Scale SHALL NOT change. Static bodies SHALL NOT move. Only root entities carry dynamic or kinematic bodies,
so these writes SHALL be world values, and the children of a moved entity SHALL follow it without being written.

#### Scenario: Static terrain stays

- **WHEN** the `Physics` scene is simulated for 5 seconds
- **THEN** `Terrain`'s position is still `-38.25267, 0, -32.7754`

#### Scenario: A falling body carries its dummy

- **WHEN** `Model 0` of the `Physics` scene has a child dummy and the scene is simulated for 5 seconds
- **THEN** the dummy stays at the same place in `Model 0`'s frame, and its `PositionComponent` is not written

## ADDED Requirements

### Requirement: Child entities in the simulation

A child entity that has a `RigidBodyComponent` SHALL be left out of the simulation with one warning naming the entity.
A child entity with a collider and no rigid body SHALL be a static body at its world pose when no ancestor of it has
a dynamic or kinematic body. When an ancestor has one, the child's collider SHALL be left out with one warning naming
the entity, and the ancestor's body SHALL be simulated without it. Dummies SHALL take no part in the simulation.

#### Scenario: Rigid body on a child

- **WHEN** a child of `Model 0` in a copy of the `Physics` scene has a dynamic rigid body and a box collider
- **THEN** no body is created for it, one warning names it, and `Model 0` is simulated as before

#### Scenario: Static child collider

- **WHEN** a child of `Terrain` with `localPosition` (1, 0, 0) has a box collider and no rigid body
- **THEN** a static box is created at `Terrain`'s position plus (1, 0, 0)
