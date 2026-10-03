# Tasks

## 1. Component editor (JSON level)

- [x] 1.1 Add the field descriptor table for the eight modeled kinds (name, type, getter/setter over the codec's typed component) in `ecs/scene/`; verify a unit test lists the expected fields for each kind
- [x] 1.2 Implement `ComponentEditor.add` building a component from codec defaults and rejecting a duplicate kind; verify tests for Light defaults, duplicate rejection and Render with a chosen asset
- [x] 1.3 Implement `ComponentEditor.update` (parse text input, apply through the codec, replace only that component node, `Unchanged` when equal); verify tests for a camera field, a non-number, default-valued write and no-op
- [x] 1.4 Add reference validation (unknown id, own parent, parent cycle, `-1` clears the key); verify tests for each scenario of "References between entities stay valid"
- [x] 1.5 Implement `ComponentEditor.remove` with the dependents check and unmodeled-kind refusal; verify tests for plain removal and a look-at target
- [x] 1.6 Round-trip test on `Main Scene`: after add/update/remove, unmodeled components, `archetypes`, `componentIdentifiers`, `metadata`, key order and formatting are unchanged, and the result loads through `SceneEcsLoader` with no new warnings

## 2. Writing to the scene file

- [x] 2.1 Add `commandAddComponent`, `commandEditComponent`, `commandRemoveComponent` and the validation messages to `AbyssusBundle.properties`; verify the keys are used by the next tasks
- [x] 2.2 Add a `SceneComponentEdits` entry point that runs the editor inside `editSceneJson` and returns the result for the UI; verify a test on a temp scene file covers one undoable command per change and Undo restoring the text
- [x] 2.3 Verify the open Scene view follows an edit: extend `SceneFileEditorTest` so a component change reloads the params without reopening the tab

## 3. Properties panel for entities and components

- [x] 3.1 Add entity and component states to `PanelState` and resolve them from the selected node (`isEntityEntry` / `isComponentEntry`); verify tests for entity, component, and scene/project/setting still showing "Nothing to show"
- [x] 3.2 Render entity and component sections with typed editors from the descriptor table, and read-only JSON with a note for unmodeled components; verify panel tests against `Main Scene` entity `0`
- [x] 3.3 Wire field edits to `SceneComponentEdits`, showing a rejected value's reason beside the field and restoring the old value; verify a panel test for a valid and an invalid edit
- [x] 3.4 Add the panel's "Add component" choice and per-section "Remove"; for Render, choose a project MODEL or TERRAIN asset; verify panel tests for add Light and remove Light
- [x] 3.5 Refresh the panel on scene document and disk changes and show a message when the entity or component is gone; verify tests for a text edit and a deleted component
- [x] 3.6 Keep asset Meta display read-only; verify the existing `AssetPropertiesPanelTest` still passes unchanged

## 4. Abyssus tree actions

- [x] 4.1 Implement "Add Component..." (list of missing modeled kinds, asset chooser for Render) and register it in `plugin.xml`; verify an action test on an entity row, and that other rows do not show it
- [x] 4.2 Implement "Remove Component" for modeled component rows only and register it; verify tests that Light is removable and Pickable is not
- [x] 4.3 Verify the tree refreshes after each action: the entity's `N components` count and child rows change, covered by a tree test

## 5. Integration and docs

- [ ] 5.1 Run `./gradlew test` and verify the whole suite passes
- [ ] 5.2 Run the plugin in the IDE sandbox on the `Untitled` project and verify add, edit, remove, Undo and the Scene view update by hand
- [x] 5.3 Add a CHANGELOG entry and a short README section on editing components; verify the documented steps match the UI labels
