# Spec Delta

## Purpose

Shows the physics of a scene in the Scene view (collider shapes and constraints) so a user can see what they authored
before and while it runs.

## ADDED Requirements

### Requirement: Colliders are drawn

With Abyssus Physics installed and "Show Physics" on, the Scene view SHALL draw each collider as a wireframe of its
shape, at the entity's pose and the collider's offset: green for dynamic, blue for kinematic, grey for static. A height
field SHALL be drawn as its terrain's outline. "Show Physics" SHALL be off when a Scene view opens.

#### Scenario: Box on Model 0

- **WHEN** `Model 0` of the `Physics` scene has a dynamic box with half extents `0.5, 0.5, 0.5` and Show Physics is on
- **THEN** a green 1 m wireframe cube is drawn centred on `Model 0`

#### Scenario: Off by default

- **WHEN** the Scene view of the `Physics` scene opens
- **THEN** no physics wireframes are drawn until Show Physics is turned on

### Requirement: Constraints are drawn

With Show Physics on, each constraint SHALL be drawn as a line between its two anchors, with a marker at each anchor.
A distance constraint's line SHALL be solid while the anchors are at most its maximum distance apart.

#### Scenario: Rope line

- **WHEN** `Model 2` has a rope to `Model 0`
- **THEN** a line is drawn between the two anchors with a marker at each end

### Requirement: The overlay follows edits and play

The overlay SHALL reflect component edits, gizmo drags and Undo without reopening the view, and during Play SHALL
follow the simulated poses.

#### Scenario: Resize a box

- **WHEN** the box's half extents are changed to `1, 0.5, 0.5` in the panel
- **THEN** the wireframe becomes 2 m long along X at once

#### Scenario: During play

- **WHEN** the `Physics` scene is playing and `Model 0` falls
- **THEN** its wireframe falls with it

### Requirement: The selected entity's physics stands out

The selected entity's collider and constraints SHALL be drawn brighter and on top of other geometry, so they stay
visible inside its model.

#### Scenario: Collider inside a model

- **WHEN** `Model 0` is selected and its box lies inside its model
- **THEN** the box's wireframe is visible through the model
