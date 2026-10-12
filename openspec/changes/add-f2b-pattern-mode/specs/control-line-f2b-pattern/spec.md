# Spec Delta

## Purpose

Defines an F2B pattern flight in the Control Line game: the FAI F2B schedule and its K-factors, the rules that score a
manoeuvre 0, the flight score as the sum of mark times K, and the F2B score boards kept per assist level.

## ADDED Requirements

### Requirement: The F2B schedule

An F2B flight SHALL be scored on these 15 manoeuvres, in this order, with these K-factors (FAI Sporting Code Volume F2,
2027 edition, rule 4.2.12): Take-off 2, Reverse wingover 8, Three inside loops 6, Two laps of inverted flight 2, Three
outside loops 6, Two inside square loops 12, Two outside square loops 12, Two inside triangular loops 14, Two
horizontal eights 7, Two square horizontal eights 18, Two vertical eights 10, Hourglass 10, Two overhead eights 10,
Four-leaf clover 8, Landing 5.

#### Scenario: The score sheet lists the schedule

- **WHEN** an F2B flight with `Stunter` ends
- **THEN** its score sheet lists the 15 manoeuvres in this order, each with its K

#### Scenario: The best possible flight

- **WHEN** every manoeuvre of a flight is marked 10
- **THEN** the flight scores 1300.00

### Requirement: The flight score

Each manoeuvre SHALL score its mark, from 1 to 10 in steps of 0.1 or 0, times its K. The flight score SHALL be the sum
of the manoeuvres' scores, rounded down to two decimals, and SHALL be shown with two decimals.

#### Scenario: Summing a sheet

- **WHEN** Take-off is marked 8.5 and Reverse wingover 7.2, and every other manoeuvre scores 0
- **THEN** the flight scores 74.60 (8.5 × 2 + 7.2 × 8)

### Requirement: Manoeuvres in order

The judge SHALL expect the manoeuvres in schedule order. When the pilot flies a manoeuvre that comes later in the
schedule than the one expected, every manoeuvre skipped SHALL score 0 as omitted, and the one flown SHALL be judged. A
manoeuvre the schedule has already passed SHALL NOT be scored when flown again.

#### Scenario: Inverted laps left out

- **WHEN** the pilot finishes the inside loops and then flies three outside loops without the two inverted laps
- **THEN** Two laps of inverted flight scores 0 as omitted and the outside loops are marked

#### Scenario: A loop flown again later

- **WHEN** the pilot flies another inside loop after the inside square loops were marked
- **THEN** no score on the sheet changes

### Requirement: Whole manoeuvres only

A manoeuvre started but not completed, or flown with too few or too many repeat figures, SHALL score 0, with the reason
"not completed" or "wrong number of repeats". The schedule SHALL then move on to the next manoeuvre.

#### Scenario: A third square loop

- **WHEN** the pilot flies three inside square loops where two are expected
- **THEN** Two inside square loops scores 0 with "wrong number of repeats", and Two outside square loops is expected next

#### Scenario: A square loop given up

- **WHEN** the pilot climbs into the first inside square loop and levels off along the 45° parallel without diving back
- **THEN** Two inside square loops scores 0 with "not completed"

### Requirement: A pause between manoeuvres

A manoeuvre SHALL score 0 with the reason "too soon" when it starts less than 1½ laps after the previous manoeuvre
ended. That pause includes the recommended entry and exit of each manoeuvre.

#### Scenario: Outside loops straight after the inverted laps

- **WHEN** the pilot starts the outside loops 1 lap after the end of the inverted laps
- **THEN** Three outside loops scores 0 with "too soon"

### Requirement: Seven minutes of flight time

An F2B flight's time SHALL start when the plane begins its take-off ground roll and SHALL be shown counting up to 7:00.
A manoeuvre that ends after 7:00 SHALL score 0 with the reason "after 7:00". The flight SHALL go on after 7:00 until it
ends as a landing or a crash.

#### Scenario: The clover runs late

- **WHEN** the four-leaf clover ends at 7:02
- **THEN** Four-leaf clover scores 0 with "after 7:00", and the manoeuvres before it keep their marks

### Requirement: A crash ends the scoring

When an F2B flight ends as a crash, including lines that went slack, every manoeuvre completed before it SHALL keep its
mark. The manoeuvre in progress and every one after it SHALL score 0 with the reason "crashed".

#### Scenario: Crash in the triangles

- **WHEN** `Stunter` crashes during the second inside triangular loop
- **THEN** every manoeuvre up to Two outside square loops keeps its mark, and Two inside triangular loops through
  Landing score 0 with "crashed"

### Requirement: The landing

The Landing SHALL be judged only from level flight at the base with the engine stopped, until the flight ends as a
landing. It SHALL score 0 when the flight ends after 7:00, with the reason "after 7:00", or as a crash. Flying between
the end of the four-leaf clover and the start of the landing SHALL NOT be judged.

#### Scenario: A loop while the fuel runs out

- **WHEN** the pilot flies an inside loop after the four-leaf clover, the engine then stops, and the plane lands at 6:40
- **THEN** the loop changes no score and the Landing is marked

#### Scenario: Landing too late

- **WHEN** the plane touches down and the flight ends as a landing at 7:05
- **THEN** Landing scores 0 with "after 7:00"

### Requirement: Fuel for a pattern

The bundled `Stunter` SHALL have a fuel time of 360 s, so that its engine stops after the schedule and before 7:00, and
the landing can be flown with the engine stopped.

#### Scenario: Stunter's engine

- **WHEN** `Stunter` has flown for 360 s
- **THEN** its thrust is 0 from then on

### Requirement: F2B score boards

The game SHALL keep `~/.abyssus-control-line/f2b-scores.json` with one board of the 10 best F2B flights per assist
level. Each flight SHALL have name, plane, score, flight time, date and its 15 marks. A board SHALL rank best score
first, earlier flights first among equal scores. A flight SHALL enter its level's board when that board has fewer than
10 flights or the flight beats its lowest score.

#### Scenario: A Mid flight

- **WHEN** a Mid flight scores 612.40 and the Mid board holds 3 flights
- **THEN** the flight enters the Mid board with its 15 marks, and the Pro board is unchanged

#### Scenario: Free flight is separate

- **WHEN** an F2B flight is saved
- **THEN** `~/.abyssus-control-line/scores.json` is unchanged

### Requirement: A damaged F2B score file does not stop the game

An `f2b-scores.json` that cannot be read SHALL be renamed to `f2b-scores.json.bad` (replacing an older one), its boards
SHALL start empty, and the F2B tabs of the score table screen SHALL say the old F2B scores could not be read.

#### Scenario: Not JSON

- **WHEN** `f2b-scores.json` holds text that is not JSON and the game starts
- **THEN** the file becomes `f2b-scores.json.bad`, the F2B boards are empty, the F2B tabs say the old scores could not
  be read, and the Free flight table is unaffected
