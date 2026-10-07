# Tasks

All code is in `projects/app-game-control-line`, with new code in the package `pattern`
(`net.nevinsky.abyssus.app.game.controlline.pattern`). Run single tests with
`./gradlew :app-game-control-line:test --tests '<class>'`. Manual checks (M1, M2, ...) use
`./gradlew :app-game-control-line:run`, and they never edit the committed `project/ControlLine`.

## 1. Flyability spike

- [ ] 1.1 Add a scripted pilot in the test sources (`pattern/ScriptedPilot.kt`). It takes a target path as climb angle
  over progress and sets the handle tilt each step with a feedback loop. Fly with `Stunter` on `FieldFlight`:
  - an inside loop;
  - an inside square loop at full tilt;
  - an inside triangular loop.

  Record each corner's radius and each loop's top latitude in `PatternFlyabilityTest`. Verify the test runs and prints
  the numbers.
- [ ] 1.2 If any square corner needs more than 2 m radius at full tilt, tune `Stunter`'s `elevatorEffect` and/or
  `pitchDamping` in `project/ControlLine/scenes/Field.scene`, editing only those numbers. Then assert in
  `PatternFlyabilityTest` that corners are at most 2 m and that the loop top reaches 45°. Verify that
  `PatternFlyabilityTest` and `FlightTest` pass; `FlightTest` keeps Full up loops the plane.

## 2. Samples and hemisphere geometry

- [ ] 2.1 Extend `TrackSample` and `SphereTrack.sample`, keeping the existing fields unchanged:
  - the offset from the pilot's ground point;
  - the height above the ground;
  - the plane's up vector;
  - whether the engine is running.

  Update `Flight.update` to pass them in. Verify that `SphereTrackTest` passes plus a new case (height and offset of a
  plane at 18 m, 30° up), and that `ManeuversTest` and `FlightTest` are unchanged.
- [ ] 2.2 Add `HemisphereGeometry`, which handles:
  - the centre 1.5 m above the pilot's ground;
  - the unit direction, latitude and azimuth in laps;
  - great circles and small circles;
  - distance in metres for radius R.

  Verify with `HemisphereGeometryTest`: the base at latitude 0, the 45° parallel, the zenith, ⅛ lap at the base,
  and a small circle of radius 22.5° touching the base and the 45° parallel.
- [ ] 2.3 Add `PathTokens`, which reads a sample stream as tokens (straight, corner, arc, level, ground roll,
  touchdown), with inside and outside taken from the plane's up. Verify with `PathTokensTest`:
  - an inside loop reads as `Arc(inside, 360)`;
  - an inverted outside loop reads as `Arc(outside, 360)`;
  - a square reads as 4 corners and 4 straights;
  - a path through the zenith keeps its side.

## 3. Ideal tracks

- [ ] 3.1 Add `Schedule`, with the 15 manoeuvres, their K and their labels. Verify with `ScheduleTest`: the order and
  K of rule 4.2.12, and K summing to 130.
- [ ] 3.2 Add `IdealTracks` for Take-off, Reverse wingover, inside and outside loops, inverted laps and Landing,
  built from an anchor with tagged segments and progress. Verify with `IdealTracksTest`: the loop circles touch the
  base and the 45° parallel, the wingover passes the zenith with a ½-lap inverted segment, take-off levels over the
  release point, and the landing glide is 1 lap.
- [ ] 3.3 Add `IdealTracks` for the squares, the triangles and the hourglass. Verify with `IdealTracksTest`:
  - square width ⅛ lap with vertical sides;
  - triangle legs 30° off vertical with the apex on the 45° parallel;
  - hourglass crossing at the 45° parallel, symmetric about its axis.
- [ ] 3.4 Add `IdealTracks` for the horizontal, square horizontal, vertical and overhead eights and the four-leaf
  clover. Verify with `IdealTracksTest`:
  - the eights' loops meet at one crossing point;
  - the vertical eight spans 0°, 45° and 90°;
  - the overhead eight's bottoms lie on the 45° parallel;
  - the clover's ¾ loops have equal diameters, with 45° and vertical connecting lines.
- [ ] 3.5 Add a test helper, `SyntheticFlight` in the test sources, that flies an ideal track as `TrackSample`s at a
  set speed, with perturbations: offset, scale, rounded corners, an extra repeat, and a height offset on base
  segments. Verify that `SyntheticFlightTest` round-trips an ideal loop's samples back to the same directions.

## 4. Recognition and marks

- [ ] 4.1 Add a matcher per manoeuvre (`Matchers`), as in design decision 3. Verify with `MatchersTest`, per
  manoeuvre:
  - the ideal track is recognised with its repeat count;
  - one repeat more or fewer is recognised as wrong repeats;
  - squares are not loops, and eights are not loops (spec: Recognising the manoeuvre);
  - overhead eights are counted through the zenith;
  - rounded corners (2.5 m) still read as squares.
- [ ] 4.2 Add `MarkWeights` and `Grader`, as in design decision 5: progress matching, criteria, capped deductions, and
  the mark clamped to 1 to 10 in tenths, keeping each deduction. Verify with `GraderTest`:
  - every ideal track of the 15 marks 10.0;
  - bottoms 0.5, 1.0 and 2.0 m above the tolerance give non-increasing marks, all at least 1.0;
  - squares ¼ lap apart mark lower than superimposed ones;
  - corners of 4 m radius mark lower than corners of 1 m;
  - a ½-lap glide marks lower than a 1-lap glide;
  - a wobbly exit does not change the mark.
- [ ] 4.3 Add `PatternJudge` (the sequencer) and `ScoreSheet`, with the zero rules: omitted, not completed, wrong
  repeats, too soon, after 7:00, crashed, the landing rules, and no judging between the clover and the landing. Add
  the integer-tenths sum. Verify with `PatternJudgeTest`, using synthetic whole flights:
  - a perfect flight scores 1300.00;
  - the sum 8.5 × 2 + 7.2 × 8 gives 74.60;
  - inverted laps left out are omitted;
  - a loop flown again later changes nothing;
  - a third square scores wrong repeats;
  - a square given up scores not completed;
  - outside loops 1 lap after the inverted laps score too soon;
  - a clover ending at 7:02 scores after 7:00;
  - a crash in the triangles scores crashed from there on;
  - a landing at 7:05 scores 0.
- [ ] 4.4 Add a `FlightJudge` hook to `Flight.takeoff(azimuth, judge)` and feed it samples and the flight end. Verify
  with:
  - `FlightTest`: a new case where an F2B judge on a `Stunter` flight, after take-off and 2 level laps flown by
    `ScriptedPilot`, marks Take-off between 1 and 10, and the arcade score is still kept;
  - `ControlLinePlayTest`, unchanged (Play has no judge).

## 5. Fuel and score boards

- [ ] 5.1 Set `Stunter`'s `PlaneComponent.fuelTime` to `360` in `project/ControlLine/scenes/Field.scene`, changing
  nothing else in the file. Verify with:
  - `GameFlowTest`: the bundled planes case now asserts 360;
  - a `FlightTest` case: Stunter's engine has thrust 0 after 360 s;
  - `BundledProjectTest`, passing.
- [ ] 5.2 Add `PatternScoreTable` for `f2b-scores.json`, with boards per level (`pro`, `mid`, `noob`), ranking,
  `qualifies` and `add` per board, atomic writes, and `.bad` handling. Verify with `PatternScoreTableTest`:
  - a Mid flight enters only the Mid board, with its 15 marks;
  - the eleventh flight on a board;
  - earlier flights rank first among equal scores;
  - text that is not JSON becomes `f2b-scores.json.bad` with empty boards;
  - `scores.json` is untouched.

## 6. Flow and screens

- [ ] 6.1 Extend `GameFlow` as in design decision 7:
  - `Mode` and `AssistLevel`;
  - the Free flight and F2B pattern entries, disabled when there are no planes or no stunt plane;
  - `AssistSelect` (Pro and Mid);
  - stunt-only plane select;
  - `Flying.mode`, with retry keeping it;
  - `FlightReport` with a `Free` or `Pattern` result;
  - name entry and saving to the right table;
  - score tabs that open on the saved flight's tab.

  Update `FlightSession` to take the mode and build the judge. Verify with `GameFlowTest`, existing cases adapted plus:
  - F2B goes to assist select;
  - Mid then plane select offers only `Stunter`;
  - no stunt plane disables F2B, with its message;
  - retry keeps Pro;
  - a Pro flight that ranks first opens Scores on the Pro tab, highlighted;
  - Free flight is saved to `scores.json` only.
- [ ] 6.2 Update `GameUi`:
  - the main menu entries;
  - the assist select screen;
  - the HUD per mode: Free as today; Pro with tension, fuel and `m:ss / 7:00`; Mid adding the next manoeuvre with K,
    the pause laps out of 1½, the 1 to 3 m warning, and the mark toasts;
  - the 15-row score sheet on GAME OVER;
  - the score table tabs, with Left and Right and the F2B damaged-file message.

  Verify by compiling (`./gradlew :app-game-control-line:compileKotlin`), then with manual checks:
  - M1: Free flight plays as before;
  - M2: F2B → Mid → Stunter shows Take-off (K 2) as next, the clock counts, and the toast `Take-off x.x × 2 = …`
    appears after the 3rd lap;
  - M3: Pro shows no manoeuvre names or toasts;
  - M4: a crash shows GAME OVER with the sheet, with "crashed" rows;
  - M5: Scores shows Free flight, Pro and Mid tabs, switched with Left and Right.
- [ ] 6.3 Update `projects/app-game-control-line/README.md`:
  - the two modes and the assist levels;
  - the F2B schedule with K;
  - the zero rules;
  - how marks are given, with the `MarkWeights` table and the game simplifications (touchdown ends the landing; no
    Starting or preparation time);
  - `f2b-scores.json`;
  - the `pattern` package in the code table.

  Verify with `scripts/check-docs.sh`.

## 7. Integration

- [ ] 7.1 Fly a whole F2B pattern at Mid with `Stunter` (M6). Check that every manoeuvre flown appears on the sheet
  with a non-zero mark, the engine stops before 7:00, and a gentle dead-stick landing is marked. Tune `MarkWeights`
  if marks feel off, keeping `GraderTest` green.
- [ ] 7.2 Run `./gradlew check` and `scripts/check-docs.sh`. Verify that both pass.

## Workflow follow-up

- `add-f2b-pro-debrief`: the replay of the ideal and flown tracks, and the sheet opened from the score table.
- `add-f2b-noob-guide`: the Noob level and the ideal track drawn ahead.
- Archive this change after review, merging `control-line-f2b-pattern` and `control-line-f2b-judging` into
  `openspec/specs/`.
