# Tasks

## 1. Enabled flag: format and loading

- [ ] 1.1 Spike: read how `EcsLoader`, `ComponentRegistry` and `ComponentCodec` bind a component entry's keys, and what an unknown key does today. Record the result under D1 in `design.md` (strip-before-bind is feasible, or the key placement changes and the delta specs are amended). Verify: the note exists and names the class and method that bind fields.
- [ ] 1.2 Add `EnabledFlag` read side in `lib-core` and write side in `lib-core-editor` (D1); admit `enabled` in `AbyssusDocumentFormat` (boolean only, otherwise unsupported and untouched). Verify: `AbyssusDocumentFormatTest` cases for missing, `true`, `false` and `"no"`; `./gradlew :lib-core:test :lib-core-editor:test` pass.
- [ ] 1.3 `EcsLoader` strips the reserved key, keeps the flag on the entity and component, and `EcsWriter` round-trips it; report a registered or schema component that declares a field `enabled` and keep it read-only. Verify: loader round-trip test, registry test for the clash; `./gradlew :lib-core:checkNoSingletons` stays green.
- [ ] 1.4 Document the key in `docs/ai/file-formats.md` ("Toggles", "ecs") and `docs/ai/glossary.md`. Verify: `scripts/check-docs.sh`.

## 2. Enabled flag: runtime

- [ ] 2.1 One `isActive` predicate on entity state; render, light, camera and foliage systems use it (D2). Verify: system tests for disabled light, renderable, entity and a toggle during update without reload (`./gradlew :lib-core:test`).
- [ ] 2.2 `lib-physics` creates no body or shape for disabled physics components or entities. Verify: `./gradlew :lib-physics:test` cases for disabled collider and disabled entity; `checkNoSingletons` and `checkNoEditorCode` stay green.
- [ ] 2.3 Control Line: confirm `PlaneComponent` / `PilotComponent` have no `enabled` field and Control Line tests still pass. Verify: `./gradlew :app-game-control-line:test :app-game-control-line:checkShippedClasspath`.

## 3. Enabled flag: editing and the scene view

- [ ] 3.1 `SetEnabledAction` in `lib-core-editor` (entity and component targets, togglable rule of D6) and its `HeadlessEditing` entry; the plugin calls it through `editSceneJson` as one undoable command. Verify: tests that disable and enable, assert bytes outside the entry unchanged, `enabled` key removed on enable, Undo restores the text, and an unsupported document is refused.
- [ ] 3.2 The scene view draws, shadows, picks and gizmos only active entities; the ray snapshot skips disabled ones (D7). Verify: picking test with a disabled entity in front of an enabled one, a `lib-core-editor` snapshot test, and `./gradlew :plugin-abyssus:test --tests '*SceneViewPanelTest'`.
- [ ] 3.3 Document the editing and view behavior in `lib-core-editor/README.md` and `sceneview/README.md`. Verify: `scripts/check-docs.sh`.

## 4. Panel model

- [ ] 4.1 Add `Header`, `Section` and `Row` model types and `FieldValue.isDefault` in `lib-core-editor` (D3, D5) with strings in `AbyssusEditorBundle.properties`. Verify: model tests for each row kind, units, axes, dim from key absence (an explicitly written default is not dim), and no Swing on the classpath (`NoPlatformClasspathTest`).
- [ ] 4.2 Builders for project, folder, scene, setting, `ecs`, entity, component, UI screen (D8) with breadcrumb, subtitle, badges (`read-only`, `no scene uses this`), `game` flag and class hint from the tree change's pure decision. Verify: one test per builder against the board's section titles, row labels, order and footer text, using the Control Line fixture (`Racer`, `Pilot`, `Sun`, `Field`).
- [ ] 4.3 Asset builders per type (MODEL, TERRAIN, FOLIAGE, TEXTURE, SKYBOX, SKYBOX_PROCEDURAL, SKYBOX_HDR, CLOUDS, UI) with `meta.json` and `additional` sections, read-only tables for layers and screens, and the existing typed editors kept as editable rows. Verify: per-type builder tests, plus the existing terrain, sky and foliage panel tests unchanged and green.
- [ ] 4.4 Section collapse key and `Enabled` state in the model (togglable rule, `<x>Enabled` for settings). Verify: model tests for the togglable matrix (Render, Rigid body, Collider, Light, Foliage, Plane, Pilot yes; Name, Position, Parent no) and settings.

## 5. Panel views

- [ ] 5.1 `plugin/ui` building blocks: `PropertySection`, `FieldRow` kinds, `HeaderPanel`, `BadgeLabel`, dim styling, `DesignColors` pairs for dark and light (D4). Verify: `FieldRowTest` and `PropertySectionTest` (kinds, collapse, dim, tooltip and accessible names), contrast of every pair at least 4.5:1 in a unit test.
- [ ] 5.2 Rebuild `AssetPropertiesPanel` on the model: header with breadcrumb, badges, title-bar Options and Hide, empty state; remove the Name / Value table and `columnHeader`. Verify: `PropertiesPanelTest` for each selection kind and the empty state; existing refresh, stale-edit and undo tests pass unchanged.
- [ ] 5.3 Port the entity, component, scene, project and water views and the asset views to the new rows, keeping `FieldEditors` commit, refuse and revert behavior; the asset reference row hosts the existing Browse (tree change task 4.1) as the 26 px button. Verify: the existing `EntityDetailsView`, `SceneDetailsView`, `ProjectDetailsView`, `SkyboxAssignTest` and component-edit tests pass; a rejected value still keeps the previous value and shows the reason.
- [ ] 5.4 `Enabled` check box wired to `SetEnabledAction` for components and `<x>Enabled` for settings; the section dims and stays editable. Verify: `PropertiesPanelTest` unchecks Collider (flag written, one undo step), unchecks Fog (`fogEnabled` only), no box on Name.
- [ ] 5.5 Update `properties/README.md`. Verify: `scripts/check-docs.sh`.

## 6. Tree eyes

- [ ] 6.1 Eye on entity and component rows through the existing `RowActions`, same action as 3.1; disabled subtree dimmed with gray icons; none on GROUP, Name, Parent, Position. Verify: `RowActionsTest` layout and togglable matrix, `AbyssusNodesTest` for the dimmed subtree, and a toggle test that Undo restores the row.
- [ ] 6.2 Document in `projectView/README.md` and `docs/ai/glossary.md`. Verify: `scripts/check-docs.sh`.

## 7. UI tests and baselines (Remote Robot, canonical container)

- [ ] 7.1 Page-object helpers for header text, badges, section titles, row labels, field geometry and section state in `PageObjects.kt`. Verify: `UiStartupTest` extended to read the header of a selected entity.
- [ ] 7.2 `PropertiesLayoutUiTest`: label column 150, field height 24, Browse 26, section header 34, dim defaults, game badge, breadcrumb and subtitle for entity, game component, folder, `ecs`, asset, project and the empty state. Verify: run the class in the container.
- [ ] 7.3 `PropertiesEnabledUiTest`: tree eye and section check box stay in sync, one undo step, a disabled entity is not picked in the view. Verify: run the class in the container.
- [ ] 7.4 Add scenarios for each selection kind to `ScreenshotInventory` and generate baselines only for them with `-PupdateUiScreenshots=true`; inspect every image for clipping and correct content. Verify: `BaselineInventoryTest` and a comparison run without update flags pass.
- [ ] 7.5 Document the new classes and scenarios in `docs/ai/testing.md`. Verify: `scripts/check-docs.sh`.

## 8. Integration

- [ ] 8.1 runIde on a copy of the Control Line project (never the committed folder): compare each selection kind with the board in dark and light themes, toggle components and entities, undo, edit a dim field. Record the result and the accepted font differences in the PR.
- [ ] 8.2 Run `openspec validate redesign-abyssus-properties --strict`, then `./gradlew check`, `./gradlew :plugin-abyssus:verifyPlugin`, the full UI suite twice in fresh canonical sandboxes without baseline writes, and `scripts/check-docs.sh`. Report unrelated failures explicitly without silently fixing them.
