# Tasks

Module paths are the ones on disk today (`projects/lib-core`, `projects/app-game-control-line`, ...). The root project
is no longer the plugin, so single plugin tests run with `./gradlew :plugin-abyssus:test --tests '<class>'`, and
library tests with `./gradlew :<module>:test --tests '<class>'`. Moves use `git mv` so history follows the files.

## 1. Scaffold the two editor-side modules

- [ ] 1.1 Create `projects/lib-importer` and `projects/lib-game-link` with their build scripts, following
  `lib-physics.gradle.kts`: Kotlin JVM, `abyssus.dependency-platforms`, an empty `abyssusSingletonExcludes`, and
  inclusion in `checkPackageCycles`. Include both in `settings.gradle.kts`. `lib-importer` depends on `lib-core`;
  `lib-game-link` depends on `lib-physics` and `lib-runtime`. Verify with
  `./gradlew :lib-importer:check :lib-game-link:check` (green with no sources) and `./gradlew projects` listing both.

## 2. FlightGear import moves to `lib-importer`

- [ ] 2.1 `git mv` `lib-core/.../flightgear/*` to `lib-importer/src/main/kotlin/net/nevinsky/abyssus/lib/importer/flightgear/`,
  and its 8 tests and the `FlightGearFixtures` testFixture with them. Update packages and imports. Add
  `implementation(project(":lib-importer"))` to `plugin-abyssus` (bundled like the other libraries) and
  `"importersImplementation"(project(":lib-importer"))` to the game. Verify with `./gradlew :lib-importer:test`
  (`FlightGearImportTest`, `Ac3dReaderTest`, `GlbWriterTest`, `FlightGearArchiveTest`, `FlightGearModelXmlTest`,
  `SgiImageTest`, `RestStateTest`, `FlightGearFixturesTest` all pass), `./gradlew :plugin-abyssus:test --tests
  '*ImportFlightGearTest' --tests '*FlightGearImportSettingsTest'`, and `./gradlew
  :app-game-control-line:compileImportersKotlin`.
- [ ] 2.2 Update the docs that name the old location: `lib-core/README.md`, `docs/ai/file-formats.md` (the
  `FlightGearImport.kt` path), the `AGENTS.md` Layout entry for `core` and a new entry for `lib-importer`, and a new
  `projects/lib-importer/README.md`. Verify that `scripts/check-docs.sh` reports no broken path naming `flightgear`.
- [ ] 2.3 Amend `openspec/changes/add-model-import/design.md` and `tasks.md` so `ModelImport`, `GltfWriter` and the
  FlightGear writer swap target `lib-importer` (its package and test resources), not `core`. Verify with
  `grep -n 'core/src\|core.flightgear' openspec/changes/add-model-import/*.md`, which finds no stale target.

## 3. Ray-tracing data moves to `lib-core-editor`

- [ ] 3.1 Add the neutral `ModelPreparationTap` fun interface to `lib-core` (`assets/model`). Replace `ModelLoader`'s
  `raySnapshots` parameter with `preparation: ModelPreparationTap? = null`, keeping the cancellation and dispose
  path. Verify with `./gradlew :lib-core:test --tests '*ModelLoader*'` plus a new
  `ModelLoaderTapTest`: a tap receives the data and images once, and a cancellation thrown from `offer` disposes the
  prepared model.
- [ ] 3.2 `git mv` the ray files (`RaySnapshotStorage.kt`, `ModelRaySnapshot*.kt`, `RayModelSkinning.kt`,
  `RayTerrainSnapshot.kt`, `TerrainRaySnapshotLoader.kt`, `RaySkySnapshot.kt`, `SkyboxRaySnapshotLoader.kt`,
  `HdrSkyRaySnapshotLoader.kt`, `ProceduralSkyRaySnapshotLoader.kt`) to
  `lib-core-editor/.../editor/ray/snapshot/`, and `RayModelSnapshotTest`, `RaySkySnapshotTest` and
  `RayTerrainSnapshotTest` with them. The editor adapts its model `RaySnapshotStore` to `ModelPreparationTap`; update
  `plugin-abyssus/.../AssetLoading.kt`. List here each `lib-core` declaration made `public` for the move. Verify with
  `./gradlew :lib-core-editor:test --tests '*RayModelSnapshotTest' --tests '*RaySkySnapshotTest' --tests
  '*RayTerrainSnapshotTest' --tests '*RayAnimationSnapshotTest' --tests '*RaySceneSnapshotTest' --tests
  '*RayViewFeedTest'`.
- [ ] 3.3 Remove `rayTracingEnabled` / `rayTracing` from `lib-core`'s `Scene` and delete `scene/RayTracing.kt`. Add
  the `DocumentParsing` sibling that returns the scene with its root node. Change `renderParamsOf` to give
  `SceneRaySettingsCodec` the raw `rayTracing` subtree, and update `SceneParamsSource`. Move
  `SceneRayTracingBindingTest`'s cases to `lib-core-editor` against the raw path. Add `SceneRenderParamsTest` cases
  for a missing block, `{}`, and an explicit `null` field, each giving the same `SceneRaySettingsState` as before.
  Verify with `./gradlew :lib-core-editor:test --tests '*SceneRenderParamsTest' --tests '*SceneRayTracingBindingTest'`
  and `./gradlew :plugin-abyssus:test --tests '*EditorReadBaselineTest'`.
- [ ] 3.4 Add a `lib-runtime` test, `SceneEditorKeysIgnoredTest`. It loads a copy of `Untitled`'s `Main Scene` with
  `"rayTracingEnabled": true` and `"rayTracing": {"targetSamplesPerPixel": 64}` through `RuntimeSceneLoader`, and
  asserts the same entities as without the keys and unchanged file bytes. This covers the spec's "Scene with
  ray-tracing settings" scenario headlessly. Verify with `./gradlew :lib-runtime:test --tests
  '*SceneEditorKeysIgnoredTest'`.
- [ ] 3.5 Update `lib-core/README.md` and `lib-core-editor/README.md`, and the `core` / `editor-core` lines of
  `AGENTS.md` (drop `RaySnapshotStore` from `core`'s description and add it to `editor-core`'s). Verify with
  `scripts/check-docs.sh` reporting no broken path naming `RaySnapshot`.

## 4. The scene writer moves to `lib-core-editor`

- [ ] 4.1 `git mv` `EcsWriter.kt` to `lib-core-editor/.../editor/components/` and `EcsWriterTest` with it. Split
  `NativeEcsAdmissionTest`: the read cases stay in `lib-runtime`, and the write cases go to
  `lib-core-editor`'s `NativeEcsWriteAdmissionTest`. Update `lib-runtime/README.md`. Verify with `./gradlew
  :lib-runtime:test --tests '*NativeEcsAdmissionTest'` and `./gradlew :lib-core-editor:test --tests '*EcsWriterTest'
  --tests '*NativeEcsWriteAdmissionTest'`.

## 5. The Play bridge moves to `lib-game-link`

- [ ] 5.1 `git mv` `lib-physics/.../play/*` to `lib-game-link/.../gamelink/play/`, along with `PlayHostMainTest`,
  `PlayProtocolTest` and `PlayExportTest`. Move `SchemaExportMain.kt` (`exportSchema` too) from
  `lib-runtime/schema` to `lib-game-link/.../gamelink/export/`. Move `lib-physics`'s `playHost` configuration to
  `lib-game-link`. Verify with `./gradlew :lib-game-link:test`, `./gradlew :lib-physics:test` and `./gradlew
  :lib-runtime:test --tests '*SchemaFileTest'`.
- [ ] 5.2 In `plugin-abyssus-physics`:
  - depend on `lib-game-link` (non-transitive, bundled);
  - point `playHostLibs` at `lib-game-link` plus its `playHost` configuration;
  - run `schemaExport` on `lib-game-link`;
  - update `PLAY_HOST_MAIN` in `PlayLaunch.kt` and the imports.

  Verify with `./gradlew :plugin-abyssus-physics:test` (including `BundledPlayHostTest`),
  `./gradlew :plugin-abyssus-physics:checkNoJolt`, and `unzip -l` of the `buildPlugin` zip showing
  `lib/lib-game-link*.jar`, `play-host/lib-game-link*.jar` and no `lib/jolt-jni*`.
- [ ] 5.3 Add the game's `play` source set (`src/play/kotlin`) and `git mv` `play/ControlLinePlay.kt` into it.
  - Add `playImplementation(project(":lib-game-link"))`.
  - Run `exportComponentSchema` / `exportPlay` on `play.runtimeClasspath`.
  - Give `test` the `play` output.

  Verify with `./gradlew :app-game-control-line:test --tests '*ControlLinePlayTest' --tests '*ComponentsExportTest'`.
  Also run `./gradlew :app-game-control-line:exportAbyssus`, then check that `git diff --exit-code
  projects/app-game-control-line/project/ControlLine/abyssus/components.schema.json` is clean and that the new
  `play.json` classpath has the `play` output and the `lib-game-link` jar.
- [ ] 5.4 Docs:
  - `lib-physics/README.md`: the "Play in Abyssus" section points to `lib-game-link`.
  - New `projects/lib-game-link/README.md`: what it is, who links it, and "run `exportAbyssus` again after updating".
  - `projects/app-game-control-line/README.md`: the `play` row, the `play` source set, and the re-export note.
  - `AGENTS.md`: Layout and the Jolt hard rule, which says "only a game or the play host" and now names
    `lib-game-link`.

  Verify that `scripts/check-docs.sh` reports no broken path naming `play`.
- [ ] 5.5 Amend `openspec/changes/merge-physics-into-abyssus/tasks.md` (and `design.md` where it says "no change to
  `lib-physics`, the play protocol...") so the move of `playHostLibs`, `schemaExport` and the protocol dependency
  names `lib-game-link`. Verify with `grep -n 'lib-physics' openspec/changes/merge-physics-into-abyssus/*.md`, where
  each remaining hit is about physics rather than Play.

## 6. The checks

- [ ] 6.1 Add `checkNoEditorCode` to `gradle/checks.gradle.kts` using the D7 pattern, registered for modules with
  `extra["abyssusShippedLibrary"] = true`. Set that flag in `lib-gdx-model`, `lib-core`, `lib-runtime` and
  `lib-physics`. Verify with `./gradlew checkNoEditorCode` passing. Then, as a throwaway check, add a file declaring
  `class RayFooSnapshot` to `lib-core/src/main` and confirm the task fails naming that file and line. Remove the file.
- [ ] 6.2 Register `:app-game-control-line:checkShippedClasspath` (D7), configuration-cache safe, and make `check`
  depend on it. Verify with `./gradlew :app-game-control-line:checkShippedClasspath --configuration-cache` passing.
  Then, as a throwaway check, add `implementation(project(":lib-core-editor"))` to the game and confirm the task
  fails naming `:lib-core-editor`. Revert it.
- [ ] 6.3 Document both checks in `AGENTS.md` (Hard rules: "A shipped game carries no editor code", with both
  commands) and in `docs/ai/architecture.md` (module graph: shipped vs. editor side). Verify with
  `scripts/check-docs.sh` reporting no broken path in those sections.

## 7. Integration

- [ ] 7.1 Build the distribution with `./gradlew :app-game-control-line:installDist` and list
  `build/install/*/lib`: no `lib-core-editor`, `lib-importer`, `lib-game-link`, `lib-raytracing` or `plugin-*` jar.
  Also run `unzip -l` on the `lib-core`, `lib-runtime` and `lib-physics` jars and confirm no `flightgear/`, `play/`,
  `EcsWriter`, `SchemaExportMain` or `Ray*Snapshot` class.
- [ ] 7.2 Manual game check, needs a display. Start the installed game with
  `JAVA_OPTS=-DcontrolLine.project=<copy of project/ControlLine> build/install/app-game-control-line/bin/app-game-control-line`
  (on macOS also `-XstartOnFirstThread`), fly a round from the main menu to GAME OVER, and record the scores. The screens and flight are as before.
- [ ] 7.3 runIde check 1. In `./gradlew :plugin-abyssus-physics:runIde`, open a **copy** of
  `projects/app-game-control-line/project/ControlLine` after `exportAbyssus`, and play the field. The plane flies on
  its lines, and W / S work. Then open a copy of the `Physics` fixture, which has no `play.json`, and play it: the
  bodies simulate.
- [ ] 7.4 runIde check 2. In a copy of `Untitled`, run Import FlightGear Aircraft... on the Assets node with a test
  archive. The model, `meta.json` and undo behave as before.
- [ ] 7.5 runIde check 3. In a copy of `Untitled`, toggle Ray Tracing in the scene Properties and edit
  `targetSamplesPerPixel`. The panel shows the file's values, the edit lands as one undoable change, and other keys
  and number text are unchanged.
- [ ] 7.6 Run `./gradlew check` and `scripts/check-docs.sh`. `check` passes. `check-docs.sh` reports no broken path
  that this change introduced; list any remaining failures (left over from `restructure-gradle-modules`) by name as
  pre-existing.

## Workflow follow-up

- Archive with `/openspec-archive-change`. That creates `openspec/specs/game-distribution/spec.md`.
- Next change: `extract-lib-render`, built on the shipped/editor boundary set here.
