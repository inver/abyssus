## Purpose

The scene view's interactive behavior: selecting an entity, the move and rotate gizmos, and the
transform a completed drag writes to the scene file.

## ADDED Requirements

### Requirement: A rotate drag on a directional or spot light keeps the turned direction

When the user rotates a selected directional light or spot light with the rotate gizmo and releases
the drag, the editor SHALL write the light's new rotation to the scene file, and the light's facing
direction SHALL be the direction the drag turned it to.

#### Scenario: Dragging a directional light's rotate handle writes its rotation

- **WHEN** the scene `RotateLight` is open, the directional light `1` is selected, and the user drags
  the rotate gizmo about an axis and releases
- **THEN** the light's `PositionComponent.localRotation` in the scene file is the rotated value, and
  the light's direction is the direction the drag turned it to

#### Scenario: Dragging a spot light's rotate handle writes its rotation

- **WHEN** the scene `RotateLight` is open, the spot light `4` is selected, and the user drags the
  rotate gizmo about an axis and releases
- **THEN** the spot light's `PositionComponent.localRotation` in the scene file is the rotated value,
  and its direction is the direction the drag turned it to

### Requirement: The light's direction stays in sync with its rotation when written

Because a light's direction is derived from its rotation in the scene file, writing a rotated
direction SHALL also write the rotation that produces it; no new file field is introduced for the
direction.

#### Scenario: Re-reading the scene after a rotate drag

- **WHEN** the user rotates a directional light `1` in `RotateLight` and releases, then the scene is
  re-read
- **THEN** the light's direction is derived from the written `PositionComponent.localRotation` and
  matches the direction the drag turned it to, within rendering precision

### Requirement: The preview shows the exact direction being turned

While a rotate drag on a directional or spot light is in progress, the light's marker and its
contribution to the lighting SHALL show the exact direction the drag has turned it to, not a
direction re-derived from the in-progress rotation.

#### Scenario: A light in progress points where the user dragged it

- **WHEN** the user is mid-drag on the rotate gizmo of directional light `1` in `RotateLight`
- **THEN** the light's direction line and its lighting point along the exact direction the drag has
  turned it to

### Requirement: A point light has no direction to turn

A point light SHALL not offer rotate handles, because it has no facing direction; a completed drag on
a point light SHALL not write a direction.

#### Scenario: Selecting a point light in rotate mode

- **WHEN** the scene `RotateLight` is open, a point light is selected, and the gizmo is in rotate mode
- **THEN** no rotate handles are shown for the point light, and rotating the point light writes no
  direction to the file
