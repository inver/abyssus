# Design

## Context

See proposal.md for motivation. The right-click menus of the Abyssus view are `AbyssusTreeAction<T>` subclasses registered in
`plugin.xml` into the `ProjectViewPopupMenu` group (anchors `first`, `last` or relative to another action). Each action hides
itself by returning null from `targetOf`; `AbyssusTreeAction.update` sets `isVisible` and `isEnabled` from that. The shared-group pattern already exists in the codebase: `AddAssetGroup`, `AddLightGroup`,
`AddPathArrangementGroup` and `addComponentGroup` are `DefaultActionGroup`s built in `perform`, shared between the tree and
the Scene view toolbar.

## Goals / Non-Goals

- Group the Assets-row and Scene-row actions under `Import Model`, `New` and `Add` headers, using the platform's own grouping; Add Water joins `Add`.
- Offer Rename Scene on a standalone `.scene` row as well as a scene listed under a project.
- Keep every action's `targetOf`, `isEnabled` and `perform` unchanged; only the registration and one target predicate change.
- Keep the Scene view toolbar, the Properties tool window and every other tree row's menu untouched.

Non-Goals: reordering within groups, mnemonics, drag and drop, truncating long lists, any new action.

## Decisions

### D1. Group in `plugin.xml`, not by composing groups in `perform`

The Assets-row and Scene-row actions are registered as `<action>` elements in the `<actions>` block of `plugin.xml`. Each one already decides its
own visibility through `targetOf`, so the platform can hide individual children of a group without any code change.

**Approach:** declare three `<group popup="true">` elements with ids `Abyssus.Menu.ImportModel`, `Abyssus.Menu.New` and
`Abyssus.Menu.Add`, each added to `ProjectViewPopupMenu` (anchor `first`; `Add` after `Abyssus.RenameScene`). Labels come from
`AbyssusBundle` (`group.Abyssus.Menu.<Name>.text`), not literal text. The existing `<action>` elements move inside them with
`<add-to-group group-id="Abyssus.Menu.…"/>` and keep a relative `anchor` only where order inside the group matters: Import
FlightGear before Import Model; New UI Asset, New Terrain, New Foliage; Add Asset, Add Path Arrangement, Add Light, Add Water.
`RenameSceneAction` stays a flat item added to `ProjectViewPopupMenu`, above the `Add` group. Water-row actions (Rename Water,
Remove Water) and Regenerate / Add Foliage stay where they are.

**Alternative considered:** build `DefaultActionGroup`s in `perform` (like `AddAssetGroup`). Rejected: the actions already own
their visibility logic and are registered for the toolbar and other entry points; composing in `perform` would require each
action to expose a "build my subgroup" path separate from "show my popup", duplicating the existing group builders and
changing the toolbar wiring.

### D2. Rename Scene matches standalone scenes

`RenameSceneAction.targetOf` today is `(node as? DtoEntryNode)?.value?.takeIf { sceneFileOf(it) != null }`. `sceneFileOf`
(`EnabledToggle.kt`) takes a `DtoEntry` only, so a standalone `.scene` (an `AbyssusAssetNode`) never matched. The helper that
covers both node kinds is `viewableSceneFile(node)` (`AbyssusNodes.kt`), which Add Light, Add Asset and Add Path Arrangement
already use.

**Approach:** the action's target type becomes `VirtualFile` and `targetOf` is `viewableSceneFile(node)`. `perform` can no
longer call `sceneName(entry)`, which needs a `SceneEntry` that a standalone scene lacks; it reads the scene's current `name`
from the file (the cached parse in `AssetReadCache` is enough, no new state) for the dialog's initial value and the
"unchanged" check, then calls `renameScene(project, file, name)` as before. A scene with no `name` starts with an empty field.

### D3. Empty sub-menus

A group whose every child is hidden (e.g. `Add` where only some actions apply) must not render as an empty or disabled entry.
Children hide themselves through `AbyssusTreeAction.update`. Whether the platform then also hides a `popup="true"` group, or
shows it disabled, depends on the group's presentation flags and is not assumed.

**Approach:** verify in a test that a group with no visible child is not visible. If the plain `<group>` shows disabled, register
a small `DefaultActionGroup` subclass (`class` attribute on the `<group>`) that hides itself when no child is visible. Either
way the spec scenario holds.

## Risks / Trade-offs

- [A group header renders even when only one child is visible] → expected; the header names the intent (Import Model / New / Add)
  and is the point of the change.
- [Add Water is registered by the in-progress `add-realistic-water` change] → this change only moves its registration into the
  `Add` group; if that change lands first the `plugin.xml` edit is a rebase, not a conflict of intent.
- [Older IDE builds may render `<group popup="true">` differently] → the grouping is a standard platform action-group feature;
  `plugin.xml` already uses `<group>` elsewhere, so no new API is introduced.
- [Tests assert each action appears alone on its row] → `TreeActionVisibilityTest` is updated to assert the group and its
  children instead (see tasks); a rename test covers the standalone scene.

## Migration Plan

No file migration. The grouping is presentation only; every write path, undo step and document format is unchanged. Rollback is
a revert of the change.

## Open Questions

None.