# Proposal

## Why

Paint Foliage and Sculpt Terrain are two brushes that do the same job on different data: press, drag along the
terrain, stamp a footprint of grid cells, report a dirty rectangle, commit one undoable step. Only the stamp walk
(`BrushPath`) and `smoothFalloff` are shared. Everything above and below that is written twice: the mode state
(`FoliagePaintMode` / `SculptMode`), the host interface (`FoliagePaint` / `TerrainSculpt`), the gesture
(`Gesture.Painting` / `Gesture.Sculpting`), the toolbar controls (`FoliagePaintControls` / `SculptTerrainControls`),
the stroke command lifecycle, the draft registries, and the stamp-to-grid loop (`FoliageBrush` / `SculptBrush`).
Painting terrain textures (the splat map) is planned as a third brush; without this change it would be a third copy of
all of it, edited into `GestureDriver`, `SceneViewState` and the toolbar. The sculpting change has just finished the
second copy, so this is the cheapest point to unify.

## What Changes

A pure refactor: no file format, rendered output, message, toolbar text, key binding or limit changes, and a stroke
writes the same bytes as today.

- **Generic stamp engine in `lib-core-editor` `brush`:** `Footprint`, `BrushStamp<G>`, `BrushTool<G>`,
  `BrushTarget<G>` and `StampBrush<G>`, generalizing today's `SculptBrush`. A tool computes one change per footprint
  cell and channel from the grid as it was before the stamp; the target applies the changes (quantizes, clamps,
  renormalizes) and reports the dirty rectangle.
- **Sculpt and foliage become configurations of it:** `SculptTool` stays a sealed interface (now a `BrushTool` over
  `SculptGrid`) and `SculptDraft` becomes a `BrushTarget`; `FoliageBrush` becomes a thin `StampBrush` over a byte
  mask target with a paint/erase tool, keeping its signature and its exact output bytes.
- **One brush mode, one stroke interface, one gesture:** `BrushMode` replaces `FoliagePaintMode` and `SculptMode`,
  `BrushStroke` replaces `FoliagePaint` and `TerrainSculpt`, and `GestureDriver` gets one `Gesture.Brushing` with one
  `startBrush`. `SceneViewState` holds one `brush` field, so mutual exclusion of the two modes is structural.
- **One cursor, one control strip base, one stroke lifecycle shell:** `BrushCursor` (built on `BrushPath`'s scale
  math), a `BrushStrip` base for the toggle, radius, strength, session loading and ending, a `TerrainStrokeCommand<S>`
  shell for the press/drag/release/cancel lifecycle, and a generic `StrokeDrafts<D>` registry. The two commit
  pipelines (foliage's staged bake, sculpt's file set) stay separate.
- **A `BrushKind` registration** so the planned splat brush adds a strip, a stroke factory and a target without
  editing `GestureDriver`, `SceneViewState` or the toolbar.
- Documentation of the new structure in `docs/ai/architecture.md`, `projects/lib-core-editor/README.md` and the
  `sceneview` README.

Out of scope: the splat brush itself, any change to the Shift convention (foliage's Erase checkbox ORs with Shift,
sculpt's Shift flips; both are kept), shared radius or strength between modes, new toolbar controls, and merging the
live-preview sinks (sculpt copies into the mesh, foliage re-scatters chunks; only `revision` and the dirty rectangle
are shared).

Native file fields: none read or written differently. `terrain.data`, the sculpt delta and manifest, the foliage mask
and bake, and `meta.json` keep their formats, validation and write paths (`AssetTransaction` for strokes,
`editSceneJson` for scene documents).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. No requirement changes; the change sets `skip_specs: true`. `terrain-foliage-painting` and the sculpting
requirements in the open `add-terrain-sculpting` change describe behavior that this change must keep, and their tests
are the oracle.

## Impact

- `projects/lib-core-editor`: `brush` (new generic types, `MaskRect` renamed `GridRect`), `foliage/FoliageBrush.kt`,
  `foliage/FoliageDraft.kt`, `terrain/sculpt/*` (tool and brush supertypes), `pick/` (`SceneViewState`,
  `GestureDriver`, `SceneInteraction`, `FoliagePaint`, `TerrainSculpt`, `DrawingSession`).
- `projects/plugin-abyssus`: `sceneview` (`FoliagePaintControls`, `SculptTerrainControls`, `FoliageBrushCursor`,
  `SceneInputForwarder`, `SceneRenderer`, `SceneViewPanel`), `foliage/FoliageStrokeCommand.kt`,
  `foliage/FoliageDrafts.kt`, `terrain/SculptStrokeCommand.kt`, `terrain/SculptDrafts.kt`.
- Tests that must stay green unchanged in behavior: `FoliageBrushTest`, `SceneInteractionPaintTest`,
  `SceneInteractionSculptTest`, the `Sculpt*Test` classes, `FoliageStrokeCommandTest`, `SculptStrokeCommandTest`,
  `SceneFoliageControlsTest`, `SculptModeAvailabilityTest`, and the UI helper tests that look controls up by name.
- Sequencing: applies after `add-terrain-sculpting` is archived. Its sculpt files are still uncommitted in the
  working tree, and unifying them earlier would mean rebasing against unfinished work.
- Module rules unchanged: `lib-core-editor` stays free of IntelliJ, Swing and AWT and of behavior-carrying
  `object`s; `checkPackageCycles` must stay green (the move of `MaskRect` out of `foliage` removes a
  `terrain.sculpt` to `foliage` edge).
