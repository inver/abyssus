# Tasks

## 0. Before moving anything

- [x] 0.1 Confirm nobody else is editing the loading code: `git status` shows no uncommitted change under
      `src/main/kotlin/net/nevinsky/abyssus/sceneview/` or `src/main/resources/shader/`, and the user confirms no other
      session is working there. Verify: `git status --short src/main` is empty and the answer is recorded here.
      Recorded: on `79c909b`, `git status --short src/main` was empty; the user confirmed no other session is editing.
- [x] 0.2 Amend the open changes that name paths this change moves. Only `add-hdr-skybox-ibl` does: add a note at
      the top of its tasks and design mapping each moved class, shader and test to its `core` location, and point its
      open tasks (1.1, 3.2) at the new names; its completed tasks keep the paths they used when they were done.
      `add-scene-object-drop` and `add-light-entities` name none of the moved code. Verify: the note is present,
      `grep -n "sceneview/skybox\|HdrToneMap\|HdrFixtures" openspec/changes/add-*/tasks.md` finds old names only in
      checked tasks or next to their new name, and `openspec validate <each> --strict` passes

## 1. The module

- [x] 1.1 Add `:core`: `settings.gradle.kts` includes it; `core/build.gradle.kts` as design decision 1
      (`java-library`, Kotlin JVM 21, own stdlib, `api(project(":gdx-model"))`, Jackson, JUnit 4, the opt-in GL test
      dependencies); a `jacksonVersion` property in `gradle.properties` used by both modules; the plugin gets
      `implementation(project(":core"))` with the same Kotlin and SLF4J excludes as `:gdx-model`. Verify:
      `./gradlew :core:build :buildPlugin` succeeds and `unzip -l build/distributions/*.zip | grep core` lists the jar
- [x] 1.2 Add the `checkNoSingletons` task to `core`, wired into `check`: it fails on a `core/src/main` Kotlin file
      declaring `object <Name>` or `companion object`, other than `data object`; object expressions pass. Verify: with a
      temporary `core/src/main/kotlin/Probe.kt` holding `object Probe`, `./gradlew :core:checkNoSingletons` fails
      naming the file; with `val x = object : Runnable { override fun run() {} }` and `sealed interface S { data object A : S }`
      it passes; the probe file is deleted afterwards

## 2. Files and JSON

- [x] 2.1 Move `JsonProcessor` to `core` (`assets.json`) without its companion, and add `core`'s
      `runCatchingKeepingCancellation` (rethrows `CancellationException`). Add the plugin's light application service
      `AbyssusCore` exposing `json`; `dto/ProjectReader` and `dto/SceneReader` use `service<AbyssusCore>().json`.
      Verify: `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.json.JsonProcessorTest'` (new: skips unknown
      properties, takes an enum's default for an unknown value, keeps declaration order when pretty-printing) and
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.dto.*'` pass
- [x] 2.2 Move `MetaBase`, `MetaType`, `Asset`, `TerrainFiles` and the file part of `ProjectAssetFiles` to
      `core` (`assets.files`) as `AssetFiles(projectDir, json)`, replacing IntelliJ's `StringUtils` with
      `isNullOrBlank`, and add a `JsonProcessorTest` case parsing a `MetaBase` of every `MetaType` and an unknown type
      as `UNKNOWN`; move the asset-folder constants of `ProjectLayout` (`ASSETS_DIR`, `META_FILE`, `SPLAT_FIELDS`)
      to `assets.AssetLayout` and point `ProjectLayout` at them. Move `loadShortAssets` to plugin
      `dto/ProjectAssetListing.kt`, taking the `JsonProcessor`; drop the `@Service` on the old class and delete it.
      Verify: `ProjectAssetFilesTest` moved to `core` as plain JUnit `AssetFilesTest` passes with
      `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.files.AssetFilesTest'`, and
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.dto.*' --tests 'net.nevinsky.abyssus.AbyssusViewTest'` shows no
      failure that `HEAD` does not already have

## 3. The pipeline

- [x] 3.1 Add `AssetLog` (one `warn(message, error)` method) and `ShaderSource(root)` (`program(vertex, vararg
      fragment)`, joining fragment files) to `core`; the plugin's `Shaders` object becomes a `ShaderSource("/shader/scene")`
      instance owned by `AbyssusCore`. Verify: `ShaderSourceTest` in `core` (missing file names the path; fragments are
      joined in order, read from a test resource root) passes with `./gradlew :core:test`
- [x] 3.2 Move `AssetLoader`, `AssetCache` (taking `AssetLog` instead of `Logger`) and the generic `SceneAssets`
      (taking the executor, the loader, a `files: (File) -> AssetFiles` factory and the log) to `assets.loading`.
      `PlacedEntities` and `toMatrix` stay in the plugin in `sceneview/PlacedEntities.kt`. Verify: `AssetCacheTest`
      moved to `core` with a recording `AssetLog` passes, including a new case `aFailedAssetIsLoggedOnce`, with
      `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.loading.*'`

## 4. Models and terrains

- [x] 4.1 Move `ModelLoader` / `PreparedModel` to `assets.model`. Verify: `./gradlew :core:test` and
      `./gradlew :compileKotlin` pass
- [x] 4.2 Move `TerrainLoader`, `TerrainData`, `TerrainMesh` to `assets.terrain`; the `TerrainData` companion
      factory becomes `TerrainDataReader`, `TerrainMesh.SPLAT_UNIT` a top-level `const val` the plugin's
      `TerrainShader` imports. Verify: `TerrainDataTest` moved to `core` passes with
      `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.terrain.*'`

## 5. Skies

- [x] 5.1 Move the six-face cube (`SkyboxCube`, `SkyboxLoader`, `SkyboxMeta`) and the procedural sky
      (`ProceduralSky`, its loader and meta, `AtmosphereParams` with default arguments instead of a companion) to
      `assets.sky.cube` / `assets.sky.procedural`; `skybox.vert/frag` move to `core/src/main/resources/shader/sky/`.
      `ProceduralSky.draw` takes a libGDX `Vector3` sun; `SceneSkybox` converts the plugin's `Vec3`. Verify:
      `SkyboxMetaTest`, `ProceduralSkyLoaderTest`, `ProceduralSkyFixtureTest`, `AtmosphereParamsTest` moved to `core`
      pass with `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.sky.*'`
- [x] 5.2 Move the HDR sky to `assets.sky.hdr` with the singletons replaced as design decision 3
      (`RadianceDecoder`, `HdrSkyFiles`, `EquirectProjection`, `ToneCurve`, `HdrPreview` as classes; header reading on
      `RadianceDecoder`; `const val` sizes); `hdr_*` and `hdrsky.*` shaders move to `shader/sky/`. `SkyboxChoices` and
      `PanelState` take the decoder and `HdrPreview` from `AbyssusCore`. Verify: `RadianceDecoderTest`,
      `HdrSkyFilesTest`, `EquirectTest`, `HdrToneMapTest` (as `ToneCurveTest`), `HdrSkyLoaderTest` moved to `core` pass
      with `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.sky.*'`; `HdrFixtures` moves to `core`'s
      `testFixtures` source set, which `core`'s tests and the plugin's (`SceneRenderGlTest`) depend on; and
      `./gradlew :test --tests 'net.nevinsky.abyssus.projectView.SkyboxChoicesTest' --tests
      'net.nevinsky.abyssus.properties.AssetPropertiesPanelTest'` shows no failure `HEAD` does not already have
- [x] 5.3 Move `SkyLoader` / `PreparedSky` to `assets.sky`, taking its three loaders through the constructor (no
      default arguments that build them). Share `gdx-model`'s `TestGl` AWT GL context with `core`'s
      tests through `gdx-model`'s `testFixtures`, and move `HdrEnvironmentGlTest` onto it. Verify:
      `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.sky.hdr.HdrEnvironmentGlTest' -Dabyssus.glTests=true`
      passes on a machine with a display (4 cases); without a display, record that and rely on 7.2

## 6. Wiring

- [x] 6.1 Add `AssetLoading(json, log, executor, shaders)` as in design decision 2. `AbyssusCore` builds one from an
      IntelliJ-logger `AssetLog`, `AppExecutorUtil` and `ShaderSource("/shader/sky")`. `SceneRenderer` takes an
      `AssetLoading` (no `SceneLoaders`, no default); `SceneModels` / `SceneTerrains` / `SceneSkybox` get their
      `SceneAssets` from it; `SceneViewPanel` passes `service<AbyssusCore>().loading`. Delete `SceneLoaders`. Verify:
      `./gradlew :compileKotlin :core:checkNoSingletons` pass and
      `grep -rn "service<JsonProcessor>\|JsonProcessor.of\|ProjectAssetFiles\|SceneLoaders" src/ core/` finds nothing
- [x] 6.2 Cover the `asset-loading` spec in `core` with `AssetLoadingGlTest` (opt-in GL), built with no IntelliJ class
      on the classpath: `mainScenesContentLoadsOutsideTheIde` (the three models and the terrain of `Main Scene`),
      `everySkyKindLoadsOutsideTheIde`, `aMissingFolderIsLoggedOnceToTheCallersLog`,
      `aBrokenAssetIsReportedOnceAcrossFrames`, `twoProjectsLoadIndependently` (`Untitled` and `Animated`, separate
      logs, disposing one leaves the other's assets usable). Verify:
      `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.AssetLoadingGlTest' -Dabyssus.glTests=true` passes,
      same no-display rule
- [x] 6.3 Make the plugin's GL harness build `AssetLoading` directly (recording log, direct shaders), so plugin GL
      tests load assets with no IntelliJ application. Verify:
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneRenderGlTest.mainSceneDrawsModelsAndTerrain' -Dabyssus.glTests=true`
      passes (it draws 0 models on `HEAD`); run the GL suite with no other Gradle build running at the same time

## 7. Documentation and checks

- [x] 7.1 Update `AGENTS.md` (layout: the `core` module; commands: `./gradlew :core:test`; hard rules: no singletons
      in `core`, `core` stays free of IntelliJ imports), `docs/ai/architecture.md` (modules, data flow, threading),
      `docs/ai/testing.md` (`core` tests, the fixture helper's new home), `sceneview/README.md` (what moved), and add
      `core/README.md` like `gdx-model/README.md`. Verify: `scripts/check-docs.sh` passes and no doc names a removed
      path
- [ ] 7.2 In a copy of the test project (never `src/test/testData/project/Untitled` itself), run `./gradlew runIde`
      and check: (1) `Main Scene` shows its models, terrain and procedural sky as before; (2) choosing `skybox_default`
      and an HDR sky in the chooser draws each, with the chooser's HDR thumbnail; (3) the properties panel shows a
      model's rows and an HDR preview; (4) renaming an asset folder a scene uses logs one warning in `idea.log` and
      the rest of the scene draws
- [x] 7.3 `./gradlew check` (both modules, `checkNoSingletons` included) and `scripts/check-docs.sh` pass. The five
      tests that `HEAD` already failed expected `Main Scene` to name `skybox_default`; the fixture names `skybox_physical`
      since `671e339`, which the user confirmed is intended, so `AbyssusViewTest`, `SceneContentTest`,
      `SkyboxChoicesTest` and `ProjectAssetsTest` now expect `skybox_physical` (and `skybox_default` unused)
