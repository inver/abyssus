# Spec Delta

## ADDED Requirements

### Requirement: Arrangement copies are squashed into their arrangement

Under a scene's `ecs` node the tree SHALL NOT list an entity that a path arrangement lists in its `slots` and that has a
`ParentComponent` pointing to that arrangement. The row of the arrangement SHALL read `N components, M copies`, where M
is the number of its slots that name an entity. An entity without such a `ParentComponent` SHALL be listed like any other.

#### Scenario: Railway copies

- **WHEN** a user expands the `ecs` node of a scene with an arrangement `Railway` whose slots list 12 linked copies
- **THEN** the 12 copies are not listed and the `Railway` row reads `3 components, 12 copies`

#### Scenario: A detached copy

- **WHEN** a copy has been detached, so it has no `ParentComponent` and its slot is `-1`
- **THEN** it is listed under `ecs` as an ordinary entity
