# Design

## Context

Terrain heights live in `terrain.data` (big-endian floats, square grid). `TerrainMesh` builds its GPU mesh once from
`PreparedTerrain`; `TerrainData` is the CPU copy used by picking, foliage and ray snapshots. Regeneration already
commits several files as one undoable `AssetTransaction` (`FileChange(path, before, after)` over `FileSnapshot` bytes or
patches, plus `expectedFiles`) run by `AssetTransactionEngine`, with conflict checks and rollback, and
Paint Foliage already has the stroke pattern: pure brush math in `lib-core-editor`, a draft held in the project-level
`FoliageDrafts` registry that every Scene view watches by `revision`, a stroke command in the plugin, commit on
release. Each Scene view loads its own `TerrainData` and `TerrainMesh` through its own asset storage, so nothing about
the heights is shared between views today. See proposal.md for scope and the file list.

## Goals / Non-Goals

**Goals:**
- Reuse the foliage stroke pattern and the existing multi-file undoable write path; no new write mechanism.
- Keep every height-changing algorithm pure, deterministic and headless-testable.
- Keep `terrain.data` the single source the runtime reads.

**Non-Goals:**
- Hydraulic erosion, seam stitching, splat painting, resolution change, headless/`HeadlessEditing` sculpting.
- Any change to `meta.json`, `formatVersion` or the recipe schema version.

## Decisions

**1. Files and fingerprints.** Beside `terrain.data`:

```
abyssus-terrain.sculpt.json  { schemaVersion:1, baseKind:"recipe"|"stored", resolution,
                               deltaSha256, composedSha256, baseSha256? }
abyssus-terrain.sculpt       resolution^2 big-endian floats, additive delta, world units
abyssus-terrain.base         same layout; only when baseKind = "stored"
```

`composedSha256` must equal the hash of `terrain.data`; `deltaSha256` the hash of the delta file. Any mismatch is
"unsafe state" (spec) and blocks sculpting. The recipe's `heightsSha256` now means the **base** (what the generator
produced); for terrains with no sculpt state that is `terrain.data`, so existing recipes stay valid unchanged.
Alternative considered: embed fingerprints in the binary files with a header. Rejected: the existing height format has
no header and a JSON manifest is inspectable and follows the recipe's style.

**2. Base resolution.** `recipe` base is recomputed by the deterministic generator and verified against the recipe's
`heightsSha256`; if that fails the terrain is in unsafe state (not silently re-based). `stored` base is read from the
base file and verified by `baseSha256`. A terrain with no/invalid recipe at first stroke becomes `stored`. Regenerate
on a `stored` terrain replaces the base with the recipe's and switches `baseKind` to `recipe`.

**3. Composition is `composed = base + delta`, floats added once per commit.** Deterministic summation order is
irrelevant (per-vertex add). Strokes edit an in-memory `SculptDraft` holding the composed heights and the delta (copies of
the arrays, sized at most 255² floats); the base is never edited. Tool math reads the draft's composed heights and
writes `delta += change`, `composed += change`, so composed always equals base + delta bit for bit as floats are
added the same way on regenerate (base' + delta). Regenerate recomputes `composed = base' + delta` with the same
single float add.

**4. Absolute tools write relative deltas.** Flatten and Set height compute `change = (target - composed) * weight`
per vertex, with `weight = strength * smoothstep falloff`. They end up as additive deltas, so after Regenerate the pad
follows the new noise (confirmed decision; documented in the panel text).

**5. Brush loop.** Generalise `FoliageBrush`'s stamp walker (stamp spacing `radius / 4`, terrain-local conversion
through the entity matrix, world-space distance through scale) into a shared `BrushPath` used by both foliage and
sculpting, rather than duplicating it. `BrushPath` owns only the walk (press stamp, spacing carried across drag calls,
terrain-local centre and per-axis scale) and calls a stamp callback with the centre; `FoliageBrush` keeps its mask
writes, byte clamping and falloff in that callback, so its behaviour is unchanged. `SculptTool` is a sealed interface
(Strategy) with one implementation per tool and typed options (Stamp holds its image grid, Flatten the start height,
Noise the stroke seed), each computing `(vertex context, weight) -> change` over a dirty rectangle. Smooth, Slope limit and Thermal erosion need neighbours, so they read from a per-stamp snapshot of
the rectangle plus a one-vertex border, making a stamp independent of iteration order.

**6. Determinism.** Noise uses the existing `NoiseSampler` seeded by a stroke seed stored with the tool options and
sampled at world-local coordinates (`x / (resolution - 1) * size`), so identical strokes yield identical heights at any
resolution. Thermal erosion uses a fixed iteration count, fixed neighbour order and no randomness. Stamp samples the image
bilinearly.

**7. Stamp heightmap source.** Project image assets (`TEXTURE` / `PIXMAP_TEXTURE`) chosen in the strip using the same
chooser pattern as the skybox chooser; luminance maps to 0..1 and then to `heightScale`. Decoding happens off the AWT
thread without GL (pixmap only); the result is a float grid the stamp applies on the AWT thread. Unreadable image: tool
disabled (spec).

**8. Live preview.** A project-level `SculptDrafts` registry in the plugin (the counterpart of `FoliageDrafts`, keyed by
terrain folder) holds the stroke's `SculptDraft` with a `revision` and the dirty rectangle since each view last looked.
Every Scene view showing the terrain compares its seen revision on each frame and, on the AWT thread inside
`GdxRuntime.withContext`, copies the dirty rectangle of the draft's composed heights into **its own**
`TerrainData.heights` in place (`heights` is a `val` array, so the array is mutated, never swapped) and calls
`TerrainMesh.updateRegion(rect)`. That recomputes positions and normals for the rect plus a one-vertex border and
re-uploads that vertex range, falling back to a full upload if the rect exceeds a fraction of the grid. Whether `IntMesh`
(`lib-gdx`) supports a partial vertex upload is unverified: task 4.2 checks it first, and if it does not, the full path
(rebuild or re-set all vertices; 255² is only 65k vertices) is the implementation, with partial upload left as an
optimisation. On commit, or when the stroke is discarded, the registry entry is dropped and views reload from the
written files. Picking uses each view's CPU `TerrainData`, no GL.

**9. Commit.** On release, the plugin builds an `AssetTransaction` of `FileChange`s for `terrain.data`, the delta,
manifest and (first stroke only) base, off the AWT thread, and applies it through the same `AssetTransactionEngine`
path regeneration uses, as one undoable step with conflict detection on undo/redo. This is the only write path used (`terrain.data` is not a scene file, so
`editSceneJson` does not apply, as for regeneration). A cache like `FoliageUndoCache` is not needed: before/after file
snapshots are at most ~260 KB per file. `FileSnapshot.Patch` is available if a delta or `terrain.data` rewrite proves
too heavy per stroke, but whole-file bytes are the starting point.

**10. Panel, regenerate, reset, forget.** The terrain panel reads sculpt state (`None / Sculpted(kind) / Unsafe(reason)`)
from a pure `SculptState` reader in `lib-core-editor` next to `RecipeStatus`. Regenerate Apply composes `base' + delta`
unless Discard is set. Reset and Forget are `FileChange` sets over the same transaction.

**11. Dependents.** Foliage derives heights at draw time, so it follows after the view's CPU `TerrainData` update (verified by
a test using the Untitled fixture and a stale bake). Ray tracing: the terrain snapshot is refreshed after commit (not per
stamp). Play and physics read `terrain.data` at load and need no change. Shadows use the mesh and need no extra work.

**12. Layering.** All tool math, `BrushPath`, `SculptDraft`, `SculptManifest` codec, `SculptState`, thermal erosion and stamp
sampling live in `lib-core-editor` (no Swing/AWT/IntelliJ; no `object` with behavior). The plugin owns the toolbar,
brush strip, cursor, input forwarding, panel UI, the stroke command and the `SculptDrafts` registry (wired by
constructor and passed to each view's renderer like `FoliageDrafts`). `lib-core` only gains `TerrainMesh.updateRegion`
and `TerrainData` dirty-region normal recompute and in-place height copy.

## Risks / Trade-offs

- Float add order drift between "composed" and "base + delta" → composed is always produced by the one `base + delta` add
  on commit, and the manifest fingerprints it.
- Large strokes allocate a rectangle snapshot per stamp → bound by radius and the 255² grid; reuse a scratch buffer.
- Flatten pads are not flat after regenerate → documented; a pinned layer can be added later without breaking these files.
- Regenerate-keep may produce spikes if the new range differs wildly → the preview shows composed heights, and Discard
  is one click.
- Stored base duplicates the terrain size on disk (~260 KB) → acceptable and enables Reset.
- Partial GPU update bugs at rect borders → one-vertex border recompute and a fallback to full upload; covered by a GL test.
- Views drifting from the draft (a view opened or reloaded mid-stroke) → a view with no seen revision copies the whole
  composed array once, then only dirty rectangles; the registry entry is the single source while a stroke is open.

## Open Questions

- Default values for slope-limit angle, talus angle and erosion iterations (tuned in runIde; no spec impact).
- Whether Set height should read the terrain's height under the cursor as an eyedropper (UI nicety).
