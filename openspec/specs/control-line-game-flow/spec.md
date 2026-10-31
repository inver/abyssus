# control-line-game-flow Specification

## Purpose

Defines the control-line game's screens and how a player moves between them: from the main menu, through choosing a
plane and flying, to GAME OVER, a retry and the score table.

## Requirements

### Requirement: Main menu

The game SHALL open on a main menu offering Free flight, F2B pattern, Scale, Scores and Quit, usable with the arrow keys
and Enter or with the mouse. Free flight SHALL be disabled, with a message, when the bundled field scene has no plane.
F2B pattern SHALL be disabled, with a message, when it has no plane of class `STUNT`. Scale SHALL be disabled, with a
message, when it has no plane of class `SCALE`.

#### Scenario: Start

- **WHEN** the player chooses Free flight
- **THEN** the plane select screen opens, offering every plane

#### Scenario: F2B pattern

- **WHEN** the player chooses F2B pattern
- **THEN** the assist level screen opens

#### Scenario: Scale

- **WHEN** the player chooses Scale on the bundled `ControlLine` field
- **THEN** the plane select screen opens, offering only `Mig-3`

#### Scenario: No planes

- **WHEN** the field scene holds no entity with a `PlaneComponent`
- **THEN** Free flight, F2B pattern and Scale are disabled and the menu says no plane was found in the field scene

#### Scenario: No stunt plane

- **WHEN** the field scene holds planes but none of class `STUNT`
- **THEN** F2B pattern is disabled and the menu says the F2B pattern needs a stunt plane

#### Scenario: No replica

- **WHEN** the field scene holds planes but none of class `SCALE`
- **THEN** Scale is disabled and the menu says Scale needs a scale replica

### Requirement: Plane select from the field scene

The plane select screen SHALL offer every entity of the field scene that has a `PlaneComponent`, in the order of their
names, showing each plane's name, class, line length, mass and fuel time, and moving the camera to the selected parked
plane. For an F2B pattern it SHALL offer only planes of class `STUNT`. Left and right SHALL change the plane, Enter
SHALL start the flight with it, and Escape SHALL return to the screen before it.

#### Scenario: Bundled planes

- **WHEN** the plane select screen opens for Free flight on the bundled `ControlLine` field
- **THEN** it offers `Racer`, `Stunter` and `Trainer` in that order, starting with `Racer`

#### Scenario: Bundled stunt planes

- **WHEN** the plane select screen opens for an F2B pattern on the bundled `ControlLine` field
- **THEN** it offers only `Stunter`, with a fuel time of 360 s

#### Scenario: A plane added in Abyssus

- **WHEN** a fourth plane entity with a `PlaneComponent` is added to the field scene in Abyssus and the game is started
  again
- **THEN** the plane select screen offers it too, with no code change

### Requirement: Flying
The flight screen SHALL show the scene from the pilot's eyes, turning to follow the plane, and a HUD with line tension, laps, score, combo multiplier and fuel left in Free flight. F2B flights SHALL show line tension, fuel and time against 7:00 plus the selected assist display. In human flights, W or Up SHALL tilt the handle up and S or Down down, reaching full tilt in 0.15 s and returning to neutral when released; moving the mouse vertically SHALL set the tilt by its distance from the window's centre; the handle axis of a connected controller SHALL set the tilt as `control-line-controller-input` describes; and whichever of these was used last SHALL control the handle. In AI demonstrations only the bot SHALL control the handle. Escape, or the pause button of the controller in use, SHALL pause, offering Resume and Main menu.

#### Scenario: Pause
- **WHEN** the player presses Escape while flying
- **THEN** the flight stops advancing and the pause menu offers Resume and Main menu

#### Scenario: Pause with a controller
- **WHEN** the player presses the pause button of the controller in use while flying
- **THEN** the flight stops advancing and the pause menu offers Resume and Main menu

#### Scenario: A Pro HUD
- **WHEN** a human F2B flight at Pro has flown 1:12
- **THEN** the HUD shows tension, fuel left and `1:12 / 7:00`, and no manoeuvre names, laps or marks

#### Scenario: A stick flies the plane
- **WHEN** the player pushes a connected stick's handle axis up during a human flight
- **THEN** the handle tilts up by the amount the device sets and the plane's elevator follows it

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

### Requirement: Debrief from GAME OVER

After an F2B flight, at every assist level, GAME OVER SHALL offer Debrief next to Retry, Scores and Main menu, after any
name entry. Debrief SHALL open the debrief of the flight just flown, whether or not it entered a board, and Escape
there SHALL return to GAME OVER. Free flight SHALL NOT offer Debrief.

#### Scenario: Debrief after a crash

- **WHEN** a Pro flight with `Stunter` crashes during the triangles and the player chooses Debrief on GAME OVER
- **THEN** the debrief opens on Take-off, and the manoeuvres from Two inside triangular loops on show "crashed"

#### Scenario: Free flight

- **WHEN** a Free flight ends
- **THEN** GAME OVER offers Retry, Scores and Main menu only

### Requirement: Debrief from the score table

On an F2B tab of the score table screen, Up and Down SHALL select a row, and Enter or a click SHALL open that flight's
debrief. Escape there SHALL return to the score table on the same tab and row.

#### Scenario: Replaying the best Pro flight

- **WHEN** the player opens Scores, switches to the Pro tab and presses Enter on the first row
- **THEN** the debrief of that flight opens, and Escape returns to the Pro tab with the first row selected

### Requirement: Assist level

After F2B pattern, the game SHALL offer the assist levels Pro, Mid and Noob, each with a one-line description, usable with
the arrow keys and Enter or with the mouse. Choosing one SHALL open plane select for the F2B pattern, and Escape SHALL
return to the main menu.

#### Scenario: Choosing Mid

- **WHEN** the player chooses F2B pattern and then Mid
- **THEN** plane select opens offering `Stunter`, and the flight that follows is a Mid F2B flight

### Requirement: Mid guidance

At Mid, the HUD SHALL show the next manoeuvre expected with its K, the laps flown since the last manoeuvre ended,
counting to 1½, and a warning while the plane flies level between manoeuvres outside 1 to 3 m above the ground. When a
manoeuvre is scored, a toast SHALL show its name and its mark × K, or 0 with its reason.

#### Scenario: After the take-off

- **WHEN** a Mid flight's Take-off is marked 8.5
- **THEN** a toast shows `Take-off 8.5 × 2 = 17.0`, and the HUD shows Reverse wingover (K 8) as next with the pause
  count starting from 0

#### Scenario: Too low between manoeuvres

- **WHEN** at Mid the plane flies level at 0.6 m between manoeuvres
- **THEN** the HUD warns that the plane is below 1 m

#### Scenario: Pro gives no guidance

- **WHEN** a Pro flight's Take-off is scored
- **THEN** no toast is shown and the HUD names no manoeuvre

### Requirement: Noob assist level

The assist level screen SHALL offer Noob after Pro and Mid, described as drawing the ideal track ahead. A Noob flight
SHALL have Mid's guidance (next manoeuvre with K, pause count, clock, height warning and mark toasts) and the Noob
guide. It SHALL be judged like Pro and Mid.

#### Scenario: Choosing Noob

- **WHEN** the player chooses F2B pattern, then Noob, then `Stunter`
- **THEN** a Noob flight starts with the take-off guide drawn and the HUD showing Take-off (K 2) next

### Requirement: Noob board and debrief

Noob flights SHALL be saved on the Noob board and shown on a Noob tab of the score table screen, after the Pro and Mid
tabs. GAME OVER of a Noob flight SHALL offer Debrief, and a Noob row SHALL open its debrief, as for Pro and Mid.

#### Scenario: A Noob flight on the board

- **WHEN** a Noob flight scores 845.20 and enters the Noob board
- **THEN** the Noob tab lists it highlighted, the Pro and Mid boards are unchanged, and its row opens its debrief

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

### Requirement: Settings screen

The main menu SHALL offer Settings, between Scores and Quit, which opens the settings screen. The settings screen SHALL have a Display section with the Fullscreen checkbox (`control-line-display`), and a Controller section: the connected devices listed by name (or a note that no controller was found), a way to choose one, and for the chosen device its handle axis with a live bar of the tilt the device sets, a Detect button for the handle axis, an Invert checkbox, a dead zone slider (0 to 50 %), a curve slider (linear to cubic), its pause button with a Detect button, and Reset, which restores the device's default profile. Changes SHALL apply at once and be saved. Back or Escape SHALL return to the main menu. The screen SHALL be operable with the keyboard, the mouse and a controller, and SHALL work with no device connected.

#### Scenario: Open settings

- **WHEN** the player chooses Settings on the main menu
- **THEN** the settings screen opens, with the Fullscreen checkbox and the connected devices listed

#### Scenario: No controller

- **WHEN** no device is connected
- **THEN** the settings screen says no controller was found, still offers Fullscreen, and Back returns to the main menu

#### Scenario: Live preview

- **WHEN** the player pushes the handle axis of the chosen device
- **THEN** the live bar follows the tilt the device would set, with its dead zone, invert and curve applied

#### Scenario: Change and leave

- **WHEN** the player turns Invert on and presses Back
- **THEN** the setting is saved and a flight with that device uses it

#### Scenario: Reset

- **WHEN** the player presses Reset for a gamepad
- **THEN** its profile is the gamepad default

#### Scenario: Only a controller

- **WHEN** a player uses only a gamepad
- **THEN** the pad's D-pad and confirm button move through the settings screen, change the sliders with Left and Right, and toggle Invert
