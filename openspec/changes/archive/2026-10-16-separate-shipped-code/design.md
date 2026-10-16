# Design

## Context

See proposal.md for the motivation. These are the facts the approach depends on, as found in the code on 2026-10-14:

- Modules: `lib-gdx`, `lib-core`, `lib-raytracing`, `lib-core-editor`, `lib-physics`, `plugin-abyssus`,
  `app-game-control-line`. The old `lib-runtime`, `lib-gdx-model` and `plugin-abyssus-physics` no longer exist (only
  untracked `build/` folders remain).
- The game's main dependency is `lib-physics` (which exposes `lib-core` and, through it, `lib-gdx`). The game's `tools`,
  `importers`, `uiTools` and `fieldFoliageTools` source sets reuse the main runtime classpath and are not part of the
  `application` distribution; `importers` and `fieldFoliageTools` already depend on `lib-core-editor`.
- **FlightGear, `ModelImport` and `EcsWriter`** already live in `lib-core-editor` (`editor/flightgear`,
  `editor/modelimport`, `editor/ecs`). Nothing to move.
- **Ray data in `lib-core`**:
  - `assets/loading/RaySnapshotStorage.kt` (`RaySnapshot`, `RaySnapshotLoader`, `RaySnapshotStore`, the leases);
  - the snapshot types `assets/model/ModelRaySnapshot.kt`, `assets/terrain/RayTerrainSnapshot.kt`,
    `assets/sky/RaySkySnapshot.kt` (and `RayModelSkinning` where it lives);
  - the loaders `ModelRaySnapshotLoader`, `TerrainRaySnapshotLoader`, `SkyboxRaySnapshotLoader`,
    `HdrSkyRaySnapshotLoader` and `ProceduralSkyRaySnapshotLoader`;
  - their tests `RayModelSnapshotTest`, `RaySkySnapshotTest`, `RayTerrainSnapshotTest` in `lib-core`;
  - the `rayTracingEnabled` / `rayTracing` fields of `dto/SceneDto.kt`.

  `StandardAssetLoaders.kt` and `ModelLoader`'s optional `raySnapshots` parameter reference these types; the plugin's
  `AssetLoading.kt` wires them. `lib-core-editor` already has `editor/ray/` (e.g. `RaySceneSnapshot`). `PreparedModel`
  exposes `data` but keeps its pixmaps private.
- `renderParamsOf` (`lib-core-editor/.../scene/SceneRenderParams.kt`) rebuilds the `rayTracing` block from the typed
  scene with `valueToTree` before `SceneRaySettingsCodec` reads it. `SceneRaySettings` / `RayMaterialOverrides` already
  read raw JSON. `JsonProcessor` disables `FAIL_ON_UNKNOWN_PROPERTIES`, so dropping fields from the DTO makes Jackson
  ignore them.
- **Play:**
  - `lib-physics/.../play/` (7 files) holds `PlayModule`, `PhysicsOnlyPlayModule`, `PlayHost`, `PlayHostMain`,
    `PlayProtocol`, `PlayFile` and `PlayExportMain`. `PlayModule` names `PhysicsWorld`, which is in `lib.physics.jolt`.
  - `lib-physics` has a consumable `playHost` configuration with the four platforms' Jolt natives.
  - `plugin-abyssus` (`plugin/physics/`: `PlayLaunch`, `PlayClient`, `PlayProcessLauncher`, ...) uses the play types.
    `PlayLaunch.kt` names `PLAY_HOST_MAIN` and `PHYSICS_ONLY_MODULE` by their `lib.physics.play` FQNs. Its Gradle file
    bundles `lib-physics` (non-transitive) and `playHostLibs` = `lib-physics` + its `playHost` configuration, and its
    zip check looks for `/lib/lib-physics` and Jolt files in `play-host/`.
  - `ControlLinePlay` sits in the game's main sources (`play/`) and is tested by `ControlLinePlayTest`. The game's Gradle
    file defines no `exportPlay`, `exportComponentSchema` or `exportAbyssus` task; a machine-local `play.json` is made by
    running `PlayExportMain` by hand (game README, physics guide). There is no schema export entry point;
    `abyssus/components.schema.json` is committed data.
- Source checks are registered through `gradle/checks.gradle.kts` (`registerSourceCheck`), driven by `extra` flags a
  module sets, and are configuration-cache safe.

## Goals / Non-Goals

**Goals:**
- After this change, the module graph alone keeps editor code out of the game. Two checks stop it from coming back.
- Behavior stays the same: the same file bytes, Play results and ray settings validation.

**Non-Goals:**
- No new abstractions beyond the one neutral `ModelLoader` hook.
- Class names stay the same. Only packages and modules change for the moved classes.
- Not designed here: how ray tracing later becomes an optional plugin. Placing it in `lib-core-editor` is a step toward
  that, not the end state.

## Decisions

### D1. One new module, `lib-game-link`; no `lib-importer`

```
 shipped by the game            editor side / dev-time only
 -------------------            ------------------------------------------------
 lib-gdx                        lib-game-link -> lib-physics, lib-core   (Play)
 lib-core                       lib-core-editor -> lib-core, lib-gdx,
 lib-physics                                       lib-raytracing   (+ ray data,
                                                                      FlightGear, EcsWriter)
 app main  ------------------>  (nothing on the right)
 app play source set -------->  lib-game-link
 app importers source set --->  lib-core-editor
```

- **`lib-game-link`:** the play host runs in the **game's** JVM and loads Jolt, so it can't live in `lib-core-editor`,
  whose code loads in the IDE. It also can't stay in a library the game ships.
- **No `lib-importer`:** FlightGear already sits in `lib-core-editor`, and the game's `importers` source set (outside
  the distribution) is the only game-side user. A separate module would add a dependency edge and docs without keeping
  anything more out of the shipped game. **Rejected:** extracting it anyway; a single `lib-tools` module (it would mix
  Jolt with editor code).

`lib-game-link` is plain JVM and constructor-wired. It gets `abyssusSingletonExcludes` (so `checkNoSingletons`
applies), is included in `checkPackageCycles`, and has no IntelliJ imports. `PlayHostMain` and the export main stay
top-level `main` functions; they are not objects.

### D2. Ray data moves to `lib-core-editor`, behind a neutral `ModelLoader` hook

- The ray files listed in Context move to `net.nevinsky.abyssus.lib.core.editor.ray.snapshot` with their class names
  unchanged.
- `copyRayImage` and `immutableModelList` stay `internal` and move with them.
- `ModelLoader`'s `raySnapshots` parameter is replaced by `preparation: ModelPreparationTap? = null`, a `lib-core`
  fun interface: `begin(name): Capture?`, then `Capture.offer(data: ModelData, images: Map<String, Pixmap>)`. The
  cancellation and dispose handling is the same as today. The editor implements it by adapting its
  `RaySnapshotStore<RayModelSnapshot, RayModelSource>`.
- `StandardAssetLoaders` loses its ray parameters; the editor-side wiring (plugin `AssetLoading.kt`) registers the
  snapshot loaders itself.
- **Rejected:** wrapping `ModelLoader` in a decorator. The decoded images are private to `PreparedModel`'s upload
  queue, and exposing them would widen a shipped API more than one neutral hook does.
- **Rejected:** moving the ray data to `lib-raytracing`. That module has no libGDX dependency by design (its README),
  and the loaders use `Pixmap`, `HdrImage` and `TextureLoader`.

**Threads:** unchanged.
- Snapshot loading still runs on `AssetStorage`'s prepare executor, off the AWT thread and without GL.
- The tap is called inside `loadPrepared` on that same executor.
- Nothing new runs inside `GdxRuntime.withContext`.

### D3. The editor reads scene ray settings from the document, not from `lib-core`'s DTO

- `SceneDto` loses `rayTracingEnabled` and `rayTracing` (and the ray DTO type, if nothing else uses it).
- `renderParamsOf` takes the scene's validated root node, or its `rayTracing` subtree, and hands that to
  `SceneRaySettingsCodec`. It no longer rebuilds the block with `valueToTree`.
- The plugin's scene-params source already has the text and parses it; add a sibling parse that returns the scene with
  its root node so the text is parsed once. Confirm the exact class names (`DocumentParsing`, `SceneParamsSource`) when
  implementing.
- The game's scene loader ignores the two keys as unknown properties.
- Writes don't change: ray settings are written through `editSceneJson` / `DocumentTextEditor` as today, never by
  serializing the DTO.

This also removes a subtle conversion. Today an omitted field reaches the codec as its default, through the
data-class defaults. After the change, the codec sees exactly what the file holds. The codec's own defaults
(`SceneRaySettingsCodec`) must produce the same state for omitted fields, and `SceneRenderParamsTest` checks that
(see Risks).

### D4. The Play bridge becomes `lib-game-link`

- **Moves** into `net.nevinsky.abyssus.lib.gamelink.play`, class names unchanged: `PlayModule`,
  `PhysicsOnlyPlayModule`, `PlayHost`, `PlayHostMain`, `PlayProtocol` (with `PLAY_PROTOCOL` still 1, `PlayFrame`,
  `PlayInput`, `DebugLine`, `PLAY`), `PlayFile` and `PlayExportMain`.
- **The `playHost` configuration** (the four platforms' Jolt natives) moves from `lib-physics` to `lib-game-link`.
  `lib-physics` keeps the Jolt API dependency.
- **`plugin-abyssus`:**
  - `implementation(project(":lib-game-link")) { isTransitive = false }`, so its jar is bundled the same way as
    `lib-physics`;
  - `playHostLibs` is `lib-game-link` plus `lib-physics` plus the `playHost` configuration;
  - the bundling zip check also requires `lib/lib-game-link*.jar` and `play-host/lib-game-link*.jar`;
  - `PLAY_HOST_MAIN` and `PHYSICS_ONLY_MODULE` in `PlayLaunch.kt`, and all imports in `plugin/physics`, change package;
  - `checkNoJolt` still passes: the plugin names only protocol and file types. `PlayModule` references
    `PhysicsWorld`, but the IDE never loads `PlayModule`, as today.
- **`PlayHostMain` keeps its simple JVM name** (`@file:JvmName("PlayHostMain")`). Its fully qualified name becomes
  `net.nevinsky.abyssus.lib.gamelink.play.PlayHostMain`. `play.json` doesn't name the host class, so its format is
  unchanged. But a `play.json` made before this change has a classpath without `lib-game-link` or the game's
  `play` output, so it must be exported again (see Risks).

### D5. Control Line's `play` source set and `exportPlay`

- **`play` source set** (`src/play/kotlin`), holding `play/ControlLinePlay.kt`:
  - `compileClasspath` and `runtimeClasspath` add `main`'s output and runtime classpath;
  - `playImplementation(project(":lib-game-link"))`.
- **New `exportPlay` task** (a `JavaExec` running `PlayExportMain` on `play.runtimeClasspath`, with the module name and
  the project folder; the folder is given as a property, so it is used on a disposable copy). The exported `play.json`
  classpath then holds `main`, `play`, `lib-game-link` and the rest. This replaces the hand-run `PlayExportMain` the
  README describes today. No schema export task is added.
- **Tests:** `test` gets `play`'s output so `ControlLinePlayTest` keeps running. GL tests are unchanged.
- **Distribution:** `application`'s `installDist` / `distZip` still package `main` only. `play` is real code, so
  `kover` covers it (it is not added to the excluded source sets).
- **Rejected:** a separate `app-control-line-play` module. A source set is enough because nothing else depends on the
  play code. The existing `tools` / `importers` source sets use the same pattern.

### D6. Two checks, both part of `check`

- **`:app-game-control-line:checkShippedClasspath`:**
  - reads `configurations.runtimeClasspath`'s resolution result (as a provider, for the configuration cache);
  - fails on any project component whose path is `:lib-core-editor`, `:lib-game-link`, `:lib-raytracing` or starts
    with `:plugin-`, naming each one.
  - It is registered in the game's build script, because the denylist is about this game's shipped classpath.
- **`checkNoEditorCode`:**
  - registered through `registerSourceCheck` in `gradle/checks.gradle.kts` for every module that sets
    `extra["abyssusShippedLibrary"] = true`: `lib-gdx`, `lib-core` and `lib-physics`;
  - one regex over `src/main/kotlin`, matching:
    - imports of `net.nevinsky.abyssus.lib.core.editor`, `.lib.gamelink`, `.lib.raytracing` or `.plugin`;
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
  `SceneRayTracingBindingTest` assertions (if still present) move to `lib-core-editor` against the raw-node path.
- **[A contributor's old `play.json` names a classpath without `lib-game-link` or the `play` output]** → The host
  JVM can't find `PlayHostMain`, so Play ends with the existing "play ended unexpectedly" notification instead of the
  "export again" message (that message covers only missing files). The cost is one re-export per checkout. The game
  README and `lib-game-link`'s README say to run `exportPlay` after this change. No protocol bump: the protocol
  didn't change.
- **[Bundled jar set changes in Abyssus]** → `lib-game-link.jar` is added to the plugin's `lib/` and `play-host/`.
  The "bundle `lib-physics` without its dependencies" rule applies to it too. Verify with `unzip -l` of the zip.
- **[`separate-shipped-code` and `reduce-architecture-duplication` overlap]** → That change's R6 waits for this
  change's acceptance evidence (checks green, distribution inspected). No second implementation there.
- **[`check-docs.sh` may already fail on stale paths]** → The final task requires no **new** broken path and lists the
  remaining ones as pre-existing.

## Migration Plan

1. Land the change. Contributors run `./gradlew :app-game-control-line:exportPlay -P...` again for their project copy.
2. A game outside the repository that implements `PlayModule`:
   - adds `lib-game-link`, ideally in a separate source set;
   - updates its imports to `net.nevinsky.abyssus.lib.gamelink.play`;
   - exports again.
3. No user-facing migration: no native file changes.
4. Rollback: revert the change and re-export `play.json`.

## Open Questions

None that change the specs, approach or tasks. `PreparedModel` could later expose its images and make the tap
unnecessary; that's a separate cleanup.
