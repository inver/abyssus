# Proposal

## Why

A path arrangement of 12 railway segments or 10 street lamps fills the `ecs` list of the Abyssus tree with one row per
copy (`Path 634 #0` ... `#11`), which buries the scene's real objects. The arrangement entity already holds the line and
lists its copies, so the copies add rows, not information.

## What Changes

- The Abyssus tree no longer lists the linked copies of a path arrangement under `ecs`. A linked copy is an entity that an
  arrangement lists in `slots` and that points back to it with a `ParentComponent`.
- The arrangement's own row shows `N components, M copies`.
- A detached copy (no `ParentComponent` to the arrangement) is an ordinary entity and stays listed.
- The files, the scene view, picking and the properties panel are unchanged; only the tree rows change.

### Native files

None. Nothing is read or written differently.

## Capabilities

### Modified Capabilities

- `abyssus-project-view`: the `ecs` listing hides linked arrangement copies and the arrangement row counts them.

## Impact

- `lib-core-editor`: `arrange/ArrangementCopies.kt` (`arrangementCopyIds`, `arrangementCopyCount`).
- `plugin-abyssus`: `projectView/DtoTree.kt` (`ecsRows`, entity row text), `AbyssusBundle.properties`.
- Docs: `projectView/README.md`.
- Limit: a hidden copy has no tree row, so picking one in the scene view selects nothing in the tree.
