# Tasks

Apply after `add-f2b-pattern-mode`. All code is in `projects/app-game-control-line`. Run single tests with
`./gradlew :app-game-control-line:test --tests '<class>'`. Manual checks (M1, M2, ...) use
`./gradlew :app-game-control-line:run`, with scores in a scratch home (`-Duser.home=<tmp>` through
`JAVA_TOOL_OPTIONS`), so a real `~/.abyssus-control-line` is untouched.

## 1. Recordings

- [ ] 1.1 Add `FlightRecording`: 30 Hz samples (time, offset, rotation), per-manoeuvre judgements (span, mark or
  reason, deductions, ideal track in world offsets thinned to 0.25 m), plane name, line length and level. Fill it from
  `PatternJudge` during the flight. Verify with `FlightRecordingTest`:
  - a 10 s synthetic flight gives 300 samples ±1;
  - every judged manoeuvre has its span inside the samples;
  - an omitted manoeuvre has a reason and no ideal track.
- [ ] 1.2 Add the recording's JSON codec (`version: 1`, gzip) and `RecordingStore`: save through a temporary file and
  an atomic move, load, delete, prune unnamed files and leftover temporary files, and rename an unreadable file to
  `.bad`. Verify with `RecordingStoreTest`:
  - a round trip keeps samples to 3 decimals and the judgements exactly;
  - prune keeps only the named ids;
  - text that is not gzip becomes `.bad` and loads as not available.
- [ ] 1.3 Add the optional `flight` field to `PatternScoreTable` entries. Save the recording before the entry, keeping
  the entry if the save fails. Delete the recording of an entry that leaves its board, and prune at start. Check that
  the reader from `add-f2b-pattern-mode` ignores unknown fields and accepts a missing `flight`; if not, make it so.
  Verify with `PatternScoreTableTest`:
  - a Mid flight entering saves `f2b-flights/<id>.json.gz` and names it;
  - the eleventh flight deletes the leaving flight's recording;
  - a flight that does not qualify writes nothing;
  - an entry without `flight` still loads.

## 2. Debrief model and cameras

- [ ] 2.1 Add `DebriefModel`:
  - selection, starting on the first manoeuvre;
  - the playback clock with ½, 1 and 2 speed, pause, restart, and holding the last pose at the end;
  - `segments()` with `IDEAL`, `FLOWN` and `GUIDE` tags, and guides over the figure's azimuth span ±10°;
  - `planePose()` with interpolation;
  - `deductionLines()` largest first, with "no deductions" for a 10 and the reason for a 0;
  - `frame()`;
  - sheet-only mode.

  Verify with `DebriefModelTest`, with cases named after the spec scenarios:
  - Inside loops: the ideal and flown segments plus the guides;
  - Omitted: no segments and the reason;
  - Slow motion: 12 s takes 24 s at ½, and Space holds the pose;
  - Low bottoms: one line `Base height 0.60 m off (±0.30): −0.6`;
  - Too soon;
  - A deleted recording: the sheet only, with "replay not available".
- [ ] 2.2 Add `Cameras.debrief(view, pilot, frame)` for the pilot view (with field of view up to 90°) and the outside
  view (1.6 × line length, 6 m up). Verify with `DebriefCameraTest`:
  - every frame point is inside the frustum for loops, vertical eights and overhead eights, in both views;
  - the outside eye sits on the figure's azimuth.

## 3. Flow and screens

- [ ] 3.1 Extend `GameFlow`:
  - `Screen.Debrief(from, model)`;
  - `debrief()` on F2B GAME OVER only;
  - row selection with Up and Down on F2B score tabs, and `openRow`, which loads through `RecordingStore`;
  - `back()` returning to GAME OVER, or to Scores on the same tab and row.

  Verify with `GameFlowTest`:
  - Debrief after a crash opens on Take-off;
  - Free flight has no Debrief;
  - Replaying the best Pro flight, where Escape returns to the Pro tab with the first row selected;
  - a missing recording opens sheet-only.
- [ ] 3.2 Add the optional `width` to `LineSegment`, and batch by width in `FieldRenderer`. Verify by compiling, and
  with M1: control lines still look the same in Free flight.
- [ ] 3.3 Draw the debrief in `ControlLineGame`: a fresh field scene, the recorded plane posed from `planePose()`
  (or a cross when no plane has that name), the pilot hidden in the pilot view, and segments coloured by tag. Wire
  the keys: Up and Down, Space, R, 1, 2, 3, V and Escape. Add the debrief layout to `GameUi`: the sheet with the
  selected row, a legend, the deductions panel and the key help. Verify with manual checks:
  - M2: after a Mid flight, Debrief shows Take-off with green ideal and orange flown tracks and the plane replaying;
  - M3: Down six times turns the view to the inside squares;
  - M4: V shows the outside view, with both loops of the vertical eights in frame;
  - M5: 1, 2 and 3 change the speed, Space pauses and R restarts;
  - M6: after a Pro flight enters the board, Scores → Pro → Enter opens its replay, and Escape returns to the same
    row;
  - M7: deleting the recording file shows "replay not available" with the marks.
- [ ] 3.4 Update `projects/app-game-control-line/README.md`: the debrief and its keys, `f2b-flights/` and pruning,
  and that replays show the marks judged at the time. Verify with `scripts/check-docs.sh`.

## 4. Integration

- [ ] 4.1 Run `./gradlew check` and `scripts/check-docs.sh`. Verify that both pass.

## Workflow follow-up

- `add-f2b-noob-guide` can reuse `DebriefModel.segments()`'s world-space ideal track for drawing ahead of the plane.
- Archive after review, after `add-f2b-pattern-mode` has been archived.
