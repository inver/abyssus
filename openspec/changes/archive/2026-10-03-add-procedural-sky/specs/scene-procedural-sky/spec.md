# Spec Delta

## Purpose

Lets a scene use a physically based atmosphere, computed per pixel by shaders stored in the sky's asset folder,
as its background instead of a cube of images.

## ADDED Requirements

### Requirement: Procedural sky asset

A folder under the project's `assets` whose `meta.json` has `"type": "SKYBOX_PROCEDURAL"` SHALL be a procedural
sky. Its `additional` SHALL name a `vertex` and a `fragment` file in that folder and MAY set the atmosphere
parameters; a parameter left out SHALL take the Earth-like default.

#### Scenario: Fixture sky is recognized

- **WHEN** the `Untitled` project contains `assets/skybox_physical` with `"type": "SKYBOX_PROCEDURAL"`,
  `"vertex": "sky.vert"` and `"fragment": "sky.frag"`
- **THEN** the asset is a procedural sky and its two files are found in the folder

#### Scenario: Parameters omitted

- **WHEN** `additional` names only `vertex` and `fragment`
- **THEN** the sky is drawn with the default Earth-like atmosphere

### Requirement: Procedural sky background

When a scene has `skyboxEnabled` true and `skyboxName` names a procedural sky, the view SHALL draw its
atmosphere behind everything else, following the camera's orientation but not its position, without building a
cube or any geometry other than a fullscreen triangle. Each pixel SHALL be lit by single scattering of sunlight
through Rayleigh (air) and Mie (haze) layers along that pixel's view direction.

#### Scenario: Daytime sky

- **WHEN** the sun is high and the camera looks above the horizon
- **THEN** the background is blue, brightening and desaturating toward the horizon, with a bright sun disc and
  halo around the sun direction

#### Scenario: Sunset sky

- **WHEN** the sun is at the horizon
- **THEN** the sky near the sun turns orange to red and the opposite sky darkens toward blue

#### Scenario: No seams while orbiting

- **WHEN** the camera orbits through every direction, including straight up and straight down
- **THEN** the sky shows no cube edges, faces or discontinuities, and models and terrain stay in front of it

#### Scenario: Below the horizon

- **WHEN** the camera looks below the horizon
- **THEN** the sky shows the dark ground color of the planet rather than empty space or a repeated sky

### Requirement: Sun direction from the scene

The sun SHALL shine from the opposite of the direction of the scene's brightest directional light. A scene with
no usable directional light SHALL use a default sun about forty-five degrees above the horizon. Changing that
light in the scene file SHALL change the sky when the view refreshes.

#### Scenario: Sun follows the light

- **WHEN** the scene's directional light is rotated to point almost horizontally and the view refreshes
- **THEN** the sun in the sky sits near the horizon and the sky shows sunset colors

#### Scenario: No directional light

- **WHEN** the scene has only point lights, or none
- **THEN** the sky is drawn with the default sun

### Requirement: Procedural sky failures are isolated

A procedural sky whose folder, `meta.json`, named shader files or shader compilation is missing, unreadable or
invalid SHALL be skipped and logged; the view falls back to the clear color and everything else still renders.

#### Scenario: Missing shader file

- **WHEN** `fragment` names a file that is not in the asset folder
- **THEN** no sky is drawn and the rest of the scene renders

#### Scenario: Shader does not compile

- **WHEN** the fragment shader has a syntax error
- **THEN** the error is logged once, no sky is drawn, and the rest of the scene renders
