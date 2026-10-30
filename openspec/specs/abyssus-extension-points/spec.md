# abyssus-extension-points Specification

## Purpose

Lets other IDE plugins add to what Abyssus edits and shows, so game-specific editor features can ship separately from
Abyssus itself.

## Requirements

### Requirement: Plugins contribute component schemas

Another installed plugin SHALL be able to contribute a component schema, in the format of the project schema file, to
every project Abyssus opens. Its components SHALL be edited like those of a project schema.

#### Scenario: Contributed component

- **WHEN** a test plugin contributes a schema declaring `MarkerComponent` and the `Untitled` project is open
- **THEN** "Add component" on entity `0` (`Model 0`) of `Main Scene` offers `MarkerComponent`

### Requirement: The project's schema wins

When a project schema and a contributed schema declare the same short name, Abyssus SHALL use the project's
declaration for that project and report the conflict once, naming the component and the contributing plugin.

#### Scenario: Two declarations

- **WHEN** the `Custom` project's schema and a contributed schema both declare `PlaneComponent`, with different fields
- **THEN** entity `0`'s plane shows the project's fields, and one message names `PlaneComponent` and the plugin

### Requirement: Contributions follow the plugin's lifetime

When a contributing plugin is unloaded or disabled, its components SHALL become unknown components, shown read-only,
and no scene file SHALL change.

#### Scenario: Plugin unloaded

- **WHEN** the plugin contributing `MarkerComponent` is unloaded while a scene with one is open
- **THEN** the component is shown as read-only JSON and the scene file is unchanged

### Requirement: Plugins draw in the Scene view

Another installed plugin SHALL be able to draw lines and markers each frame in every available Scene view, seeing
authored or simulated poses and selection. An unavailable provider SHALL contribute no drawing or actions. Providers without an availability gate
SHALL remain available. A draw failure SHALL disable that overlay for the view with one logged error while the view
keeps drawing. Availability changes SHALL take effect without reopening the view.

#### Scenario: Test overlay
- **WHEN** a test plugin draws a marker at each entity's position and `Main Scene` of a copy of `Untitled` is open
- **THEN** a marker is drawn at `Model 0`, `Model 2` and `Model 6`, among the others

#### Scenario: Failing overlay
- **WHEN** a test overlay throws while drawing
- **THEN** one error names the plugin, the overlay stops for that view and the scene keeps being drawn

#### Scenario: Availability changes
- **WHEN** a provider becomes unavailable and then available while its Scene view is open
- **THEN** its drawing and actions disappear and become available again without reopening the view

#### Scenario: Existing overlay provider
- **WHEN** an existing plugin provides an overlay without an availability gate
- **THEN** its drawing and actions remain available

### Requirement: Plugins run simulations in the Scene view

Another installed plugin SHALL be able to provide Play for a Scene view: Abyssus SHALL show the play controls when a provider offers Play for the scene's project, hand it the scene text, the project folder and the selection on Play, show the poses it returns instead of the authored ones, and stop it on Stop, on any document change and when the view closes. When no provider offers Play for the project, no play controls SHALL appear.

#### Scenario: No simulation provider
- **WHEN** a Scene view of `Main Scene` opens in a copy of `Untitled`, whose physics is off, and no other plugin
  provides Play
- **THEN** the toolbar has no Play, Pause, Step or Stop

#### Scenario: View closed while playing
- **WHEN** the Scene view tab is closed while playing
- **THEN** the provider is told to stop, and its process exits

#### Scenario: Available third-party Play with physics off
- **WHEN** physics is off but another provider offers Play for the scene's project
- **THEN** Play controls remain available for that provider

#### Scenario: Existing simulation provider
- **WHEN** an existing plugin provides Play without an availability gate
- **THEN** its Play controls remain available
