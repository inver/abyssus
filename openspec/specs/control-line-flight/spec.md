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
