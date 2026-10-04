# scene-entity-lights Specification

## Purpose

Lets users see a scene lit by the light entities it defines, in addition to its ambient light, so
the view matches how the scene is lit in the game.

## Requirements

### Requirement: Light entities light the view

The scene view SHALL light the displayed models and terrain with every light entity of the scene
(directional, point and spot lights), using each light's color, intensity and, where it applies,
position, direction and range, in addition to the scene's ambient light - or, while an HDR sky lights the scene as
described by the `scene-environment-lighting` capability, in addition to the sky's light.

#### Scenario: Directional light

- **WHEN** a scene has a directional light entity pointing down at an angle
- **THEN** displayed surfaces are shaded according to that direction

#### Scenario: Point light

- **WHEN** a scene has a point light entity at a position with a range
- **THEN** surfaces near that position are lit and surfaces beyond its range are not

#### Scenario: Scene without light entities

- **WHEN** a scene has no light entities
- **THEN** the view is lit by the ambient light alone, as before, or by the sky alone when an HDR sky lights it

#### Scenario: Light entities add to the sky

- **WHEN** a scene lit by an HDR sky also has a directional light entity
- **THEN** surfaces facing the light are brighter than with the sky alone

#### Scenario: Spotlight cone
- **WHEN** a spotlight points at displayed models and terrain
- **THEN** it illuminates surfaces within its cone and range and does not illuminate surfaces outside the cone

#### Scenario: Soft cone edge
- **WHEN** a spotlight has nonzero edge softness
- **THEN** its contribution fades smoothly from full intensity to zero toward the outer cone boundary

### Requirement: Light limits and failures

The view SHALL ignore light entities it cannot read and SHALL cap the number of lights of each
kind it applies at the renderer's supported maximum, preferring the lights nearest the camera
target, without failing.

#### Scenario: Too many lights

- **WHEN** a scene defines more lights of one kind than the renderer supports
- **THEN** the supported number is applied and the view still renders

#### Scenario: Unreadable light

- **WHEN** a light entity has a missing or malformed color or position
- **THEN** that light is skipped and the others still apply

### Requirement: Spotlight beam parameters

Spotlights SHALL use their saved full cone angle and edge softness. Cone angle SHALL be measured in degrees, greater than 0 and less than 180. Edge softness SHALL range from 0 to 100 percent of the cone's angular radius; zero means a sharp edge. Missing values SHALL use documented defaults without modifying the scene merely by opening it.

#### Scenario: Wider cone
- **WHEN** a spotlight's cone angle increases from 30 to 60 degrees
- **THEN** it illuminates a wider area and its shadow coverage follows that area

#### Scenario: Increased softness
- **WHEN** edge softness increases at a fixed cone angle
- **THEN** the fade region widens inward while the outer cone boundary remains unchanged

#### Scenario: Legacy scene
- **WHEN** a spotlight has no saved cone angle or softness
- **THEN** it renders with the documented defaults and opening the view does not modify the file

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
