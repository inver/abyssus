# Spec Delta

## Purpose

Defines how Control Line's raster game lights and shows its field physically: in linear light, with sky light on every
surface and a correct display transform, and with the control lines drawn as real cables.

## ADDED Requirements

### Requirement: Lit in linear light, shown through the output transform

The game SHALL compute lighting in linear space and SHALL write every drawn surface (sky, terrain, models, foliage, control
lines) through the shared output transform once. The scene's sun and ambient values SHALL keep their meaning as multipliers
of colour; the game's exposure SHALL be a game setting with a documented default under which the bundled field reads as a
bright midday scene without clipped sun-lit surfaces.

#### Scenario: No clipped midday surfaces

- **WHEN** the bundled field is drawn from the pilot camera at midday
- **THEN** sun-lit asphalt, dirt and roofs show distinct values (no more than 1% of their pixels at pure white) and shadowed parts of them stay above pure black

#### Scenario: Sky and ground agree

- **WHEN** the horizon is drawn with the fog on
- **THEN** the distant terrain fades to the sky's horizon colour without a visible seam

### Requirement: Models are lit by the sky

When the field's sky opts in to lighting the scene, the game SHALL light PBR models with the sky's diffuse irradiance and
prefiltered specular reflection instead of the flat ambient colour, and SHALL add the sun's direct light on top with its
shadow. A model with a metallic material SHALL show the sky in its reflection, rougher surfaces a blurrier one. When the
sky does not opt in or its lighting is not ready, models SHALL fall back to the flat ambient colour without a visible pop
larger than the first fade.

#### Scenario: Metal reflects the sky

- **WHEN** a plane with a metallic, smooth material is drawn in the field
- **THEN** its surface shows the sky colour in the direction of the reflection vector and differs from the same plane under the flat ambient

#### Scenario: Roughness blurs reflection

- **WHEN** a smooth and a rough surface of the same metal are drawn side by side
- **THEN** the rough one's reflection is lower in contrast than the smooth one's

#### Scenario: Sky without lighting

- **WHEN** the field's sky does not opt in to lighting the scene
- **THEN** models are drawn with the flat ambient colour and the game does not fail

### Requirement: Terrain and foliage share the lighting

Terrain and foliage SHALL use the same sun, the same shadow, the same sky ambient and the same fog and output transform as
the models. A terrain and a model lit the same way SHALL come out within 5% of the same linear brightness.

#### Scenario: Matching surfaces

- **WHEN** a grey diffuse model and a terrain of the same albedo face the sun at the same angle, in the same shadow state
- **THEN** their lit linear brightness is within 5% of each other

#### Scenario: Foliage in shadow

- **WHEN** a tree casts its shadow on the terrain and grass lies in it
- **THEN** the grass inside the shadow is darker than the grass outside it

### Requirement: Control lines are lit cable

The two control lines SHALL be drawn as thin lit cables from the handle to each leadout, in a polyline of two or more points,
with a thickness that follows the plane's `lineDiameter` scaled up so that the cable is always at least one pixel wide on
screen. They SHALL be lit and shadowed by the sun and sky, SHALL depth-test against the world, and SHALL NOT be tone mapped
or encoded twice. Drawing them SHALL NOT change the simulation, the tension or scoring.

#### Scenario: Cable follows the lines

- **WHEN** a flight is in progress
- **THEN** each cable runs from its handle point to the plane's matching leadout every frame

#### Scenario: Hidden behind terrain

- **WHEN** a hill lies between the camera and part of a cable
- **THEN** that part is not drawn

#### Scenario: Gameplay is unchanged

- **WHEN** a flight is flown with the same inputs before and after the cable is drawn
- **THEN** the recorded track and the score are identical

### Requirement: Raster remains the shipped path and boundaries hold

The pipeline SHALL work with 4x multisampling on the default framebuffer and on a 3.3 core context, SHALL NOT make the game
depend on any editor, game-link or ray-tracing module, and SHALL NOT change ray mode. On a context that cannot run the
pipeline the game SHALL draw as before and log the reason.

#### Scenario: Shipped classpath

- **WHEN** `checkShippedClasspath` runs
- **THEN** it passes

#### Scenario: Ray mode unchanged

- **WHEN** the game is launched with the ray field view
- **THEN** the ray image is produced as before and the raster pipeline does not run for the field

#### Scenario: Frame time

- **WHEN** the pilot view is drawn on the recorded reference machine and resolution
- **THEN** the steady GPU frame time stays within the budget recorded in the design, measured apart from loading
