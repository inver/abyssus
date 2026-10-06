# Design

## Context

`AddComponentAction` targets entity rows (`componentTargetOf(...).kind == null`). Its choices come from
`ComponentEditor.missingKinds`, and `addComponentGroup` builds them. Each choice calls `SceneComponentEdits.add` for a
fixed entity id. `ComponentEditor.add` needs the entity to exist and does not touch `archetypes`. `AssetEntities` (from
`add-scene-asset-entities`) already inserts a new entity in both layouts.

## Decisions

- **`SceneEntities`** (`ecs/scene`): `insert(root, components)`. It takes the highest numeric id + 1 and, in a wrapped
  layout, the archetype matching the component names. It also offers `matchArchetype(root, id)`, which re-points a
  wrapped entity at the archetype for its current components, adding one when none matches. `AssetEntities` delegates
  to it; behaviour is unchanged and its tests still hold.
- **`SceneComponentEdits.addAsNewEntity(project, file, kind, metaFiles, initial)`** works inside one `editSceneJson`
  command:
  1. Insert `{NameComponent: "Entity <id>"}`.
  2. Run `ComponentEditor.add` for the kind, with the same asset sets as `add`. A rejection aborts the whole command, so
     no bare entity is left behind.
  3. Call `matchArchetype` in a wrapped layout.

  It returns the result and the new id.
- **The target:** a second action, `AddComponentOnEcsAction` (`Abyssus.AddComponentOnEcs`, also labelled "Add
  Component..."), targets the ecs row: a `DtoEntryNode` named `ecs` at the top of a scene document (`ecsRowSceneOf`).
  The tree lists a wrapped scene's entities directly under that row too, so there is no `entities` row to handle. The two actions never show on the same row. `AddComponentAction` and its entity-row behaviour
  stay unchanged.
- **Choices:** `addComponentGroup` takes the action to run for a kind and its initial values, so entity rows and the
  ecs row share the Render submenu and the mnemonic-safe labels. For the ecs row, the kinds are all of the editor's
  kinds except `NameComponent`. A new entity is selected with `selectCreatedEntity`.
- **Threads:** the same as Add Component: the menu is built on the EDT from the cached document; the write is
  `editSceneJson`.

## Risks / Trade-offs

- [`ComponentEditor.add` validating against the root] → the entity is inserted first, so the checks see the real
  scene.
