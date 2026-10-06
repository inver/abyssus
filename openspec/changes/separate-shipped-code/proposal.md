# Proposal

## Why

Control Line will ship to players, and a shipped game must never carry editor code. Today the game's runtime classpath
(`lib-core`, `lib-runtime`, `lib-physics`) contains the FlightGear importer, the ray-tracing CPU snapshots and scene
settings, the editor's scene writer, and the whole Play bridge (play host, protocol, export entry points). Nothing
stops more from arriving. This must be fixed before `lib-render` is extracted, so the shared renderer starts from a
clean boundary.

## What Changes

- **New module `lib-importer`** (package `net.nevinsky.abyssus.lib.importer`, plain JVM, constructor-wired): the FlightGear aircraft importer moves out of
  `lib-core/flightgear` into it. Abyssus and Control Line's `importers` source set depend on it; the game's main code
  does not. Import behavior is unchanged.
- **Ray-tracing data leaves `lib-core`**: the CPU snapshot store, the model, terrain and sky snapshot loaders and their
  snapshot types move to `lib-core-editor`. `lib-core`'s scene model stops binding `rayTracingEnabled` and
  `rayTracing`; the editor reads them from the same validated document. `ModelLoader` keeps one neutral hook for
  observing prepared model data, with no ray types in its signature.
- **The editor's scene writer** (`EcsWriter`, used only by `lib-core-editor`) moves from `lib-runtime` to
  `lib-core-editor`.
- **New module `lib-game-link`** (package `net.nevinsky.abyssus.lib.gamelink`, plain JVM, constructor-wired): everything a game links only so that Abyssus can talk
  to it. That is the play host and its entry point, the play protocol, `PlayModule` with the physics-only module, the
  `play.json` file, and the schema and play export entry points. These leave `lib-physics/play` and
  `lib-runtime/schema`. The component annotations, `ComponentRegistry` and the schema reader the game needs at run time
  stay in `lib-runtime`.
- **Control Line gets a `play` source set** holding `ControlLinePlay`. `exportAbyssus` runs on that source set, so
  `play.json` names a classpath that includes the play code, while the game's main classpath and its distribution do
  not.
- **Abyssus Physics** gets the play protocol types from `lib-game-link`, and its bundled `play-host/` is built from
  `lib-game-link`, `lib-physics` and the Jolt natives.
- **New build checks, part of `./gradlew check`**:
  - `:app-game-control-line:checkShippedClasspath` fails when the game's main runtime classpath holds an editor-only
    module: `lib-core-editor`, `lib-importer`, `lib-game-link`, `lib-raytracing`, or any `plugin-*`.
  - `checkNoEditorCode` on `lib-core`, `lib-gdx-model`, `lib-runtime` and `lib-physics` fails on ray-tracing snapshot
    types or importer packages in their main sources.
- **BREAKING (contributors):** package and module moves. The `lib.physics.play`, `lib.core.flightgear` and ray
  snapshot classes change package. A game outside this repository that implements `PlayModule` must depend on
  `lib-game-link` and run its export again.

Native files:
- **Reads:** `.scene` `rayTracingEnabled` and `rayTracing` are now read only by the editor, after
  `AbyssusDocumentFormat` validation as today. The game ignores them as unknown native data.
- **Writes:** none. No `.scene`, `.abss` or `meta.json` changes, and no format version change.
- `abyssus/play.json` keeps its fields (`protocol`, `module`, `classpath`, `jvmArgs`). Only its classpath entries
  change after a re-export. `abyssus/components.schema.json` stays byte-identical.

Out of scope:
- `lib-render`, the editor/game render parity, and removing the game's forked terrain shader (next change,
  `extract-lib-render`).
- Moving ray tracing into an optional plugin. The snapshots move only as far as `lib-core-editor`.
- Per-OS game packaging, bundled JRE and content cooking.
- Any change to Play behavior, the play protocol version, the physics schema, or the FlightGear import results.
- Kotlin package renames beyond the moved classes.

## Capabilities

### New Capabilities

- `game-distribution`: what a shipped game build contains. Editor-only code (importers, the Play bridge, ray-tracing
  data, the scene writer, editor modules) stays out of it, while Play and the exports Abyssus reads keep working from
  a separate build of the same game.

### Modified Capabilities

None. Schema export, Play, FlightGear import and ray-tracing settings keep their current requirements. Their code
moves, but what the user sees and what lands in files stays the same.

## Impact

- **Modules:** new `projects/lib-importer` and `projects/lib-game-link`; `settings.gradle.kts`. Changed:
  `lib-core`, `lib-runtime`, `lib-physics`, `lib-core-editor`, `plugin-abyssus`, `plugin-abyssus-physics`,
  `app-game-control-line` (new `play` source set and changed `exportAbyssus` classpath).
- **Build logic:** two new check tasks, wired into `check`, in the style of `checkNoJolt` and `checkNoSingletons`.
- **Generated file:** `projects/app-game-control-line/project/ControlLine/abyssus/play.json` is git-ignored and is
  regenerated by `exportAbyssus`.
- **Other open changes:**
  - `add-model-import` places its writer and importer in `lib-core`; its design and tasks are amended to target
    `lib-importer`.
  - `merge-physics-into-abyssus` moves `playHostLibs` and the play-host copy; its tasks are amended to name
    `lib-game-link`.
  - `restructure-gradle-modules` is unfinished. This change uses the module names on disk today.
- **Docs:** `AGENTS.md` (Layout, Hard rules, Commands), `docs/ai/architecture.md`, the READMEs of `lib-core`,
  `lib-runtime`, `lib-physics`, `lib-core-editor` and `app-game-control-line`, plus new READMEs for the two modules.
