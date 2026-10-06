# Spec Delta

## ADDED Requirements

### Requirement: Layer colliders

An OBJECT layer SHALL accept an optional collider with shape BOX, SPHERE or CAPSULE, its half extents, radius, half height and
offset, using the defaults and limits of a collider component. The foliage panel SHALL edit it, with None as the default.
DETAIL layers SHALL NOT offer a collider. Changing a collider SHALL write only `meta.json`, and SHALL NOT make the bake
out of date.

#### Scenario: Give trees a trunk
- **WHEN** the user sets the `tree` layer's collider to CAPSULE with radius `0.4` and half height `3`, and applies
- **THEN** the layer holds `"collider": {"shape": "CAPSULE", "radius": 0.4, "halfHeight": 3}`, `foliage.data` is unchanged, and the panel does not say the bake is out of date

#### Scenario: Grass has no collider
- **WHEN** the user edits a DETAIL layer
- **THEN** no collider field is offered

#### Scenario: Invalid size
- **WHEN** the user enters a radius of `0` or a non-finite half extent
- **THEN** the value is rejected with a reason beside the field and nothing is written

#### Scenario: Remove a collider
- **WHEN** the user sets a layer's collider to None and applies
- **THEN** the layer's `collider` member is removed and the rest of the file is unchanged
