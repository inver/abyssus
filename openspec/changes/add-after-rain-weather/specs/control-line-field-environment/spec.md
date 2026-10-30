# Spec Delta

## MODIFIED Requirements

### Requirement: Realistic daylight presentation

With Default weather, the field SHALL use ground and scenery materials based on photo-sourced textures, under bright midday sun. After the rain SHALL add wet ground and automatic puddles while preserving the scene's authored sky, sunlight and fog. In the
game, the scene's sun SHALL cast soft-edged shadows from models onto the terrain and other models. Pavement,
compacted earth, dry vegetation, roofs and rails SHALL remain distinguishable from the pilot camera.

#### Scenario: Inspect midday appearance

- **WHEN** the field is rendered with Default weather in the game from the pilot camera
- **THEN** trees, buildings, the pilot and the plane cast soft-edged shadows on the ground and on other scenery
- **AND** lighting and material detail communicate a dry, worn midday environment

#### Scenario: Gameplay cameras are unchanged

- **WHEN** the game is played
- **THEN** the menu, plane-select and flying cameras behave as before

#### Scenario: After-rain field
- **WHEN** After the rain is selected on the bundled field
- **THEN** the asphalt circle and suitable surrounding ground look wet with irregular puddles, the painted rings remain identifiable, and the sky, lighting, cameras and flight behaviour remain unchanged

