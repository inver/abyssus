# Spec Delta

## ADDED Requirements

### Requirement: Child entities are listed under their parent

Under a scene's `ecs` node, an entity whose `ParentComponent` names another entity of the scene SHALL NOT be listed
directly under `ecs`. It SHALL be listed under its parent's entity row, before that row's component rows, in id order,
and it SHALL nest the same way under its own children's parent. The `ecs` row SHALL still count every entity. An
entity row with children SHALL read `N components, M children` as its value. An entity whose parent link is broken
(missing parent, itself, or a cycle) SHALL be listed directly under `ecs`. Linked copies of a path arrangement SHALL
stay squashed into their arrangement's row, as before. A dummy's row SHALL show a dummy icon.

#### Scenario: A camera's handle

- **WHEN** a user expands the `ecs` node of `Untitled`'s `Main Scene`
- **THEN** `'Direction' handle` (entity `3`) is not listed under `ecs`; it is the first child of the `Camera 4` row,
  which reads `6 components, 1 children`, and `ecs` still reads `11 entities`

#### Scenario: Dummies under a plane

- **WHEN** a user expands the `Trainer` row of Control Line's `Field.scene`
- **THEN** its first two children are the dummies `leadoutUp` and `leadoutDown`, followed by its component rows

#### Scenario: A broken link

- **WHEN** an entity's `parentEntityId` names an entity the scene does not have
- **THEN** the entity is listed directly under `ecs`

#### Scenario: Selecting a child in the view reveals it

- **WHEN** the user clicks a dummy in the Scene view whose parent row is collapsed
- **THEN** the parent row expands and the dummy's row is selected
