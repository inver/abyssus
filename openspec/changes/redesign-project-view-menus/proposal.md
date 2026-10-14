# Proposal

## Why

The Abyssus view's right-click menus are flat: every action is its own top-level item, ordered by registration in
`plugin.xml`, and each hides itself when its `targetOf` returns null. On the project's **Assets** row that reads
Import FlightGear Aircraft, Import Model, New Terrain, New Foliage, New UI Asset as five siblings, and on a **Scene** row
Add Light, Add Water, Add Asset, Add Path Arrangement as four siblings. The related actions are not grouped, so a user has to scan
the whole list to find the importers or the creators. Grouping them under `Import Model`, `New` and `Add` makes the two
menus read as their intent.

## What Changes

- **Group the Assets row menu.** In `plugin.xml` the Assets-row actions nest under two `popup` groups:
  - `Import Model` → Import FlightGear Aircraft, Import Model
  - `New` → New UI Asset, New Terrain, New Foliage
- **Group the Scene row menu.** The scene-row actions nest under one `popup` group:
  - `Add` → Add Asset, Add Path Arrangement, Add Light, Add Water (from `add-realistic-water`)
  - Rename Scene stays a flat item above the `Add` group. Rename Water and Remove Water stay on water rows, unchanged.
- **Rename is offered on every scene row, including standalone scenes.** `RenameSceneAction.targetOf` today only
  matches `DtoEntryNode`, so a top-level `.scene` (`AbyssusAssetNode`) gets the Add actions but no Rename. It now
  targets the scene `VirtualFile` through `viewableSceneFile(node)` (as the Add actions do), and `perform` reads the scene's
  current name from that file, since a standalone scene has no `SceneEntry`.
- **Entity, component, UI-asset, foliage and weather-preset rows keep their existing menus unchanged.** Add Component,
  Remove Component, Add Foliage, Regenerate, Register / Unregister UI Asset and New Weather Preset are not moved.
- **Group labels are localized** through `AbyssusBundle` (`group.Abyssus.*.text`), and the groups hide when they have no
  visible child.
- **Tests updated.** `TreeActionVisibilityTest` asserts each action is offered alone on its row; the grouped actions now
  live under a group, so the assertions change to check the group and its children. `RenameSceneActionTest` covers the
  standalone scene.

No document format changes. No new reads or writes. Every action keeps its existing `targetOf`, `isEnabled` and `perform`;
only the `plugin.xml` grouping, the group labels, and `RenameSceneAction`'s target and name lookup change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `abyssus-project-view`: the right-click menu of an Assets row is two grouped sub-menus (`Import Model`, `New`) and the
  right-click menu of a Scene row is Rename plus one grouped sub-menu (`Add`); Rename is offered on a standalone scene
  row as well as a scene listed under a project.

## Impact

- Code: `projects/plugin-abyssus/src/main/resources/META-INF/plugin.xml` (grouping), `AbyssusBundle.properties` (group labels),
  `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/projectView/RenameSceneAction.kt` (standalone-scene target and name).
- Tests: `projects/plugin-abyssus/src/test/kotlin/net/nevinsky/abyssus/plugin/projectView/TreeActionVisibilityTest.kt`,
  `RenameSceneActionTest`.
- Coordination: `add-realistic-water` registers Add Water in `plugin.xml`; this change places it in the `Add` group.
- Out of scope: the Scene view toolbar (it already groups through `AddAssetGroup` / `AddLightGroup` /
  `AddPathArrangementGroup` and is unchanged), the Properties tool window, and any other tree row's menu.