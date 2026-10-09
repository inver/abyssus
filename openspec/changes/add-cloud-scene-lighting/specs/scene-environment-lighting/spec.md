# Spec Delta

## MODIFIED Requirements

### Requirement: An HDR sky replaces the ambient color

A scene's sky SHALL light the scene when `skyboxEnabled` is true, its environment is built, and `skyboxName` names
either an `SKYBOX_HDR` asset or a `SKYBOX_PROCEDURAL` asset whose `additional.lightsScene` is true. While it lights the
scene it SHALL replace the scene's ambient color for every model and terrain, whether or not the scene's ambient light
is enabled, and the requirements on PBR, default-shader and terrain sky light SHALL apply to it as to an HDR sky. In
every other case the scene SHALL be lit by its ambient color exactly as before this change.

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

#### Scenario: Building in progress

- **WHEN** a procedural sky with `lightsScene` true has just been chosen and its environment is still building
- **THEN** the scene is lit by its ambient color until the environment is ready
