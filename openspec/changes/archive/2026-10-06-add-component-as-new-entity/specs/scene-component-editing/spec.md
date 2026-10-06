# Spec Delta

## ADDED Requirements

### Requirement: Add a component as a new entity

Add Component... SHALL also be offered on the **ecs** row of a scene in the Abyssus tree, including a scene whose
entities sit in an older wrapped `entities` block. It SHALL list every modeled or schema-declared kind except Name; Render SHALL
offer the project's models and terrains as it does on an entity row. Choosing a kind SHALL add one new entity:
- its id SHALL be one more than the highest numeric entity id;
- it SHALL have a `NameComponent` `Entity <id>`;
- it SHALL hold the chosen component, from its defaults or the chosen asset, by the same rules as adding to an existing
  entity.

In a wrapped scene, its `archetype` SHALL name an archetype listing exactly its components, reusing a matching one.
The addition SHALL be one undoable edit, nothing else in the file SHALL change, and the new entity's row SHALL be
selected. It SHALL be unavailable when the scene cannot be read as a native scene.

#### Scenario: A camera as a new entity

- **WHEN** the user right-clicks the `ecs` row of `Main Scene` of the Untitled project, whose highest entity id is `8`, and chooses Add Component... → Camera
- **THEN** the file gains entity `9` named `Entity 9` with a camera component of default values, and entities `0` to `8` are unchanged

#### Scenario: A render component as a new entity

- **WHEN** the user chooses Add Component... → Render → model `tree` on the `ecs` row of `Main Scene`
- **THEN** the new entity `9` holds a render component of asset `MODEL` `tree`

#### Scenario: Name is not offered

- **WHEN** the user opens Add Component... on the `ecs` row
- **THEN** Name is not among the choices, since every new entity is named

#### Scenario: Undo

- **WHEN** the user chooses Undo after adding a component as a new entity
- **THEN** the scene file is as it was before

#### Scenario: Unreadable scene

- **WHEN** the scene file holds text that is not valid JSON
- **THEN** Add Component... is not offered on its `ecs` row and nothing is written
