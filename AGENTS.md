# AGENTS.md

Abyssus is an independent libGDX scene editor implemented as an IntelliJ Platform plugin. It shows a
project's `.abss` / `.scene` / asset files as a tree, the selected asset's `meta.json` in a properties panel, and a
scene in a libGDX-rendered view where objects can be selected, moved and rotated.

This file is a map. Detail lives in `docs/ai/`; start at `docs/README.md`.

## Commands

| What | Command |
|---|---|
| All module checks and tests | `./gradlew check` (CI adds `-Pabyssus.requireShaders=true`; verifier and Qodana run separately) |
| Plugin tests only | `./gradlew :plugin-abyssus:test` |
| `gdx-model` tests only | `./gradlew :lib-gdx-model:test` |
| Ray tracing tests / native jar packaging | `./gradlew :lib-raytracing:test` / `./gradlew :lib-raytracing:verifyNativePackaging` |
| `runtime` tests only | `./gradlew :lib-runtime:test` |
| `physics` tests only | `./gradlew :lib-physics:test` |
| `core` tests only | `./gradlew :lib-core:test` (one class: `./gradlew :lib-core:test --tests 'net.nevinsky.abyssus.lib.gdx.assets.loading.AssetStorageTest'`) |
| `editor-core` tests only | `./gradlew :lib-core-editor:test` (one class: `./gradlew :lib-core-editor:test --tests 'net.nevinsky.abyssus.lib.gdx.editor.pick.OrbitCameraTest'`) |
| One plugin test class | `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewPanelTest'` |
| Also run GL tests (open a window) | add `-Dabyssus.glTests=true` |
| Sandbox IDE | `./gradlew :plugin-abyssus:runIde` (open a project with `-PideProject=/path/to/project`) |
| Sandbox IDE with Abyssus Physics | `./gradlew :plugin-abyssus-physics:runIde` |
| Abyssus Physics tests | `./gradlew :plugin-abyssus-physics:test` |
| Control Line game: play / tests | `./gradlew :app-game-control-line:run` / `./gradlew :app-game-control-line:test` |
| Control Line component schema and play launch file | `./gradlew :app-game-control-line:exportAbyssus` |
| Plugin zip | `./gradlew :plugin-abyssus:buildPlugin` (to `projects/plugin-abyssus/build/distributions/`) |
| Docs path check | `scripts/check-docs.sh` |

Use `:plugin-abyssus:test`, not `test`, with `--tests`: plain `test` also runs in the other modules and fails there with
"No tests found".

## Layout

- `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/`: the plugin (Kotlin), registered in `projects/plugin-abyssus/src/main/resources/META-INF/plugin.xml`.
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
- `projects/lib-core-editor/`: a plain JVM library, root package `net.nevinsky.abyssus.lib.gdx.editor`: scene documents (`SceneJson`,
  `SceneDocument`, `DocumentTextEditor`), component editing (`ComponentEditor`, `LightEntities`), the scene read model
  (`SceneContent`), picking and gizmo math, terrain generation, asset meta editing, the ray tracing bridge, and the
  headless editing API (`HeadlessEditing`), and the FlightGear and model imports that stage new MODEL asset folders
  (`flightgear`, `modelimport`). Ashley components, codecs and systems stay in `runtime`. See
  `projects/lib-core-editor/README.md`.
- `projects/lib-gdx`: a plain JVM library (libGDX model runtime with 32-bit indices, Assimp import, a binary glTF writer), with inherited sources documented in `docs/third-party/gdx-model-origin.md`.
  See `projects/lib-gdx`.
- `projects/lib-core/`: a plain JVM library, root package `net.nevinsky.abyssus.lib.gdx`: project layout and file access
  (`core.io.AbyssusProjectLayout`, `core.io.FileLoader`, `core.io.JsonProcessor`), asset metas (`AssetMeta`, `AssetMetaLoader`), the loading
  pipeline (`AssetLoader`, `AssetStorage`), the optional CPU snapshots for ray tracing (`RaySnapshotStore`), the
  loaders with the drawables they build (models, terrains, the cube, procedural and HDR skies, and the sky shaders).
  The plugin wires it in `AssetLoading` (root package). See `projects/lib-core/README.md`.
- `projects/lib-raytracing/`: plain JVM GPU ray tracing contracts, snapshots, scheduling, and optional Metal/Vulkan backends.
  The plugin owns view integration; native probing starts only when enabled. See `projects/lib-raytracing/README.md`.
- `projects/lib-runtime/`: plain JVM scene loading and Ashley components, codecs, systems, loader
  and writer. Game components (`@SceneComponent`, `ComponentRegistry`) and their schema export live in its `schema` package. `RuntimeSceneLoader` wires it by constructors. See `projects/lib-runtime/README.md`.
- `projects/lib-physics/`: a plain JVM library on `runtime`, root package `net.nevinsky.abyssus.lib.physics`: the physics components
  (`PhysicsComponents`) and `PhysicsWorld`, which runs Jolt through jolt-jni (only its `jolt` package imports Jolt). See
  `projects/lib-physics/README.md`.
- `projects/plugin-abyssus-physics/`: **Abyssus Physics**, a second IntelliJ plugin that depends on Abyssus
  (`localPlugin(project(":"))`). It holds the physics overlay (`sceneOverlay`), Play through a play process
  (`sceneSimulation`), the physics schema generated at build time, and the bundled `play-host` folder.
- `projects/app-game-control-line/`: **Control Line**, a libGDX desktop game (LWJGL3) on `runtime` and `physics` that proves the
  editor-for-games chain: its native project `projects/app-game-control-line/project/ControlLine` is authored in Abyssus, its
  `PlaneComponent` / `PilotComponent` are game components, and its `PlayModule` flies a plane in Play. Open a copy of
  that project in the IDE, never the committed folder (its tests assert on the scene). See `projects/app-game-control-line/README.md`.
- `projects/plugin-abyssus/src/main/java/`: only the grammar sources `Gltf.bnf` / `Gltf.flex`; `projects/plugin-abyssus/src/main/gen` is generated from them.
- `projects/plugin-abyssus/src/test/kotlin/`, `projects/lib-core-editor/src/test/kotlin/`, `projects/lib-gdx`, `projects/lib-core/src/test/kotlin/`,
  `projects/lib-runtime/src/test/kotlin/`, `projects/lib-physics/src/test/kotlin/`, `projects/plugin-abyssus-physics/src/test/kotlin/`,
  `projects/lib-raytracing/src/test/kotlin/`, `projects/app-game-control-line/src/test/kotlin/`: tests. Fixtures in `projects/plugin-abyssus/src/test/testData/project/`
  (shared with `core`'s and `editor-core`'s tests). Test helpers shared across modules live in `testFixtures` source
  sets (`gdx-model`: `TestGl`; `core`: `HdrFixtures`; `editor-core`: `parseScene`, `testProject`, `rayTestModel`).
- `openspec/`: specs and changes (see Workflow). `docs/superpowers/`: one historic design and plan.

## Hard rules

- **Native documents only:** `.abss`, `.scene` and asset `meta.json` require `format: "abyssus"` and integral
  `formatVersion: 1`. Validate with `AbyssusDocumentFormat` before binding, enumerating, editing or formatting.
  Reject legacy `ecs.componentIdentifiers` and renderable `class`; a component entry is keyed by the class name of a built-in or registered component (fully qualified, or its short name), assets use `kind: "asset"`.
  Keep unknown native extension data, unrelated numbers/key order and default omission. No importer or implicit migration.

- **`physics` stays a plain JVM library wired by constructors**, like `runtime` (`./gradlew :lib-physics:checkNoSingletons`
  is part of `check`). **Jolt never loads in the IDE process:** only a game or the play host calls `JoltNatives`.
- **Extension plugins bundle only their own code.** `physics-plugin` ships its jar and `physics.jar`; libGDX,
  `runtime`, `editor-core`, `core` and `gdx-model` come from Abyssus's classloader (`Gdx.*` is process-global). Its main sources may
  not name `com.github.stephengold` or `net.nevinsky.abyssus.physics.jolt` (`./gradlew :plugin-abyssus-physics:checkNoJolt`,
  part of `check`).
- **Don't edit `projects/plugin-abyssus/src/main/gen`.** It is git-ignored and regenerated from `Gltf.bnf` / `Gltf.flex`.
- **`gdx-model` stays a plain JVM library**: no IntelliJ or plugin imports, so other libGDX projects can use it.
- **`core` stays a plain JVM library wired by constructors**: no IntelliJ or plugin imports, and no `object` or
  `companion object` in `projects/lib-core/src/main` (a `data object` case of a sealed type is fine). Pass collaborators in;
  `./gradlew :lib-core:checkNoSingletons` (part of `check`) fails otherwise.
- **`runtime` stays a plain JVM library wired by constructors**: no IntelliJ or plugin imports, and no `object` or
  `companion object` in `projects/lib-runtime/src/main`. `./gradlew :lib-runtime:checkNoSingletons` is part of `check`.
- **`editor-core` stays a plain JVM library wired by constructors**: no IntelliJ, Swing or AWT imports (its classpath
  has no platform artifact; `NoPlatformClasspathTest` checks), and no `object` or `companion object` with behavior in
  `projects/lib-core-editor/src/main` (`./gradlew :lib-core-editor:checkNoSingletons`, part of `check`; pure constant holders are listed
  in its `abyssusSingletonExcludes`). Neither `core` nor `runtime` depends on it.
- **No package cycles:** `./gradlew checkPackageCycles` (part of `check`) fails on a cycle between the packages of a
  module; its allowlist `gradle/package-cycles.allowlist` stays empty.
- **Write scene files only through `editSceneJson`** (`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/filetype/SceneDocumentWriter.kt`).
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
- **User-facing text goes in `projects/plugin-abyssus/src/main/resources/messages/AbyssusBundle.properties`** via `AbyssusBundle.message`;
  text that `editor-core` produces goes in `projects/lib-core-editor/src/main/resources/messages/AbyssusEditorBundle.properties`,
  read through an injected `EditorMessages` (the plugin passes `EditorBundle`).
- **Keep the `<!-- Plugin description -->` markers in `README.md`.** `patchPluginXml` copies that block into the
  plugin and fails without them.
- **Don't use `projects/plugin-abyssus/src/test/testData/project/Untitled` as the `runIde` project.** Edits made in the IDE (gizmo drags,
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
- Package and module notes: `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md`,
  `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/projectView/README.md`, `projects/lib-core-editor/README.md`.
