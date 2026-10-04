# Tasks

## 1. Module and project

- [ ] 1.1 Add `:games:control-line` (design decision 1): `settings.gradle.kts` includes it; `application` with the
      main class; `:physics`, the full LWJGL3 backend, desktop natives and the build machine's Jolt natives; a `Main.kt`
      opening an empty 1280 x 720 window. Verify: `./gradlew :games:control-line:build` succeeds, and
      `./gradlew :games:control-line:run` opens a window that closes cleanly (by hand)
- [ ] 1.2 Add `PilotComponent`, `PlaneComponent` and `ControlLineComponents` (with the physics components), and the
      `exportAbyssus` task (`SchemaExportMain` + `PlayExportMain` into `project/ControlLine/abyssus/`); git-ignore
      `play.json`. Verify: `ComponentsExportTest` (schema lists both components with their groups and limits; two
      exports are byte-identical) passes with `./gradlew :games:control-line:test`, and `git status` shows no
      `play.json` after `./gradlew :games:control-line:exportAbyssus`
- [ ] 1.3 Add `generatePlaneModels` (`tools/PlaneModels.kt`) and commit the three glTF planes; generate and commit the
      field terrain; copy `skybox_physical`; write `ControlLine.abss` and `scenes/Field.scene` as design decision 2,
      with starting values for each plane. Verify: `BundledProjectTest` (the scene loads with `SceneLoading` and
      `ControlLineComponents` with no warnings; three planes, a pilot, a height-field field) passes with
      `./gradlew :games:control-line:test`

## 2. Track and scoring (no physics)

- [ ] 2.1 Add `SphereTrack`. Verify: `SphereTrackTest` (azimuth, elevation, climb angle and inverted flag for
      hand-made poses: level flight, straight up, overhead, upside down) passes with
      `./gradlew :games:control-line:test --tests 'net.nevinsky.abyssus.games.controlline.track.*'`
- [ ] 2.2 Add the maneuver detectors (design decision 6). Verify: `ManeuversTest` covers each scenario of "Maneuvers
      are detected from the flight path" on synthetic paths (inside loop detected; half a loop not; figure 8
      replaces its loops), plus outside loop, inverted lap and wingover, with the same command as 2.1
- [ ] 2.3 Add `ScoreKeeper`. Verify: `ScoreKeeperTest` (three laps score 30 unmultiplied; chained loops score 50, 100,
      150 with best combo 3; combo resets after 12 s and on slack lines; landing after 30 lap points gives 230; a crash
      adds nothing) passes with `./gradlew :games:control-line:test --tests
      'net.nevinsky.abyssus.games.controlline.score.*'`
- [ ] 2.4 Add `ScoreTable` (design decision 7). Verify: `ScoreTableTest` (eleventh flight replaces the 120 entry and
      keeps order; equal scores keep the earlier flight first; a non-JSON file becomes `scores.json.bad` with an empty
      table and the flag set) passes with the same command as 2.3

## 3. Flight

- [ ] 3.1 Add `Aero` (design decision 3) as pure functions. Verify: `AeroTest` (zero airspeed gives zero lift and
      drag; lift grows with the square of speed; past 15° lift stops growing and drag doubles; no thrust after fuel
      time; line drag matches `rho * d * L * V^2 / 8` per line) passes with
      `./gradlew :games:control-line:test --tests 'net.nevinsky.abyssus.games.controlline.flight.AeroTest'`
- [ ] 3.2 Add `LineRig` (design decision 4) on `PhysicsWorld`. Verify: `LineRigTest` (takeoff places the plane at its
      line length along the circle with taut lines; after half a lap the handle faces the plane and both lines end at
      the leadouts; below 1 N the elevator stays neutral when the tilt changes) passes with
      `./gradlew :games:control-line:test --tests 'net.nevinsky.abyssus.games.controlline.flight.LineRigTest'`
- [ ] 3.3 Add `FlightSystem` and `FlightOutcome` (design decision 5). Verify: `FlightOutcomeTest` (full down from level
      flight crashes; a 2 m/s upright touch after the engine stops lands; a 2 m/s touch with the engine running does
      not end the flight; 2 s slack in the air crashes as "lines went slack") passes with
      `./gradlew :games:control-line:test --tests 'net.nevinsky.abyssus.games.controlline.flight.FlightOutcomeTest'`
- [ ] 3.4 Tune `Aero`'s constants and the three planes' scene values until the flight scenarios pass. Verify:
      `FlightTest` (Trainer above 1 m within 5 s with neutral handle; Stunter's full up passes vertical and comes over
      the top within one lap; level-flight tension within 30% of `m * v^2 / L`; doubled thrust gives a higher steady
      speed; Racer's thrust is 0 after 60 s; Trainer flies 10 level laps with a held trim without crashing) passes with
      `./gradlew :games:control-line:test --tests 'net.nevinsky.abyssus.games.controlline.flight.FlightTest'`; record
      the tuned constants in `games/control-line/README.md`

## 4. Flow and input (no window)

- [ ] 4.1 Add `GameFlow` (design decision 8). Verify: `GameFlowTest` (Start opens plane select; no planes disables
      Start with the message; planes listed `Racer`, `Stunter`, `Trainer`; Escape pauses and Resume continues; a
      high-score crash asks for a name, saves, then offers Retry / Scores / Main menu; Retry restarts the same plane at
      score 0; Scores from GAME OVER highlights the saved rank and Back returns to GAME OVER) passes with
      `./gradlew :games:control-line:test --tests 'net.nevinsky.abyssus.games.controlline.flow.*'`
- [ ] 4.2 Add `HandleInput` (design decision 9). Verify: `HandleInputTest` (W reaches full up in 0.15 s and returns to
      neutral on release; mouse Y sets tilt by distance from centre; last-used device wins) passes with the same
      command as 4.1

## 5. Screens and rendering

- [ ] 5.1 Add `FieldRenderer` with the game's terrain shader, models, sky, sun and lines, and the flight camera.
      Verify by hand with `./gradlew :games:control-line:run` (check 7.1 items 1 and 3)
- [ ] 5.2 Add the five screens and the HUD on `GameFlow` with the bundled `uiskin`. Verify: `./gradlew
      :games:control-line:test` still passes, and by hand (check 7.1 items 2, 4 and 5)
- [ ] 5.3 Write `games/control-line/README.md`: how to run, controls, how to open the bundled project in Abyssus (as a
      copy, never the committed folder), `exportAbyssus`, the tuned constants; add the module to `AGENTS.md`
      (layout, commands) and `docs/ai/architecture.md`. Verify: `scripts/check-docs.sh` passes

## 6. Play in Abyssus

- [ ] 6.1 Add `ControlLinePlay` (design decision 10). Verify: `ControlLinePlayTest` drives `PlayHostMain` over a test
      socket with selection `Trainer`: it takes off, a `W` input event raises the elevator, and a crash restarts the
      flight after 1 s, passing with `./gradlew :games:control-line:test --tests
      'net.nevinsky.abyssus.games.controlline.play.*'`

## 7. Integration

- [ ] 7.1 Run `./gradlew :games:control-line:run` and check: (1) the field, sky, pilot and parked planes are drawn;
      (2) Main menu works with keys and mouse; (3) plane select moves the camera between `Racer`, `Stunter` and
      `Trainer`; (4) a flight with `Stunter` shows the HUD, loops on full up, and a crash shows GAME OVER with name
      entry; (5) Retry flies `Stunter` again from takeoff, and Scores shows the saved flight highlighted
- [ ] 7.2 Run `./gradlew :games:control-line:exportAbyssus`, then in `./gradlew :physics-plugin:runIde` open a copy
      of `games/control-line/project/ControlLine` (with `play.json` exported for it) and check: (1) `PlaneComponent`
      and `PilotComponent` edit in the panel; (2) selecting `Trainer` and pressing Play flies it on its lines, W / S
      tilt the handle; (3) Stop restores the parked pose with the file unchanged; (4) a plane added by duplicating
      `Trainer` appears in the game's plane select after restarting the game
- [ ] 7.3 Run `./gradlew check` and `scripts/check-docs.sh`. Verify: both pass
