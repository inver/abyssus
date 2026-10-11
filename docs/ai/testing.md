# Testing

## Running

| What | Command |
|---|---|
| All module tests and verification tasks | `./gradlew check` (CI requires compiled shaders; see `AGENTS.md`) |
| Core tests | `./gradlew :lib-core:test` |
| Physics library / plugin tests | `./gradlew :lib-physics:test` / `./gradlew :plugin-abyssus:test` |
| Ray tracing contracts / jar packaging | `./gradlew :lib-raytracing:test` / `./gradlew :lib-raytracing:verifyNativePackaging` |
| Control Line tests | `./gradlew :app-game-control-line:test` |
| Plugin tests | `./gradlew :plugin-abyssus:test` |
| `lib-core-editor` tests | `./gradlew :lib-core-editor:test` (one class: `--tests 'net.nevinsky.abyssus.lib.core.editor.pick.ScenePickerTest'`) |
| `lib-gdx` tests | `./gradlew :lib-gdx:test` |
| One class | `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.SkyboxPickerModelTest'` |
| One method | `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.AbyssusViewTest.testNodeTree'` |
| GL tests too | add `-Dabyssus.glTests=true` (opens a window; needs a display) |

**Use `:plugin-abyssus:test` with `--tests`.** Plain `test --tests X` also runs the other modules' tests, which fail with
"No tests found". For a library module, name it: `./gradlew :lib-core:test --tests '<class>'`.

**After moving classes between packages or modules, build clean** (`./gradlew :plugin-abyssus:clean :lib-core-editor:clean`): stale
classes of the old packages otherwise fail tests with `NoSuchMethodError` / `NoClassDefFoundError`.
Results are in each module's `build/test-results/test/*.xml` and `build/reports/tests/test/`; a later run overwrites them.
CI also runs Plugin Verifier and Qodana separately from `check`.

## Coverage

Kover generates HTML and XML coverage reports for every module and a combined report at the root during
`./gradlew check`. The combined report includes library code exercised by tests in other modules.

| What | Command |
|---|---|
| Combined HTML and XML reports | `./gradlew :koverHtmlReport :koverXmlReport` |
| One module's HTML and XML reports | `./gradlew :lib-core:koverHtmlReport :lib-core:koverXmlReport` |

Report tasks run the required tests automatically. Combined reports are in `build/reports/kover/html/index.html`
and `build/reports/kover/report.xml`; module reports use the same paths under `projects/<module>/build/`.
GL and native backend tests retain their usual opt-in flags. Control Line's asset generator source sets
(`tools` and `importers`) are excluded from coverage.

## Layout

- `projects/plugin-abyssus/src/test/kotlin/net/nevinsky/abyssus/plugin/`: plugin tests, mirroring the main packages (`projectView/`, `sceneview/`,
  `properties/`, `dto/`, `filetype/`, `terrain/`), plus `editor/`: `EditorMessagesParityTest` and
  `HeadlessSceneEditingTest`, which compares `lib-core-editor`'s headless edits with the plugin's own write path.
- `projects/lib-core-editor/src/test/kotlin/`: the editing engine's tests by package (`document`, `components`, `scene`, `pick`,
  `terrain`, `meta`, `ray`, `headless`), plain JUnit with no IntelliJ class on the classpath (`NoPlatformClasspathTest`).
  Their working directory is `projects/plugin-abyssus/`, so `src/test/testData/project/` resolves to the same
  fixtures as the plugin tests.
- `projects/lib-gdx`: model runtime tests (`AssimpLoadingTest`, `PbrAttributesTest`, `LargeMeshGlTest`).
- `projects/lib-core/src/test/kotlin/`: asset reading and loading tests, plain JUnit with no IntelliJ classes. They read the shared
  fixtures through `testProject(name)` (Gradle passes the folder as `abyssus.testData`) and get a project's
  `FileLoader` and `AssetMetaLoader` from `testFileLoader(dir)` / `testMetaLoader(dir)` (`TestData.kt`); loaders and
  stores are wired by hand in each test. `exrFixture()` extracts the bundled EXR sky to a real file. `AssetStorageTest`
  covers the loading pipeline with a fake loader; `AssetLoadingGlTest` covers the `asset-loading` spec on real GL.
- Scene loading, Ashley component registration, `EcsLoader` and systems are tested in `projects/lib-core/src/test/kotlin/`.
  Component editing and `EcsWriter` are tested in `projects/lib-core-editor/src/test/kotlin/`; there is no separate runtime module.
- `projects/lib-physics/src/test/kotlin/`: physics components, `PhysicsWorld`, rope tension and the Jolt natives. Plain JUnit, no
  IntelliJ or GL. They load the build machine's `DebugSp` Jolt natives, and use `testProject(name)` and
  `loadPhysicsScene()`.
- `projects/app-game-control-line/src/test/kotlin/`: the Control Line game's flight, track, scoring, screen flow and Play module,
  headless (Jolt's `ReleaseSp` natives for the build machine, no window). They read the bundled project
  `projects/app-game-control-line/project/ControlLine` through `bundledProject()` / `loadField()`, and fly it with `FieldFlight`;
  `ControlLinePlayTest` runs `PlayHostMain` in a child process. `CrashClipGlTest` is the one GL test there (opt-in).
- `projects/plugin-abyssus/src/test/kotlin/net/nevinsky/abyssus/plugin/physics/`: overlay geometry, game/fallback launch selection and bundled play-host packaging.
- `projects/lib-raytracing/src/test/kotlin/`: backend contracts, fake backend, snapshots, scheduling, budgets, optics, accumulation
  and native packaging. Metal and Vulkan device tests opt in separately with `-Dabyssus.metalTests=true` and
  `-Dabyssus.vulkanTests=true`; these are not enabled by `abyssus.glTests`. See `projects/lib-raytracing/README.md` for toolchains
  and dedicated timing gates. Plugin ray integration tests live under `projects/plugin-abyssus/src/test/kotlin/`.
- Shared test helpers live in `testFixtures` source sets: `lib-gdx`'s `TestGl` (a GL 3.2 core context for one
  block), `lib-core`'s `HdrFixtures` (Radiance files from a pixel function) and `lib-core-editor`'s `parseScene`,
  `testProject`, `testAsset`, `terrainData` and `rayTestModel` (used by the plugin's tests too). Plugin GL tests build their renderer with
  `testRenderer()` (`sceneview/TestRendering.kt`), wired the way `AbyssusCore` wires it in the IDE.
- Plugin tests use JUnit 4 (`junit:junit:4.13.2`) and the IntelliJ test framework.

Two kinds of tests:

- **Platform tests:** these extend `BasePlatformTestCase` and need the VFS, documents, PSI or project services. Use
  `myFixture.addFileToProject` for inline files and `myFixture.copyFileToProject` for fixtures. They run on the EDT.
- **Plain JUnit tests:** plain classes for logic with no platform (`lib-core-editor`'s tests, and in the plugin
  `SceneInteractionTest`, `StableGateTest`, `SceneToolbarStateTest`). Prefer this kind: keep new logic in
  `lib-core-editor`, in a class that doesn't need Swing, GL or the platform.

## Fixtures

`projects/plugin-abyssus/src/test/testData/project/`:
- **`Tree/`:** a stable nine-entity native scene snapshot, its project document and nine asset metadata folders,
  without binary payloads. Tree, asset-listing and runtime ECS/scene suites use it to pin identities and counts
  independently of `Untitled`. The real-ray regression uses this scene with Untitled's binary assets.
- **`Untitled/`:** a native Abyssus project with `Untitled.abss` and `scenes/Main Scene.scene`. The scene has models,
  terrain, a skybox, directional lights and `Spot Light 8`, `Camera 4` looking at entity 3, and a parented entity. `assets/` holds 4 models, `tree`,
  a terrain, `skybox_default`, `skybox_physical` (a procedural sky) and `skybox_hdr` (an OpenEXR sky named by its
  metadata). `HdrFixtures` remains a helper for writing Radiance bytes; it does not describe the current EXR loader.
- **`Animated/`:** `scenes/Main.scene` with two entities sharing one animated model (`assets/model_anim`). It has no `.abss`.
- **`Foliage/`:** the foliage chain: the `Untitled` heights as a terrain, `tree`, one model, and `foliage_meadow` with an
  `OBJECT` layer of `tree`, a `DETAIL` layer of the model, a painted `layer-1.mask` and a committed `foliage.data`
  generated from those inputs. `Main Scene` has the terrain as entity `1` and the model as entity `2`, neither naming
  the foliage asset, so tests can Add Foliage and undo. `FoliageFixtureTest` (`lib-core`) fails when the bake goes stale:
  regenerate it after changing the meta, the mask or the terrain (see the fixture's README at
  `projects/plugin-abyssus/src/test/testData/project/Foliage/README.md`).
- **`Custom/`:** game components. `scenes/Field.scene` has entity `0` (a plane, `"PlaneComponent": {"lineLength": 22,
  "kind": "STUNT"}`) and entity `1` (a pilot, no plane); `assets/tree` is copied from `Untitled`;
  `abyssus/components.schema.json` is retained schema fixture data. The current editor does not consume it;
  tests verify that custom components remain read-only and survive built-in edits.
- **`Physics/`:** a copy of `Untitled` whose `Main Scene` gives `Model 0` (at Y `3.086434`) a dynamic box, `Terrain`
  a height field (all heights `0`) and `Model 2` a static sphere and a 3 m rope to `Model 0`. See its `README.md`.

**Size limit:** the binary assets exceed the test VFS size limit, so platform tests copy only what they need (the
`.abss`, a scene, one `meta.json`). See the `fixture()` helper in `AbyssusViewTest`.

**Native fixtures and rejection inputs.** Every fixture project, scene and `meta.json` carries
`"format":"abyssus","formatVersion":1`, has no `componentIdentifiers`, and uses `kind: "asset"` renderables plus inert
editor-marker kinds (`camera-marker`, `direction-handle-marker`, `direction-line-marker`, `light-marker`). Legacy,
unmarked and future-version documents are written inline in `AbyssusDocumentFormatTest`, `NativeDocumentReadTest`,
`NativeDocumentWriteGuardTest`, so rejection cases need not sit beside native fixtures. Editor tests that
assert a rejection also assert the document text and disk bytes are unchanged.

**Don't edit fixtures through the IDE.** Opening `projects/plugin-abyssus/src/test/testData/project/Untitled` as the `runIde` project and
using the eye, Rename Scene, the skybox chooser or gizmo drags changes the files the tests assert on.
`AbyssusViewTest` and runtime scene regressions use the stable `Tree` snapshot. Other tests that pin Untitled's
coordinates or asset counts still break when that project is edited; use a disposable copy for interactive work.

HDR preview platform tests require real filesystem-backed EXR files and TinyEXR's native binaries. The plugin build
adds those classifiers with `testRuntimeOnly`; this test setup does not add them to plugin packaging. Undo tests
clear only fixture-creation history before the first panel edit, then retain exact Undo/Redo assertions.

## GL tests

- **Opt-in:** tests that open a GL window (`SceneRenderGlTest`, `ModelPreviewCanvasGlTest`, `LargeMeshGlTest`, `lib-core`'s `*GlTest`, Control Line's `CrashClipGlTest`) run only with
  `-Dabyssus.glTests=true` on a machine with a display. Otherwise `GlHarness.enabled` is false and they are skipped
  (counted as skipped, not failed).
- **Harness:** `GlHarness` (`projects/plugin-abyssus/src/test/kotlin/net/nevinsky/abyssus/plugin/sceneview/GlHarness.kt`) renders a few frames on
  an AWT GL canvas inside `GdxRuntime.withContext` and runs checks after each frame.

## GL-free seams

- **`SceneView` interface:** `SceneFileEditor` takes a `viewFactory`, so tests pass a fake view and check the params
  it receives and the edits it triggers (`SceneFileEditorTest`).
- **`SceneParamsSource`:** this is how the editor reads a scene; tests can supply their own.
- **Drop:** `ScenePickerTest` covers rotated footprints, box support, transformed bilinear terrain maxima and
  tolerance; `SceneInteractionTest` covers availability, loading events, previews and Y-only edits without GL.
  `SceneTransformWriterTest` and `SceneFileEditorTest` verify preserved number text, one Move Entity command and Undo.
  `SceneRenderGlTest` covers loaded bounds, fixture resting heights and drawn-list versions on real GL. Toolbar/key
  focus and the complete drop interaction still need the sandbox IDE check in the OpenSpec change.
- **Picking and gizmos:** `SceneRenderer.pick` and the gizmo hit tests use CPU data only. `ScenePicker`,
  `SceneMarkers`, `ScenePreview` and the gizmo math are plain `lib-core-editor` code (its pick package).
- **Dialogs:** `SkyboxChooserDialog` can be built in a platform test and driven through its internal test hooks;
  `SkyboxPickerModel` holds its logic.
- **Properties panel:** `PanelState`, `readAssetState` and `readEntityState` hold its logic without Swing; the parts
  without IDE types (`readEntitySections`, `readRenderOptics`, `assetFieldStates`, `detailRows`) are in `lib-core-editor`. Entity editors are
  found by component name in `EntityPropertiesPanelTest` (`field-<Kind>-<field>`, `remove-<Kind>`, `add-component`).
- **Design screenshots:** `DesignScreenshotTest` paints the tree, the properties panel states, the skybox chooser rows and
  an icon sheet to `build/screenshots/*.png`, and writes what differs from the design canvas ("Abyssus Panel Design": icon
  colours, chooser row height) to `build/screenshots/<name>-diff.txt`. The design is HTML, so pixels are not compared. The test
  fails on a difference only with `-Dabyssus.designStrict=true`.
- **Component edits:** `ComponentEditorTest` (`lib-core-editor`) runs on JSON trees; `SceneComponentEditsTest` checks the undoable writes;
  `ComponentActionsTest` subclasses the tree actions to supply the selected node.
