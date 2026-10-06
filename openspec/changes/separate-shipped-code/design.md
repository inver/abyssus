# Design

## Context

See proposal.md for the motivation. These are the facts the approach depends on, as found in the code:

- The game's main dependencies are `lib-core`, `lib-gdx-model` and `lib-physics`, and through them `lib-runtime`
  (`app-game-control-line.gradle.kts`). The game's `tools` and `importers` source sets reuse the main runtime
  classpath and are not part of the `application` distribution.
- **FlightGear** (`lib-core/.../flightgear/`, 6 files, ~1,150 LOC) depends only on `lib-core`'s `format` and `io`
  packages. Its users are the plugin (`ImportFlightGearAction`, `FlightGearImportSettings`, `AbyssusCore`), the game's
  `importers/TrainerModel.kt`, 8 `lib-core` tests and the `lib-core` testFixture `FlightGearFixtures`.
- **Ray data in `lib-core`** (~780 LOC):
  - `RaySnapshotStorage.kt` (`RaySnapshot`, `RaySnapshotLoader`, `RaySnapshotStore`, the leases);
  - the snapshot types `ModelRaySnapshot.kt`, `RayTerrainSnapshot.kt`, `RaySkySnapshot.kt` and `RayModelSkinning.kt`;
  - the loaders for models, terrain, cube, HDR and procedural skies;
  - `scene/RayTracing.kt` and the `rayTracingEnabled` / `rayTracing` fields of `scene/Scene.kt`.

  Only `lib-core-editor` and the plugin use these outside `lib-core`. The one hook in a non-ray file is `ModelLoader`'s
  optional `raySnapshots` parameter, which captures `ModelData` and the decoded images inside `loadPrepared`.
  `PreparedModel` exposes `data` but keeps its pixmaps private.
- `renderParamsOf` (`lib-core-editor/.../scene/SceneRenderParams.kt`) rebuilds the `rayTracing` block from the typed
  `Scene` with `valueToTree` before `SceneRaySettingsCodec` reads it. `SceneRaySettings` / `RayMaterialOverrides` already
  read raw JSON. `JsonProcessor` disables `FAIL_ON_UNKNOWN_PROPERTIES`, so dropping fields from `Scene` makes Jackson
  ignore them.
- **`EcsWriter`** (`lib-runtime/.../ecs/EcsWriter.kt`) is used only by `lib-core-editor/.../components/*`. It is
  tested by `EcsWriterTest` and by the writer cases of `NativeEcsAdmissionTest`.
- **Play:**
  - `lib-physics/.../play/` (7 files) holds `PlayModule`, `PhysicsOnlyPlayModule`, `PlayHost`, `PlayHostMain`,
    `PlayProtocol`, `PlayFile` and `PlayExportMain`. `PlayModule` names `PhysicsWorld`, which is in `lib.physics.jolt`.
  - `lib-physics` has a consumable `playHost` configuration with the four platforms' Jolt natives.
  - `plugin-abyssus-physics` uses `PlayFile`, `PlayProtocol`, `PlayFrame`, `PlayInput` and `PLAY`. Its
    `playHostLibs` and `schemaExport` configurations point at `lib-physics`.
  - `SchemaExportMain` / `exportSchema` sit in `lib-runtime/schema`, next to the run-time schema types the game needs
    (`ComponentRegistry`, annotations, `ComponentSchemaReader` via `GameComponents`).
  - `ControlLinePlay` sits in the game's main sources and is tested by `ControlLinePlayTest`.
- Source checks are registered through `gradle/checks.gradle.kts` (`registerSourceCheck`), driven by `extra` flags a
  module sets, and are configuration-cache safe.

## Goals / Non-Goals

**Goals:**
- After this change, the module graph alone keeps editor code out of the game. Two checks stop it from coming back.
- Behavior stays the same: the same file bytes, Play results, import output and ray settings validation.

**Non-Goals:**
- No new abstractions beyond the one neutral `ModelLoader` hook.
- Class names stay the same. Only packages and modules change for the moved classes.
- Not designed here: how ray tracing later becomes an optional plugin. Placing it in `lib-core-editor` is a step toward
  that, not the end state.

## Decisions

### D1. Two new modules, not more code in `lib-core-editor`

```
 shipped by the game            editor side / dev-time only
 -------------------            ------------------------------------------------
 lib-gdx-model                  lib-importer  -> lib-core            (FlightGear)
 lib-core                       lib-game-link -> lib-physics, lib-runtime  (Play)
 lib-runtime                    lib-core-editor -> lib-core, lib-runtime,
 lib-physics                                       lib-raytracing    (+ ray data,
                                                                       EcsWriter)
 app main  ------------------>  (nothing on the right)
 app play source set -------->  lib-game-link
 app importers source set --->  lib-importer
```

- **`lib-importer`:** the game's `importers` source set and Abyssus both need FlightGear. Putting it in
  `lib-core-editor` would pull `lib-raytracing` into a command-line importer. `add-model-import` gets a home too.
- **`lib-game-link`:** the play host runs in the **game's** JVM and loads Jolt, so it can't live in `lib-core-editor`,
  whose code loads in the IDE. It also can't stay in a library the game ships.
- **Rejected:** a single `lib-tools` module for both. Its dependencies would combine Jolt and the importer for no gain.

Both modules are plain JVM and constructor-wired. They get `abyssusSingletonExcludes` (so `checkNoSingletons`
applies), are included in `checkPackageCycles`, and have no IntelliJ imports. `PlayHostMain` and the export mains stay
top-level `main` functions; they are not objects.

### D2. Ray data moves to `lib-core-editor`, behind a neutral `ModelLoader` hook

- The ray files listed in Context move to `net.nevinsky.abyssus.lib.core.editor.ray.snapshot` with their class names
  unchanged.
- `copyRayImage` and `immutableModelList` stay `internal` and move with them.
- `ModelLoader`'s `raySnapshots` parameter is replaced by `preparation: ModelPreparationTap? = null`, a `lib-core`
  fun interface: `begin(name): Capture?`, then `Capture.offer(data: ModelData, images: Map<String, Pixmap>)`. The
  cancellation and dispose handling is the same as today. The editor implements it by adapting its
  `RaySnapshotStore<RayModelSnapshot, RayModelSource>`.
- **Rejected:** wrapping `ModelLoader` in a decorator. The decoded images are private to `PreparedModel`'s upload
  queue, and exposing them would widen a shipped API more than one neutral hook does.
- **Rejected:** moving the ray data to `lib-raytracing`. That module has no libGDX dependency by design (its README),
  and the loaders use `Pixmap`, `HdrImage` and `TextureLoader`.

**Threads:** unchanged.
- Snapshot loading still runs on `AssetStorage`'s prepare executor, off the AWT thread and without GL.
- The tap is called inside `loadPrepared` on that same executor.
- Nothing new runs inside `GdxRuntime.withContext`.

### D3. The editor reads scene ray settings from the document, not from `lib-core`'s `Scene`

- `Scene` loses `rayTracingEnabled` and `rayTracing`, and `RayTracing.kt` is deleted from `lib-core`.
- `renderParamsOf` takes the scene's validated root node, or its `rayTracing` subtree, and hands that to
  `SceneRaySettingsCodec`. It no longer rebuilds the block with `valueToTree`.
- `SceneParamsSource` (plugin) already has the text and parses it through `DocumentParsing`. `DocumentParsing.parse`
  gains a sibling that returns the `Scene` with its root node, so the text is parsed once.
- The game's `SceneLoader` ignores the two keys as unknown properties.
- Writes don't change: ray settings are written through `editSceneJson` / `DocumentTextEditor` as today, never by
  serializing `Scene`.

This also removes a subtle conversion. Today an omitted field reaches the codec as its default, through the
data-class defaults. After the change, the codec sees exactly what the file holds. The codec's own defaults
(`SceneRaySettingsCodec`) must produce the same state for omitted fields, and `SceneRenderParamsTest` checks that
(see Risks).

### D4. `EcsWriter` moves to `lib-core-editor`

- It moves to `lib.core.editor.components`, next to its only users.
- `EcsWriterTest` moves with it.
- `NativeEcsAdmissionTest` splits: the read cases stay in `lib-runtime`, and the write cases move to a new
  `lib-core-editor` test `NativeEcsWriteAdmissionTest`.
- The admission rules shared by reading and writing stay in `lib-runtime`, which the writer calls.

### D5. The Play bridge becomes `lib-game-link`

- **Moves** into `net.nevinsky.abyssus.lib.gamelink.play`, class names unchanged: `PlayModule`,
  `PhysicsOnlyPlayModule`, `PlayHost`, `PlayHostMain`, `PlayProtocol` (with `PLAY_PROTOCOL` still 1, `PlayFrame`,
  `PlayInput`, `DebugLine`, `PLAY`), `PlayFile` and `PlayExportMain`.
- **`SchemaExportMain` / `exportSchema` move** to `net.nevinsky.abyssus.lib.gamelink.export`. `SchemaFile`,
  `SchemaJson`, `ComponentSchemaReader`, `ComponentRegistry` and the annotations stay in `lib-runtime`: the game reads
  schemas at run time, and the editor parses the file.
- **The `playHost` configuration** (the four platforms' Jolt natives) moves from `lib-physics` to `lib-game-link`.
- **`plugin-abyssus-physics`:**
  - `implementation(project(":lib-game-link")) { isTransitive = false }`, so its jar is bundled the same way as
    `physics.jar`;
  - `playHostLibs` is `lib-game-link` plus its `playHost` configuration;
  - `schemaExport` runs on `lib-game-link`.
  - `checkNoJolt` still passes: the plugin names only protocol and file types. `PlayModule` references
    `PhysicsWorld`, but the IDE never loads `PlayModule`, as today.
- **`PlayHostMain` keeps its simple JVM name** (`@file:JvmName("PlayHostMain")`). Its fully qualified name becomes
  `net.nevinsky.abyssus.lib.gamelink.play.PlayHostMain`, and Abyssus Physics updates `PLAY_HOST_MAIN`
  (`PlayLaunch.kt`), the one place that names it. `play.json` doesn't name the host class, so its format is
  unchanged. But a `play.json` exported before this change has a classpath without `lib-game-link` or the game's
  `play` output, so it must be exported again (see Risks).

### D6. Control Line's `play` source set

- **`play` source set** (`src/play/kotlin`), holding `play/ControlLinePlay.kt`:
  - `compileClasspath` and `runtimeClasspath` add `main`'s output and runtime classpath;
  - `playImplementation(project(":lib-game-link"))`.
- **Exports run on it:** `exportComponentSchema` and `exportPlay` use `play.runtimeClasspath`. The exported
  `play.json` classpath then holds `main`, `play`, `lib-game-link` and the rest, and the schema export uses
  `ControlLineComponents`, which is unchanged in `main`.
- **Tests:** `test` gets `play`'s output so `ControlLinePlayTest` keeps running. GL tests are unchanged.
- **Distribution:** `application`'s `installDist` / `distZip` still package `main` only. `kover` excludes nothing new,
  since `play` is real code, so it is covered.
- **Rejected:** a separate `app-control-line-play` module. A source set is enough because nothing else depends on the
  play code. The existing `tools` / `importers` source sets use the same pattern.

### D7. Two checks, both part of `check`

- **`:app-game-control-line:checkShippedClasspath`:**
  - reads `configurations.runtimeClasspath`'s resolution result (as a provider, for the configuration cache);
  - fails on any project component whose path is `:lib-core-editor`, `:lib-importer`, `:lib-game-link`,
    `:lib-raytracing` or starts with `:plugin-`, naming each one.
  - It is registered in the game's build script, because the denylist is about this game's shipped classpath.
- **`checkNoEditorCode`:**
  - registered through `registerSourceCheck` in `gradle/checks.gradle.kts` for every module that sets
    `extra["abyssusShippedLibrary"] = true`: `lib-gdx-model`, `lib-core`, `lib-runtime` and `lib-physics`;
  - one regex over `src/main/kotlin`, matching:
    - imports of `net.nevinsky.abyssus.lib.core.editor`, `.lib.importer`, `.lib.gamelink`, `.lib.raytracing` or
      `.plugin`;
    - `package` lines ending in `.flightgear`;
    - the identifiers `RaySnapshot`, `RaySnapshotStore`, `RaySnapshotLoader` or `Ray\w*Snapshot\b`.
  - The message names the file and line, as the existing checks do.
  - **Rejected:** a bytecode or jar-content scan. The dependency check already catches whole modules, and source
    patterns match how the repo enforces its other rules.

## Risks / Trade-offs

- **[The ray loaders need `lib-core` internals once they are in another module]** → For example
  `runCatchingKeepingCancellation` in `lib.core.assets`, HDR image and downsampling helpers, and `TextureLoader`
  pieces. Make only what's needed `public`, list each one in the task, and prefer the existing
  `runCatchingKeepingCancellation` of `lib-core-editor` where one exists.
- **[Ray settings read differently once they're read raw]** → `SceneRenderParamsTest` gets cases for a missing block,
  an empty `{}` block, and an explicit `null` field. They pin the same `SceneRaySettingsState` as today's path. The
  `SceneRayTracingBindingTest` assertions move to `lib-core-editor` against the raw-node path.
- **[A contributor's old `play.json` names a classpath without `lib-game-link` or the `play` output]** → The host
  JVM can't find `PlayHostMain`, so Play ends with the existing "play ended unexpectedly" notification instead of the
  "export again" message (that message covers only missing files). The cost is one re-export per checkout. The game
  README and `lib-game-link`'s README say to run `exportAbyssus` after this change. No protocol bump: the protocol
  didn't change.
- **[Bundled jar set changes in Abyssus Physics]** → `lib-game-link.jar` is added to the plugin's `lib/` and
  `play-host/`. The "extension plugins bundle only their own code" rule is unchanged: only Abyssus Physics uses this
  jar. Verify with `unzip -l` of the zip.
- **[Collisions with open changes]** →
  - `add-model-import`'s design and tasks are amended to put `ModelImport` / `GltfWriter` in `lib-importer`;
  - `merge-physics-into-abyssus`'s tasks are amended to move `lib-game-link` (not `lib-physics`) as the play-host
    source and protocol dependency;
  - `restructure-gradle-modules` is not touched. This change uses today's paths.
- **[`check-docs.sh` already fails on paths left over from the restructure]** → The final task requires no **new**
  broken path and lists the remaining ones as pre-existing.

## Migration Plan

1. Land the change. Contributors re-run `./gradlew :app-game-control-line:exportAbyssus`.
2. A game outside the repository that implements `PlayModule`:
   - adds `lib-game-link`, ideally in a separate source set;
   - updates its imports to `net.nevinsky.abyssus.lib.gamelink.play`;
   - exports again.
3. No user-facing migration: no native file changes.
4. Rollback: revert the change and re-export `play.json`.

## Open Questions

None that change the specs, approach or tasks. `PreparedModel` could later expose its images and make the tap
unnecessary; that's a separate cleanup.
