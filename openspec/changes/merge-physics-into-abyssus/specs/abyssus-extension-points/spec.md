## MODIFIED Requirements

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
