# Spec Delta

## Purpose

Lets a user run a scene's physics, and the game's own logic, from the Scene view without leaving the editor, and
without risking the IDE or the scene file.

## ADDED Requirements

### Requirement: Play controls

With Abyssus Physics installed, the Scene view toolbar SHALL offer Play, Pause, Step and Stop. Play SHALL start or
resume the simulation of the scene as the editor holds it (unsaved text included), Pause SHALL hold it, Step SHALL
advance a paused simulation by one fixed step, and Stop SHALL end it. While playing, the toolbar SHALL show that the
view is in play mode.

#### Scenario: Play and stop

- **WHEN** the user presses Play on the `Physics` scene, waits 3 seconds and presses Stop
- **THEN** `Model 0` is seen falling onto the terrain, and after Stop it is drawn at Y `3.086434` again

#### Scenario: Step while paused

- **WHEN** the simulation is paused and the user presses Step
- **THEN** the poses advance by one 1/120 s step and the simulation stays paused

### Requirement: Play never writes the scene

While playing, entity poses SHALL be shown from the simulation without changing the document or the file. Stop SHALL
show the scene exactly as the document holds it. No undo entry SHALL be created by playing.

#### Scenario: File untouched

- **WHEN** the `Physics` scene is played for 3 seconds and stopped
- **THEN** the scene file's text and the document are byte-identical to before Play, and Undo history is unchanged

### Requirement: Editing ends play

A change to the scene's document while playing, from any editor, the panel, the tree or a gizmo, SHALL stop the
simulation first. Gizmos SHALL NOT be offered while playing. Escape SHALL stop the simulation.

#### Scenario: Edit while playing

- **WHEN** the user changes a light's intensity in the panel while the scene is playing
- **THEN** play stops, the scene is shown as the document holds it, and the intensity change is applied

### Requirement: Play runs outside the IDE process

The simulation SHALL run in a separate process. If that process fails or exits, play SHALL stop, the scene SHALL be
shown as the document holds it, and one notification SHALL say that play ended unexpectedly, with the last lines the
process printed. The IDE SHALL keep running. Native physics code SHALL never be loaded into the IDE process.

#### Scenario: Play process killed

- **WHEN** the play process is killed while the `Physics` scene is playing
- **THEN** the view returns to the authored poses, one notification says play ended unexpectedly, and the IDE keeps
  working

### Requirement: Play runs the game's code when the project names it

When the project holds `abyssus/play.json`, Play SHALL run the game module, classpath and protocol version it names,
so the game's components and systems act in the simulation. Without it, Play SHALL run physics only. A `play.json`
naming a missing file or an unsupported protocol version SHALL stop Play before it starts with a message saying to
export the game again.

#### Scenario: Physics only

- **WHEN** the `Physics` project, which has no `play.json`, is played
- **THEN** the bodies simulate with physics alone

#### Scenario: Stale play file

- **WHEN** `play.json` names a classpath jar that no longer exists
- **THEN** Play does not start, and a message names the missing jar and says to export the game again

### Requirement: Input reaches the game while playing

While playing and the Scene view has focus, keyboard and mouse input other than Escape SHALL be passed to the game,
not to the editor's shortcuts. The selected entity when Play was pressed SHALL be passed to the game.

#### Scenario: Keys go to the game

- **WHEN** a test game that records input is playing and the user presses `W`
- **THEN** the game receives `W` pressed and released, and the editor's move-gizmo shortcut does not fire

### Requirement: Play starts quickly or explains why not

Play SHALL show progress while the process starts, and SHALL report a failure if the process has not connected within
20 seconds, then clean the process up.

#### Scenario: Hung start

- **WHEN** a game's play module blocks before connecting
- **THEN** after 20 seconds a message says play could not start, and no play process is left running
