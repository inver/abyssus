# Testing

## Running

| What | Command |
|---|---|
| All module tests and verification tasks | `./gradlew check` (CI requires compiled shaders; see `AGENTS.md`) |
| Core tests | `./gradlew :core:test` |
| Physics library / plugin tests | `./gradlew :physics:test` / `./gradlew :physics-plugin:test` |
| Ray tracing contracts / jar packaging | `./gradlew :raytracing:test` / `./gradlew :raytracing:verifyNativePackaging` |
| Control Line tests | `./gradlew :games:control-line:test` |
| Runtime tests | `./gradlew :runtime:test` |
| Plugin tests | `./gradlew :test` |
| `gdx-model` tests | `./gradlew :gdx-model:test` |
| One class | `./gradlew :test --tests 'net.nevinsky.abyssus.projectView.SkyboxPickerModelTest'` |
| One method | `./gradlew :test --tests 'net.nevinsky.abyssus.AbyssusViewTest.testNodeTree'` |
| GL tests too | add `-Dabyssus.glTests=true` (opens a window; needs a display) |

**Use `:test` with `--tests`.** Plain `test --tests X` also runs `:gdx-model:test` and `:core:test`, which fail with
"No tests found". For `core`, use `./gradlew :core:test --tests '<class>'`.
Results are in each module's `build/test-results/test/*.xml` and `build/reports/tests/test/`; a later run overwrites them.
CI also runs Plugin Verifier and Qodana separately from `check`.

## Layout

- `src/test/kotlin/net/nevinsky/abyssus/`: plugin tests, mirroring the main packages (`projectView/`, `sceneview/`,
  `ecs/`, `properties/`, `dto/`, `filetype/`).
- `gdx-model/src/test/kotlin/`: model runtime tests (`AssimpLoadingTest`, `PbrAttributesTest`, `LargeMeshGlTest`).
- `core/src/test/kotlin/`: asset reading and loading tests, plain JUnit with no IntelliJ classes. They read the shared
  fixtures through `testProject(name)` (Gradle passes the folder as `abyssus.testData`) and get a project's
  `FileLoader` and `AssetMetaLoader` from `testFileLoader(dir)` / `testMetaLoader(dir)` (`TestData.kt`); loaders and
  stores are wired by hand in each test. `exrFixture()` extracts the bundled EXR sky to a real file. `AssetStorageTest`
  covers the loading pipeline with a fake loader; `AssetLoadingGlTest` covers the `asset-loading` spec on real GL.
- `runtime/src/test/kotlin/`: scene loading, schemas, ECS codecs, components, loader, writer and
  systems. Plain JUnit, no IntelliJ or GL; `testProject(name)` uses the same `abyssus.testData` fixture root.
- `physics/src/test/kotlin/`: physics components, `PhysicsWorld`, rope tension and the Jolt natives. Plain JUnit, no
  IntelliJ or GL. They load the build machine's `DebugSp` Jolt natives, and use `testProject(name)` and
  `loadPhysicsScene()`.
- `games/control-line/src/test/kotlin/`: the Control Line game's flight, track, scoring, screen flow and Play module,
  headless (Jolt's `ReleaseSp` natives for the build machine, no window). They read the bundled project
  `games/control-line/project/ControlLine` through `bundledProject()` / `loadField()`, and fly it with `FieldFlight`;
  `ControlLinePlayTest` runs `PlayHostMain` in a child process.
- `physics-plugin/src/test/kotlin/`: overlay geometry, game/fallback launch selection and bundled play-host packaging.
- `raytracing/src/test/kotlin/`: backend contracts, fake backend, snapshots, scheduling, budgets, optics, accumulation
  and native packaging. Metal and Vulkan device tests opt in separately with `-Dabyssus.metalTests=true` and
  `-Dabyssus.vulkanTests=true`; these are not enabled by `abyssus.glTests`. See `raytracing/README.md` for toolchains
  and dedicated timing gates. Plugin ray integration tests live under `src/test/kotlin/`.
- Shared test helpers live in `testFixtures` source sets: `gdx-model`'s `TestGl` (a GL 3.2 core context for one
  block) and `core`'s `HdrFixtures` (Radiance files from a pixel function). Plugin GL tests build their renderer with
  `testRenderer()` (`sceneview/TestRendering.kt`), wired the way `AbyssusCore` wires it in the IDE.
- Plugin tests use JUnit 4 (`junit:junit:4.13.2`) and the IntelliJ test framework.

Two kinds of tests:

- **Platform tests:** these extend `BasePlatformTestCase` and need the VFS, documents, PSI or project services. Use
  `myFixture.addFileToProject` for inline files and `myFixture.copyFileToProject` for fixtures. They run on the EDT.
- **Plain JUnit tests:** plain classes for logic with no platform (`ScenePickerTest`, `SceneInteractionTest`,
  `StableGateTest`, `TerrainDataTest`, gizmo tests). Prefer this kind: keep new logic in a class that doesn't need
  Swing, GL or the platform.

## Fixtures

`src/test/testData/project/`:
- **`Untitled/`:** a native Abyssus project with `Untitled.abss` and `scenes/Main Scene.scene`. The scene has models,
  terrain, a skybox, directional lights and `Spot Light 8`, `Camera 4` looking at entity 3, and a parented entity. `assets/` holds 4 models, `tree`,
  a terrain, `skybox_default`, `skybox_physical` (a procedural sky) and `skybox_hdr` (an OpenEXR sky named by its
  metadata). `HdrFixtures` remains a helper for writing Radiance bytes; it does not describe the current EXR loader.
- **`Animated/`:** `scenes/Main.scene` with two entities sharing one animated model (`assets/model_anim`). It has no `.abss`.
- **`Custom/`:** game components. `scenes/Field.scene` has entity `0` (a plane, `"PlaneComponent": {"lineLength": 22,
  "kind": "STUNT"}`) and entity `1` (a pilot, no plane); `assets/tree` is copied from `Untitled`;
  `abyssus/components.schema.json` is the export of the test-only `PlaneComponent` in `runtime`'s tests (one field of
  each type), checked byte for byte by `SchemaFileTest`. Change the class and the file together.
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

**Don't edit fixtures through the IDE.** Opening `src/test/testData/project/Untitled` as the `runIde` project and
using the eye, Rename Scene, the skybox chooser or gizmo drags changes the files the tests assert on.
`AbyssusViewTest` reads the scene name from the file for this reason. A test that pins exact coordinates breaks when
an object was dragged.

## GL tests

- **Opt-in:** tests that open a GL window (`SceneRenderGlTest`, `LargeMeshGlTest`, `core`'s `*GlTest`) run only with
  `-Dabyssus.glTests=true` on a machine with a display. Otherwise `GlHarness.enabled` is false and they are skipped
  (counted as skipped, not failed).
- **Harness:** `GlHarness` (`src/test/kotlin/net/nevinsky/abyssus/sceneview/GlHarness.kt`) renders a few frames on
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
  `SceneMarkers`, `ScenePreview` and `sceneview/gizmo/` are plain math.
- **Dialogs:** `SkyboxChooserDialog` can be built in a platform test and driven through its internal test hooks;
  `SkyboxPickerModel` holds its logic.
- **Properties panel:** `PanelState`, `readAssetState` and `readEntityState` hold its logic without Swing. Entity editors are
  found by component name in `EntityPropertiesPanelTest` (`field-<Kind>-<field>`, `remove-<Kind>`, `add-component`).
- **Design screenshots:** `DesignScreenshotTest` paints the tree, the properties panel states, the skybox chooser rows and
  an icon sheet to `build/screenshots/*.png`, and writes what differs from the design canvas ("Abyssus Panel Design": icon
  colours, chooser row height) to `build/screenshots/<name>-diff.txt`. The design is HTML, so pixels are not compared. The test
  fails on a difference only with `-Dabyssus.designStrict=true`.
- **Component edits:** `ComponentEditorTest` runs on JSON trees; `SceneComponentEditsTest` checks the undoable writes;
  `ComponentActionsTest` subclasses the tree actions to supply the selected node.
