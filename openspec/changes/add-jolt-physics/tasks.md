# Tasks

## 0. Decisions to confirm

- [ ] 0.1 Ask the user to confirm jolt-jni 6.1.1 (`ReleaseSp` natives for Linux64, Windows64, MacOSX64 and
      MacOSX_ARM64) as the binding (design decision 1). Verify: the answer is recorded here; if the user picks xJolt,
      stop and update design decisions 1, 4 and 5 before continuing

## 1. Build spikes

- [ ] 1.1 Spike the second IntelliJ plugin (design decision 3): an empty `:physics-plugin` with
      `<depends>net.nevinsky.abyssus</depends>` and one class calling an Abyssus class, trying the three options in
      order. Verify: `./gradlew :physics-plugin:buildPlugin` succeeds and `./gradlew :physics-plugin:runIde` starts with
      both plugins listed under Settings > Plugins; the option used is recorded in design decision 3
- [ ] 1.2 Add `:physics` (plain JVM, `checkNoSingletons`, jolt-jni API as `api`, the build machine's `DebugSp` natives
      for tests, the four `ReleaseSp` natives in a `playHost` configuration). Verify: a `NativesTest` loads the
      library and creates and closes an empty physics system with `./gradlew :physics:test`

## 2. Components and fixture

- [ ] 2.1 Add `RigidBodyComponent`, `ColliderComponent`, `ConstraintComponent` with `@SceneComponent` / `@Field` and
      the defaults of `physics-components`, and `PhysicsComponents` (a `ComponentRegistry`). Verify:
      `PhysicsComponentsTest` (defaults written as `{}`; the rope scenario writes `{"other": 0, "maxDistance": 3}`;
      the validation limits of "Physics values are validated" are declared) passes with
      `./gradlew :physics:test --tests 'net.nevinsky.abyssus.physics.PhysicsComponentsTest'`
- [ ] 2.2 Add the `Physics` fixture (design decision 11). Verify: `SceneLoadingTest`-style
      `PhysicsFixtureTest.loadsWithPhysicsComponents` reads `Model 0`'s box and body, `Terrain`'s height field and
      `Model 2`'s rope with no warnings, via `./gradlew :physics:test`

## 3. The physics world

- [ ] 3.1 Add `PhysicsWorld` build and close with shape validation (design decision 4). Verify: `PhysicsWorldTest`
      cases `rigidBodyWithoutColliderIsLeftOutWithOneWarning`, `flatModelHullIsRefused`,
      `nonFiniteValueIsRefused`, `maxBelowMinConstraintIsLeftOut`, `closedWorldRefusesUse` pass with
      `./gradlew :physics:test --tests 'net.nevinsky.abyssus.physics.PhysicsWorldTest'`
- [ ] 3.2 Add fixed-step advance and pose write-back. Verify: `PhysicsWorldTest.boxFallsOntoTheTerrain` (rest within
      `0.05` of the terrain, speed below `0.01` after 5 s), `staticTerrainStays`, `sameRunTwiceIsIdentical` and
      `hundredWorldsInARowGiveTheSameResult` pass with the same command
- [ ] 3.3 Add the game facade (forces, torques, kinematic moves, velocities, add / remove constraints, contacts,
      entity removal).
      Verify: `PhysicsWorldTest.constantThrustGivesTwentyMetresPerSecond`, `removingAnEntityRemovesItsConstraints`
      and `landingReportsAContactWithTheTerrain` pass
- [ ] 3.4 Add rope tension (design decision 5). Verify: `RopeTensionTest.hangingWeightReadsItsWeight` (9.32..10.30
      N), `slackRopeReadsZero` and `twoRopesShareTheLoad` (two parallel ropes each read about half) pass with
      `./gradlew :physics:test --tests 'net.nevinsky.abyssus.physics.RopeTensionTest'`
- [ ] 3.5 Write `physics/README.md` (components, world lifecycle, tension as measured, natives) and add the module to
      `AGENTS.md` and `docs/ai/architecture.md`. Verify: `scripts/check-docs.sh` passes

## 4. Play host

- [ ] 4.1 Add the protocol codec (design decision 6). Verify: `PlayProtocolTest` (every frame type round-trips; a bad
      token closes; protocol `2` gets an error) passes with
      `./gradlew :physics:test --tests 'net.nevinsky.abyssus.physics.play.PlayProtocolTest'`
- [ ] 4.2 Add `PlayModule`, `PhysicsOnlyPlayModule` and `PlayHostMain` (design decision 7). Verify:
      `PlayHostMainTest.playsThePhysicsSceneOverASocket` (connect, `load`, `play`, receive poses with `Model 0` falling,
      `stop`, `bye`, process exits 0) and `exitsWhenTheSocketCloses` pass with `./gradlew :physics:test`
- [ ] 4.3 Make a game's export task write `play.json` next to the schema (`{protocol, module, classpath}`), as a
      `PlayExportMain` beside `SchemaExportMain`. Verify: `PlayExportTest` (absolute classpath entries, stable output)
      passes, and `physics/README.md` documents the Gradle task; `scripts/check-docs.sh` passes

## 5. Abyssus extension points

- [ ] 5.1 Declare `sceneOverlay` and `sceneSimulation` (design decision 8) in the root `plugin.xml`, call overlays
      inside `GdxRuntime.withContext` (depth-tested, then on top), and disable a throwing overlay or provider for its
      view with one logged error. Verify: `SceneOverlayHostTest` (a fake overlay sees the entity positions of
      `Main Scene`; a throwing one is disabled once) passes with
      `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneOverlayHostTest'`
- [ ] 5.2 Generalise `ScenePreview` to placement overrides and add `PlayState` (design decision 8) to
      `SceneViewPanel`: no play controls without a provider; edits stop play first; gizmo keys disabled and input
      forwarded while playing; Escape stops. Verify: `PlayStateTest` (every transition, edit-stops-play, Escape,
      failure returns to authored poses) and a new `ScenePreviewTest` (a pose override replaces a placement; a drag
      preview still works) pass with
      `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.PlayStateTest' --tests
      'net.nevinsky.abyssus.sceneview.ScenePreviewTest'`
- [ ] 5.3 Update `sceneview/README.md` and `docs/ai/architecture.md` (the two extension points, play state, pose
      overrides). Verify: `scripts/check-docs.sh` passes

## 6. Abyssus Physics plugin

- [ ] 6.1 Generate the physics schema at build time from `PhysicsComponents` and bundle it as the plugin's
      `componentSchemas` resource; add the build check that fails on `com.github.stephengold` or
      `net.nevinsky.abyssus.physics.jolt` in the plugin's main sources. Verify: `./gradlew :physics-plugin:buildPlugin`
      contains the schema, and a temporary import of `com.github.stephengold.joltjni.Jolt` makes
      `./gradlew :physics-plugin:check` fail (then removed)
- [ ] 6.2 Add `PhysicsOverlayGeometry` and `PhysicsOverlay` (colors per motion type, height-field outline, constraint
      lines and markers, selection on top). Verify: `PhysicsOverlayGeometryTest` (a 1 m cube for `Model 0`'s box; a
      rope line between the anchors; resizing to `1, 0.5, 0.5` gives 2 m along X) passes with
      `./gradlew :physics-plugin:test`
- [ ] 6.3 Add `PlayProcessLauncher` and `PlayClient` (design decisions 6 and 7: `play.json` or `play-host/`, missing
      jar and protocol messages, 20 s connect timeout, stderr tail, process-tree kill). Verify: `PlayLaunchTest`
      (stale `play.json` names the jar; a module that never connects fails after the timeout with no process left;
      a killed host returns `FAILED` with its last stderr lines) passes with `./gradlew :physics-plugin:test`
- [ ] 6.4 Bundle `play-host/` (runtime libraries, `physics`, jolt-jni with the four release natives) in the plugin
      zip. Verify: `unzip -l physics-plugin/build/distributions/*.zip` lists `play-host/` with the four native jars,
      and the plugin's own `lib/` holds no jolt-jni jar

## 7. Integration

- [ ] 7.1 In a copy of the `Physics` project, run `./gradlew :physics-plugin:runIde` and check: (1) Add component
      offers the three physics components; (2) a zero mass is rejected; (3) Show Physics draws the green box, the grey
      terrain outline and the rope line, and the selected box shows through its model; (4) Play makes `Model 0` fall
      onto the terrain, Pause and Step work, Stop returns it to Y `3.086434` with the file and Undo history unchanged;
      (5) editing a light while playing stops play and applies the edit; (6) killing the play process shows one
      notification and the IDE keeps working; (7) without Abyssus Physics (plain `./gradlew runIde`) there are no play
      controls and physics components are read-only JSON
- [ ] 7.2 Run `./gradlew check` and `scripts/check-docs.sh`. Verify: both pass
