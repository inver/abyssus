# Proposal

## Why

A terrain's surface textures can be assigned in the Properties panel, but the splat map that says where each texture
shows is never editable: it is a PNG somebody generated outside Abyssus (Control Line's comes from a tool script), and
a new terrain starts with no splat map at all. Painting grass, dirt or rock onto the terrain by hand, with textures
chosen from the project's assets, is the missing half of terrain authoring after sculpting. The brush framework is
now in place (`add-terrain-sculpting`, `unify-terrain-brush-modes`), so this is a third brush, not a third copy.

## What Changes

- New **Paint Texture** brush mode, a third terrain brush beside Paint Foliage and Sculpt Terrain, available on a
  terrain entity whose terrain asset is readable. It follows the same interaction as the others: toggle, brush strip
  (layer, radius, strength, options), a cursor circle on the terrain, drag to paint, one undoable step per stroke,
  Esc cancels, live preview in every open Scene view.
- **Five layer slots, as the terrain already has them:** the base layer (always visible) and the splat map's R, G, B
  and A channels. The strip shows the five slots with their texture's name and thumbnail; a slot's texture is assigned
  from the project's readable texture assets. Painting a channel raises that channel's weight under the brush; the
  base layer is the fill that shows where every channel is zero.
- Modes: Paint (Shift erases), Erase (lowers every channel, revealing the base), Smooth (blurs the weights) and Fill
  (sets the whole map to the chosen layer). **Exclusive** painting is on by default: raising one channel lowers the
  others in proportion so the four weights never sum above 1; turning it off paints one channel alone.
- **Create splat map:** a terrain with `splatMap: null` is offered a "Create splat map" step (resolution 1024 by
  default, independent of the height grid) that makes a new TEXTURE asset folder with a blank `splat.png` and links
  it in the terrain's `meta.json`, as one undoable step.
- **Shared splat maps are not silently edited:** when a splat map is referenced by another terrain, the strip warns
  and offers a unique copy for this terrain before the first stroke.
- Strokes write only the splat PNG (plus the bootstrap files the first time). The ray tracing terrain snapshot is
  refreshed after a commit, as for sculpting.

**Format (additive, no version change):** reads and writes `additional.splatMap`, `splatBase`, `splatR`, `splatG`,
`splatB` and `splatA` of a terrain `meta.json` (all exist today and keep their meaning: a texture asset `uuid` or
`null`), and creates a TEXTURE asset (`meta.json` with `type: "TEXTURE"` and `additional.file`, and `splat.png` as
RGBA8, never premultiplied). No new native field, no `formatVersion` bump, no migration. Every `meta.json` it touches
still requires `format: "abyssus"` and integral `formatVersion: 1` and is validated before any write. The shader, the
game's terrain loading and the ray snapshot's reading of splat images do not change; a terrain painted here renders
the same in the game as in the editor.

**Out of scope:** more than five layers (a second splat map or an indexed map would be its own change, with a format
and shader change); changing the shader's sequential `mix` blending; painting by slope or height ("auto-texturing");
foliage reacting to the painted textures (a bake still decides); splat painting from the headless API; per-layer tiling
or tint; importing or exporting splat maps; non-RGBA splat images (they are reported, not converted); pressure-sensitive
input.

## Capabilities

### New Capabilities
- `terrain-texture-painting`: the Paint Texture brush, its layer strip and modes, Create splat map, the shared
  splat map rule, live preview, undo, and the files a stroke writes.

### Modified Capabilities

None. `scene-terrain-rendering` and `object-properties-panel` (the existing splat reference editors) keep their
requirements; the new capability only adds a way to edit the splat map.

## Impact

- `lib-core-editor`: a splat brush on the existing stamp engine (`StampBrush<ByteGrid>` with four channels, a splat
  `BrushTool` and a renormalizing `BrushTarget`), PNG encode/decode that keeps RGBA unpremultiplied, the Create splat
  map and unique-copy file sets, and a pure splat state reader (ready / unusable / shared); `BrushMode` gains the
  splat kind and its options.
- `lib-core`: an in-place region update of a terrain's splat `Texture`; the ray terrain snapshot refresh after commit.
  `TerrainMesh` keeps reading its splat textures from the asset storage on every draw, so it needs no change.
- `plugin-abyssus`: a Paint Texture `BrushStrip`, a splat stroke command on `TerrainStrokeCommand`, a draft registry
  and per-view sync (texture region upload; the splat texture asset is invalidated when the stroke ends), the
  Properties-panel entry that sits beside the Sculpt Terrain toggle, a texture chooser, the splat file transaction,
  bundle strings.
- Write path: the PNG and the new asset folder are written as one `AssetTransaction` through `AssetFileCommand`, like
  sculpt strokes. Linking the splat map in the terrain's `meta.json` must keep the file's formatting and number text and
  stay in the same undo step; how that meets `editSceneJson` is a design decision.
- Docs: `docs/ai/file-formats.md` (terrain splat note), `docs/ai/architecture.md`, `projects/lib-core-editor/README.md`,
  the sceneview README.
- Sequencing: applies after `unify-terrain-brush-modes` (and so after `add-terrain-sculpting`) is archived. It needs the
  generic stamp engine, `BrushMode`/`BrushStroke`, `BrushStrip` and `TerrainStrokeCommand` from that change.
