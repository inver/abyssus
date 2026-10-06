# Tasks

No stage changes behavior. Each stage ends with `./gradlew check` and `scripts/check-docs.sh` green and is committed as
slices (a move commit contains no logic edit). Single plugin tests: `./gradlew :test --tests '<class>'`; module tests:
`./gradlew :editor-core:test` once the module exists. Only tasks 5.x need `runIde`.

**Order:** `refactor-solid-dedup` phases 1, 2, 3 and 5 first. Its phases 4 and 7 are done here (tasks 3.x) and not
twice. Land or rebase the open changes that touch `sceneview/` before stage 3 (task 0.2).

`share-native-document-validation` is implemented: its validator, rejection types and core tests stay in
`core.format`. Task 3.3 moves the editor-facing aliases and consumers only (design D1/D2).

## 0. Preparation

- [x] 0.1 Record the baseline: `./gradlew check` result, the line and file count per package, the import-edge table of the
  proposal (S3) and the 14 `SceneEcsPaths` sites. Keep the commands that produced them in this file's commit message.
- [x] 0.2 List open changes touching `sceneview/`, ray files, `ComponentEditor` or `PanelState`
  (`rg -l 'sceneview|RaySceneSnapshot|ComponentEditor|PanelState' openspec/changes/*/tasks.md`); for each, record in
  design D7 whether it lands first or rebases. Verify that the list is in the design.
- [x] 0.3 Add `checkPackageCycles` (a Gradle import-scan task, second path segment per module) with an allowlist of
  today's cycles. Verify it passes with the allowlist and fails when a new cycle is added temporarily.

## 1. Stage 1: break the cycles (S3, S5)

- [x] 1.1 Move `Vec3`, `Quat`, `PlacementTransform` and the placement records out of `sceneview/SceneContent.kt` into a
  leaf package; fix `ecs`, `projectView` and `sceneview` imports. Verify with `SceneContentTest`,
  `ComponentEditorTest`, and the allowlist entry `ecs → sceneview` removed.
- [x] 1.2 Introduce `SceneViewHost` (four to six methods) implemented by the project view pane, and give
  `SceneFileEditor` the host by constructor; drop its five `projectView` imports. Verify with `SceneFileEditorTest`,
  `AbyssusViewTest`, and the `sceneview → projectView` allowlist entry removed.
- [x] 1.3 Introduce `SceneFacts` (read-only view of content, selection and ray mode) and make `properties` use it
  instead of 20 `sceneview` types. Verify with `EntityPropertiesPanelTest`, `AssetPropertiesPanelTest`,
  `SceneDetailsViewTest` and the `properties → sceneview` entry removed.
- [x] 1.4 Resolve `terrain ↔ properties`, `projectView ↔ properties` and `dto ↔ filetype` (design D5). Verify with the
  terrain test package, `ProjectAssetsTest`, and an empty allowlist in `checkPackageCycles`.
- [x] 1.5 Move `AbyssusProjectLayout`, `FileLoader`, `GeometryUtils`, `JsonProcessor` out of the shared
  `net.nevinsky.abyssus.core` root into `core.io` / `core.project` (98 importing files, by IDE rename). Verify with
  `./gradlew check` and `rg -n '^package net.nevinsky.abyssus.core$' core/src/main` returning nothing.

## 2. Stage 2: one typed scene document layer (S4, S6)

- [x] 2.1 Pin the current outputs: for the Untitled fixture, record `SceneContent.of`, `RaySceneSnapshots.capture`
  inputs, the tree rows and the panel state as test expectations. Verify they pass before any change.
- [x] 2.2 Add `SceneDocument` and `EntityView` (design D4) beside `SceneEcsPaths`, with tests comparing them with the
  pinned outputs. Verify with the new `SceneDocumentTest`.
- [x] 2.3 Switch callers one at a time (`SceneContent`, `RaySceneSnapshots`, `RayViewFeed`, `PanelState`, `DtoTree`,
  `EntitySelection`, `AddLightAction`, then the writers `SceneTransformWriter`, `SceneRayEdits`, `ComponentEditor`,
  `LightEntities`), running the pinned tests after each. Verify that `rg -n 'SceneEcsPaths|"components"' src/main` lists
  only `SceneDocument`; the shared format check's reserved-field paths stay in `core.format`.
- [x] 2.4 Make `SceneEcsPaths` private to `SceneDocument` and record the ADR in design D3 (editor keeps the JSON model,
  games keep Ashley; both share the codecs). Verify with `./gradlew check`.

## 3. Stage 3: extract `editor-core` (S1, S2); replaces `refactor-solid-dedup` phases 4 and 7

- [x] 3.1 Split the mixed files inside the plugin first (design D2/D7): the Swing-free parts of `PanelState`,
  `SceneFileEditor`, `SceneViewPanel` (toolbar state, input forwarding, camera choices), `AbyssusProjectViewPane`
  and `AssetPropertiesPanel` into their own files. Verify with the existing tests unchanged.
- [x] 3.2 Create the `editor-core` Gradle module (plain Kotlin, depends on `core`, `runtime`, `raytracing`,
  `gdx-model`, Jackson; `checkNoSingletons` and the shared checks from `refactor-solid-dedup` 5.3). Verify with
  `./gradlew :editor-core:build` on an empty module and the classpath test of design D8.
- [x] 3.3 Slice `document`: `SceneJson`, `JsonFormat`, the editor-facing format type aliases, `DocumentParsing`,
  `AssetMetaReader`, `SceneDocument` and their editor tests. Keep `AbyssusDocumentFormat`, its rejection types and
  core validation tests in `core.format`; consumers and aliases use that implementation. Verify with
  `./gradlew :editor-core:test :test :core:test :runtime:test`, unchanged rejection reasons, no duplicate validator
  implementation, and no dependency from `core` or `runtime` to `editor-core`.
- [x] 3.4 Slice `components`: the four files of the component editor (with `refactor-solid-dedup` 4.1 done here),
  `ComponentReader`, `LightEntities`, `SchemaMerge`. Verify with `:editor-core:test` and `ComponentActionsTest`.
- [x] 3.5 Slice `content` and `pick`: `SceneContent`, `SceneRenderParams`, `PlacementMapper`, `ScenePicker`,
  `SceneQueries`, `TerrainRestHeight`, `OrbitCamera`, gizmo math, `SceneInteraction`, `ScenePreview`,
  `SceneTransformWriter`. Verify with `SceneInteractionTest`, `ScenePickerTest`, `GizmoDragTest`,
  `SceneTransformWriterTest` (moved) and (GL) `SceneRenderGlTest` still passing in the plugin.
- [x] 3.6 Slice `terrain` and `meta`: terrain generation, noise, recipe and new-terrain file logic; `AssetMetaEditor`,
  field descriptions, `MetaRows`, `AssetReferenceChoices`, the pure part of `PanelState`. Verify with the moved
  terrain and meta tests and `NewTerrainActionTest` in the plugin.
- [x] 3.7 Slice `ray`: the plain `Ray*` files and `RaySceneSnapshots`. Verify with the moved ray tests, `:raytracing:test`
  and `SceneViewPanelRayTest` in the plugin.
- [x] 3.8 Add the headless API for spec `headless-scene-editing`: validate and edit by text in, text out (no `Project`,
  no VFS), using the shared `core.format` validator and rejection types. Verify with `HeadlessSceneEditingTest`:
  the move, the refusal (including the same rejection reason) and the asset-property scenarios of the
  spec, each compared with the plugin's `editSceneJson` result on the same text.
- [x] 3.9 Check the plugin zip and `physics-plugin`: `./gradlew buildPlugin` contains `editor-core` once; `:physics-plugin:test`
  and `:physics-plugin:checkNoJolt` pass; the physics plugin does not bundle it.

## 4. Stage 4 decision (`editor-render`)

- [x] 4.1 Decide using design D9: write the decision and its reason in the design. If a concrete user exists, create the
  module and move `SceneRenderer`, shadows, fog, sky and gizmo drawing in slices like stage 3; otherwise close the task.
  Verify (if done) with `./gradlew check -Dabyssus.glTests=true` on a machine with a display.

## 5. Manual checks (runIde; cannot run headless)

- [ ] 5.1 In `./gradlew runIde` with a copy of the Untitled project: open the scene view, drag a gizmo handle, Undo,
  rename the scene, toggle an eye, edit a component and an asset property; check each still works and Undo reverts it.
- [ ] 5.2 Turn on Ray Tracing in the Abyssus Properties panel and confirm the view still switches modes and falls back
  as before.
- [ ] 5.3 Install the built zip of both plugins into a running sandbox, update and uninstall without a restart.

## 6. Docs and finish

- [ ] 6.1 Update `AGENTS.md` (layout, hard rules: new module, no IntelliJ import in `editor-core`), `docs/ai/architecture.md`
  (module graph, scene document layer), `docs/ai/conventions.md`, `docs/ai/testing.md`, `editor-core/README.md` and the
  package READMEs. Verify with `scripts/check-docs.sh`.
- [ ] 6.2 Run `./gradlew check` and `scripts/check-docs.sh`; report anything failing for other reasons with its cause.
- [ ] 6.3 Archive the change, which adds `headless-scene-editing` to `openspec/specs/`.
