# AGENTS.md

Abyssus is an IntelliJ Platform plugin for [Mundus](https://github.com/mbrlabs/Mundus) game projects. It shows a
project's `.abss` / `.scene` / asset files as a tree, the selected asset's `meta.json` in a properties panel, and a
scene in a libGDX-rendered view where objects can be selected, moved and rotated.

This file is a map. Detail lives in `docs/ai/`; start at `docs/README.md`.

## Commands

| What | Command |
|---|---|
| Everything CI runs (tests, verification) | `./gradlew check` |
| Plugin tests only | `./gradlew :test` |
| `gdx-model` tests only | `./gradlew :gdx-model:test` |
| `core` tests only | `./gradlew :core:test` (one class: `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.files.AssetFilesTest'`) |
| One test class | `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.OrbitCameraTest'` |
| Also run GL tests (open a window) | add `-Dabyssus.glTests=true` |
| Sandbox IDE | `./gradlew runIde` (open a project with `-PideProject=/path/to/project`) |
| Plugin zip | `./gradlew buildPlugin` (to `build/distributions/`) |
| Docs path check | `scripts/check-docs.sh` |

Use `:test`, not `test`, with `--tests`: plain `test` also runs in `gdx-model` and `core` and fails there with
"No tests found".

## Layout

- `src/main/kotlin/net/nevinsky/abyssus/`: the plugin (Kotlin), registered in `src/main/resources/META-INF/plugin.xml`.
  - `dto/`: reads `.abss` / `.scene` files and asset folders (`ProjectLayout`, `AssetReader`, `ProjectAssets`).
  - `scene/`: the scene DTOs (`SceneDto`, fog, lights).
  - `projectView/`: the Abyssus tree, eye toggles, Rename Scene and the skybox chooser. `filetype/` holds `editSceneJson`.
  - `properties/`: the Abyssus Properties tool window.
  - `sceneview/`: the scene view: GL canvas, renderer, picking, cameras, gizmos, transform write-back. Asset loading
    itself is in `core`.
  - `ecs/`: Ashley components, systems and a scene ECS loader/writer. Only tests use those; `ecs/scene/ComponentEditor.kt`
    (add, update and remove a component in the scene JSON) is the part the plugin uses.
  - `filetype/`, `language/`: file types, icons, scene JSON, the GLTF PSI.
- `gdx-model/`: a plain JVM library (libGDX model runtime with 32-bit indices, Assimp import), forked from Mundus.
  See `gdx-model/README.md`.
- `core/`: a plain JVM library, root package `net.nevinsky.abyssus.assets`: asset folders and `meta.json`
  (`AssetFiles`, `JsonProcessor`), the loading pipeline (`AssetLoader`, `AssetCache`, `SceneAssets`), and the loaders
  with the drawables they build (models, terrains, the cube, procedural and HDR skies, and the sky shaders).
  `AssetLoading` wires it; the plugin builds one in `AbyssusCore`. See `core/README.md`.
- `src/main/java/`: only the grammar sources `Gltf.bnf` / `Gltf.flex`; `src/main/gen` is generated from them.
- `src/test/kotlin/`, `gdx-model/src/test/kotlin/`, `core/src/test/kotlin/`: tests. Fixtures in `src/test/testData/project/`
  (shared with `core`'s tests). Test helpers shared across modules live in `testFixtures` source sets
  (`gdx-model`: `TestGl`; `core`: `HdrFixtures`).
- `openspec/`: specs and changes (see Workflow). `docs/superpowers/`: one historic design and plan.

## Hard rules

- **Don't edit `src/main/gen`.** It is git-ignored and regenerated from `Gltf.bnf` / `Gltf.flex`.
- **`gdx-model` stays a plain JVM library**: no IntelliJ or plugin imports, so other libGDX projects can use it.
- **`core` stays a plain JVM library wired by constructors**: no IntelliJ or plugin imports, and no `object` or
  `companion object` in `core/src/main` (a `data object` case of a sealed type is fine). Pass collaborators in;
  `./gradlew :core:checkNoSingletons` (part of `check`) fails otherwise.
- **Write scene files only through `editSceneJson`** (`src/main/kotlin/net/nevinsky/abyssus/filetype/SceneDocumentWriter.kt`).
  It edits the document as one undoable command and keeps the file's formatting and number text. The only other
  writer is `SceneFormatListener`, which pretty-prints a `.scene` / `.abss` opened in the text editor.
- **Never change numbers or key order you didn't mean to change.** Parse with `SceneJson`, which keeps both.
- **`Gdx.*` is process-global inside the IDE.** Run libGDX code only inside `GdxRuntime.withContext`, on the AWT
  thread that renders the canvas (a Swing timer drives frames). Only the `prepare` step of `AssetCache` runs off that
  thread, and it must not touch GL. Picking and gizmo hit tests use CPU data and need no context.
- **Use GL only while the canvas is safely on screen** (`GuardedGLCanvas.glSafe`). On macOS a zero-sized surface
  aborts the JVM.
- **Catch with `runCatchingKeepingCancellation`**, not `runCatching`, around anything that may be cancelled
  (`./gradlew checkNoRunCatching`, part of `check`, fails otherwise).
- **User-facing text goes in `src/main/resources/messages/AbyssusBundle.properties`** via `AbyssusBundle.message`.
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
- `docs/ai/glossary.md`: Mundus and Abyssus terms.
- `docs/ai/conventions.md`: code conventions.
- `docs/ai/testing.md`: test layout, fixtures, GL tests.
- Package notes: `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md`,
  `src/main/kotlin/net/nevinsky/abyssus/projectView/README.md`, `src/main/kotlin/net/nevinsky/abyssus/ecs/README.md`.
