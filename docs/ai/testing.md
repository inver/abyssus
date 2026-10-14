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

## UI tests (Remote Robot)

`projects/plugin-abyssus/src/uiTest/kotlin` drives an installed Abyssus in a real sandbox IDE through the JetBrains
Remote Robot client (version pinned to the robot-server plugin, `remoteRobotVersion` in `plugin-abyssus.gradle.kts`).
It is a separate source set and `:plugin-abyssus:uiTest` task: the client is a plain JVM process with no platform
classpath, and `check` never runs it. Nothing under `uiTest` ships in the plugin.

| What | Command |
|---|---|
| Complete run in the canonical Linux/Xvfb container | `scripts/run-ui-tests-docker.sh [--tests '*UiStartupTest']` |
| Complete run on the host (needs a usable desktop) | `scripts/run-ui-tests.sh [--tests <pattern>]` |
| Headless helper tests only (`...uitesting.headless`), part of `check` | `./gradlew :plugin-abyssus:uiHelperTest` |
| Attach to an IDE you started | `./gradlew :plugin-abyssus:uiTest -PuiProject=<copy of Untitled> -PuiRobotUrl=http://127.0.0.1:8082` |

- **The runner** (`scripts/run-ui-tests.sh`) copies `src/test/testData/project/Untitled` (without `.idea`) into a temp
  directory, starts `runIdeForUiTests` against it with an isolated sandbox (`-PuiSandbox`) and robot-server port
  (`-PuiPort`, `ABYSSUS_UI_PORT`, default 8082), polls until robot-server answers, runs `uiTest`, then stops the IDE it
  started (matched by its unique sandbox path) and copies the IDE logs to `projects/plugin-abyssus/build/reports/ui-tests/ide-logs`.
  One run per port: a lock directory in the temp folder refuses a second. It fails if the committed fixture's hashes changed.
- **The container** (`docker/ui-tests`) is Ubuntu 24.04 with Xvfb 1920x1080x24, a minimal window manager, UTC and an
  English locale. `scripts/run-ui-tests-docker.sh` copies the working tree in (host build directories are never shared),
  keeps the Gradle cache and the downloaded IDE in the volume `abyssus-ui-gradle` (the first run downloads gigabytes and
  takes about an hour; later runs about a minute), and returns reports to `projects/plugin-abyssus/build/reports/ui-tests`.
  `ABYSSUS_UI_PLATFORM=linux/amd64` selects the platform. Pixel baselines must come from the canonical platform.
- **On the host (macOS)** the sandbox IDE never becomes the active window and Remote Robot screenshots are blank, so
  mouse-driven scenarios and captures do not work there. Use the container.
- **Failures** write `failures/<Class>.<method>/{screen.png,hierarchy.html,failure.txt}` under the reports folder; the
  hierarchy is robot-server's component page, useful for finding locators (the tree is `EyeTree`, the view selector's
  list is `MyList`).
- **Troubleshooting:** a leftover lock (`abyssus-ui-tests-<port>.lock` in the temp folder) after a killed run can be
  removed by hand; `ABYSSUS_UI_KEEP_WORK=1` keeps the temp project and sandbox; a changed `Untitled` copy never reaches
  the committed fixture.
- **Rules for tests:** mutate only through the UI; the helpers (`IdeQueries`) only read IDE state. `FixtureProject` copies
  and restores the disposable project on disk, which the IDE then refreshes. Locate components by Swing name or class and
  re-resolve them after any refresh; add production component names only when that is not enough.

### UI tests in CI

`.github/workflows/run-ui-tests.yml` runs `scripts/run-ui-tests-docker.sh` on `ubuntu-24.04` for pull requests and manual
dispatch, separately from the headless `check` in `build.yml`. `CI=true` is forwarded into the container, so a baseline update
request fails there. A run that discovers no UI test fails (`failOnNoDiscoveredTests`). The job always uploads
`projects/plugin-abyssus/build/reports/ui-tests/` as the `ui-test-diagnostics` artifact (failure screenshots, component
hierarchies, expected/actual/diff images, IDE logs and the JUnit report); download it from the run's summary page.

### UI screenshot baselines

Fifteen Swing captures (the tree, the Properties panels and two dialogs) are compared pixel for pixel with PNGs in
`projects/plugin-abyssus/src/uiTest/resources/screenshots/linux/`, named `<scenario>.png`. The scenario list is
`SCREENSHOT_SCENARIOS` (`ScreenshotInventory.kt`); `BaselineInventoryTest` fails on a missing or orphaned file. Captures are
component images (the tool window or dialog), never the 3D viewport or the whole desktop.

- **Canonical environment:** `docker/ui-tests` on the amd64 platform (Ubuntu 24.04, Xvfb 1920x1080x24, the IDE's own JBR 21 and Inter
  font, Dark theme, UI scale 1, English, UTC). `environment.properties` in the baselines folder records it, including font and
  X packages; before any comparison or update the running IDE is checked against it and a difference fails with the key, expected
  and found value. Nothing is tolerated or masked. Reproduce with
  `ABYSSUS_UI_PLATFORM=linux/amd64 scripts/run-ui-tests-docker.sh` (an arm64 image cannot show HDR previews).
- **Readiness:** a capture is taken only after the scenario waited for the content (real previews, loaded chooser thumbnails, the
  tree rows), the layout is normalised (`IdeFrame.normalizeLayout`: frame, Project and Properties widths, no blinking carets), the
  pointer is moved away and the focus is on the tree (dialogs keep their own focus), and two consecutive captures are identical.
- **Modes:** `-PuiScreenshots=compare` (default), `off` (interaction only: captures are kept as diagnostics, nothing is
  compared; for hosts that are not the canonical environment) and update. A run outside the canonical environment in compare mode
  fails explicitly rather than skipping.
- **Update one or more baselines** (never on CI, only in the canonical environment, only the named scenarios, only after the
  scenario's own checks passed; the PNGs are copied back to the working tree):
  `ABYSSUS_UI_PLATFORM=linux/amd64 scripts/run-ui-tests-docker.sh -- -PupdateUiScreenshots=true -PuiScreenshotScenarios=props-empty,tree-expanded`.
  Review every changed PNG by eye (content, loaded previews, nothing clipped or incidental) before committing, and commit the
  environment manifest with them when the environment changed.
- **On a failure** `build/reports/ui-tests/screenshots/<scenario>/` holds actual, expected and diff PNGs (changed pixels in
  red over a dimmed copy) and a report text file with the changed-pixel count, bounds and the environment.
- **Known layout facts the baselines record:** the entity panel's fields run past the Properties window's right edge at any width
  (it keeps a horizontal scrollbar), and the skybox chooser clips the `unused` badge and `used by 1 scene` at its default width.
  `DesignScreenshotTest` stays a separate design diagnostic and compares no pixels.

### UI test scenarios

Each scenario drives the installed plugin with the mouse and keyboard on a disposable copy of `Untitled` (restored before
every test), checks disk bytes, and where it edits, presses Undo and checks the bytes come back.

| Class | Scenarios |
|---|---|
| `UiStartupTest` | robot connection and project loaded; Abyssus installed, no scene editor open; Properties window opens and closes; Abyssus tree, entity 0 and the Camera component of entity 4 are found; fixture reset restores the IDE document and the tree |
| `AbyssusTreeUiTest` | Project, Scenes, Assets and ECS expand; the fog eye toggles only `fogEnabled` and Undo restores the bytes; switching views does not duplicate nodes |
| `PropertiesUiTest` | empty state; model and terrain assets; cube (editable, previews loaded), procedural (editable) and HDR (read-only, preview loaded) skyboxes; entity 0 and Camera fields; entity name edit and Undo; an invalid Camera number shows a field error and leaves the scene unedited |
| `RenameSceneUiTest` | Rename Scene opens from the scene's context menu; Cancel is byte-identical; applying changes only `name`, refreshes the tree, and Undo restores the bytes |
| `SkyboxChooserUiTest` | Choose opens the chooser with None and the three fixture skyboxes; the `hdr` filter and the no-match state; Cancel is byte-identical; Assign `skybox_default` changes only `skyboxName`, leaves `skyboxEnabled`, updates the tree, and Undo restores the bytes |
| `...uitesting.headless.*` | helper unit tests (`FixtureIsolationTest`), run by `uiHelperTest` |

Out of scope: the 3D viewport (GL under Xvfb is not deterministic), gizmos, the Game UI editor, the Asset Library, and
add/remove component and terrain creation workflows.

The Abyssus tree is refreshed by the plugin's own edits (`AbyssusSceneEdited`), not by Undo or by changes made outside the IDE, so scenarios assert Undo through the document and disk bytes, and each test starts with a Project view refresh and the view shown again (the tree otherwise keeps a name that Undo has already reverted).

Undo is tied to the scene document, not the project tree: a scenario that Undoes first opens the scene file in the plain text
editor (`IdeQueries.openTextEditor`, navigation only) and presses the IDE's Undo shortcut there.

The HDR preview needs the `natives-linux` (x86_64) LWJGL natives, so on an arm64 container it shows "Cannot read sky.exr" and the
HDR checks fail; run those with `ABYSSUS_UI_PLATFORM=linux/amd64`.

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
  `loadPhysicsScene()`. Foliage collision tests write a temporary project with `FoliageProject` (a flat 100 m terrain, two
  models and a foliage asset with a matching or stale bake) instead of copying a committed fixture:
  `PhysicsFoliageTest` (settling box, imported DETAIL collider, invalid collider, moving owner, stale/missing bake),
  `PhysicsFoliageLifecycleTest` (owner removal, injected layer failure, 100 worlds, the copy cap), `ShapeFactoryTest`
  (compound sub-shape poses and sizes) and `PlayHostMainTest` (a box settling for 5 s of simulated time on a foliage
  collider in the child play process). The pure parts are `FoliageColliderTransformsTest` and `FoliageAdmissionTest`.
- `projects/app-game-control-line/toolsTest/`: the field foliage migration (`./gradlew :app-game-control-line:testFieldFoliageTools`,
  part of `check`). It starts from `projects/app-game-control-line/toolsTest/fixtures/Field.original.scene`, the scene from before the migration, because
  the committed one has no vegetation left; it checks reruns from the original and the migrated project, refused conflicting
  edits, and that the committed assets are what the tool makes of the original. The tool compiles through its own
  `fieldFoliageTools` source set (`compileFieldFoliageToolsKotlin`); the older `tools` source set is compiled separately and
  has stale imports of its own (`FieldAssets.kt`), which this change leaves alone.
- `projects/app-game-control-line/src/test/kotlin/`: the Control Line game's flight, track, scoring, F2B pattern judging (`pattern`: synthetic flights built from the ideal tracks by `SyntheticFlight` / `SyntheticSchedule`, plus a scripted pilot), screen flow and Play module,
  headless (Jolt's `ReleaseSp` natives for the build machine, no window). They read the bundled project
  `projects/app-game-control-line/project/ControlLine` through `bundledProject()` / `loadField()`, and fly it with `FieldFlight`;
  `ControlLinePlayTest` runs `PlayHostMain` in a child process. `CrashClipGlTest`, `GameUiGlTest` (every authored screen lays out and draws) and `GameScreensCharacterizationTest` (the screens' text against a golden file) are the GL tests there (opt-in); `ControlLineUiAssetTest` and `GameUiHostTest` check the `screens` asset against the game's contract without GL.
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

- **Opt-in:** tests that open a GL window (`SceneRenderGlTest`, `ModelPreviewCanvasGlTest`, `LargeMeshGlTest`, `lib-core`'s `*GlTest`, Control Line's `CrashClipGlTest` and `GameUiGlTest`) run only with
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
