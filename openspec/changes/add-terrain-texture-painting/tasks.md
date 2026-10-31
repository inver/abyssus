# Tasks

Apply after `add-terrain-sculpting` and `unify-terrain-brush-modes` are archived. Commit per group; each group compiles and passes the existing tests on its own.

## 1. Splat image, state and shared detection (lib-core-editor)

- [x] 1.1 Check the framework this change builds on exists (`StampBrush`, `BrushTool`, `BrushTarget`, `ByteGrid` with `channels`, `GridRect`, `BrushMode`, `BrushStroke`, `BrushStrip`, `BrushKinds`, `TerrainStrokeCommand`); verify with `./gradlew :lib-core-editor:compileKotlin :plugin-abyssus:compileKotlin` and a grep for each name; stop and report if one is missing
- [x] 1.2 Add `SplatImageCodec` in a new `terrain.splat` package: decode an 8-bit RGBA PNG to a `ByteArray` of non-premultiplied RGBA and encode it back; reject RGB, grey, palette, 16-bit, non-square, under 64 or over 2048 pixels, and non-`.png` files with a reason; verify with `SplatImageCodecTest` (round trip of every channel value at alpha 0 and 255, premultiplication does not occur at alpha 0, each rejection reason)
- [x] 1.3 Add `SplatState` (`None` / `Ready(folder, file, pixels, users)` / `Unusable(reason)`): validate the terrain `meta.json` as native first, resolve `splatMap` by `uuid`, decode, and list the other terrain assets that reference the same `uuid`; verify with `SplatStateTest` using temp project folders (no splat map, unresolved `uuid`, unreadable image, not RGBA, too large, invalid or unsupported `meta.json`, shared by a second terrain)
- [x] 1.4 Document the new package in `projects/lib-core-editor/README.md`; run `scripts/check-docs.sh`

## 2. Splat tools, target and draft (lib-core-editor)

- [x] 2.1 Add `SplatDraft` (RGBA pixels, `revision`, accumulated `changed` rectangle, resolution, size) and `SplatTarget` (applies the four changes of a texel, rounding `abs(change)` and applying the sign afterwards, clamping 0..255, reporting a change only when a byte differs) with Exclusive off; verify with `SplatTargetTest` (clamping, half-way rounding in both directions, no change reported for an equal byte)
- [x] 2.2 Add Exclusive to `SplatTarget`: raised channels first, excess taken proportionally from the others with largest-remainder rounding and ties by channel order, sum never above 255; verify with `SplatExclusiveTest` (sum invariant over a randomized stroke sequence with a fixed seed, deterministic result, full channel painted over a full other channel, Exclusive off leaves the others)
- [x] 2.3 Add the sealed `SplatTool` (`BrushTool<ByteGrid>`, `channels = 4`): Paint (slot channel, reversed lowers only that channel; Base slot acts as Erase), Erase (all channels) and Smooth (3x3 average from a reused per-stamp snapshot); verify with `SplatToolsTest` (centre strongest, nothing beyond the radius, Smooth leaves a flat area unchanged and keeps the sum at or under 255, a scaled and yaw-rotated entity paints under the drawn circle)
- [x] 2.4 Add `SplatDraft.fill(slot)` (whole grid, whole-grid rectangle, Base clears all four channels); verify with `SplatFillTest` (G fill gives 0,255,0,0 everywhere; Base fill gives all zeros; the revision increments)
- [x] 2.5 Pin the texel placement: a stroke's centre texel and the shader's `uv = local xz / size` sampling differ by under half a texel, and image row `r` is grid row `r`; verify with `SplatPlacementTest` against the documented formula
- [x] 2.6 Check the hot path allocates nothing per stamp after warm-up (same method as sculpt's `SculptBrushAllocationTest`); verify with `SplatBrushAllocationTest` over 50+ stamps of radius 40 on a 1024 grid
- [x] 2.7 Document the tools and Exclusive in `projects/lib-core-editor/README.md`; run `scripts/check-docs.sh`

## 3. Create splat map and Make unique copy (lib-core-editor)

- [x] 3.1 Add the new-texture-asset builder (folder name `texture_<terrain>_splat` with `_2`, `_3` while taken, fresh `uuid`, native compact `meta.json` with `type: "TEXTURE"` and `additional.file`, blank or copied `splat.png`) and the terrain `meta.json` edit of `splatMap` through `DocumentTextEditor`; verify with `SplatBootstrapTest` (blank image is all zero RGBA at the requested size, 64 and 2048 accepted and 63 and 2049 refused, formatting, key order and number text of the terrain `meta.json` kept, unknown fields kept, name collision picks the next free name, invalid `meta.json` refused with nothing planned)
- [x] 3.2 Add Make unique copy (same builder with the image copied from the shared map); verify with `SplatUniqueCopyTest` (the other terrain's reference is untouched, the copy equals the source bytes)

## 4. Texture region update (lib-core)

- [x] 4.1 Add `TerrainMesh.splatTexture()` (the built splat `Texture` or null) and `SplatTexture.updateRegion(texture, x, y, w, h, buffer)` with a pure row-packing function that fills a contiguous buffer from a pixel array and rectangle; verify with `SplatTextureRowsTest` (rectangle at a corner, full width, one texel) and `./gradlew :lib-core:test`
- [x] 4.2 GL check that a region update leaves the texture equal to a freshly uploaded one; verify with `SplatTextureUpdateGlTest` run with `-Dabyssus.glTests=true` (read the texture back after updating a rectangle of a 64 map)
- [x] 4.3 Update `projects/lib-core/README.md` (terrain section); run `scripts/check-docs.sh`

## 5. Draft registry, view sync and stroke command (plugin-abyssus)

- [x] 5.1 Add `SplatDrafts` (project `@Service`: `of(splatFolder)`, `open`, `discard`, `discardAll`, keyed by the splat texture's asset folder) and `SplatViewSync` (new revision: copy the changed rectangle into the view's own splat `Texture`, whole array for a view that first sees a draft mid-stroke; draft gone: invalidate the splat texture asset); wire both into `SceneRenderer` next to the sculpt sync; verify with `SplatViewSyncTest` (two views follow one stroke, a view opened mid-stroke gets the whole map, two terrains sharing a map both follow, discard reloads)
- [x] 5.2 Add `SplatTransactions` (one-file transaction for `splat.png` and the multi-file Create and Make unique copy ones with `createdDirs`, expected files and the terrain `meta.json` before/after bytes); verify with `SplatTransactionsTest` (a stroke plans one change, an equal image plans none, Create plans folder + two files + the terrain meta, undo removes the folder and restores `splatMap: null`)
- [x] 5.3 Add `SplatStrokeCommand : TerrainStrokeCommand` with `begin` (state ready, draft opened), `press`, `drag`, Fill on press, `released` (encode off the AWT thread, apply on it, draft kept until applied, `generation` guard) and `cancelled`; verify with `SplatStrokeCommandTest` (empty stroke writes nothing, failed write restores the files, undo and redo, conflict refusal when the image changed in between, Esc leaves the files, a press during a commit is refused, a stale release is dropped)
- [x] 5.4 Check that a committed stroke reloads views and the ray terrain store through the existing revision path: a change of only the splat image flags the terrain in `AssetRevisionTracker`; verify with `AssetRevisionTrackerTest` extended and `AssetRefreshTest` (`./gradlew :plugin-abyssus:test --tests '*AssetRevisionTrackerTest'`)

## 6. Paint Texture strip and toggle (plugin-abyssus)

- [x] 6.1 Add `PaintTextureRequests` and `PaintTextureRequestListener` (mirror of `SculptRequests`) and the `paint-texture` toggle beside `sculpt-terrain` in `EntityDetailsView`; verify with a plugin test that each toggle ends the other and that selecting another entity ends it
- [x] 6.2 Add `SplatSession` and `PaintTextureControls : BrushStrip<SplatSession>` registered after sculpt in `BrushKinds`: slot selector (five slots with name and thumbnail), radius, strength, mode (Paint, Erase, Smooth, Fill), Exclusive (on by default), the reason line for an unusable map, Create splat map with a resolution field (64 to 2048, default 1024), the shared-map warning with Make unique copy; component names `paint-texture-*`, strings in `AbyssusBundle.properties`; verify with `PaintTextureControlsTest` (strip appears with slot 1 chosen, Exclusive default on, Shift reaches `reversed` for Paint only, unusable reasons shown and a press paints nothing, Create and Make unique copy go through `SplatTransactions`)
- [x] 6.3 Add the layer assignment: a chooser of readable texture assets from `AssetReferenceChoices.textures` (unresolved values kept visible) that sets or clears `splatBase`..`splatA` through the existing `AssetMetaEditor` path as one undoable step; thumbnails decoded off the AWT thread and cached by file length and mtime; verify with `SplatSlotAssignTest` (assign, clear, undo restores the old `uuid`, unresolved reference stays selectable and paintable)
- [x] 6.4 Update `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md` and `docs/ai/architecture.md` (the fourth brush kind, the splat draft and sync) and `docs/ai/file-formats.md` (the splat map as an RGBA PNG written by Abyssus, never premultiplied, 64 to 2048 square); run `scripts/check-docs.sh`

## 7. Dependents and UI helper tests

- [x] 7.1 Add the control names to the UI helper lookups and a headless check that the toolbar order, the existing brush control names and the Sculpt and Foliage toggles are unchanged; verify with `./gradlew :plugin-abyssus:uiHelperTest` and `SceneFoliageControlsTest`, `SculptModeAvailabilityTest` passing unchanged
- [x] 7.2 Check that a splat image written by a stroke loads in the game's path: decode it with `TerrainLoader` / `TextureLoader` in a test and compare the pixels with the draft; verify with `TerrainLoaderSplatTest` extended (`./gradlew :lib-core:test --tests '*TerrainLoaderSplatTest'`)

## 8. Integration

- [ ] 8.1 Manual check in `./gradlew :plugin-abyssus:runIde` on a copy of a project with a terrain (never the committed Untitled): 1) select a terrain without a splat map, turn on Paint Texture, Create splat map; 2) assign textures to slots R and G; 3) drag Paint R, release, then Paint G across it with Exclusive on and off; 4) Shift erases, Erase and Smooth, Fill; 5) open a second Scene view and watch a stroke appear live; 6) Undo and Redo each step, and Esc during a stroke; 7) with a second terrain sharing the map, see the warning and Make unique copy; 8) rotate and scale the terrain entity and paint under the circle; 9) turn on Ray tracing, paint and see the traced terrain update; 10) Play and compare; 11) drag with a large radius on a 2048 map and check the stroke stays smooth
- [x] 8.2 Run `./gradlew check` and `scripts/check-docs.sh`; confirm `checkNoSingletons`, `checkPackageCycles`, `checkNoEditorCode`, `checkShippedClasspath` and `NoPlatformClasspathTest` are green and `gradle/package-cycles.allowlist` is still empty; report failures outside this change instead of fixing them silently
