# Tasks

## 1. Asset edit model and metadata commands

- [x] 1.1 Add core field descriptions and AssetMetaEditor for terrain size/uv/splat fields, cube faces and procedural atmosphere fields, including omitted effective defaults, identity protection, no-op detection and expected-value conflicts; verify core `AssetMetaEditorTest` cases for all value kinds, unchanged unknown fields, invalid numeric ranges and coupled atmosphere radii with `./gradlew :core:test`.
- [x] 1.2 Add project texture/image choice models with UUID storage, readable image filtering, unresolved-current-value display and canonical path containment; verify `AssetReferenceChoicesTest` for null, missing UUIDs, wrong types, symlink escapes and folder boundary cases with `./gradlew :test --tests 'net.nevinsky.abyssus.properties.AssetReferenceChoicesTest'`.
- [x] 1.3 Wire metadata commits through editSceneJson and retain optimistic conflict checking; verify `AssetMetaEditsTest` for one-field edits, exact Undo/Redo, `60.0` preservation, omitted defaults, unchanged lastModified, malformed input and stale field rejection with `./gradlew :test --tests 'net.nevinsky.abyssus.properties.AssetMetaEditsTest'`.
- [x] 1.4 Update docs/ai/conventions.md and architecture.md for asset metadata edits and editor models; verify `scripts/check-docs.sh` and check the documented write path matches 1.3.

## 2. Typed asset properties UI

- [x] 2.1 Add typed asset rows, shared-asset note and supported/unsupported header handling, sharing only useful editor widgets with EntityDetailsView; verify AssetPropertiesPanelTest for terrain editors, six cube faces, atmosphere defaults, unsupported metadata inspection, validation reversion, selection without writes and unchanged entity editing with `./gradlew :test --tests 'net.nevinsky.abyssus.properties.*PropertiesPanelTest'`.
- [ ] 2.2 Add localized field labels, errors, shared scope and command text, and properties-panel Undo context for metadata edits; verify AssetPropertiesPanelTest for error/refresh behavior and manual check M1 below.
- [ ] 2.3 Update README properties instructions while preserving Plugin description markers; verify `scripts/check-docs.sh` and manual check M1 against the documented behavior.

## 3. Deterministic terrain generation and formats

- [x] 3.1 Pin FastNoiseLite Java source and license in an isolated third-party location, record the immutable source revision, wrap it behind an injected noise sampler and document the exception to Kotlin-only sources; verify the retained license/revision artifact, `./gradlew :core:checkNoSingletons` and `./gradlew :core:compileKotlin`.
- [x] 3.2 Implement settings validation and world-local OpenSimplex2 fBm generation with finite bounded heights, z-major orientation and cancellation; verify TerrainGeneratorTest for golden bytes, repeatability, differing seeds, height range, zero persistence, resolution endpoints 2/255, invalid settings and cancellation with `./gradlew :core:test`.
- [x] 3.3 Add big-endian height encoding and new terrain metadata construction; verify the precise terrain metadata and binary conventions against a pinned upstream Mundus writer, record the source reference in core README, and verify TerrainAssetEncodingTest plus TerrainDataTest round trips for the 180-resolution fixture and new assets with `./gradlew :core:test`.
- [x] 3.4 Add recipe encoding/status and pure draft/heightmap preview models; verify TerrainRecipeTest and TerrainGenerationDraftTest for round trips, missing/malformed/unknown recipes, height or size mismatch, stale completion, changed settings, cancellation, and same-seed previews with `./gradlew :core:test`.
- [x] 3.5 Update core README and docs/ai/file-formats.md for generation API, default settings, the separate recipe and unchanged terrain format; verify `scripts/check-docs.sh` and the recipe/encoder examples against 3.3/3.4 tests.

## 4. Asset revision refresh

- [x] 4.1 Add explicit cache invalidation and revision tracking with stale request disposal and failed-load retry; verify AssetCacheTest for invalidate-ready, invalidate-loading, repeated changes, stale upload completion, exactly-once disposal and isolated project caches with `./gradlew :core:test`.
- [x] 4.2 Add terrain dependency revision models and refreshable UUID lookup/metadata snapshots, retaining unaffected drawables; verify AssetDependencyRevisionTest and AssetFilesTest for splat image replacement, UUID changes, absent textures, membership changes and separate projects with `./gradlew :core:test`.
- [x] 4.3 Observe project asset VFS/document changes, capture unsaved metadata overrides, coalesce effective revisions and queue GL work for a safe render frame; verify `AssetRefreshTest` with fake scene/loading boundaries for unsaved text, save deduplication, Undo/Redo, external deletion/recreation, hidden view deferral and disposed listeners with `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.AssetRefreshTest'`.
- [ ] 4.4 Publish terrain mesh/CPU data as one replacement and notify available Drop/shadow consumers; verify ScenePickerTest for new heights and `TerrainRefreshTest` for shared instances/current geometry, plus manual checks M3/M5. Inspect the open drop/shadow delta specs and amend any requirement actually contradicted by the integrated implementation; verify their OpenSpec validation when amended.
- [ ] 4.5 Update sceneview README and docs/ai/architecture.md for asset revisions, dependency refresh, metadata snapshots and GL thread rules; verify `scripts/check-docs.sh` and manual check M5.

## 5. Undoable terrain file commands

- [x] 5.1 Add immutable byte snapshots and staged plugin AssetFileCommand commits for regeneration, creation and folder/file absence, with expected-state checks and rollback; verify AssetFileCommandTest with injected failures before/after each file write, late cancellation, stale source data, unsaved metadata conflicts and no success event on failure with `./gradlew :test --tests 'net.nevinsky.abyssus.properties.AssetFileCommandTest'`.
- [ ] 5.2 Register binary/file UndoableActions and properties/creation command contexts; verify AssetFileCommandTest for exact height/recipe restoration, previously absent recipe, same UUID on Redo, external byte changes, new folder collisions, newly added files and new saved/unsaved scene references blocking creation Undo; verify manual checks M2/M4 for initiating-UI behavior.
- [x] 5.3 Update docs/ai/conventions.md and architecture.md to describe the justified binary/new-file write path, application rollback and crash limitation; verify `scripts/check-docs.sh` and ensure all existing scene/project edits still use editSceneJson.

## 6. Regeneration panel and new terrain creation

- [x] 6.1 Add terrain controls, read-only existing resolution, grayscale preview, Regenerate preview, Randomize seed, Apply/Cancel and recipe status, using cancellable background work and stable draft state; verify TerrainGenerationPanelTest for matching-preview gating, selection/disposal cancellation, malformed data/recipe handling, apply preservation of the fixture's 180 resolution and all metadata bytes, and localized errors with `./gradlew :test --tests 'net.nevinsky.abyssus.properties.TerrainGenerationPanelTest'`.
- [x] 6.2 Add pure folder-name/creation validation plus New terrain action on the owning Assets node, creation dialog and post-success asset selection; verify NewTerrainActionTest for ownership, standalone selection, missing Assets folder, name/path/symlink/case collisions, 2..255 resolution, failure cleanup, UUID uniqueness, unused listing, unchanged `.abss`/`.scene` bytes and no placement with `./gradlew :test --tests 'net.nevinsky.abyssus.projectView.NewTerrainActionTest'`.
- [ ] 6.3 Add creation/regeneration messages and update README terrain authoring instructions; verify `scripts/check-docs.sh` and manual checks M2/M4 against the documented workflow.

## 7. Integration verification

Use a disposable copy of Untitled, never the fixture itself. Prepare two scene entities using the same terrain and a readable texture asset with a UUID for texture checks. New test class names above denote planned tests, not existing files.

- [ ] 7.1 M1: Run `./gradlew runIde -PideProject=/path/to/copied/Untitled`; edit size 1600 to 800 and uv 60 to 30, assign/clear a texture, edit a cube face and a procedural atmosphere parameter, reject invalid values, and Undo/Redo from the properties panel. Verify only intended metadata fields change and each scene updates without reopening.
- [ ] 7.2 M2: Preview noise on the existing terrain, regenerate unchanged settings twice, Randomize seed, cancel once, then Apply and Undo/Redo from the panel. Verify Cancel changes no bytes, Apply preserves metadata and 180 resolution, both terrain instances refresh, and Undo restores exact previous data/recipe absence.
- [ ] 7.3 M3: Apply twice while loading, externally replace heights, edit unsaved metadata, break then repair a texture/data file and verify latest geometry/material wins; click the new surface and, if the drop change is available, Drop an object onto it. Verify all consumers use the refreshed height field and no objects move automatically.
- [ ] 7.4 M4: Create `hills`, reject invalid/colliding names, verify selection/unused marking and no scene placement, then Undo/Redo creation from the initiating UI. Verify byte-identical restoration and stable UUID; add a reference or external file change and verify conflicting creation Undo is rejected.
- [ ] 7.5 M5: Hide or close scene views during edits and reopen/show them. Verify refresh waits for safe GL rendering, no crash or stale resource use occurs, and unrelated assets/projects remain usable. If shadows are available, verify regenerated terrain updates casting/receiving geometry. Record unperformed conditional checks without claiming them passed.
- [ ] 7.6 Run `openspec validate add-asset-editing-and-terrain-generation --strict`, `./gradlew check` and `scripts/check-docs.sh`; record results and any unrelated failures, keeping unavailable manual checks open.
