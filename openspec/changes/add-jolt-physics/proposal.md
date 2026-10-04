# Proposal

## Why

Games edited in Abyssus need physics, and the editor should show and run it. Otherwise a designer authors bodies
and constraints blind and finds out how they behave only in the game. Jolt is a modern, deterministic rigid-body
engine with JVM bindings. Built as an extension, physics becomes the first plugin to use Abyssus's extension points,
and users who never need it never load native physics code into their IDE. The control-line game
(`add-control-line-game`) is the first game to use it.

## What Changes

- **`physics` library module.** A plain JVM library on `runtime`, wired by constructors with no singletons. It uses
  Jolt through **jolt-jni** (the proposed binding; xJolt is the alternative if a web build is ever needed, and the
  choice is confirmed in design). It builds a Jolt world from the physics components of an Ashley engine, steps it at
  a fixed rate, writes poses back to `PositionComponent`, and releases every native object on dispose. It exposes the
  world to game systems, so a game can add forces and create constraints at run time, and reports each rope's
  tension (jolt-jni 6.1.1 does not expose a constraint's impulse, so tension is measured from the bodies' motion).
  Shapes and values are validated before they reach native code.
- **Physics components,** declared with `@SceneComponent` / `@Field` from `add-custom-components`:
  - `RigidBodyComponent`: motion type (static, kinematic, dynamic), mass, friction, restitution, damping.
  - `ColliderComponent`: box, sphere, capsule, a convex hull from the entity's model, or a height field from the
    entity's terrain; size and offset.
  - `ConstraintComponent`: distance (with min / max, so a rope is distance 0..L), hinge or fixed; the other body as an
    entity reference; anchors.
- **Abyssus Physics,** an IntelliJ plugin in this repository that depends on Abyssus. It uses Abyssus's libGDX,
  `core` and `runtime` classes and bundles only its own code (no second copy of libGDX, whose `Gdx.*` is
  process-global). It never loads Jolt natives into the IDE.
- **New Abyssus extension points:**
  - `sceneOverlay`: draws in the scene view each frame, on the render thread inside `GdxRuntime.withContext`, with a
    disposable per view. Abyssus Physics draws collider shapes and constraint anchors and lines with it.
  - `sceneSimulation`: Play, Pause, Step and Stop in the scene view's toolbar. While it runs, entity poses come from
    the simulation as transient overrides, the way a gizmo drag previews today. The scene document is never written.
    Stop restores the authored pose. Editing the scene while playing stops the simulation.
- **Out-of-process play host.** Play launches a child JVM that loads the scene (including unsaved text), runs
  `runtime` + `physics`, and streams poses, debug lines and telemetry back. The scene view sends commands and input
  events to it. The protocol is versioned at a handshake. A crash in the child stops the simulation and shows a
  notification; the IDE keeps running.
- **Games ship their own host.** A game's export task (the one that writes `components.schema.json`) also writes
  `<project>/abyssus/play.json`: the game's `PlayModule` class (components to register, systems to add, input
  mapping), the resolved classpath and the protocol version. Abyssus Physics launches that classpath, so Play runs the
  game's own code. Without `play.json` it uses its built-in classpath and runs physics only. `PlayModule` is a small
  API in `physics`.

**Fields read/written.**
- **Read:** the scene's `ecs` block, `RenderComponent` asset references (for hulls and height fields), terrain
  `.terra` data, and the new `abyssus/play.json`.
- **Written:** the physics components through the `add-custom-components` rules, when a user adds or edits them in
  Abyssus. Simulation writes nothing. The format changes only as `add-custom-components` allows.

**Out of scope.**

- Running Jolt inside the IDE process.
- "Keep simulated pose": writing simulated poses back to the scene.
- Soft bodies, vehicles, characters, ragdolls, convex decomposition of concave models.
- A web build (xJolt / TeaVM) and Android.
- The control-line game and its aerodynamics (`add-control-line-game`).
- Moving the scene view onto the Ashley engine.

## Capabilities

### New Capabilities

- `physics-simulation`: building, stepping and disposing a Jolt world from a scene's physics components, outside the
  IDE, with poses written back to the engine and constraints a game can add at run time.
- `physics-components`: the rigid body, collider and constraint components a scene can hold, their fields, defaults
  and validation.
- `scene-physics-overlay`: drawing collider shapes and constraints in the scene view.
- `scene-play-mode`: Play, Pause, Step and Stop in the scene view, run in a child process from the game's `play.json`
  or the default classpath, with transient poses, input forwarding, crash isolation and the play protocol.

### Modified Capabilities

- `abyssus-extension-points` (introduced by `add-custom-components`): adds `sceneOverlay` and `sceneSimulation`.

## Impact

- **Build:** new modules `:physics` (library) and `:physics-plugin` (the Abyssus Physics IntelliJ plugin, built
  against the root plugin), with jolt-jni and its per-platform natives in the library and the play host only. The
  build has to support a second IntelliJ plugin depending on the first. That is verified first in design.
- **Abyssus:** two extension points, the scene view toolbar and transient pose overrides alongside `ScenePreview`.
- **Tests:** headless world tests (a falling body, a rope's slack and tension, a height field from the fixture
  terrain), the protocol and its handshake, a child crash, and the overlay geometry. GL drawing and the toolbar are
  checked by hand in `runIde`.
- **Docs:** `AGENTS.md` (layout and a hard rule: extension plugins bundle only their own code, and Jolt never loads in
  the IDE), `docs/ai/architecture.md`, `sceneview/README.md`, new `physics/README.md`.
- **Depends on:** `extract-scene-runtime`, `add-custom-components`. May split into "physics library + overlay" and
  "play mode" when designed.
