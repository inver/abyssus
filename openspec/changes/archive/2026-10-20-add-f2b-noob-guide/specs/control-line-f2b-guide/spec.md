# Spec Delta

## Purpose

Defines the Noob guide of the Control Line game's F2B pattern: the ideal track of the next manoeuvre drawn in the 3D
view during the flight, where and when it appears, and how it follows the pilot's progress, so a beginner can fly the
schedule by following it.

## ADDED Requirements

### Requirement: The next manoeuvre is drawn ahead

During a Noob flight, once the 1½-lap pause after the previous manoeuvre is complete, the game SHALL draw the ideal
track of the next manoeuvre expected. Its start SHALL be a quarter lap ahead of where the plane was at that moment,
with a start gate there: a vertical bar from the ground to 1 m above the base. Only Noob flights SHALL show it.

#### Scenario: Loops after the wingover

- **WHEN** at Noob the plane has flown 1½ laps since the reverse wingover ended
- **THEN** three superimposed loops between the base and the 45° parallel appear, starting a quarter lap ahead of the
  plane, with the start gate there

#### Scenario: Mid draws nothing

- **WHEN** a Mid flight reaches the same point
- **THEN** no track is drawn

### Requirement: The guide waits for the pilot

The guide SHALL stay where it was placed until the manoeuvre is scored. A pilot who passes the gate without starting
SHALL find the same track at the same place on the next lap.

#### Scenario: Not ready

- **WHEN** the pilot flies past the start gate of the square loops in level flight
- **THEN** the track stays where it is, and the pilot can start the squares there on the next lap

### Requirement: Progress along the guide

Once the judge sees the drawn manoeuvre start, the part of its track up to the point nearest the plane SHALL be drawn
dimmed, and the part ahead bright. When the manoeuvre is scored, by a mark or by 0 for any reason, its track SHALL
disappear, and the next one SHALL appear after the next 1½-lap pause.

#### Scenario: Halfway through the loops

- **WHEN** the plane is at the top of the second of three inside loops
- **THEN** the first loop and the climb of the second are dimmed, and the rest is bright

#### Scenario: Skipping ahead

- **WHEN** the pilot ignores the drawn inverted laps and flies outside loops, which the judge scores
- **THEN** the inverted laps' track disappears, and after the next pause the inside square loops are drawn

### Requirement: Take-off and landing guides

At the start of a Noob flight, the take-off's climb and its two laps at the base SHALL be drawn from the release
point. After the four-leaf clover is scored and the engine has stopped, the landing's one-lap glide from the base to
the ground SHALL be drawn, starting a quarter lap ahead of the plane.

#### Scenario: Engine out

- **WHEN** at Noob the four-leaf clover has been scored and the engine stops
- **THEN** a one-lap spiral from the base down to the ground appears, starting a quarter lap ahead

### Requirement: Reference rings

During a Noob flight the game SHALL draw, around the whole circle, a faint ring at the base and a fainter ring along
the 45° parallel.

#### Scenario: Rings from the start

- **WHEN** a Noob flight with `Stunter` starts
- **THEN** the base ring at 1.5 m and the 45° ring are drawn all around the circle, behind the take-off guide
