# Proposal

## Why

The Abyssus view's right-click menus are flat: every action is its own top-level item, ordered by registration in
`plugin.xml`, and each hides itself when its `targetOf` returns null. On the project's **Assets** row that reads
Import FlightGear Aircraft, Import Model, New UI Asset, New Terrain, New Foliage as five siblings, and on a **Scene** row
Rename Scene, Add Light, Add Asset, Add Path Arrangement, Add Water as five siblings. The related actions are not grouped, so a
user has to scan the whole list to find the importers or the creators. Grouping them under `Import Model`, `New` and
`Add` makes the two menus read as their intent.

## What Changes

- **Group the Assets row menu.** In `plugin.xml` the Assets-row actions nest under two `popup` groups, in this order:
  - `Import Model` → Import FlightGear Aircraft, Import Model
  - `New` → New UI Asset, New Terrain, New Foliage
- **Group the Scene row menu.** The scene-row actions nest under one `popup` group:
  - `Add` → Add Asset, Add Path Arrangement, Add Light, Add Water
  - Rename Scene stays a flat item above the `Add` group. Rename Water and Remove Water stay on water rows, unchanged.
- **Re-anchor the actions that pointed into the grouped ones.** `Abyssus.RegenerateArrangement` (registered after
  `Abyssus.AddPathArrangement`) and `Abyssus.AddFoliage` (after `Abyssus.AddAsset`) anchor after the `Add` group;
  `Abyssus.NewUiAsset` (after `Abyssus.NewWeatherPreset`) gets an anchor inside `New`. The platform resolves
  `relative-to-action` only among the group's own children and leaves an unresolvable one pending, so those actions
  would silently move to the end of the menu.
- **Empty sub-menus hide themselves.** Each group hides itself while it has no enabled and visible child, so it never
  renders as an empty or disabled header.
- **Rename is offered on every scene row, including standalone scenes.** `RenameSceneAction.targetOf` today only
  matches `DtoEntryNode`, so a top-level `.scene` (`AbyssusAssetNode`) gets the Add actions but no Rename. It now
  targets the scene `VirtualFile` through `viewableSceneFile(node)` (as the Add actions do), and `perform` reads the scene's
  current name from that file, since a standalone scene has no `SceneEntry`.
- **Entity, component, UI-asset, foliage and weather-preset rows keep their existing menus unchanged.** Add Component,
  Remove Component, Add Foliage, Regenerate, Register / Unregister UI Asset and New Weather Preset are not moved.
- **Group labels are localized** through `AbyssusBundle` (`group.Abyssus.Menu.*.text`).
- **Tests updated.** `TreeActionVisibilityTest` keeps its "offered on its row" assertions (grouping changes only
  `plugin.xml`, not any `targetOf`) and gains assertions on the registered groups and their children; a new
  `RenameSceneActionTest` covers target and name lookup for a scene listed under a project and a standalone scene.

No document format changes and no new file writes. Every action other than `RenameSceneAction` keeps its existing
`targetOf`, `isEnabled` and `perform`; only the `plugin.xml` grouping, the group labels, and `RenameSceneAction`'s target
and name lookup change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `abyssus-project-view`: the right-click menu of an Assets row is two grouped sub-menus (`Import Model`, `New`) and the
  right-click menu of a Scene row is Rename Scene plus one grouped sub-menu (`Add`); Rename Scene is offered on a
  standalone scene row as well as a scene listed under a project.

## Impact

- Code: `projects/plugin-abyssus/src/main/resources/META-INF/plugin.xml` (three groups; the grouped actions' and the three
  re-anchored actions' `add-to-group` entries), `AbyssusBundle.properties` (group labels),
  `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/projectView/RenameSceneAction.kt` (standalone-scene target and name).
- Tests: `projects/plugin-abyssus/src/test/kotlin/net/nevinsky/abyssus/plugin/projectView/TreeActionVisibilityTest.kt`,
  a new `RenameSceneActionTest`.
- Coordination: none. `add-realistic-water` is archived and Add Water is already registered in `plugin.xml`
  (`Abyssus.AddWater`, anchored after `Abyssus.AddLight`); this change only moves that registration into the `Add` group.
- Out of scope: the Scene view toolbar (it already groups through `AddAssetGroup` / `AddLightGroup` /
  `AddPathArrangementGroup` / `AddWaterGroup` and is unchanged), the Properties tool window, and any other tree row's menu.
