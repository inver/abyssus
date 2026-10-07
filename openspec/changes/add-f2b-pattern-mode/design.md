# Design

## Context

See proposal.md for why and what. The current code, all in `projects/app-game-control-line`:

- `Flight.update` runs before every fixed physics step (`PHYSICS_STEP` = 1/120 s). It turns the plane's pose into a
  `TrackSample` (`track/SphereTrack`) and feeds it to `FlightScoring` (laps, the `Maneuvers` state machines and the
  combo `ScoreKeeper`). `FlightSession.report()` builds a `FlightReport` with score, laps and best combo.
- A `TrackSample` holds azimuth, elevation and climb angle relative to the handle, `inverted` and `airborne`. Its climb
  angle means nothing near the zenith, which `Maneuvers` works around. F2B figures go through and around the zenith:
  the wingover, hourglass, overhead eights and clover.
- `GameFlow` is a GL-free screen state machine. `GameUi` (Scene2D) shows it. `ScoreTable` keeps `scores.json`, and an
  unreadable file becomes `.bad`.
- Bundled field: the pilot's `handleHeight` is 1.5 m (the default), so the F2B base, 1.5 m above the ground, is the
  handle's horizon on the flat flying circle. `Stunter` is the only `STUNT` plane: 18 m lines, fuel 180 s.
- Play in Abyssus (`play/ControlLinePlay`) builds a `Flight` and reads `flight.scoring.keeper`.
- The rules come from the FAI Sporting Code Volume F2, 2027 edition, rule 4.2: schedule 4.2.12, scoring 4.2.8,
  manoeuvres 4.2.13 to 4.2.15. Annex 4B, the Judges' Guide, is not part of that volume.

## Goals / Non-Goals

**Goals:**

- One ideal-track model per manoeuvre, built from the F2B wording. The judge uses it now; the debrief replay and the
  Noob guide reuse it in the next two changes.
- Judging that is deterministic and fully testable headless from synthetic tracks, with no physics in the loop.
- Free flight and Play behave exactly as today.

**Non-Goals:**

- Matching human judges' marks closely. The deduction weights are a first, tunable guess.
- Judging what the game does not simulate: the ground roll after touchdown (a flight ends at touchdown), propeller
  standstill (the engine is simply off), retractable gear, or the plane's attitude at the knife-edge overhead point.

## Decisions

### 1. Hemisphere coordinates as unit vectors, not angles

The pattern code works with the direction `u` from the hemisphere centre C to the plane. C is the pilot's ground point
plus 1.5 m. Lengths along the hemisphere are angles times the nominal radius R, which is the line length. Latitude φ
is measured from the base; the azimuth θ grows in the flying direction.

- Great circles (vertical lines, 45° inclined lines, the wingover path) and small circles (loops, the base and the 45°
  parallel) are both exact and cheap in this form.
- Nothing is singular at the zenith.
- Rejected: reusing azimuth, elevation and climb. They break overhead, which is half of the schedule.

`TrackSample` gains these fields; `SphereTrack` fills them, and the existing ones are untouched:

- the plane's offset from the pilot's ground point;
- its height above the ground;
- its up vector;
- whether the engine is running.

The base-height checks use the height above the ground directly, as the ±30 cm rule is about the ground.

### 2. Inside and outside from the plane's up, and corners from curvature

From consecutive `u` the path's tangent `t` and its geodesic curvature vector `k` follow.

- A turn is **inside** when `k · planeUp > 0`. That is the handle-up direction for any attitude, including overhead
  and inverted. It is **outside** otherwise.
- A **straight** stretch has |k| below 1/(4R): a great circle.
- A **corner** is a curvature peak with radius under 3 m, between straights. Its turned angle is ∫|k| ds over the
  peak.
- A **loop arc** is a stretch of steady curvature.

A path then reads as tokens:

| Token | What it is |
|---|---|
| `Straight(direction)` | the direction is vertical, level, inclined at 45°, or 30° off vertical |
| `Corner(side, angle)` | a sharp turn, inside or outside |
| `Arc(side, angle)` | a steady turn |
| `Level(upright\|inverted, latitude)` | level flight on a parallel |
| `GroundRoll` | rolling on the ground before lift-off |
| `Touchdown` | the plane touching down |

Turn accumulation counts loops (±360° each). Corner counts tell squares (4), triangles and the hourglass (3 and 4,
told apart by leg angles) from loops (0).

### 3. A sequencer with one matcher per manoeuvre

`PatternJudge` implements a new `FlightJudge` interface: `sample`, `ended(FlightEnd)`. It holds the expected manoeuvre
index, the time and laps since the last manoeuvre ended, and the score sheet. Each manoeuvre has a matcher, a small
grammar over the tokens with its start and end points per the F2B "Start of manoeuvre" and "End of manoeuvre":

| Manoeuvre | How the matcher finds it |
|---|---|
| Take-off | Starts at the flight's start. Ends 3 laps of azimuth after the release point |
| Reverse wingover | Two vertical great-circle passes over the zenith, with an inverted base stretch between them |
| Inside / outside loops | Repeated `Arc(side, 360°)` between the base and the 45° parallel |
| Inverted laps | Fixed in time: the 3rd and 4th laps after the inside loops end. Inverted level flight is expected there |
| Squares, triangles | Repeats of 4 or 3 corners joined by vertical or 30°-off-vertical straights |
| Horizontal eights, square eights | Inside and outside figures alternating through one crossing point |
| Vertical eights | An inside loop below the 45° parallel and an outside loop above it, alternating |
| Hourglass | Four corners: two crossing legs, a top segment along the wingover path, a bottom segment along the base |
| Overhead eights | Inside and outside loops crossing at the zenith |
| Four-leaf clover | Four ¾ loops with inclined and vertical connecting lines, ending in a climb through the zenith |
| Landing | Leaves the base with the engine off. Ends at the touchdown that ends the flight as a landing |

How the sequencer uses the matchers:

- **Which one counts.** Matchers for the expected manoeuvre and every later one run on the tokens since the last
  pause. The first to complete wins, and the expected one wins a tie. Every skipped manoeuvre scores 0 as "omitted".
- **Wrong repeats.** When a matcher's figure is recognised but the repeat count is wrong, the manoeuvre scores
  "wrong number of repeats".
- **Not completed.** When a started matcher sees the path settle into a pause (level flight on the base for ½ lap)
  before its end, the manoeuvre scores "not completed".
- **Too soon.** The pause rule compares the azimuth travelled between the previous end and this start with 540°.
- **Time and crashes.** A manoeuvre whose end is past 420 s scores "after 7:00". On `ended(CRASHED)` the manoeuvre in
  progress and the rest score "crashed".
- **After the clover.** Matchers are not run between the clover and the landing.

Alternatives considered:

- *Today's per-manoeuvre state machines, one per figure.* They don't scale to 15 figures with shared parts.
- *Classifying whole segments by fitting every template and picking the best fit.* The cost is fine, but a badly
  flown square fits a loop template better than a square one. That would turn "badly flown" into "out of order",
  which the spec forbids: recognition must not depend on quality.

### 4. Ideal tracks anchored by the rules, not by best fit

`IdealTracks` builds, for a manoeuvre and an anchor, a polyline of directions with segment tags: `base`,
`parallel45`, `vertical`, `corner`, `arc`, `wingoverPath`, `glide` and so on. Each point carries its progress along
the manoeuvre. Anchors are taken from the flown track exactly where the description defines them:

| Manoeuvre | Anchor |
|---|---|
| Loops | The first vertical attitude (the lateral reference) |
| Eights | The first passage of the intersection point |
| Square eights | The first vertical climb |
| Overhead eights | The first pass over the zenith |
| Clover | The 9 o'clock vertical |
| Take-off | The release point |
| Wingover | The azimuth of the first climb |
| Hourglass | The first turn from the base |
| Landing | Where the plane leaves the base |

Rejected: optimising the anchor to minimise the error. That forgives a misplaced figure, while judges hold the pilot
to the reference the pilot defined.

Sizes come straight from the rules:

| Figure | Ideal size |
|---|---|
| Loops | Circles of angular radius 22.5° centred on latitude 22.5° |
| Squares | ⅛ lap wide, between the base and the 45° parallel |
| Square eights | ¼ lap wide |
| Triangles | Legs 30° off vertical, apex on the 45° parallel |
| Vertical eights | 0° to 45° to 90° |
| Overhead eights | Loop bottoms on the 45° parallel, crossing at the zenith |
| Clover | ¾ loops of equal diameter |
| Take-off | Ground roll between 4.5 m and ¼ lap, levelling over the release point, 2 laps at the base |
| Landing | 1 lap of glide from the base to the ground |

Corners are ideal at a 1 m radius.

### 5. Marks are 10 minus capped deductions

The flown samples of a figure are matched to the ideal by progress. Each sample is compared with the nearest ideal
point within ±10% of its expected progress, so a repeat cannot snap onto another repeat. Each criterion gives
`deduction = min(cap, perUnit × max(0, measured − tolerance))`. The mark is `clamp(10 − Σ deductions, 1, 10)` rounded
to 0.1. A complete manoeuvre never scores 0. Every criterion is non-decreasing in its error, which gives the spec's
monotonic marks. The initial weights live in one table, `MarkWeights`, to be tuned by play:

| Criterion | Measured | Tolerance | Per unit | Cap |
|---|---|---|---|---|
| Base height | worst \|height − 1.5 m\| on base segments | 0.30 m | 2 / m | 3 |
| Track deviation | RMS distance to the ideal (m) | 0.5 m | 1 / m | 4 |
| 45° parallel | \|top − 45° parallel\| (m) | 0.5 m | 1 / m | 2 |
| Line angle | verticals and 30° legs (°) | 3° | 0.1 / ° | 2 |
| Corner radius | per corner (m) | 1.5 m | 1 / m | 3 |
| Segment length | ⅛ lap, ½ lap, 1 lap (laps) | 0.02 | 10 / lap | 2 |
| Superimposition | worst distance between repeats (m) | 0.5 m | 1 / m | 2 |
| Symmetry and crossing point | drift of the axis or crossing (m) | 0.5 m | 1 / m | 2 |
| Take-off roll | outside 4.5 m to ¼ lap (m) | 0 | 0.2 / m | 2 |
| Landing touchdown | vertical speed (m/s) | 1 m/s | 1 / (m/s) | 2 |

The judge keeps each deduction with the mark, for the debrief change and reasons later. It also keeps the ideal and
flown polylines of each judged manoeuvre.

Marks are held as integer tenths, and points as tenths × K, so the sum is exact. With one judge, rounding down to two
decimals never changes it, but the rule is still applied when the score is formatted.

### 6. Flight gets an optional judge; arcade scoring is untouched

- `Flight.takeoff(azimuth, judge: FlightJudge? = null)`. Each step feeds the sample to `scoring`, as today, and to the
  judge when there is one. The flight end goes to both.
- `FlightSession` takes the mode.
- `FlightReport` keeps plane, end and flight time, plus a sealed result: `Free(score, laps, bestCombo)` or
  `Pattern(level, sheet)`.
- Play keeps calling `takeoff()` with no judge, so it is unchanged.
- The arcade scoring keeps running in an F2B flight. It costs nothing, nothing shows or saves it, and keeping it
  avoids touching Play or the `ScoreKeeper` code.
- Rejected: a polymorphic scorer replacing `scoring`. It would churn Play and the HUD for no user-visible gain.

### 7. Flow, screens and boards

- `GameFlow` gains:
  - `Mode`: `Free`, or `Pattern(level)` with `AssistLevel { PRO, MID, NOOB }`. `NOOB` is not offered until
    `add-f2b-noob-guide`.
  - a `Screen.AssistSelect` screen;
  - `mode` on `PlaneSelect` and `Flying`, with stunt-only plane filtering;
  - `tab` on `Scores`.
- `GameFlow` takes the `ScoreTable` and a new `PatternScoreTable`. `flightEnded` and `nameEntered` pick the table by
  mode.
- `PatternScoreTable` writes `f2b-scores.json` the way `ScoreTable` does: a temporary file, an atomic move, and `.bad`
  on a read failure. The format is:

  ```
  {version: 1, boards: {pro: [...], mid: [...], noob: [...]}}
  ```

  Each entry is `{name, plane, score, flightTime, date, marks: [15 numbers]}`. Scores and marks are written as
  decimal numbers (two and one decimals), and a missing board reads as empty.
- `GameUi`:
  - builds the HUD per mode;
  - shows the Mid guidance from the judge's public state (expected manoeuvre, pause laps, last scored entry,
    height-band flag);
  - shows the 15-row sheet on GAME OVER;
  - switches the score table tabs with Left and Right.

### 8. Threads

There is no IDE here. All new code runs on the game's LWJGL3 main thread: the judge inside `Flight.update` at each
fixed step, and the flow and boards on screen events, like the rest of the game. A figure is graded once, when it
ends: at most a few thousand samples against a few hundred ideal points, well under a millisecond.

### 9. GL-free, testable classes

These classes use only `com.badlogic.gdx.math` or plain Kotlin, with no GL, Scene2D or physics:

- `pattern.HemisphereGeometry`, `PathTokens`;
- `pattern.IdealTracks`, `Matchers`, `Grader`, `MarkWeights`;
- `pattern.PatternJudge`, `Schedule`, `ScoreSheet`;
- `pattern.PatternScoreTable`;
- `flow.GameFlow`.

Tests drive them with synthetic `TrackSample`s generated from the ideal tracks: a flown path at a set speed, then
perturbed (offset, scaled, rounded corners, an extra repeat). A few physics tests use a scripted pilot in the test
sources that follows an ideal track with a feedback loop on the handle.

### 10. Data change

`Stunter`'s `PlaneComponent.fuelTime` in `project/ControlLine/scenes/Field.scene` goes from `180` to `360`, edited by
hand like earlier edits to the bundled project's numbers. Key order and other numbers stay untouched. If the spike
shows corners of more than about 2 m radius at full handle, `Stunter`'s `elevatorEffect` and `pitchDamping` are tuned
in the same file. `GameFlowTest`'s fuel assertion moves to 360.

## Risks / Trade-offs

- [The plane cannot fly tight corners or a clean square] → The spike comes first (task 1). Tune `Stunter`'s
  aerodynamic numbers; if that is not enough, raise the corner-radius tolerance in `MarkWeights` and say so in the
  README.
- [Recognition misreads a sloppy figure, for example a square with soft corners read as a loop] → The corner radius
  limit (3 m) is well above the tolerance (1.5 m), so soft corners still count as corners. There are tests per figure
  with rounded corners, and a recognition miss shows as "omitted" or "wrong repeats" on the sheet, where play-testing
  will find it.
- [Marks feel arbitrary without Annex 4B] → All weights are in one table, documented in the README. Ask the user for
  Annex 4B to calibrate later.
- [Keyboard input is too coarse to hold ±30 cm] → This is accepted for Pro. Mid's warnings help, and Noob comes in its
  own change.
- [Flight ends at touchdown, so the ground roll is not judged and "stopped" means touchdown] → Documented as a game
  simplification.
- [Free flight with `Stunter` now lasts 6 minutes] → Accepted with the user.

## Migration Plan

Nothing migrates:

- `scores.json` and its format are unchanged.
- `f2b-scores.json` is new.
- A player's existing Free flight table still loads.
- Rolling back leaves an unused `f2b-scores.json` behind.

## Open Questions

- The final deduction weights, tuned by play-testing, or against Annex 4B if it becomes available. These change
  numbers in `MarkWeights` only, not the specs or the structure.
