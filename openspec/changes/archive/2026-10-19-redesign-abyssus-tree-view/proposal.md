# Proposal

## Why

The Abyssus tree is the editor's main navigation, but it still looks and is organized like a debug view of the files: assets
come before scenes, assets are one flat list, the project's `name:` appears as a property row, game components are not marked,
and the `unused` badge, eye and Choose button are painted at sizes and colors that differ from the design. The design mockup
https://claude.ai/artifact/9mBTh6DatEKs5QqM4Lfkns (boards "Abyssus tree and Properties", "Icon set") now defines the whole
tree. The existing UI test harness (`add-plugin-ui-tests`) can check layout and icons against a real IDE, so the redesign
can be verified instead of eyeballed.

## What Changes

- **Structure.** A project's children become, in order, **Scenes**, **UI** and **Assets**, each a folder row with its count.
  Assets are grouped by type under the Assets folder (Models, Terrains, Foliage, Textures, Skyboxes, Clouds, and any other
  type present), each group with its count. The project's `name:` row is removed (the name is in Properties and in the
  `.abss` row). Every entity, scene and asset is listed; the mockup's "N more" rows are **not** built.
- **UI folder.** UI assets (`type: UI`) are listed under **UI**, with their screens as children, instead of under Assets.
- **Row painting.** Row height 24, 16-px indent, 16-px icons from the Icon set, 14-px eye (blue `#548af7` on, gray `#6f737a`
  off), `unused` as amber text with the row dimmed, a `game` badge on components registered by the game project, the
  design's selected-row color, and the 28-px counts footer.
- **Icons.** New or revised SVGs for the Scenes / UI / Assets folders, type-group folders, UI screen, texture, procedural sky,
  clouds, UI assets, and the component kinds the Icon set lists (name, position, render, parent, rigid body, collider,
  constraint, light, foliage, path arrangement, plane, pilot), each with a dark variant where the glyph is not theme-neutral.
- **BREAKING (UI)**: the skybox **Choose** button leaves the tree row. The chooser opens from a new **Browse** button on the
  skybox field in Abyssus Properties (the same `SkyboxChooserDialog`, same write). The eye on the skybox row stays.
- **Header.** The platform's header buttons (Collapse All, Options, Hide) stay as the platform draws them; the unused filter is
  the first item of the Options menu, with the Icon set glyph and the amber selected state while on.
- **UI tests** on the existing Remote Robot harness: structure and order, per-row icon, painted geometry, row states
  (selected, disabled, unused, game), header and footer, the Browse flow, an icon inventory test, and regenerated tree
  baselines.
- Documentation: `projectView/README.md`, `docs/ai/testing.md`, `docs/ai/glossary.md` where it names tree nodes.

No document format changes. The folders are view-only rows: nothing new is read from or written to `.abss`, `.scene` or
`meta.json`, and every existing write (eye toggle, Rename Scene, skybox assignment, component edits) keeps going through
`editSceneJson`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `abyssus-project-view`: project expansion (Scenes / UI / Assets order, type groups, no `name` row), asset node structure,
  presentation (icons per node kind, `game` badge, `unused` painting, geometry), unused filter (applies inside type groups),
  counts footer styling, header toolbar.
- `abyssus-scene-skybox`: the Choose button requirement moves from the tree row to Properties; the dialog and assignment
  requirements are unchanged.
- `object-properties-panel`: the skybox of a selected scene gets a Browse button that opens the chooser.

## Impact

- Code: `projects/plugin-abyssus/.../plugin/projectView/` (`AbyssusNodes`, `DtoTree`, `RowActions`, `AbyssusProjectViewPane`,
  `AbyssusFooter`, `UnusedFilter`, `DesignColors`, `EntitySelection`, `TreeReselect`, `ProjectAssetListing`), `filetype/`
  icon holders, `properties/` scene section, `AbyssusBundle.properties`, SVGs in `src/main/resources/icons/`.
- Row identity: folder rows need stable entry paths so `reselect`, the unused filter and `selectAssetInAbyssusView` still find
  their rows.
- Tests: tree and icon unit tests, `projectView` tests that assume the old order, `SkyboxChooserUiTest` (opens from Properties),
  `AbyssusTreeUiTest`, new tree layout and icon UI tests, baselines under `projects/plugin-abyssus/src/uiTest/resources/screenshots/linux/`.
- Depends on: `add-plugin-ui-tests` (harness; its CI task 4.1 is still open). Overlaps `redesign-scene-view-toolbar` in icons
  and baselines (see design D9) and `add-remote-asset-library` if it adds tree rows. No new dependencies.

## Out of scope

- The Properties panel re-layout beyond the Browse button, the Screen Editor, the Picker, the ECS and Logic boards.
- Truncating long sibling lists ("N more"); drag and drop; new tree actions.
- Persisting folder expansion beyond what the platform tree already does.
- Pixel comparison of the GL viewport.
