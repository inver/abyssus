# Spec Delta

## ADDED Requirements

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
