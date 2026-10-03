# Proposal

## Why

Asset metadata can currently only be inspected, and terrain heights must be authored elsewhere. Editing supported asset properties and generating terrain in Abyssus will let users tune a scene's assets without switching tools, while preserving Mundus-compatible assets.

## What Changes

- Add validated, typed editors for terrain size, UV repetition and texture references; cube skybox face files; and the existing procedural sky atmosphere parameters. Preserve inspection of every other field and asset type.
- Replace the asset panel's read-only header and read-only-first-stage requirement for supported properties. Identity and bookkeeping fields remain read-only; merely selecting an asset never writes files.
- Add a terrain generation section with seeded noise, heightmap preview, Regenerate preview, Randomize seed, Apply and Cancel. Regeneration replaces an existing terrain's heights while preserving its resolution, identity, size, textures and scene references.
- Add New terrain from the project's Assets node, with a folder name, world size, resolution and generation settings. Create a new asset and select it; creation does not place an entity in a scene.
- Store reproducible generation settings in an Abyssus-only recipe beside the height data. Keep existing Mundus JSON and binary formats unchanged.
- Make metadata edits, regeneration and creation undoable, and refresh the properties panel, project tree and affected scene assets after edits, Undo, Redo and external asset changes.
- Explain that editing a shared asset affects all instances. No automatic movement of objects after a terrain change.

## Capabilities

### New Capabilities

- `terrain-authoring`: Seeded terrain previews, regeneration, new asset creation, recipe persistence and undo.

### Modified Capabilities

- `object-properties-panel`: Typed editors for supported asset properties, validation and shared-asset scope, while retaining read-only inspection for other fields.
- `asset-loading`: Reload changed assets and dependent assets without reopening scene views; discard stale in-flight loads.

## Impact

- Plugin properties UI, project-view actions and selection, file commands/Undo, VFS and document listeners, scene asset refresh, and localized messages.
- Plain JVM `core`: metadata edit descriptions and validation, deterministic noise generation, recipe encoding, binary height encoding and cache invalidation. No IntelliJ dependencies or singleton additions; no changes required to `gdx-model`.
- Use a pinned MIT-licensed FastNoiseLite Java source behind an injected noise interface, retaining its license and recording the generator revision in recipes.
- Existing asset edits write only the selected `additional` fields: terrain `size`, `uv`, `splatMap`, `splatBase`, `splatR`, `splatG`, `splatB`, `splatA`; cube sky `top`, `bottom`, `left`, `right`, `front`, `back`; procedural sky `planetRadius`, `atmosphereRadius`, `betaRayleigh`, `betaMie`, `heightRayleigh`, `heightMie`, `mieG`, `sunIntensity`. Existing `version`, `uuid`, `type`, `lastModified` and file keys remain unchanged.
- New terrain metadata uses the established `version`, `lastModified`, `uuid`, `type: TERRAIN` and `additional` fields (`terrainFile`, `size`, `uv`, nullable splat fields), with a fresh UUID and ordinary big-endian square height data. No `.abss` or `.scene` writes; no generation parameters in Mundus metadata.
- Update README user instructions, docs/ai architecture and file formats, core README and sceneview package notes.
- Coordinate with open `show-project-assets`, `add-scene-object-drop` and `add-scene-shadows`: listing stays read-only, new unplaced assets are unused, and refreshed terrain geometry reaches picking, Drop and shadow invalidation.
- Out of scope: scene placement actions, heightmap import, sculpting, erosion, additive noise, splat painting/generation, asset duplication/renaming, model material editing, generic JSON editing, HDR/texture file editors and changing existing terrain resolution. Procedural skies retain their existing plugin-only compatibility status.
