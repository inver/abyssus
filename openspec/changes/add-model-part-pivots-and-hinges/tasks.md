# Tasks

## 1. Prerequisites

- [x] 1.1 Confirm `add-entity-hierarchy-and-dummies` and `add-scene-model-mesh-selection` are archived (`openspec list`
  shows neither in progress). Read the archived `scene-entity-hierarchy` requirement "A child can sit on a node of its
  parent's model" and, if it says the rest transform comes from the model file, amend that spec so the rest transform
  includes the node's pivot; verify with `openspec validate add-model-part-pivots-and-hinges --strict`.

## 2. Asset data and re-pivot (lib-core)

- [x] 2.1 Add `ModelPartMeta`, `PartPivot`, `PartHinge` and `ModelMeta.parts` (nullable) per design D1; verify with a new
  `ModelMetaPartsTest` in lib-core: binding the trainer meta with `parts` reads the values, printing a meta without
  `parts` writes no `parts` key, and a meta of `Untitled`'s `model_900f6f61-...` reads `parts == null`
  (`./gradlew :lib-core:test --tests '*ModelMetaPartsTest'`).
- [x] 2.2 Implement `ModelPivots` (design D2) with its refusal checks, returning the applied and refused entries; verify
  with `ModelPivotsTest` on `model_trainer`: (a) with a pivot rotated 90 deg about Z at (0,0,-0.02) on `LeftFlap`,
  every vertex of `LeftFlap` and the world origin of each node keep their world position within 1e-5 and normals keep
  their world direction; (b) the node's new world origin and X axis are as in the spec scenario; (c) unknown node, a
  node with scale (1,2,1), a shared mesh, an animated node each produce one refusal and leave the node unchanged; (d)
  child nodes of a re-pivoted node keep their world transforms.
- [x] 2.3 Call `ModelPivots` in `ModelLoader.read` before `selectMesh`, logging one warning per refusal through the
  loader's `Logger`; verify with `ModelLoaderPivotTest`: a `meshId` variant of a re-pivoted model has the same pivot,
  and a refusal is logged once (`./gradlew :lib-core:test --tests '*ModelLoaderPivotTest'`).
- [x] 2.4 Check that `ModelRaySnapshotLoader` gets every model through `ModelPreparationTap` and has no Assimp path that
  skips `ModelLoader.read`; if one exists, route it through the loader. Verify with a `ModelRaySnapshotLoader` test that
  a re-pivoted node's snapshot node transform equals the raster one.
- [x] 2.5 Document `parts` (fields, frame, omission, refusals) in `docs/ai/file-formats.md`, the terms part / pivot /
  hinge in `docs/ai/glossary.md`, and the re-pivot step in `projects/lib-core/README.md`; verify with
  `scripts/check-docs.sh`.

## 3. Parts, picking and frames (lib-core-editor)

- [x] 3.1 Extend `ModelMeshLister` with `parts(asset)` (pruned node tree, own bounds, pivot status and reason),
  `fileFrame(asset, node)` and part triangles, cached and dropped by `invalidate` (design D3); verify with
  `ModelPartsListerTest`: trainer tree as in the `model-parts` scenario, Piper has one root with 192 children including
  `flap_L01_Cube.012`, a `meshId` variant lists only its nodes and ancestors, a refused pivot reports its reason.
- [x] 3.2 Implement `PartPicker` (design D4); verify with `PartPickerTest`: a ray through the trainer's left flap hits
  `LeftFlap`, a ray through the box gap misses, a disabled entity is skipped, the nearest of two hits wins.
- [x] 3.3 Implement `PivotFrames` and `PivotSnaps` (design D4); verify with `PivotSnapsTest`: an edge snap on a known
  triangle gives the closest edge point and X along the edge signed toward the current X with minimal turn; a face snap
  gives Y = normal and X projected; `toFilePivot` round-trips a frame within 1e-5.
- [x] 3.4 Invalidate the lister's answer for an asset on the same meta/asset change signal that reloads models in
  `ViewAssets` and the ray assets; verify with a plugin `ViewAssetsTest` case that a changed `parts` in unsaved meta
  text gives new `parts(asset)` results.
- [x] 3.5 Implement `ModelPartEdits` (set/clear pivot, set/clear hinge, normalize empty entries and map) over
  `AssetMetaTextEditor`, and `HingeLimits` validation; verify with `ModelPartEditsTest`: writing then clearing a pivot
  returns the original meta text, a hinge with `min` 10 is refused, an entry with neither is removed, and
  `formatVersion` stays 1.
- [x] 3.6 Implement `CompensateBoundChildren` as a `SceneEdit` (design D7); verify with `CompensateBoundChildrenTest`:
  a dummy bound to `LeftFlap` under `Trainer` in a copy of `Field.scene` keeps its world pose after the pivot change,
  and an entity bound to another node or another model is untouched.
- [x] 3.7 Add messages (refusal reasons, hinge errors) to `AbyssusEditorBundle.properties` and update
  `projects/lib-core-editor/README.md` (parts, picking, pivots, hinges); verify with `./gradlew :lib-core-editor:test`
  and `scripts/check-docs.sh`.

## 4. Plugin: tree, selection, outline

- [x] 4.1 Add the `Parts` row and nested part rows with pivot/hinge marks under model entity rows (spec
  `abyssus-project-view`); verify with a `projectView` tree test on `Untitled`'s `Main Scene`: `Model 6` has a `Parts`
  row reading 193 and `flap_L01_Cube.012` under `piper_pa18.obj`, placed after child entities and before components.
- [ ] 4.2 Extend entity selection with an optional part, synced tree <-> view, and add Alt+click part picking in the
  input handler; verify with an `EntitySelectionTest` case (part row selection selects entity and part; a plain entity
  selection clears the part) and runIde check 7.1 (1)-(2).
- [ ] 4.3 Draw the selected part's outline (own bounds posed with the node, separate color) in `SceneRenderer`; verify
  with runIde check 7.1 (3).
- [x] 4.4 Update the `projectView` and `sceneview` READMEs and add tree/label strings to `AbyssusBundle.properties`;
  verify with `scripts/check-docs.sh` and `PluginBundleTitlesTest`.

## 5. Plugin: Edit Pivot and Properties

- [x] 5.1 Add `editAssetMetaAndScene` beside `editTextDocument` (design D7) and list it in AGENTS.md's write rule; verify
  with a `filetype` test on temp copies: one command changes both documents, one Undo restores both, and nothing is
  written when either transform refuses.
- [ ] 5.2 Add Edit Pivot and the snap choice to the toolbar and `SceneViewState`, the stats strip label, the gizmo target
  switch and the write on release / snap click (design D5); verify with a `sceneview` toolbar test (disabled without a
  part, off on open, not written) and runIde check 7.1 (4)-(6).
- [x] 5.3 Add the part section to Properties (breadcrumb, asset, pivot numbers + Reset or refusal reason, hinge Add /
  min / max / Remove) following meta changes and Undo; verify with a `properties` model test on `Model 6`'s
  `flap_L01_Cube.012` (shows pivot 0, Add Hinge) and on a trainer copy (Add Hinge writes -30/30; Undo shows Add Hinge).

## 6. Plugin: hinge preview

- [ ] 6.1 Add the Preview slider and the per-frame node override after the animation update (design D6), resetting on
  part change, hinge change and view close; feed the overridden node matrix to the pose resolver for bound children.
  Verify with a headless test of the override math (rest rotation x axisAngle(X, a)) and runIde check 7.1 (7)-(8).
- [x] 6.2 Document preview and Edit Pivot in the `sceneview` README and `docs/ai/architecture.md`; verify with
  `scripts/check-docs.sh`.

## 7. Trainer data and integration

- [ ] 7.1 runIde check (manual, on a copy of the Control Line project, never `Untitled`): (1) Alt+click the trainer's
  left flap in `Field.scene`: `LeftFlap` selected, revealed in the tree; (2) plain click elsewhere clears it; (3) the
  flap outline follows a gizmo move of `Trainer`; (4) Edit Pivot on `LeftFlap`, drag Z: flap does not move, meta
  written, Undo restores; (5) Edge snap on the flap leading edge: pivot on the edge with X along it; (6) Face snap
  missing the flap writes nothing; (7) add hinges to both flaps, Preview 20: flap swings about the leading edge, files
  unchanged; (8) select `RightFlap`: left flap back at rest.
- [ ] 7.2 Author `parts.LeftFlap` and `parts.RightFlap` (pivot on the hinge line, X along it; hinge -35/35) in
  `projects/app-game-control-line/project/ControlLine/assets/model_trainer/meta.json` with the editor (on a copy, then
  copy the meta back); verify `./gradlew :app-game-control-line:test` passes and the game draws the trainer unchanged
  (`./gradlew :app-game-control-line:run`, visual check).
- [ ] 7.3 Run `./gradlew check` and `scripts/check-docs.sh`; both pass. Report any failure outside this change instead
  of fixing it silently.
