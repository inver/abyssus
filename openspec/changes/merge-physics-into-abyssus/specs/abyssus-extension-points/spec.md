## MODIFIED Requirements

### Requirement: Plugins run simulations in the Scene view

Another installed plugin SHALL be able to provide Play for a Scene view: Abyssus SHALL show the play controls when a provider offers Play for the scene's project, hand it the scene text, the project folder and the selection on Play, show the poses it returns instead of the authored ones, and stop it on Stop, on any document change and when the view closes. When no provider offers Play for the project, no play controls SHALL appear.

#### Scenario: No simulation provider
- **WHEN** a Scene view of `Main Scene` opens in a copy of `Untitled`, whose physics is off, and no other plugin
  provides Play
- **THEN** the toolbar has no Play, Pause, Step or Stop

#### Scenario: View closed while playing
- **WHEN** the Scene view tab is closed while playing
- **THEN** the provider is told to stop, and its process exits
