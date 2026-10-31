# Design

## Context

Everything here is in `projects/app-game-control-line`, on libGDX's render thread. See proposal.md for the motivation and
the specs for behaviour.

- **Modes.** `GameFlow` is a pure state machine over `Screen`. `Mode` is `Free` or `Pattern(level)`; `choices(mode)`
  filters the planes, `FlightResult` is `Free` or `Pattern`, and `ScoreTab` has one tab per kind of board. F2B was added
  by extending these, so Scale follows the same path.
- **Judging.** `FlightJudge` (`sample(TrackSample)`, `ended(FlightEnd)`) is fed by `Flight` on every simulation step.
  `PatternJudge` is the only implementation; `reportOf` turns a finished session into a `FlightReport`.
  `TrackSample` already carries `time`, `azimuth`, `elevation` (degrees) and `airborne`, and `Laps` counts a lap from
  azimuth progress while airborne.
- **Plane select** parks the planes where `Field.scene` puts them (x=4, one every 2.5 m, on the grass) and
  `Cameras.planeSelect` frames the chosen one 2.4 m ahead of its nose. A flight loads its own copy of the scene, so
  moving a parked plane for display never touches a flight.
- **Tables** are plain model entities (`model_airfield_table_picnic` / `_work`) in the preparation area. The work table's
  recorded bounds reach 0.96 m in height, so its top is not at one obvious height.
- **Screens are authored UI** (`assets/screens/meta.json`), driven through `GameUiModel` values and checked by
  `screens.golden`.
- **Boundaries.** `PlaneComponent` is a registered game component bound from the scene by name
  (`GAME_COMPONENTS`); game components are read-only in the editor and listed in `components.schema.json`. The shipped
  game must not reach editor modules, and `lib-core` / `lib-physics` do not change.

## Goals / Non-Goals

**Goals:**
- Scale is one more `Mode` that reuses Free flight's flight, session and screens, with its own judge, result and board.
- Plane placement on tables is data driven: a plane added in Abyssus, or a new table, needs no code change.
- Every new rule (marks, slot assignment, table-top height, board) is a plain class a headless test covers.

**Non-Goals:**
- No change to flight physics, the F2B judge or the F2B debrief, and no new physics for the Mig-3.
- No in-game editing of tables or planes; Abyssus authors them.

## Decisions

### 1. Scale is a `Mode`, its result a `FlightResult.Scale`

`Mode.Scale` joins `Free` and `Pattern`. `choices(Mode.Scale)` keeps the `SCALE` planes, `scaleEnabled` mirrors
`patternEnabled`, and the menu message gains a third case (`NO_SCALE_MESSAGE`). `FlightResult.Scale(score, marks, laps)`
carries the three marks. `GameFlow.flightEnded` / `nameEntered` get a third branch that saves to the new table;
`retry`, `mainMenu` and `back` need no change because they carry the mode. Watch AI and the debrief are gated on
`Mode.Pattern` already, so Scale offers neither.

*Alternative:* a `scale: Boolean` flag on `Mode.Free`. Rejected: Free's report, board and HUD all differ, and every
`when (mode)` would hide the third case behind a flag.

### 2. The realism marks are a `FlightJudge`

`ScaleJudge : FlightJudge` collects, per airborne lap (a private `Laps`), the lap time and the airborne elevation
samples, and exposes `marks` live (for the HUD) and final. Pure arithmetic lives in `ScaleMarks` (no GL, no flow):

- `speed`: spread = max over laps of |lap - mean| / mean; 400 at spread <= 0.05, linear to 0 at 0.30. Fewer than 2 laps: 0.
- `level`: mean of |elevation| over airborne samples; 400 at <= 15 degrees, linear to 0 at 45. Fewer than 2 laps: 0.
- `landing`: 200 when `ended(FlightEnd.Landed)`, else 0.

A lap in progress at the crash does not count. The judge is passed to `Flight.takeoff` as Pattern's is, so `Flight`
changes nothing. `reportOf` gets a `Mode.Scale` branch that takes the judge's final marks.

*Alternative:* derive the marks from `ScoreKeeper`. Rejected: it has no lap times or elevations and Scale must not score
Free's laps and manoeuvres.

### 3. `ScaleComponent` and `VitrineTableComponent` are game components

Both join `GAME_COMPONENTS`. `ScaleComponent`: `prototype: String`, `firstFlight: Int`, `wingspan: Float` (m),
`scale: Int` (denominator). `VitrineTableComponent`: `topHeight: Float`, the table-top's height above the table
entity's origin in metres, authored per table. `PlaneClass` gains `SCALE`. `components.schema.json` lists both
components and the new enum value so Abyssus shows them read-only like `PlaneComponent`.

*Alternative for `topHeight`:* read it from the model's bounds. Rejected: the bounds give the highest point (0.96 m on
the work table), not the top surface, and a table with a lamp or a bench back would put the plane at the wrong height.

*Alternative for tables:* find them by entity name ("Preparation table N"). Rejected: names are for people and would
silently stop matching on a rename. The preparation tables stay props; only tables carrying the component are vitrine
tables, so the bundled field gets a new display row.

### 4. `VitrineLayout` places the planes

A plain class (`vitrinePoses`): given the planes (in name order, over all planes, so a plane keeps its table in every mode) and the vitrine tables (ordered by entity id), it returns for each
plane a world pose: the table's position plus `topHeight`, minus the plane model's lowest point, with the plane's yaw
set to the table's heading and its authored tilt (roll and pitch) kept, so it rests on the table as it rests on the ground. Planes beyond the last table stand on the
ground at the next position along the row, spaced like the tables. `FieldScene` applies the poses to the parked
scene only (`planes` / `entity()` already serve that copy), and `Cameras.planeSelect` aims at the plane's pose with its
framing raised to the table's height. The model's lowest point comes from the loaded model's bounding box in the
plane's own frame; the layout takes it as a number, so a test needs no GL.

*Alternative:* leave the plane y values authored in `Field.scene`. Rejected: the spec requires a plane added in
Abyssus to land on the next free table, and sizes differ (the Mig-3 is 0.4 m high, the Racer far lower).

### 5. The card is computed in the UI model

`PlaneChoice` gains `scale: ScaleFacts?` (prototype, year, span, scale), filled by `planeChoices` from the
`ScaleComponent`. `GameUiModel.planeSelect` fills the card's text values; the replica lines are hidden for a plane
without it. The authored UI (`assets/screens/meta.json`) gets a Scale menu button, the card's replica lines, the Scale
mode's GAME OVER rows and a Scale score tab; `screens.golden` is regenerated and reviewed.

### 6. The Scale board is a sibling of `ScoreTable`

`ScaleScoreTable` has the same shape as `ScoreTable` (top 10, ties in time order, atomic JSON through `AtomicJsonStore`
and Jackson DTOs, unreadable file renamed to `.bad`) with `ScaleScoreEntry` (name, plane, prototype, score, the three
marks, laps, time, date). `ScoreTab` gains `SCALE`; `tabOf(Mode.Scale)` returns it and the tab loop already iterates
`ScoreTab.entries`.

*Alternative:* generalise `ScoreTable` over its entry type. Rejected for now: `PatternScoreTable` is already a separate
sibling, and merging the three is a refactor outside this change.

### 7. The Mig-3 plane

`Field.scene` gets a `Mig-3` entity (model `model_mig3`, class `SCALE`, a `ScaleComponent`, a rigid body and a convex
hull as the other planes have) and a display row of four tables (`VitrineTableComponent`) near the pilot. The Mig-3's
`PlaneComponent` numbers (lines, thrust, wing area, lift slope, leadouts, mass) are set by hand and proven by a flight
test that takes off, flies laps and lands. The glb's frame must be nose +Z, up +Y, left wing +X; a test asserts it from
the model's bounds and a runIde check confirms it visually.

## Threads

Everything runs on libGDX's render thread: `GameFlow`, `ScaleJudge` (called from `Flight`'s fixed steps on that
thread), `VitrineLayout`, and the board's file I/O, as the Free and F2B boards do. Plain, headless-testable classes:
`ScaleMarks`, `ScaleJudge`, `VitrineLayout`, `ScaleScoreTable`, `GameFlow`'s Scale transitions and `GameUiModel`'s card
values. Only the model's lowest point and the camera framing touch libGDX, and neither needs a GL context in tests.

## Risks / Trade-offs

- [The Mig-3 may be hard to fly at the given scale and mass] -> its numbers are tuned against the flight test before
  the spec's example scores are trusted, and the realism thresholds are constants in one place (`ScaleMarks`).
- [The thresholds (5 %-30 %, 15 degrees-45) are my estimate, not measured] -> a synthetic-flight test pins them, and
  tuning them later changes constants and one spec number.
- [The Mig-3 glb may not follow the plane frame, or its lowest point may include a tail wheel] -> checked from bounds
  in a test; if it fails, the glb is re-imported with the chosen axes, not special-cased in code.
- [Existing tests assert the three-plane scene and `screens.golden`] -> updated in the same task group as the scene.
- [Free flight now lists `Mig-3` first] -> accepted and written in the spec; a later change can filter it out.

## Open Questions

- The exact positions and facing of the display row (pure scene data; settled while authoring and checked in runIde).
