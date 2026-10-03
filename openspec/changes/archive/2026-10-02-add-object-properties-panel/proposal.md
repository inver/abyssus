# Proposal

## Why

Each asset in a project is described by its `meta.json` (`version`, `lastModified`, `uuid`, `type` and a
type-specific `additional` object), but the Abyssus view shows only the asset's folder and type; to read the rest
the user has to open the file. A properties panel that shows the selected asset's Meta in one place gives that
readout, and gives the later stages (editing, type-specific editors) a panel and selection plumbing to build on.

## What Changes

- Add an `Abyssus Properties` tool window (right side) with a read-only Name / Value table.
- It shows the Meta of the asset selected in the Abyssus view: `version`, `lastModified`, `uuid`, `type`, then the
  fields of `additional` (a skybox's six face files, a model's `file`/`format`/`binary`/`materials`, a terrain's
  `terrainFile`/`size`/`uv`/`splat*`). Rows come from the `meta.json` content, so any asset type and any extra
  field is listed.
- A header block (type icon, asset name, `<type> asset · read-only`) above the table; `lastModified` is shown as a readable date-time.
- For a skybox, a "Face previews" section with the six face images, each labelled with its face and file name.
- Empty state (icon, message naming what is selected, hint to select a skybox, model or terrain) when the selection is not an asset.
- The look follows the UI design canvas https://claude.ai/artifact/FCGmbq4BAJW9PK6uvBDfke (board "Panels"); its ECS card editor, asset picker, eye toggles and unused filter are separate work.

## Capabilities

### New Capabilities

- `object-properties-panel`: a tool window that lists the Meta properties of the asset selected in the Abyssus view.

### Modified Capabilities

None. `abyssus-project-view` and `abyssus-project-assets` keep their requirements; the view only publishes its selection.

## Impact

- Code: new `properties/` package (tool window factory, panel, table model, row builder); `projectView/AbyssusProjectViewPane.kt`
  publishes selection changes; `plugin.xml` registers the tool window; `AbyssusBundle.properties` gets the strings.
  Skybox face images are read from the asset's folder.
  The asset's `meta.json` is located through `AssetInfo` / `ProjectLayout.META_FILE` and read with `SceneJson`.
- Tests: unit tests for row building against the `skybox_default`, `model_*` and `terrain_*` samples in `src/test/testData`;
  a platform test for selection to panel.
- Dependencies: none.
- Out of scope (later stages): editing values, scene / entity / component properties, type-specific editors,
  multi-selection, undo.

## Assumptions

- "Chosen object" means an asset row (an `AssetInfo` entry under a project's `assets`) selected in the Abyssus view.
- `lastModified` (epoch milliseconds in the file) is displayed as a date-time; the file is never changed.
- The panel is a separate tool window rather than a section of the scene tab.
