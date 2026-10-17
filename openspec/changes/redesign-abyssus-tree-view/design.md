# Design

## Context

The tree is `AbyssusProjectViewPane` over `AbyssusRootNode` -> `AbyssusAssetNode` (one per `.abss` / standalone `.scene`) ->
`DtoEntryNode` (everything below). Children come from `childrenOf(dto)` (Jackson bean properties of `ProjectDto` /
`SceneDto`), with `foldToggles` and `UnusedFilter`; icons from `entryIcon`; right-edge actions are painted by `EyeTree`
(`RowActions.kt`). A `DtoEntry` is identified by its path inside the asset, and `reselect`, `EntitySelection`,
`selectAssetInAbyssusView` and the unused filter all rely on those paths. See proposal.md for motivation and scope; the
mockup is the "Abyssus tree and Properties" and "Icon set" boards.

## Goals / Non-Goals

**Goals:**
- The mockup's structure and painting, checked by UI tests against a real IDE and by headless tests where no IDE is needed.
- Folder rows are pure view rows: no new document reads or writes.
- Row identity stays path-based, so refresh, reselect and picking from the scene view keep working.

**Non-Goals:** truncation of long lists, drag and drop, new tree actions, restyling Properties beyond the Browse button.

## Decisions

### D1. Folder rows are synthetic `DtoRow`s over the existing DTO, not new DTO fields
`childrenOf(ProjectDto)` stops returning the bean properties. A new Swing-free `projectRows(ProjectDto)` returns three rows:
`Scenes` (the `scenes` list), `UI` (assets of type `UI`) and `Assets` (the rest), the latter two built from a `FolderRow`
value carrying the asset list. `Assets` expands into `TypeGroup` rows, one per `meta.type`, each holding its assets. Paths
stay deterministic: `scenes`, `ui`, `assets`, `assets/<TYPE>`, then the asset's own segment, so an asset's entry path gains
`assets/<TYPE>/` before its name. `name` is simply not emitted. *Alternative:* add `ui` and per-type fields to `ProjectDto`;
rejected because the DTO mirrors the file and these groupings are presentation.

### D2. Group order and names
Type groups use a fixed order (MODEL, TERRAIN, FOLIAGE, TEXTURE, SKYBOX\*, CLOUDS, then any other type alphabetically) and
a display label table (`Models`, `Terrains`, `Foliage`, `Textures`, `Skyboxes`, `Clouds`; an unknown type shows its
`meta.type` name capitalised). SKYBOX, SKYBOX_PROCEDURAL and SKYBOX_HDR share the `Skyboxes` group; their rows keep
distinct icons. Labels live in `AbyssusBundle.properties`.

### D3. Unused filter applies inside groups
`UnusedFilter.apply` filters the asset lists before grouping, so a group with no unused asset disappears and counts follow
what is listed. The footer keeps counting from `footerCounts`, unaffected.

### D4. Icons and the mockup's Icon set
Every glyph path in the Icon set board is copied into a 16 px SVG under `src/main/resources/icons/` with a `_dark` variant
only where the stroke color is not theme-neutral; hues come from the board (`#3fb8c9` project, `#7bd88f` scene, `#8c9cff` UI,
`#d4b483` assets, ...). Holders `AbyssusProjectIcons`, `AssetIcons`, `ComponentIcons`, `EntityIcons`, `PropertyIcons` get the
new entries; `ComponentIcons.forComponent` also covers game components, which fall back to the Plane / Pilot glyphs only for
those names and to the generic glyph otherwise. A headless `TreeIconInventoryTest` lists every icon the tree may show and
fails with the file name on a missing or orphaned resource (same pattern as the toolbar change's `IconInventoryTest`).

### D5. `game` badge
`ComponentRegistry` already separates built-in from registered components. The badge is another `RowAction`-style painted
fragment (rounded 1 px outline, 10.5 px text, pink `#f47fb0` on border `#6a3a52`, dark/light pairs in `DesignColors`), placed
after the label, not at the right edge. `DtoEntry.equals` includes the flag so a registration change repaints. The decision
"built-in vs game" is a pure function over the component's class name and the registry's built-in set, unit-tested without UI.

### D6. Painting changes in `RowActions`
`unused` becomes amber text with the row dimmed (`UnusedBadge` shrinks to text; its outline code goes), the eye is 14 px with
`#548af7` / `#6f737a` (new `EyeIcons` SVGs), the `ChooseButton` class and its `choose_swap_icon*.svg` leave the tree.
`GrayKeepingRenderer` is kept for disabled rows. Row height and indent come from the platform renderer (new UI: 24 px at
scale 1); the platform's own tree UI supplies the 16 px level indent through `JBUI.CurrentTheme.Tree`, which the geometry test
verifies instead of the code forcing values. If the test finds a mismatch the renderer sets `ui.indent` on the tree UI.

### D7. Header toolbar
`addToolbarActions` keeps the platform actions the pane inherits (Expand All, Collapse All, Options, Hide are platform
actions) and gets their icons replaced via the pane's presentation, plus the unused toggle with the amber pressed state
(`ToggleAction.Presentation` selected icon). The order and names are asserted by a UI test; nothing in the platform
toolbar is rebuilt by hand.

### D8. Skybox Browse in Properties
The scene section of Abyssus Properties (Swing view over `lib-core-editor`'s panel model) gets a `skybox` row model with
`browse: (() -> Unit)?`, null for a standalone scene. The Swing view paints the field plus a 26 px Browse button that calls
`chooseSkybox` (same dialog, same `setSkybox` write through `editSceneJson`). The tree's `skybox` row refreshes through the
existing `AbyssusSceneEdited` topic, so no new listener is needed. The panel model stays free of Swing; its `skybox` row is
unit-tested in `lib-core-editor`.

### D9. Baselines and overlap with other changes
This change regenerates `tree-expanded.png` and `tree-fog-off.png` and adds new tree scenarios. `redesign-scene-view-toolbar`
also touches icons and baselines; the two can land in either order, but the second to merge regenerates only the baselines it
changes (the harness's selected-scenario update). `add-remote-asset-library` adds no tree rows today; re-check at apply time.
The `sceneview` icon inventory is separate from the tree inventory, so neither test sees the other's SVGs.

### D10. Tests
Headless: `ProjectRowsTest` (order, no `name`, grouping, counts, UI vs Assets, paths, no truncation), `TypeGroupOrderTest`,
`UnusedFilterTest` (groups hide), `GameBadgeTest`, `TreeIconInventoryTest`, `FooterCountsTest` wording. Platform:
`AbyssusNodesTest` updated for the new shape. UI (Remote Robot, canonical container): `TreeStructureUiTest` (visible row order
and labels), `TreeIconsUiTest` (the renderer's icon per row kind through the icon's resource path), `TreeGeometryUiTest`
(row heights, indent, eye and `unused` positions from painted bounds), `TreeStatesUiTest` (selected, disabled, unused, game
badge), `TreeHeaderUiTest` (button names, order, Collapse/Expand All, filter pressed state), and `SkyboxChooserUiTest` /
`PropertiesUiTest` changed to open the chooser from Browse. Screenshot scenarios added: `tree-collapsed`, `tree-assets-groups`,
`tree-unused-filter`, `tree-selected-disabled`, `tree-header`.

## Risks / Trade-offs

- [Entry paths change for assets, so code that matches by path breaks] -> D1 keeps paths deterministic; `selectAssetInAbyssusView`,
  `reselect`, `UnusedFilter` and `AssetSelection` are updated and covered by tests; grep for `"assets"` string matches in the task.
- [The Properties panel's asset lookup assumed assets directly under `assets`] -> `AssetSelection` resolves by the asset value,
  not by its parent name; a test selects an asset inside a type group.
- [The platform row height may not be 24 px in every theme/scale] -> the geometry test asserts it at scale 1 in the canonical
  environment and documents the scale dependence; no hard-coded override unless the test fails.
- [Removing Choose from the tree surprises current users] -> the Browse button is the replacement and is in the same
  Properties window; the release note lists it as a UI change.
- [127-entity scenes make a long tree] -> unchanged from today; folder rows at least let the user collapse Assets and UI.

## Migration Plan

No file migration. Persisted state: the unused filter key and the platform's expanded-path state; stale expanded paths for
assets simply do not match and collapse. Rollback is a revert of the change; no data was written.

## Open Questions

- Whether `Skyboxes` should also list a project's SKYBOX\* assets that are unused first; the mockup shows no ordering rule, so
  alphabetical within the group is used.
