# Spec Delta

## Purpose

Defines the physics data a scene entity can carry (a rigid body, a collider and a constraint), as it is stored in the
scene file and edited in Abyssus.

## ADDED Requirements

### Requirement: Rigid body component

A `RigidBodyComponent` SHALL hold a motion type (`STATIC`, `KINEMATIC` or `DYNAMIC`, default `DYNAMIC`), a mass in
kilograms (default `1`), friction (default `0.2`), restitution (default `0`), linear and angular damping (default
`0.05` each) and a gravity factor (default `1`), with defaults omitted from the file.

#### Scenario: Default rigid body

- **WHEN** a rigid body is added to `Model 0` of a copy of `Untitled` with Abyssus Physics installed
- **THEN** the entity gains `"RigidBodyComponent": {}` and the panel shows motion type `DYNAMIC` and mass `1`

### Requirement: Collider component

A `ColliderComponent` SHALL hold a shape (`BOX`, `SPHERE`, `CAPSULE`, `CONVEX_HULL` or `HEIGHT_FIELD`, default `BOX`),
half extents for a box (default `0.5, 0.5, 0.5`), a radius for a sphere or capsule (default `0.5`), a half height for a
capsule (default `0.5`) and an offset from the entity (default `0, 0, 0`). A convex hull SHALL be built from the
entity's model and a height field from the entity's terrain, both scaled by the entity's scale.

#### Scenario: Terrain collider

- **WHEN** a collider with shape `HEIGHT_FIELD` is added to `Terrain` (entity `1`) of a copy of `Untitled`
- **THEN** the file holds `"ColliderComponent": {"shape": "HEIGHT_FIELD"}` for entity `1`

### Requirement: Constraint component

A `ConstraintComponent` SHALL join its entity's body to another entity's body, or to a fixed point in the world when
the other entity is `-1`. It SHALL hold a kind (`DISTANCE`, `HINGE` or `FIXED`, default `DISTANCE`), the other entity
(default `-1`), an anchor on each body (default the body's origin), a minimum and maximum distance for `DISTANCE`
(defaults `0` and `1`), and a hinge axis for `HINGE` (default `0, 1, 0`).

#### Scenario: A rope to another entity

- **WHEN** a constraint on `Model 2` is set to kind `DISTANCE`, other entity `0`, minimum `0` and maximum `3`
- **THEN** the file holds `"ConstraintComponent": {"other": 0, "maxDistance": 3}` for entity `2`

### Requirement: Physics values are validated

Editing SHALL reject a mass, half extent, radius or half height that is not greater than `0`, and a distance below
`0`, each with a message and no change to the file. A scene file holding such a value, or a maximum distance below the
minimum, SHALL be simulated with that body or constraint left out and one warning naming the entity and the field.

#### Scenario: Zero mass

- **WHEN** the mass of a dynamic body is set to `0`
- **THEN** the value is rejected with a message naming `mass`, and the file is unchanged

#### Scenario: Maximum below minimum in the file

- **WHEN** a scene holds a distance constraint with minimum `2` and maximum `1`
- **THEN** the simulation runs without that constraint and logs one warning naming the entity and `maxDistance`

### Requirement: Physics components need Abyssus Physics only to edit

With Abyssus Physics installed, Abyssus SHALL offer the three physics components in "Add component" and edit them like
any schema-declared component. Without it, existing physics components SHALL be shown as read-only JSON and kept
unchanged.

#### Scenario: Plugin not installed

- **WHEN** a scene with a `RigidBodyComponent` is opened in an IDE without Abyssus Physics
- **THEN** the component is shown as read-only JSON and the file is unchanged
