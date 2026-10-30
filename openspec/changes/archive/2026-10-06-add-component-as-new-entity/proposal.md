# Proposal

## Why

Add Component... is offered only on an entity row. To start a new entity with, say, a camera or a render component,
the user has to get an entity first: with Add Light or Add Asset, or by editing JSON by hand. The scene's **ecs** row
is where users look for "add something here".

## What Changes

- Right-click the **ecs** row of a scene in the Abyssus tree to get **Add Component...**. This includes older wrapped
  scenes, whose entities the tree also lists under that row.
  - It lists every modeled and schema-declared component kind except Name. Render opens its per-asset submenu, as on an
    entity row.
  - Choosing one creates a new entity, with the next numeric id and a `NameComponent` `Entity <id>`, holding that
    component. Defaults or the chosen asset are applied the same way as adding to an existing entity.
  - This is one undoable edit through `editSceneJson`, and the new entity's row is selected.
- Add Component on an entity row is unchanged.

Native fields:
- **Read:** the scene document and the project asset metas, as Add Component already does.
- **Written:** one new `ecs` entity. In an older wrapped layout, its `archetype` matches or extends `ecs.archetypes`.
  Unrelated keys and number text are preserved.
- No format change.

Out of scope:
- Adding several components at once.
- Choosing the new entity's name in the menu.
- Add Component on the scene row itself.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `scene-component-editing`: adds a requirement for creating an entity from the ecs row through Add Component.

## Impact

- Plugin `projectView`:
  - `ComponentActions.kt`: a second Add Component action for the ecs row; the shared group takes where a choice goes.
  - `SceneComponentEdits.addAsNewEntity`.
- `ecs/scene`: a shared `SceneEntities` for inserting an entity, which `AssetEntities` now uses.
- `AbyssusBundle.properties`; `projectView/README.md` and the `README.md` user section.
