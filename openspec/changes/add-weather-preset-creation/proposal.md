# Proposal

## Why

`add-sky-clouds` introduces `WEATHER_PRESET` assets, but they can only be written by hand. A user who has tuned a
sky's clouds should be able to keep that weather as a reusable preset in one step, without copying JSON between
folders.

Depends on `add-sky-clouds` (the preset format) and on `add-asset-editing-and-terrain-generation` (its undoable asset
folder creation and folder-name validation).

## What Changes

- **New action.** A right-click action **New Weather Preset from Sky...** on a `SKYBOX_PROCEDURAL` asset row of the
  Abyssus tree, shown when that sky has a `clouds` object.
- **What the dialog asks.** A folder name for the preset. It suggests `weather_<sky>` and applies the same name rules
  as New terrain.
- **What creation does:**
  - It writes a new `assets/<name>/meta.json` with the native markers `"format": "abyssus"` and `"formatVersion": 1`, `"type": "WEATHER_PRESET"`, a fresh `uuid`, `version` 1, the
    creation time as `lastModified`, and the sky's **resolved** bands: its preset's bands with the sky's own bands
    applied, built-ins included.
  - It is one undoable command. The new preset is selected in the tree and the Properties panel.
- **What it leaves alone.** Creation doesn't change the sky, any scene or the `.abss`. The new preset shows as unused
  until a sky names it.

**Fields read/written:**
- **Read:** the sky's `additional.clouds` and the preset it names.
- **Written:** only the new preset folder's `meta.json`.

**File format:** no change beyond `add-sky-clouds`' `WEATHER_PRESET` format.

### Out of scope

- Editing a preset's bands in the Properties panel.
- Pointing the sky at the new preset automatically.
- Creating a preset from scratch without a sky.
- Deleting or renaming presets.
- Built-in presets as copyable rows in the tree (they are copied through a sky that names them).

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `weather-presets` (from `add-sky-clouds`): new requirement for creating a preset from a sky.

## Impact

- **Plugin:**
  - a new `NewWeatherPresetAction` in `projectView/` and its dialog;
  - a pure `WeatherPresetDraft` (core) that resolves a sky's bands into preset JSON;
  - reuse of `add-asset-editing-and-terrain-generation`'s `AssetFileCommand` and folder-name validation;
  - `plugin.xml` registration;
  - `AbyssusBundle` strings.
- **Docs:** the user section of `README.md`, the changelog, and the projectView README.
- **No new dependencies.**
