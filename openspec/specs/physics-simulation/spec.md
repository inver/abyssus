# physics-simulation Specification

## Purpose

Simulates a scene's physics components as rigid bodies, colliders and constraints, in a game or the play host, with
results written back to the entities' positions.

## Requirements

### Requirement: Bodies follow the physics components

Starting a simulation of a scene SHALL create one body per entity with a collider: dynamic, kinematic or static as its
rigid body says, and static when it has no rigid body. A rigid body without a collider SHALL be left out with one
warning naming the entity. Gravity SHALL be `9.81` m/s² downward along Y.

#### Scenario: A box falls onto the terrain

- **WHEN** the `Physics` test project's scene (a copy of `Untitled` where `Model 0` has a dynamic box and `Terrain` a
  height-field collider) is simulated for 5 seconds
- **THEN** `Model 0` has moved down from Y `3.086434` and rests on the terrain: its box's lowest point is within
  `0.05` of the terrain height beneath it, and its speed is below `0.01` m/s

#### Scenario: Rigid body without collider

- **WHEN** an entity has a rigid body but no collider
- **THEN** it is left out of the simulation and one warning names the entity

### Requirement: Poses are written to the entities

After each step, every dynamic or kinematic body's position and rotation SHALL be written to its entity's position
component. Scale SHALL NOT change. Static bodies SHALL NOT move.

#### Scenario: Static terrain stays

- **WHEN** the `Physics` scene is simulated for 5 seconds
- **THEN** `Terrain`'s position is still `-38.25267, 0, -32.7754`

### Requirement: The simulation advances in fixed steps

The simulation SHALL advance in fixed steps of 1/120 s, running as many steps as the elapsed time needs, up to 8 per
call, and carrying the remainder to the next call. Two runs of the same scene with the same inputs and the same calls
SHALL produce identical poses on the same machine.

#### Scenario: Same run twice

- **WHEN** the `Physics` scene is simulated twice for 2 seconds in calls of 1/60 s
- **THEN** both runs end with identical positions and rotations for every entity

### Requirement: Ropes pull and go slack

A `DISTANCE` constraint with minimum `0` SHALL act as a rope: it SHALL stop the bodies from moving further apart than
its maximum and SHALL NOT push them together. Its tension in newtons SHALL be readable after each step, and SHALL be
`0` while the rope is slack.

#### Scenario: Hanging weight

- **WHEN** a 1 kg dynamic sphere hangs at rest on a 2 m rope from a fixed point in the world
- **THEN** the rope's tension reads between `9.32` and `10.30` N (9.81 N within 5%)

#### Scenario: Slack rope

- **WHEN** the sphere is pushed up so it is closer than 2 m to the fixed point
- **THEN** the rope's tension reads `0` until the sphere is 2 m away again

### Requirement: Games act on the simulation

A game SHALL be able, between steps, to apply forces and torques to a body, move a kinematic body, create and remove
constraints between bodies, read each body's velocity, and learn which bodies touched during the last step and at what
relative speed. A body removed from the scene SHALL leave the simulation with its constraints.

#### Scenario: Thrust

- **WHEN** a game applies a constant 20 N force along +X to a 1 kg dynamic body with no gravity for 1 second
- **THEN** the body's speed along X is `20` m/s within 1%

#### Scenario: Touching the terrain

- **WHEN** `Model 0` of the `Physics` scene first lands on the terrain
- **THEN** that step reports a contact between `Model 0` and `Terrain` with a relative speed above `0`

### Requirement: Bad input is refused before it reaches the engine

A value that is not finite, a shape with a size not greater than `0`, a convex hull from a model with fewer than 4
distinct points, and a height field from a terrain without height data SHALL be refused with one warning naming the
entity. The rest of the scene SHALL be simulated, and the program SHALL keep running.

#### Scenario: Flat model as a hull

- **WHEN** an entity asks for a convex hull from a model whose points all lie in one plane
- **THEN** the entity is left out with one warning, and the other bodies simulate normally

### Requirement: Simulations release their resources

Closing a simulation SHALL release every body, shape and constraint it created. Using a closed simulation SHALL fail
with an error saying it is closed. Physics SHALL work in a program that runs no IDE.

#### Scenario: Many simulations in a row

- **WHEN** a test with no IDE running creates, steps and closes the `Physics` simulation 100 times
- **THEN** every run gives the same result as the first, and stepping a closed simulation fails with an error saying
  it is closed
