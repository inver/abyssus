# Tasks

## 1. Group the Assets-row and Scene-row menus in plugin.xml

- [ ] 1.1 Declare the `Abyssus.Menu.ImportModel` and `Abyssus.Menu.New` popup groups (added to `ProjectViewPopupMenu`, anchor first)
  and move Import FlightGear Aircraft, Import Model, New UI Asset, New Terrain and New Foliage into them: verify
  `plugin.xml` has no Assets-row action added directly to `ProjectViewPopupMenu`
- [ ] 1.2 Declare the `Abyssus.Menu.Add` popup group (after `Abyssus.RenameScene`) and move Add Asset, Add Path Arrangement,
  Add Light and Add Water into it, leaving Rename Scene flat: verify `plugin.xml` shows the group, the child order and Rename
  Scene outside it
- [ ] 1.3 Add `group.Abyssus.Menu.ImportModel.text`, `.New.text` and `.Add.text` to `AbyssusBundle.properties`: verify the
  groups use the bundle keys, not literal text
- [ ] 1.4 Build the plugin: run `./gradlew :plugin-abyssus:verifyPluginStructure` and confirm no descriptor error

## 2. Offer Rename Scene on a standalone scene

- [ ] 2.1 Make `RenameSceneAction` target the scene `VirtualFile` through `viewableSceneFile(node)` and read the current name
  from the file in `perform`: verify `RenameSceneActionTest` covers a standalone scene and a scene listed under a project
  (offered, initial name, one undoable rename) and a scene with no `name`

## 3. Update the menu-visibility tests

- [ ] 3.1 Update `TreeActionVisibilityTest` to assert the grouped structure: verify it checks the `Import Model` / `New` /
  `Add` groups and their children on the Assets and Scene rows (using the registered action ids), that Rename Scene is
  offered on a standalone scene row and not on other rows, and that the existing "offered alone on its row" assertions are
  replaced
- [ ] 3.2 Cover empty groups: verify a test shows a group with no visible child is not visible, and if a plain `<group>` is
  shown disabled, register a hiding `DefaultActionGroup` subclass for it
- [ ] 3.3 Run `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.TreeActionVisibilityTest'`
  and the `RenameSceneActionTest` class: verify both pass

## 4. Documentation and full check

- [ ] 4.1 Update `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/projectView/README.md` (the
  `RenameSceneAction` row and the action list) to describe the grouped menus: verify it lists the `Import Model` / `New` /
  `Add` groups and Rename on any scene row
- [ ] 4.2 Open a copy of a project with `./gradlew :plugin-abyssus:runIde`: verify by hand that the Assets and Scene menus show
  the groups and that Rename works on a standalone scene
- [ ] 4.3 Run `scripts/check-docs.sh` and `./gradlew check` for the affected modules: verify both pass
