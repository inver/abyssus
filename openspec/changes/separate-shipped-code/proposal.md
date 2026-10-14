# Proposal

## Why

Control Line will ship to players, and a shipped game must never carry editor code. The game's runtime classpath
(`lib-core`, `lib-gdx`, `lib-physics`) still contains the ray-tracing CPU snapshots and the `rayTracing` scene settings,
and the whole Play bridge (play host, protocol, export entry point, `PlayModule`). Nothing stops more from arriving.
The FlightGear importer and the editor's scene writer already live in `lib-core-editor`, which the game's main code does
not use. This must be fixed before `lib-render` is extracted (`reduce-architecture-duplication`, R6), so the shared
renderer starts from a clean boundary; that change relies on this one as its R2.

## What Changes

- **Ray-tracing data leaves `lib-core`**: the CPU snapshot store, the model, terrain and sky snapshot loaders and their
  snapshot types move to `lib-core-editor`. `lib-core`'s scene DTO stops binding `rayTracingEnabled` and
  `rayTracing`; the editor reads them from the same validated document. `ModelLoader` keeps one neutral hook for
  observing prepared model data, with no ray types in its signature.
- **New module `lib-game-link`** (package `net.nevinsky.abyssus.lib.gamelink`, plain JVM, constructor-wired): everything a game links only so that Abyssus can talk
  to it. That is the play host and its entry point, the play protocol, `PlayModule` with the physics-only module, the
  `play.json` file and the play export entry point. These leave `lib-physics/play`.
- **Control Line gets a `play` source set** holding `ControlLinePlay`, and a new `exportPlay` task that runs on that
  source set. `play.json` then names a classpath that includes the play code, while the game's main classpath and its
  distribution do not.
- **Abyssus** (`plugin-abyssus`, package `plugin/physics`) gets the play protocol types from `lib-game-link`, and its
  bundled `play-host/` is built from `lib-game-link`, `lib-physics` and the Jolt natives.
- **New build checks, part of `./gradlew check`**:
  - `:app-game-control-line:checkShippedClasspath` fails when the game's main runtime classpath holds an editor-only
    module: `lib-core-editor`, `lib-game-link`, `lib-raytracing`, or any `plugin-*`.
  - `checkNoEditorCode` on `lib-gdx`, `lib-core` and `lib-physics` fails on ray-tracing snapshot types or editor/Play
    imports in their main sources.
- **BREAKING (contributors):** package and module moves. The `lib.physics.play` and ray snapshot classes change package.
  A game outside this repository that implements `PlayModule` must depend on `lib-game-link` and export again.

Already satisfied by the current checkout, so not part of this change: the FlightGear importer and `ModelImport`
(in `lib-core-editor`, `editor/flightgear` and `editor/modelimport`), `EcsWriter` (`lib-core-editor`, `editor/ecs`),
and the merge of `lib-runtime`, `lib-gdx-model` and `plugin-abyssus-physics` into `lib-core`, `lib-gdx` and
`plugin-abyssus`. No `lib-importer` module is created: the importer is isolated from the game's main code by the game's
`importers` source set, which is enough.

Native files:
- **Reads:** `.scene` `rayTracingEnabled` and `rayTracing` are now read only by the editor, after
  `AbyssusDocumentFormat` validation as today. The game ignores them as unknown native data.
- **Writes:** none. No `.scene`, `.abss` or `meta.json` changes, and no format version change.
- `abyssus/play.json` keeps its fields (`protocol`, `module`, `classpath`, `jvmArgs`). Only its classpath entries
  change after a re-export. `abyssus/components.schema.json` is committed data and is not touched.

Out of scope:
- `lib-render`, the editor/game render parity, and the shared terrain shader (`reduce-architecture-duplication`).
- Moving ray tracing into an optional plugin. The snapshots move only as far as `lib-core-editor`.
- A schema export task or entry point (the game has none today).
- Per-OS game packaging, bundled JRE and content cooking.
- Any change to Play behavior, the play protocol version, the physics schema, or the FlightGear import results.
- Kotlin package renames beyond the moved classes.

## Capabilities

### New Capabilities

- `game-distribution`: what a shipped game build contains. Editor-only code (the Play bridge, ray-tracing data,
  editor modules) stays out of it, while Play keeps working from a separate build of the same game.

### Modified Capabilities

None. Play and ray-tracing settings keep their current requirements. Their code moves, but what the user sees and what
lands in files stays the same.

## Impact

- **Modules:** new `projects/lib-game-link`; `settings.gradle.kts`. Changed: `lib-core`, `lib-physics`,
  `lib-core-editor`, `plugin-abyssus` (`plugin/physics`, bundling), `app-game-control-line` (new `play` source set and
  `exportPlay`).
- **Build logic:** two new check tasks, wired into `check`, in the style of `checkNoJolt` and `checkNoSingletons`.
- **Generated file:** `projects/app-game-control-line/project/ControlLine/abyssus/play.json` is machine-local (absolute
  paths) and must stay out of version control; it is written by `exportPlay`.
- **Other open changes:** `reduce-architecture-duplication` (task 1.3 is satisfied by this revision; R6 waits for this
  change's acceptance evidence). `separate-shipped-code` uses the module names on disk today.
- **Docs:** `AGENTS.md` (Layout, Hard rules, Commands), `docs/ai/architecture.md`, the READMEs of `lib-core`,
  `lib-physics`, `lib-core-editor` and `app-game-control-line`, plus a new README for `lib-game-link`.
