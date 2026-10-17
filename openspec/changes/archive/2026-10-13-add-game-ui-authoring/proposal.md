# Proposal

## Why

Game menus and HUDs currently require handwritten layouts, as Control Line's Scene2D `GameUi` demonstrates. Creators need to author reusable screens and overlays in Abyssus, exercise them interactively, and use the same layouts with live game data at runtime.

## What Changes

- Add native `UI` assets containing named standalone screens and overlays, a widget hierarchy, anchored positioning, rows/columns, styling, bindings and actions.
- Add a visual editor with canvas, widget tree and properties, including undoable creation, duplication, reparenting, ordering, deletion and property edits.
- Support labels, images, buttons, panels, rows, columns, progress bars, sliders, checkboxes and text fields. Ship a default theme with image/font/color and interaction-state overrides.
- Share rendering and layout between the editor preview and standalone games. Add typed live bindings, validated write-back, screen navigation and named game actions.
- Add interactive preview with selectable resolution, scene backdrop for overlays, editable sample values and an action log. Preview never invokes game code or writes real settings.
- Store `type: "UI"` and `additional.ui` in asset `meta.json`; UI fields include `schemaVersion`, `entryScreen`, `screens`, `bindings`, `resources` and `styles`. Add optional project `.abss` `uiAssetNames` as the explicit UI asset registry and usage roots. No scene fields change.
- Validate native markers through `AbyssusDocumentFormat` before binding/editing/previewing; validate UI schema version 1 separately. Preserve unknown fields, unrelated number text, key order and omitted defaults. Native `formatVersion` stays 1; no migration is introduced.
- Out of scope: arbitrary scripts or expressions, animation timelines, full theme editor, gamepad navigation, custom widget plugins, automatic conversion of Kotlin menus, full game execution in preview, and replacing Control Line's existing menus in this change. The standalone demo uses its own fixture project, not Control Line's committed `project/ControlLine`.

## Capabilities

### New Capabilities

- `game-ui-authoring`: native UI assets, visual editing, responsive layout and styling.
- `game-ui-runtime`: shared screen/overlay presentation, typed bindings, write-back and actions.
- `game-ui-preview`: isolated interactive previews, sample data, scene backdrop and diagnostics.

### Modified Capabilities

- `abyssus-project-assets`: registered UI assets are additional usage roots, including UI navigation dependencies.

## Impact

`lib-core` gains the native UI model, admission, asset metadata support, renderer and runtime contracts. `lib-core-editor` gains pure document editing, selection, layout manipulation and preview models. `plugin-abyssus` adds asset creation/opening, visual editor, properties and guarded GL integration. Existing libGDX Scene2D is reused; no new UI framework or IDE-loaded game code is required. A small standalone test/demo host proves runtime parity without changing Control Line game flow. Existing scene Play and scene editing contracts stay intact. Update format, architecture, module and user documentation during implementation.
