# Tasks

Phases 1, 3, 4, 5 and 7 change no behavior: the existing tests must pass unchanged, apart from moves and renames named
in a task. Single plugin tests: `./gradlew :test --tests '<class>'`. Single `core` tests:
`./gradlew :core:test --tests '<class>'`.

## 1. Rule compliance and trivial duplicates (H2, D1, D3, D9, D11, D14, L2, L5, L6)

- [ ] 1.1 Confirm that `ProcessCanceledException` extends `CancellationException` on since-build 252: a test in
  `CancellationTest` throws a PCE through the `core` `runCatchingKeepingCancellation` and expects it rethrown.
  Then delete `dto/Cancellation.kt` and switch the imports to `net.nevinsky.abyssus.assets`. Keep the PCE
  compatibility test in plugin `CancellationTest` (IntelliJ must not enter `core`'s test classpath).
  Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.dto.CancellationTest'`.
- [ ] 1.2 Replace `runCatching` in `EnabledToggle.kt`, `ComponentActions.kt`, `SceneRenderParams.kt` and
  `SceneJson.kt`, plus the current sites in `SceneViewPanel`, `NewTerrain`, `TerrainPreviewRunner`,
  `AssetReferenceChoices` and `core`'s `AssetMetaEditor`, with `runCatchingKeepingCancellation` (currently eleven
  calls total). Refresh the inventory with `rg -n '\brunCatching\s*\{' src/main/kotlin core/src/main/kotlin`.
  Verify with `SceneJsonTest`, `SceneRenderParamsTest`, `ComponentActionsTest`, `SceneViewPanelRayTest`,
  `AssetReferenceChoicesTest`, the terrain test package, `./gradlew :core:test --tests
  'net.nevinsky.abyssus.assets.edit.AssetMetaEditorTest'`, and task 1.3's build check.
- [ ] 1.3 Add a `checkNoRunCatching` task (pattern `\brunCatching\s*\{` over `src/main/kotlin` and
  `core/src/main/kotlin`) and wire it into `check`. Verify that `./gradlew checkNoRunCatching` passes, and that it
  fails when a `runCatching {` is temporarily added.
- [ ] 1.4 Remove `ProjectReader.sceneFiles` in favor of `ProjectLayout.sceneFiles`. Make `ProjectLayout` use the
  `core` constants directly instead of re-exporting them (L6). Verify with `ProjectAssetsTest` and `UnusedFilterTest`.
- [ ] 1.5 Make `AssetReadResult` sealed (`Ok` / `Failed`) with `AssetReadResult.of(Result)`, and use it in
  `SceneReader` and `ProjectReader`. Update the callers in `AbyssusNodes` and `AssetReadCache`. Verify with
  `ProjectAssetsTest` and `RowTextTest`.
- [ ] 1.6 Add `Throwable.displayMessage()` and use it in the 8 `message ?: javaClass.simpleName` sites. Remove
  `WorldUtils` and keep the `Engine.getFromWorld` extension. Move `textOf` to `dto/` (L5). Make `SkyboxChoice` a plain
  class (L2). Verify with `SkyboxChoicesTest`, `SkyboxChooserDialogTest`, `SystemsTest` and `./gradlew :test`.

## 2. One interpretation of component values (H1; spec `scene-entity-lights`)

- [ ] 2.1 Add `ecs/component/ComponentDefaults.kt` and use it in `LightData`, `CameraComponent`, `LightCodec.write`
  and the `sceneview` placements (replacing `DEFAULT_LIGHT_RANGE` / `DEFAULT_CAMERA_*`). Verify with
  `ComponentCodecsTest` and `ComponentEditorTest`.
- [ ] 2.2 Add `SceneEcsPaths` (`entities`, `components`, `entityName`) and use it in `ComponentEditor`,
  `SceneTransformWriter`, `PanelState`, `LightEntities`, `AddLightAction` and `DtoTree`. Verify with
  `ComponentEditorTest`, `SceneTransformWriterTest`, `EntityPropertiesPanelTest`, `LightEntitiesTest` and
  `RowTextTest`.
- [ ] 2.3 Make `PositionCodec` read `lookAtId` as an integer or text and write it back in the form it was read. Add
  decoded-reference/original-node storage while preserving numeric access for Ashley callers. Add cases to
  `ComponentCodecsTest` for `"lookAtId": 3`, `"lookAtId": "3"`, `"-1"` and `"h"`, and a `ComponentEditorTest`
  case showing that an unrelated position edit preserves each original reference node. Verify both classes with
  `./gradlew :test --tests 'net.nevinsky.abyssus.ecs.ComponentCodecsTest' --tests 'net.nevinsky.abyssus.ecs.ComponentEditorTest'`.
- [ ] 2.4 Add a pure `PlacementMapper` that turns decoded components into placements, and rebuild `SceneContent.of`
  on top of the codecs.
  - Change `SceneContentTest.lightColorAndIntensityMayBeFlat_andPointKindIsRecognised` to expect g 0 / b 0.
  - Add `SceneContentTest` cases for a missing `intensity` (expect 1) and a missing `color` (expect white).
  - Add `SceneEntityParityTest`, which loads test copies of Untitled `Main Scene` with explicit light components
    and the prepared spotlight from the delta scenarios, and asserts that each light, camera and model
    placement equals the values `ComponentEditor.read` returns for the same entity (spec: *Stated values agree*).
  - Preserve all positioned entities and `HANDLE` ids, post-decode light target aiming, camera targets and rotation
    fallback for missing/coincident targets. Verify `SceneContentTest` and `SceneEntityParityTest`, including the
    existing `Lights` fixture cases and a textual `"h"` target case; no GL is needed for these checks.
- [ ] 2.5 Add a test that `SceneTransformWriter` output is byte-identical to before for a move, a rotation and a camera
  move on the Untitled fixture, comparing against stored expected text in `SceneTransformWriterTest` (the `scene-object-transform`
  requirement that a write changes nothing else). Verify that `SceneFileEditorTest` passes, including
  `testMovingAnEntityWritesOnlyItsPositionAndUndoRestoresTheFile` and `testATransformThatChangesNothingIsNotWritten`.
  Also retain `SceneTransformWriterTest`'s `Lights` handle-target edits, textual handle ids, no-op and missing-target
  cases; verify no unrelated light transform or reference node changes.
- [ ] 2.6 Prepare a test copy of `Main Scene` with a `LightComponent` on entity `7` and new spotlight entity `8`
  exactly as the delta scenarios specify. Add a `SceneFileEditorTest` that snapshots the prepared text, opens the view
  and selects both entities, then verifies that text remains byte-identical. Do not modify the shared fixture.
- [ ] 2.7 Document the light defaults in `docs/ai/file-formats.md`, including the visible change for lights without
  `intensity`, and add that visible change to CHANGELOG's Unreleased section. Verify with `scripts/check-docs.sh`
  and review the release note against the light-default delta.
- [ ] 2.8 Run the runIde check, using a copy of `Untitled` (not the fixture itself):
  1. In the temporary copy, add `LightComponent.light` with color r 1, g 0.96, b 0.84, a 1 and intensity 1.2 to
     existing directional entity `7`; create spotlight entity `8` as the delta's *Omitted spotlight values* scenario
     specifies, then open `Main Scene`.
  2. Select `Spot Light 8`; the panel shows range 100, cone 45 and softness 20 percent.
  3. Remove the newly supplied `intensity` from entity `7` in the text tab; the view's light brightness matches intensity 1, and the panel
     shows 1.

## 3. `meta.json` reading and `MetaType` (M4, D6, D7, D10)

- [ ] 3.1 Add `AssetMetaReader` / `MetaDocument` in `core`, parsing once, and use them in `AssetFiles`. Move
  `SKYBOX_FACES` next to `SkyboxAdditional`. Verify with `./gradlew :core:test --tests
  'net.nevinsky.abyssus.assets.files.AssetFilesTest'` plus a new case asserting that one `meta.json` read serves both
  the JSON and the typed lookups.
  Preserve `MetaTextSource` and per-snapshot caching. Add `AssetFilesTest` cases showing that `refreshed` observes
  unsaved metadata, changed UUIDs and added/removed assets, while independent snapshots do not share mutable cache
  state; verify with the same `:core:test` command.
- [ ] 3.2 Make `ProjectAssetListing`, `AssetMeta` and the skybox chooser read through a VFS adapter onto
  `AssetMetaReader`. Change `AssetMeta.Loaded.type` to `MetaType`. Replace `SKYBOX_TYPE` / `PROCEDURAL_SKY_TYPE` /
  `HDR_SKY_TYPE` and the `"SKYBOX"` / `"MODEL"` / `"TERRAIN"` strings with `MetaType`. Verify with
  `ProjectAssetsTest` (the main `abyssus-project-assets` scenarios unchanged), `AssetPropertiesPanelTest`,
  `SkyboxChoicesTest`, `SceneComponentEditsTest` and `NodeIconsTest`.
- [ ] 3.3 Merge `PanelState.thumbnail` / `hdrThumbnail` file lookup into one private helper. Verify with
  `AssetPropertiesPanelTest`.

## 4. `core` loading simplifications (M6, M7, M8)

- [ ] 4.1 Make `AssetCache` take an `AssetLoader`, and simplify `SceneAssets`. Verify with
  `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.loading.AssetCacheTest'`, with the test's fake lambdas
  turned into a fake loader.
- [ ] 4.2 Rework `SkyLoader` / `PreparedSky` so each prepared sky carries its loader, and choose the kind through
  `AssetMetaReader`. Verify with the `core` sky tests (`./gradlew :core:test`).
- [ ] 4.3 Add `TextureUploadQueue` and use it in `PreparedModel` and `PreparedTerrain`. Add a `TextureUploadQueueTest`
  with a fake texture factory: it uploads one image per call, `dispose` releases both the remaining pixmaps and the
  uploaded textures, and it is safe to call twice.
- [ ] 4.4 Add top-level `createFullscreenTriangle()` / `rotationOnlyViewProj` (`core/.../sky/SkyGeometry.kt`) and use them in
  `SkyboxCube`, `ProceduralSky`, `HdrSky`, `HdrEnvironmentBuild` and `LoadingOverlay`. Reformat the `SkyboxCube`
  vertex array and make `CAMERA_HEIGHT` a file-level const. Verify with a `SkyGeometryTest` for the matrix (no GL),
  plus `./gradlew :test -Dabyssus.glTests=true --tests 'net.nevinsky.abyssus.sceneview.SceneRenderGlTest'`.
- [ ] 4.5 Add `JsonFormat` in `core` and use it from `JsonProcessor` and `SceneJson`. Verify with `JsonProcessorTest`,
  `SceneJsonTest` and `SceneEditFormattingTest` (pretty output unchanged).

## 5. Writer, events and dependency injection (M2, M3)

- [ ] 5.1 Move `editSceneJson` to `filetype/SceneDocumentWriter.kt`. Have it publish `AbyssusSceneEdited.TOPIC`
  instead of refreshing the pane, and make `AbyssusProjectViewPane` subscribe and call `updateFromRoot(true)`.
  Verify with `SceneComponentEditsTest`, `SceneTransformEditTest` and `RowActionsTest` (the tree refreshes after a
  toggle), plus a new test that a write publishes exactly one event for the file.
- [ ] 5.2 Pass `JsonProcessor` / `SceneReader` through the constructors of `SceneReader` and `ProjectReader`, an
  `HdrPreviewSource` into `PanelState`, and parameters into `SkyboxChoices` / `SceneComponentEdits`. Remove the default
  `service<AbyssusCore>()` argument from `SceneViewPanel` and inject its optional `RayIntegration`. Also pass the
  asset-field/terrain collaborators into the newer `PanelState` readers. Verify with `rg -n 'service<|getService\('
  src/main/kotlin`: lookups
  appear only in actions, providers, factories and `@Service` constructors. Then run `./gradlew :test`.
- [ ] 5.3 Update the threading, write-path and extension-point sections of `docs/ai/architecture.md` and
  `docs/ai/conventions.md` (the new writer location, the event topic and `checkNoRunCatching`). Verify with
  `scripts/check-docs.sh`.
  Update AGENTS.md's writer path too; after `editSceneJson` moves it must point to `filetype/SceneDocumentWriter.kt`.

## 6. Live updates and the scene document cache (H3; specs `scene-model-rendering`, `scene-light-creation`)

- [ ] 6.1 Add a `SceneDocumentCache` project service keyed by document modification stamp, and use it in
  `canAddLight`, `AddComponentAction.choices` and the `SceneComponentEdits.addLight` validation. Add a
  `SceneDocumentCacheTest`:
  - Repeated reads of an unchanged document parse once.
  - An edit invalidates the entry.
  - A delete or move drops the entry.
  - An invalid text reports unreadable, so Add Light is disabled (spec: `scene-light-creation` *Unreadable scene*).
- [ ] 6.2 Add a pure `ReloadPolicy` with a `ReloadPolicyTest`:
  - A document change is delayed.
  - A VFS change, `AbyssusSceneEdited` or an undo/redo reloads now.
  - A pending delayed reload is dropped when an immediate reload happens.
  - Disposal drops pending work and prevents delivery to a closed editor.
- [ ] 6.3 Wire `ReloadPolicy` into `SceneFileEditor` with a 200 ms `MergingUpdateQueue`. Update
  `testRendersAndUpdatesInPlaceOnUnsavedEdits`, `testAbssEditRefreshesCamera` and `testBadEditDisposesViewThenFixRecreatesIt`
  to flush the queue. Add `testTypingBurstReloadsOnce` and `testInvalidIntermediateTextNeverShowsError`. Verify that
  `testComponentEditsReachTheOpenView` and `testDroppingAnEntityIsOneMoveCommandAndUndoRestoresTheViewAndFile` still
  pass without a flush. All in `SceneFileEditorTest`.
  Add a dispose-with-pending-reload case and verify with
  `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneFileEditorTest'`.
- [ ] 6.4 Run the runIde check on a copy of `Untitled`:
  1. With the scene view and the text tab side by side, type quickly in `Model 0`'s `localPosition.x`; the view
     follows within about 0.3 s and shows no error flash.
  2. A move in the view followed by Undo updates the view at once.
  3. Add Light stays responsive while the text is invalid, and is disabled.

## 7. Tree actions and entity holders (M5, D8, L3, L4)

- [ ] 7.1 Add `AbyssusTreeAction<T>` and port `AddComponentAction`, `RemoveComponentAction` and `AddLightAction` to it.
  `AddLightAction` uses `viewableSceneFile`. Verify with `ComponentActionsTest` and `SceneRowActionTest`.
- [ ] 7.2 Add `PlacedAssets<P, A, E>`. Make `SceneModels` and `SceneTerrains` extend it, and cache terrain local bounds
  on `TerrainEntity`. Add `SceneSkybox.abandon()`, and abandon the old skybox, overlay, line batch and terrain shader in
  `SceneRenderer.create()`. Verify with `RenderLoopTest`, plus `SceneRenderGlTest` with `-Dabyssus.glTests=true`.

## 8. Split the scene view (M1, M9)

- [ ] 8.1 Add `SceneViewState` and move `selectedId`, `gizmoMode`, `hoveredAxis`, `viewCamera` and `preview` out of
  `SceneRenderer`. `SceneViewPanel` owns it. Verify with `SceneViewPanelTest` and `SceneInteractionTest`.
- [ ] 8.2 Add `FrameSnapshot`, published by `SceneRenderer` after each frame, and `SnapshotSceneQueries` implementing
  `SceneQueries` (`pick`, `rayAt`, `groundBelow`, `lowestPoint`, `gizmoHandles`, `gizmoHit`, `beginDrag`). Add a
  `SnapshotSceneQueriesTest` with hand-built snapshots: pick nearest, Drop rest height, gizmo hit, and a single
  `targets()` build per query.
- [ ] 8.3 Make `SceneInteraction` depend on `SceneViewState` + `SceneQueries`, with the `Gesture` sealed state and
  `ViewSize.toFramebuffer`. Remove the `groundBelow` constructor lambda. Port `SceneInteractionTest` to a fake
  `SceneQueries`, and keep its cases (click select, drag, Esc cancel, Drop, params change).
- [ ] 8.4 Move the grid builder and the selection box out of `SceneRenderer` (`GridModel`, `SelectionBox`), leaving the
  renderer with GL passes only. Verify with `SceneRendererCameraTest`, `RenderLoopTest`, `SceneMarkersTest`, the
  `gizmo` and `shadows` test packages, and `SceneRenderGlTest` with `-Dabyssus.glTests=true`.
  Preserve ray frame presentation and animated pose capture with `RayFrameCompositionGlTest` and
  `RayAnimationSnapshotTest`. If the FPS change has landed, preserve settings delivery to replacement views and the
  post-swap sampling boundary, verified by its editor, lifecycle and overlay tests; do not add FPS behavior here.
- [ ] 8.5 Update `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md` and the scene view parts of
  `docs/ai/architecture.md` (the classes and who owns the view state). Verify with `scripts/check-docs.sh`.
- [ ] 8.6 Run the runIde check on a copy of `Untitled`:
  1. Click-select `Model 2`.
  2. W/E switch the gizmo.
  3. Drag the move and rotate handles; Esc cancels.
  4. D drops `Model 0` onto the terrain.
  5. Look through `Camera 4`.
  6. Add a light from the toolbar.
  7. Hide and show the tab (context abandon); the view reloads without errors in `idea.log`.

## 9. Integration

- [ ] 9.1 Verify that `scripts/check-docs.sh` exits 0 with the docs updated in groups 2, 5 and 8.
- [ ] 9.2 Verify that `./gradlew check` passes (tests, `checkNoSingletons`, `checkNoRunCatching`, plugin verification),
  and that the GL tests pass with `-Dabyssus.glTests=true` on a machine with a display.
- [ ] 9.3 Verify that `openspec validate design-review-refactor --strict` passes and that `docs/reviews/design-review-2026-10.md`
  lists which findings this change closed (all but L1, L8 and the deferred number-text question).
- [ ] 9.4 Finish by running `./gradlew check` and `scripts/check-docs.sh` after all preceding edits and verification;
  report unrelated failures without silently fixing them. Confirm the runtime extraction change still follows this
  change, or update affected paths/test commands before applying if the order has changed.
