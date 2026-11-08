# Spec Delta

## ADDED Requirements

### Requirement: Leadouts are the plane's dummies

A plane's two leadouts SHALL be its child dummies named `leadoutUp` and `leadoutDown`, at their poses in the plane's
frame, so an author places and sees them in Abyssus. The lines SHALL be attached to those points, as they follow the
plane in flight. A plane that lacks either dummy SHALL NOT be offered for flight, and one warning SHALL name the plane
and the missing dummy. Each plane of `Field.scene` (`Racer`, `Stunter`, `Trainer`, `Mig-3`) SHALL have both dummies,
placed at the leadout values it flew with before this change.

#### Scenario: The trainer's leadouts

- **WHEN** Control Line loads `Field.scene`
- **THEN** the `Trainer`'s lines run to the points (0.48, 0, 0.02) and (0.48, 0, -0.04) of the plane's frame, as
  before

#### Scenario: Moving a leadout in Abyssus

- **WHEN** the `Trainer`'s `leadoutUp` dummy is moved in Abyssus to (0.5, 0, 0.02) in the plane's frame and the game
  is played
- **THEN** the up line runs to that new point

#### Scenario: A plane without leadouts

- **WHEN** a plane entity has no `leadoutDown` child dummy
- **THEN** it is not offered on plane select, and one warning names the plane and `leadoutDown`
