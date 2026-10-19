# Tasks

## 1. Group the Assets-row and Scene-row menus in plugin.xml

- [x] 1.1 Declare the `Abyssus.Menu.ImportModel` and `Abyssus.Menu.New` groups
  (`class="com.intellij.ide.actions.NonTrivialActionGroup"`, `popup="true"`) with the anchors from the design and move
  Import FlightGear Aircraft, Import Model, New UI Asset, New Terrain and New Foliage into them: verify `plugin.xml` no
  longer adds any of those actions directly to `ProjectViewPopupMenu`
- [x] 1.2 Declare the `Abyssus.Menu.Add` group after `Abyssus.RenameScene` and move Add Asset, Add Path Arrangement,
  Add Light and Add Water into it, leaving Rename Scene flat: verify `plugin.xml` shows the group, the child order and
  Rename Scene outside it
- [x] 1.3 Re-anchor `Abyssus.RegenerateArrangement` after `Abyssus.Menu.Add`, `Abyssus.AddFoliage` after
  `Abyssus.RegenerateArrangement`, and give `Abyssus.NewUiAsset` its `first` anchor inside `New`: verify no
  `relative-to-action` in `plugin.xml` still points at an action that is no longer in the same group
- [x] 1.4 Add `group.Abyssus.Menu.ImportModel.text`, `group.Abyssus.Menu.New.text` and `group.Abyssus.Menu.Add.text` to
  `AbyssusBundle.properties`: verify the groups use the bundle keys, not literal text
- [x] 1.5 Extend `TreeActionVisibilityTest` with the registered group structure — `ActionManager.getAction(id)` is a
  `NonTrivialActionGroup` and its children are the expected action ids in order — keeping its per-action "offered on its
  row" assertions: verify
  `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.TreeActionVisibilityTest'` passes
- [x] 1.6 Update the menu part of
  `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/projectView/README.md` to describe the three
  groups and their contents: verify the README names `Import Model`, `New` and `Add` and what each contains
- [x] 1.7 Run `./gradlew :plugin-abyssus:verifyPluginStructure`: verify the descriptor is accepted

## 2. Offer Rename Scene on a standalone scene

- [x] 2.1 Make `RenameSceneAction` an `AbyssusTreeAction<VirtualFile>` with `targetOf = viewableSceneFile(node)`, read
  the dialog's initial name through a new internal `sceneNameOf(project, file)`
  (`SceneDocumentCache.of(project).read(file)?.scene?.name`), keep `renameScene` as the single write, and remove the
  now-unused `sceneName(entry)` (switch `AbyssusViewTest` to `sceneNameOf`): verify
  `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.AbyssusViewTest'` passes
- [x] 2.2 Add `RenameSceneActionTest` covering a standalone `.scene` row and a scene listed under a project (offered on
  both, the initial name comes from the file, one undoable rename writes only `name`) and non-scene rows: verify
  `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.RenameSceneActionTest'` passes
- [x] 2.3 Update the `RenameSceneAction` row in `projectView/README.md` to say it is offered on any scene row, including
  standalone scenes: verify the README matches the action

## 3. Integration checks

- [ ] 3.1 Check both menus by hand in `./gradlew :plugin-abyssus:runIde` on a copy of a project (never the committed
  test fixture): verify the Assets row shows Import Model then New, a scene row shows Rename Scene then Add, Add Asset
  and Add Path Arrangement are disabled with no model or terrain, no empty sub-menu shows anywhere, and a standalone
  scene can be renamed
- [ ] 3.2 Run `scripts/check-docs.sh` and `./gradlew check`: verify both pass
