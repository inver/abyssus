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
