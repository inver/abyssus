# Spec Delta

## MODIFIED Requirements

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
