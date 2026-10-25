# Design

## Context

See proposal.md for scope and the spec for behavior. What exists today:

- A terrain's `meta.json` names six textures by `uuid`: `splatMap` (an RGBA image) and the layers `splatBase`, `splatR`,
  `splatG`, `splatB`, `splatA`. The shader draws the base, then mixes each present channel over it in turn
  (`mix(color, layer, weight)`, not normalized), reading the map at `localXZ / terrainSize` with linear filtering and
  clamped edges. Image row `r` is the first-uploaded row, so row `r` lies at `z = r / height * size`.
- `TerrainMesh` keeps only the asset folders of its splat textures and fetches the built `Texture` from the view's asset
  storage on every draw. Each Scene view has its own storage, so each has its own `Texture` of the splat map.
- The brush framework from `add-terrain-sculpting` and `unify-terrain-brush-modes` (both still open, applied before
  this change): `StampBrush<G>` / `BrushTool<G>` / `BrushTarget<G>` over a `BrushGrid`, `ByteGrid` (already
  multi-channel), `GridRect`, `BrushMode` / `BrushStroke` / `Gesture.Brushing`, `BrushStrip` and `BrushKinds` in the
  plugin, `TerrainStrokeCommand<S>`. Sculpting adds `SculptDrafts` + `SculptViewSync` (live preview) and commits through
  `AssetFileCommand` and an `AssetTransaction`.
- `AssetRefresh` already tracks per-asset revisions (meta hash, file length and mtime, and for a terrain the folders its
  splat uuids resolve to). A changed splat image makes the texture and every terrain using it reload in each view, and
  the ray terrain store is invalidated through the same path.
- The loader decodes images with libGDX `Pixmap` and halves any image over 2048 pixels (`Pixmaps.MAX_SIZE`).

## Goals / Non-Goals

**Goals:**
- A fourth brush that is only configuration of the existing engine, mode, strip, stroke shell and write path; nothing
  above `BrushStrip` and nothing in `GestureDriver` or `SceneViewState` changes.
- Weights exact in the file: what the brush stores is what the shader reads.
- Every weight algorithm pure, deterministic and headless-testable.

**Non-Goals:**
- More than five layers, a shader change, auto-texturing by slope or height, headless painting, a generic
  `BrushRequests` replacing `SculptRequests` (the new request service mirrors it; merging the two is a later cleanup).

## Decisions

**1. The splat map is a `ByteGrid` with four channels, rows as the image has them.** `ByteGrid(resolution = image
side, size = terrain size, channels = 4)` over the RGBA bytes, z-major, row `r` of the image = grid row `r`. Channel
`0..3` = R, G, B, A, slot `1..4`; slot `0` (Base) has no channel. `BrushPath`'s `cell = size / (resolution - 1)` places
grid point `i` at `i * cell`, while the shader samples texel `i` centred at `(i + 0.5) / resolution * size`: the brush
and the picture differ by under half a texel (10 cm for a 1024 map on a 200 m terrain). Accepted and pinned by a test
rather than adding a second coordinate convention to the engine; the brush circle is still drawn from `BrushPath`, so
circle and stamp agree.
*Alternative: shift the footprint by half a texel.* Rejected: a special case in the shared walker for an error below the
brush's own falloff.

**2. Weights are bytes, tools return float changes, the target quantizes.** As in unify decision 2: the target rounds
`abs(change)`, applies the sign, clamps to 0..255 and reports a change only when a byte differs. `SplatTool` is a sealed
`BrushTool<ByteGrid>` with `channels = 4`:

```
Paint(slot)    change[slot channel] = +strength * 64 * smoothFalloff   (reversed: -, that channel only)
Erase          change[all four]     = -strength * 64 * smoothFalloff
Smooth         change[c] = (average of the 3x3 neighbours of c, from a per-stamp snapshot - v) * weight
Base slot      Paint on slot 0 acts as Erase (spec: Base shows where every channel is zero)
```

The 64 is foliage's constant, so strength 1 reaches full weight in a few stamps; Fill is not a stamp (decision 4).
Smooth reads a snapshot of the rectangle plus a one-texel border (a scratch buffer reused from stamp to stamp, as the
sculpt tools do), so a stamp does not depend on iteration order.

**3. Exclusive is the target, not the tool.** `SplatTarget(draft, exclusive)` applies the four changes of one texel at
once (the engine's all-channels `apply`). Non-exclusive: add each, clamp. Exclusive: first add to the channels that
rise and clamp each; if the sum of the four now exceeds 255, take the excess from the channels that did not rise,
proportionally to their values, with largest-remainder integer rounding and ties broken by channel order so the result
is deterministic; the raised channel is cut back only when the others cannot give enough (then it is clamped to
`255 - others`, which is 0). The sum never exceeds 255. Lowering changes (Erase, Shift, Smooth) never trigger
redistribution, and Smooth keeps `sum <= 255` because averages of valid texels are valid.
The shader's sequential `mix` means two half-weights do not blend 50/50 (`R .5 G .5` shows `0.25 base, 0.25 R, 0.5 G`).
That is the existing look of every splat map in the game and is not changed (spec out of scope); Exclusive only keeps
the weights from over-driving, and the Properties note in the strip says so.

**4. Fill is a draft operation.** Fill writes the chosen layer over the whole grid in one pass on the press, marks the
whole grid dirty and commits on release like any stroke (the stroke shell's press hook). It needs no footprint and no
`StampBrush`.

**5. Draft, keyed by the splat texture's asset folder.** `SplatDraft(resolution, size, pixels: ByteArray)` implements
`BrushTarget` storage, with `revision` and the accumulated `changed: GridRect`. `SplatDrafts` (a project `@Service`,
25 lines, like `SculptDrafts`) maps the **splat texture folder name** to its draft, not the terrain's. Two terrains that
share a map therefore both follow one stroke, and a view needs no knowledge of which terrain is being painted. A press
opens the draft from the decoded file; the draft is dropped when the stroke ends.

**6. Live preview into each view's own `Texture`.** `SplatViewSync` (the counterpart of `SculptViewSync`, same `seen`
bookkeeping) runs on the GL thread each frame: for every drawn terrain whose splat folder has a draft with a new
revision, it copies the rows of `draft.changed` into a reused direct `ByteBuffer` and calls a small `lib-core` helper
`SplatTexture.updateRegion(texture, x, y, w, h, buffer)` that binds the texture and issues `glTexSubImage2D` with unpack
alignment 1 (rows are copied contiguous because GLES2 has no row-length unpack). A view that first sees a draft
mid-stroke copies the whole array once. The helper is the only new GL code. The texture's mipmaps go stale, which is
harmless: `TerrainMesh` switches the splat to linear, non-mipmapped filtering the first time it draws it. When the draft
is gone, the view invalidates the splat texture asset (`assets.invalidate`) and reloads it from the file, exactly as
sculpt reloads its terrain. `TerrainMesh` needs one accessor (`splatTexture()`), nothing else.
*Alternative: upload through `Pixmap` + `Texture.draw`.* Rejected: it re-uploads the whole texture each time.

**7. The image codec is `ImageIO`, strictly 8-bit RGBA.** `SplatImageCodec` in `lib-core-editor` decodes with
`ImageIO` into `TYPE_4BYTE_ABGR` and reads the raster (non-premultiplied, so the weights are exact), and encodes the
same way, with channel order converted. Anything else (RGB, grey, palette, 16-bit, interlaced into another type, not
a `.png` file, non-square, under 64 or over 2048 pixels) is `Unusable(reason)`. 2048 is the loader's `Pixmaps.MAX_SIZE`:
a larger map would be halved on upload, so the view's texture and the file would disagree. `ImageIO` is already used in
`lib-core-editor` (`TextureGather`); the module rule is no IntelliJ/Swing imports and no `NoPlatformClasspathTest`
violation. The runtime decodes with libGDX `Pixmap` (straight alpha), the same bytes.
*Alternative: libGDX `Pixmap` / `PixmapIO` for both.* Rejected: needs the gdx native library in pure tests of the codec,
and `PixmapIO` writes premultiplied-free PNG but with a less predictable colour type.

**8. State, shared detection and the unusable reasons are a pure reader.** `SplatState` in `lib-core-editor` reads the
terrain `meta.json` (native validation first), resolves `splatMap` through `AssetReferenceChoices`-style lookup, decodes
the image and returns `None` (no map to paint: offer Create), `Ready(folder, file, pixels, users)` or `Unusable(reason)`.
`users` lists the other terrain assets whose `splatMap` names the same uuid. The strip renders the reason and the share
warning; nothing here touches the UI.

**9. Create splat map and Make unique copy are one multi-file `AssetTransaction`.** The files: a new folder
`assets/<name>/` (`createdDirs`) holding `meta.json` (`format`, `formatVersion`, `version`, `lastModified`, fresh `uuid`,
`type: "TEXTURE"`, `additional.file: "splat.png"`, compact, built like `TerrainAssetWriter`) and `splat.png`, plus the
terrain's `meta.json` with `splatMap` set. The folder name is `texture_<terrain>_splat`, or `_2`, `_3` while taken; the
transaction's `Collision` check is the backstop. **Why not `editSceneJson`:** it makes one undoable command on one
document, and this step creates a folder and two files and changes a third, which must undo together. The terrain
`meta.json` text for the transaction comes from lib-core-editor's `DocumentTextEditor` (the same transform
`editSceneJson` uses), so formatting, key order and number text are kept; the transaction's `expectedFiles` and
`before` bytes (the editors' current text, as `readTerrainFolder` reads it) refuse the step if the file changed since.
Assigning or clearing one layer slot is a single-document edit and **does** use the existing meta editing path
(`editSceneJson` with `AssetMetaEditor`), the same as the Properties panel's six pickers.
*Alternative: create the texture asset first, then link it with `editSceneJson`.* Rejected: two undo steps, and an
Undo of the second leaves an orphan asset with the first still applied.

**10. Commit: encode off the AWT thread, apply on it.** On release, the stroke copies the draft pixels, encodes the PNG
on a pooled thread (a 1024 map is about 50 ms, a 2048 one a few hundred), then on the AWT thread plans a one-file
`AssetTransaction` (`splat.png` before/after bytes, `expectedFiles` = the splat texture's `meta.json` and the terrain's)
and runs it through `AssetFileCommand`. The draft stays registered until the write finishes, so no view reloads early,
and a new press is refused while a commit is in flight (a `generation` guard like `FoliageStrokeCommand`, so a stale
release or a disposed view drops the result). A stroke that changed no byte writes nothing.
Undo keeps whole before/after PNG bytes: `FileSnapshot.Patch` patches raw mask files and cannot patch a compressed PNG.
Typical maps compress well (a blank 1024 is a few KB, a heavily painted one a few hundred KB); the worst case
(a noisy 2048 map) is a few MB per side.

**11. Entry point and mode.** `PaintTextureRequests` (project service) and `PaintTextureRequestListener` mirror
`SculptRequests`; `EntityDetailsView` shows a second toggle (`paint-texture`) beside `sculpt-terrain`.
`PaintTextureControls : BrushStrip<SplatSession>` is registered in `BrushKinds` after sculpt; `BrushKinds.endOthers`
ends the other brushes and the strip's `onEnded` clears its request, as sculpt's does. `BrushMode.kind = SPLAT_BRUSH`
with `SplatBrushOptions(slot, mode, exclusive)`; `SplatSession` carries why painting is unavailable, the loaded
`SplatState`, the stroke and the texture choices. `modeFor` computes `reversed` as the Shift key (Paint only). The
strip's component names are `paint-texture-*` and its strings go in `AbyssusBundle.properties`.
`SplatStrokeCommand : TerrainStrokeCommand<Stroke>` supplies `begin` (state ready, draft opened), `press`, `drag`, and its
own `released` / `cancelled`.

**12. Slots and thumbnails.** The strip lists the five slots from the terrain `meta.json`; a slot's name and thumbnail
come from `AssetReferenceChoices.textures(assetsDir, current)` and a 32-pixel scaled copy of each texture's image,
decoded off the AWT thread and cached by file length and mtime. Choosing a slot only sets the brush's slot; "Assign..."
opens the chooser.

**13. Dependents follow the file.** After a commit the splat image's length and mtime change, so `AssetRefresh` reloads
the texture and every terrain using it in each view and invalidates the ray terrain store, which reads the image afresh.
No dedicated ray hook is added; a test pins that the revision tracker flags the terrain when only its splat image
changes. Play and the game read the PNG at load. Foliage, heights and physics do not read the splat map.

## Risks / Trade-offs

- [The brush is up to half a texel off the drawn map] → Documented in decision 1, pinned by a test, below the brush falloff.
- [Large radius on a 2048 map is thousands of texels per stamp, and Smooth snapshots them] → Footprint, change and snapshot
  buffers are reused and grow by doubling; spacing is `radius / 4` so a fine mouse step adds few stamps; the default
  resolution is 1024; measured by hand while dragging in `runIde` (task 8.1).
- [The view's texture shows the painted pixels until the reload after Esc finishes] → The reload is queued at once; same
  behaviour as sculpt's discard, and the stale frame is the stroke the user just cancelled.
- [Two reloads after a commit: the draft ending and `AssetRefresh`] → Both invalidate the same asset, which is idempotent
  and keeps the old texture until the new one is built.
- [ImageIO encode time on a large map] → Encoded on a background thread; the draft stays live until applied.
- [Undo stack memory for noisy maps] → Whole-file PNG bytes; a cache like `FoliageUndoCache` can be added without changing
  the transaction if it is ever a problem.
- [Exclusive does not give a true crossfade because of the shader's sequential `mix`] → Documented in the strip note and
  the spec's scope; changing the shader would alter every existing splat map.
- [This change depends on two open changes] → Applied after both are archived; the first tasks assert the engine types it
  uses exist, so a mismatch fails early.

## Migration Plan

None: no file, `formatVersion` or schema change; terrains and splat maps made outside Abyssus keep loading. Rollback is
reverting the commits; painted splat maps stay valid RGBA PNGs.

## Open Questions

- The default Smooth and Paint strength curve (the 64 constant) is tuned in `runIde`; no spec impact.
- Whether Fill asks for confirmation. The spec makes it one undoable click, so no.
