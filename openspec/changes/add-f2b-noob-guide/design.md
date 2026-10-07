# Design

## Context

See proposal.md for why. This change builds on the two F2B changes before it:

- `IdealTracks.build(manoeuvre, anchor, R)` returns a polyline of hemisphere directions with segment tags and progress
  (`add-f2b-pattern-mode`, design decision 4). Anchors follow each manoeuvre's own reference: the lateral reference
  for loops, the intersection for eights, the release point for take-off, and so on.
- The grader matches samples to an ideal by progress within a ±10% window.
- `PatternJudge` exposes the state of the expected manoeuvre for the Mid HUD: the expected index, the laps since the
  last end, and the last scored entry.
- `AssistLevel.NOOB` and the `noob` board already exist, but `NOOB` is not offered.
- `add-f2b-pro-debrief` adds `LineSegment.width`, `FieldRenderer` batching by width, and the conversion from
  hemisphere directions to world positions around the pilot.
- While flying, `ControlLineGame.render` draws the control lines as `LineSegment`s. The camera sits at the pilot's eyes
  looking at the plane, with a 67° vertical field of view, so about ±50° is visible horizontally at 16:9.

## Goals / Non-Goals

**Goals:**

- The guide's placement, gate, dimming and life cycle are GL-free and tested headless.
- No change to judging. Noob is judged by the same code with the same anchors from the flown track.

**Non-Goals:**

- Placing figures to suit wind, sun or the judges' side.

## Decisions

### 1. Place a manoeuvre by its start, not by its anchor

The game knows where the pilot should start: the gate. Each template, however, is anchored at its own reference,
which lies somewhere inside the figure. `GuidePlanner` therefore builds the template at azimuth 0, reads the azimuth of
its first point (its "Start of manoeuvre"), and rotates the whole polyline about the vertical axis so that the first
point lands on the gate. This works the same for every manoeuvre. It needs no per-manoeuvre table, and it stays right
if `IdealTracks` is tuned.

- Rejected: a table of offsets between start and anchor for each manoeuvre. It duplicates knowledge `IdealTracks`
  already has, and goes stale when that changes.

The gate is the plane's azimuth when the judge reports the pause done, plus `GUIDE_LEAD`, a quarter lap (90°).

- Take-off is placed at the release point, at azimuth 0 of the flight.
- The landing is placed once the clover is scored and the engine is off.
- The rings are fixed: base and 45° parallel, 128 segments each.

### 2. The guide never moves

A placed guide is kept until the judge scores its manoeuvre. "Waiting for the pilot" is free: the circle brings the
plane back past the gate every lap. A skip-ahead (the judge scoring a later manoeuvre) removes the guide like any
scoring. After the clover, no guide is shown until the landing guide.

### 3. Dimming by progress, monotone

Once the judge reports the drawn manoeuvre started, `GuidePlanner` tracks the progress of the point nearest the plane.
It searches only a window just ahead of the last progress (the grader's ±10% matching, reused), so it never jumps
back or skips onto a later repeat that lies on the same circle. Points behind the progress are tagged `GUIDE_DONE` and
points ahead `GUIDE_AHEAD`. The gate is tagged `GATE`, and the rings `RING_BASE` and `RING_45`.

### 4. Drawing

`ControlLineGame` asks `GuidePlanner.segments()` for world-space segments while flying a Noob flight and appends them to
the control lines.

| Tag | Colour | Width |
|---|---|---|
| `GUIDE_AHEAD` | bright cyan | 3 px |
| `GUIDE_DONE` | cyan at 30% alpha | 2 px |
| `GATE` | yellow | 4 px |
| `RING_BASE` | white at 25% alpha | 1 px |
| `RING_45` | white at 12% alpha | 1 px |

`FieldRenderer` enables blending for segments with alpha below 1. The lines stay depth-tested, so the ground hides
lines below it, as it does the control lines.

### 5. Flow

- `GameFlow` offers `NOOB` on `AssistSelect`, after Pro and Mid.
- `GameUi` gives Noob Mid's HUD, and the score table gets the Noob tab after Pro and Mid.
- The debrief already works for any F2B level; its "Pro and Mid" wording is only a spec matter (task 3.1).

### 6. Threads

Everything runs on the LWJGL3 main thread. `GuidePlanner.update` is called in `ControlLineGame.render` after the
session advances; it reads the judge's state and the plane's position. Building a guide is one `IdealTracks.build` and
a rotation, done once per manoeuvre. Each frame costs one windowed nearest-point search: a few dozen points.

### 7. GL-free, testable classes

- `pattern.GuidePlanner`, using only `com.badlogic.gdx.math` and the pattern classes;
- `flow.GameFlow`.

`ControlLineGame`'s drawing and the colours are checked by hand.

## Risks / Trade-offs

- [The gate is a quarter lap ahead, at the edge of the pilot's view, so a beginner may not see it] → It comes into
  view as the plane approaches, and it waits. `GUIDE_LEAD` is one constant, and M-checks decide whether to reduce it
  to ⅛ lap.
- [Overhead parts of a track fall outside the pilot's view while the plane is low] → Accepted: the camera follows the
  plane, as a real pilot's eyes do. The parts near the plane are what matter while following.
- [A guide drawn at 1 px is hard to see on some drivers that cap line width] → Colour and alpha still separate it.
  Tubes are out of scope, as in the debrief.
- [Noob boards fill with near-perfect scores] → Separate boards, as decided in `add-f2b-pattern-mode`.

## Migration Plan

None. The Noob board is already part of `f2b-scores.json` version 1.
