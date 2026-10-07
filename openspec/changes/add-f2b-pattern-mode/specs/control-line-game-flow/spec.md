# Spec Delta

## MODIFIED Requirements

### Requirement: Main menu

The game SHALL open on a main menu offering Free flight, F2B pattern, Scores and Quit, usable with the arrow keys and
Enter or with the mouse. Free flight SHALL be disabled, with a message, when the bundled field scene has no plane. F2B
pattern SHALL be disabled, with a message, when it has no plane of class `STUNT`.

#### Scenario: Start

- **WHEN** the player chooses Free flight
- **THEN** the plane select screen opens, offering every plane

#### Scenario: F2B pattern

- **WHEN** the player chooses F2B pattern
- **THEN** the assist level screen opens

#### Scenario: No planes

- **WHEN** the field scene holds no entity with a `PlaneComponent`
- **THEN** Free flight and F2B pattern are disabled and the menu says no plane was found in the field scene

#### Scenario: No stunt plane

- **WHEN** the field scene holds planes but none of class `STUNT`
- **THEN** F2B pattern is disabled and the menu says the F2B pattern needs a stunt plane

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

The flight screen SHALL show the scene from the pilot's eyes, turning to follow the plane, and a HUD. In Free flight the
HUD SHALL show line tension, laps, score, combo multiplier and fuel left. In an F2B pattern it SHALL show line tension,
fuel left and the flight time against 7:00, plus what the assist level adds. W or Up SHALL tilt the handle up and S or
Down down, reaching full tilt in 0.15 s and returning to neutral when released; moving the mouse vertically SHALL set
the tilt by its distance from the window's centre, and whichever was used last SHALL control the handle. Escape SHALL
pause, offering Resume and Main menu.

#### Scenario: Pause

- **WHEN** the player presses Escape while flying
- **THEN** the flight stops advancing and the pause menu offers Resume and Main menu

#### Scenario: A Pro HUD

- **WHEN** an F2B flight at Pro has flown 1:12
- **THEN** the HUD shows tension, fuel left and `1:12 / 7:00`, and no manoeuvre names, laps or marks

### Requirement: GAME OVER

When a flight ends, the game SHALL show GAME OVER with the reason (crashed, lines went slack or landed). For Free flight
it SHALL show the score, laps, best combo and flight time. For an F2B pattern it SHALL show the assist level, the score
sheet (each manoeuvre's mark, K and points, or 0 with its reason), the score and the flight time. When the score enters
its table or board, it SHALL first ask for a name of up to 12 characters, offering the last name entered. It SHALL then
offer Retry, Scores and Main menu.

#### Scenario: Crash with a high score

- **WHEN** a Free flight with `Stunter` crashes with a score that enters the table
- **THEN** GAME OVER shows "Crashed" and the score, asks for a name, saves the flight, then offers Retry, Scores and
  Main menu

#### Scenario: F2B score sheet

- **WHEN** a Mid F2B flight lands after the take-off was marked 8.5 and every other manoeuvre scored 0
- **THEN** GAME OVER shows "Landed", the 15 manoeuvres with Take-off `8.5 × 2 = 17.0` and the others 0 with their
  reasons, and the score 17.00

### Requirement: Retry flies the same plane again

Retry SHALL start a new flight with the same plane, in the same mode and assist level, straight away, from takeoff,
with the score, laps, combo, clock and score sheet reset.

#### Scenario: Retry

- **WHEN** the player chooses Retry after a Free flight with `Stunter`
- **THEN** a new Free flight with `Stunter` starts from takeoff with score 0

#### Scenario: Retry an F2B pattern

- **WHEN** the player chooses Retry after a Pro F2B flight with `Stunter`
- **THEN** a new Pro F2B flight with `Stunter` starts from takeoff, its clock at 0:00 and Take-off expected

### Requirement: Score table screen

The score table screen SHALL have a tab for Free flight and one for each offered F2B assist level, Pro and Mid. Free
flight SHALL list its 10 best flights with rank, name, plane, score, laps, best combo, flight time and date. An F2B tab
SHALL list its level's 10 best flights with rank, name, plane, score with two decimals, flight time and date. Left and
right SHALL change the tab. The screen SHALL open on the tab of the flight just saved, highlight it, and return with
Escape or Back to the screen it was opened from.

#### Scenario: After saving

- **WHEN** the player chooses Scores on GAME OVER after saving a Free flight that ranks second
- **THEN** the Free flight tab shows that flight in second place, highlighted, and Back returns to GAME OVER

#### Scenario: After saving an F2B flight

- **WHEN** the player chooses Scores on GAME OVER after saving a Pro F2B flight that ranks first
- **THEN** the Pro tab is shown with that flight first, highlighted

## ADDED Requirements

### Requirement: Assist level

After F2B pattern, the game SHALL offer the assist levels Pro and Mid, each with a one-line description, usable with
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
