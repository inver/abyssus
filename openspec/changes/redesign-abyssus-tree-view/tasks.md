# Tasks

## 1. Icons

- [ ] 1.1 Copy the Icon set board's glyph paths for the project, Scenes / UI / Assets folders, folder, scene, UI screen, texture, procedural sky, clouds, UI assets, name, position, render, parent, rigid body, collider, constraint, light, foliage, path arrangement, plane, pilot, eye on/off and expand/collapse/unused/options/hide into 16 px SVGs (with `_dark` where not theme-neutral) and register them in the icon holders. Verify with `TreeIconInventoryTest` (headless, `projects/plugin-abyssus/src/test`): `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.TreeIconInventoryTest'` passes and fails naming the file for a missing listed icon or an orphaned tree icon.
- [ ] 1.2 Document icon sources and naming in `projectView/README.md`. Verify with `scripts/check-docs.sh`.

## 2. Structure

- [ ] 2.1 Add the Swing-free `projectRows` / `FolderRow` / `TypeGroup` model (D1, D2) with deterministic entry paths, no `name` row and no truncation, and wire `AbyssusAssetNode` / `childrenOf(ProjectDto)` to it. Verify with `ProjectRowsTest` (order Scenes, UI, Assets; counts; MODEL x3 + TERRAIN + SKYBOX_PROCEDURAL grouping; UI assets under UI with screens; 127 entities all listed; project without `scenes` folder) and `TypeGroupOrderTest`.
- [ ] 2.2 Make the unused filter apply before grouping (D3). Verify with `UnusedFilterTest`: group with no unused asset is hidden, counts equal listed rows, scenes unaffected, footer unchanged.
- [ ] 2.3 Update every path-based consumer (`TreeReselect`, `EntitySelection`, `AssetSelection`, `selectAssetInAbyssusView`, `NewWeatherPresetAction`, `NewTerrainAction`, `ImportModelAction` selection after create, `UiScreenOpener`) for the grouped paths; grep for `"assets"` and `"scenes"` string matches. Verify with the updated `AbyssusNodesTest` and a test that selects an asset inside a type group and reselects it after an edit.
- [ ] 2.4 Folder and type-group rows: labels and counts from `AbyssusBundle.properties`, folder icons, no actions or context menu entries (Add Component etc. stay off them). Verify with `ProjectRowsTest` labels and an `AbyssusTreeActionTest` case that right-clicking a folder offers no component or rename action.
- [ ] 2.5 Update `projectView/README.md` (pieces, row identity, new tree shape) and `docs/ai/glossary.md` node names. Verify with `scripts/check-docs.sh`.

## 3. Row painting and header

- [ ] 3.1 Implement `game` badge (D5) with a pure built-in-vs-game decision, include it in `DtoEntry.equals`, and the amber `unused` text with the dimmed row, the 14 px eye colors, and remove `ChooseButton` and its icons from the tree (D6). Verify with `GameBadgeTest` (built-in Render no badge, registered Plane badge), `RowActionsTest` (layout order and widths, eye right of `unused`, no Choose action on a skybox row) and `DesignColors` pairs reviewed for contrast in dark and light.
- [ ] 3.2 Header toolbar (D7): icons, order Expand All, Collapse All, unused filter, Options, Hide, amber pressed state for the filter, the 28 px footer and its amber unused count. Verify with `AbyssusFooterTest` wording (`1 scene`, `N assets`, `N unused`) and a `UnusedFilterActionTest` for the selected icon state; the order is asserted again in 5.4.
- [ ] 3.3 runIde check on a copy of `Untitled` (never the committed folder): the tree matches the Panels board in dark and light themes; toggle each eye; select disabled and unused rows; Collapse All, Expand All and the unused filter. Record the result in the PR.

## 4. Skybox Browse in Properties

- [ ] 4.1 Add the scene `skybox` row with an optional `browse` to the panel model in `lib-core-editor` and paint the field with a 26 px Browse button in the Properties view, calling `chooseSkybox` (D8); none for a standalone scene. Verify with a `lib-core-editor` panel-model test (`./gradlew :lib-core-editor:test --tests '*ScenePanelModelTest'`), a plugin `PropertiesPanelTest` (button present for a project scene, absent for a standalone one, tooltip and accessible name set) and `SkyboxPickerModelTest` unchanged and green.
- [ ] 4.2 Assignment from Browse updates the field and the tree row, one undo step, writes only `skyboxName` through `editSceneJson`. Verify with a `SkyboxAssignTest` case (bytes identical apart from `skyboxName`, Undo restores) and `scripts/check-docs.sh` after documenting Browse in `projectView/README.md`.

## 5. UI tests (Remote Robot, canonical container)

- [ ] 5.1 Add tree page-object helpers (visible rows with depth, row icon resource path, row bounds, eye and `unused` bounds, badge presence) to `PageObjects.kt` without desktop coordinates. Verify with `UiStartupTest` extended to read the first rows' depth and icon path, and `scripts/run-ui-tests-docker.sh` selecting it.
- [ ] 5.2 `TreeStructureUiTest`: Scenes, UI, Assets order under the project row; no `name:` row; type groups with counts under Assets; every asset of the `Untitled` fixture appears exactly once; scene, ECS and entity rows as before; switching views does not duplicate rows. Verify by running the class in the container.
- [ ] 5.3 `TreeIconsUiTest`: the icon resource of each row kind (project, three folders, type groups, scene, ambient, fog, skybox, ecs, each entity type in the fixture, each component kind in the fixture, each asset type, UI screen) equals the expected one from the inventory, and no two kinds that the inventory marks distinct share an icon. Verify by running the class in the container and by temporarily swapping one icon to see the test name the row.
- [ ] 5.4 `TreeGeometryUiTest` and `TreeHeaderUiTest`: row height 24 and indent 16 at scale 1, eye 14 px and right of the `unused` text, no overlap between a row's painted actions, header buttons named and ordered as the spec, Collapse All then Expand All, the filter's pressed state. Verify by running both classes in the container.
- [ ] 5.5 `TreeStatesUiTest`: selected row color, disabled fog row and its children gray with dimmed icon, unused asset amber and dimmed, a registered game component badge (`Plane` in a copy of the Control Line project, or a test-registered one). Verify by running the class in the container.
- [ ] 5.6 Update `SkyboxChooserUiTest` and `PropertiesUiTest` to open the chooser from the Browse button (none in the tree row), check the three fixture skyboxes plus None, filtering, Cancel, assignment of `skybox_default` and Undo. Verify by running both classes in the container; saved changes touch only `skyboxName`.

## 6. Baselines and integration

- [ ] 6.1 Add the scenarios `tree-collapsed`, `tree-assets-groups`, `tree-unused-filter`, `tree-selected-disabled`, `tree-header` to `ScreenshotInventory`, regenerate `tree-expanded` and `tree-fog-off` and the new baselines on the canonical environment with `-PupdateUiScreenshots=true` for the selected scenarios only, and inspect each image for correct content and no clipped layout. Verify `BaselineInventoryTest` passes and a comparison run without update flags passes.
- [ ] 6.2 Run the full UI suite twice in fresh canonical sandboxes without baseline writes. Verify both pass, baselines and source fixture hashes are unchanged, and every scenario was compared rather than skipped.
- [ ] 6.3 Document the new scenarios, the tree classes and the Browse-based chooser flow in `docs/ai/testing.md`. Verify with `scripts/check-docs.sh`.
- [ ] 6.4 Run `openspec validate redesign-abyssus-tree-view --strict`, then `./gradlew check`, `./gradlew :plugin-abyssus:verifyPlugin` and `scripts/check-docs.sh`. Report unrelated failures explicitly without silently fixing them.
