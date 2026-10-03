# Spec Delta

## Purpose

Keeps entity state consistent when a scene engine is advanced, matching how Mundus derives
rotations, camera orientation and render placement from components.

## ADDED Requirements

### Requirement: Look-at orients an entity

An entity with a position and a look-at target SHALL, on each engine update, have its rotation set
so that it faces the target entity's position. An entity without a look-at target (`-1`) SHALL keep
its rotation.

#### Scenario: Face a target

- **WHEN** an entity at the origin looks at an entity at `(0, 0, -10)` and the engine is updated
- **THEN** its rotation equals the one Mundus' look-at system computes for the same positions

#### Scenario: Target directly above

- **WHEN** the horizontal offset to the target is zero
- **THEN** the yaw is `90` degrees and the rotation contains no NaN

#### Scenario: No target

- **WHEN** an entity's look-at id is `-1`
- **THEN** its rotation is unchanged by the update

### Requirement: Renderables follow their entity

A renderable SHALL be placed by its entity's transform on each update; a point-to-point
renderable SHALL be placed between the positions of its two entities, and left alone when either
entity has no position.

#### Scenario: Render placement

- **WHEN** an entity has position and render components and the engine is updated
- **THEN** its renderable receives the entity's position, rotation and scale as its transform

#### Scenario: Point to point

- **WHEN** a point-to-point entity references two entities that both have positions
- **THEN** its renderable receives those two positions

#### Scenario: Missing endpoint

- **WHEN** one referenced entity has no position component
- **THEN** the update completes without error and the renderable is unchanged

### Requirement: Cameras follow their entity

A camera component SHALL take its position from its entity's position and, when the entity has a
look-at target, face that target.

#### Scenario: Camera with target

- **WHEN** a camera entity looks at another entity and the engine is updated
- **THEN** the camera sits at the entity's position and is directed at the target's position

### Requirement: Render pass draws all renderables

The render system SHALL draw every entity with a render component when render data is set, and
SHALL do nothing when none is set.

#### Scenario: Render data set

- **WHEN** render data is supplied and the engine is updated with two renderable entities
- **THEN** both renderables are drawn once

#### Scenario: No render data

- **WHEN** no render data has been supplied
- **THEN** updating the engine draws nothing and does not fail

### Requirement: Systems run in a fixed order

Within one update the systems SHALL run in the order look-at, render placement, camera
synchronisation, point-to-point placement, render pass, so that a renderable is drawn with this
update's rotation.

#### Scenario: Rotation applied before drawing

- **WHEN** an entity with a look-at target and a renderable is updated once
- **THEN** the renderable is drawn with the rotation computed in that same update
