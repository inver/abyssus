# Proposal

## Why

The Abyssus Properties panel is still a Name / Value table with a plain header: no breadcrumb, no section frame, no marker for
game components, no way to tell a default from a written value, and no per-component switch. The design mockup
https://claude.ai/artifact/9mBTh6DatEKs5QqM4Lfkns (board "Abyssus tree and Properties · Control Line (interactive)", with the
"Icon set" board) now defines the whole panel, and the in-flight `redesign-abyssus-tree-view` change deliberately left the
panel re-layout out of scope. Matching the design completely also needs one thing the document format does not have: an
enabled flag on entities and components, which the mockup shows as a tree eye and as an `Enabled` check box.

## What Changes

- **Header.** A breadcrumb (project > scene > ecs ...), a 24 px colored icon, a 15 px title, a subtitle line (`Entity 3 · OBJECT`,
  `Game component · PlaneComponent on Racer`, `MODEL asset` ...) and badges: `read-only` and `no scene uses this`. The panel
  title bar carries Options and Hide. The empty state is the design's (icon, "Nothing is selected.", hint).
- **Section frame.** Every section has a collapse chevron, a colored kind icon, a title, a pink `game` badge for components
  registered by the game project, the component class name as a hint, a hairline, and an `Enabled` check box on togglable
  sections. Collapse state is kept per section while the selection stays on the same node.
- **Field rows.** A 150 px label column and 24 px field boxes; monospace values; unit suffixes (`kg`, `m`, `N`, `s`, `m²`, `°`);
  vectors as axis-labelled boxes (`x y z`, `x y z w`); combo, check box, color swatch, read-only mono, link ("Open in Screen
  Editor"), note, sub-heading, and a read-only table (foliage layers, cloud layers, UI screens). An asset reference is a field
  with a 26 px Browse button.
- **Dim defaults.** A value that is not written to the file (the default applies) is drawn dim; the footer says so. Editing a
  dim field writes it, as today; nothing is written for a default that is left alone.
- **Per-selection layouts.** Project, folder (Scenes, UI, Assets and type groups, read-only), scene and each scene setting,
  `ecs`, entity, one component, UI screen, and one layout per asset type (MODEL, TERRAIN, FOLIAGE, TEXTURE, SKYBOX_PROCEDURAL,
  CLOUDS, UI), each with the `meta.json` section and the `additional` section, and the footer notes.
- **Existing editors stay.** The mockup draws assets as read-only; this change restyles them and keeps every editor the specs
  already require (terrain, cube and procedural sky, foliage, terrain regeneration, water, spotlight, ray settings). Their rows
  take the new look; no capability is removed.
- **Enabled flag (format).** An optional boolean `enabled` on an entity entry and on a component entry, written only as
  `false` (missing means enabled, so existing files read as before). The exact key placement is decided in design D1; the
  document admission validates it, and an unknown shape stays untouched. No migration.
- **Enabled flag (runtime and editor).** A disabled component is not built by `EcsLoader`'s systems (no rendering, light,
  collider or rigid body), a disabled entity contributes nothing; the scene view does not draw or pick it. The tree gets the
  design's eye on entity and component rows and the Properties section gets the `Enabled` check box; both write through
  `editSceneJson` as one undoable edit.
- **Panel model.** `lib-core-editor`'s panel model gains the facts the view needs without Swing: per-field `isDefault`, the
  breadcrumb, the badges, the `game` flag and class hint, the unit of a field, the folder / `ecs` / UI-screen states and the
  read-only tables.
- Docs: `docs/ai/file-formats.md`, `docs/ai/glossary.md`, `docs/ai/testing.md`, `properties/README.md`, `projectView/README.md`,
  `lib-core-editor/README.md`.

## Native file fields

Read and written: the new optional `enabled` boolean on an entity entry and a component entry of `ecs` (placement per D1).
Read only, as today: every field the panel already shows. Written as today and unchanged by this change: component fields via
`ComponentCodec`, `<x>Enabled` scene toggles, `physicsEnabled`, asset `meta.json` fields. Validation: `AbyssusDocumentFormat`
admission as before, plus "`enabled`, when present, is a boolean"; anything else keeps the document unsupported for editing
and unchanged. `formatVersion` stays 1 because the key is optional and additive; files that never use it are byte-identical
after any edit, and unknown extension data, key order and number text are preserved.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `object-properties-panel`: header, breadcrumb and badges, section frame, field row kinds, dim defaults, tables, per-selection
  layouts, empty state, title-bar actions, the `Enabled` check box.
- `abyssus-project-view`: eye on entity and component rows (extends the tree change's eye requirements).
- `abyssus-document-format`: admission of the optional `enabled` flag.
- `scene-ecs-components`: loading and writing keep `enabled`; a disabled component is not instantiated.
- `scene-ecs-systems`: systems ignore disabled components and entities (render, light, camera, foliage).
- `physics-components`: a disabled rigid body or collider creates no body or shape.
- `scene-component-editing`: toggling a component or entity is one undoable edit that writes only `enabled`.
- `scene-picking`: a disabled entity is neither drawn nor picked.

## Impact

- Code: `projects/plugin-abyssus/.../properties/` (all views, `AssetRows`, `PanelState`), new shared field-row and section
  components under `plugin/ui/`, `projectView/` (`RowActions`, `AbyssusNodes`), icon holders and SVGs; `lib-core-editor`
  panel model, `ComponentCodec`, `EcsWriter`, edit API; `lib-core` (`EcsLoader`, components, systems); `lib-physics`
  (`PhysicsWorld` body creation); `AbyssusBundle.properties` and `AbyssusEditorBundle.properties`.
- Boundaries kept: `lib-core` / `lib-physics` read `enabled` without editor types; Jolt stays out of the IDE; the panel model
  stays free of Swing; all writes go through `editSceneJson`.
- Tests: panel-model tests in `lib-core-editor`, loader and system tests in `lib-core`, physics tests, plugin panel tests,
  Remote Robot UI tests with new baselines (canonical container).
- Overlaps: `redesign-abyssus-tree-view` (icons, row painting, Browse button in the scene section; this change builds on its
  icon holders and amends nothing it already spec'd except adding entity / component eyes), `redesign-game-ui-editor-layout`
  (UI screen link only), `add-scene-model-mesh-selection` and `add-terrain-sculpting` (picking, re-check at apply time).

## Out of scope

- The Foliage board ("Foliage in Abyssus Properties · three states"), Paint Foliage, Edit Path, the Screen Editor, the Picker
  dialog, the ECS board and the "How it works" board.
- Truncated lists ("N more" rows); drag and drop; making asset `meta.json` fields read-only; new asset types.
- Light theme pixel parity beyond contrast checks; the mockup is dark only.
- Any migration of existing scenes; the flag is additive.
