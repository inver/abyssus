# Design

## Context

See proposal.md - Why. After `extract-scene-runtime` and `add-custom-components`:

- `runtime` loads a scene into an Ashley `SceneEngine`, with game components registered through a
  `ComponentRegistry` and exported to `abyssus/components.schema.json` by `SchemaExportMain`.
- Abyssus has one extension point, `componentSchemas` (a schema resource per contributing plugin, dynamic).
- The Scene view (`SceneViewPanel`) draws on a Swing `Timer` inside `GdxRuntime.withContext`; placements come from
  the `ecs` JSON as `SceneContent`; `ScenePreview.apply(content, Map<id, DragResult>)` overrides placements during a
  drag; `LineBatch` implements `LineSink.line(from, to, color)` with a depth-test switch; keys W / E / D / Esc are bound
  on the panel.
- The root plugin shadows libGDX, LWJGL, `core` and `gdx-model` without relocation, and depends on `runtime`.

**jolt-jni 6.1.1** (MIT, Maven Central, `com.github.stephengold`): the Java API is the plain
`jolt-jni-<Platform>` jar; the natives are classifier jars (`ReleaseSp`, `DebugSp`, `ReleaseDp`, `DebugDp`) holding
e.g. `linux/x86-64/com/github/stephengold/libjoltjni.so`, loaded with `NativeLibraryLoader.loadLibrary`. Native objects
are `AutoCloseable` (`JoltPhysicsObject`). `DistanceConstraintSettings` has min / max distance and limit springs.
`HeightFieldShapeSettings` takes a float sample grid, offset and scale. **`DistanceConstraint` exposes no impulse or
lambda**, so tension cannot be read from it directly.

## Goals / Non-Goals

**Goals:**

- `physics` runs headless and deterministically for a given step sequence; every spec scenario of
  `physics-simulation` is a JUnit test with no IDE and no GL.
- The IDE process never loads `libjoltjni`; the play process carries all native risk.
- The protocol is the only contract between Abyssus Physics and a game's play host.

**Non-Goals:**

- Multithreaded physics jobs (single-threaded keeps determinism; revisit if a game needs it).
- Jolt's own debug renderer (it needs a debug-rendering native build; the overlay draws from component data and
  streamed poses instead).

## Decisions

### 1. Binding: jolt-jni, single precision, release natives

jolt-jni over xJolt: it tracks the current Jolt release, publishes natives for every desktop platform the plugin
ships LWJGL for, and has debug builds for development. `ReleaseSp` natives for Linux64, Windows64, MacOSX64 and
MacOSX_ARM64 go on the play host's runtime classpath; tests use the build machine's `DebugSp`. Single precision matches
libGDX's `float` math. **xJolt** stays the fallback only if a web build is required (proposal, Out of scope).

The exploration proposed this binding without an explicit confirmation; task 0.1 records the user's confirmation
before any dependency is added.

### 2. Module layout

| Module | Kind | Depends on | Holds |
|---|---|---|---|
| `:physics` | plain JVM library, no singletons (`checkNoSingletons`) | `:runtime`, jolt-jni API (`api`), natives `runtimeOnly` | components, `PhysicsWorld`, shape builders, rope tension, `PlayModule`, `PlayHostMain`, protocol |
| `:physics-plugin` | IntelliJ plugin "Abyssus Physics" | root plugin (plugin dependency), `:runtime` compile-only | overlay, play controller, process launcher, protocol client, bundled schema, the default play-host classpath |

`:physics` classes that touch Jolt live in `net.nevinsky.abyssus.physics.jolt`; the protocol and component classes
do not import Jolt. A build check in `:physics-plugin` fails if its main sources reference
`com.github.stephengold` or `net.nevinsky.abyssus.physics.jolt`, so the IDE side cannot load the natives.

The physics component classes are declared with `@SceneComponent` and registered by `PhysicsComponents` (a
`ComponentRegistry`). `:physics-plugin`'s build runs `SchemaExportMain` on it and bundles the result as its
`componentSchemas` resource, so the schema is generated, never hand-written.

### 3. A second IntelliJ plugin in this build (spike first)

`:physics-plugin` applies the IntelliJ Platform Gradle Plugin and must compile against the root plugin's classes and
run in a sandbox with both installed. Options, verified in task 1.1 in this order:

1. `intellijPlatform { localPlugin(project(":")) }` (if the Gradle plugin 2.19 supports a project reference),
2. `localPlugin` pointing at the root's `buildPlugin` zip, with `compileOnly(project(":"))` for classes,
3. fallback: build Abyssus Physics as a separate Gradle build in `physics-plugin/` with a composite build.

**Result of the spike (task 1.1):** option 1 works. `localPlugin(project(":"))` compiles `:physics-plugin` against
Abyssus's classes, `buildPlugin` zips only `physics-plugin/lib/physics-plugin-<version>.jar`, and `runIde` loads
both plugins (`Loaded custom plugins: abyssus, Abyssus Physics`).
`localPlugin` exposes only Abyssus's own classes to the compiler, so `:runtime` is `compileOnly` (it brings `core`,
`gdx-model` and libGDX, which Abyssus's classloader provides at run time). The plugin also bundles `physics.jar`
without its dependencies, for the play protocol, `play.json` and the component classes. `checkNoJolt` keeps its own
sources off `physics.jolt` and jolt-jni, so nothing in the IDE loads the natives.

`plugin.xml` declares `<depends>net.nevinsky.abyssus</depends>`. It bundles only its own jar, the generated schema and
the `play-host/` folder (decision 7); libGDX, `runtime`, `core` and `gdx-model` come from Abyssus's classloader.

### 4. `PhysicsWorld` (physics, Jolt)

- **Build:** from a `SceneEngine` and the project folder: per entity with a `ColliderComponent`, validate (spec "Bad
  input is refused"), build the shape (box / sphere / capsule directly; convex hull from the model's vertex positions
  read through `core`'s model loading `prepare` step, which needs no GL; height field from `core`'s
  `TerrainDataReader`, scaled by terrain size / samples), scale by the entity's scale (non-uniform scale only for
  boxes; spheres and capsules take the largest axis, with one warning), and create the body. Layers: static vs moving,
  with the standard two-layer broad phase.
- **Step:** fixed 1/120 s, at most 8 steps per `advance(seconds)`, remainder carried; single-threaded
  `JobSystemSingleThreaded` and a fixed-size temp allocator, so a run is deterministic on one machine.
- **Write-back:** after each `advance`, dynamic and kinematic bodies' positions and rotations go to
  `PositionComponent.localPosition` / `localRotation` (parents are ignored, as the Scene view ignores them; an entity
  with a parent and a body gets one warning).
- **Game access:** `bodyOf(entity)` returns a small facade (`applyForce`, `applyTorque`, `velocity`,
  `moveKinematic(target, dt)`); `contacts()` lists the entity pairs that touched in the last step with their relative
  speed, collected by a contact listener into a list the world owns and clears each step; `addRope(a, anchorA, b, anchorB, length)` and `addConstraint(...)` return handles that
  can be removed. Removing an entity removes its body and constraints.
- **Close:** `PhysicsWorld` owns every native object it creates in one list and closes them in reverse order; after
  `close()` every method throws `IllegalStateException("physics world is closed")`.

### 5. Rope tension without a lambda

For each rope, after a step: let `n` be the unit vector from anchor A to anchor B and `d` their distance. If
`d < max - 1 mm` the tension is `0`. Otherwise tension is the impulse the constraint removed along `n`, recovered from
body B's velocity: `T = m_B * ((v_B,before + a_B * dt) - v_B,after) . n / dt`, where `a_B` is the acceleration from the
forces the game applied plus gravity, recorded before the step (and symmetrically for A when B is static or
kinematic). Clamped at `0`. For one rope per body this matches the constraint impulse; the "hanging weight" scenario
bounds it at 5%. When two ropes share a body (the control-line plane), the measured value is their combined tension,
split between them by the share of their direction along `n`. That is enough for a HUD and a slack check.

**Alternative rejected:** modelling each line as a stiff spring force instead of a Jolt constraint. Tension would be
exact, but a stiff spring at 120 Hz is unstable for 15-20 m lines, and the constraint gives slack for free.

### 6. Play protocol

The IDE opens a loopback `ServerSocket` on an ephemeral port and starts the play process with
`--port <p> --token <t>`. The process connects and sends `hello {protocol: 1, token, module}`. A mismatched token
closes the socket; an unsupported protocol gets an `error` and the process exits. Stdout and stderr are not used for
the protocol, so game logging cannot corrupt it; the IDE keeps their last 200 lines for the failure notification.

Frames are length-prefixed. Commands and events are UTF-8 JSON; poses are binary:

| Direction | Frame |
|---|---|
| IDE -> host | `load {sceneText, projectDir, selection}`, `play`, `pause`, `step`, `stop`, `input {kind, key / button, x, y}`, `bye` |
| host -> IDE | `ready`, `poses` (binary: frame no, sim time, count, then per entity id `int`, position `3 x float`, rotation `4 x float`), `lines` (binary: extra debug segments with colors from game systems), `telemetry {key: value}`, `error {message}` |

Poses are sent at most 60 times a second. The host exits when the socket closes, so a dead IDE never leaves a play
process behind.

### 7. Choosing what to launch

- **`play.json` present** (`{protocol, module, classpath: [absolute paths], jvmArgs?}`), written by the game's export
  task next to the schema: launch `<IDE's JBR>/bin/java` with that classpath and
  `net.nevinsky.abyssus.physics.play.PlayHostMain <module>`. Missing classpath entries or another protocol stop Play
  with the "export the game again" message.
- **No `play.json`:** launch the same main with the plugin's own `play-host/` folder: `runtime`, `core`, `gdx-model`,
  libGDX, Jackson, Ashley, `physics` and jolt-jni with the four platforms' release natives, and module
  `PhysicsOnlyPlayModule`.

`PlayModule` (in `physics`, which every game with play mode already depends on) has `components(): ComponentRegistry`,
`systems(world, engine, selection): List<EntitySystem>` and `input(event)`; `PlayHostMain` builds the engine with
`SceneLoading`, a `PhysicsWorld`, the module's systems, and runs the loop on its own thread at 120 Hz.

### 8. IDE side

- **Extension points** (root plugin): `sceneOverlay` (`SceneOverlay.draw(view: OverlayView, lines: LineSink)`,
  called inside `GdxRuntime.withContext` after the scene, twice: once depth-tested, once on top, for the selection),
  and `sceneSimulation` (`SceneSimulationProvider.start(sceneText, projectDir, selection): SceneSimulation` with
  `pause`, `resume`, `step`, `stop`, `input`, and a `poses(): Map<String, PlacementTransform>?` snapshot). A provider
  or overlay that throws is disabled for that view with one logged error.
- **Pose overrides:** `SceneViewPanel` applies the simulation's latest snapshot through `ScenePreview` (generalised
  from `DragResult` to a placement override), so the renderer, picking and markers see simulated poses with no other
  change.
- **Play state in the panel:** a `PlayState` machine (`IDLE -> STARTING -> PLAYING <-> PAUSED -> IDLE`, plus `FAILED`)
  with no Swing or GL, owned by `SceneViewPanel`; document listeners stop play before an edit applies; the W / E / D
  bindings and gizmos are disabled while not `IDLE`; key and mouse events are forwarded instead; Escape stops.
- **Abyssus Physics:** `PhysicsOverlay` (wireframes from `SceneContent` + the physics components' JSON, so no
  `runtime` engine is needed per frame), `PlayProcessLauncher` (process, socket, timeouts, stderr tail),
  `PlayClient` (protocol, a reader thread that publishes pose snapshots atomically).

### 9. Threads

| Piece | Thread | GL / native |
|---|---|---|
| `PhysicsWorld` build, step, write-back, rope tension | the play host's loop thread (or a test thread) | Jolt natives, play process only |
| Play host socket writer / reader | two threads in the play process | none |
| `PlayProcessLauncher`, `PlayClient` reader | pooled thread / one reader thread per play | none |
| `PlayState` transitions, document listeners, toolbar | EDT | none |
| Overlay drawing, applying pose snapshots | AWT render thread inside `GdxRuntime.withContext` | GL |

### 10. Testable without Swing, GL or the IDE

`PhysicsWorld` and every `physics-simulation` scenario; shape validation; rope tension; the protocol codec (frames
round-trip, bad token, wrong version); `PlayHostMain` end to end against a test socket; `PlayState` transitions;
overlay geometry (`PhysicsOverlayGeometry`: collider and constraint segments as data, checked without drawing);
launch selection from `play.json`. The toolbar, focus and key forwarding, and real drawing are runIde checks.

### 11. Test fixture

`src/test/testData/project/Physics`: a copy of `Untitled` whose `Main Scene` gives `Model 0` a dynamic rigid body and
a box collider (half extents `0.5`), `Terrain` a `HEIGHT_FIELD` collider, and `Model 2` a rope to `Model 0`
(maximum `3`), under their short names in a native scene.

## Risks / Trade-offs

- **Multi-plugin build may not work as hoped.** → Spike first (task 1.1) with the fallbacks in decision 3.
- **Tension is an estimate.** → Bounded by a test at 5%; documented as measured, not exact. If a later jolt-jni
  exposes the lambda, `RopeTension` switches to it with the same tests.
- **Play host size.** Four platforms' natives (~5 MB each) plus libraries in `play-host/`. → Accepted for v1; a
  per-OS download is a later optimisation.
- **Determinism is per machine.** Cross-platform determinism needs Jolt's cross-platform build. → The spec only
  promises same machine.
- **A blocked game module.** → The 20-second connect timeout and killing the process tree on Stop or failure.
- **Gizmo and play state interplay.** → `PlayState` gates interaction in one place and is unit-tested.

## Migration Plan

Additive: projects without physics components see nothing new; without Abyssus Physics installed, nothing changes
in Abyssus except two unused extension points. Rollback is removing the plugin or reverting the change.
