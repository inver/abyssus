# Spec Delta

## Purpose

Lets users inspect ray-traced shadows and scene reflections during interactive scene editing on supported GPUs, with an explicit switch and reliable fallback.

## ADDED Requirements

### Requirement: Optional Ray Tracing mode

The Scene view SHALL offer a Ray Tracing toggle, off by default for each newly opened view. Enabling it on a supported machine SHALL enable both ray-traced shadows and reflections. Disabling it SHALL restore the existing rendering mode while preserving the camera and selection. The toggle SHALL NOT change `.scene`, `.abss` or asset files.

#### Scenario: Switch an existing scene
- **WHEN** the user enables Ray Tracing in a copy of Untitled's Main Scene with Model 0 selected
- **THEN** the mode becomes active with the same selection and camera, and the project files remain unchanged

#### Scenario: Independent views
- **WHEN** the user enables Ray Tracing in one view and opens another scene view
- **THEN** the new view starts with Ray Tracing off

### Requirement: Supported platforms and availability

Ray Tracing SHALL be available on macOS, Windows and Linux when a compatible GPU, driver and backend are present. Unavailable machines SHALL continue using the existing renderer and show a concise reason beside or in the disabled toggle. Recoverable initialization or rendering failures SHALL revert to the existing renderer without a modal dialog.

#### Scenario: Supported platform
- **WHEN** the user opens the view on any of the three operating systems with a usable ray-tracing GPU and backend
- **THEN** Ray Tracing can be enabled

#### Scenario: Unsupported hardware
- **WHEN** the required GPU features are absent
- **THEN** the scene renders normally and the disabled toggle explains that ray tracing is unavailable

#### Scenario: Backend failure
- **WHEN** initialization or rendering reports a recoverable backend failure
- **THEN** the view resumes its existing renderer, preserves editing state and displays a non-modal explanation

### Requirement: Ray-traced shadows

Opaque and alpha-tested models and terrain SHALL cast and receive ray-traced shadows from selected supported directional, point and spot lights. Each shadow SHALL attenuate only its own light, preserving other lights, ambient/HDR diffuse lighting and emission. Cutout holes SHALL remain open; alpha-blended surfaces SHALL receive shadows but not cast them.

#### Scenario: Geometry blocks a light
- **WHEN** a model or terrain ridge blocks a selected light from a displayed surface
- **THEN** the blocked surface shows that light's shadow and continues receiving other illumination

#### Scenario: Local lights
- **WHEN** geometry blocks a point light on different sides or a spot light within its cone and range
- **THEN** the appropriate surfaces show shadows respecting the light's direction, cone and range

#### Scenario: Cutout and blended surfaces
- **WHEN** an alpha-tested leaf or alpha-blended surface lies between a selected light and terrain
- **THEN** the leaf casts a shadow with holes, and the blended surface does not cast a shadow

### Requirement: Scene geometry appears in reflections

PBR model surfaces SHALL reflect opaque and alpha-tested scene geometry, including terrain, according to their existing metallic and roughness values. Reflections SHALL include geometry outside the camera image. Rays that miss geometry SHALL use the existing environment. Reflection recursion SHALL be bounded; transparent refraction and reflected alpha-blended geometry are outside this version.

#### Scenario: Offscreen reflection
- **WHEN** a model outside the camera image lies in a smooth PBR surface's reflected direction
- **THEN** the model appears in that surface's reflection

#### Scenario: Roughness changes the reflection
- **WHEN** otherwise identical PBR surfaces have low and high roughness
- **THEN** the low-roughness surface has a sharper reflection and the high-roughness surface has a broader reflection

#### Scenario: Reflection misses geometry
- **WHEN** a reflection ray hits no scene geometry
- **THEN** it shows the applicable sky environment, or the existing background when no sky is enabled

### Requirement: Effects remain live while editing

Shadows and reflections SHALL update during camera movement and object/light transform previews, after commits and undo/redo, and when animation, loading or document edits change displayed content. Editing SHALL remain responsive; rendering work and queued updates SHALL be bounded. Quality MAY improve when movement stops, but effects SHALL NOT require movement to stop before updating.

#### Scenario: Drag an object or light
- **WHEN** the user continuously drags a model or light with a gizmo
- **THEN** shadows and reflections follow the ongoing preview, then the committed transform, without reopening the view

#### Scenario: Change the camera
- **WHEN** the user orbits, pans or looks through Camera 4 in a copy of Untitled
- **THEN** reflections follow the active camera and controls remain usable

#### Scenario: Undo and asset changes
- **WHEN** the user undoes a move, deletes an entity or an asset finishes loading
- **THEN** both effects update to the displayed scene without stale removed geometry

#### Scenario: Animated geometry
- **WHEN** the model in a copy of Animated/Main.scene changes pose
- **THEN** its visible geometry, shadows and reflected appearance follow the same pose

### Requirement: Editor display and lifecycle

Ray Tracing SHALL preserve textures, terrain splats, sky background, fog, picking, selection and transform controls. Editor grids, markers, highlights and gizmos SHALL remain visible with their existing occlusion behavior and SHALL NOT cast shadows or appear in reflections. Hidden or closed views SHALL stop submitting rendering work and release resources safely; showing a view SHALL resume without stale images.

#### Scenario: Edit through overlays
- **WHEN** the user selects Model 0 and moves it in Ray Tracing mode
- **THEN** picking and gizmos operate as in the existing mode and the gizmo has no shadow or reflection

#### Scenario: Restore a view
- **WHEN** a view is hidden, resized and shown again
- **THEN** it resumes with the current camera and scene, correct image size and no stale effects
