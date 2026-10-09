# Proposal

## Why

Procedural skies already draw reusable `CLOUDS` assets, but the IDE has no creation action for keeping a tuned
weather setup as a separate asset. A user should be able to snapshot the clouds a sky references without copying
JSON between folders.

This change builds on the current `cloud-assets`, `scene-sky-clouds` and `terrain-authoring` capabilities and the
existing undoable asset-file transaction infrastructure; it does not wait for the historic changes that introduced
them.

## What Changes

- Offer **New Weather Preset from Sky...** on a `SKYBOX_PROCEDURAL` asset row with a nonblank textual
  `additional.clouds` UUID reference. Missing or malformed references do not become inline cloud descriptions.
- Ask for a folder name, suggest `weather_<sky>`, and use the same folder-name checks as New Terrain, including
  the existing rejection of `:` and case-insensitive collisions.
- Resolve that UUID within the owning project and read the referenced native `CLOUDS` metadata, using unsaved
  editor text when present. Refuse creation with a reason if the source is missing, unreadable, unsupported,
  of another type, or has no valid band.
- Write only `assets/<name>/meta.json`: native markers, `version` 1, creation time `lastModified`, a fresh UUID,
  `type: "CLOUDS"`, and the source's stored technique and valid bands with all known band defaults resolved.
  This is a standalone snapshot; the scene view's technique override is not copied.
- Create the folder in one undoable command, select it in the Abyssus tree and Properties panel, and list it as an
  unused cloud asset until a sky references it. Leave the source cloud asset, sky, scenes and project unchanged.
- Permit explicit IDE creation in `cloud-assets` while retaining read-only loading, listing and rendering.
- Require the generated canonical cloud metadata to round-trip through the runtime binder. The current typed
  binding tests use a different band representation from the specs and templates; correct the reader as needed
  for the existing canonical contract before shipping the action.

**Fields read/written:**
- **Read:** native markers and `type` of source documents; the sky's `additional.clouds`; project asset UUIDs;
  the referenced cloud asset's `additional.technique`, `low`, `mid`, `high`, and their band fields.
- **Written:** only a newly created cloud asset's `meta.json`, with `format`, `formatVersion`, `version`,
  `lastModified`, `uuid`, `type`, and `additional`.

**File format:** use native version 1 and the existing `CLOUDS` format: lowercase technique/type keys and
`wind: [x, z]`. No `WEATHER_PRESET` type, built-in reference scheme, importer or format migration is introduced.
Validate the owning `.abss`, sky and source cloud metadata before using them. Keep unknown native extension data
when making the snapshot; unrelated source key order, number text and omitted defaults remain untouched.

### Out of scope

- Editing cloud bands in Properties, automatically assigning the new asset to a sky, or creating from scratch.
- Deleting or renaming cloud assets, a built-in preset catalogue, or merging inline sky-band overrides.
- Capturing toolbar technique overrides, animation time, GL resources, or scene lighting.

## Capabilities

### New Capabilities

- `weather-presets`: the user workflow for snapshotting a sky's referenced clouds into a new reusable cloud asset.

### Modified Capabilities

- `cloud-assets`: allow explicit creation from a sky while loading, listing and drawing remain read-only.

## Impact

- **lib-core-editor:** a pure `WeatherPresetDraft` and metadata writer, native document admission and resolved
  cloud settings; reuse the folder-name checks.
- **lib-core:** targeted canonical cloud binding corrections if needed, covered by runtime round-trip tests.
- **Plugin:** `NewWeatherPresetAction` in `projectView/`, its dialog, `AssetTransaction` staging and
  `AssetFileCommand.execute`, reference guards, selection, `plugin.xml` registration and `AbyssusBundle` strings.
- **Docs:** user README, changelog, projectView README, file-format notes and relevant module READMEs.
- **No new dependencies.**
