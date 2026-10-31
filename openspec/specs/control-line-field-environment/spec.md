
## MODIFIED Requirements

### Requirement: Realistic daylight presentation

The field SHALL use ground and scenery materials based on photo-sourced textures, under bright midday sun. In the
game, the scene's sun SHALL cast soft-edged shadows from models onto the terrain and other models. The game SHALL show the field in linear light through the output transform and light models, terrain and foliage by the same sun and sky, as `control-line-pbr-rendering` requires. Pavement,
compacted earth, dry vegetation, roofs and rails SHALL remain distinguishable from the pilot camera.

#### Scenario: Inspect midday appearance

- **WHEN** the field is rendered in the game from the pilot camera
- **THEN** trees, buildings, the pilot and the plane cast soft-edged shadows on the ground and on other scenery
- **AND** lighting and material detail communicate a dry, worn midday environment
- **AND** sun-lit surfaces are not clipped to white and shadowed surfaces are not black

#### Scenario: Surfaces stay distinguishable under the pipeline

- **WHEN** the field is rendered from the pilot camera with the pipeline on
- **THEN** pavement, compacted earth, dry vegetation, roofs and rails differ from one another by at least 10% in linear brightness or by hue

#### Scenario: Gameplay cameras are unchanged

- **WHEN** the game is played
- **THEN** the menu, plane-select and flying cameras behave as before
