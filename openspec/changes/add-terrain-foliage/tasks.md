# Tasks

Commands use the real Gradle paths (`:lib-core`, `:lib-core-editor`, `:lib-gdx-model`, `:plugin-abyssus`). GL tests
need `-Dabyssus.glTests=true` on a machine with a display. Manual checks run in `./gradlew runIde` on a copy of the
`Untitled` project, never on the fixture itself.

## 1. Foliage files in `lib-core` (headless)

- [ ] 1.1 Add `MetaType.FOLIAGE` and bind it in `AssetMetaBinder` to `FoliageMeta`, which holds `terrain`, `dataFile`,
  `maskResolution` and the layers with their documented defaults. Verify with `AssetMetaLoaderTest` cases in
  `./gradlew :lib-core:test`:
  - a foliage meta with every field binds;
  - omitted members take their defaults;
  - an unknown layer member does not fail binding.
- [ ] 1.2 Add `FoliageMaskFile`: read and write `maskResolution²` bytes in z-major order, where a missing file means 255
  everywhere and a wrong length is an error naming the file. Verify with `FoliageMaskFileTest`: a round trip, a missing
  file, and a truncated file.
- [ ] 1.3 Add `FoliageDataFile`, which reads and writes the `ABFO` v1 layout from design decision 3. Verify with
  `FoliageDataFileTest`:
  - a round trip of two layers over 3 × 3 chunks gives the same bytes;
  - another magic number, version 2 or a truncated file all read as "stale" rather than throwing;
  - the chunk size rule `max(32, size/64)` holds for sizes 100 and 1600.
- [ ] 1.4 Add `FoliageLoader`, registered in the plugin's `AssetLoading` and in `CompositeAssetLoader` wiring. Its
  prepare step runs off the GL thread: it validates the meta with `AbyssusDocumentFormat`, then reads the masks and the
  bake. It declares the terrain and the layer models as dependencies. Verify with `FoliageLoaderTest` in `:lib-core:test`:
  - the dependencies list the terrain and both models of a two-model layer;
  - `formatVersion` 2 is refused with a reason;
  - a missing bake is prepared as stale, not failed.
- [ ] 1.5 Describe the foliage asset (meta fields, mask and bake layout) in `docs/ai/file-formats.md` under "Asset
  `meta.json`", and add `assets/foliage` to the module README. Verify that `scripts/check-docs.sh` passes.

## 2. Settings, generation and fingerprint in `lib-core-editor` (headless)

- [ ] 2.1 Add `FoliageSettings`, read from a foliage meta `JsonNode`, with validation that returns
  `AbyssusEditorBundle` reasons: density ≥ 0, scale min ≤ max, alignment in [0, 1], slope in [0, 90], min height ≤ max
  height, weight > 0, unique layer ids, and models that are MODEL assets. Verify with `FoliageSettingsTest` in
  `./gradlew :lib-core-editor:test`, covering each rejection in the spec scenario "Invalid value" and a valid layer of
  `tree`.
- [ ] 2.2 Add `FoliageScatter`, the jittered grid with per-cell SplitMix64 streams from design decision 2: mask, height
  and slope rules, models by weight, yaw and scale. Verify with `FoliageScatterTest`:
  - equal inputs give byte-equal bakes;
  - changing a seed changes only that layer;
  - no pair of copies is closer than `0.2 / sqrt(density)`;
  - a slope limit of 20 and a height range of 5 to 40 hold for every copy, checked on the `Untitled` terrain data;
  - an empty mask gives 0 copies;
  - a mask of 128 gives 50% ± 3% of a full mask's count;
  - weights 3:1 give 75% ± 3%.
- [ ] 2.3 Add partial re-scatter of a set of chunks. Verify with a `FoliageScatterTest` case: re-scattering the chunks
  under a random rectangle and merging them gives the full bake's bytes, over 20 random rectangles.
- [ ] 2.4 Add the copy limit (1,000,000 in total) and the 16,000,000-candidate guard as a result with a reason. Verify
  with a `FoliageScatterTest` case: density 1 on the 1600-unit terrain reports about 2,560,000 and is refused without
  generating every copy.
- [ ] 2.5 Add `FoliageFingerprint` over the inputs listed in design decision 3. Verify with `FoliageFingerprintTest`:
  - changing a height byte, a mask byte, a seed, a weight or the terrain size changes it;
  - changing `alignToNormal` or `drawDistance` does not.
- [ ] 2.6 Add `FoliageMetaEdits`, which turns a settings diff into `DocumentTextEditor` edits. Verify with
  `FoliageMetaEditsTest`:
  - changing a density keeps `"note": "north slope"`, the key order and the number text of untouched values (`60.0`
    stays `60.0`);
  - adding a layer appends it;
  - removing one deletes only its object;
  - an equal edit produces no change.
- [ ] 2.7 Add `NewFoliageFiles`, which plans a new asset: the meta text with the markers first, a fresh uuid,
  `maskResolution`, no layers and an empty bake. It validates the folder name and that the resolution is in 16..2048.
  Verify with `NewFoliageFilesTest`, covering the spec scenarios "Create for the fixture terrain", "Name taken" and
  "Resolution out of range".
- [ ] 2.8 Add `FoliagePlacement` to `SceneContent`, for terrain entities with a string `FoliageComponent.assetName`.
  Verify with `SceneContentTest` cases:
  - entity `1` of a `parseScene` copy of `Main Scene` with `FoliageComponent` gives one placement carrying its
    transform;
  - a model entity with the component gives none;
  - a non-string `assetName` gives none.
- [ ] 2.9 Describe the `foliage/` package in `lib-core-editor`'s README. Verify that `scripts/check-docs.sh` passes.

## 3. Brush math and drafts in `lib-core-editor` (headless)

- [ ] 3.1 Add `FoliageBrush`: stamps along a path at a quarter-radius spacing, the smoothstep falloff, Paint, Erase,
  clamping, terrain-local mapping through an inverse entity matrix, and the dirty rectangle. Verify with
  `FoliageBrushTest`:
  - a radius 10, strength 1 stroke on an empty mask raises texels only within 10 units of the path;
  - Erase on 0 changes nothing and reports an empty dirty rectangle;
  - a rotated, scaled entity paints under the world-space circle.
- [ ] 3.2 Add `FoliageDraft`: the draft settings and masks, the revision, the dirty chunks derived from a brush
  rectangle, and revert to the state at the press. Verify with `FoliageDraftTest`: a stamp marks only the chunks it
  overlaps, and revert restores the masks byte for byte.
- [ ] 3.3 Add the paint mode to `SceneViewState` and `Gesture.Painting` to `SceneInteraction`:
  - a left press on the selected terrain starts a stroke;
  - other buttons and the wheel navigate;
  - Esc cancels;
  - selecting another entity leaves the mode;
  - presses off the terrain do nothing.

  Verify with `SceneInteractionPaintTest`, using fake `SceneQueries` as in the existing gizmo drag tests, with one
  case per spec scenario of "Paint Foliage mode" and "Painting the density mask".

## 4. Undoable file operations in `plugin-abyssus` (headless)

- [ ] 4.1 Add derived files (`DerivedFile` with before/after SHA-256 and `rebuild`) and mask patches (a dirty rectangle
  checked against the file's SHA-256) to `AssetTransaction` / `AssetTransactionEngine`. Verify with
  `AssetTransactionEngineTest` cases in `./gradlew :plugin-abyssus:test --tests '*AssetTransactionEngineTest'`:
  - a derived file is rebuilt forward and back to the expected hashes;
  - a hash mismatch is a `Conflict`;
  - a failing rebuild rolls back the files already written;
  - a patch outside the file's bounds is refused.
- [ ] 4.2 Extend `AssetReferenceGuard` to block Undo of a foliage Create while a scene names it in
  `FoliageComponent.assetName`. Verify with an `AssetReferenceGuardTest` case covering the spec scenario "Undo Create
  while used".
- [ ] 4.3 Add a test fixture project `src/test/testData/project/Foliage` (the Untitled terrain, `tree`, one model, a
  scene with terrain entity `1`, and a foliage asset with one OBJECT and one DETAIL layer, a mask and a bake), documented
  in `docs/ai/testing.md`. Verify that `FoliageFixtureTest` loads it through `FoliageLoader` with a matching
  fingerprint and that `scripts/check-docs.sh` passes.

## 5. Instanced drawing in `lib-gdx-model` and `lib-core`

- [ ] 5.1 Check that every fixture model's `VertexBufferObject` and `IndexBufferObject` keep CPU-side data that an
  instanced copy can read. If one has none, switch to the prepared `ModelData` fallback from the design's risks. Verify
  with `FoliageMeshSourceTest` in `:lib-core:test`, reading back vertices and indices for each `Untitled` model.
- [ ] 5.2 Add `instancedFlag` variants of `DefaultShader`, `PbrShader` and `ModelDepthShader`, which take the world
  matrix from 4 `vec4` instance attributes, and have the shader providers (including `FogShaderProvider`) choose them
  for instanced meshes. Verify:
  - the existing shader compile tests and a new `InstancedShaderGlTest` (one instanced copy draws the same pixels as
    the model drawn as a `ModelInstance`, in both default and PBR, within 1/255) pass with
    `./gradlew :lib-gdx-model:test -Dabyssus.glTests=true`;
  - without the flag, `./gradlew :lib-gdx-model:test` still passes.
- [ ] 5.3 Add `FoliageDrawable`:
  - instanced meshes per layer model;
  - per-chunk matrices: entity transform × terrain height × tilt by `alignToNormal` × yaw × scale;
  - frustum and DETAIL distance culling;
  - an instance buffer that is rebuilt only when the visible set changes;
  - animated models in their bind pose.

  Verify with the pure parts in `FoliageChunkMatricesTest` (a copy at terrain-local (10, 20) on the `Untitled` terrain
  under entity `1`'s transform has its origin on `heightAt(10, 20)` plus the entity offset; alignment 0 is upright) and
  `FoliageCullingTest` (chunks beyond a draw distance of 80 are dropped for DETAIL and kept for OBJECT), and the GL part
  with `FoliageDrawableGlTest` under `-Dabyssus.glTests=true`.

## 6. Scene view integration in `plugin-abyssus`

- [ ] 6.1 Add `SceneFoliage` beside `SceneTerrains`. It draws `FoliagePlacement`s from `ViewAssets`, prefers a
  `FoliageDrafts` draft, generates stale bakes on the pool, and logs a wrong or missing terrain, a missing model or a
  corrupt bake once per revision. Verify with `SceneFoliageTest` (headless, with fake asset views):
  - each scenario of "Foliage failures are isolated" logs and skips as specified;
  - removing the component removes the drawn foliage;
  - a new draft revision re-scatters only the dirty chunks.
- [ ] 6.2 Wire foliage into `SceneShadows`: OBJECT layers go to the depth pass, DETAIL layers only to the lit pass.
  Verify with `SceneShadowsTest`, which asserts which renderables reach the caster list. The visual result is manual
  check 8.4.
- [ ] 6.3 Have `RaySceneSnapshot` skip foliage and `RayControl` show the "foliage is not ray traced" note. Verify with a
  `RaySceneSnapshotTest` case (a scene with foliage gives the same snapshot as without) and a `RayControlTest` case for
  the note text from `AbyssusBundle`.
- [ ] 6.4 Confirm that picking and drops ignore copies; no `BoxTarget`s are added. Verify with a `SnapshotSceneQueries`
  test case: a ray through a foliage copy on entity `1` picks entity `1`.
- [ ] 6.5 Update `sceneview/README.md` and `docs/ai/architecture.md` (data flow and threads for foliage). Verify that
  `scripts/check-docs.sh` passes.

## 7. Authoring UI in `plugin-abyssus`

- [ ] 7.1 Add New Foliage... on the Assets node (disabled without a terrain). It shows a dialog with the terrain, the
  folder name and the mask resolution, then runs the `NewFoliageFiles` plan through `AssetFileCommand`, selects the
  asset and refreshes the tree. Verify with `NewFoliageActionTest`: Create and Undo on a temporary copy of the Foliage
  fixture, and that the action is disabled for a project without a terrain.
- [ ] 7.2 Add the foliage properties section, covering:
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
- [ ] 7.3 Add Add Foliage... on terrain entities, in the tree context menu and the Scene view toolbar. It lists the
  foliage assets of the entity's terrain and writes through `editSceneJson`. Verify with `AddFoliageActionTest` against
  a temporary copy of the Foliage fixture, covering the spec scenarios "Add to the fixture terrain", "Replace",
  "Nothing to add" and "Not a terrain", plus Undo.
- [ ] 7.4 Add Paint Foliage in the Scene view toolbar. It needs the brush strip (layer, radius, strength, Paint/Erase),
  the cursor circle drawn through `LineBatch`, and stroke commits through `AssetFileCommand`. Each commit carries the
  mask patch and the derived bake, joins the undo stacks of the scene file and the foliage meta, and refuses strokes
  over the limit. Verify:
  - `FoliageStrokeCommandTest`: a stroke writes a mask patch; Undo through `UndoManager` in the scene file's context
    restores the mask and the bake hashes; Redo reapplies them; a stroke that changes nothing adds no undo step; a mask
    replaced on disk makes Undo refuse.
  - The interactive parts are manual check 8.3.
- [ ] 7.5 Add the foliage asset icon and use it in the project view. Have unused-asset marking follow foliage, from the
  scene to the foliage asset to its terrain and models. Verify with `ProjectAssetUsageTest` cases covering the spec
  scenarios "Foliage used through a terrain entity", "Model used only through foliage" and "Foliage nothing shows", and
  an `AbyssusViewTest` case asserting that the foliage icon differs from the terrain and model icons.
- [ ] 7.6 Put every new user-facing string in `AbyssusBundle.properties` or `AbyssusEditorBundle.properties`. Verify
  that `PluginBundleTitlesTest` passes and that `grep` finds no string literals in the new Swing code.
- [ ] 7.7 Describe New Foliage, Add Foliage and Paint Foliage in the user section of `README.md`, keeping the
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

- [ ] 9.1 Correct the stale module paths in `AGENTS.md`, `docs/ai/*.md` and the package READMEs (`editor-core/` to
  `projects/lib-core-editor/`, `core/` to `projects/lib-core/`, `src/main/kotlin/net/nevinsky/abyssus/` to
  `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/`, the Gradle task paths, and so on). Add the
  foliage terms to `docs/ai/glossary.md`. Verify that `scripts/check-docs.sh` passes and that every command in the
  `AGENTS.md` table runs or fails only for reasons outside this change.
- [ ] 9.2 Run `./gradlew check` and `scripts/check-docs.sh`. Verify that both pass, including `checkNoSingletons`,
  `checkNoRunCatching`, `checkPackageCycles` and `NoPlatformClasspathTest`. Report any failure outside the change
  instead of fixing it silently.

## Workflow follow-up

- Archive with `/openspec-archive-change` after review. This syncs the three new capabilities and the deltas to
  `abyssus-project-assets`, `abyssus-project-view` and `scene-shadows` into `openspec/specs/`.
- Then explore and propose `add-foliage-runtime`: a `FoliageComponent` codec in `lib-runtime`, drawing in Control Line
  with `FoliageDrawable`, and static colliders for OBJECT layers in `lib-physics`.
