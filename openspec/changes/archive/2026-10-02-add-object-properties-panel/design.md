# Design

## Context

The Abyssus view lists a project's assets as `AssetInfo` entries (`dto/ProjectAssets.kt`), bound from each asset
folder's `meta.json` (`ProjectLayout.META_FILE`) but keeping only `name`, `uuid`, `type`, `references` and `unused`.
The full file has `version`, `lastModified`, `uuid`, `type` and a type-specific `additional` object; `MetaBase<T>` /
`SkyboxMeta` model it for loading, and `ProjectAssetFiles` reads it as a `JsonNode`. Tree nodes are `DtoEntryNode`
holding a `DtoEntry`. See proposal.md for scope.

Visual design: the "Panels" board of https://claude.ai/artifact/FCGmbq4BAJW9PK6uvBDfke, summarised under UI design below.
The ECS card editor, asset picker, row eye toggles, unused filter and expand/collapse-all on other boards are outside this change.

## UI design

- **Header block:** icon for the asset type (skybox, model, terrain; generic fallback; one hue per kind, as in the tree), asset name, and `<type> asset · read-only` in secondary text, separated from the table by a divider.
- **Table:** a `NAME` / `VALUE` header row (small, uppercase, secondary colour); name column about 150px, values monospace. Order: `version`, `lastModified`, `uuid`, `type`, then an `additional` heading row in the accent colour with a divider above it, then the `additional` fields indented.
- **Face previews (skybox only):** below the table, a "Face previews" heading and a 3-column grid of the six faces; each cell is the face image with the face name and file name under it.
- **Empty state:** centred icon, `Nothing selected.` or `Nothing to show: <name> is a scene / the project file / an entity or setting.`, and a secondary hint "Select a skybox, model or terrain under Assets to see its meta.json."
- **Theme:** colours come from the IDE theme, so light and dark both work; the board's dark palette is a reference, not literal values.

## Goals / Non-Goals

**Goals:**
- Show every field of an asset's `meta.json`, for any asset type, without a class per type.
- Row building is a pure function of the parsed Meta, testable without a UI.

**Non-Goals:**
- Editing, scene / entity properties, typed per-asset editors, multi-selection.

## Decisions

1. **Separate tool window, not a scene-tab side panel.** The selection lives in the Project view, which exists
   independently of any open scene tab. Alternative: split inside `SceneFileEditor`; rejected, it ties the panel to an open scene.
2. **Selection published by the Abyssus pane.** `AbyssusProjectViewPane` adds a `TreeSelectionListener` and publishes
   the selected user object on a project message-bus topic (`AbyssusSelectionListener`); the panel subscribes and keeps
   the last value, so it is correct if it opens after a selection. The panel acts only on a `DtoEntryNode` whose value
   is an `AssetInfo`; anything else is the empty state. Alternative: `ProjectView` selection API / `DataProvider`; rejected,
   it exposes platform nodes rather than our entries.
3. **Rows come from the raw `meta.json` `JsonNode`, not from `MetaBase`/`AssetInfo`.** `AssetInfo` drops fields and
   `MetaBase<T>` needs a class per type (only `SkyboxMeta` exists), so a typed route would hide unknown fields. A pure
   `metaRowsOf(JsonNode)` yields `PropertyRow(name, valueText, group)`: top-level scalars with group none, then `additional`'s
   fields with group `additional`. Scalars use `scalarOf`, null shows as `null`, lists as an item-count summary (bundle string),
   and a nested object, if any, as a property-count summary. Typed `MetaBase` subclasses can back editors in a later stage.
4. **Locating the file.** The asset folder is `<project>/assets/<AssetInfo.name>`; the panel reads `meta.json` there through
   `SceneJson.parseObject`, preferring unsaved editor text over disk (the same source order the scene tab uses), and reports
   parse errors in the panel instead of throwing. Disk reads stay off the EDT.
5. **Custom read-only layout in a scroll pane, not a `JBTable`.** The design stacks a header block, a Name / Value grid with a
   heading row and an image grid; one Swing panel (`JBScrollPane` over a vertical box) with a grid layout for the rows
   fits that, and `JBTable` would not host the image section. Rows are plain labels from `PropertyRow`, so nothing is editable.
   A tree-table is deferred to the editing stage. A `CardLayout` switches between the details and the empty state.
6. **Header and `lastModified`.** The header icon is chosen by the Meta `type` (skybox, model, terrain, else generic) from the
   plugin's existing icon set (`AssetIcons`). `lastModified` is formatted with the platform date-time formatter for display only.
7. **Face previews load off the EDT.** The six face files named in `additional` are resolved in the asset folder and decoded
   to scaled thumbnails on a background thread (cancellable when the selection changes); a missing or undecodable file
   yields a placeholder cell. Thumbnails are not cached across selections in this stage.
8. **Refresh** on VFS content change and document change of the selected `meta.json` (same listeners the scene tab uses),
   re-reading it and rebuilding rows and previews; if the asset folder disappears the panel shows the empty state.

## Risks / Trade-offs

- [Selection listener leaks or fires after pane disposal] -> register on the pane's disposable and the topic connection on the panel's.
- [Meta shapes differ per type and may grow] -> rows are generic, so new fields appear without code changes; per-type labels or ordering wait for a later stage.
- [Large face images are slow or heavy to decode] -> decode scaled, in the background, and drop results for a stale selection.
- [The board's colours do not match every IDE theme] -> use theme colours and keep the board's hierarchy, not its hexes.
- [Display-only values lose JSON number formatting] -> use the node's text for numbers, as the rest of the plugin does, so `60.0` stays `60.0`.
