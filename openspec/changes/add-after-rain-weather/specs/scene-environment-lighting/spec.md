# Spec Delta

## MODIFIED Requirements

### Requirement: Default-shader models and terrain get diffuse sky light

A model drawn with the default shader SHALL take as its ambient the sky's irradiance in the six world axis
directions (`+X`, `-X`, `+Y`, `-Y`, `+Z`, `-Z`), blended by its surface normal as the default shader's ambient
cubemap already blends. A terrain SHALL take its ambient light from the irradiance cube in the direction of its
surface normal. Neither SHALL show sky reflections with Default weather. After the rain SHALL additionally allow wet ground and puddle reflections without changing the authored sky or the existing diffuse-lighting policy.

#### Scenario: Default-shader model

- **WHEN** `Model 0` (default shader) is lit by a sky that is bright above and dark below
- **THEN** its upward faces are brighter than its downward faces

#### Scenario: Terrain

- **WHEN** the terrain of a scene is lit by a sky whose pixels are all (0.0, 0.0, 1.0) and no light entity
- **THEN** the terrain's texture is tinted blue and shows no grey from the scene's ambient color

#### Scenario: Wet ground exception
- **WHEN** After the rain is enabled on eligible default-shader ground models and terrain
- **THEN** puddle regions reflect the displayed sky while non-receiver models keep their existing lighting

