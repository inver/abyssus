## MODIFIED Requirements

### Requirement: Rigid body component

A `RigidBodyComponent` SHALL hold a motion type (`STATIC`, `KINEMATIC` or `DYNAMIC`, default `DYNAMIC`), a mass in
kilograms (default `1`), friction (default `0.2`), restitution (default `0`), linear and angular damping (default
`0.05` each) and a gravity factor (default `1`), with defaults omitted from the file.

#### Scenario: Default rigid body

- **WHEN** a rigid body is added to `Model 0` of a copy of `Untitled` with physics turned on for the project
- **THEN** the entity gains `"RigidBodyComponent": {}` and the panel shows motion type `DYNAMIC` and mass `1`

## REMOVED Requirements

### Requirement: Physics components need Abyssus Physics only to edit

**Reason**: Abyssus Physics is no longer a separate plugin. Physics ships inside Abyssus and is switched per project
by `physicsEnabled` in the `.abss`.
**Migration**: Replaced by "Physics components are edited only with physics on". A project that relied on Abyssus
Physics being installed turns physics on in its project properties.

## ADDED Requirements

### Requirement: Physics components are edited only with physics on

With physics on for a project, Abyssus SHALL offer the three physics components in "Add component" and edit them like
any schema-declared component. With physics off, it SHALL NOT offer them, and existing physics components SHALL be
shown as read-only JSON and kept unchanged.

#### Scenario: Physics off

- **WHEN** a scene with a `RigidBodyComponent` is opened in a project whose `.abss` has no `physicsEnabled`
- **THEN** the component is shown as read-only JSON, "Add component" does not offer physics components, and the file
  is unchanged

#### Scenario: Turning physics on makes them editable

- **WHEN** physics is turned on for that project while its scene is open
- **THEN** the `RigidBodyComponent` becomes editable without reopening the scene, and "Add component" offers
  `RigidBodyComponent`, `ColliderComponent` and `ConstraintComponent`
