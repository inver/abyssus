# Proposal

## Why

Terrain can only be made by noise generation today, so a user cannot shape a hill, flatten a runway or carve a valley
without leaving Abyssus. Sculpting by hand in the Scene view is the missing half of terrain authoring, and Paint Foliage
already proves the interaction model (toolbar mode, brush cursor, live update, one undoable stroke).

## What Changes

- New **Sculpt Terrain** mode in the Scene view toolbar with a brush strip (tool, radius, strength, tool options) and
  a cursor circle, available on a terrain entity whose terrain asset is readable.
- Eight tools: raise/lower (Shift inverts), smooth, flatten, set height, noise, slope limit, stamp from a heightmap
  image, and brush-scoped thermal erosion. Hydraulic erosion is not built.
- Sculpting is stored as an **additive delta layer** over the terrain's base heights, so regenerating from noise
  keeps the sculpting on top of the new noise, and Reset sculpting returns to the base.
- A terrain with no usable recipe gets a **stored base**: the first committed stroke snapshots the current heights.
- Regenerate gains a choice to keep (default) or discard sculpting; the terrain panel shows sculpt state and offers
  Reset sculpting.
- One stroke is one undoable operation; strokes preview live in every open Scene view and are written on release.
- **Format (deliberate, Abyssus-only, additive):** three new optional files beside `terrain.data`:
  `abyssus-terrain.sculpt.json` (manifest), `abyssus-terrain.sculpt` (delta) and, only for terrains without a usable
  recipe, `abyssus-terrain.base`. `terrain.data` stays the composed heights, so `meta.json` (`additional.terrainFile`,
  `size`, `uv`, splat fields) is unchanged, the game and Mundus-compatible loading are unchanged, and a terrain
  loads with none of the new files present. `meta.json` still requires `format: "abyssus"` and `formatVersion: 1`
  and is validated before any write; no `formatVersion` bump and no migration.
  The recipe file keeps its schema; its `heightsSha256` now fingerprints the base heights, not the composed ones.

**Out of scope:** hydraulic erosion; terrain edge/seam stitching between neighbouring terrains; splat/texture
painting; changing resolution or world size; heightmap export; pressure-sensitive input; a separate absolute "pinned"
layer (flatten and set height write into the additive delta, so they follow regenerated noise); sculpting from the
headless API.

## Capabilities

### New Capabilities
- `terrain-sculpting`: the Sculpt Terrain mode, its tools, the delta/base layer files, Reset sculpting, live
  preview and undo.

### Modified Capabilities
- `terrain-authoring`: Regenerate keeps or discards sculpting; recipe fingerprints cover the base; restore-safely and
  undo rules cover the new files.

## Impact

- `lib-core-editor`: new `terrain/sculpt` math (brush tools, delta/base composition, manifest codec, thermal erosion,
  heightmap stamping); `TerrainRecipe` fingerprint semantics; regeneration composes base + delta.
- `lib-core`: `TerrainData` / `TerrainMesh` need dirty-region height updates; ray-tracing terrain snapshot refresh.
- `plugin-abyssus`: `sceneview` toolbar mode, brush strip, cursor, input forwarding; `terrain` panel state and Reset
  sculpting; a stroke command with undo (modelled on `FoliageStrokeCommand`); bundle strings.
- Docs: `docs/ai/file-formats.md` (terrain section), `docs/ai/architecture.md`, `projects/lib-core-editor/README.md`,
  the sceneview README. Existing specs touched only through this change's delta.
