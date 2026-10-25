# Tasks

## 1. Sculpt storage and state (lib-core-editor)

- [x] 1.1 Add `SculptManifest` codec and file layout (delta/base grids, hashes) in `net.nevinsky.abyssus.lib.core.editor.terrain.sculpt`; verify with `SculptManifestTest` (round trip, unsupported schema, malformed, wrong resolution) via `./gradlew :lib-core-editor:test --tests '*SculptManifestTest'`
- [x] 1.2 Add `SculptState` reader (None / Sculpted(recipe|stored) / Unsafe(reason)) resolving the base from the recipe or the stored base and checking all fingerprints; verify with `SculptStateTest` covering matching, external `terrain.data` edit, missing delta, mismatched recipe, no recipe
- [x] 1.3 Add composition `compose(base, delta)` and `SculptDraft` (composed + delta copies, dirty rectangle); verify with `SculptDraftTest` that composed equals base + delta exactly after strokes
- [x] 1.4 Update `docs/ai/file-formats.md` terrain section (new files, recipe `heightsSha256` now means base), `projects/lib-core-editor/README.md`; run `scripts/check-docs.sh`

## 2. Brush path and tools (lib-core-editor)

- [x] 2.1 Extract the stamp walker from `FoliageBrush` into a shared `BrushPath` that takes a stamp callback (foliage keeps its mask writes in the callback), and add the sealed `SculptTool` interface the tools below implement; keep `FoliageBrush` behaviour; verify with the existing `FoliageBrushTest` and `SceneInteractionPaintTest` unchanged and passing
- [x] 2.2 Raise/Lower with Shift invert and smoothstep falloff incl. rotated and scaled entity matrix; verify with `SculptRaiseTest` (centre rises, outside radius unchanged, scaled entity matches drawn circle)
- [x] 2.3 Smooth, Flatten and Set height (relative-delta writes, neighbour snapshot); verify with `SculptSmoothFlattenTest` (flat area is empty stroke, flatten converges to start height, delta equals target minus base)
- [x] 2.4 Noise (deterministic, resolution independent) and Slope limit; verify with `SculptNoiseTest` (same stroke twice gives identical bytes; two resolutions agree at world points) and `SculptSlopeLimitTest` (no slope above the limit within strength 1)
- [x] 2.5 Thermal erosion (fixed order, iterations, talus) ; verify with `SculptErosionTest` (deterministic, mass conserved within float tolerance, slopes decrease)
- [x] 2.6 Stamp from heightmap (Add/Max/Min, size, rotation, height scale, bilinear sampling); verify with `SculptStampTest` against a small synthetic image
- [x] 2.7 Document tools in `projects/lib-core-editor/README.md`; run `scripts/check-docs.sh`

## 3. Regenerate, reset and forget (lib-core-editor and plugin terrain panel)

- [x] 3.1 Make regeneration compose `base' + delta` unless Discard, replace a stored base with the recipe, and write sculpt files; verify with `TerrainRegenerateSculptTest` (keep gives new noise plus the same delta bytes; discard removes files) in `:lib-core-editor:test`
- [x] 3.2 Add Reset sculpting and Forget sculpting as `AssetTransaction` `FileChange` sets; verify with `SculptResetTest` (recipe base, stored base, unsafe state, undo restores exact bytes)
- [x] 3.3 Terrain panel: sculpted state line, Reset, Forget, Discard sculpting option, updated regenerate text and strings in `AbyssusBundle.properties`; verify with the plugin test `TerrainGenerationSectionTest` extended (`./gradlew :plugin-abyssus:test --tests '*TerrainGenerationSectionTest'`)
- [x] 3.4 Update `docs/ai/architecture.md` and `terrain-authoring` notes; run `scripts/check-docs.sh`

## 4. Live heights and mesh update (lib-core, plugin sceneview)

- [ ] 4.1 `TerrainData` dirty-region normal recompute and an in-place copy of a rectangle of heights (the `heights` array is mutated, not swapped); verify with `TerrainDataTest` (normals match a full rebuild after a region edit)
- [ ] 4.2 First check whether `IntMesh` in `lib-gdx` supports a partial vertex upload (record the answer in design decision 8; if not, use the full upload); then `TerrainMesh.updateRegion` with one-vertex border and full-upload fallback; verify with a GL test `TerrainMeshUpdateTest` run with `-Dabyssus.glTests=true` (vertex buffer equals a fresh mesh) and a headless test of the region/border computation
- [ ] 4.3 Refresh the ray-tracing terrain snapshot after commit; verify with the existing ray terrain snapshot test extended for changed heights (`:lib-core:test`)

## 5. Sculpt Terrain mode (plugin sceneview)

- [ ] 5.0 `SculptDrafts` registry in the plugin (counterpart of `FoliageDrafts`: open, of, discard, discardAll, `revision` and per-view dirty rectangle), passed to each view's renderer; each view copies dirty rectangles into its own `TerrainData` and calls `TerrainMesh.updateRegion` on the render thread; verify with `SculptDraftsTest` (two views see one stroke, a view opened mid-stroke gets the whole array, discard drops the entry)
- [ ] 5.1 Stroke command modelled on `FoliageStrokeCommand`: press, drag, release, discard, one commit through the regeneration `AssetTransaction` and `AssetTransactionEngine`, first-stroke stored base; verify with `SculptStrokeCommandTest` (empty stroke writes nothing, failed write restores files, undo/redo, conflict rejection)
- [ ] 5.2 Toolbar toggle, availability and disabled reasons, brush strip with per-tool options, cursor circle, input forwarding and Shift handling; verify with `SculptModeAvailabilityTest` and the UI helper tests
- [ ] 5.3 Stamp image chooser and decode off the AWT thread without GL; verify with `StampImageSourceTest` (missing asset disables tool)
- [ ] 5.4 Headless check that foliage on the Untitled terrain follows raised heights and a stale bake keeps its x/z; verify with `FoliageFollowsSculptTest`
- [ ] 5.5 Update `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md`; run `scripts/check-docs.sh`

## 6. Manual and integration checks

- [ ] 6.1 runIde check on a copy of Untitled (never the committed folder): 1) select entity 1 Terrain, turn on Sculpt Terrain; 2) drag Raise, release; 3) open a second Scene view and confirm live update; 4) Undo and Redo; 5) Regenerate keep and Discard; 6) Reset; 7) rotate and scale the terrain entity and sculpt under the circle
- [ ] 6.2 Run `./gradlew check` and `scripts/check-docs.sh`; report any failures outside this change instead of fixing them silently
