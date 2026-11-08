# Spec Delta

## ADDED Requirements

### Requirement: A child's position is shown as local

For an entity that has a valid parent, the `PositionComponent` section SHALL show and edit the stored local values and
SHALL carry a note "local to <parent name>" (naming the node too when the parent link has one). Edits SHALL write only
that entity. A root entity SHALL show no such note.

#### Scenario: A dummy's section

- **WHEN** a dummy under `Model 9` is selected
- **THEN** its `PositionComponent` section shows its local values and the note "local to" followed by `Model 9`'s name

#### Scenario: A root

- **WHEN** an entity without a parent is selected
- **THEN** its `PositionComponent` section has no local note
