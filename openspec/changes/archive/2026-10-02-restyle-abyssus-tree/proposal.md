# Proposal

## Why

The "Panels" board of the design canvas (https://claude.ai/artifact/FCGmbq4BAJW9PK6uvBDfke) redesigns the Abyssus view:
labelled `Scenes` / `Assets` folder rows with counts, a distinct icon hue per kind, entities named with their component
count and expanding straight into components, an "unused assets" filter and a counts footer. The tree today shows raw
JSON keys (`scenes`, `assets`, `ecs` > `entities` > `0` > `components` > `NameComponent`), which is hard to scan.

## What Changes

- Rows: `scenes` and `assets` read `Scenes` and `Assets` with their element count as the secondary value; `ecs` shows
  `N entities`; icon hue per node kind (project, scene, light, fog, skybox, ecs, folder, model, terrain, component kinds);
  secondary text (values, counts) in the gray text style; the `unused` badge and dimming of unused and disabled rows stay.
- ECS: an entity is listed directly under `ecs` (the `entities` level is dropped), labelled with its `NameComponent` name
  (the id when it has none) and `N components` as value; it expands into its components, named without the `Component`
  suffix, each expanding into its fields as today. Selecting an entity in the scene view still selects its row.
- Unused filter: a toggle in the view's toolbar options, "Show Only Unused Assets", that limits the `Assets` list to
  assets no scene reaches.
- Footer: a strip under the tree reading `N scenes · N assets · N unused`, counted over the projects in the view.
- Eye toggles keep working on the rows that have an `xxxEnabled` flag; no new flag is introduced.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `abyssus-project-view`: asset node structure (folder labels and counts, entity rows, no `entities` level) and presentation
  (icon hues, secondary text); new requirements for the unused filter and the footer.

## Impact

- Code: `projectView/AbyssusNodes.kt`, `projectView/DtoTree.kt` (labels, counts, entity rows), `projectView/EntitySelection.kt`
  (entity row path), `projectView/AbyssusProjectViewPane.kt` (toolbar toggle, footer wrapper), `filetype/AbyssusFileTypes.kt`
  and `resources/icons` (new per-kind icons), `AbyssusBundle.properties`.
- Tests: `AbyssusViewTest`, `EntitySelectionTest` and the tree tests follow the new structure; new tests for labels,
  entity rows, the filter and the footer counts.
- Dependencies: none.

## Out of scope and assumptions

- The board's monospace values, toolbar buttons and bordered cards cannot be reproduced inside the platform tree; the
  platform font and its own Expand/Collapse buttons are used, matching hierarchy and colour roles instead of pixels.
- The board shows eye toggles on entities and components, but the scene format has no enabled flag for them, so none is
  added; adding one would be a file-format change and a separate proposal.
- The ECS card editor and the asset picker boards are separate work (the skybox chooser is `pick-scene-skybox`).
- Counts in the footer sum all `.abss` projects shown in the view; a standalone `.scene` counts as one scene.
