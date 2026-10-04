# Spec Delta

## ADDED Requirements

### Requirement: A vector's limits hold for each axis

A minimum, maximum or exclusive minimum declared on a 3D vector field SHALL apply to each of its axes, when loading
and when editing. A vector with an axis outside the limits SHALL be unusable as a whole, and the problem SHALL name
the axis.

#### Scenario: Box half extents

- **WHEN** a collider's half extents are declared with an exclusive minimum of `0` and the file holds
  `{"x": 1, "y": 0, "z": 1}`
- **THEN** the half extents load as the default and the log names `halfExtents`, the axis `y` and the minimum

#### Scenario: Editing one axis

- **WHEN** the `y` of those half extents is set to `0` in the panel
- **THEN** the file is unchanged and a message names the field and its minimum
