# Spec Delta

## Purpose

Lets users see a scene lit by the light entities it defines, in addition to its ambient light, so
the view matches how the scene is lit in the game.

## ADDED Requirements

### Requirement: Light entities light the view

The scene view SHALL light the displayed models and terrain with every light entity of the scene
(directional, point and spot lights), using each light's color, intensity and, where it applies,
position, direction and range, in addition to the scene's ambient light.

#### Scenario: Directional light

- **WHEN** a scene has a directional light entity pointing down at an angle
- **THEN** displayed surfaces are shaded according to that direction

#### Scenario: Point light

- **WHEN** a scene has a point light entity at a position with a range
- **THEN** surfaces near that position are lit and surfaces beyond its range are not

#### Scenario: Scene without light entities

- **WHEN** a scene has no light entities
- **THEN** the view is lit by the ambient light alone, as before

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
