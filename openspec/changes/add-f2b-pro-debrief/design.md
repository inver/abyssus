# Design

## Context

See proposal.md for why. This change starts from what `add-f2b-pattern-mode` builds:

- `PatternJudge` keeps, per judged manoeuvre:
  - its start and end;
  - its anchored ideal polyline (hemisphere directions with segment tags and progress);
  - the flown samples it judged;
  - its mark with each criterion's deduction, or its zero reason.

  Samples carry the plane's offset from the pilot's ground point, its height and its up vector.
- `PatternScoreTable` writes `f2b-scores.json` (`{version: 1, boards: {pro, mid, noob}}`) through a temporary file
  and an atomic move.
- `GameFlow` has `Mode`, `AssistLevel`, `Screen.GameOver` and `Screen.Scores(from, tab, highlight)`. `GameUi` shows
  the 15-row sheet on GAME OVER.
- `ControlLineGame.render` picks a camera per screen through `Cameras` and draws the field with
  `FieldRenderer.draw(field, camera, lines, hidden, clips, seconds)`. `lines` are `LineSegment(from, to, color)`
  drawn with `ShapeRenderer` one pixel wide, depth-tested. A flight's session scene stays alive through GAME OVER, so
  the crashed plane comes to rest.
- The bundled field: pilot at the circle's centre, `Stunter` on 18 m lines, the base at the handle's horizon (1.5 m).

## Goals / Non-Goals

**Goals:**

- One recording format that serves both the flight just flown (in memory) and saved flights (on disk), so the
  debrief has one code path.
- All debrief logic is GL-free and tested headless: what to draw, where the cameras go, the playback clock and the
  deduction text. Only the drawing itself needs GL.

**Non-Goals:**

- Reproducing the flight through physics. The replay plays back poses; it never steps the world.
- Smooth camera animation between manoeuvres. The camera cuts.

## Decisions

### 1. `FlightRecording`: thinned samples plus judgements

During an F2B flight the judge appends a recording sample every 4th physics step (30 Hz). A sample holds the time, the
plane's offset from the pilot's ground point, and the plane's rotation as a quaternion. Each judgement holds:

- the schedule index;
- the start and end times;
- the mark in tenths, or the zero reason;
- the deductions (criterion id, measured value, tolerance, points in tenths);
- the ideal track, as world-space offsets from the pilot's ground point, thinned to one point per 0.25 m.

The recording also holds the plane's name, the line length and the assist level.

- Rejected: storing only the judge's raw 120 Hz samples. Replay is smooth at 30 Hz with interpolation, and that is a
  quarter of the size.
- Rejected: storing anchors only and rebuilding the ideal tracks on load. A later change to `IdealTracks` or
  `MarkWeights` would then show tracks that don't match the saved marks. The out-of-scope note in the proposal
  depends on this.

Size: 7 minutes is about 12,600 samples of 8 numbers. With 3 decimals that is about 1.1 MB of JSON, around 0.3 MB
gzipped, and the ideal tracks add little.

### 2. One file per saved flight, beside `f2b-scores.json`

`RecordingStore` writes `f2b-flights/<id>.json.gz`, with its own `version: 1`. The id is a random UUID. Writes go
through a temporary file in the same folder and an atomic move. A score entry gains an optional `flight: "<id>"`.

- On `add`, the table first saves the recording, then the entry. If the save fails, the entry is still saved without
  `flight`.
- An entry pushed off its board has its recording deleted.
- At start, `RecordingStore.prune(ids)` deletes the files no entry names, and any leftover temporary files.
- Reading a damaged file renames it `.json.gz.bad` and reports "not available".
- Rejected: tracks inside `f2b-scores.json`. That needs version 2, and it makes every score-table read and write
  carry megabytes.
- Rejected: one archive file for all recordings. Pruning would mean rewriting it.

This changes `add-f2b-pattern-mode`'s file only by the optional field. That change's reader must accept a missing
`flight`, which it does, and must ignore unknown fields. Task 1.3 checks this against whatever that change shipped.

### 3. `DebriefModel`, GL-free

`DebriefModel(recording or sheet-only entry, view)` holds:

- the selected index;
- the playback clock (time, speed ½, 1 or 2, playing or paused);
- the view, `PILOT` or `OUTSIDE`.

It produces:

- `segments()`: world-space ideal and flown polylines for the selected manoeuvre, plus guide arcs, each tagged
  `IDEAL`, `FLOWN` or `GUIDE`. The flown polyline covers the manoeuvre's time span, and the guides are the base and the
  45° parallel over the figure's azimuth span plus 10°.
- `planePose()`: the position and rotation at the clock, interpolated between samples (slerp for the rotation).
- `deductionLines()`: largest first, formatted from criterion id, measured value, tolerance and points.
- `frame()`: the figure's bounding points, for the cameras.

Colours are picked in `GameUi` and `ControlLineGame` from the tags: ideal green, flown orange, guides faint white.
`GameFlow` gains:

- `Screen.Debrief(from: Screen, model)`;
- `debrief()` on GAME OVER;
- `openRow(index)` on F2B score tabs;
- `back()` from the debrief, returning `from`, with `Scores` keeping its tab and selected row.

`Scores` gains a `selected` row.

### 4. Cameras frame the figure

`Cameras.debrief(view, pilot, frame)` is pure math on `Vector3` and is unit-tested.

- **Pilot view:** the eye is at the pilot's eye height, looking at the centre of the figure's bounding points. The
  field of view is widened up to 90° when needed so all bounding points fit; overhead figures look up.
- **Outside view:** the eye is outside the circle at 1.6 × the line length from the pilot, on the figure's azimuth, at
  6 m, looking back at the figure's centre.

### 5. Drawing reuses `FieldRenderer`

The debrief scene is a fresh load of the field, as a flight's is. The plane named by the recording is placed at
`planePose()` every frame, and the pilot entity is hidden in the pilot view. If no plane by that name is found, there
is no model and a small cross marks the pose.

`LineSegment` gains an optional `width`, and `FieldRenderer` batches segments by width with `glLineWidth`. Tracks are
drawn 3 px and guides 1 px. Some drivers cap line width at 1, which is acceptable: the colours still tell the tracks
apart. The ideal and flown polylines become consecutive segments.

- Rejected: ribbons or tubes as meshes. Nicer, but new geometry code for no gain in learning value.

### 6. Threads

Everything runs on the LWJGL3 main thread:

- Recording happens in `Flight.update` at the fixed step.
- Saving happens at name entry, gzip of about 1 MB, a few tens of milliseconds, once.
- Loading a recording happens when a row is opened.
- Pruning happens at start.

These are one-off blocking reads and writes on the menu screens, where a frame hitch is invisible. A worker thread is
not worth its synchronisation.

### 7. GL-free, testable classes

These use only `com.badlogic.gdx.math`, Jackson, `java.util.zip` and plain Kotlin:

- `pattern.FlightRecording` and its JSON codec;
- `pattern.RecordingStore`;
- `pattern.DebriefModel`;
- `render.Cameras.debrief`, whose math is tested like `SunShadowCameraTest`;
- `flow.GameFlow`.

`GameUi`'s debrief layout and `ControlLineGame`'s drawing are checked by hand.

## Risks / Trade-offs

- [Large recordings slow the menus] → 30 Hz thinning, gzip, and load on demand. If saves feel slow in M-checks, move
  `RecordingStore.save` to the asset executor the game already has.
- [Disk use grows with orphans after crashes mid-save] → Temporary files and unnamed recordings are pruned at start.
- [Replayed marks disagree with re-judging after the judge changes] → Accepted, and documented: a replay shows what
  was judged at the time.
- [Overhead figures are hard to read from the pilot's view] → The outside view, and a widened field of view.
- [The two changes both touch `GameFlow` and `PatternScoreTable`] → This change is applied after
  `add-f2b-pattern-mode`, and its game-flow delta only adds requirements, so the archived specs merge cleanly.

## Migration Plan

- Entries saved before this change have no `flight`. They open sheet-only debriefs.
- The new folder is created on the first save.
- Rolling back leaves `f2b-flights/`, which the older reader ignores, and the extra `flight` field, which the older
  reader must tolerate (task 1.3).
