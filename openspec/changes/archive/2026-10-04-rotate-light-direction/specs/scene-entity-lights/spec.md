## ADDED Requirements

### Requirement: A look-at light faces its target

A directional or spot light whose `PositionComponent.lookAtId` names an existing entity SHALL face that entity, from
its own position toward the target's position, in its shading, its spot cone and its direction line. A light with no
`lookAtId`, or one that names no entity, SHALL face along its rotation as before. A light at the same position as its
target SHALL face along its rotation.

#### Scenario: A Mundus directional light faces its handle

- **WHEN** `Lights/scenes/Mundus Lights.scene` is shown, where directional light `1` at (0, 10, 0) has `lookAtId` 0
  and handle `0` is at (0, 0, 0)
- **THEN** the light shines straight down, along (0, -1, 0), and its direction line points down

#### Scenario: A Mundus spot light faces its handle

- **WHEN** the same scene is shown, where spot light `4` at (0, 5, 0) has `lookAtId` 3 and handle `3` is at (0, 0, 0)
- **THEN** the spot light's cone points straight down at the origin

#### Scenario: A light without a look-at target

- **WHEN** a directional light has no `lookAtId` and a `localRotation` turned a quarter turn about Y
- **THEN** it shines along its rotated -Z, as before

#### Scenario: A look-at target that is missing

- **WHEN** a directional light's `lookAtId` names an entity the scene does not have
- **THEN** it shines along its rotated -Z
