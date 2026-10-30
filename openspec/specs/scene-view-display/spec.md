# scene-view-display Specification

## Purpose

Defines the scene view's display controls (shading, Shadows, Fog, Grid), Frame selection, the orientation widget and the stats strip, all of which change what the view shows without changing the scene.

## Requirements

### Requirement: View layer toggles

The toolbar SHALL have Shadows, Fog and Grid toggles, each on by default. Turning one off SHALL hide that layer in this view only; it SHALL NOT change the scene's `fogEnabled`, any light's shadow setting or any file. A toggle SHALL take effect on the next frame without reopening the view. Shadows and Fog SHALL be disabled when the scene has nothing that uses them.

#### Scenario: Hide the grid
- **WHEN** the user turns Grid off
- **THEN** the ground grid is not drawn and the scene file is unchanged

#### Scenario: Fog toggle is separate from the scene setting
- **WHEN** the scene has `fogEnabled` true and the user turns the Fog toggle off
- **THEN** the view draws no fog and `fogEnabled` stays true in the file

#### Scenario: A new view starts on
- **WHEN** the user turns Shadows off, closes the scene and opens it again
- **THEN** Shadows is on

#### Scenario: Nothing to toggle
- **WHEN** the scene has no light that casts shadows
- **THEN** the Shadows toggle is disabled

### Requirement: Shading choice

The toolbar SHALL have a shading drop-down with Lit, Unlit and Wireframe, Lit by default. Unlit SHALL draw models and terrains with their base colors and textures and without scene lighting or shadows. The choice SHALL apply to the view only.

#### Scenario: Unlit
- **WHEN** the user chooses Unlit
- **THEN** models are drawn without lighting or shadows and the Shadows toggle is disabled

#### Scenario: Back to Lit
- **WHEN** the user chooses Lit again
- **THEN** lighting and the previous Shadows toggle state are restored

### Requirement: Wireframe shading mode

The shading drop-down SHALL offer Wireframe as a third mode alongside Lit and Unlit. In Wireframe mode models, terrain meshes, foliage OBJECT layers, the skybox cube and water surfaces SHALL draw as edge lines instead of filled surfaces; lights, shadows, fog, the grid, markers, selection box and gizmos are unchanged, and no shadow atlas is produced while shading is Wireframe (shadows run only under Lit). Wireframe SHALL apply to the raster path only: while Ray Tracing presents a frame, the ray image is drawn as usual.

#### Scenario: Wireframe is a third shading mode
- **WHEN** the user opens the scene view's shading dropdown
- **THEN** it lists Lit, Unlit and Wireframe

#### Scenario: Wireframe draws edges only
- **WHEN** shading is Wireframe and the scene has a model, a terrain, a skybox and water
- **THEN** those draw as edge lines instead of filled surfaces, and the grid, markers, selection box and gizmos are still drawn

#### Scenario: Ray Tracing keeps showing the ray image
- **WHEN** Ray Tracing is presenting a frame and the user chooses Wireframe
- **THEN** the ray-traced image is shown as usual

#### Scenario: Wireframe is per view and never saved
- **WHEN** the user sets shading to Wireframe, closes the scene view and reopens it
- **THEN** shading is Lit again

### Requirement: Frame selection

Frame selection, from the toolbar button or `F` while the view has focus, SHALL move the orbit camera to look at the selected object so that it fills a comfortable part of the view, keeping the current view direction. With no selection it SHALL frame the scene's drawn content. It SHALL NOT write the scene and SHALL do nothing while looking through a scene camera or while Play runs.

#### Scenario: Frame a model
- **WHEN** `Model 0` is selected and the user presses F
- **THEN** the orbit target moves to the model and the model is fully visible

#### Scenario: Nothing selected
- **WHEN** nothing is selected and the user clicks Frame selection
- **THEN** the camera shows all drawn models and terrains

#### Scenario: Looking through a camera
- **WHEN** the view looks through `Camera 4` and the user presses F
- **THEN** nothing changes

### Requirement: Orientation widget

The viewport SHALL show an orientation widget drawn in its upper-right corner: X, Y and Z axes in the gizmo colors, rotated to match the camera. Clicking an axis SHALL turn the orbit camera to look along it, keeping its target and distance. The widget SHALL stay in the corner when the view is resized, and SHALL NOT take clicks meant for the scene outside its circle.

#### Scenario: Follows the camera
- **WHEN** the user orbits the camera 90 degrees about Y
- **THEN** the X and Z axes of the widget turn with it

#### Scenario: Click an axis
- **WHEN** the user clicks the Y axis of the widget
- **THEN** the camera looks straight down and the target and distance are unchanged

#### Scenario: Click through
- **WHEN** the user clicks the viewport outside the widget's circle
- **THEN** the click picks as usual

### Requirement: Stats strip

The viewport SHALL show a strip in its lower-left corner with the number of objects the scene draws and the current tool and space. When `Show FPS` of the project is on, the strip SHALL begin with the frame rate as `<n> fps`. The strip SHALL stay readable on light and dark scenes, SHALL stay above loading feedback, and SHALL NOT take clicks.

#### Scenario: Object count
- **WHEN** `Main Scene` of the `Untitled` fixture is open
- **THEN** the strip shows the count of its drawn models and terrains and the current tool

#### Scenario: FPS is optional
- **WHEN** `Show FPS` is off
- **THEN** the strip shows no frame rate

#### Scenario: FPS on
- **WHEN** `Show FPS` is on and a complete sample has been taken
- **THEN** the strip begins with the measured integer frame rate

#### Scenario: Strip does not block clicks
- **WHEN** the user clicks an object behind the strip
- **THEN** the object is selected
