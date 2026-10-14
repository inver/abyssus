# Tasks

Module paths are the ones on disk today (`projects/lib-core`, `projects/app-game-control-line`, ...). Single plugin tests
run with `./gradlew :plugin-abyssus:test --tests '<class>'`, and library tests with
`./gradlew :<module>:test --tests '<class>'`. Moves use `git mv` so history follows the files. Class and file names in
the tasks come from inspection on 2026-10-14; confirm them when implementing.

## 1. Scaffold `lib-game-link`

- [ ] 1.1 Create `projects/lib-game-link` with its build script, following `lib-physics.gradle.kts`: Kotlin JVM,
  `abyssus.dependency-platforms`, an empty `abyssusSingletonExcludes`, and inclusion in `checkPackageCycles`. Include it
  in `settings.gradle.kts`; it depends on `lib-physics` and `lib-core`. Verify with `./gradlew :lib-game-link:check`
  (green with no sources) and `./gradlew projects` listing it.

## 2. Ray-tracing data moves to `lib-core-editor`

- [ ] 2.1 Add the neutral `ModelPreparationTap` fun interface to `lib-core` (`assets/model`). Replace `ModelLoader`'s
  `raySnapshots` parameter with `preparation: ModelPreparationTap? = null`, keeping the cancellation and dispose
  path. Verify with `./gradlew :lib-core:test --tests '*ModelLoader*'` plus a new
  `ModelLoaderTapTest`: a tap receives the data and images once, and a cancellation thrown from `offer` disposes the
  prepared model.
- [ ] 2.2 `git mv` the ray files (`RaySnapshotStorage.kt`, `ModelRaySnapshot.kt`, `ModelRaySnapshotLoader.kt`,
  `RayTerrainSnapshot.kt`, `TerrainRaySnapshotLoader.kt`, `RaySkySnapshot.kt`, `SkyboxRaySnapshotLoader.kt`,
  `HdrSkyRaySnapshotLoader.kt`, `ProceduralSkyRaySnapshotLoader.kt`, and `RayModelSkinning` wherever it lives) to
  `lib-core-editor/.../editor/ray/snapshot/`, and `RayModelSnapshotTest`, `RaySkySnapshotTest` and
  `RayTerrainSnapshotTest` with them. Remove the ray parameters from `StandardAssetLoaders`; the editor adapts its
  model `RaySnapshotStore` to `ModelPreparationTap`; update `plugin-abyssus/.../AssetLoading.kt`. List here each
  `lib-core` declaration made `public` for the move. Verify with `./gradlew :lib-core-editor:test --tests
  '*RayModelSnapshotTest' --tests '*RaySkySnapshotTest' --tests '*RayTerrainSnapshotTest' --tests
  '*RayAnimationSnapshotTest' --tests '*RaySceneSnapshotTest' --tests '*RayViewFeedTest'` and
  `./gradlew :lib-core:test :plugin-abyssus:compileKotlin`.
- [ ] 2.3 Remove `rayTracingEnabled` / `rayTracing` from `lib-core`'s `SceneDto` (and the ray DTO type if unused). Add a
  plugin-side parse that returns the scene with its root node (the sibling of `DocumentParsing.parse`). Change
  `renderParamsOf` to give `SceneRaySettingsCodec` the raw `rayTracing` subtree, and update the scene-params source.
  Move any binding test that asserted on the DTO's ray fields to `lib-core-editor` against the raw path. Add
  `SceneRenderParamsTest` cases for a missing block, `{}`, and an explicit `null` field, each giving the same
  `SceneRaySettingsState` as before. Verify with `./gradlew :lib-core-editor:test --tests '*SceneRenderParamsTest'`
  and `./gradlew :plugin-abyssus:test --tests '*EditorReadBaselineTest'`.
- [ ] 2.4 Add a `lib-core` test, `SceneEditorKeysIgnoredTest`. It loads a copy of `Untitled`'s `Main Scene` with
  `"rayTracingEnabled": true` and `"rayTracing": {"targetSamplesPerPixel": 64}` through `RuntimeSceneLoader`, and
  asserts the same entities as without the keys and unchanged file bytes. This covers the spec's "Scene with
  ray-tracing settings" scenario headlessly. Verify with `./gradlew :lib-core:test --tests
  '*SceneEditorKeysIgnoredTest'`.
- [ ] 2.5 Update `lib-core/README.md` and `lib-core-editor/README.md`, and the `lib-core` / `lib-core-editor` lines of
  `AGENTS.md` (drop `RaySnapshotStore` from `lib-core`'s description and add it to `lib-core-editor`'s). Verify with
  `scripts/check-docs.sh` reporting no broken path naming `RaySnapshot`.

## 3. The Play bridge moves to `lib-game-link`

- [ ] 3.1 `git mv` `lib-physics/.../play/*` (7 files) to `lib-game-link/.../gamelink/play/`, along with
  `PlayHostMainTest`, `PlayProtocolTest` and `PlayExportTest`. Move `lib-physics`'s `playHost` configuration to
  `lib-game-link`. Verify with `./gradlew :lib-game-link:test` and `./gradlew :lib-physics:test`.
- [ ] 3.2 In `plugin-abyssus`:
  - depend on `lib-game-link` (non-transitive, bundled like `lib-physics`);
  - point `playHostLibs` at `lib-game-link`, `lib-physics` and the `playHost` configuration;
  - update `PLAY_HOST_MAIN` and `PHYSICS_ONLY_MODULE` in `plugin/physics/PlayLaunch.kt` and the imports in
    `plugin/physics`;
  - extend the zip check to require `lib/lib-game-link*.jar` and `play-host/lib-game-link*.jar`.

  Verify with `./gradlew :plugin-abyssus:test --tests '*PlayLaunchTest'` (and any bundled play-host test),
  `./gradlew :plugin-abyssus:checkNoJolt`, and `unzip -l` of the `buildPlugin` zip showing the two
  `lib-game-link` jars and no `lib/jolt-jni*`.
- [ ] 3.3 Add the game's `play` source set (`src/play/kotlin`) and `git mv` `play/ControlLinePlay.kt` into it.
  - Add `playImplementation(project(":lib-game-link"))`.
  - Add the `exportPlay` task (design D5), running `PlayExportMain` on `play.runtimeClasspath`.
  - Give `test` the `play` output.
  - Make sure the generated `play.json` stays out of version control.

  Verify with `./gradlew :app-game-control-line:test --tests '*ControlLinePlayTest'`. Then run `exportPlay` into a
  **copy** of `project/ControlLine`, and check that the new `play.json` classpath has the `play` output and the
  `lib-game-link` jar, and that `git status` shows `components.schema.json` and the rest of the committed project
  unchanged.
- [ ] 3.4 Docs:
  - `lib-physics/README.md`: the "Play in Abyssus" section and its Gradle sample point to `lib-game-link`.
  - New `projects/lib-game-link/README.md`: what it is, who links it, and "run `exportPlay` again after updating".
  - `projects/app-game-control-line/README.md`: the `play` row and source set, `exportPlay` replacing the manual
    `PlayExportMain` run, and the re-export note.
  - `AGENTS.md`: Layout and the Jolt hard rule, which says "only a game or the play host" and now names
    `lib-game-link`.

  Verify that `scripts/check-docs.sh` reports no broken path naming `play`.

## 4. The checks

- [ ] 4.1 Add `checkNoEditorCode` to `gradle/checks.gradle.kts` using the D6 pattern, registered for modules with
  `extra["abyssusShippedLibrary"] = true`. Set that flag in `lib-gdx`, `lib-core` and `lib-physics`. Verify with
  `./gradlew checkNoEditorCode` passing. Then, as a throwaway check, add a file declaring `class RayFooSnapshot` to
  `lib-core/src/main` and confirm the task fails naming that file and line. Remove the file.
- [ ] 4.2 Register `:app-game-control-line:checkShippedClasspath` (D6), configuration-cache safe, and make `check`
  depend on it. Verify with `./gradlew :app-game-control-line:checkShippedClasspath --configuration-cache` passing.
  Then, as a throwaway check, add `implementation(project(":lib-core-editor"))` to the game and confirm the task
  fails naming `:lib-core-editor`. Revert it.
- [ ] 4.3 Document both checks in `AGENTS.md` (Hard rules: "A shipped game carries no editor code", with both
  commands) and in `docs/ai/architecture.md` (module graph: shipped vs. editor side). Verify with
  `scripts/check-docs.sh` reporting no broken path in those sections.

## 5. Integration

- [ ] 5.1 Build the distribution with `./gradlew :app-game-control-line:installDist` and list
  `build/install/*/lib`: no `lib-core-editor`, `lib-game-link`, `lib-raytracing` or `plugin-*` jar. Also run `unzip -l`
  on the `lib-core` and `lib-physics` jars and confirm no `play/` package of the play bridge and no `Ray*Snapshot`
  class.
- [ ] 5.2 Manual game check, needs a display. Start the installed game with
  `JAVA_OPTS=-DcontrolLine.project=<copy of project/ControlLine> build/install/app-game-control-line/bin/app-game-control-line`
  (on macOS also `-XstartOnFirstThread`), fly a round from the main menu to GAME OVER, and record the scores. The
  screens and flight are as before.
- [ ] 5.3 runIde check 1. In `./gradlew :plugin-abyssus:runIde`, open a **copy** of
  `projects/app-game-control-line/project/ControlLine` after `exportPlay` into it, and play the field. The plane flies
  on its lines, and W / S work. Then open a copy of the `Physics` fixture, which has no `play.json`, and play it: the
  bodies simulate.
- [ ] 5.4 runIde check 2. In a copy of `Untitled`, toggle Ray Tracing in the scene Properties and edit
  `targetSamplesPerPixel`. The panel shows the file's values, the edit lands as one undoable change, and other keys
  and number text are unchanged.
- [ ] 5.5 Run `./gradlew check` and `scripts/check-docs.sh`. `check` passes. `check-docs.sh` reports no broken path
  that this change introduced; list any remaining failures by name as pre-existing.

## Workflow follow-up

- Archive with `/openspec-archive-change`. That creates `openspec/specs/game-distribution/spec.md`.
- `reduce-architecture-duplication` task 1.3 and its R6 gate then reference this change's acceptance evidence.
