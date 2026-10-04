# Design

## Context

See `proposal.md` and the delta spec.
- **From `add-sky-clouds`:** `CloudSettingsReader`, `WeatherPresetReader`, `BuiltinPresets`, band-by-band merging and
  `MetaType.WEATHER_PRESET`, all in `core`.
- **From `add-asset-editing-and-terrain-generation`:**
  - `AssetFileCommand`, which creates asset folders with staged bytes, one named undoable command, expected-state
    checks, rollback, and Undo that removes the folder only when it is unchanged and unreferenced;
  - pure folder-name validation;
  - the New terrain action pattern on the Assets node.

This change composes them. It adds no new write mechanism.

## Goals / Non-Goals

**Goals:** create a preset from a sky's resolved clouds in one undoable step; reuse the existing creation command and
validation.

**Non-Goals:** editing bands in the UI, or rewriting the sky to use the preset.

## Decisions

### 1. `WeatherPresetDraft` resolves and serializes, in `core`, pure
`WeatherPresetDraft.from(skyAdditional, presets, builtins)` returns the resolved bands, using the exact merge
`add-sky-clouds` uses for drawing. It also reports when a named preset couldn't be read (the dialog notice).

`toMetaJson(uuid, lastModified)` writes, in this key order:
`format: "abyssus"`, `formatVersion: 1`, `version: 1`, `lastModified`, `type: "WEATHER_PRESET"`, `uuid`, `additional: { low, mid, high }`. The source sky's metadata must itself be a supported native document, or creation is refused.
- Only present bands are written.
- Each band is written with every field resolved (type defaults filled in), so a preset is self-describing and
  doesn't depend on future default changes.
- Numbers are written with the plugin's float formatting through `SceneJson`.

`WeatherPresetDraftTest` covers it without IntelliJ.

*Alternative:* copy the sky's raw `clouds` JSON. Rejected: built-ins and folder presets wouldn't be resolved, and the
copy would depend on the original preset staying around.

### 2. The action and dialog reuse asset creation
`NewWeatherPresetAction` is registered in the Abyssus tree popup and enabled for a `SKYBOX_PROCEDURAL` asset node
whose `meta.json` has `clouds`. It runs on the EDT:
1. The dialog validates the name with the shared folder-name validator, plus a rule rejecting `:`.
2. The action stages the bytes from `WeatherPresetDraft` with a fresh `UUID`.
3. It calls `AssetFileCommand.create` with the command name "New Weather Preset".
4. On success it selects the new asset through `AbyssusSelection`.

Reading the sky and preset happens in a read action with unsaved document text (`textOf`). Undo and Redo behavior is
`AssetFileCommand`'s, including Redo reusing the same UUID.

### 3. Threading
- **EDT:** dialog, validation, command.
- **Read action:** reading `meta.json` texts.
- No pool or GL work: drafts are tiny.

## Risks / Trade-offs

- `add-asset-editing-and-terrain-generation` changes its creation API before landing → this change is applied after
  it, and task 1.1 re-reads its current API.
- Default values written at creation drift from later type defaults → intended; presets are snapshots.
- Unsaved edits in the sky's `meta.json` editor → the draft reads the document text, so what the user sees is what
  gets copied.

## Migration Plan

No migration. Rollback removes the action; presets created by it stay as ordinary `WEATHER_PRESET` assets.
