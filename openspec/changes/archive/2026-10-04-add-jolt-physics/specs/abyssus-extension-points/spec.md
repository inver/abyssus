# Spec Delta

## ADDED Requirements

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
