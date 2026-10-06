# Proposal

## Why

Placing a project's model or terrain in a scene today takes two steps: add a light or edit JSON by hand to get an
entity, then use Add Component → Render and pick the asset. Add Light already shows the one-step flow, offered from
the scene row's menu and the Scene view toolbar. Assets need the same.

## What Changes

- New **Add Asset** next to Add Light: in the right-click menu of a scene row of the Abyssus tree, and as a dropdown in
  the Scene view toolbar.
  - It lists the project's MODEL assets under "Models" and its TERRAIN assets under "Terrains", by folder name, with
    speed search.
  - Choosing one adds one entity to the scene as a single undoable `editSceneJson` command, then selects it.
  - The entity has `NameComponent` (`Model <id>` / `Terrain <id>`), `TypeComponent` (`OBJECT` / `TERRAIN`),
    `PositionComponent`, and a `RenderComponent` asset renderable (`shaderKey` `defaultShader` / `terrain`).
- **Placement:** like a light, at the Scene view's orbit point or at the scene origin from the tree. A terrain is centred
  on that point, using its `size`.
- Add Asset is unavailable when the scene cannot be read as a native scene, when it belongs to no project, or when the
  project has no models or terrains.

Native fields:
- **Read:** the scene (`format`, `formatVersion`, `ecs` entity map), the project's asset `meta.json` (`type`, and
  `additional.size` of a terrain).
- **Written:** one new `ecs` entity, with an id one above the highest numeric id. Unrelated keys, number text and
  defaults are preserved.
- Documents are validated before use. No format change.

Out of scope:
- Other asset kinds (skyboxes, textures).
- Physics colliders for the new entity.
- Placing by mouse drop or ray-cast onto the terrain.
- Multi-select, and creating a new asset from this menu.

## Capabilities

### New Capabilities

- `scene-asset-placement`: adding a project's model or terrain to a scene as a new entity from the tree and the Scene
  view.

### Modified Capabilities

None.

## Impact

- Plugin:
  - `ecs/scene` gets a JSON-only entity builder (beside `LightEntities`).
  - `projectView` gets the Add Asset tree action and group, with `SceneComponentEdits.addAsset`.
  - `sceneview` gets the toolbar button (`SceneViewPanel`, `SceneFileEditor`).
  - Also `plugin.xml` and `AbyssusBundle.properties`.
- Docs: `README.md` (user section), `projectView/README.md`, `sceneview/README.md`.
