# Spec Delta

## ADDED Requirements

### Requirement: Watch AI launch
F2B plane selection SHALL offer Watch AI alongside the existing player launch. It SHALL launch the selected stunt plane with the selected assist display level and be operable by keyboard and mouse. Free flight SHALL NOT offer Watch AI. With no stunt plane available, no AI flight SHALL launch.

#### Scenario: Watch the selected plane
- **WHEN** the player chooses F2B pattern, Mid, Stunter and Watch AI
- **THEN** a Mid F2B demonstration starts with Stunter and the flight screen clearly displays AI demonstration

#### Scenario: Human launch remains available
- **WHEN** the player uses the existing launch action on F2B plane selection
- **THEN** a human-controlled F2B flight starts as before

#### Scenario: No stunt aircraft
- **WHEN** the field contains no stunt plane
- **THEN** the existing unavailable-F2B message prevents both player and AI pattern launches

### Requirement: Demonstration identity and controls
AI demonstration identity SHALL remain visible in flight, pause, results and debrief. Keyboard and mouse flight input SHALL NOT affect the AI handle. Escape SHALL pause both the flight and bot; Resume SHALL continue the same attempt. Main menu SHALL end the attempt, and starting a human flight afterward SHALL restore normal input.

#### Scenario: Spectator moves the mouse
- **WHEN** the spectator moves the mouse or holds flight-control keys during Watch AI
- **THEN** the bot remains the sole handle controller

#### Scenario: Pause during a figure
- **WHEN** the spectator pauses during a loop and later resumes
- **THEN** the plane, fuel, clock and bot progress stay frozen during pause and continue from that state on resume

### Requirement: Demonstration results and retry
AI flights SHALL show their real F2B score sheet and offer Retry, Scores, Main menu and Debrief without name entry. They SHALL NOT qualify for or write player score boards or board recordings. Their current recording SHALL remain available for debrief until the attempt is discarded. Retry SHALL start a fresh AI attempt with the same plane and assist level.

#### Scenario: AI score beats the board
- **WHEN** an AI flight ends with a score above every player score
- **THEN** results open without name entry, all score and recording files remain unchanged, and Debrief shows that AI flight's actual tracks and deductions

#### Scenario: Retry from results
- **WHEN** the spectator chooses Retry after an AI flight
- **THEN** the same plane starts stationary as a fresh AI attempt with fuel, clock, controller state and score sheet reset

## MODIFIED Requirements

### Requirement: Flying
The flight screen SHALL show the scene from the pilot's eyes, turning to follow the plane, and a HUD with line tension, laps, score, combo multiplier and fuel left in Free flight. F2B flights SHALL show line tension, fuel and time against 7:00 plus the selected assist display. In human flights, W or Up SHALL tilt the handle up and S or Down down, reaching full tilt in 0.15 s and returning to neutral when released; moving the mouse vertically SHALL set the tilt by its distance from the window's centre, and whichever was used last SHALL control the handle. In AI demonstrations only the bot SHALL control the handle. Escape SHALL pause, offering Resume and Main menu.

#### Scenario: Pause
- **WHEN** the player presses Escape while flying
- **THEN** the flight stops advancing and the pause menu offers Resume and Main menu

#### Scenario: A Pro HUD
- **WHEN** a human F2B flight at Pro has flown 1:12
- **THEN** the HUD shows tension, fuel left and `1:12 / 7:00`, and no manoeuvre names, laps or marks

### Requirement: GAME OVER
When a flight ends, the game SHALL show GAME OVER with the reason (crashed, lines went slack or landed). Free flight SHALL show score, laps, best combo and flight time; F2B SHALL show assist level, the 15-row score sheet with marks, K, points or zero reasons, total score and flight time. When a human score enters its table or board, it SHALL first ask for a name of up to 12 characters, offering the last name entered. AI demonstrations SHALL bypass name entry and board qualification. Results SHALL offer Retry, Scores and Main menu, and F2B results SHALL also offer Debrief.

#### Scenario: Crash with a high score
- **WHEN** a human Free flight with Stunter crashes with a score that enters the table
- **THEN** GAME OVER shows Crashed and the score, asks for a name, saves the flight, then offers Retry, Scores and Main menu

#### Scenario: F2B score sheet
- **WHEN** a human Mid F2B flight lands after takeoff was marked 8.5 and every other manoeuvre scored 0
- **THEN** GAME OVER shows Landed, Take-off `8.5 × 2 = 17.0`, the other 14 rows as zero with reasons, and total 17.00

### Requirement: Retry flies the same plane again
Retry SHALL start a new flight with the same plane, mode, assist level and human or AI controller, straight away from stationary takeoff, with fuel, controller state, score, laps, combo, clock and score sheet reset.

#### Scenario: Retry
- **WHEN** the player chooses Retry after a human Free flight with Stunter
- **THEN** a new human Free flight with Stunter starts from takeoff with score 0

#### Scenario: Retry an F2B pattern
- **WHEN** the player chooses Retry after a human Pro F2B flight with Stunter
- **THEN** a new human Pro F2B flight starts with its clock at 0:00 and Take-off expected

#### Scenario: Retry a demonstration
- **WHEN** the spectator retries a Mid AI demonstration
- **THEN** the same plane begins a new Mid AI demonstration without retaining the old bot's path progress
