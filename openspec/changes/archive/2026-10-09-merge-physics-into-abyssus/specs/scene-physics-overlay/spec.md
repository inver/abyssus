## MODIFIED Requirements

### Requirement: Colliders are drawn

With physics on for the scene's project and "Show Physics" on, the Scene view SHALL draw each collider as a wireframe of its
shape, at the entity's pose and the collider's offset: green for dynamic, blue for kinematic, grey for static. A height
field SHALL be drawn as its terrain's outline. "Show Physics" SHALL be off when a Scene view opens, and SHALL NOT be
offered while physics is off for the project.

#### Scenario: Box on Model 0

- **WHEN** `Model 0` of the `Physics` scene has a dynamic box with half extents `0.5, 0.5, 0.5` and Show Physics is on
- **THEN** a green 1 m wireframe cube is drawn centred on `Model 0`

#### Scenario: Off by default

- **WHEN** the Scene view of the `Physics` scene opens
- **THEN** no physics wireframes are drawn until Show Physics is turned on

#### Scenario: Physics off for the project

- **WHEN** physics is turned off for a copy of the `Physics` project while Show Physics is on
- **THEN** the wireframes disappear and Show Physics is no longer offered in the Scene view toolbar
