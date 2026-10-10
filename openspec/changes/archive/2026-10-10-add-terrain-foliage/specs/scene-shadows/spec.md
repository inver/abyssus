# Spec Delta

## MODIFIED Requirements

### Requirement: Scene geometry casts and receives shadows

Displayed opaque and alpha-tested models and terrain SHALL cast and receive shadows from supported directional, point and spot lights selected within the shadow budget. Displayed foliage copies of OBJECT layers SHALL cast and receive shadows like models; copies of DETAIL layers SHALL receive shadows and SHALL NOT cast them. Editor grids, sky backgrounds, camera and light markers, selection highlights and gizmos SHALL NOT cast or receive shadows.

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

#### Scenario: Foliage trees shadow the terrain
- **WHEN** foliage trees of an OBJECT layer stand between a shadowed directional light and the terrain
- **THEN** the trees cast shadows on the terrain and on each other

#### Scenario: Grass does not cast
- **WHEN** DETAIL-layer grass stands in the shadow of a foliage tree
- **THEN** the grass is darkened by the tree's shadow and casts no shadow of its own

#### Scenario: Painted foliage shadows
- **WHEN** the user paints new copies of an OBJECT layer
- **THEN** their shadows appear with them during the stroke
