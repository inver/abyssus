# Spec Delta

## Purpose

Lets users see how placed models and terrain occlude scene lights, so the scene view communicates depth and light placement.

## ADDED Requirements

### Requirement: Scene geometry casts and receives shadows

Displayed opaque and alpha-tested models and terrain SHALL cast and receive shadows from supported directional, point and spot lights selected within the shadow budget. Editor grids, sky backgrounds, camera and light markers, selection highlights and gizmos SHALL NOT cast or receive shadows.

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
