# AGENTS.md

Abyssus is an independent libGDX scene editor implemented as an IntelliJ Platform plugin. It shows a
project's `.abss` / `.scene` / asset files as a tree, the selected asset's `meta.json` in a properties panel, and a
scene in a libGDX-rendered view where objects can be selected, moved and rotated.

This file is a map. Detail lives in `docs/ai/`; start at `docs/README.md`.

## Commands

| What | Command |
|---|---|
| All module checks and tests | `./gradlew check` (CI adds `-Pabyssus.requireShaders=true`; verifier and Qodana run separately) |
| Plugin tests only | `./gradlew :test` |
| `gdx-model` tests only | `./gradlew :gdx-model:test` |
| Ray tracing tests / native jar packaging | `./gradlew :raytracing:test` / `./gradlew :raytracing:verifyNativePackaging` |
| `runtime` tests only | `./gradlew :runtime:test` |
| `physics` tests only | `./gradlew :physics:test` |
| `core` tests only | `./gradlew :core:test` (one class: `./gradlew :core:test --tests 'net.nevinsky.abyssus.lib.core.assets.loading.AssetStorageTest'`) |
| `editor-core` tests only | `./gradlew :editor-core:test` (one class: `./gradlew :editor-core:test --tests 'net.nevinsky.abyssus.lib.core.editor.pick.OrbitCameraTest'`) |
| One plugin test class | `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewPanelTest'` |
| Also run GL tests (open a window) | add `-Dabyssus.glTests=true` |
| Sandbox IDE | `./gradlew runIde` (open a project with `-PideProject=/path/to/project`) |
| Sandbox IDE with Abyssus Physics | `./gradlew :physics-plugin:runIde` |
| Abyssus Physics tests | `./gradlew :physics-plugin:test` |
| Control Line game: play / tests | `./gradlew :games:control-line:run` / `./gradlew :games:control-line:test` |
| Control Line component schema and play launch file | `./gradlew :games:control-line:exportAbyssus` |
| Plugin zip | `./gradlew buildPlugin` (to `build/distributions/`) |
| Docs path check | `scripts/check-docs.sh` |

Use `:test`, not `test`, with `--tests`: plain `test` also runs in the other modules and fails there with
"No tests found".

## Layout

- `src/main/kotlin/net/nevinsky/abyssus/`: the plugin (Kotlin), registered in `src/main/resources/META-INF/plugin.xml`.
  The plugin is IDE glue; the editing logic is in `editor-core`.
  - `dto/`: reads `.abss` / `.scene` files and asset folders through the VFS (`ProjectLayout`, `SceneReader`, `ProjectReader`, `ProjectAssetListing`).
  - `projectView/`: the Abyssus tree, eye toggles, Rename Scene and the skybox chooser. `filetype/` holds `editSceneJson`.
  - `properties/`: the Abyssus Properties tool window (Swing views over `editor-core`'s panel model).
  - `sceneview/`: the scene view: GL canvas, renderer, shadows, fog, sky and gizmo drawing, the toolbar, Play, and the
    view's asset storage (`ViewAssets`). Picking, cameras, gizmo math and transform write-back are in `editor-core`.
  - `schema/`: component schemas for game components: the `componentSchemas` extension point and the
    `ComponentSchemas` project service that builds each scene's `ComponentEditor`.
  - `terrain/`: the New Terrain dialog and the regeneration controls; generation itself is in `editor-core`.
  - `filetype/`, `language/`: file types, icons, the GLTF PSI.
- `editor-core/`: a plain JVM library, root package `net.nevinsky.abyssus.lib.core.editor`: scene documents (`SceneJson`,
  `SceneDocument`, `DocumentTextEditor`), component editing (`ComponentEditor`, `LightEntities`), the scene read model
  (`SceneContent`), picking and gizmo math, terrain generation, asset meta editing, the ray tracing bridge, and the
  headless editing API (`HeadlessEditing`). Ashley components, codecs and systems stay in `runtime`. See
  `editor-core/README.md`.
- `gdx-model/`: a plain JVM library (libGDX model runtime with 32-bit indices, Assimp import), with inherited sources documented in `docs/third-party/gdx-model-origin.md`.
  See `gdx-model/README.md`.
- `core/`: a plain JVM library, root package `net.nevinsky.abyssus.lib.core`: project layout and file access
  (`core.io.AbyssusProjectLayout`, `core.io.FileLoader`, `core.io.JsonProcessor`), asset metas (`AssetMeta`, `AssetMetaLoader`), the loading
  pipeline (`AssetLoader`, `CompositeAssetLoader`, `AssetStorage`), the optional CPU snapshots for ray tracing (`RaySnapshotStore`), and the
  loaders with the drawables they build (models, terrains, the cube, procedural and HDR skies, and the sky shaders).
  The plugin wires it in `AssetLoading` (root package). See `core/README.md`.
- `raytracing/`: plain JVM GPU ray tracing contracts, snapshots, scheduling, and optional Metal/Vulkan backends.
  The plugin owns view integration; native probing starts only when enabled. See `raytracing/README.md`.
- `runtime/`: plain JVM scene loading and Ashley components, codecs, systems, loader
  and writer. Game components (`@SceneComponent`, `ComponentRegistry`) and their schema export live in its `schema` package. `RuntimeSceneLoader` wires it by constructors. See `runtime/README.md`.
- `physics/`: a plain JVM library on `runtime`, root package `net.nevinsky.abyssus.physics`: the physics components
  (`PhysicsComponents`) and `PhysicsWorld`, which runs Jolt through jolt-jni (only its `jolt` package imports Jolt). See
  `physics/README.md`.
- `physics-plugin/`: **Abyssus Physics**, a second IntelliJ plugin that depends on Abyssus
  (`localPlugin(project(":"))`). It holds the physics overlay (`sceneOverlay`), Play through a play process
  (`sceneSimulation`), the physics schema generated at build time, and the bundled `play-host` folder.
- `games/control-line/`: **Control Line**, a libGDX desktop game (LWJGL3) on `runtime` and `physics` that proves the
  editor-for-games chain: its native project `games/control-line/project/ControlLine` is authored in Abyssus, its
  `PlaneComponent` / `PilotComponent` are game components, and its `PlayModule` flies a plane in Play. Open a copy of
  that project in the IDE, never the committed folder (its tests assert on the scene). See `games/control-line/README.md`.
- `src/main/java/`: only the grammar sources `Gltf.bnf` / `Gltf.flex`; `src/main/gen` is generated from them.
- `src/test/kotlin/`, `editor-core/src/test/kotlin/`, `gdx-model/src/test/kotlin/`, `core/src/test/kotlin/`,
  `runtime/src/test/kotlin/`, `physics/src/test/kotlin/`, `physics-plugin/src/test/kotlin/`,
  `raytracing/src/test/kotlin/`, `games/control-line/src/test/kotlin/`: tests. Fixtures in `src/test/testData/project/`
  (shared with `core`'s and `editor-core`'s tests). Test helpers shared across modules live in `testFixtures` source
  sets (`gdx-model`: `TestGl`; `core`: `HdrFixtures`; `editor-core`: `parseScene`, `testProject`, `rayTestModel`).
- `openspec/`: specs and changes (see Workflow). `docs/superpowers/`: one historic design and plan.

## Hard rules

- **Native documents only:** `.abss`, `.scene` and asset `meta.json` require `format: "abyssus"` and integral
  `formatVersion: 1`. Validate with `AbyssusDocumentFormat` before binding, enumerating, editing or formatting.
  Reject legacy `ecs.componentIdentifiers` and renderable `class`; a component entry is keyed by the class name of a built-in or registered component (fully qualified, or its short name), assets use `kind: "asset"`.
  Keep unknown native extension data, unrelated numbers/key order and default omission. No importer or implicit migration.

- **`physics` stays a plain JVM library wired by constructors**, like `runtime` (`./gradlew :physics:checkNoSingletons`
  is part of `check`). **Jolt never loads in the IDE process:** only a game or the play host calls `JoltNatives`.
- **Extension plugins bundle only their own code.** `physics-plugin` ships its jar and `physics.jar`; libGDX,
  `runtime`, `editor-core`, `core` and `gdx-model` come from Abyssus's classloader (`Gdx.*` is process-global). Its main sources may
  not name `com.github.stephengold` or `net.nevinsky.abyssus.physics.jolt` (`./gradlew :physics-plugin:checkNoJolt`,
  part of `check`).
- **Don't edit `src/main/gen`.** It is git-ignored and regenerated from `Gltf.bnf` / `Gltf.flex`.
- **`gdx-model` stays a plain JVM library**: no IntelliJ or plugin imports, so other libGDX projects can use it.
- **`core` stays a plain JVM library wired by constructors**: no IntelliJ or plugin imports, and no `object` or
  `companion object` in `core/src/main` (a `data object` case of a sealed type is fine). Pass collaborators in;
  `./gradlew :core:checkNoSingletons` (part of `check`) fails otherwise.
- **`runtime` stays a plain JVM library wired by constructors**: no IntelliJ or plugin imports, and no `object` or
  `companion object` in `runtime/src/main`. `./gradlew :runtime:checkNoSingletons` is part of `check`.
- **`editor-core` stays a plain JVM library wired by constructors**: no IntelliJ, Swing or AWT imports (its classpath
  has no platform artifact; `NoPlatformClasspathTest` checks), and no `object` or `companion object` with behavior in
  `editor-core/src/main` (`./gradlew :editor-core:checkNoSingletons`, part of `check`; pure constant holders are listed
  in its `abyssusSingletonExcludes`). Neither `core` nor `runtime` depends on it.
- **No package cycles:** `./gradlew checkPackageCycles` (part of `check`) fails on a cycle between the packages of a
  module; its allowlist `gradle/package-cycles.allowlist` stays empty.
- **Write scene files only through `editSceneJson`** (`src/main/kotlin/net/nevinsky/abyssus/filetype/SceneDocumentWriter.kt`).
  It edits the document as one undoable command and keeps the file's formatting and number text; the text transform
  itself is `editor-core`'s `DocumentTextEditor`, which `HeadlessEditing` uses without the IDE. The only other
  writer is `SceneFormatListener`, which pretty-prints a `.scene` / `.abss` opened in the text editor.
- **Never change numbers or key order you didn't mean to change.** Parse with `SceneJson`, which keeps both.
- **`Gdx.*` is process-global inside the IDE.** Run libGDX code only inside `GdxRuntime.withContext`, on the AWT
  thread that renders the canvas (a Swing timer drives frames). Only the `prepare` step of `AssetStorage` runs off that
  thread, and it must not touch GL. Picking and gizmo hit tests use CPU data and need no context.
- **Use GL only while the canvas is safely on screen** (`GuardedGLCanvas.glSafe`). On macOS a zero-sized surface
  aborts the JVM.
- **Catch with `runCatchingKeepingCancellation`**, not `runCatching`, around anything that may be cancelled
  (`./gradlew checkNoRunCatching`, part of `check`, fails otherwise).
- **User-facing text goes in `src/main/resources/messages/AbyssusBundle.properties`** via `AbyssusBundle.message`;
  text that `editor-core` produces goes in `editor-core/src/main/resources/messages/AbyssusEditorBundle.properties`,
  read through an injected `EditorMessages` (the plugin passes `EditorBundle`).
- **Keep the `<!-- Plugin description -->` markers in `README.md`.** `patchPluginXml` copies that block into the
  plugin and fails without them.
- **Don't use `src/test/testData/project/Untitled` as the `runIde` project.** Edits made in the IDE (gizmo drags,
  renames, toggles) change the fixture the tests assert on.

## Workflow

Non-trivial changes go through OpenSpec: propose (`openspec/changes/<name>/` with proposal, specs, design, tasks),
apply, then archive, which merges the change's specs into `openspec/specs/`. `openspec/specs/` is the source of
truth for required behavior; read the relevant capability before changing a feature. The workflow commands live in
`.agents/commands/` and `.claude/skills/`.

When a change makes a doc wrong, update the doc in the same change and run `scripts/check-docs.sh`.

## Docs

- `docs/ai/architecture.md`: modules, data flow, threading, extension points.
- `docs/ai/file-formats.md`: `.scene`, `.abss`, asset `meta.json`.
- `docs/ai/glossary.md`: Native Abyssus terms.
- `docs/ai/conventions.md`: code conventions.
- `docs/ai/testing.md`: test layout, fixtures, GL tests.
- Package and module notes: `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md`,
  `src/main/kotlin/net/nevinsky/abyssus/projectView/README.md`, `editor-core/README.md`.
