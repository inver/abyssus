# Spec Delta

## Purpose

Defines the Instructor of the Control Line game. When it is on, it warns a beginner with a ghost handle and takes the
handle near a crash to fly the plane back to safety. It also defines which instructed flights still count for the score
tables.

## ADDED Requirements

### Requirement: Instructor toggle

Plane select SHALL offer an Instructor toggle in every mode: Free flight, F2B pattern at every assist level, and Scale.
It SHALL be operable by the I key, a mouse click and a controller (by focusing it and confirming). It SHALL be off when
the game starts. It SHALL keep its state for the rest of the game session, across modes and visits to plane select. A
flight started with it on is an instructed flight. Retry SHALL start the same kind of flight again, with the instructor
on or off as before. Watch AI SHALL ignore the toggle. The toggle SHALL NOT be saved between game sessions.

#### Scenario: Turning it on

- **WHEN** the player presses I on plane select for Free flight with `Trainer` shown, then Enter
- **THEN** the toggle shows on, and the flight with `Trainer` starts as an instructed flight

#### Scenario: Kept across modes

- **WHEN** the player turns the instructor on in Free flight, returns to the main menu and opens F2B pattern, Noob
- **THEN** the toggle on plane select is still on

#### Scenario: Watch AI

- **WHEN** the instructor is on and the player chooses Watch AI with `Stunter`
- **THEN** the AI demonstration starts as before, with no instructor, ghost handle or INSTRUCTOR flag

#### Scenario: A new game session

- **WHEN** the player turned the instructor on, quits and starts the game again
- **THEN** the toggle is off

### Requirement: Danger

During an instructed flight, the game SHALL measure the danger of the plane's state every simulation step, from 0 to 1.
It SHALL be the largest of these three:

- **Ground:** the pull-out margin is the plane's height above the ground, minus the height it would lose pulling out of
  its present descent at its tightest turn short of the stall, minus the height it sinks in 0.15 s. Ground danger is 0
  at a margin of 1.2 m or more, 1 at 0.4 m or less, and linear between them. It is 0 while the plane climbs, and while
  it is upright and sinks slower than 1.5 m/s, so a landing is not a danger.
- **Inverted:** while the plane is upside down, danger is 0 at 0.9 m or more above the ground, 1 at 0.4 m or less, and
  linear between them.
- **Slack:** while the plane is in the air with lines below the control tension, danger is 0 for the first 0.4 s, rises
  linearly to 1 at 1.2 s, and is 0 again once the lines are taut.

Danger SHALL be 0 on the ground and below the airspeed at which the elevator steers the plane.

#### Scenario: Level at the base

- **WHEN** an instructed flight flies level laps upright or inverted at the 1.5 m base
- **THEN** the danger stays 0

#### Scenario: A dive at the base

- **WHEN** an instructed flight at the base pushes the handle full down
- **THEN** the danger rises above 0 before the plane is 1.2 m above the ground

#### Scenario: A figure that dives at the plane's limit

- **WHEN** a flight dives straight at the ground and pulls out only at the plane's limit, as the Watch AI bot does in its
  vertical figures
- **THEN** the danger rises above 0 during the dive, and the instructor may take over, which makes the flight Assisted

#### Scenario: Gentle landing

- **WHEN** an instructed flight glides down upright with the engine stopped, sinking at 1 m/s
- **THEN** the danger stays 0 down to the touchdown

### Requirement: Ghost handle

The HUD of an instructed flight SHALL show the player's handle tilt as a marker. While the danger is above 0, or while
the instructor holds any share of the handle, it SHALL also show a ghost marker at the tilt the instructor would set,
with the cue `Up!` when that tilt is more than 0.2 above the player's, or `Down!` when it is more than 0.2 below.
Otherwise the ghost marker and cue SHALL be hidden. A flight that is not instructed SHALL show neither marker.

#### Scenario: A warning before the takeover

- **WHEN** a student dives from the base and the danger first rises above 0 while the instructor holds less than half
  the handle
- **THEN** the ghost marker shows above the player's marker with `Up!`, and no INSTRUCTOR flag is shown

#### Scenario: Safe flight

- **WHEN** an instructed flight flies level at the base
- **THEN** only the player's marker is shown

### Requirement: Takeover and handback

The instructor SHALL hold a share of the handle from 0 to 1. The handle's tilt SHALL be the player's tilt times
(1 - share) plus the instructor's tilt times share. The player's keys, mouse and controller SHALL move only the player's
part. When the danger is above the share, the share SHALL rise to the danger at once. After the danger has stayed 0 for
1 s, the share SHALL fall to 0 over 0.5 s, and SHALL rise again if the danger rises above it.

While it holds a share, the instructor SHALL fly the plane back to level flight at the base, upright or inverted as the
plane flies. Once the engine has stopped, it SHALL turn an inverted plane upright and fly the landing glide. It SHALL act
only through the handle, under the same flight rules as the player. It SHALL NOT move the plane, override fuel or line
tension, or change the judging.

A takeover SHALL begin each time the share reaches 0.5 and end when it falls below 0.5. The HUD SHALL show INSTRUCTOR
during a takeover. Pausing SHALL freeze the danger, the share and the handback timer with the flight.

#### Scenario: Saved from a dive

- **WHEN** an instructed Free flight of any bundled plane (`Mig-3`, `Racer`, `Stunter` or `Trainer`) flies two laps
  level at the base and the player then holds the handle full down for 3 s
- **THEN** the instructor takes over, the plane does not hit the ground, and INSTRUCTOR is shown during the takeover

#### Scenario: Handing back

- **WHEN** the student releases the handle after the instructor has saved the plane and the plane flies level at the
  base
- **THEN** INSTRUCTOR goes away, and 1.5 s after the danger reached 0 the player holds the whole handle again

#### Scenario: Wrestling the handle

- **WHEN** the student keeps holding full down while the instructor holds the whole handle
- **THEN** the handle follows the instructor alone until the share falls

#### Scenario: Pause

- **WHEN** the player pauses during a takeover and resumes later
- **THEN** the share and INSTRUCTOR are as they were at the pause

### Requirement: Untouched flights count

An instructed flight with no takeover SHALL be scored, offered name entry, and saved in its score table or board like any
human flight. Its F2B recording SHALL be saved under the same rule. An instructed flight with one or more takeovers SHALL
be Assisted. Its GAME OVER SHALL show the real result of the flight, including its score or score sheet. It SHALL show
how many times the instructor took over and that the flight was not saved. It SHALL NOT offer name entry. It SHALL NOT
write any score table, board or board recording, whatever its score. A share below 0.5 that never reached a takeover
SHALL NOT make a flight Assisted. GAME OVER of an instructed flight with no takeover SHALL say the instructor was not
needed.

#### Scenario: Never needed

- **WHEN** an instructed F2B Noob flight with `Stunter` scores 845.20 with no takeover
- **THEN** GAME OVER says the instructor was not needed, the name entry opens, and the flight enters the Noob board

#### Scenario: Assisted flight with a top score

- **WHEN** an instructed Free flight ends with a score above every entry of the free-flight table after two takeovers
- **THEN** GAME OVER shows the score and that the instructor took over 2 times and the flight was not saved, no name
  entry opens, and the score file is unchanged

#### Scenario: A light touch

- **WHEN** during an instructed flight the share once reaches 0.3 and falls back to 0
- **THEN** the flight is not Assisted

### Requirement: Takeovers in the debrief

The debrief of an Assisted F2B flight SHALL draw the parts of the flown path that the plane flew during a takeover in a
third colour, named Instructor in the legend, alongside the ideal and flown tracks. For a manoeuvre that a takeover
overlapped, the debrief SHALL say the instructor took over during it, above its deductions or zero reason. The marks and
deductions SHALL be those the judge gave. A debrief of a flight with no takeover SHALL show no Instructor colour or note.

#### Scenario: A takeover in the loops

- **WHEN** the debrief of an Assisted Mid flight selects the inside loops, during which the instructor took over once
- **THEN** the part of the flown path during the takeover is drawn in the Instructor colour, the legend names it, and the
  loops say the instructor took over during them

#### Scenario: A board flight

- **WHEN** a debrief is opened from a board row
- **THEN** it shows no Instructor colour and no instructor note
