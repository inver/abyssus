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
| One test class | `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.OrbitCameraTest'` |
| Also run GL tests (open a window) | add `-Dabyssus.glTests=true` |
| Sandbox IDE | `./gradlew runIde` (open a project with `-PideProject=/path/to/project`) |
| Plugin zip | `./gradlew buildPlugin` (to `build/distributions/`) |
| Docs path check | `scripts/check-docs.sh` |

Use `:test`, not `test`, with `--tests`: plain `test` also runs in `gdx-model` and fails there with
"No tests found".

## Layout

- `src/main/kotlin/net/nevinsky/abyssus/`: the plugin (Kotlin), registered in `src/main/resources/META-INF/plugin.xml`.
  - `dto/`: reads `.abss` / `.scene` files and asset folders (`ProjectLayout`, `AssetReader`, `ProjectAssets`).
  - `scene/`: the scene DTOs (`SceneDto`, fog, lights).
  - `projectView/`: the Abyssus tree, eye toggles, Rename Scene, skybox chooser, and `editSceneJson`.
  - `properties/`: the Abyssus Properties tool window.
  - `sceneview/`: the scene view: GL canvas, renderer, picking, cameras, gizmos, transform write-back.
  - `ecs/`: Ashley components, systems and a scene ECS loader/writer. Only tests use it so far.
  - `filetype/`, `language/`: file types, icons, scene JSON, the GLTF PSI.
- `gdx-model/`: a plain JVM library (libGDX model runtime with 32-bit indices, Assimp import), forked from Mundus.
  See `gdx-model/README.md`.
- `src/main/java/`: only the grammar sources `Gltf.bnf` / `Gltf.flex`; `src/main/gen` is generated from them.
- `src/test/kotlin/`, `gdx-model/src/test/kotlin/`: tests. Fixtures in `src/test/testData/project/`.
- `openspec/`: specs and changes (see Workflow). `docs/superpowers/`: one historic design and plan.

## Hard rules

- **Don't edit `src/main/gen`.** It is git-ignored and regenerated from `Gltf.bnf` / `Gltf.flex`.
- **`gdx-model` stays a plain JVM library**: no IntelliJ or plugin imports, so other libGDX projects can use it.
- **Write scene files only through `editSceneJson`** (`src/main/kotlin/net/nevinsky/abyssus/projectView/EnabledToggle.kt`).
  It edits the document as one undoable command and keeps the file's formatting and number text. The only other
  writer is `SceneFormatListener`, which pretty-prints a `.scene` / `.abss` opened in the text editor.
- **Never change numbers or key order you didn't mean to change.** Parse with `SceneJson`, which keeps both.
- **`Gdx.*` is process-global inside the IDE.** Run libGDX code only inside `GdxRuntime.withContext`, on the AWT
  thread that renders the canvas (a Swing timer drives frames). Only the `prepare` step of `AssetCache` runs off that
  thread, and it must not touch GL. Picking and gizmo hit tests use CPU data and need no context.
- **Use GL only while the canvas is safely on screen** (`GuardedGLCanvas.glSafe`). On macOS a zero-sized surface
  aborts the JVM.
- **Catch with `runCatchingKeepingCancellation`**, not `runCatching`, around anything that may be cancelled.
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
