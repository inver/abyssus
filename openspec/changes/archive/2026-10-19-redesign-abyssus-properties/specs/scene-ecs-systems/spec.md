# Spec Delta

## ADDED Requirements

### Requirement: Systems skip disabled entities and components

The rendering, light, camera and foliage systems SHALL treat a disabled entity or a disabled component as absent. Enabling one again SHALL restore it on the next update without reloading the scene.

#### Scenario: Disabled light
- **WHEN** a directional light's `LightComponent` is disabled
- **THEN** the scene is lit as if the light were absent

#### Scenario: Disabled renderable
- **WHEN** a model entity is disabled
- **THEN** the render pass draws no model for it and its shadow is not cast

#### Scenario: Re-enable
- **WHEN** the entity is enabled again while the scene is shown
- **THEN** it is drawn again without a reload
