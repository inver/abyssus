# Spec Delta

## Purpose

Lets a player fly scale replicas of real aircraft in Control Line and scores how realistically they fly: the replica
plane class and the prototype facts it carries, the realism marks of a Scale flight, and the Scale score table.

## ADDED Requirements

### Requirement: Replica planes

A plane of class `SCALE` SHALL be a replica of a real aircraft. Its entity SHALL carry a `ScaleComponent` naming the
prototype, the year of its first flight, its real wingspan in metres and the model scale as a denominator (8 for 1:8).
A `SCALE` plane without a `ScaleComponent` SHALL still be flown in Scale, with its prototype facts shown as unknown. The
bundled `ControlLine` field SHALL hold one replica, `Mig-3`, whose prototype is the Mikoyan-Gurevich MiG-3.

#### Scenario: The bundled replica

- **WHEN** the game loads the bundled `ControlLine` field scene
- **THEN** `Mig-3` is a plane of class `SCALE` with a `ScaleComponent` naming the MiG-3

#### Scenario: A replica added in Abyssus

- **WHEN** a plane entity of class `SCALE` is added to the field scene in Abyssus and the game is started again
- **THEN** Scale offers it, with no code change

#### Scenario: No prototype data

- **WHEN** a `SCALE` plane has no `ScaleComponent`
- **THEN** Scale offers it and its card shows the prototype facts as unknown

### Requirement: Scale flights

A Scale flight SHALL use the Free flight physics, line rig and controls with the chosen replica, and SHALL end as a
landing or a crash as a Free flight does. It SHALL be scored by the realism marks below and SHALL NOT score laps,
maneuvers, combo or the landing bonus of Free flight. The flying HUD SHALL show line tension, fuel left and the realism
score so far.

#### Scenario: Loops do not score

- **WHEN** a replica flies an inside loop in Scale
- **THEN** the loop adds no points

### Requirement: Realism marks

A Scale flight SHALL score up to 1000 points from three marks, each measured over the laps the plane flies while
airborne:
- **Steady speed**, up to 400 points: 400 when the plane's lap times differ from their mean by no more than 5 %, falling
  linearly to 0 at 30 %.
- **Level laps**, up to 400 points: 400 when the mean absolute elevation of the plane's airborne laps is at most 15°,
  falling linearly to 0 at 45°.
- **Clean landing**, up to 200 points: 200 for a flight that ends as a landing, 0 for a crash.

A flight with fewer than 2 laps SHALL score 0 for steady speed and level laps. A crash SHALL keep the speed and level
marks earned until the crash and add no landing mark.

#### Scenario: A steady level flight

- **WHEN** a replica flies five laps with lap times within 3 % of their mean, has a mean absolute elevation of 10°, and lands
- **THEN** it scores 400 for steady speed, 400 for level laps and 200 for the landing, 1000 in all

#### Scenario: Racing and stalling

- **WHEN** a replica's lap times differ from their mean by 30 % or more
- **THEN** steady speed scores 0

#### Scenario: A crash after good laps

- **WHEN** a replica flies four steady, level laps and then crashes
- **THEN** it keeps the steady speed and level marks and scores 0 for the landing

#### Scenario: Too few laps

- **WHEN** a replica lands after one lap
- **THEN** only the landing mark scores, 200 in all

### Requirement: Scale score table

The game SHALL keep the 10 best Scale flights in `~/.abyssus-control-line/scale-scores.json`, each with name, plane,
prototype, score, its three marks, laps, flight time and date, best score first, and earlier flights first among equal
scores. A flight SHALL enter the table when it beats the lowest entry or the table has fewer than 10. A file that cannot
be read SHALL be renamed to `scale-scores.json.bad` and the table start empty. Scale scores SHALL NOT mix with Free
flight or F2B scores.

#### Scenario: A full table

- **WHEN** a Scale flight scores 640 and the table holds 10 entries, the lowest 700
- **THEN** it does not enter the table

#### Scenario: Separate tables

- **WHEN** a Scale flight enters its table
- **THEN** `scores.json` and `f2b-scores.json` are unchanged

#### Scenario: An unreadable table

- **WHEN** `scale-scores.json` holds text that is not valid JSON at start
- **THEN** it is renamed to `scale-scores.json.bad` and the Scale table is empty
