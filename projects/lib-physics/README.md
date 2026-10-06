# physics

Plain JVM physics for native scenes, through [Jolt](https://github.com/jrouwe/JoltPhysics) and its JVM binding
jolt-jni 6.1.1 (`com.github.stephengold`, MIT). No IntelliJ or plugin imports. It is constructor-wired, with no
`object` or `companion object`, and `checkNoSingletons` runs as part of `check`. Depends on `runtime` and jolt-jni.

Jolt's natives load only in a game or the play host, never in the IDE. Only `jolt/` imports Jolt. Component classes
and the play protocol do not.

## Components

Declared with `@SceneComponent` / `@Field` (see `projects/lib-runtime/README.md`) and registered by `PhysicsComponents`.

| Component | Fields (default) |
|---|---|
| `RigidBodyComponent` | `motionType` (`DYNAMIC`; `STATIC`, `KINEMATIC`), `mass` (`1` kg, > 0), `friction` (`0.2`), `restitution` (`0`), `linearDamping` / `angularDamping` (`0.05`), `gravityFactor` (`1`) |
| `ColliderComponent` | `shape` (`BOX`; `SPHERE`, `CAPSULE`, `CONVEX_HULL`, `HEIGHT_FIELD`), `halfExtents` (`0.5, 0.5, 0.5`, each > 0), `radius` / `halfHeight` (`0.5`, > 0), `offset` (`0, 0, 0`) |
| `ConstraintComponent` | `kind` (`DISTANCE`; `HINGE`, `FIXED`), `other` (entity, `-1` = a fixed point in the world), `anchor` / `otherAnchor` (local points; `otherAnchor` is a world point without another entity), `minDistance` (`0`), `maxDistance` (`1`), `hingeAxis` (`0, 1, 0`) |

A distance constraint with minimum `0` is a rope. A convex hull is built from the vertex positions of the entity's
model, and a height field from its terrain's `terrain.data`. Both are read by `PhysicsAssets` through `core` and
`gdx-model`, with no GL.

## The world

`PhysicsWorld(engine, PhysicsAssets(projectDir, json), log, JoltNatives())`:

- **Build:** one body per entity with a collider: dynamic, kinematic or static as its rigid body says, static without
  one. A rigid body without a collider is left out with one warning. Each entity's pose comes from its
  `PositionComponent`. Boxes, hulls and height fields scale non-uniformly; spheres and capsules take the largest axis
  of the scale, with one warning. Parents are ignored, with one warning per entity that has one. Then one constraint
  per `ConstraintComponent`.
- **Bad input** is refused before it reaches Jolt, with one warning naming the entity and the field, and the rest of
  the scene is simulated. This covers a value that is not finite, a size not greater than `0`, a hull from fewer than
  4 points not in one plane, a terrain without heights, and a maximum distance below the minimum.
- **Step:** `advance(seconds)` runs fixed 1/120 s steps, at most 8 per call. It carries the fraction of a step that is
  left and drops whole steps beyond the limit. Poses of dynamic and kinematic bodies go to
  `PositionComponent.localPosition` / `localRotation` after each call; scale is never changed. `step()` runs exactly
  one. Single-threaded jobs and a fixed temp allocator make a run deterministic on one machine.
- **Game access:** `bodyOf(entity)` (`applyForce` / `applyTorque` act on every step of the next `advance`,
  `velocity`, `setVelocity`, `moveKinematic`, `setPose`: put a dynamic or kinematic body at a pose at once, at rest), `contacts()` (the pairs that touched during the last `advance`, with
  their relative speed), `addRope`, `addConstraint` and `remove(constraint)`. Removing an entity from the engine, or
  `removeEntity`, removes its body and its constraints.
- **Close:** `close()` removes every constraint and body and releases every native object the world created, in
  reverse order. After that, every method throws `IllegalStateException("physics world is closed")`.

## Rope tension, as measured

jolt-jni 6.1.1 does not expose a distance constraint's impulse, so `PhysicsConstraint.tension` is measured from the
motion of the body the rope pulls (`RopeTension`). That is the other entity's body when it is dynamic, else the
holding entity's. The force the ropes applied in a step is `m * (v_after - (v_before + a * dt)) / dt`, where `a` is
gravity plus the game's forces. A rope whose anchors are more than 1 mm closer than its maximum reads `0`. Ropes
pulling the same body share the force by how far each points along it. Contacts and damping during the step also
change the velocity, so the value is an estimate. Tests hold a hanging 1 kg weight to 9.81 N within 5%.

## Play in Abyssus

Abyssus Physics runs Play in a separate process, `PlayHostMain`. `PlayHost` loads the scene text it is sent, builds a
`PhysicsWorld` and runs at 120 Hz. Each frame it runs the game's systems, then `advance`. It sends poses at most 60
times a second, over a loopback socket that speaks the play protocol (`play/PlayProtocol.kt`, version
`PLAY_PROTOCOL` = 1). The host exits on `bye` or when the socket closes.

A game names what Play runs with a `PlayModule`. The module provides its components (without the physics ones), the
systems to run before each advance, input handling, telemetry and extra debug lines. `PlayExportMain` writes
`<project>/abyssus/play.json` (`{protocol, module, classpath}`, stable bytes) next to the component schema, with the
classpath it runs on made absolute. A game wires both exports as Gradle tasks:

```kotlin
tasks.register<JavaExec>("exportPlay") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.physics.play.PlayExportMain")
    args("com.example.game.GamePlayModule", rootProject.file("assets-project").absolutePath)
}
tasks.register("exportAbyssus") { dependsOn("exportComponentSchema", "exportPlay") }
```

The runtime classpath must hold `physics` and the jolt-jni natives for the platforms the game is played on.
Without `play.json`, Abyssus Physics runs its bundled play host with `PhysicsOnlyPlayModule`, which runs physics
alone and reports each rope's tension. Export again whenever the classpath changes. A `play.json` naming a jar that
no longer exists stops Play with that message.

## Natives

`JoltNatives.load()` extracts the platform's library from the classpath to
`<java.io.tmpdir>/abyssus-jolt-jni-6.1.1/` and loads it. It then sets Jolt up (allocator, callbacks, factory, types),
once per process. Tests use the build machine's `DebugSp` jar. The `playHost` configuration resolves the four
`ReleaseSp` jars (Linux64, Windows64, MacOSX64, MacOSX_ARM64) that the play host ships.

Tests run with `./gradlew :lib-physics:test`. They use the `Physics` fixture (`projects/plugin-abyssus/src/test/testData/project/Physics`).
Required behavior is in the `physics-simulation` and `physics-components` specs.
