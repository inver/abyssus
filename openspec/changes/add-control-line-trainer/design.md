# Design

## Context

The flight loop already has the seam this change needs. Once per fixed step, `FlightSession` asks a
`FlightControlSource` for the tilt, giving it a `FlightObservation` of the state the last step left. Two sources exist
today: `HumanControl` (keys, mouse and controller through `HandleInput`) and `F2bBot` (Watch AI). The bot has two halves:

- `F2bRoutinePlanner` decides what to fly.
- `F2bPilot` steers along any `Course`, using `Handling` for the feed-forward. It is pure and GL-free.

`F2bFigures` builds the paths the planner flies, including `turnOver` and `landing`. `Parallel` is level flight at a
latitude, in either direction. These are enough to fly a plane back to the base.

What ends a flight is in `FlightOutcome`: touching the ground faster than `CRASH_SPEED` (3 m/s) or not upright, or lines
below `CONTROL_TENSION` in the air for `SLACK_TIME` (2 s). Stalling is not fatal: lift is held at its stall value in
`Aero`.

`GameFlow` is a pure state machine. Watch AI already shows how a flight skips name entry and the boards: `Control.AI`
goes straight to `GameOver(rank = null)`. The screens are an authored UI asset fed by `PUBLISHED_BINDINGS` and `ACTIONS`
(`GameUiContract`). Its widgets include `checkbox` and `progressBar`.

Everything below runs in the game process on its main (render) thread. Control sources are only called inside
`FlightSession.advance`. None of it runs in the IDE, so `GdxRuntime.withContext` does not apply. The new logic touches
no GL and no input device, and is testable headless.

## Goals / Non-Goals

**Goals:**
- One new control source that wraps the player's source. The flight model, the judges and `FlightSession` are unchanged.
- Danger, the rescue course and share smoothing as pure classes with unit tests.
- A headless physics test that proves every bundled plane is saved from a dive held at full down.

**Non-Goals:**
- Making the instructor a good pilot. It only has to get the plane to safe level flight. It does not fly figures.
- Tuning per plane by hand. The instructor reads each plane's own `PlaneComponent` through `Handling`, as Watch AI does.

## Decisions

### 1. `InstructorControl` wraps the human source

`ai/InstructorControl(student: FlightControlSource, gauge: DangerGauge, rescue: RescuePilot) : FlightControlSource`. In the game the student is the `HumanControl`. Tests pass a scripted source or a Watch AI bot instead. Each step it:

1. calls `student.tilt(o)`, which for a `HumanControl` still ramps the keys by one step, so the player's part feels the same;
2. computes the danger;
3. updates the share (decision 4);
4. asks the rescue pilot for its tilt;
5. returns `(1 - share) * player + share * rescue`.

It exposes `danger`, `share`, `playerTilt`, `rescueTilt`, `holding` (share at least 0.5) and the list of takeover spans
(flight-time start and end) for the HUD and the report.

`FlightRunner.start` builds it when `Screen.Flying.instructor` is true and the control is `HUMAN`. `key` and
`mouseMoved` still reach `HandleInput`.

*Alternative:* add the blend inside `FlightSession`. Rejected: the session would have to know about two sources, and the
existing seam makes that unnecessary.

### 2. `DangerGauge`: the spec's three terms, from one observation

The gauge is pure. It takes the plane's `Handling` and keeps one piece of state, the time the lines have been slack.

- **Ground:** `R = 1 / handling.maxCurvature`, the tightest radius short of the stall.
  - Descent angle: `gamma = asin(-v.y / |v|)`, clamped to 0 or more.
  - Margin: `height - R * (1 - cos gamma) - sink * 0.15`.
  - The gate (upright, sinking slower than 1.5 m/s) keeps landings and the bot's glide out of it.
  - `maxCurvature` does not depend on speed, so `R` is fixed per plane. Gravity is ignored, which errs on the safe side
    in a dive.
- **Inverted:** `o.up.y < 0`, against `o.height`.
- **Slack:** count the time with `tension < CONTROL_TENSION` while `height` is above the wheels.

The bounds (1.2 / 0.4 m, 0.9 / 0.4 m, 0.4 / 1.2 s) are constants beside the gauge. The spec states them, so a change to
them changes the spec too.

*Alternative:* time to impact (`height / sink`). Rejected: it says nothing about whether the plane can still pull out, and
it fires on every loop's descent.

### 3. `RescuePilot`: level at the base, in the plane's own direction

The rescue pilot owns an `F2bPilot(Handling(plane, mass))` and builds its own course, which the planner and the judge
never see.

- **Engine running:** `Parallel(radius, 0f, clockwise)`, where `clockwise` is the direction the plane flies around the
  circle. The planner's `recover` computes it the same way: after a wingover the plane flies clockwise and inverted. The
  instructor does not turn a running plane upright. Level inverted at the base is safe, and the student gets the handle
  back where they were.
- **Engine stopped:** if flying clockwise, `F2bFigures.turnOver(azimuth, clockwise = false)` first. Then
  `F2bFigures.landing(azimuth)` from where the plane is. A `PathCourse` that has not progressed for 3 s is rebuilt from
  the plane's place, like the planner's `lost` check.
- The course is built when the share first rises above 0. It is dropped when the share returns to 0, so each rescue
  starts from where the plane actually is. Below `CONTROL_SPEED`, `F2bPilot` is already neutral.

The rescue tilt is computed every step while the ghost may show (danger above 0 or share above 0). Otherwise it is
skipped and is 0.

*Alternative:* follow the Noob guide or the figure the student started. Rejected for this change (see the proposal's
out-of-scope list): the instructor would need to know the student's intent, and a wrong guess fights the student.

### 4. Share, handback and takeovers in `InstructorShare`

`InstructorShare` is a small pure state machine, stepped with `PHYSICS_STEP`. Its state is the share, the time the danger
has been 0, and the takeover in progress.

- When the danger is above the share, the share becomes the danger.
- After 1 s at 0 danger, the share falls by `PHYSICS_STEP / 0.5 s` per step.
- A takeover opens when the share crosses 0.5 upwards and closes when it falls below 0.5.

The source is not called while the game is paused, so pausing freezes all of it for free.

*Alternative:* a first-order filter on the danger. Rejected: its "at once" rise and its 1 s hold are harder to state and
test than these explicit rules.

### 5. Flow: a flag on `Flying`, the takeovers on the report

- `Screen.Flying` gains `instructor: Boolean = false`.
- `GameFlow` gains `var instructor` (off when built), `toggleInstructor()` (only on plane select), and
  `instructorOffered` (any plane select). `choosePlane` copies the flag into `Flying`. `watchAi` does not, and `retry`
  keeps it.
- `FlightReport` gains `takeovers: List<Takeover>` (empty by default). `reportOf` takes it from the runner's instructor.
- `flightEnded` treats `takeovers.isNotEmpty()` like `Control.AI`: straight to `GameOver(rank = null)`. Nothing reaches
  `table`, `patternTable` or `scaleTable`, so no score or recording file is touched. Untouched flights follow the
  existing path unchanged.

*Alternative:* a new `Control.INSTRUCTED`. Rejected: control and instructor are independent. Retry and the AI checks
already key on `Control`, and a third value would leak into every one of them.

### 6. UI: bindings and an action, authored in the screens asset

New published bindings:

| Binding | Type |
|---|---|
| `instructorOffered` | boolean |
| `instructorOn` | boolean |
| `isInstructed` | boolean |
| `handleTilt` | number |
| `ghostVisible` | boolean |
| `ghostTilt` | number |
| `ghostCue` | string |
| `instructorHolds` | boolean |
| `instructorFact` | string |

There is a new action, `toggleInstructor`, and a new focus id, `ps-instructor`.

- Plane select gets a `checkbox`.
- Each HUD (`hudFree`, `hudPattern`, `hudScale`) gets two thin vertical `progressBar`s: the player's tilt and the ghost,
  mapped from -1..1 to 0..1. It also gets the cue label and an INSTRUCTOR label bound to `instructorHolds`.
- GAME OVER adds a `facts` row, Instructor, for instructed flights: "Not needed", or "Took over N times, not saved".
  `facts` is already a list binding, so GAME OVER needs no new widget for it.
- The key `I` maps to `toggleInstructor` on plane select in `GameUiModel`. The controller reaches the checkbox through
  `PadNavigator`'s focus order.

The asset is edited as authored content, as other changes do. The existing UI asset loader validates and binds it. No
JSON is parsed by hand.

### 7. Debrief: spans, not a new file field

`DebriefModel` gains `takeovers: List<Takeover>` (empty for board flights), passed in when the debrief is opened from
GAME OVER.

- The flown samples carry their flight time (sample stride 8, time first). The flown line of a manoeuvre is cut into
  `FLOWN` and a new `TrackKind.INSTRUCTOR` by the span times.
- `DebriefColors` gets `INSTRUCTOR_COLOR`, a cyan distinct from the green ideal and orange flown tracks, and the legend
  names it.
- The note for an overlapped manoeuvre goes through the debrief's existing notice binding. That binding only shows "replay
  not available" for board flights without a recording, which never have takeovers.

`FlightRecording` and `RECORDING_VERSION` stay unchanged, because an Assisted flight is never saved.

### 8. Its own capability, and the name "Instructor"

The rules are written as `control-line-instructor`, not as `MODIFIED` deltas of `control-line-game-flow`. Two open
changes, `add-control-line-gamepad-support` and `add-control-line-scale-mode`, already rewrite its Flying and plane
select requirements. A third full rewrite would conflict with them on archive.

The UI and the code say "Instructor", not "Trainer", because `Trainer` is a bundled plane (`TrainerCrashAnimationsTest`).
The change keeps its user-chosen name, `add-control-line-trainer`.

## Risks / Trade-offs

- **[Risk] A fast plane (`Racer`) cannot pull out from the 1.2 m bound in time.**
  → The rescue test flies every bundled plane. If one fails, first check the 0.15 s allowance and the share's immediate
  rise. Only then raise the bounds, in the spec and the constants together, keeping level flight at the 1.5 m base at 0
  danger.
- **[Risk] Ghost noise in F2B.** A square loop's corner near the base could flash the ghost.
  → The margin uses the tightest radius, not the figure's, so a corner flown at the base keeps a margin near 1.5 m. A
  test flies the bot's own schedule with an instructor wrapped around a scripted player and asserts no takeovers.
- **[Risk] The instructor and the student fight below share 1.** A student holding full down at share 0.5 cancels half
  the rescue.
  → The share rises to the danger at once, and the danger reaches 1 before the margin is gone. The rescue test holds full
  down throughout.
- **[Trade-off] Engine-out inverted rescue is best effort.** A turn-over with no thrust needs speed, and a plane that is
  too slow may still land badly. The spec only guarantees the dive rescue.
- **[Trade-off] The toggle is not saved.** A returning student turns it on again each session. Saving it would need a
  settings file entry, which is out of scope.

## Migration Plan

None. No file format or version changes. Rolling back removes the toggle, and no file written by this change outlives it.
