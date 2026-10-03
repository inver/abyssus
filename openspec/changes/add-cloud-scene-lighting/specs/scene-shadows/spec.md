# Spec Delta

## MODIFIED Requirements

### Requirement: Scene geometry casts and receives shadows

Displayed opaque and alpha-tested models and terrain SHALL cast and receive shadows from supported directional, point and spot lights selected within the shadow budget. Editor grids, sky backgrounds, camera and light markers, selection highlights and gizmos SHALL NOT cast or receive shadows. The only exception is the clouds of a procedural sky, which SHALL cast cloud shadows as defined in "Clouds cast shadows from the sun".

#### Scenario: Model shadows terrain
- **WHEN** a model stands between a shadowed light and a terrain surface
- **THEN** the model casts a shadow on the terrain

#### Scenario: Model shadows another model
- **WHEN** one model blocks a shadowed light from another model
- **THEN** the receiving model shows the shadow

#### Scenario: Terrain occludes a light
- **WHEN** a terrain ridge blocks a shadowed light from a model or another terrain surface
- **THEN** the blocked surface shows the shadow

#### Scenario: Point light coverage
- **WHEN** objects surround a shadowed point light on different sides
- **THEN** objects can cast shadows in every direction around the light

#### Scenario: Spotlight coverage
- **WHEN** an object blocks a shadowed spotlight within its cone
- **THEN** surfaces behind the object within the cone show a shadow

## ADDED Requirements

### Requirement: Clouds cast shadows from the sun

While the scene's procedural sky draws enabled clouds, terrain and models SHALL be shaded by the clouds between them
and the sun, the brightest directional light. The shade SHALL drift with each band's wind, match where the sky shows
clouds, attenuate only the sun light, and combine with that light's own scene shadow. Within the cloud shadow area, it
SHALL replace the uniform sun dimming of `scene-entity-lights`; beyond it, that dimming SHALL apply.

#### Scenario: Moving cloud shadows

- **WHEN** a copy of `Main Scene` uses `skybox_physical` with `builtin:fair` clouds and the view stays open for ten
  seconds
- **THEN** dark patches move across the terrain along the low band's wind direction

#### Scenario: Only the sun is shaded

- **WHEN** a cloud shadow covers the part of the terrain that `Spot Light 8` lights
- **THEN** entity `7`'s (the sun's) contribution is reduced there and the spot light's is unchanged

#### Scenario: Combined with a model shadow

- **WHEN** `Model 0` already shadows terrain from the sun and a cloud shadow passes over the same spot
- **THEN** the spot is no darker than the sun's absence allows; ambient, sky and other lights still light it

#### Scenario: No double dimming

- **WHEN** the orbit target lies inside the cloud shadow area under a cloud
- **THEN** surfaces there are dimmed by the cloud shadow only, not also by the uniform sun dimming

#### Scenario: Clouds disabled

- **WHEN** the sky's clouds are disabled or the scene has no procedural sky
- **THEN** no cloud shadows are drawn and shadows are as before this change
