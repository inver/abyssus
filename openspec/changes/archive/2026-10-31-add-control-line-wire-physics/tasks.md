## 1. Wire model

- [x] 1.1 Add `Wire` (density, modulus, area, mass per metre, stiffness) with a unit test checking 1.54 g/m and EA/L = 2.6 kN/m for 15 m at 0.5 mm
- [x] 1.2 Set `lineDiameter` to 0.0005 in `PlaneComponent`, `Field.scene` and `components.schema.json`; verify `BundledProjectTest` and a new test asserting 0.0005 on every bundled plane

## 2. Plane mass and stretch

- [x] 2.1 Add the one-third wire tip mass to the plane's mass when a flight is set up; verify a test that `Trainer`'s simulated mass is its authored mass plus about 15.4 g
- [x] 2.2 Make rope length follow `L + T/k` with 1 mm hysteresis in `LineRig`; verify `LineRigTest` for 4-12 mm stretch at 30 N and continuous tension across a re-add
- [x] 2.3 Run `./gradlew :app-game-control-line:test` and fix balance regressions (tension within 30% of `m v²/L`, takeoff within 5 s, F2B tests)

## 3. Shape

- [x] 3.1 Add `WireShape` computing each line's points from tension, weight and drag; verify unit tests for taut (within 20 cm, bowed back) and slack (below chord) cases
- [x] 3.2 Expose the shapes from `FlightSession` (smoothed tension) and verify a flight test on a flying `Trainer`

## 4. Rendering

- [x] 4.1 Draw wire ribbons with true-width opacity and sun glint in `FieldRenderer`; verify a unit test of the width/opacity function (15 m, 1080p, 67° -> opacity below 0.2) and `FieldShadowsGlTest`-style GL test with `-Dabyssus.glTests=true`
- [x] 4.2 Use the ribbons in `ControlLineGame` for the flight lines; verify by running `./gradlew :app-game-control-line:run` and checking the lines in flight

## 5. Docs and specs

- [x] 5.1 Document the wire model in `projects/app-game-control-line/README.md` and run `scripts/check-docs.sh`
- [x] 5.2 Run `./gradlew check` and `openspec validate add-control-line-wire-physics`
