# Design

## Context

See proposal.md for motivation. Today:

- `lib-core-editor/brush` holds `BrushPath` (stamp walk and the grid cells within a radius) and `smoothFalloff`;
  `MaskRect` is the dirty rectangle type and is in the middle of moving there from `foliage`.
- `SculptBrush` is already a generic loop in sculpt types: walk, build a footprint, ask a `SculptTool` for one change
  per footprint cell (reading the heights as they were before the stamp), add the changes to the `SculptDraft`.
  `FoliageBrush` is the same loop written by hand over a byte mask.
- `pick/` has two mode records on `SceneViewState` (`paint`, `sculpt`), two host interfaces, two gestures and two start
  functions in `GestureDriver` that differ only in the mode they read.
- `plugin-abyssus/sceneview` has two control strips with the same toggle / radius / strength / session-load-token
  shape, and one cursor class named for foliage that both modes use.
- Two stroke commands share a lifecycle (`active`, `pressed`, `dragged`, `released`, `cancelled`, `dispose`) and two
  `@Service` registries share a shape. Their commit pipelines have nothing in common: foliage stages a bake on a
  background thread, expects file snapshots and keeps an undo cache; sculpt plans one synchronous file set.
- Planned: a splat-map texture brush (4 byte channels per texel whose sum is kept, stored as the image of a texture
  asset). Its semantics are not specified yet.

## Goals / Non-Goals

**Goals:**

- One generic stamp engine that both brushes run on, whose tool and target contracts a splat brush can implement
  without editing the engine, the gesture driver, the view state or the toolbar.
- Byte-for-byte identical output for foliage strokes, bit-for-bit identical heights for sculpt strokes.
- Every unified class is testable without Swing, GL or the platform.

**Non-Goals:**

- The splat brush, its image encoding, or its live texture upload.
- A common draft or live-preview contract. Sculpt copies dirty heights into each view's mesh, foliage re-scatters
  dirty chunks, splat will upload a texture region; the only shared piece is `GridRect`.
- Making Shift consistent between the modes.

## Decisions

### 1. The engine lives in `lib-core-editor/brush` and is generic over the grid, not over the element type

```
Footprint        gx[], gz[], falloff[], weight[], count            reused per stamp, grows by doubling
BrushGrid        resolution, size, cell, sampleAt(x, z): Float      read view of the data before a stamp
BrushStamp<G>    grid: G, footprint, centreX/Z, cellWorldX/Z, strength, reversed, strokeStart
BrushTool<G>     channels = 1; changes(stamp, out: FloatArray); reach(r); smoothFalloff; singleStamp
BrushTarget<G>   grid: G; apply(gx, gz, changes: FloatArray, offset): Boolean
StampBrush<G>    (path, footprint, tool, target): stroke(from, to, radius, strength, reversed): GridRect
```

`SculptGrid` implements `BrushGrid`, and `SculptTarget(draft, size)` adapts `SculptDraft` (which does not know the
terrain size) to `BrushTarget<SculptGrid>`. `SculptTool` stays a sealed interface, now `sealed interface SculptTool :
BrushTool<SculptGrid>`, so the factory's exhaustive `when` and all eight tools keep their names; the tool files change
their supertype and the stamp type only. `FootprintScratch` and the bilinear `heightAt` stay sculpt-only.

`strokeStart` is `grid.sampleAt(centre)` at the first stamp of a stroke (what Flatten moves toward today). It is
generic because computing it is the engine's job, but only height tools read it.

*Alternative: generic over the element type (`BrushTarget<Float>` / `<Byte>`).* Rejected: neighbour-reading tools need
typed grid access (`SculptGrid.at`, `heightAt`), which an element type does not give, and a splat grid has four
channels per cell.

### 2. A tool returns floats for every target; the target quantizes

`out` holds `footprint.count * channels` floats. The target decides what a change means: sculpt adds the float,
foliage rounds and clamps to 0..255, splat will renormalize across channels. `apply` receives all channels of one
vertex at once because splat cannot be applied per channel. `apply` returns whether the stored value changed, which is
what builds the dirty rectangle (foliage reports a change only when the clamped byte differs, sculpt when the change
is not zero; both stay as they are).

Two exactness rules, because the existing tests compare bytes:

- **Expression order.** Foliage computes `strength * 64 * smoothFalloff(falloff)` and rounds. Sculpt computes
  `strength * smoothFalloff(falloff)`. Multiplying differently can differ by one ulp and flip a half-way rounding. The
  footprint therefore keeps the raw `falloff` next to `weight`, and the foliage tool reproduces today's expression
  order from `falloff` and `strength`.
- **Rounding of erase.** `roundToInt` rounds half up, so `round(-x)` is not `-round(x)` at exact halves. Today erase
  rounds the positive delta and subtracts it. The byte mask target therefore rounds `abs(change)` and applies the sign
  afterwards. A test pins a half-way case in both directions.

*Alternative: a typed `apply` per element kind.* Rejected: it puts quantization into each tool and gives splat nothing
to reuse.

### 3. `BrushMode`, `BrushStroke`, `Gesture.Brushing`

`BrushMode(kind, entityId, radius, strength, reversed, options)` replaces `FoliagePaintMode` and `SculptMode`.
`options: BrushOptions` is a marker interface implemented by the plugin-side or kind-specific records
(`FoliageBrushOptions(layerId)`); `GestureDriver` never reads it. `reversed` is the already-resolved flag: the strip
computes it (foliage: `erase || shift`, sculpt: the tool's inversion with Shift flipping it), so the two conventions
are kept and the driver passes `mode.reversed` through. `BrushStroke` has `pressed(terrain, at)`,
`dragged(to, reversed)`, `released()` and `cancelled()`, the shape both interfaces already have.

`SceneViewState` holds `brush: BrushMode?`; `brushing` becomes `brush != null` and `endBrush` sets it to null. The
gesture driver has one `startBrush` (selected entity matches, ray, `terrainOf`, `terrainHit`, `pressed`) and one drag
and release path. `kind` names which stroke the mode drives (`FOLIAGE_BRUSH`, `SCULPT_BRUSH`). `SceneInteraction` keeps one stroke per kind
(`setBrushStroke(kind, stroke)`, `brushStroke(kind)`) because both strips are alive for the selected entity at once and each
registers its own stroke when its session loads; the gesture captures the kind at the press and a drag is applied only
while the mode still has that kind.

*Alternative: keep two fields and a shared supertype.* Rejected: mutual exclusion would remain a convention
enforced in two controls instead of a property of the state.

### 4. `BrushStrip` is the plugin's registration seam, and `BrushKinds` is its registry

A brush is one `BrushStrip<S>` subclass (it carries its kind id, its session, its stroke and the mode it builds). The
view registers its strips in a `BrushKinds` in toolbar order (foliage, sculpt, later splat), and the toolbar, the panel
and the Shift handler loop over it. Starting one strip ends the others that hold the view (`BrushKinds.endOthers`),
which replaces the explicit `endFoliage` callback. A separate `BrushKind` record would only have repeated what the
strip already holds.
`BrushStrip` is an abstract base for the shared parts: the toggle, radius and strength spinners, the session-load
token (`entity`, `load`, `closed`, `refreshSession`), `end`, `close`, `beforeSync` and `sync`. Subclasses add their
controls and build their `BrushMode.options`. Because the view holds one mode, a strip puts its mode on the view only
when it is its own kind, clears the view only when the mode is still its own, and treats another kind's mode replacing
its own as the end of its brush (as the sculpt strip's `beforeSync` did when Paint Foliage took the view). Component names (`paint-foliage`, `foliage-brush-radius`,
`sculpt-radius`, ...) and message keys do not change; the UI tests find controls by them.

*Alternative: one combined brush panel with a kind selector.* That is a UI change and is out of scope.

### 5. `BrushCursor` on `BrushPath`

`FoliageBrushCursor` recomputes the scale and inverse that `BrushPath` already owns. `BrushCursor` takes them from
`BrushPath` (`scaleX`, `scaleZ`, `toLocal`) and is renamed. The radius circle it draws is unchanged.

### 6. A stroke lifecycle shell, with the commit pipelines left alone

`TerrainStrokeCommand<S>` (package `plugin/brush`, so foliage and terrain gain no edge between them) implements
`BrushStroke` and owns: the `active` stroke, the preconditions of `pressed` (a mode is on, nothing is active, the mode's
entity matches the terrain), `dragged` forwarding, and the dispose path. Subclasses supply
`begin(terrain, at, mode): S?` (null refuses the press), `press`, `drag`, and write their own `released` and `cancelled`,
which have nothing in common.
`FoliageStrokeCommand` keeps its `generation`, `releasing` flag, staged backups and background bake, and
`SculptStrokeCommand` keeps its synchronous commit; those are not generalized.

A generic `StrokeDrafts<D>` map behind `FoliageDrafts` and `SculptDrafts` was dropped. Measured, the two `@Service`
classes are 25 lines of code between them (a concurrent map, `of`, `open`, `discard`, `discardAll`), and services are looked
up by class, so both would stay as delegating wrappers around a new class of about the same size. Their policies also
differ (foliage's draft outlives a stroke and is shared with the panel, sculpt's exists while the button is down), which a
shared class would only expose as two methods. The shell in `plugin/brush` is the only shared stroke code.

### 7. Threading and purity

All new `brush` types, `BrushMode`, `BrushStroke` and the shell's pure parts are in `lib-core-editor`: no `Gdx.*`, no
Swing, no AWT, no `object` with behavior. They run on the AWT thread, as the brushes do today. `BrushStrip` and
`BrushCursor` are Swing and `LineBatch` code in `plugin-abyssus/sceneview` on the AWT thread; the cursor draws inside
the renderer's existing frame. Foliage's bake staging stays on its background scheduler, unchanged. Nothing new
touches GL.

## Risks / Trade-offs

- [A refactor of the foliage brush changes mask bytes] → Characterization tests are written first and kept: golden
  masks for Paint and Erase including half-way rounding, and golden heights for every sculpt tool. They are written
  against the current code before anything moves and must pass unchanged after each slice.
- [Hot path allocation or boxing while dragging a 255 by 255 sculpt] → Footprint and change arrays are reused and
  sized by doubling (the change buffer to twice the first need, because the number of grid points in a circle varies a
  little with where its centre falls); the tool contract is primitive arrays. Checked by hand while dragging in `runIde`.
- [UI tests look up controls by name] → `BrushStrip` keeps every component name; `uiHelperTest` runs after the strip
  slice.
- [The generic contract is designed before its third user] → The engine test suite includes a fake four-channel
  renormalizing target and tool, in test sources only, so the multi-channel path is exercised. The splat change may
  still adjust the contract; the fields it needs (`channels`, all-channels `apply`) are in from the start.
- [Working tree churn] → The change is applied after `add-terrain-sculpting` is archived, one slice per commit, each
  green.
- [Type parameters spread through sculpt tool files] → Limited to supertype and stamp type; `SculptTool` and the tool
  names stay.

## Migration Plan

Mechanical slices, each compiling and passing the existing tests: characterization tests; `MaskRect` to `GridRect`;
generic engine plus sculpt on it; foliage on it; `BrushMode` / `BrushStroke` / gesture and state; `BrushCursor`;
`BrushStrip` and `BrushKind`; the stroke shell and drafts; documentation. No data or file migration. Rollback is
reverting slices from the top.
