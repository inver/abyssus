# scene-shadows Specification

## Purpose

Lets users see how placed models and terrain occlude scene lights, so the scene view communicates depth and light placement.

## Requirements

### Requirement: Scene geometry casts and receives shadows

Displayed opaque and alpha-tested models and terrain SHALL cast and receive shadows from supported directional, point and spot lights selected within the shadow budget. Displayed foliage copies of OBJECT layers SHALL cast and receive shadows like models; copies of DETAIL layers SHALL receive shadows and SHALL NOT cast them. Editor grids, sky backgrounds, camera and light markers, selection highlights and gizmos SHALL NOT cast or receive shadows. The only exception is the clouds of a procedural sky, which SHALL cast cloud shadows as defined in "Clouds cast shadows from the sun".

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

### Requirement: Shadows affect individual lights

A shadow SHALL attenuate only the contribution of its associated light. Other lights, ambient light, HDR environment lighting and emissive materials SHALL remain independent of that shadow.

#### Scenario: A second light fills a shadow
- **WHEN** a surface is blocked from one light but exposed to another
- **THEN** the second light continues to illuminate it

#### Scenario: Environment remains visible
- **WHEN** a surface lit by an HDR sky is shadowed from a scene light
- **THEN** its environment lighting remains visible

### Requirement: Shadows follow the displayed scene

Shadows SHALL follow displayed object and light transforms, animated geometry, loading, deletion, scene edits and in-progress transform previews without reopening the view.

#### Scenario: Drag preview
- **WHEN** the user moves a model or light with a gizmo
- **THEN** its shadows follow the displayed preview during the drag and the committed transform after release

#### Scenario: Animation
- **WHEN** an animated model changes pose
- **THEN** its shadow follows that pose

#### Scenario: Asset appears or disappears
- **WHEN** an asset finishes loading or its entity is removed
- **THEN** its shadow appears or disappears with its geometry

### Requirement: Shadow cost and failures are bounded

The view SHALL use a fixed shadow budget, choose lights deterministically, and continue illuminating with supported lights that lack shadows. Shadow resource failures SHALL leave the scene interactive and lit without a modal error. Invalid or removed lights SHALL NOT leave stale shadows.

#### Scenario: More lights than shadow capacity
- **WHEN** the scene contains more supported lights than the shadow budget permits
- **THEN** a deterministic subset casts shadows and the remaining supported lights still illuminate the scene

#### Scenario: Shadow resource failure
- **WHEN** a shadow resource cannot be created
- **THEN** the affected light illuminates without shadows and the remaining scene still renders

#### Scenario: View restored
- **WHEN** the scene view is hidden and then shown again
- **THEN** it resumes rendering without stale or invalid shadow images

### Requirement: Transparent material shadow behavior

Alpha-tested cutouts SHALL preserve their holes in cast shadows. Alpha-blended models SHALL receive shadows but SHALL NOT cast shadows in this version.

#### Scenario: Cutout texture
- **WHEN** an alpha-tested leaf surface occludes a light
- **THEN** its shadow follows the solid parts and leaves holes where fragments are cut out

#### Scenario: Blended material
- **WHEN** a model uses an alpha-blended material
- **THEN** it does not cast a shadow and its visible surface can receive shadows

### Requirement: Clouds cast shadows from the sun

While the scene's procedural sky draws enabled clouds, terrain and models SHALL be shaded by the clouds between them
and the sun, the brightest usable directional light. The shade SHALL drift with each band's wind, match where the sky shows
clouds, attenuate only the sun light, and combine with that light's own scene shadow. Within the cloud shadow area, it
SHALL replace the uniform sun dimming of `scene-entity-lights`; beyond it, that dimming SHALL apply.

#### Scenario: Moving cloud shadows

- **WHEN** a copy of `Main Scene` uses `skybox_physical` naming a cloud asset made from the fair template and the view stays open for ten
  seconds
- **THEN** dark patches move across the terrain along the low band's wind direction

#### Scenario: Outside the shadow area

- **WHEN** a surface lies beyond the cloud shadow area
- **THEN** the existing uniform sun dimming applies, with a smooth transition at the boundary

#### Scenario: Shadow resource failure

- **WHEN** a cloud shadow resource cannot be built
- **THEN** uniform sun dimming continues, the view remains interactive, and no modal error appears

#### Scenario: Sun not rendered

- **WHEN** the brightest usable directional light is outside the rendered light budget, or no usable directional light exists
- **THEN** cloud shadows do not attenuate a different light

#### Scenario: Cloud asset reload

- **WHEN** the cloud asset is changed or removed while the view remains open
- **THEN** cloud shadows follow the newly displayed clouds, and no stale cloud pattern remains

#### Scenario: Only the sun is shaded

- **WHEN** a copy of `Main Scene` makes entity `7` the brightest usable directional light within the light budget,
  and a cloud shadow covers the part of the terrain that `Spot Light 8` lights
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

#### Scenario: Cloud drawing failed

- **WHEN** every cloud drawing technique has failed and the sky is drawn without clouds
- **THEN** no cloud shadows remain on models or terrain
