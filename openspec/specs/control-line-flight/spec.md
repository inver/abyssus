# control-line-flight Specification

## Purpose

Makes a control-line plane fly as one does: tethered by two lines to a handle the pilot holds, controlled only
through those lines, and able to lose control when they go slack.

## Requirements

### Requirement: Takeoff on the lines

A flight SHALL start with the chosen plane on the ground at its line length from the pilot, pointing along the
circle, its lines attached from the handle to its leadouts and taut, and its engine running at full thrust. With the
handle neutral, the plane SHALL accelerate along the circle and lift off.

#### Scenario: Trainer takes off

- **WHEN** a flight starts with `Trainer` of the bundled `ControlLine` field and the handle is held neutral
- **THEN** within 5 seconds `Trainer` is more than 1 m above the ground and still on its lines

### Requirement: The pilot turns with the plane

The handle SHALL stay at the pilot's position and handle height and turn to face the plane every step, so the lines
lead from the handle to the plane.

#### Scenario: Half a lap

- **WHEN** `Trainer` has flown half a lap
- **THEN** the handle faces the plane's new direction, and both lines run from the handle to the plane's leadouts

### Requirement: Control only through taut lines

The handle SHALL tilt between full down and full up. The plane's elevator SHALL follow the handle while the lines'
combined tension is at least 1 N, and SHALL return to neutral while it is below 1 N. The elevator SHALL pitch the plane
up for handle up and down for handle down.

#### Scenario: Full up loops the plane

- **WHEN** `Stunter` is in level flight at its cruising speed and the handle is held full up
- **THEN** within one lap the plane's climb angle passes vertical and the plane comes over the top inside the circle

#### Scenario: Slack lines give no control

- **WHEN** the lines' tension is below 1 N and the handle is moved from neutral to full up
- **THEN** the plane's elevator stays neutral until the tension is back at 1 N or more

### Requirement: Line tension is shown and follows the flight

The game SHALL report the lines' combined tension each step. In steady level flight it SHALL be within 30% of the
plane's mass times its speed squared over the line length.

#### Scenario: Level flight tension

- **WHEN** `Trainer` flies level laps at a steady speed
- **THEN** the reported tension is within 30% of `m * v^2 / L` for its mass, speed and line length

### Requirement: Aerodynamic forces

Each step, the plane SHALL receive thrust along its nose while it has fuel, lift and drag from its airspeed and angle
of attack, the elevator's pitching moment, pitch damping and the drag of its lines, from the values its
`PlaneComponent` holds. Changing those values in the scene SHALL change the flight without changing code.

#### Scenario: More thrust, more speed

- **WHEN** `Trainer`'s thrust is doubled in the scene and it flies level laps
- **THEN** its steady speed is higher than with the original thrust

### Requirement: Fuel runs out

The engine SHALL stop after the plane's fuel time, and the plane SHALL then glide on its lines.

#### Scenario: Engine stops

- **WHEN** `Racer` (fuel time 60 s) has flown for 60 seconds
- **THEN** its thrust is 0 from then on

### Requirement: Crash and landing

A flight SHALL end as a crash when the plane touches the ground faster than 3 m/s or upside down, or when the lines'
tension stays below 1 N for 2 seconds while airborne. After the engine has stopped, a touch-down upright at 3 m/s or
slower SHALL end the flight as a landing. While the engine runs, an upright touch at 3 m/s or slower SHALL NOT end the
flight.

#### Scenario: Diving into the ground

- **WHEN** `Stunter` is held full down from level flight
- **THEN** it hits the ground faster than 3 m/s and the flight ends as a crash

#### Scenario: Gentle landing

- **WHEN** the engine has stopped and `Trainer` glides onto the ground upright at 2 m/s
- **THEN** the flight ends as a landing

#### Scenario: Slack too long

- **WHEN** the lines stay below 1 N for 2 seconds while the plane is in the air
- **THEN** the flight ends as a crash with the reason "lines went slack"

### Requirement: Crash severity

A flight that ends as a crash because the plane touched the ground SHALL record its impact speed: how fast the plane
came down onto the ground just before the touch. It SHALL record a severity from that speed: little below 6 m/s, medium
from 6 m/s and below 10 m/s, full from 10 m/s. A flight that lands, or ends because the lines went slack, SHALL have no
severity.

#### Scenario: A hard touch breaks the gear

- **WHEN** `Trainer` comes down onto the ground upright at 4.5 m/s
- **THEN** the flight ends as a crash with a little severity

#### Scenario: A harder touch breaks the wing and the tail

- **WHEN** `Trainer` comes down onto the ground upright at 8 m/s
- **THEN** the flight ends as a crash with a medium severity

#### Scenario: A dive wrecks the plane

- **WHEN** `Trainer` comes down onto the ground at 14 m/s
- **THEN** the flight ends as a crash with a full severity

#### Scenario: A landing has no severity

- **WHEN** the engine has stopped and `Trainer` glides onto the ground upright at 2 m/s
- **THEN** the flight ends as a landing with no severity

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
