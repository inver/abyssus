# Spec Delta

## Purpose

Lets players watch an autonomous control-line pilot fly the complete F2B schedule under the game's normal flight and judging rules, from stationary startup to landing in the circle.

## ADDED Requirements

### Requirement: Autonomous complete routine
Watch AI SHALL start the selected stunt plane stationary at its normal takeoff position with its engine running, then fly takeoff, all F2B figures in schedule order with their required repetitions and pauses, and landing without player flight input. The bundled unmodified Stunter SHALL complete all 15 manoeuvres with positive marks and land by 7:00.

#### Scenario: Complete bundled demonstration
- **WHEN** Watch AI starts Stunter in the bundled unmodified ControlLine field
- **THEN** the plane accelerates from rest, takes off, flies every scheduled figure and required pause, and lands by 7:00 with all 15 score-sheet rows positively marked

### Requirement: Landing in the circle
After the final airborne figure the bot SHALL hold controlled base flight until the engine stops under the normal fuel rule, then glide to an upright landing in the flying circle. For the bundled Stunter it SHALL land with its line ends (where the lines meet the plane, its leadouts) within its line-length radius from the pilot's ground position, allowing 0.5 m physical tolerance, and meet the normal landing criteria. The plane's centre of gravity lies outboard of its leadouts (0.75 m for the Stunter, about 18.7 m from the pilot with taut lines), so it is not the measured point.

#### Scenario: Fuel exhaustion and landing
- **WHEN** Stunter has completed the figures and reaches 360 seconds of flight
- **THEN** its engine stops normally and the bot glides to an upright touchdown with its line ends within 18.5 m horizontally of the pilot, with a Landed outcome by 7:00

### Requirement: Actual flight and independent judging
The bot SHALL act through the same handle authority and flight rules as a human. It SHALL NOT move the plane along a prerecorded or ideal path, override fuel, bypass line tension, inject judged samples or assign marks. The normal judge SHALL score the actual flight; a perfect score is not required.

#### Scenario: Imperfect figure
- **WHEN** the bot flies a recognizable figure with a geometry deviation
- **THEN** the normal deduction appears in the score sheet and debrief rather than a preassigned demonstration mark

### Requirement: Honest failure outside the baseline
Watch AI SHALL use the selected stunt plane's actual settings. A crash or slack-line failure SHALL end the attempt and show the real outcome and partial score sheet. The game SHALL NOT silently restart, teleport or report a completed routine. Full-routine success SHALL be guaranteed for the bundled unmodified Stunter, not every modified aircraft.

#### Scenario: Modified aircraft fails
- **WHEN** a selected modified stunt plane crashes during an AI flight
- **THEN** the attempt ends with the real crash reason, completed marks retained and remaining rows judged under the normal crash rules
