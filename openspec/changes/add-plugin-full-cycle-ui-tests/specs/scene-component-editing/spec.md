# Spec Delta

## ADDED Requirements

### Requirement: Remove an entity from the tree

Remove Entity SHALL be offered only on entity rows of an admitted native scene, never on project, scene, `ecs`, component, asset, water or UI-asset rows. Successful removal SHALL delete only that entity, leave its referenced assets intact, refresh the tree and open scene view, and clear selections or properties that referred to the removed entity.

#### Scenario: Complete a new camera entity lifecycle
- **WHEN** a user adds Camera through the `ecs` row in a fresh copy of Untitled's `Main Scene.scene` (entities `0`–`10`, so the new id is `11`), edits and reopens the new entity, then chooses Remove Entity on it
- **THEN** that new entity disappears from the file and tree, the entities that existed before it remain unchanged, and no stale selection or editable properties remain for it

#### Scenario: Removing an asset instance keeps its asset
- **WHEN** the user adds model asset `tree` to a copy of Untitled and removes the resulting entity
- **THEN** the entity is absent after save and reopen, and the `tree` asset folder and metadata remain unchanged

#### Scenario: Unrelated rows
- **WHEN** the user opens the context menu of a project, scene, `ecs`, component, asset, water or UI-asset row
- **THEN** Remove Entity is absent

### Requirement: Entity removal protects remaining references

Removal SHALL be refused if another entity references the target through a built-in entity reference, arrangement slot or registered entity-reference field. It SHALL also be refused if deleting the target would leave owned arrangement copies behind. Refusal SHALL identify the blocking relationship and leave document text and history unchanged. No other entity SHALL be silently deleted, detached or retargeted.

#### Scenario: Camera target cannot be removed
- **WHEN** the user removes entity `3`, named `'Direction' handle`, in a copy of Untitled while `Camera 4` still names it as its look-at target
- **THEN** removal is refused with an explanation naming a referring entity and field, and the scene remains byte-identical

#### Scenario: Parent and endpoint references
- **WHEN** a target is still named as another entity's parent or point-to-point endpoint and the user chooses Remove Entity
- **THEN** removal is refused and every entity stays unchanged

#### Scenario: Registered reference
- **WHEN** a registered component declares an entity-reference field naming the selected entity and the user chooses Remove Entity
- **THEN** removal is refused with the referring entity and field identified

#### Scenario: Arrangement ownership
- **WHEN** an arrangement still owns generated copies, or a copy is still named in an arrangement slot, and the user tries to remove that entity
- **THEN** removal is refused with the arrangement relationship identified and no copies or slots change

#### Scenario: Clear a reference then remove
- **WHEN** the user clears all blocking references through supported editing actions and removes the target
- **THEN** only that target is removed and the resulting scene reopens successfully

### Requirement: Entity removal preserves native documents and undo history

Entity removal SHALL use the latest document text, require supported native markers, and preserve all unrelated text, unknown payloads, number spelling, key order, omitted defaults and wrapped-scene archetype data. It SHALL be one named undoable edit. Undo SHALL restore exact prior text and Redo SHALL restore the removed state; tree, properties and open scene view SHALL follow both operations.

#### Scenario: Undo and redo from the initiating view
- **WHEN** the user removes unreferenced `Model 0` in a fresh copy of Untitled, uses Undo with the tree focused, then Redo
- **THEN** Undo restores the original scene bytes and entity row, and Redo removes the same entity without changing any other entity or asset

#### Scenario: Unsupported or changed target
- **WHEN** the scene has unsupported native markers, or the selected entity was removed from the latest document before the action executes
- **THEN** no removal is written and no undo step is created

#### Scenario: Native scene
- **WHEN** an unreferenced entity is removed from an admitted native scene
- **THEN** only its entity entry under `ecs` is removed, the rest of the file keeps its text, and Undo restores the original document

#### Scenario: Unknown payload and unsaved edits
- **WHEN** unrelated unsaved document edits include extension data and number text `1.000`, and an unreferenced entity is removed
- **THEN** those unsaved edits and their spelling survive and Undo restores the document as it was immediately before removal

## MODIFIED Requirements

### Requirement: Add a component as a new entity

Add Component... SHALL also be offered on the **ecs** row of a scene in the Abyssus tree, including a scene whose
entities sit in an older wrapped `entities` block. It SHALL list every modeled or schema-declared kind except Name; Render SHALL
offer the project's models and terrains as it does on an entity row. Choosing a kind SHALL add one new entity:
- its id SHALL be one more than the highest numeric entity id;
- it SHALL have a `NameComponent` `Entity <id>`;
- it SHALL hold the chosen component, from its defaults or the chosen asset, by the same rules as adding to an existing
  entity.

The new entity SHALL have `components` and no `flags`.
The addition SHALL be one undoable edit, nothing else in the file SHALL change, and the new entity's row SHALL be
selected. It SHALL be unavailable when the scene cannot be read as a native scene.

#### Scenario: A camera as a new entity

- **WHEN** the user right-clicks the `ecs` row of `Main Scene` of the Untitled project, whose highest entity id is `10`, and chooses Add Component... → Camera
- **THEN** the file gains entity `11` named `Entity 11` with a camera component of default values, and entities `0` to `10` are unchanged

#### Scenario: A render component as a new entity

- **WHEN** the user chooses Add Component... → Render → model `tree` on the `ecs` row of `Main Scene`
- **THEN** the new entity `11` holds a render component of asset `MODEL` `tree`

#### Scenario: Name is not offered

- **WHEN** the user opens Add Component... on the `ecs` row
- **THEN** Name is not among the choices, since every new entity is named

#### Scenario: Undo

- **WHEN** the user chooses Undo after adding a component as a new entity
- **THEN** the scene file is as it was before

#### Scenario: Unreadable scene

- **WHEN** the scene file holds text that is not valid JSON
- **THEN** Add Component... is not offered on its `ecs` row and nothing is written
