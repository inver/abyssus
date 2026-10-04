# Spec Delta

## Purpose

Lets a game put a moving body back at a pose, at rest, so a run can restart inside a world it does not build (Play in
Abyssus restarting a control-line flight).

## MODIFIED Requirements

### Requirement: Games act on the simulation

A game SHALL be able, between steps, to apply forces and torques to a body, move a kinematic body, put a dynamic or
kinematic body at a pose at once and at rest, create and remove constraints between bodies, read each body's velocity,
and learn which bodies touched during the last step and at what relative speed. A body removed from the scene SHALL
leave the simulation with its constraints.

#### Scenario: Thrust

- **WHEN** a game applies a constant 20 N force along +X to a 1 kg dynamic body with no gravity for 1 second
- **THEN** the body's speed along X is `20` m/s within 1%

#### Scenario: Touching the terrain

- **WHEN** `Model 0` of the `Physics` scene first lands on the terrain
- **THEN** that step reports a contact between `Model 0` and `Terrain` with a relative speed above `0`

#### Scenario: Back at rest

- **WHEN** a game puts a moving dynamic body at a new position and rotation
- **THEN** its entity's pose is that position and rotation at once, and after the next step the body is still there
  with no velocity
