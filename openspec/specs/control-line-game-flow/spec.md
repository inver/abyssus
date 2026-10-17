# control-line-game-flow Specification

## Purpose

Defines the control-line game's screens and how a player moves between them: from the main menu, through choosing a
plane and flying, to GAME OVER, a retry and the score table.

## Requirements

### Requirement: Main menu

The game SHALL open on a main menu offering Start, Scores and Quit, usable with the arrow keys and Enter or with the
mouse. Start SHALL be disabled, with a message, when the bundled field scene has no plane.

#### Scenario: Start

- **WHEN** the player chooses Start
- **THEN** the plane select screen opens

#### Scenario: No planes

- **WHEN** the field scene holds no entity with a `PlaneComponent`
- **THEN** Start is disabled and the menu says no plane was found in the field scene

### Requirement: Plane select from the field scene

The plane select screen SHALL offer every entity of the field scene that has a `PlaneComponent`, in the order of their
names, showing each plane's name, class, line length, mass and fuel time, and moving the camera to the selected parked
plane. Left and right SHALL change the plane, Enter SHALL start the flight with it, and Escape SHALL return to the main
menu.

#### Scenario: Bundled planes

- **WHEN** the plane select screen opens on the bundled `ControlLine` field
- **THEN** it offers `Racer`, `Stunter` and `Trainer` in that order, starting with `Racer`

#### Scenario: A plane added in Abyssus

- **WHEN** a fourth plane entity with a `PlaneComponent` is added to the field scene in Abyssus and the game is started
  again
- **THEN** the plane select screen offers it too, with no code change

### Requirement: Flying

The flight screen SHALL show the scene from the pilot's eyes, turning to follow the plane, and a HUD with line tension,
laps, score, combo multiplier and fuel left. W or Up SHALL tilt the handle up and S or Down down, reaching full tilt in
0.15 s and returning to neutral when released; moving the mouse vertically SHALL set the tilt by its distance from the
window's centre, and whichever was used last SHALL control the handle. Escape SHALL pause, offering Resume and Main
menu.

#### Scenario: Pause

- **WHEN** the player presses Escape while flying
- **THEN** the flight stops advancing and the pause menu offers Resume and Main menu

### Requirement: GAME OVER

When a flight ends, the game SHALL show GAME OVER with the reason (crashed, lines went slack or landed), the score,
laps, best combo and flight time. When the score enters the score table, it SHALL first ask for a name of up to 12
characters, offering the last name entered. It SHALL then offer Retry, Scores and Main menu.

#### Scenario: Crash with a high score

- **WHEN** a flight with `Stunter` crashes with a score that enters the table
- **THEN** GAME OVER shows "Crashed" and the score, asks for a name, saves the flight, then offers Retry, Scores and
  Main menu

### Requirement: Retry flies the same plane again

Retry SHALL start a new flight with the same plane straight away, from takeoff, with the score, laps and combo reset.

#### Scenario: Retry

- **WHEN** the player chooses Retry after a flight with `Stunter`
- **THEN** a new flight with `Stunter` starts from takeoff with score 0

### Requirement: Score table screen

The score table screen SHALL list the 10 best flights with rank, name, plane, score, laps, best combo, flight time and
date, highlight the flight just saved, and return with Escape or Back to the screen it was opened from.

#### Scenario: After saving

- **WHEN** the player chooses Scores on GAME OVER after saving a flight that ranks second
- **THEN** the list shows that flight in second place, highlighted, and Back returns to GAME OVER

### Requirement: The same flight in Abyssus

Pressing Play in Abyssus on the game's field scene SHALL fly the plane selected in the Abyssus tree, or the first
plane by name when no plane is selected, with the same flight rules and controls as the game, without its screens.

#### Scenario: Play the selected plane

- **WHEN** `Trainer` is selected in the Abyssus tree and the user presses Play on the field scene
- **THEN** `Trainer` takes off on its lines and the W and S keys tilt the handle

### Requirement: The crash shows on the plane

When a flight ends as a crash with a severity, the game SHALL play the crashed plane's model clip for that severity
once (`crash_little`, `crash_medium` or `crash_full`), from the moment of the crash, and SHALL hold its last frame
while the plane comes to rest and through GAME OVER. A plane whose model has no such clip SHALL be drawn as before.

#### Scenario: The Trainer crashes hard

- **WHEN** `Trainer` crashes with a medium severity
- **THEN** its left wing folds down at the root and its tail breaks off, and they stay so on GAME OVER

#### Scenario: A plane without crash clips

- **WHEN** `Racer` crashes
- **THEN** it is drawn whole, as before

### Requirement: The Trainer's crash clips

The bundled `Trainer` model SHALL have three crash clips, each starting from its rest pose: `crash_little`, where the
landing gear breaks; `crash_medium`, where the left wing and the tail break; and `crash_full`, where the gear, the
propeller, both wings and the tail come off.

#### Scenario: The clips are in the model

- **WHEN** the game loads `model_trainer`
- **THEN** it has `crash_little`, `crash_medium` and `crash_full`, and its rest pose is the whole plane

### Requirement: Screens are authored UI
Every screen and the flying HUD of the game SHALL be defined in a UI asset of the bundled `ControlLine` project and presented by the shared UI renderer, driven by the game's flow state. The game SHALL NOT keep a hand-written layout for any of them. The screens, their text, their order, their keyboard and mouse controls and their results (including the score tables, the F2B sheet, the debrief rows and the name entry limits) SHALL be those of the game before the change. Editing the asset in Abyssus SHALL change the game's screens without a code change. Game state that the screens display (laps, score, combo, fuel, time, tension, the next manoeuvre, warnings, score rows, sheet rows, debrief rows and deductions) SHALL reach the UI only as typed bindings published by the game.

#### Scenario: Same screens, same controls
- **WHEN** a player moves through main menu, assist select, plane select, flight, pause, game over, name entry, scores and a pattern debrief
- **THEN** each screen shows the same information as before and Up, Down, Left, Right, Enter, Escape, Space, R, V and 1 / 2 / 3 do what they did

#### Scenario: Edit a screen without code
- **WHEN** a designer changes the main menu's title text in the UI asset and starts the game
- **THEN** the new title is shown with no code change

#### Scenario: Free and pattern HUD
- **WHEN** a free flight and a pattern flight at each assist level are flown
- **THEN** the free HUD shows laps, score, combo and fuel, the pattern HUD shows fuel and time, and the Mid and Noob levels also show the next manoeuvre, the pause count and the height warning, as before

### Requirement: Scores and results rows come from lists
The free-flight table, the F2B boards, the F2B results sheet and the debrief rows SHALL be shown through list widgets bound to rows the game publishes, with the rank, highlight, selected row, red failure rows and "No flights yet" empty state of the earlier screens. A click on an F2B board row SHALL open its debrief as before.

#### Scenario: Saved flight is highlighted
- **WHEN** a flight is saved at rank 3 and the scores screen opens
- **THEN** the third row is highlighted and the others are not

#### Scenario: Empty table
- **WHEN** a board has no flights
- **THEN** it shows "No flights yet"

### Requirement: No separate UI demo
The repository SHALL NOT contain a standalone UI demo application, its launch task, its fixture project or its documentation. The game's own screens SHALL be the end-to-end proof that authored UI runs in a game.

#### Scenario: Demo removed
- **WHEN** the repository is searched after the change
- **THEN** no `runUiDemo` task, `uidemo` package or `uiDemo` fixture exists and no document refers to them
