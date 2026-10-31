# Spec Delta

## MODIFIED Requirements

### Requirement: Main menu

The game SHALL open on a main menu offering Free flight, F2B pattern, Scale, Scores and Quit, usable with the arrow keys
and Enter or with the mouse. Free flight SHALL be disabled, with a message, when the bundled field scene has no plane.
F2B pattern SHALL be disabled, with a message, when it has no plane of class `STUNT`. Scale SHALL be disabled, with a
message, when it has no plane of class `SCALE`.

#### Scenario: Start

- **WHEN** the player chooses Free flight
- **THEN** the plane select screen opens, offering every plane

#### Scenario: F2B pattern

- **WHEN** the player chooses F2B pattern
- **THEN** the assist level screen opens

#### Scenario: Scale

- **WHEN** the player chooses Scale on the bundled `ControlLine` field
- **THEN** the plane select screen opens, offering only `Mig-3`

#### Scenario: No planes

- **WHEN** the field scene holds no entity with a `PlaneComponent`
- **THEN** Free flight, F2B pattern and Scale are disabled and the menu says no plane was found in the field scene

#### Scenario: No stunt plane

- **WHEN** the field scene holds planes but none of class `STUNT`
- **THEN** F2B pattern is disabled and the menu says the F2B pattern needs a stunt plane

#### Scenario: No replica

- **WHEN** the field scene holds planes but none of class `SCALE`
- **THEN** Scale is disabled and the menu says Scale needs a scale replica

### Requirement: Plane select from the field scene

The plane select screen SHALL be a vitrine: every plane stands on a table of its own in a display row, and the camera
glides from table to table to the selected plane. It SHALL offer every entity of the field scene that has a
`PlaneComponent`, in the order of their names, showing on a card each plane's name, class, line length, mass and fuel
time. For a replica the card SHALL also show its prototype, first-flight year, real wingspan and model scale. For an F2B
pattern it SHALL offer only planes of class `STUNT`, and for Scale only planes of class `SCALE`. Left and right SHALL
change the plane, Enter SHALL start the flight with it, and Escape SHALL return to the screen before it. A plane SHALL
rest on the top of its table whatever the plane's size. A plane with no table left SHALL stand on the ground beside the
last table.

#### Scenario: Bundled planes

- **WHEN** the plane select screen opens for Free flight on the bundled `ControlLine` field
- **THEN** it offers `Mig-3`, `Racer`, `Stunter` and `Trainer` in that order, starting with `Mig-3`, each on its own table

#### Scenario: Bundled stunt planes

- **WHEN** the plane select screen opens for an F2B pattern on the bundled `ControlLine` field
- **THEN** it offers only `Stunter`, with a fuel time of 360 s

#### Scenario: Bundled replicas

- **WHEN** the plane select screen opens for Scale on the bundled `ControlLine` field
- **THEN** it offers only `Mig-3`, and its card shows the MiG-3 as its prototype

#### Scenario: A plane added in Abyssus

- **WHEN** a fifth plane entity with a `PlaneComponent` is added to the field scene in Abyssus and the game is started
  again
- **THEN** the plane select screen offers it too, with no code change, and it stands on the next free table

#### Scenario: More planes than tables

- **WHEN** the field scene holds more planes than display tables
- **THEN** the planes beyond the last table stand on the ground beside it, and plane select still offers them

#### Scenario: Gliding between tables

- **WHEN** the player presses right on plane select
- **THEN** the camera glides to the next table's plane and the card changes to that plane

#### Scenario: A plane on its table

- **WHEN** plane select shows any bundled plane
- **THEN** the plane's lowest point is at the table-top and the plane is not below or through it
