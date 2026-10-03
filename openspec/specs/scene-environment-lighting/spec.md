# scene-environment-lighting Specification

## Purpose
Lets users see a scene lit by its HDR sky - diffuse light from the sky around each surface and, on PBR materials,
reflections of the sky - so a captured environment can be judged as a light source and not only as a background.

## Requirements

### Requirement: An HDR sky replaces the ambient color

A scene's HDR sky SHALL light the scene when `skyboxEnabled` is true, `skyboxName` names an `SKYBOX_HDR` asset, and
that asset's environment is built. While it lights the scene it SHALL replace the scene's ambient color for every
model and terrain, whether or not the scene's ambient light is enabled. In every other case - no skybox, a disabled
skybox, a `SKYBOX` or `SKYBOX_PROCEDURAL` asset, an HDR sky still building or failed - the scene SHALL be lit by its
ambient color exactly as before this change.

#### Scenario: Sky replaces the ambient color

- **WHEN** a scene with ambient color (0.3, 0.3, 0.3) and no light entities names an HDR sky whose pixels are all
  (1.0, 0.0, 0.0), and the sky is built
- **THEN** a white default-shader model is drawn red, with no grey from the ambient color

#### Scenario: Disabled skybox does not light

- **WHEN** the scene names an HDR sky but `skyboxEnabled` is false
- **THEN** no sky is drawn and the scene is lit by its ambient color

#### Scenario: Non-HDR skies do not light

- **WHEN** the scene names `skybox_default` or `skybox_physical`
- **THEN** the scene is lit by its ambient color, as before this change

#### Scenario: Ambient light disabled

- **WHEN** the scene's ambient light is disabled and it names a built HDR sky
- **THEN** models and terrain are still lit by the sky

### Requirement: PBR models are lit by the sky

A model drawn with the PBR shader SHALL take its ambient diffuse light from the irradiance cube in the direction of
its surface normal, and its ambient specular light from the specular cube in the direction of the view reflected
about the normal, at the mip level for its roughness, combined with the shader's existing environment BRDF
approximation and occlusion. A model drawn with the PBR shader while no HDR sky lights the scene SHALL use a shader
compiled without the sky path, identical to today's.

#### Scenario: Diffuse follows the sky

- **WHEN** a rough white PBR sphere is lit by a sky that is bright above the horizon and dark below it
- **THEN** the sphere is bright on top and dark underneath

#### Scenario: Smooth metal reflects the sky

- **WHEN** a PBR sphere with metallic 1 and roughness 0 is lit by a sky with a small bright patch
- **THEN** a sharp reflection of the patch appears on the sphere, and with roughness 1 it spreads into a broad
  soft highlight

#### Scenario: No sky, no change

- **WHEN** `Main Scene`, which names no skybox, is drawn
- **THEN** `Model 6` (PBR) is drawn exactly as before this change

### Requirement: Default-shader models and terrain get diffuse sky light

A model drawn with the default shader SHALL take as its ambient the sky's irradiance in the six world axis
directions (`+X`, `-X`, `+Y`, `-Y`, `+Z`, `-Z`), blended by its surface normal as the default shader's ambient
cubemap already blends. A terrain SHALL take its ambient light from the irradiance cube in the direction of its
surface normal. Neither SHALL show sky reflections.

#### Scenario: Default-shader model

- **WHEN** `Model 0` (default shader) is lit by a sky that is bright above and dark below
- **THEN** its upward faces are brighter than its downward faces

#### Scenario: Terrain

- **WHEN** the terrain of a scene is lit by a sky whose pixels are all (0.0, 0.0, 1.0) and no light entity
- **THEN** the terrain's texture is tinted blue and shows no grey from the scene's ambient color
