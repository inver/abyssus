# Spec Delta

## MODIFIED Requirements

### Requirement: Renderables follow their entity

A renderable SHALL be placed by its entity's world transform on each update, which for a child is its parent's world
transform (or its parent's model node) combined with its own local transform; a point-to-point
renderable SHALL be placed between the world positions of its two entities, and left alone when either
entity has no position.

#### Scenario: Render placement

- **WHEN** an entity has position and render components and the engine is updated
- **THEN** its renderable receives the entity's position, rotation and scale as its transform

#### Scenario: A child's renderable

- **WHEN** an entity with a render component and `localPosition` (1, 0, 0) is the child of an entity at (0, 5, 0) and
  the engine is updated
- **THEN** its renderable is placed at (1, 5, 0)

#### Scenario: Point to point

- **WHEN** a point-to-point entity references two entities that both have positions
- **THEN** its renderable receives those two positions

#### Scenario: Missing endpoint

- **WHEN** one referenced entity has no position component
- **THEN** the update completes without error and the renderable is unchanged

## ADDED Requirements

### Requirement: World poses are resolved before the systems that use them

Each engine update SHALL resolve every entity's world pose from its parents, after game logic and physics have changed
poses and before look-at, renderable, camera and render work, so that a child moved by its parent in this update is drawn at
its new place in the same frame. Look-at SHALL aim at the target's world position.

#### Scenario: A child follows in the same frame

- **WHEN** a game moves a parent by 1 along X and then updates the engine
- **THEN** the child's renderable is placed 1 further along X in that same update
