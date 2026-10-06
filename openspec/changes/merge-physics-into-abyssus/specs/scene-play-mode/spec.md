## MODIFIED Requirements

### Requirement: Play controls

With physics on for the scene's project, the Scene view toolbar SHALL offer Play, Pause, Step and Stop. Play SHALL start or
resume the simulation of the scene as the editor holds it (unsaved text included), Pause SHALL hold it, Step SHALL
advance a paused simulation by one fixed step, and Stop SHALL end it. While playing, the toolbar SHALL show that the
view is in play mode. Turning physics off for the project SHALL stop play and remove the controls.

#### Scenario: Play and stop

- **WHEN** the user presses Play on the `Physics` scene, waits 3 seconds and presses Stop
- **THEN** `Model 0` is seen falling onto the terrain, and after Stop it is drawn at Y `3.086434` again

#### Scenario: Step while paused

- **WHEN** the simulation is paused and the user presses Step
- **THEN** the poses advance by one 1/120 s step and the simulation stays paused

#### Scenario: Physics turned off while playing

- **WHEN** physics is turned off for a copy of the `Physics` project while its scene is playing
- **THEN** play stops, the scene is shown as the document holds it, and the toolbar has no Play, Pause, Step or Stop
