# Spec Delta

## ADDED Requirements

### Requirement: Collider outlines are drawn at world pose

The physics overlay SHALL draw each collider outline at its entity's world pose. A child collider SHALL therefore
follow its parent in the overlay.

#### Scenario: A child collider

- **WHEN** a child with a box collider and `localPosition` (1, 0, 0) is under an entity at (0, 5, 0)
- **THEN** the box outline is drawn around (1, 5, 0)
