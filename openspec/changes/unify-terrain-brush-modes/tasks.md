# Tasks

Apply after `add-terrain-sculpting` is archived. Each group compiles and passes the existing tests on its own; commit per group.

## 1. Characterization tests (lib-core-editor)

- [x] 1.1 Pin `FoliageBrush` output before anything moves: golden masks for Paint and Erase over a scaled and yaw-rotated entity, a stroke split into many drags equal to one drag, and a half-way rounding case in both directions; verify with `./gradlew :lib-core-editor:test --tests '*FoliageBrushTest'` passing on the unmodified brush
- [x] 1.2 Pin `SculptBrush` output for every tool (raise/lower with and without Shift, smooth, flatten, set height, noise, slope limit, erosion, stamp) as golden float arrays or hashes over one fixed terrain; verify with the new `SculptBrushGoldenTest` passing on the unmodified brush and `./gradlew :lib-core-editor:test --tests 'net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.*'` still green

## 2. Grid rectangle and generic engine (lib-core-editor)

- [x] 2.1 Rename `MaskRect` / `emptyMaskRect` to `GridRect` / `emptyGridRect` in `brush` and update every user (foliage, sculpt, plugin, tests); verify with `./gradlew :lib-core-editor:test :plugin-abyssus:compileTestKotlin` compiling both modules, `grep -rn "foliage.MaskRect\|foliage.GridRect" projects` empty, and `checkPackageCycles` reporting the same cycles as before the change (the module already has cycles among `arrange`, `pick`, `scene`, `components`, `meta`, `terrain` and in the plugin, none involving `brush`, `foliage` or `terrain.sculpt`; the allowlist stays empty and 8.2 expects exactly those)
- [x] 2.2 Add `Footprint` (with raw `falloff` next to `weight`), `BrushGrid`, `BrushStamp<G>`, `BrushTool<G>`, `BrushTarget<G>` and `StampBrush<G>` in `brush`, with footprint and change arrays reused from stamp to stamp (only the small `BrushStamp` view is made per stamp, as `SculptStamp` is today); verify with `StampBrushTest` using a fake one-channel tool and target (spacing across split drags, `singleStamp`, `reach`, `strokeStart` taken once, dirty rectangle equals the cells that reported a change)
- [x] 2.3 Prove the multi-channel path in test sources only: a fake four-channel tool and a renormalizing target (sum kept at 255); verify with `StampBrushChannelsTest` (the target receives all channels of a vertex at once, the sum holds after every stamp)

## 3. Sculpt on the engine (lib-core-editor)

- [x] 3.1 Make `SculptGrid` a `BrushGrid`, `SculptTool` a sealed `BrushTool<SculptGrid>` and `SculptDraft` a `BrushTarget<SculptGrid>`; move `SculptFootprint` to `brush.Footprint`; reduce `SculptBrush` to a `StampBrush` call keeping its constructor and `stroke` signature; verify `SculptBrushGoldenTest` and all `Sculpt*Test` classes pass unchanged
- [x] 3.2 Check the hot path allocates nothing per stamp after warm-up; verify with `SculptBrushAllocationTest`: a drag of 50+ stamps of a radius-40 Raise/Lower-style tool over a 255 grid hands the tool the same footprint and change arrays every time (the change buffer is sized at twice the first need so the few cells a circle gains or loses by sub-cell position do not reallocate it)

## 4. Foliage on the engine (lib-core-editor)

- [x] 4.1 Add the paint/erase tool (`strength * 64 * smoothFalloff(falloff)` in today's expression order) and a byte mask target that rounds `abs(change)`, applies the sign afterwards, clamps to 0..255 and reports a change only when the byte differs; reduce `FoliageBrush` to a `StampBrush` call with the same signature and `MaskRect`-equivalent result; verify `FoliageBrushTest` (golden masks from 1.1) and `FoliageDraftTest` pass unchanged
- [x] 4.2 Check `FoliageBrush` and the engine agree on a mask-resolution grid that is not square-aligned to the terrain size; verify with `FoliageBrushTest` cases for a non-default `maskResolution` and a scaled entity
- [x] 4.3 Update `projects/lib-core-editor/README.md` (`brush`, `terrain`, `foliage` rows: the engine types, which brushes run on them); run `scripts/check-docs.sh`

## 5. One mode, one stroke interface, one gesture (lib-core-editor)

- [x] 5.1 Add `BrushMode`, `BrushOptions` and `BrushStroke` in `pick`; replace `FoliagePaintMode` / `SculptMode` with `BrushMode` and `FoliagePaint` / `TerrainSculpt` with `BrushStroke`; `SceneViewState` holds `brush`, `brushing` and `endBrush` follow; verify `./gradlew :lib-core-editor:test` compiles and `SceneInteractionPaintTest` / `SceneInteractionSculptTest` pass with only their mode and stroke construction changed, not their assertions
- [x] 5.2 Replace `Gesture.Painting` / `Gesture.Sculpting` and `startStroke` / `startSculpt` with `Gesture.Brushing` and one `startBrush`; the drag passes `mode.reversed`; Esc, release and the terrain leaving the scene end the stroke as before; verify with both interaction tests plus a new case that a Paint stroke and a Sculpt stroke cannot be active at once (a second `brush` assignment replaces the first)
- [x] 5.3 Update the `pick` row of `projects/lib-core-editor/README.md` and the gesture description in `docs/ai/architecture.md`; run `scripts/check-docs.sh`

## 6. Cursor, strips and kinds (plugin-abyssus)

- [x] 6.1 Rename `FoliageBrushCursor` to `BrushCursor` and build it on `BrushPath` (`scaleX`, `scaleZ`, `toLocal`); verify `SceneViewPanelTest` and `SceneFoliageControlsTest` pass and that the circle matches the old one on a scaled, rotated entity (a headless test comparing the 64 line endpoints against the previous formula)
- [x] 6.2 Add `BrushStrip` (toggle, radius, strength, session-load token, `beforeSync`, `sync`, `end`, `close`) and port `FoliagePaintControls` onto it, keeping every component name and message key; verify `SceneFoliageControlsTest` and `./gradlew :plugin-abyssus:uiHelperTest` pass
- [x] 6.3 Port `SculptTerrainControls` onto `BrushStrip`, keeping the tool combo, option cards and stamp decoding in the subclass; verify `SculptModeAvailabilityTest` and `./gradlew :plugin-abyssus:uiHelperTest` pass
- [x] 6.4 Add `BrushKind` and build the view's brush controls from a list of kinds in today's toolbar order; starting one kind ends the others, replacing the `endFoliage` callback; verify with a headless test that toggling Paint Foliage turns Sculpt Terrain off and the reverse, and that the toolbar order is unchanged
- [x] 6.5 Update `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md` (the `FoliagePaintControls` row, the Sculpt Terrain paragraph, `BrushStrip`, `BrushKind`, `BrushCursor`); run `scripts/check-docs.sh`

## 7. Stroke shell and drafts (plugin-abyssus)

- [x] 7.1 Add `TerrainStrokeCommand<S>` (active stroke, `pressed` preconditions, `dragged`, `released`, `cancelled`, `dispose`) and port `SculptStrokeCommand` onto it; verify `SculptStrokeCommandTest` passes unchanged
- [x] 7.2 Port `FoliageStrokeCommand` onto the shell, keeping `generation`, `releasing`, staged backups and the background bake in the subclass; verify `FoliageStrokeCommandTest` passes unchanged, including its stale-release and disposal cases
- [x] 7.3 Dropped, as the task allowed: `StrokeDrafts<D>` would save nothing. `FoliageDrafts` and `SculptDrafts` are 25 lines of code together and, being services looked up by class, would stay as wrappers around a shared map of about the same size; the reason is recorded in design.md decision 6. Verified by `SculptViewSyncTest` and the foliage draft tests passing unchanged
- [x] 7.4 Update `docs/ai/architecture.md` (brush data flow: mode, gesture, stroke, engine, commit) and `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/terrain/` notes if any; run `scripts/check-docs.sh`

## 8. Integration

- [x] 8.1 Manual check in `./gradlew :plugin-abyssus:runIde` on a copy of a project with a terrain and a foliage asset: (1) Paint Foliage paints and Shift or the Erase box erases; (2) Sculpt Terrain raises, and Shift lowers; (3) turning one mode on turns the other off; (4) Esc during a stroke of each discards it; (5) one undo reverts each stroke; (6) the cursor circle matches the footprint on a scaled and rotated terrain; (7) dragging Raise with a large radius on a 255 by 255 terrain stays smooth
- [x] 8.2 Run `./gradlew check` and `scripts/check-docs.sh`; confirm `checkNoSingletons`, `checkPackageCycles`, `checkNoEditorCode` and `NoPlatformClasspathTest` are green and the allowlist `gradle/package-cycles.allowlist` is still empty
