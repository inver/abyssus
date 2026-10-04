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

Another installed plugin SHALL be able to draw lines and markers in every Scene view, each frame, over the scene,
seeing the poses the view shows (the authored ones, or the simulated ones during play) and the selected entity. A
contribution that fails while drawing SHALL be switched off for that view with one logged error, and the view SHALL
keep drawing.

#### Scenario: Test overlay

- **WHEN** a test plugin draws a marker at each entity's position and `Main Scene` of a copy of `Untitled` is open
- **THEN** a marker is drawn at `Model 0`, `Model 2` and `Model 6`, among the others

#### Scenario: Failing overlay

- **WHEN** a test overlay throws while drawing
- **THEN** one error is logged naming the plugin, the overlay stops for that view, and the scene keeps being drawn

### Requirement: Plugins run simulations in the Scene view

Another installed plugin SHALL be able to provide Play for a Scene view: Abyssus SHALL show the play controls when one
is installed, hand it the scene text, the project folder and the selection on Play, show the poses it returns instead
of the authored ones, and stop it on Stop, on any document change and when the view closes. Without one, no play
controls SHALL appear.

#### Scenario: No simulation provider

- **WHEN** a Scene view opens in an IDE with Abyssus but without Abyssus Physics
- **THEN** the toolbar has no Play, Pause, Step or Stop

#### Scenario: View closed while playing

- **WHEN** the Scene view tab is closed while playing
- **THEN** the provider is told to stop, and its process exits
