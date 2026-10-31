# Tasks

Run a single test class with `./gradlew :app-game-control-line:test --tests '<class>'`.

## 1. Components and scene schema

- [x] 1.1 Add `PlaneClass.SCALE`, `ScaleComponent` (prototype, firstFlight, wingspan, scale) and `VitrineTableComponent` (topHeight), register both in `GAME_COMPONENTS`, and list them and the new enum value in `abyssus/components.schema.json`. Verify with a new `ScaleComponentsTest` (a scene entity binds both components with their values; `SCALE` binds as a `planeClass`) and `BundledProjectTest` still passing.
- [x] 1.2 Add `scale: ScaleFacts?` to `PlaneChoice`, filled by `planeChoices` from the `ScaleComponent` (null without one). Verify with `GameFlowTest` cases: a `SCALE` plane with and without the component.

## 2. Scale marks and judge

- [x] 2.1 Implement `ScaleMarks` (speed, level and landing arithmetic with the spec's thresholds) free of GL and flow. Verify with `ScaleMarksTest`: the 1000-point example, spread at 5 % and 30 %, mean elevation at 15 and 45 degrees, fewer than 2 laps, and a crash.
- [x] 2.2 Implement `ScaleJudge : FlightJudge` (lap times and airborne elevations through `Laps`, live and final marks, an unfinished lap ignored). Verify with `ScaleJudgeTest` using synthetic `TrackSample` streams (steady level laps, racing and stalling laps, a crash after four laps, one lap and a landing).
- [x] 2.3 Add `FlightResult.Scale`, `Mode.Scale` and the `reportOf` branch. Verify with `GameFlowTest`: a Scale session's report carries the judge's marks and no Free laps or combo.

## 3. Scale board

- [x] 3.1 Implement `ScaleScoreEntry` and `ScaleScoreTable` beside `ScoreTable` (top 10, ties in time order, `scale-scores.json`, an unreadable file renamed to `.bad`) and `defaultScaleScoresFile`. Verify with `ScaleScoreTableTest`: ranking, the full-table cutoff of 640 against 700, ties, the `.bad` rename and that `scores.json` and `f2b-scores.json` are untouched.

## 4. Flow and screens

- [x] 4.1 Extend `GameFlow`: `scaleEnabled` and `NO_SCALE_MESSAGE`, `scale()` from the menu, `choices(Mode.Scale)`, the third branch of `flightEnded` and `nameEntered`, `ScoreTab.SCALE` and `tabOf`. Verify with `GameFlowTest`: the Scale scenarios of the game-flow delta (menu, no replica, only `Mig-3` offered, Escape back to the menu, retry keeps the mode) and that Watch AI is not offered.
- [x] 4.2 Wire the game: construct the `ScaleScoreTable` in `ControlLineGame`, pass the `ScaleJudge` to the session for a Scale flight, and show the live realism score on the flying HUD. Verify with `AiFlightLifecycleTest`-style headless session in `ScaleFlightLifecycleTest` (a Scale flight ends, reports, enters the board) and a runIde-free `GameUiModelTest` case for the HUD value.
- [x] 4.3 Update the authored UI (`assets/screens/meta.json`): the Scale menu button and its disabled message, the card's replica lines, the Scale GAME OVER rows and the Scale score tab. Update `GameUiModel` values for them. Verify with `GameUiModelTest`, `ControlLineUiAssetTest` and `GameScreensCharacterizationTest` after regenerating and reviewing `screens.golden`.

## 5. The vitrine

- [x] 5.1 Implement `VitrineLayout` (table slots by entity id, table-top height minus the model's lowest point, yaw toward the camera, ground slots beyond the last table). Verify with `VitrineLayoutTest`: a plane lands on the top whatever its size, a plane added takes the next table, extra planes stand on the ground beside the last table, and no plane is below the top.
- [x] 5.2 Apply the layout to the parked scene in `FieldScene` (the flight's own load is untouched) and read each model's lowest point from its loaded bounds. Verify with `VitrineSceneTest` on the bundled field (every plane's lowest point equals its table's top within 1 mm) and `FieldSceneModelsTest`.
- [x] 5.3 Reframe `Cameras.planeSelect` at the table-top and keep the glide. Verify with `PlaneSelectCameraTest` (the eye stays above the table-top and looks at the plane after the glide time) and `FieldRendererGlTest` with `-Dabyssus.glTests=true`.

## 6. The bundled project

- [x] 6.1 Author in `Field.scene`: four `VitrineTableComponent` tables (work or picnic models, tops measured), the `Mig-3` entity (`model_mig3`, `SCALE`, `ScaleComponent` naming the MiG-3, rigid body, convex hull) and hand-tuned `PlaneComponent` numbers. Verify with `BundledProjectTest` (planes `Mig-3`, `Racer`, `Stunter`, `Trainer` in name order; `Mig-3` is `SCALE`; `Stunter` is still the only `STUNT`) and `FieldSceneModelsTest`.
- [x] 6.2 Prove the Mig-3 flies: a flight test that takes off, flies laps and lands with a scripted pilot, and a bounds test that its glb follows the plane frame (nose +Z, up +Y, left wing +X). Verify with `Mig3FlyabilityTest` passing; if the frame test fails, re-import the glb with the chosen axes.
- [x] 6.3 Visual check in the game (`./gradlew :app-game-control-line:run`): menu shows Scale, every plane stands on its own table, the camera glides table to table and the Mig-3's card shows its prototype; fly the Mig-3 in Scale and see the score on the board. Record what was seen in the change's notes.

## 7. Documentation and integration

- [x] 7.1 Document Scale and the vitrine in `projects/app-game-control-line/README.md` (the mode, the realism marks, `scale-scores.json`, authoring a replica and a display table in Abyssus), and update `docs/ai` where it lists the game's components or fixtures. Verify with `scripts/check-docs.sh`.
- [x] 7.2 Run `./gradlew :app-game-control-line:test`, then `./gradlew check` and `scripts/check-docs.sh`, and fix what they report (including `checkShippedClasspath` and the screens golden).
