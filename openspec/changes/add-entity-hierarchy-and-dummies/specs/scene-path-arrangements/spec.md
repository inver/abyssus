# Spec Delta

## MODIFIED Requirements

### Requirement: Hand edits detach a copy

Moving or turning a copy with the gizmo, dropping it, or changing its `PositionComponent` in the Properties panel SHALL
detach it in the same edit: its `ParentComponent` is removed, its `PositionComponent` is written as its world pose so
the copy does not move, and its slot in `slots` becomes `-1`. A detached copy is a normal entity that regeneration
leaves alone. Other edits to a copy SHALL NOT detach it.

#### Scenario: Move a streetlight off a driveway

- **WHEN** the user moves copy `14` (slot `2` of `Path 11`) along Z and releases
- **THEN** copy `14` has its new world position and no `ParentComponent`, and `slots` of `Path 11` is `[12, 13, -1, 15, 16]`

#### Scenario: A rename does not detach

- **WHEN** the user renames copy `13`
- **THEN** copy `13` keeps its `ParentComponent` and its slot

#### Scenario: Detached copies stay put

- **WHEN** copy `14` was detached and the user then drags the second point of `Path 11`
- **THEN** copy `14` is unchanged and slot `2` stays `-1`

## ADDED Requirements

### Requirement: An arrangement's points and copies are local to it

An arrangement's `points` and its linked copies' `PositionComponent`s SHALL be values in the arrangement entity's own
frame, like any child. An arrangement without a `PositionComponent` sits at the origin unturned and unscaled, so its
points and copies are also world values. Drawing a new arrangement, editing its control points and regenerating SHALL
write points and copies in that frame. Ground heights SHALL be taken from the terrain at the copies' world positions.

#### Scenario: Existing arrangements are unchanged

- **WHEN** Control Line's `Field.scene` is opened, whose arrangements `Railway`, `Street lamps` and `Fence` have no
  `PositionComponent`
- **THEN** every copy is drawn where it was drawn before this change, and Regenerate leaves the file unchanged
