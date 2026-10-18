# lib-physics

Plain JVM physics for native scenes, through [Jolt](https://github.com/jrouwe/JoltPhysics) and its JVM binding
jolt-jni 6.1.1 (`com.github.stephengold`, MIT). No IntelliJ or plugin imports. It is constructor-wired, with no
`object` or `companion object`, and `checkNoSingletons` runs as part of `check`. Depends on `lib-core` and jolt-jni.

Jolt's natives load only in a game or the play host, never in the IDE. Only `jolt/` imports Jolt. Component classes
do not. The play host and protocol live in [`lib-game-link`](../lib-game-link/README.md), not here: a shipped game links
`lib-physics` and none of the Play bridge.

## Components

Plain Ashley component classes. Register the `PHYSICS_COMPONENTS` map with `core.ecs.ComponentRegistry.registerAll`
before loading. The IDE currently keeps these components read-only; schema annotations and export are absent.

| Component | Fields (default) |
|---|---|
| `RigidBodyComponent` | `motionType` (`DYNAMIC`; `STATIC`, `KINEMATIC`), `mass` (`1` kg, > 0), `friction` (`0.2`), `restitution` (`0`), `linearDamping` / `angularDamping` (`0.05`), `gravityFactor` (`1`) |
| `ColliderComponent` | `shape` (`BOX`; `SPHERE`, `CAPSULE`, `CONVEX_HULL`, `HEIGHT_FIELD`), `halfExtents` (`0.5, 0.5, 0.5`, each > 0), `radius` / `halfHeight` (`0.5`, > 0), `offset` (`0, 0, 0`) |
| `ConstraintComponent` | `kind` (`DISTANCE`; `HINGE`, `FIXED`), `other` (entity, `-1` = a fixed point in the world), `anchor` / `otherAnchor` (local points; `otherAnchor` is a world point without another entity), `minDistance` (`0`), `maxDistance` (`1`), `hingeAxis` (`0, 1, 0`) |

A distance constraint with minimum `0` is a rope. A convex hull is built from the vertex positions of the entity's
model, and a height field from its terrain's `terrain.data`. Both are read by `PhysicsAssets` through `lib-core` and
`lib-gdx`, with no GL.

## The world

`PhysicsWorld(engine, PhysicsAssets(projectDir, log), log, JoltNatives())` is the public facade and owns native
resources, body records, foliage, constraints and contact collection. It constructor-wires three internal collaborators
in `jolt/`: `PhysicsBodyFactory` validates and constructs ordinary bodies, `PhysicsStepper` advances the fixed-step
clock and runs forces and rope measurements, and `PhysicsPoseWriter` reads native poses for write-back and rope anchors.
They borrow the world's resources; `PhysicsWorld.close()` retains responsibility for releasing them.

- **Build:** one body per entity with a collider: dynamic, kinematic or static as its rigid body says, static without
  one. A rigid body without a collider is left out with one warning. Each entity's pose comes from its
  `PositionComponent`. Boxes, hulls and height fields scale non-uniformly; spheres and capsules take the largest axis
  of the scale, with one warning. Parents are ignored, with one warning per entity that has one. Then one constraint
  per `ConstraintComponent`.
- **Foliage:** a terrain entity's `FoliageComponent` adds static collision for the OBJECT layers of its foliage asset that
  declare a `collider` (`FoliageCollider` in `lib-core`): one compound body per nonempty chunk of the committed bake, one
  BOX, SPHERE or CAPSULE sub-shape per copy. The bake is read as written through `PhysicsAssets.foliage` (the shared
  `FoliageRuntimeLoad` policy a renderer uses, so both see the same copies; nothing is generated or written, and a stale
  bake keeps its saved copies). Each copy's pose is `FoliageMatrices` (entity transform, terrain height, lean, yaw, scale)
  with the collider offset carried by the full matrix; `FoliageColliderTransforms` refuses a sheared, reflected or
  singular copy before any native call, and a sphere or capsule on a non-uniform scale takes the largest axis, with a
  warning. A layer is admitted whole in entity id then layer order: at most 200,000 colliding copies in all and only the
  bodies the world's 10,240 leave after the ordinary ones (the world capacities are unchanged); a layer over either
  limit, a layer Jolt refuses (rolled back, the next one still built), a DETAIL layer with a collider, an invalid
  collider and an owner that is not static each get one warning and no collision. Contacts with a chunk are reported
  against the terrain entity, even when it has no ordinary body. Removing the owner, a failed layer and `close()` release
  the bodies and the compound shapes exactly once; removing a layer wakes dynamic bodies, since Jolt does not wake a
  sleeping body whose support went away. Foliage is built when the world is, so a later change to the owner's pose or to
  the bake is not seen.
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

The play host, the play protocol, `PlayModule` and `PlayExportMain` are in [`lib-game-link`](../lib-game-link/README.md),
which depends on this module. A game that wants Play puts its `PlayModule` in a separate source set on `lib-game-link`
(Control Line's `play` source set), so its main classpath and its distribution stay free of the bridge. Without
`<project>/abyssus/play.json`, Abyssus runs its bundled play host with `PhysicsOnlyPlayModule`, which runs physics alone.

## Natives

`JoltNatives.load()` extracts the platform's library from the classpath to
`<java.io.tmpdir>/abyssus-jolt-jni-6.1.1/` and loads it. It then sets Jolt up (allocator, callbacks, factory, types),
once per process. Tests use the build machine's `DebugSp` jar. `lib-game-link`'s `playHost` configuration resolves the four
`ReleaseSp` jars (Linux64, Windows64, MacOSX64, MacOSX_ARM64) that the play host ships.

Tests run with `./gradlew :lib-physics:test`. They use the `Physics` fixture (`projects/plugin-abyssus/src/test/testData/project/Physics`).
Required behavior is in the `physics-simulation` and `physics-components` specs.
