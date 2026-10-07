# Spec Delta

## Purpose

Lets a Control Line player study an F2B flight after it ends. The debrief replays each manoeuvre with the ideal track
the judge built and the track actually flown, shows what each mark was taken off for, and keeps saved flights
replayable from the F2B score boards.

## ADDED Requirements

### Requirement: The debrief screen

The debrief SHALL show the flight's 15-row score sheet with one manoeuvre selected, the first one at the start. It
SHALL show the field from the pilot's position facing the selected manoeuvre, and that manoeuvre's deductions. Up and
Down SHALL select the previous or next manoeuvre, and Escape SHALL return to the screen the debrief was opened from.

#### Scenario: Selecting the square loops

- **WHEN** the debrief of a Pro flight with `Stunter` is open and the player presses Down six times
- **THEN** Two inside square loops is selected and the view turns to face where they were flown

### Requirement: Ideal and flown tracks

For the selected manoeuvre, the debrief SHALL draw the ideal track the judge marked it against and the path the plane
flew from the start to the end of the manoeuvre, in two colours named in a legend. Across the sector of the figure it
SHALL draw the base and the 45° parallel as faint guides. A manoeuvre scored 0 because it was omitted SHALL show no
tracks and its reason.

#### Scenario: Inside loops

- **WHEN** Three inside loops is selected and was marked 7.4
- **THEN** the ideal three superimposed circles and the three flown loops are drawn, with the base and the 45°
  parallel across them

#### Scenario: Omitted

- **WHEN** Two laps of inverted flight is selected and was omitted
- **THEN** no track is drawn and the debrief says it was omitted

### Requirement: Replaying the plane

The debrief SHALL fly the plane along its recorded path and attitude through the selected manoeuvre, from its start to
its end, at 1× speed when selected. Space SHALL pause and resume it, R SHALL restart it, and 1, 2 and 3 SHALL set ½×,
1× and 2× speed. At the end the plane SHALL hold its last pose.

#### Scenario: Slow motion

- **WHEN** the selected manoeuvre lasted 12 s and the player presses 1
- **THEN** the plane takes 24 s to fly it, and pressing Space stops it where it is

### Requirement: Two views

The debrief SHALL offer the pilot's view and a view from outside the circle facing the selected manoeuvre, both framing
the whole figure. V SHALL switch between them. The view SHALL be kept when another manoeuvre is selected.

#### Scenario: Outside view

- **WHEN** the player presses V on the debrief of Two vertical eights
- **THEN** the view moves outside the circle, facing the eights, with both loops in view

### Requirement: What the mark was taken off for

For a marked manoeuvre, the debrief SHALL list each deduction it received, from largest to smallest. Each line shows
what was judged, what was measured, its tolerance and the points it cost. A manoeuvre marked 10 SHALL say it had no
deductions. A manoeuvre scored 0 SHALL show its reason instead.

#### Scenario: Low bottoms

- **WHEN** Three inside loops was marked 9.4 with only its bottoms 0.6 m above the tolerance
- **THEN** the debrief lists one line naming the base height, 0.6 m over ±0.30 m, and −0.6

#### Scenario: Too soon

- **WHEN** Three outside loops scored 0 with "too soon"
- **THEN** the debrief shows "too soon" and no deductions

### Requirement: Saved flights keep their recording

When an F2B flight enters a board, the game SHALL save its recording in
`~/.abyssus-control-line/f2b-flights/<id>.json.gz` and name it in the entry's `flight` field. The recording holds:

- the flown path at 30 samples a second, with the plane's attitude;
- per manoeuvre, its time span, ideal track, mark, deductions or zero reason.

A flight that enters no board SHALL NOT be saved.

#### Scenario: A Mid flight enters its board

- **WHEN** a Mid flight with `Stunter` enters the Mid board
- **THEN** its entry in `f2b-scores.json` has a `flight` id and `f2b-flights/<that id>.json.gz` holds its recording

#### Scenario: A flight that does not qualify

- **WHEN** an F2B flight scores below the lowest of a full board
- **THEN** no file is added to `f2b-flights/`

### Requirement: Recordings follow their boards

When a flight leaves its board, its recording SHALL be deleted. At start, a recording that no entry names SHALL be
deleted. Recordings SHALL be written to a temporary file first and then moved into place.

#### Scenario: Pushed off the board

- **WHEN** a new Pro flight enters a full Pro board and the tenth flight leaves it
- **THEN** the leaving flight's recording is deleted and the new one's is saved

### Requirement: A missing recording keeps the marks

When an entry has no `flight` field, or its recording is missing or cannot be read, the entry and its marks SHALL be
kept. Its debrief SHALL show the score sheet with the message that the replay is not available, and no tracks. A
recording that cannot be read SHALL be renamed with `.bad` added.

#### Scenario: A deleted recording

- **WHEN** the player deletes a recording file and opens that flight from the Pro tab
- **THEN** the debrief shows the flight's 15 marks and says the replay is not available
