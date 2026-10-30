# Proposal

## Why

At Pro the pilot flies the F2B schedule blind and only sees marks at the end, which on their own don't say what went
wrong. A real F2B pilot learns from video and coaching, comparing the figure they flew with the one the rules
describe. The judge from `add-f2b-pattern-mode` already builds the ideal track for every manoeuvre and keeps the flown
path and the deductions. This change shows them: a debrief that replays each manoeuvre with its ideal and flown tracks
side by side, for the flight just flown and for any flight on the F2B boards.

This change builds on `add-f2b-pattern-mode` and is applied after it.

## What Changes

- **Debrief** on GAME OVER after every F2B flight, at Pro and at Mid. The debrief screen shows:
  - the 15-row score sheet, where one manoeuvre is selected;
  - the field seen from the pilot's position, facing the selected manoeuvre, with its **ideal track** and **flown
    track** drawn in two colours, and the base and the 45° parallel as faint guides across the figure;
  - the plane flying the selected manoeuvre along its flown path, which can be played, paused, restarted and run at
    ½×, 1× or 2× speed;
  - the selected manoeuvre's deductions, each with what was measured, its tolerance and the points it cost, for
    example `Base height 0.62 m off (±0.30): −0.6`; for a manoeuvre scored 0, its reason;
  - a second camera, from outside the circle, to see the figure's depth.

  Up and Down select the manoeuvre. Escape returns to where the debrief was opened from.
- **Saved flights keep their recording.** When an F2B flight enters a board, its recording is saved too: the flown
  path at 30 samples a second with the plane's attitude, and per manoeuvre its time span, ideal track, mark, deductions
  or zero reason. On the score table, choosing an F2B row opens its debrief.
- Recordings live in `~/.abyssus-control-line/f2b-flights/<id>.json.gz`, one per saved flight. A score entry names
  its recording in a new optional `flight` field.
  - A recording is deleted when its flight leaves its board. Recordings that no entry names are deleted at start.
  - A recording that is missing or cannot be read leaves the entry and its marks in place. Its debrief shows the sheet
    with the message that the replay is not available.
- `f2b-scores.json` stays `version: 1`. `flight` is optional, and entries without it, saved before this change, open
  a sheet-only debrief.

### Native files

None. The change reads nothing new from the scene and writes no native Abyssus document. `f2b-scores.json` and the
recordings are game files.

### Out of scope

- The Noob level and drawing ideal tracks during a flight (`add-f2b-noob-guide`).
- Free flight replays.
- Exporting or sharing recordings, video capture, and a free-flying replay camera beyond the two fixed views.
- Re-judging an old recording with newer judge code. A replay shows the ideal tracks and marks saved with the flight.

## Capabilities

### New Capabilities

- `control-line-f2b-debrief`: the debrief of an F2B flight. It covers:
  - the sheet with a selected manoeuvre;
  - the ideal and flown tracks and the guides;
  - the plane's replay and its speeds;
  - the two views;
  - the deductions;
  - saving, naming, pruning and reading flight recordings, and how a missing one shows.

### Modified Capabilities

- `control-line-game-flow`: GAME OVER of an F2B flight offers Debrief, and an F2B row on the score table opens its
  debrief. These are added as new requirements, so the requirements `add-f2b-pattern-mode` modifies are left alone.

## Impact

- `projects/app-game-control-line`:
  - `pattern`: a `FlightRecording` built by the judge, and `RecordingStore` for `f2b-flights/`;
  - `PatternScoreTable`: the `flight` field, pruning;
  - a new GL-free `DebriefModel`: selection, playback clock, track segments, deduction lines;
  - `render/Cameras`: debrief views;
  - `screens/GameUi`: the debrief screen;
  - `flow/GameFlow`: `Screen.Debrief`;
  - `ControlLineGame`: replay drawing. It reuses `FieldRenderer`'s line drawing, extended with a line width per
    segment.
- Tests: new tests in `pattern` and `flow`, plus `PatternScoreTableTest` additions.
- Docs: the app's `README.md` (debrief controls, recordings folder).
- Disk: up to 30 saved flights (3 boards × 10) of about 0.3 MB each, compressed.
