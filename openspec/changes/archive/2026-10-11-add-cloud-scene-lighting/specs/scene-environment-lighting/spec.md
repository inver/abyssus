# Spec Delta

## MODIFIED Requirements

### Requirement: An HDR sky replaces the ambient color

A scene's sky SHALL light the scene when `skyboxEnabled` is true, its environment is built, and `skyboxName` names
either an `SKYBOX_HDR` asset or a `SKYBOX_PROCEDURAL` asset whose `additional.lightsScene` is the boolean `true`. While it lights the
scene it SHALL replace the scene's ambient color for every model and terrain, whether or not the scene's ambient light
is enabled, and the requirements on PBR, default-shader and terrain sky light SHALL apply to it as to an HDR sky. In
every other case the scene SHALL retain its existing ambient-light behavior, including disabled ambient light.

#### Scenario: Sky replaces the ambient color

- **WHEN** a scene with ambient color (0.3, 0.3, 0.3) and no light entities names an HDR sky whose pixels are all
  (1.0, 0.0, 0.0), and the sky is built
- **THEN** a white default-shader model is drawn red, with no grey from the ambient color

#### Scenario: Disabled skybox does not light

- **WHEN** the scene names an HDR sky but `skyboxEnabled` is false
- **THEN** no sky is drawn and the scene is lit by its ambient color

#### Scenario: Non-HDR skies do not light

- **WHEN** the scene names `skybox_default`, or `skybox_physical`, which has no `lightsScene`
- **THEN** the scene is lit by its ambient color, as before this change

#### Scenario: Ambient light disabled

- **WHEN** the scene's ambient light is disabled and it names a built HDR sky
- **THEN** models and terrain are still lit by the sky

#### Scenario: Procedural sky lights the scene

- **WHEN** a copy of `skybox_physical` gets `"lightsScene": true` and the sun is high, with no clouds
- **THEN** upward faces of `Model 0` take a blue tint from the sky and downward faces are darker, with no grey from the
  scene's ambient color

#### Scenario: Overcast light

- **WHEN** the same sky names a cloud asset made from the overcast template
- **THEN** the sky light on models and terrain is greyer and more even from all upper directions than with no clouds

#### Scenario: Changes fade in

- **WHEN** clouds drift over the sun, or the sun light is rotated
- **THEN** the sky light changes gradually over about one second, without a visible jump

#### Scenario: Invalid opt-in does not light

- **WHEN** `additional.lightsScene` is absent, false, or a value other than the boolean `true`
- **THEN** a procedural sky does not replace ambient lighting

#### Scenario: Cloud asset reload

- **WHEN** the referenced cloud asset is reloaded with different valid coverage or bands, including bands without wind
- **THEN** the sky lighting refreshes to that weather without requiring the scene view to reopen

#### Scenario: Lighting build failure

- **WHEN** the first procedural lighting build fails
- **THEN** the scene retains ambient lighting and remains interactive without a modal error

#### Scenario: Refresh failure

- **WHEN** a later procedural lighting refresh fails after an environment has completed
- **THEN** the last completed sky lighting remains in use and the scene continues rendering

#### Scenario: Building in progress

- **WHEN** a procedural sky with `lightsScene` true has just been chosen and its environment is still building
- **THEN** the scene is lit by its ambient color until the environment is ready

### Requirement: PBR models are lit by the sky

A model drawn with the PBR shader SHALL take its ambient diffuse light from the irradiance cube in the direction of
its surface normal, and its ambient specular light from the specular cube in the direction of the view reflected
about the normal, at the mip level for its roughness, combined with the shader's existing environment BRDF
approximation and occlusion. A model drawn with the PBR shader while no eligible sky lights the scene SHALL use a shader
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
