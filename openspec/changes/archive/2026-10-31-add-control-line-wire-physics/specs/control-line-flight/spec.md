## ADDED Requirements

### Requirement: The lines are 0.5 mm steel wire

Each plane's lines SHALL be steel wire 0.5 mm in diameter (density 7850 kg/m³, Young's modulus 200 GPa). The diameter
SHALL come from the plane's line diameter setting, and the line drag SHALL use it.

#### Scenario: Bundled planes use 0.5 mm

- **WHEN** the bundled `ControlLine` field is loaded
- **THEN** every plane's line diameter is 0.0005 m

### Requirement: The wire loads the plane

The wire's mass SHALL act on the plane: each line SHALL add to the plane's effective mass one third of its own mass
(`7850 * pi * d² / 4 * L / 3`), the inertia of a wire swinging about the handle.

#### Scenario: Trainer is heavier by its wire

- **WHEN** a flight starts with `Trainer` (15 m lines)
- **THEN** the wire adds about 15.4 g to the plane's effective mass, as a force that opposes its acceleration

### Requirement: The wire stretches

Under tension `T` a line SHALL be longer than its rest length `L` by `T * L / (E * A)`. Slack lines SHALL be exactly
their rest length at most.

#### Scenario: Stretch under load

- **WHEN** `Trainer` flies level laps with a combined tension of 30 N
- **THEN** each line is between 4 mm and 12 mm longer than its 15 m rest length

### Requirement: The wire's shape follows the loads on it

Each drawn line SHALL be a curve from the handle to the leadout shaped by the line's tension, its weight and its air drag.
While tight it SHALL lie within 20 cm of the straight line between its ends, bowed back
against the flight direction. While slack it SHALL hang below the straight line, by more as the tension falls.

#### Scenario: Taut line is nearly straight

- **WHEN** `Trainer` flies level laps with the lines taut
- **THEN** each drawn line stays within 20 cm of the straight line from the handle to its leadout and its mid point lies behind that line against the flight direction

#### Scenario: Neither line hangs like thread while pulling

- **WHEN** the lines' combined tension is at least 1 N, whichever line carries less of it
- **THEN** both drawn lines stay within 30 cm of the straight line between their ends

#### Scenario: Slack line drapes

- **WHEN** the lines' tension is below 1 N with the plane in the air
- **THEN** the mid point of each drawn line is below the straight line between its ends

### Requirement: The wire is drawn at its true thickness

The lines SHALL be drawn 0.5 mm thick in world space. When that is under one pixel on screen they SHALL be drawn one
pixel wide with opacity scaled by the true width over the pixel's size, and sunlit wire SHALL glint.

#### Scenario: A distant wire is faint

- **WHEN** a line is 15 m from the camera at a 1080p window with a 67° field of view
- **THEN** it is drawn one pixel wide with opacity below 0.2
