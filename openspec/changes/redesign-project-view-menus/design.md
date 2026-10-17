# Design

## Context

See proposal.md for motivation. The right-click menus of the Abyssus view are `AbyssusTreeAction<T>` subclasses registered
in the `<actions>` block of `plugin.xml` into the `ProjectViewPopupMenu` group, anchored `first`, `last` or relative to
another action. Each action hides itself by returning null from `targetOf`, and `AbyssusTreeAction.update` derives
`isVisible`/`isEnabled` from that, so a row shows only the actions made for it. This `plugin.xml` has no `<group>`
elements yet; grouping is a descriptor feature used here for the first time.

The platform's `com.intellij.ide.actions.NonTrivialActionGroup` is a `DefaultActionGroup` whose update hides it
(`presentation.setEnabledAndVisible(!ActionGroupUtil.isGroupEmpty(this, e))`) whenever none of its children is enabled
and visible — the "never an empty or disabled header" behavior the proposal asks for, with no plugin code. Its semantics
were verified against the bundled IDE 2025.2.4 (`app-client.jar`): `update` runs on the background thread and
`isGroupEmpty` counts only children whose presentation is enabled and visible.

All logic in this change is platform-level (actions and descriptor data); there is no Swing, GL or libGDX code involved,
and nothing new runs off the EDT except the platform's own group update.

## Goals / Non-Goals

- Group the Assets-row actions under `Import Model` and `New`, and the Scene-row actions under `Add`, keeping every other
  row's menu as it is.
- Re-anchor the actions whose `relative-to-action` points into a grouped action, so none silently moves to the menu end.
- Offer Rename Scene on a standalone `.scene` row as well as on a scene listed under a project, starting the dialog from
  the scene's current name.
- Keep every action's `isEnabled` and `perform` semantics; only targets, name lookup and registration change.

Non-Goals: any new action, mnemonics, drag and drop, the water/component/UI-asset rows, the Scene view toolbar, the
Properties tool window.

## Decisions

### D1. Declare the groups in `plugin.xml` with fixed anchors

Three `<group popup="true" class="com.intellij.ide.actions.NonTrivialActionGroup">` elements, declared after the
`Abyssus.RenameScene` action so the group anchored to it comes later in the file:

| Group id | Anchor in `ProjectViewPopupMenu` | Children, in order, with their anchor in the group |
|---|---|---|
| `Abyssus.Menu.ImportModel` | `first` | `Abyssus.ImportFlightGear` (`first`), `Abyssus.ImportModel` (`after Abyssus.ImportFlightGear`) |
| `Abyssus.Menu.New` | `after Abyssus.Menu.ImportModel` | `Abyssus.NewUiAsset` (`first`), `Abyssus.NewTerrain` (`after Abyssus.NewUiAsset`), `Abyssus.NewFoliage` (`after Abyssus.NewTerrain`) |
| `Abyssus.Menu.Add` | `after Abyssus.RenameScene` | `Abyssus.AddAsset` (`first`), `Abyssus.AddPathArrangement` (`after Abyssus.AddAsset`), `Abyssus.AddLight` (`after Abyssus.AddPathArrangement`), `Abyssus.AddWater` (`after Abyssus.AddLight`) |

Every action stays a top-level `<action>` element; only its `<add-to-group>` changes. Labels come from `AbyssusBundle`
through the group-id convention (`group.Abyssus.Menu.ImportModel.text` and so on), not literal text.

Two `relative-to-action` anchors pointed at actions that move into the `Add` group and would be left pending (the
platform then renders the action last): `Abyssus.RegenerateArrangement` re-anchors after `Abyssus.Menu.Add`, and
`Abyssus.AddFoliage` after `Abyssus.RegenerateArrangement`. `Abyssus.NewUiAsset` drops its old anchor and becomes `first`
in `New`. Every other row's registrations (`RenameWater`/`RemoveWater`, `AddComponent*`,
`Register`/`UnregisterUiAsset`, `NewWeatherPreset`) stay as they are.

**Alternative considered:** compose `DefaultActionGroup`s in `perform` (as the toolbar does). Rejected: the actions are
descriptor-registered, own their visibility, and are shared with the toolbar; composing in `perform` would duplicate the
toolbar builders and change the toolbar wiring without gaining anything.

### D2. Rename Scene targets the scene file and reads the name from it

`viewableSceneFile(node)` (`AbyssusNodes.kt`) already covers both a scene listed under a project (`DtoEntryNode`) and a
standalone `.scene` (`AbyssusAssetNode`) — the same helper the Add actions use. The action becomes
`AbyssusTreeAction<VirtualFile>` with `targetOf = viewableSceneFile(node)`.

`perform` no longer has a `SceneEntry`, so the dialog's initial name and the "unchanged" check come from a new internal
helper `sceneNameOf(project, file)` = `SceneDocumentCache.of(project).read(file)?.scene?.name`: the same cached read the
Add actions use, unsaved editor text included, no new state. A scene with no name starts with an empty field.
`renameScene(project, file, name)` stays the single write (one undoable `editSceneJson` command) and runs on the EDT as
before. `sceneName(entry)` loses its last production caller and is removed; `AbyssusViewTest` reads through
`sceneNameOf` instead.

### D3. Empty groups hide through the platform class

`NonTrivialActionGroup.update` sets `enabledAndVisible = !ActionGroupUtil.isGroupEmpty(this, e)` and `isGroupEmpty`
counts only enabled and visible children, so a group with no enabled-and-visible child — including one whose actions are
all merely disabled — is not shown at all. No plugin group class and no test-only hiding logic is written.

The menu-visibility test cannot drive that update directly: `ActionGroupUtil.isGroupEmpty` needs an update session and a
`TestActionEvent` carries an empty one (`UpdateSession.EMPTY` throws). The test instead pins the registered class
(`ActionManager.getAction(id)` is a `NonTrivialActionGroup`) and its children order, and leaves the hiding behavior to
the platform.

**Trade-off:** a group all of whose actions are disabled is hidden, not shown disabled — that is the proposal's
requirement ("never an empty or disabled header"), but it means e.g. `Add` on a scene whose text cannot be read as a
native scene (every Add action disabled) disappears entirely.

## Risks / Trade-offs

- [A `relative-to-action` left pointing into a moved action drops the action to the menu end] → the D1 table re-anchors
  every such action; task 1.3 verifies no leftover relative anchor crosses a group boundary.
- [A group header renders even with a single enabled child] → expected; the header names the intent (Import Model / New /
  Add).
- [Tests drive unregistered action instances and cannot observe group hiding] → the per-action "offered on its row"
  assertions stay; group assertions pin class and child order; hiding is the platform class's contract.
- [Rename without a `name`] → the dialog starts empty and `renameScene` writes only when a name is given, which is
  already its behavior.

## Migration Plan

No file migration. Presentation only: every write path, undo step and document format is unchanged. Rollback is a revert
of the change.

## Open Questions

None.
