# Design

## Context

See proposal.md. Add Light is the model to follow:
- `LightEntities` (`ecs/scene`) edits only the JSON tree.
- `SceneComponentEdits.addLight` wraps it in `editSceneJson`.
- `AddLightGroup` lists the choices, used both by `AddLightAction` (tree, origin) and by `SceneViewPanel`'s toolbar
  button (orbit target).
- `selectCreatedLight` selects the new row.

`SceneComponentEdits.renderAssets` already lists a project's MODEL and TERRAIN folders from their metas.

## Goals / Non-Goals

**Goals:** reuse that flow; JSON building testable without Swing; one undoable command.

**Non-Goals:** see the proposal's out-of-scope list.

## Decisions

- **`AssetEntities`** (`ecs/scene`, beside `LightEntities`) builds the entity on the scene's JSON tree. The add takes the
  asset (type and name), the placement point and, for a terrain, its size. It applies the same `canAdd` rules as
  `LightEntities`: native, `ecs` an object. It handles the native entity map and the wrapped `entities` / `archetypes`
  layout the same way `LightEntities` does, matching or adding an archetype for its four components. Ids come from the
  same "highest + 1" rule. Testable headless.
- **Terrain centring** reads `additional.size` from the terrain's `meta.json` through the project's `MetaFiles`. A
  terrain whose size cannot be read is placed with its corner at the point and the menu still offers it, so a broken
  meta does not hide the asset.
- **`SceneComponentEdits.addAsset`** runs it in one `editSceneJson` command (`commandAddAsset`).
- **`AddAssetGroup`** (`projectView`) is a `DefaultActionGroup` with a "Models" and a "Terrains" separator, built from
  `renderAssets`. Each choice is enabled while the scene reads (the same `SceneDocumentCache` check as Add Light), and
  calls `addAsset` with the position supplier, then selects the row like `selectCreatedLight`. That helper is
  generalised to `selectCreatedEntity`.
- **`AddAssetAction`** is an `AbyssusTreeAction` on scene rows (`viewableSceneFile`), enabled when the scene reads and
  the project has a model or terrain. It shows the group as a speed-search popup with the origin as the position.
  Registered in `plugin.xml` next to Add Light.
- **`SceneViewPanel`** gets an "Add Asset" button beside "Add Light", built from an optional `assetActions` supplier.
  Its enabled state follows the same editing rule as Add Light. `SceneFileEditor` passes
  `AddAssetGroup(project, file, position)`.
- **Threads:** listing reads metas through the caching `MetaFiles` on the EDT / BGT as the existing render-asset listing
  does; the write is the EDT write command of `editSceneJson`; no GL.

## Risks / Trade-offs

- [Large projects make a long list] → speed search in the popup.
- [A wrapped (older) scene layout] → handled as `LightEntities` handles it, and tested.
