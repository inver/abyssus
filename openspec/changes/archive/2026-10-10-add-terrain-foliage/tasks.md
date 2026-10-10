# Tasks

Commands use the real Gradle paths (`:lib-core`, `:lib-core-editor`, `:lib-gdx-model`, `:plugin-abyssus`). GL tests
need `-Dabyssus.glTests=true` on a machine with a display. Manual checks run in `./gradlew runIde` on a copy of the
`Untitled` project, never on the fixture itself.

## 1. Foliage files in `lib-core` (headless)

- [x] 1.1 Add `MetaType.FOLIAGE` and bind it in `AssetMetaBinder` to `FoliageMeta`, which holds `terrain`, `dataFile`,
  `maskResolution` and the layers with their documented defaults. Verify with `AssetMetaLoaderTest` cases in
  `./gradlew :lib-core:test`:
  - a foliage meta with every field binds;
  - omitted members take their defaults;
  - an unknown layer member does not fail binding.
- [x] 1.2 Add `FoliageMaskFile`: read and write `maskResolution²` bytes in z-major order, where a missing file means 255
  everywhere and a wrong length is an error naming the file. Verify with `FoliageMaskFileTest`: a round trip, a missing
  file, and a truncated file.
- [x] 1.3 Add `FoliageDataFile`, which reads and writes the `ABFO` v1 layout from design decision 3. Verify with
  `FoliageDataFileTest`:
  - a round trip of two layers over 3 × 3 chunks gives the same bytes;
  - another magic number, version 2 or a truncated file all read as "stale" rather than throwing;
  - the chunk size rule `max(32, size/64)` holds for sizes 100 and 1600.
- [x] 1.4 Add `FoliageLoader`, registered in the plugin's `AssetLoading` and in `CompositeAssetLoader` wiring. Its
  prepare step runs off the GL thread: it validates the meta with `AbyssusDocumentFormat`, then reads the masks and the
  bake. It declares the terrain and the layer models as dependencies. Verify with `FoliageLoaderTest` in `:lib-core:test`:
  - the dependencies list the terrain and both models of a two-model layer;
  - `formatVersion` 2 is refused with a reason;
  - a missing bake is prepared as stale, not failed.
- [x] 1.5 Describe the foliage asset (meta fields, mask and bake layout) in `docs/ai/file-formats.md` under "Asset
  `meta.json`", and add `assets/foliage` to the module README. Verify that `scripts/check-docs.sh` passes.

## 2. Settings, generation and fingerprint in `lib-core-editor` (headless)

- [x] 2.1 Add `FoliageSettings`, read from a foliage meta `JsonNode`, with validation that returns
  `AbyssusEditorBundle` reasons: density ≥ 0, scale min ≤ max, alignment in [0, 1], slope in [0, 90], min height ≤ max
  height, weight > 0, unique layer ids, and models that are MODEL assets. Verify with `FoliageSettingsTest` in
  `./gradlew :lib-core-editor:test`, covering each rejection in the spec scenario "Invalid value" and a valid layer of
  `tree`.
- [x] 2.2 Add `FoliageScatter`, the jittered grid with per-cell SplitMix64 streams from design decision 2: mask, height
  and slope rules, models by weight, yaw and scale. Verify with `FoliageScatterTest`:
  - equal inputs give byte-equal bakes;
  - changing a seed changes only that layer;
  - no pair of copies is closer than `0.2 / sqrt(density)`;
  - a slope limit of 20 and a height range of 5 to 40 hold for every copy, checked on the `Untitled` terrain data;
  - an empty mask gives 0 copies;
  - a mask of 128 gives 50% ± 3% of a full mask's count;
  - weights 3:1 give 75% ± 3%.
- [x] 2.3 Add partial re-scatter of a set of chunks. Verify with a `FoliageScatterTest` case: re-scattering the chunks
  under a random rectangle and merging them gives the full bake's bytes, over 20 random rectangles.
- [x] 2.4 Add the copy limit (1,000,000 in total) and the 16,000,000-candidate guard as a result with a reason. Verify
  with a `FoliageScatterTest` case: density 1 on the 1600-unit terrain reports about 2,560,000 and is refused without
  generating every copy.
- [x] 2.5 Add `FoliageFingerprint` over the inputs listed in design decision 3. Verify with `FoliageFingerprintTest`:
  - changing a height byte, a mask byte, a seed, a weight or the terrain size changes it;
  - changing `alignToNormal` or `drawDistance` does not.
- [x] 2.6 Add `FoliageMetaEdits`, which turns a settings diff into `DocumentTextEditor` edits. Verify with
  `FoliageMetaEditsTest`:
  - changing a density keeps `"note": "north slope"`, the key order and the number text of untouched values (`60.0`
    stays `60.0`);
  - adding a layer appends it;
  - removing one deletes only its object;
  - an equal edit produces no change.
- [x] 2.7 Add `NewFoliageFiles`, which plans a new asset: the meta text with the markers first, a fresh uuid,
  `maskResolution`, no layers and an empty bake. It validates the folder name and that the resolution is in 16..2048.
  Verify with `NewFoliageFilesTest`, covering the spec scenarios "Create for the fixture terrain", "Name taken" and
  "Resolution out of range".
- [x] 2.8 Add `FoliagePlacement` to `SceneContent`, for terrain entities with a string `FoliageComponent.assetName`.
  Verify with `SceneContentTest` cases:
  - entity `1` of a `parseScene` copy of `Main Scene` with `FoliageComponent` gives one placement carrying its
    transform;
  - a model entity with the component gives none;
  - a non-string `assetName` gives none.
- [x] 2.9 Describe the `foliage/` package in `lib-core-editor`'s README. Verify that `scripts/check-docs.sh` passes.

## 3. Brush math and drafts in `lib-core-editor` (headless)

- [x] 3.1 Add `FoliageBrush`: stamps along a path at a quarter-radius spacing, the smoothstep falloff, Paint, Erase,
  clamping, terrain-local mapping through an inverse entity matrix, and the dirty rectangle. Verify with
  `FoliageBrushTest`:
  - a radius 10, strength 1 stroke on an empty mask raises texels only within 10 units of the path;
  - Erase on 0 changes nothing and reports an empty dirty rectangle;
  - a rotated, scaled entity paints under the world-space circle.
- [x] 3.2 Add `FoliageDraft`: the draft settings and masks, the revision, the dirty chunks derived from a brush
  rectangle, and revert to the state at the press. Verify with `FoliageDraftTest`: a stamp marks only the chunks it
  overlaps, and revert restores the masks byte for byte.
- [x] 3.3 Add the paint mode to `SceneViewState` and `Gesture.Painting` to `SceneInteraction`:
  - a left press on the selected terrain starts a stroke;
  - other buttons and the wheel navigate;
  - Esc cancels;
  - selecting another entity leaves the mode;
  - presses off the terrain do nothing.

  Verify with `SceneInteractionPaintTest`, using fake `SceneQueries` as in the existing gizmo drag tests, with one
  case per spec scenario of "Paint Foliage mode" and "Painting the density mask".

## 4. Undoable file operations in `plugin-abyssus` (headless)

- [x] 4.1 Add derived files (`DerivedFile` with before/after SHA-256 and `rebuild`) and mask patches (a dirty rectangle
  checked against the file's SHA-256) to `AssetTransaction` / `AssetTransactionEngine`. Verify with
  `AssetTransactionEngineTest` cases in `./gradlew :plugin-abyssus:test --tests '*AssetTransactionEngineTest'`:
  - a derived file is rebuilt forward and back to the expected hashes;
  - a hash mismatch is a `Conflict`;
  - a failing rebuild rolls back the files already written;
  - a patch outside the file's bounds is refused.
- [x] 4.2 Extend `AssetReferenceGuard` to block Undo of a foliage Create while a scene names it in
  `FoliageComponent.assetName`. Verify with an `AssetReferenceGuardTest` case covering the spec scenario "Undo Create
  while used". (Its generic `assetName` scan already collects the component's value; the new cases prove it for a
  saved and an unsaved scene, and that another asset's name does not block.)
- [x] 4.3 Add a test fixture project `src/test/testData/project/Foliage` (the Untitled terrain, `tree`, one model, a
  scene with terrain entity `1`, and a foliage asset with one OBJECT and one DETAIL layer, a mask and a bake), documented
  in `docs/ai/testing.md`. Verify that `FoliageFixtureTest` loads it through `FoliageLoader` with a matching
  fingerprint and that `scripts/check-docs.sh` passes. (The bake was generated from the fixture's own meta, terrain and
  mask; the fixture's README says to regenerate it when those change.)

## 5. Instanced drawing in `lib-gdx-model` and `lib-core`

- [x] 5.1 Check that every fixture model's `VertexBufferObject` and `IndexBufferObject` keep CPU-side data that an
  instanced copy can read. If one has none, switch to the prepared `ModelData` fallback from the design's risks. Verify
  with `FoliageMeshSourceTest` in `:lib-core:test`, reading back vertices and indices for each `Untitled` model. (All
  four MODEL assets keep their geometry on the CPU and an instanced copy reads it back byte for byte, so the `ModelData`
  fallback is not needed. The test builds a `Mesh`, so it is a GL test: it ran with
  `./gradlew :lib-core:test --tests '*FoliageMeshSourceTest' -Dabyssus.glTests=true` and is skipped without the flag.)
- [x] 5.2 Add `instancedFlag` variants of `DefaultShader`, `PbrShader` and `ModelDepthShader`, which take the world
  matrix from 4 `vec4` instance attributes, and have the shader providers (including `FogShaderProvider`) choose them
  for instanced meshes. Verify:
  - the existing shader compile tests and a new `InstancedShaderGlTest` (one instanced copy draws the same pixels as
    the model drawn as a `ModelInstance`, in both default and PBR, within 1/255) pass with
    `./gradlew :lib-gdx-model:test -Dabyssus.glTests=true`;
  - without the flag, `./gradlew :lib-gdx-model:test` still passes.
  (Done. Two deviations from the task text: the module is `:lib-gdx`, not the non-existent `:lib-gdx-model`, so the
  commands are `./gradlew :lib-gdx:test -Dabyssus.glTests=true` — 55 tests, 0 skipped — and `./gradlew :lib-gdx:test`
  — 55 tests, 12 skipped, the GL ones. And `FogShaderProvider` needs no instanced variant: it is libGDX g3d's
  `DefaultShaderProvider` and draws only the editor grid, whose `Renderable`s are g3d types; the repo's instanced
  `Mesh` cannot appear in a g3d renderable at all, so the foliage renderables go through the repo's
  `DefaultShaderProvider`/`PbrShader`/`ModelDepthShader`, which do choose the variants.
  `InstancedShaderGlTest` compares an instanced draw against one renderable per instance, in the color pass for default
  and PBR and in the depth pass, and checks that the provider hands each mesh the matching variant; it also pinned down
  a real bug: `Mesh.unbind` must release the instance attributes before `VertexBufferObjectWithVAO.unbind`, or the
  `glDisableVertexAttribArray` calls land with VAO 0 bound and raise `GL_INVALID_OPERATION` on a core profile.)
- [x] 5.3 Add `FoliageDrawable`:
  - instanced meshes per layer model;
  - per-chunk matrices: entity transform × terrain height × tilt by `alignToNormal` × yaw × scale;
  - frustum and DETAIL distance culling;
  - an instance buffer that is rebuilt only when the visible set changes;
  - animated models in their bind pose.

  Verify with the pure parts in `FoliageChunkMatricesTest` (a copy at terrain-local (10, 20) on the `Untitled` terrain
  under entity `1`'s transform has its origin on `heightAt(10, 20)` plus the entity offset; alignment 0 is upright) and
  `FoliageCullingTest` (chunks beyond a draw distance of 80 are dropped for DETAIL and kept for OBJECT), and the GL part
  with `FoliageDrawableGlTest` under `-Dabyssus.glTests=true`.
  (Done. The instanced meshes are one per **node part** of a layer's model — the shared model mesh cannot change its
  vertex layout, and one mesh per part keeps a multi-material model drawn with its own materials — while the instance
  buffer of a model folder holds as many copies as the bake gives it, so a smaller bake reuses the meshes (a grown
  capacity rebuilds them). A copy's matrix goes into the four instance attributes and its `Renderable.worldTransform`
  stays at the identity; a skinned part carries the model's rest-pose bones, so an animated model draws in its bind
  pose and its node transform travels in the instance matrix instead. Culling works per chunk box (grown by the
  largest model reach times the largest scale, so a copy never leaves its own box) and the buffers are refilled when
  the visible set, the bake, the terrain, the entity transform or (a DETAIL layer being distance culled) the camera
  changes; `update` counts the fills in `rebuilds` and the drawn copies in `drawnCopies`.
  `./gradlew :lib-core:test --tests '*FoliageChunkMatricesTest' --tests '*FoliageCullingTest'` passes (9 tests), and
  `./gradlew :lib-core:test --tests '*FoliageDrawableGlTest' -Dabyssus.glTests=true` passes (1 test: the drawn copies
  match an independent culling computation, a repeated frame and an OBJECT-only foliage's tiny camera move refill
  nothing, a swapped bake and a terrain handed over as null change what is drawn, every part is an instanced copy of
  the model's own vertices, and a `ModelBatch` pass over them leaves `GL_NO_ERROR`); the same class is skipped without
  the flag and the whole `:lib-core:test` stays green.)

## 6. Scene view integration in `plugin-abyssus`

- [x] 6.1 Add `SceneFoliage` beside `SceneTerrains`. It draws `FoliagePlacement`s from `ViewAssets`, prefers a
  `FoliageDrafts` draft, generates stale bakes on the pool, and logs a wrong or missing terrain, a missing model or a
  corrupt bake once per revision. Verify with `SceneFoliageTest` (headless, with fake asset views):
  - each scenario of "Foliage failures are isolated" logs and skips as specified;
  - removing the component removes the drawn foliage;
  - a new draft revision re-scatters only the dirty chunks.
  Verified: `./gradlew :plugin-abyssus:test --tests '*SceneFoliageTest'` — 9 tests green (wrong terrain, unreadable
  terrain, missing model with a second readable layer, missing folder, still-loading silent, truncated bake
  regenerated byte-equal to the committed bake, component removal, draft revision re-scatters only its dirty chunks);
  `./gradlew :lib-core-editor:test --tests '*FoliageBakeMergeTest'` — 6 tests green (patch/chunk/grid merge rules the
  draft path relies on). `SceneRenderer` wires it: `foliage.update/draw/abandon/dispose` around the terrain pass.
- [x] 6.2 Wire foliage into `SceneShadows`: OBJECT layers go to the depth pass, DETAIL layers only to the lit pass.
  Verify with `SceneShadowsTest`, which asserts which renderables reach the caster list. The visual result is manual
  check 8.4.
  Verified: `./gradlew :plugin-abyssus:test -Dabyssus.glTests=true --tests '*SceneShadowsTest'` — 3 tests green: a
  model's own parts and the OBJECT layer's instanced parts share one caster list while no DETAIL part reaches it
  (every foliage caster carries the one box `SceneFoliage` hands over, which covers `FoliageDrawable.objectBounds`);
  a DETAIL-only foliage draws its copies but gives the list nothing; a source with no box of drawn copies gives it
  nothing. Supporting changes: `FoliageDrawable` keys its instance buffers by (folder, layer kind) — one folder
  standing in both layers still gets a set per kind, so a kind can be drawn alone — and keeps `objectBounds`, the
  world box it grows copy by copy while filling the OBJECT layers'; `SceneFoliage` implements the new
  `FoliageCastSource`, and `SceneShadows.render` collects its casters through the extracted `shadowCasters(...)`.
  `./gradlew :lib-core:test -Dabyssus.glTests=true --tests '*FoliageDrawableGlTest'` — 3 tests green (the kind-keyed
  buffers, the box of the drawn OBJECT copies, and the pre-existing draw test unchanged).
  Whole suites: `:plugin-abyssus:test -Dabyssus.glTests=true` 697 tests with only the 7 `SceneRenderGlTest` failures
  and `:lib-core:test -Dabyssus.glTests=true` 271 tests with only the 3 `AssetLoadingGlTest` failures — both sets
  fail identically on a pristine `HEAD` worktree, so they are outside this change.
- [x] 6.3 Have `RaySceneSnapshot` skip foliage and `RayControl` show the "foliage is not ray traced" note. Verify with a
  `RaySceneSnapshotTest` case (a scene with foliage gives the same snapshot as without) and a `RayControlTest` case for
  the note text from `AbyssusBundle`.
  Verified: `./gradlew :lib-core-editor:test --tests '*RaySceneSnapshotTest'` — 13 tests green, the new
  `a scene with foliage gives the same ray snapshot as without` asserting that a `FoliagePlacement` in the content
  changes neither `raySceneDiff` (assets, topology, transforms, materials, lights, environment), nor the mesh and
  instance ids; `RaySceneSnapshots.capture` keeps converting only `content.models` and `content.terrains` (its comment
  already says foliage is deliberately absent) and `RaySceneAssets.update` leases only models, terrains and the sky, so
  a foliage asset is never requested. `./gradlew :plugin-abyssus:test --tests '*RayControlTest'` — 3 tests green: the
  Scene view's `RayControl.foliageNote` is `AbyssusBundle.message("propertiesSceneRayFoliage")` while its scene shows
  foliage and null when it shows none, the Properties panel's Ray Tracing section shows that note in the new
  `ray-tracing-foliage` label, and shows nothing with no foliage. Supporting changes: `RayControl` gained
  `foliageNote` (null by default), `SceneViewPanel` reports it from the content it draws and tells its listeners when it
  changes (`refreshRay` now tracks the foliage along with the mode), `SceneRayControls.foliageNote(file)` hands it to
  `SceneDetailsView`, and the note text is the new `propertiesSceneRayFoliage` bundle key. Neighbouring suites stay
  green: `SceneRaySwitchTest` 11, `SceneViewPanelTest` 6, `SceneViewPanelRayTest` 7, `RayRenderLifecycleTest` 10,
  `RayViewFeedTest` 11, `RayDeviceLossTest` 3.
- [x] 6.4 Confirm that picking and drops ignore copies; no `BoxTarget`s are added. Verify with a `SnapshotSceneQueries`
  test case: a ray through a foliage copy on entity `1` picks entity `1`.
  Verified: `./gradlew :lib-core-editor:test --tests '*SnapshotSceneQueriesTest'` — 8 tests green, the new
  `aRayThroughAFoliageCopyPicksTheTerrainItGrowsOn` driving the centre ray of a camera looking down onto a flat
  100-unit terrain `1` that carries a `FoliagePlacement`: the click passes where copies stand and picks `1`, and
  `SceneMarkers().targets(content)` stays empty, so foliage contributes no `BoxTarget`. Nothing else can add one: the
  drawn boxes come only from `models.drawn` (`SceneRenderer.publishSnapshot`), the markers only from cameras and
  lights, and a foliage copy rides its terrain entity, which is picked as terrain.
- [x] 6.5 Update `sceneview/README.md` and `docs/ai/architecture.md` (data flow and threads for foliage). Verify that
  `scripts/check-docs.sh` passes.
  Verified: `./scripts/check-docs.sh` → "217 path(s) OK in 6 file(s)", exit 0; the sceneview README checked
  explicitly as well (`scripts/check-docs.sh projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md`)
  also passes. Added to the sceneview README: a `SceneFoliage` / `FoliageAssets` piece row, foliage in the `shadows`
  row and in the shadow chapter (OBJECT layers cast with the models and terrain, DETAIL only receives), and a
  **Terrain foliage** chapter covering the placement path (`FoliageComponent` → `FoliagePlacement`), the
  `FoliageLoader` prepare/build split and its dependencies, what `SceneFoliage.update` / `draw` do per frame, the
  off-thread one-at-a-time generation and its once-per-revision logging, `FoliageDrafts`, and the three exclusions
  (no pick targets, OBJECT-only shadow casters, no ray conversion with the `foliageNote` in Properties). Added to
  `docs/ai/architecture.md`: step 6 of "A scene file to the scene view", a foliage sentence in "Clicks, drags and
  writes", and a **Foliage** bullet under Threading.

## 7. Authoring UI in `plugin-abyssus`

- [x] 7.1 Add New Foliage... on the Assets node (disabled without a terrain). It shows a dialog with the terrain, the
  folder name and the mask resolution, then runs the `NewFoliageFiles` plan through `AssetFileCommand`, selects the
  asset and refreshes the tree. Verify with `NewFoliageActionTest`: Create and Undo on a temporary copy of the Foliage
  fixture, and that the action is disabled for a project without a terrain.
  Verified: `./gradlew :plugin-abyssus:test --tests '*NewFoliageActionTest'` → 8 tests, 0 failures (and
  `--tests '*PluginBundleTitlesTest' --tests '*NewTerrainActionTest' --tests '*ImportModelTest' --tests '*AssetTransactionEngineTest'`
  → 49 tests, 0 failures). `NewFoliageAction` (`projectView`) is an `AnAction` on the Assets node, gated by
  `foliageTerrains(projectDir, json)` (a TERRAIN `meta.json` whose heights file exists) so it is hidden when the
  project has no readable terrain; `NewFoliageDialog` is a `FormBuilder` dialog over a terrain combo, the folder name
  (default `foliage_<terrain folder>`, followed when the terrain changes and the name is still the suggested one) and
  the mask resolution (default 512), with OK enabled only while `FoliageAssetWriter.check` returns null and the reason
  shown in a label. `createFoliage` re-reads the `.abss` (`requireSupported`), re-checks the name and the resolution,
  loads the terrain heights, runs `FoliageAssetWriter.plan`, stages `meta.json` and `foliage.data` in one
  `AssetTransaction` (created dirs + an `AssetReferenceGuard` undo guard) and executes it through `AssetFileCommand`,
  then selects the asset. Tests: `Create for the fixture terrain` on a copy of `Untitled` (native meta, fresh uuid,
  `additional.terrain`, `maskResolution` 512, no `layers`, an empty non-stale bake with `copyCount` 0 and
  `dependencies = {terrain}`, no `.mask` files, the scene and `.abss` byte-identical, listed as unused); Create + Undo +
  Redo on a copy of the `Foliage` fixture (Undo removes `foliage_lake`, Redo restores the same bytes, the existing
  `foliage_meadow` stays); name taken / resolution 8 / 4096 each report their reason and write nothing; an unreadable
  terrain is refused; the action is offered with a terrain and not without one (nor when `terrain.data` is missing);
  and the dialog's defaults and OK enablement.
- [x] 7.2 Add the foliage properties section, covering:
  - the approved disk-cache undo fallback for stale/corrupt prior bakes, with hash verification and cleanup;
  - the layer list (add, remove, reorder);
  - the model picker (MODEL assets only, with weights);
  - the numeric fields with reasons beside them;
  - the per-layer and total copy counts, with the limit notice;
  - the stale notice with Re-bake;
  - Apply and Cancel through `FoliageDrafts` and `AssetFileCommand` (meta edits, masks, derived bake).

  Verify with `FoliagePanelTest`:
  - Apply writes `meta.json` and a `foliage.data` matching the preview;
  - Undo restores both;
  - selecting another asset discards the draft;
  - an unsupported meta shows the reason and no editors.
- [x] 7.3 Add Add Foliage... on terrain entities, in the tree context menu and the Scene view toolbar. It lists the
  foliage assets of the entity's terrain and writes through `editSceneJson`. Verify with `AddFoliageActionTest` against
  a temporary copy of the Foliage fixture, covering the spec scenarios "Add to the fixture terrain", "Replace",
  "Nothing to add" and "Not a terrain", plus Undo.
- [x] 7.4 Add Paint Foliage in the Scene view toolbar. It needs the brush strip (layer, radius, strength, Paint/Erase),
  the cursor circle drawn through `LineBatch`, and stroke commits through `AssetFileCommand`. Each commit carries the
  mask patch and the derived bake, joins the undo stacks of the scene file and the foliage meta, and refuses strokes
  over the limit. Verify:
  - `FoliageStrokeCommandTest`: a stroke writes a mask patch; Undo through `UndoManager` in the scene file's context
    restores the mask and the bake hashes; Redo reapplies them; a stroke that changes nothing adds no undo step; a mask
    replaced on disk makes Undo refuse.
  - The interactive parts are manual check 8.3.
- [x] 7.5 Add the foliage asset icon and use it in the project view. Have unused-asset marking follow foliage, from the
  scene to the foliage asset to its terrain and models. Verify with `ProjectAssetUsageTest` cases covering the spec
  scenarios "Foliage used through a terrain entity", "Model used only through foliage" and "Foliage nothing shows", and
  an `AbyssusViewTest` case asserting that the foliage icon differs from the terrain and model icons.
- [x] 7.6 Put every new user-facing string in `AbyssusBundle.properties` or `AbyssusEditorBundle.properties`. Verify
  that `PluginBundleTitlesTest` passes and that `grep` finds no string literals in the new Swing code.
- [x] 7.7 Describe New Foliage, Add Foliage and Paint Foliage in the user section of `README.md`, keeping the
  `<!-- Plugin description -->` markers. Verify that `./gradlew :plugin-abyssus:patchPluginXml` and
  `scripts/check-docs.sh` pass.

## 8. Manual checks in `runIde` (on a copy of Untitled)

- [ ] 8.1 Create foliage for the terrain, add an OBJECT layer of `tree` and a DETAIL layer, and Add Foliage to entity
  `1`. Check that copies stand on the terrain and that moving entity `1` with the gizmo moves them during the drag.
- [ ] 8.2 Change the DETAIL draw distance and density. Check that the preview updates at once, that Apply makes it
  stick, and that Undo from the panel restores it.
- [ ] 8.3 Paint and erase a meadow:
  - copies follow the stroke live;
  - Shift erases;
  - Esc mid-stroke cancels;
  - right-drag and the wheel still navigate;
  - Ctrl+Z in the Scene view undoes one stroke;
  - a second Scene view of the same scene updates too.
- [ ] 8.4 With a directional light, check that the trees cast shadows on the terrain and on the grass, and that the
  grass casts none.
- [ ] 8.5 Regenerate the terrain. Check that the copies re-seat on the new surface and that the panel says the bake is
  out of date. Re-bake, and check that the notice clears.
- [ ] 8.6 Paint up to about 1,000,000 copies, then orbit and click. Check that the view stays responsive, that clicking
  a tree selects the terrain, and that a stroke beyond the limit is refused with a notice.
- [ ] 8.7 Turn on ray tracing and check that the "foliage is not ray traced" note shows.

## 9. Docs and integration

- [x] 9.1 Correct the stale module paths in `AGENTS.md`, `docs/ai/*.md` and the package READMEs (`editor-core/` to
  `projects/lib-core-editor/`, `core/` to `projects/lib-core/`, `src/main/kotlin/net/nevinsky/abyssus/` to
  `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/`, the Gradle task paths, and so on). Add the
  foliage terms to `docs/ai/glossary.md`. Verify that `scripts/check-docs.sh` passes and that every command in the
  `AGENTS.md` table runs or fails only for reasons outside this change.
- [x] 9.2 Run `./gradlew check` and `scripts/check-docs.sh`. Verify that both pass, including `checkNoSingletons`,
  `checkNoRunCatching`, `checkPackageCycles` and `NoPlatformClasspathTest`. Report any failure outside the change
  instead of fixing it silently.

## Workflow follow-up

Verification on 2026-10-10:
- `./gradlew check --continue --console=plain` passed: 1,926 test cases, zero failures, 214 optional tests skipped.
  This includes 10 stroke-command tests, three paint-control tests, the bundle-title test, and the overlapping-terrain
  picking regression. A clean plugin rebuild removed stale test bytecode after the toolbar constructor changed.
- `scripts/check-docs.sh` passed (221 paths in six files); the module/package READMEs and root README also passed
  an explicit check (108 paths in ten files).
- `:lib-raytracing:verifyNativePackaging` and `:plugin-abyssus:patchPluginXml` passed; `check` also built the plugin zip.
- Documented IDE/game launch tasks were validated with `--dry-run`; their interactive behavior remains in 8.1–8.7.
  Those seven checks are open because no sandbox IDE mouse/visual verification was performed.

- Archive with `/openspec-archive-change` after review. This syncs the three new capabilities and the deltas to
  `abyssus-project-assets`, `abyssus-project-view` and `scene-shadows` into `openspec/specs/`.
- Then explore and propose `add-foliage-runtime`: a `FoliageComponent` codec in `lib-runtime`, drawing in Control Line
  with `FoliageDrawable`, and static colliders for OBJECT layers in `lib-physics`.
