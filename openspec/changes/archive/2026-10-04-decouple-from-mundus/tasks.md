# Tasks

## 1. Integration order and native validation

- [x] 1.1 Re-read `openspec list --json` and prerequisite status for `design-review-refactor` and `extract-scene-runtime`; use their final shared readers/codecs. If order changes, amend their remaining artifacts to the native contract before implementation. Verify by reviewing the resulting ownership/order notes and running `openspec validate <affected-change> --strict`; do not mark unfinished prerequisites complete.

  Integration review (2026-10-04): both prerequisites are archived under `2026-10-04-*` and absent from the active
  change list. Their remaining manual/full-suite checks stay unchecked in history. No implementation-order change
  or prerequisite artifact edit is needed. Use `core`'s `AssetMetaReader` / `JsonProcessor` and `runtime`'s
  `SceneLoading`, `SceneParser` and ECS codecs/loader/writer; retain the plugin's document-aware adapters and writer.
  Native format validation belongs in `core` and is shared by these existing boundaries.
- [x] 1.2 Add pure constructor-wired `AbyssusDocumentFormat` to `core`, with document-kind/header validation and reserved ECS-field validation. Add `AbyssusDocumentFormatTest` cases for supported headers, absent/foreign/null markers, string/fractional/future versions, legacy identifiers/classes inside marked scenes and opaque custom payloads. Verify with `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.format.AbyssusDocumentFormatTest'` and `./gradlew :core:checkNoSingletons`.
- [x] 1.3 Document native headers, stable identifiers, document-versus-metadata version, retained layout, default omission and rejection in `docs/ai/file-formats.md`; replace its compatibility-based descriptions. Verify examples with `AbyssusDocumentFormatTest` and run `scripts/check-docs.sh`.

## 2. Native ECS and repository fixtures

- [x] 2.1 Replace renderable class dispatch/output with `kind: "asset"`, preserve unknown native kinds, remove class constants/aliases and native `componentIdentifiers` creation in component/light helpers. Gate raw ECS loader/writer inputs with the shared payload validator. Verify `ComponentCodecsTest`, `SceneEcsLoaderTest`, `SceneEcsWriterTest`, `ComponentEditorTest` and `LightEntitiesTest`: native round trip, unknown kinds, rejected legacy payloads, no class identifiers, Add Light/Undo and default omission. Run these in their final module (`:runtime:test` for moved ECS tests, plugin `:test --tests '<class>'` for editor tests).
- [x] 2.2 Prepare repository fixtures `Untitled`, `Animated`, `Lights` and every inline native test document with headers, no identifier tables, asset renderable kinds and inert native editor-marker kinds. Preserve existing ids, asset names, transforms, light/handle relationships and binary files. Keep small explicit legacy fixtures for rejection tests. Verify an inventory comparison before/after, then `SceneContentTest`, `SceneTransformWriterTest`, `ProjectAssetsTest`, `SceneEntityParityTest` if present, and the corresponding runtime load/round-trip tests.

  Apply note (2026-10-04): fixtures converted; before/after inventory compared (same entity ids, components, fields,
  assets; only renderable `class` became `kind`). `:runtime:test` and `:core:test` pass. In plugin tests, `SceneContentTest`
  `mainSceneHasThreeModelsAndOneTerrain` / `mainSceneHasTheFixtureCamera` still fail on the baseline `lights.isEmpty()`
  assertion (the fixture keeps `Spot Light 8`), unrelated to this conversion and not fixed here.
- [x] 2.3 Update current ECS/runtime and scene-view package notes for native kinds and raw extension preservation. Verify the notes match the round-trip and look-at tests and run `scripts/check-docs.sh`.

## 3. Read, edit and authoring boundaries

- [x] 3.1 Gate project/scene DTO and runtime readers, scene parameters and action availability before binding/enumeration. Localize unsupported-format reasons in `AbyssusBundle.properties`. Add `NativeDocumentReadTest` and runtime native-loading cases for saved/unsaved text, unsupported project/scene and supported siblings. Verify `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.dto.NativeDocumentReadTest' --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneRenderParamsTest'`, plus runtime tests with no IDE.
- [x] 3.2 Gate shared metadata reading and all asset listing/loading/edit/preview consumers, preserving unsaved `MetaTextSource` snapshots and once-per-revision diagnostics. Add `AssetFilesTest` and `AssetMetaReaderTest` cases for refused metadata, no usable rejected references/UUIDs, changed snapshot versions and supported sibling assets; verify `./gradlew :core:test` and plugin `AssetPropertiesPanelTest`, `AssetReferenceChoicesTest`, `SceneAssetRefreshEditorTest`.
- [x] 3.3 Gate `editSceneJson` before mutation and after candidate edits, and `SceneFormatListener` before automatic formatting. Add `NativeDocumentWriteGuardTest` cases for legacy/future files through scene and asset edits, opening/switching text tabs, marker-removal attempts, unchanged disk/document text, native one-command edits and Undo. Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.filetype.NativeDocumentWriteGuardTest'` and existing component/transform/formatting tests.
- [x] 3.4 Write native markers from terrain metadata creation and any existing project/scene/asset producers; gate terrain regeneration, stale-source validation and preview on native source metadata. Keep external binary/model/image and recipe formats unchanged. Verify `TerrainAssetEncodingTest`, `AssetMetaEditorTest`, terrain transaction/controller tests and `NewTerrainTest` for native output, rejected source data and unchanged rollback/Undo behavior; run `./gradlew :core:test` and `./gradlew :test --tests 'net.nevinsky.abyssus.terrain.*'`.

  Apply note (2026-10-04): 3.1–3.3 verification passes (`NativeDocumentReadTest`, `SceneRenderParamsTest`,
  `SceneLoadingTest`, `:core:test`, `AssetPropertiesPanelTest`, `AssetReferenceChoicesTest`, `SceneAssetRefreshEditorTest`,
  `NativeDocumentWriteGuardTest`, component/transform/formatting tests). 3.4: `NewTerrainTest`, `NewTerrainActionTest`,
  `TerrainAssetEncodingTest` and `AssetMetaEditorTest` pass and new terrain metadata carries the markers; the seven
  `TerrainGenerationPanelTest` failures listed in the baseline above still fail unchanged and are not fixed here.
- [x] 3.5 Update architecture/testing notes for the validator's ownership, caller threads, raw ECS versus enclosing-document validation, fixture setup and failure presentation. Verify `scripts/check-docs.sh` and review against the implemented read/write boundaries.

## 4. Independent identity and coherent planning

- [x] 4.1 Update README introduction/plugin-description block and CHANGELOG Unreleased notes to describe an independent libGDX editor, native format version 1, breaking compatibility and no importer. Preserve the plugin-description markers. Verify `./gradlew patchPluginXml` and inspect the generated description and documentation for old product positioning.
- [x] 4.2 Replace compatibility constraints in AGENTS.md and `openspec/config.yaml`, active source descriptions, `docs/ai/glossary.md` and package READMEs. Move the existing model-fork origin information to `docs/third-party/gdx-model-origin.md` and link it from `gdx-model/README.md`; preserve source/license attribution, headers, notices and archives. Verify `scripts/check-docs.sh` and an `rg -n -i 'mundus|mbrlabs'` audit of active source/docs/config, classifying remaining matches as attribution or explicit rejection inputs rather than product/format coupling.
- [x] 4.3 Reconcile still-active artifacts for runtime extraction, design refactor, asset editing/terrain generation, custom components, weather presets, water, clouds/lighting, physics/play, ray tracing and FPS with native headers and identifiers. Remove promises to preserve/emit Java-class bookkeeping and upstream support; update producers and fixture expectations. Edit only remaining relevant plans and preserve completed status unless re-verification is needed. Verify each edited change with `openspec validate <name> --strict` and review its delta requirements for conflicts with this change. Leave archived history intact.

  Apply note (2026-10-04): reconciled `add-custom-components` (no `componentIdentifiers`, no spike, short-name-only
  writes, delta aligned with this change), `add-jolt-physics`, `add-control-line-game`, `add-realistic-water`,
  `add-weather-preset-creation`, `add-sky-clouds`, `add-cloud-scene-lighting`, `add-project-fps-counter`,
  `add-scene-raytracing-settings` and `add-remote-asset-library`. Every active change passes `openspec validate --strict`.
- [x] 4.4 Update main-spec purposes that describe an upstream-editor product/format to native Abyssus wording; do not change unrelated requirements or duplicate the behavior deltas in this change. Verify `openspec validate --specs --strict` and review that requirement/scenario blocks outside the approved deltas are unchanged.

  Apply note (2026-10-04): only the `## Purpose` text of `asset-loading`, `scene-ecs-systems`, `scene-loading`,
  `scene-component-editing` and `scene-light-creation` changed; `openspec validate --specs --strict` passes (23/23).
  Requirement/scenario text outside the approved deltas that still names the other editor (for example
  `scene-object-transform`, `scene-entity-lights` fixture-path scenarios, `scene-loading`, `terrain-authoring`'s
  "Mundus-compatible terrain output") was left untouched as instructed; it needs its own change.

## 5. Integration verification

- [x] 5.1 Verify real rendering still works with native fixtures using `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneRenderGlTest' --tests 'net.nevinsky.abyssus.plugin.sceneview.RayFrameCompositionGlTest' -Dabyssus.glTests=true` and `./gradlew :core:test -Dabyssus.glTests=true`; record unavailable display/backend checks rather than claiming they passed.

  Apply note (2026-10-04): `SceneRenderGlTest` (27) and `RayFrameCompositionGlTest` (5) with
  `-Dabyssus.glTests=true` ran with 0 skipped / 0 failed; `:core:test -Dabyssus.glTests=true` ran with 0 skipped / 0 failed.
- [ ] 5.2 Perform runIde checks 1–4 below using a temporary native fixture copy and separate legacy rejection files. Record results; leave this task open for unperformed checks.

  Not performed (needs an interactive IDE): follow the four numbered checks below on a temporary copy of `Untitled`.
- [ ] 5.3 Run `openspec validate decouple-from-mundus --strict`, `./gradlew check` and `scripts/check-docs.sh`. Report unrelated failures explicitly. Verify the final identifier/association audit finds no executable upstream aliases or active compatibility guarantees outside attribution/rejection documentation and preserved history.

  Apply note (2026-10-04), left open: `openspec validate decouple-from-mundus --strict` and `scripts/check-docs.sh`
  pass. `./gradlew check --continue` completes with exactly the 13 baseline failures listed in the apply record above
  (`AbyssusViewTest.testNodeTree`, 2 `SceneContentTest`, 2 `SceneMarkersTest`, 7 `TerrainGenerationPanelTest`,
  `VulkanNativePackagingTest`) and no new ones. They are unrelated to this change and were not fixed here. The
  identifier audit finds no executable upstream aliases in `src/main`, `core`, `runtime`; remaining matches are
  attribution (`gdx-model` sources, `docs/third-party`), legacy rejection inputs in tests, historic
  `docs/reviews` / `docs/superpowers`, the fixture file name `Mundus Lights.scene` (kept because main specs cite its path),
  and main-spec requirement text outside the approved deltas. Close this task once `check` is green or the owner accepts
  the baseline failures.

### Numbered runIde checks for task 5.2

1. Launch `./gradlew runIde -PideProject=<temporary-native-project-copy>`. Open the native project and `Main Scene`; inspect models, terrain, sky, camera, lights and properties. Move/rotate a model, aim a handle-based light, edit spotlight beam settings, Add Light and Undo; verify native identifiers/markers persist and unrelated text remains unchanged.
2. Open unmarked `.abss`/`.scene` files and marked files with a future version or old class/identifier fields. Switch text/scene tabs and attempt tree/property edits. Verify unsupported explanations and compare both document text and disk bytes against the starting values, including automatic-formatting behavior.
3. Put one unsupported asset metadata file beside supported assets. Verify the unsupported asset's reason, other assets loading, refused edit/regeneration/preview, and no source changes. Create a new terrain and verify native markers, rendering, Undo/Redo and recipe behavior.
4. Inspect the generated plugin description and public README; verify independent positioning, explicit compatibility break and working native examples. Verify source provenance remains discoverable through third-party documentation.

## Apply record (2026-10-04)

- Tasks 1.1–1.3 completed. `AbyssusDocumentFormatTest` has six passing cases, and
  `:core:checkNoSingletons`, `scripts/check-docs.sh` and strict change validation pass.
  Test compilation first failed because the format API did not exist; verification passed after implementation.
- The native gate is not wired into consumers yet; tasks 2–5 remain pending.
- Fixture decision confirmed by the user: corrected the light-creation delta to highest id 8/new entity 9.
  Existing fixture entities, including `Spot Light 8`, are preserved.
- Baseline `./gradlew check` fails before native implementation (full output:
  `/private/tmp/abyssus-native-baseline.log`). Existing failures, also recorded in runtime extraction:
  - `AbyssusViewTest.testNodeTree`: seven-entity expectation versus the current nine-entity fixture.
  - `SceneContentTest.mainSceneHasThreeModelsAndOneTerrain`, `mainSceneHasTheFixtureCamera` and
    `SceneMarkersTest.theViewCameraHasNoMarkerTarget`, `aCameraDrawsABodyAndAFrustum`: existing fixture/placement
    assertions; the latter expects 28 line segments but gets 54. No fixes made here.
  - `TerrainGenerationPanelTest.testInvalidSettingsDisablePreviewAndExplain`,
    `testTheSectionShowsResolutionRecipeStatusAndDefaults`, `testUnsavedMetadataChangedAfterPreviewRejectsApply`,
    `testPreviewWritesNothingAndCancelDiscardsTheDraft`, `testSelectionChangeDiscardsAPendingPreview`,
    `testUndoAndRedoFromThePanelRestoreExactBytesAndRemoveTheNewRecipe`,
    `testAnExternalHeightsChangeAfterPreviewRejectsApplyWithoutOverwriting`: existing panel assertions;
    recipe/Undo behavior remains unresolved. No fixes made here.
  - `VulkanNativePackagingTest.jarHoldsValidSpirvAndNoShaderCompiler`: compiled SPIR-V resources are missing.
