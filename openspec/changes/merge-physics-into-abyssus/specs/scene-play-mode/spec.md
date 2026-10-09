## MODIFIED Requirements

### Requirement: Play controls

With physics on for the scene's project, the built-in physics provider SHALL offer Play, Pause, Step and Stop in the
Scene view toolbar. Play SHALL start or
resume the simulation of the scene as the editor holds it (unsaved text included), Pause SHALL hold it, Step SHALL
advance a paused simulation by one fixed step, and Stop SHALL end it. While playing, the toolbar SHALL show that the
view is in play mode. Turning physics off SHALL stop that provider's simulation and remove its controls. Other available providers remain
subject to the extension-point availability contract.

#### Scenario: Play and stop

- **WHEN** the user presses Play on the `Physics` scene, waits 3 seconds and presses Stop
- **THEN** `Model 0` is seen falling onto the terrain, and after Stop it is drawn at Y `3.086434` again

#### Scenario: Step while paused

- **WHEN** the simulation is paused and the user presses Step
- **THEN** the poses advance by one 1/120 s step and the simulation stays paused

#### Scenario: Physics turned off while playing

- **WHEN** physics is turned off for a copy of the `Physics` project while its scene is playing
- **THEN** play stops, the scene is shown as the document holds it, and, with no other available provider, the toolbar has no Play, Pause, Step or Stop

#### Scenario: Physics disabled during startup or pause
- **WHEN** physics is turned off while its simulation is starting or paused
- **THEN** the process is stopped, authored poses are restored and late callbacks cannot restart physics Play
