# Tasks

## 1. Danger and share (pure)

- [ ] 1.1 Add `ai/DangerGauge` with the ground, inverted and slack terms and their bound constants (design decision 2).
  Verify: `./gradlew :app-game-control-line:test --tests '*DangerGaugeTest'` covers level flight upright and inverted at
  1.5 m (0), a dive at the base (above 0 before 1.2 m of height), a 1 m/s upright glide (0), inverted at 0.4 m (1), slack
  for 0.3 s (0) and 0.8 s (0.5), and the ground roll below `CONTROL_SPEED` (0).
- [ ] 1.2 Add `ai/InstructorShare`, which steps the share, the 1 s hold and the 0.5 s fall, and records takeover spans at the 0.5
  crossings (design decision 4). Verify: `*InstructorShareTest` covers the immediate rise, the hold, the fall, a rise
  again during the fall, a 0.3 peak that opens no takeover, and two takeovers with their start and end times.

## 2. Rescue and the control source

- [ ] 2.1 Add `ai/RescuePilot`: base `Parallel` in the plane's own direction while the engine runs; with the engine
  stopped, a turn-over if needed and then `F2bFigures.landing`. Rebuild on 3 s without progress, and drop the course when
  the share returns to 0 (design decision 3). Verify: `*RescuePilotTest` checks the course chosen for upright,
  clockwise-inverted and engine-out observations, and the rebuild.
- [ ] 2.2 Add `ai/InstructorControl(student, gauge, rescue)`, which blends `(1 - share) * student + share * rescue` and
  exposes the danger, share, both tilts, the holding flag and the takeovers (design decision 1). Verify:
  `*InstructorControlTest` with scripted student and rescue sources checks the blend, that the student is still asked
  every step, and that the rescue tilt is 0 and skipped while there is no danger and no share.
- [ ] 2.3 Add a headless rescue flight beside `ai/AiFlight`: an instructed Free flight of each bundled plane (`Mig-3`,
  `Racer`, `Stunter`, `Trainer`), with two level laps at the base, then the handle held full down for 3 s, then released.
  Verify: `*InstructorRescueTest` asserts for each plane that the flight has not ended by 30 s, at least one takeover
  happened, and the share is 0 within 1.5 s of the danger reaching 0 after release. If a plane fails, follow the design's
  first risk and update the spec bounds together with the constants.
- [ ] 2.4 No false takeovers in a good flight: wrap `InstructorControl` around the Watch AI bot as the student for the
  bundled `Stunter` routine. Verify: `*InstructorRescueTest` (or an `F2bAi*` test) asserts zero takeovers, a Landed end,
  and the same 15 positive rows as Watch AI.

## 3. Flow and report

- [ ] 3.1 Add `Screen.Flying.instructor`, and add `GameFlow.instructor`, `toggleInstructor()` and `instructorOffered`.
  `choosePlane` and `retry` carry the flag; `watchAi` ignores it (design decision 5). Verify: `flow/GameFlowTest` cases
  for the toggle on plane select in Free, Pattern (each level) and Scale, its kept state across modes, Retry, Watch AI
  ignoring it, and the off state of a new `GameFlow`.
- [ ] 3.2 Add `Takeover` and `FlightReport.takeovers`, fill them in `reportOf` from the runner's instructor, and have
  `flightEnded` send flights with takeovers straight to `GameOver(rank = null)`. Verify: `GameFlowTest` checks that an
  assisted Free flight with a top score opens no name entry and leaves `ScoreTable` unchanged, that the same holds for
  Pattern (board and recording store unchanged) and Scale, and that an untouched instructed flight enters name entry as
  before.
- [ ] 3.3 Have `FlightRunner.start` build `InstructorControl` around `HumanControl` for instructed human flights and expose it
  for the HUD and the report. Verify: an instructed case in `ControllerFlightLifecycleTest` or a new
  `InstructorFlightLifecycleTest`: keys and mouse still reach `HandleInput`, pause freezes the share, and a non-instructed
  flight has no instructor.

## 4. Screens

- [ ] 4.1 Add the bindings `instructorOffered`, `instructorOn`, `isInstructed`, `handleTilt`, `ghostVisible`,
  `ghostTilt`, `ghostCue`, `instructorHolds` and `instructorFact`, the action `toggleInstructor`, and the focus id
  `ps-instructor` to `GameUiContract`. Publish them in `GameUiModel`, with the `Up!` / `Down!` cue rule (0.2), and map
  the I key on plane select. Add the GAME OVER facts row. Verify: `screens/*` model tests for the bindings in a safe,
  warning and takeover state, the cue thresholds, the I key, and the facts rows "Not needed" and "Took over 2 times, not
  saved".
- [ ] 4.2 Edit `project/ControlLine/assets/screens/meta.json`. On top of the gamepad and scale changes already in the
  working tree, keep their keys and numbers. Add the plane select checkbox, the two tilt bars, the cue and the
  INSTRUCTOR label on `hudFree`, `hudPattern` and `hudScale`. Verify: the existing screens-asset contract test (every
  binding and action the asset uses is published) passes, and `BundledProjectTest` still loads the project.
- [ ] 4.3 Manual check: run `./gradlew :app-game-control-line:run`, then:
  1. Turn the instructor on with I, the mouse and a gamepad.
  2. Dive Trainer from the base: the ghost and `Up!` appear first, then INSTRUCTOR, then the handback.
  3. Pause during a takeover.
  4. Finish one untouched flight and one assisted flight, and look at GAME OVER for each.
  5. Check that Watch AI shows no instructor.

## 5. Debrief

- [ ] 5.1 Add `TrackKind.INSTRUCTOR` and `INSTRUCTOR_COLOR`. Pass the takeovers into `DebriefModel` from GAME OVER. Split each
  manoeuvre's flown line by span time, name the colour in the legend, and set the overlapped-manoeuvre note (design
  decision 7). Verify: `pattern/DebriefModelTest` cases for a manoeuvre with a takeover (an INSTRUCTOR line and the note),
  one without, and a board-row debrief with no spans.
- [ ] 5.2 Manual check in `./gradlew :app-game-control-line:run`: open the debrief of an assisted Mid flight. The
  takeover part of the loops is drawn in the Instructor colour, the legend names it, and the note shows.

## 6. Docs and integration

- [ ] 6.1 Update `projects/app-game-control-line/README.md`: the I key in the Controls table, and a new Instructor section
  covering danger, the ghost handle, takeover and handback, the untouched-or-assisted rule, and the debrief colour. Add the
  instructor to `docs/ai/architecture.md` only where it lists Control Line's control sources. Verify:
  `scripts/check-docs.sh`.
- [ ] 6.2 Run `./gradlew check` and `scripts/check-docs.sh`. Both pass, including
  `:app-game-control-line:checkShippedClasspath`.
