# Design

## Context

The tree is built from DTOs and JSON nodes by `childrenOf` / `foldToggles` (`projectView/DtoTree.kt`); each row is a
`DtoEntryNode` (`projectView/AbyssusNodes.kt`) whose label is the property name and whose value is appended in the same
text. Icons come from `AllIcons` and `PropertyIcons` / `AssetIcons` / `SceneIcons` (`filetype/AbyssusFileTypes.kt`).
The `ecs` block is a `JsonNode` shown generically, so an entity is `ecs/entities/<id>/components/<Name>Component`.
`EntitySelection.kt` finds an entity row by that path. The view is a `ProjectViewPane` subclass; the board is
"Panels" in https://claude.ai/artifact/FCGmbq4BAJW9PK6uvBDfke. See proposal.md for scope.

## Goals / Non-Goals

**Goals:**
- Same hierarchy and information as the board's tree: labelled folders with counts, named entities, per-kind hues, filter, footer.
- Keep one source of rows (`childrenOf`) so the Properties panel, tree and scene-view picking agree.

**Non-Goals:**
- Pixel parity (monospace values, cards, the board's own toolbar), eye toggles for entities or components, editing, new file-format fields.

## Decisions

1. **Presentation lives in the node, not in the DTOs.** `DtoEntryNode.update` maps a row to (label, secondary text, icon) with
   a small pure function `rowPresentation(entry)`; `scenes` / `assets` become `Scenes` / `Assets` with the element count,
   `ecs` shows `N entities`. Labels are bundle strings. Alternative: rename the properties in the DTOs; rejected, that would change the
   Properties panel and file-bound names.
2. **Entities are folded in the row model.** `childrenOf` for the `ecs` object returns the entities directly (skipping the `entities`
   level) as `DtoRow`s labelled by `NameComponent.name` with the component count; an entity's children are its components, with the
   `Component` suffix stripped. The entry `path` keeps the underlying JSON keys (`.../ecs/entities/<id>`), so identity and toggles
   still address the real location; only the visible level disappears. Alternative: filter at the node, rejected as the path and children would then disagree.
3. **Entity selection follows the folded structure.** `entityVisitAction` now expects `ecs` then the entity row (no `entities`),
   matching by entity id carried on the row. The scene view's pick still calls `selectEntityInAbyssusView`.
4. **Per-kind hues are new SVG icons**, one per kind of the board's icon set (project, scene, light, fog, skybox, ecs, folder, model,
   terrain, transform, material, physics, particles, and a generic component), registered next to the existing ones; component names
   map to a kind by a small table (`Position` / `Transform`, `Render` / `Model`, `Name`, `Type`, `Pickable` fall back to generic).
   Existing icons that already match are kept. Light and dark variants follow the platform's `_dark.svg` convention.
5. **Unused filter is a `ToggleAction` added through `addToolbarActions`** of the pane, so it sits with the platform's own view options.
   The state is stored per project (`PropertiesComponent`) and read by the `assets` node when it builds its children; toggling refreshes the pane.
   Alternative: a custom toolbar above the tree; rejected, it would duplicate the platform's Expand/Collapse buttons.
6. **Footer is a wrapper around the pane's component.** `createComponent` returns a `BorderLayout` panel with the platform component in the centre
   and a one-line footer south. Counts are summed from `AssetReadCache` results for the projects in the view, computed off the EDT and posted back,
   and refreshed when the pane refreshes; the unused filter does not affect them.

## Risks / Trade-offs

- [Dropping the `entities` level breaks code that walks the old path] -> `EntitySelection` and its tests change in the same task group; paths inside `DtoEntry` keep real JSON keys.
- [A wrapped pane component may break `ProjectView` assumptions (scroll, select-in)] -> keep the original component untouched in the centre and cover with the existing `AbyssusProjectViewPaneTest`.
- [Footer cost on large projects] -> read through the existing cache, off the EDT; no extra file reads.
- [Icons drift from the board] -> the Icons board is the reference; hues are listed in one place so they can be tuned.

## Open Questions

- Whether a component `Position` should read as `Transform` as on the board; the file calls it `PositionComponent`. This design keeps the file's name.
