# Spec Delta

## MODIFIED Requirements

### Requirement: A look-at light faces its target

A directional or spot light whose `PositionComponent.lookAtId` names an existing entity SHALL face that entity, from
its own world position toward the target's world position, in its shading, its spot cone and its direction line. A
light with no `lookAtId`, or one that names no entity, SHALL face along its rotation as before. A light at the same
position as its target SHALL face along its rotation.

#### Scenario: A Mundus directional light faces its handle

- **WHEN** `Lights/scenes/Abyssus Lights.scene` is shown, where directional light `1` at (0, 10, 0) has `lookAtId` 0
  and its child handle `0` has `localPosition` (0, -10, 0), so it is at (0, 0, 0)
- **THEN** the light shines straight down, along (0, -1, 0), and its direction line points down

#### Scenario: A Mundus spot light faces its handle

- **WHEN** the same scene is shown, where spot light `4` at (0, 5, 0) has `lookAtId` 3 and its child handle `3` has
  `localPosition` (0, -5, 0), so it is at (0, 0, 0)
- **THEN** the spot light's cone points straight down at the origin

#### Scenario: A light without a look-at target

- **WHEN** a directional light has no `lookAtId` and a `localRotation` turned a quarter turn about Y
- **THEN** it shines along its rotated -Z, as before

#### Scenario: A look-at target that is missing

- **WHEN** a directional light's `lookAtId` names an entity the scene does not have
- **THEN** it shines along its rotated -Z

## ADDED Requirements

### Requirement: A light carries its handle

Moving a light or camera whose direction handle is its child SHALL move the handle with it, so it keeps its direction.
Only the light's or camera's own `PositionComponent` (and, for a camera, `CameraComponent.camera.position`) SHALL be
written.

#### Scenario: Move a spot light

- **WHEN** in `Lights/scenes/Abyssus Lights.scene` the user moves spot light `4` along X by 3 and releases
- **THEN** only light `4`'s `localPosition.x` changes, its handle `3` is now drawn at (3, 0, 0), and the cone still
  points straight down
