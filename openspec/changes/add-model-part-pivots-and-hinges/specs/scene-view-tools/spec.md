# Spec Delta

## ADDED Requirements

### Requirement: Edit Pivot toggle

The scene view toolbar SHALL have an Edit Pivot toggle, enabled only while a part whose pivot can be applied is
selected, and off when the view opens. While it is on, the toolbar SHALL offer a snap choice of None, Edge and Face,
None by default, and the stats strip SHALL name the part being edited. Move and Rotate SHALL keep their keys and apply
to the pivot frame; Select SHALL show the pivot frame without handles. The toggle and the snap choice SHALL apply to
the view only and SHALL NOT be written to the scene.

#### Scenario: Off without a part

- **WHEN** only the entity `Model 6` is selected
- **THEN** Edit Pivot is disabled

#### Scenario: Not saved

- **WHEN** the user turns Edit Pivot on with Edge snap, then closes and reopens the scene
- **THEN** Edit Pivot is off, the snap is None and the scene file is unchanged by the choices
