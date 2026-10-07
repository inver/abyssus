# Proposal

## Why

Control Line scores flights like an arcade game: 10 points a lap, a few tricks in any order and a combo multiplier.
The real sport, FAI class F2B (Sporting Code Section 4, Volume F2, 2027 edition, rule 4.2), flies a fixed schedule of
manoeuvres within 7 minutes. Judges mark each manoeuvre from 1 to 10 on how closely it follows a precisely defined
track, and multiply the mark by a difficulty factor K. An F2B mode turns the game into practice for the real schedule,
and the arcade mode stays for casual play.

This is the first of three planned changes. It builds the rules, the judge, the mode and the Pro and Mid assist levels.
`add-f2b-pro-debrief` adds the replay of the ideal and flown tracks. `add-f2b-noob-guide` adds the Noob level, which
draws the ideal track ahead of the plane.

## What Changes

- The main menu offers **Free flight** (today's game, scored as now) and **F2B pattern**. F2B pattern asks for an
  assist level, **Pro** or **Mid**, then offers only stunt-class planes.
- An F2B flight follows the F2B rules:
  - The schedule is the 15 scored manoeuvres of rule 4.2.12, from Take-off (K 2) to Landing (K 5), with K summing to
    130 and a maximum score of 1300.
  - The flight time is 7:00, counted from the start of the take-off ground roll.
  - The zero rules apply. A manoeuvre scores 0 when it is:
    - omitted;
    - not completed;
    - flown with the wrong number of repeats;
    - flown out of order;
    - started less than 1½ laps after the previous one;
    - performed after 7:00.
  - A crash, or lines that go slack, scores 0 from the manoeuvre in progress on.
  - A landing scores 0 when it ends after 7:00 or as a crash.
- A judge marks every manoeuvre from the flown track. The mark is from 1 to 10 in 0.1 steps. Each manoeuvre has an
  ideal track built from its F2B description, anchored where the pilot flew it, and the mark is 10 minus deductions for
  how far the flown track strays from it: height of the base, the 45° parallel, verticals, corner tightness, segment
  lengths, repeats on top of each other, and symmetry.
- **Mid** shows on the HUD the next manoeuvre and its K, the 1½-lap pause count, the 7:00 clock and a height warning
  outside 1 to 3 m between manoeuvres. It also shows a toast with each manoeuvre's mark × K as soon as the manoeuvre
  ends. **Pro** shows only line tension, fuel and the clock.
- GAME OVER after an F2B flight shows the score sheet: each manoeuvre's mark, K and points, or 0 with its reason, and
  the total.
- F2B flights are saved in a new file, `~/.abyssus-control-line/f2b-scores.json`, holding one top-10 board per assist
  level. Each entry holds name, plane, score, flight time, date and the 15 marks. A file that cannot be read is
  renamed to `f2b-scores.json.bad`. The score table screen gets a tab per board, next to Free flight.
- The bundled `Stunter`'s fuel time goes from 180 s to 360 s. That leaves time for the full schedule, and the engine
  stops before 7:00 so the dead-stick landing of rule 4.2.15.17 fits. Free flight with `Stunter` lasts longer too.
- The first task is a flyability spike: scripted handle input flies square and triangular loops with `Stunter`. If
  the corners cannot be tight, `Stunter`'s aerodynamic values in the field scene are tuned.

### Native files

- Reads `PlaneComponent.planeClass` to offer only `STUNT` planes in F2B, and `PlaneComponent.fuelTime` as today.
- Writes one value in the committed `project/ControlLine/scenes/Field.scene`: `Stunter`'s `PlaneComponent.fuelTime`
  becomes 360. If the spike calls for it, `Stunter`'s aerodynamic numbers change too. The edits are made by hand to
  the committed project, like other edits to its data. The scene stays `format: "abyssus"`, `formatVersion: 1`, and
  no component schema changes.
- `f2b-scores.json` is a new game file, not a native Abyssus document (`{version: 1, boards: {...}}`).
  `scores.json` is unchanged.

### Out of scope

- The replay debrief comparing the ideal and flown tracks, and opening a saved flight's sheet from the score table
  (`add-f2b-pro-debrief`).
- The Noob assist level and any drawing of an ideal track in the 3D view (`add-f2b-noob-guide`). Its board is
  reserved in the file format but not offered.
- Reasons for a mark on the HUD ("bottom 40 cm high"). The judge keeps its deductions internally.
- The parts of the contest around a flight:
  - starting and the 3-minute preparation time, since Starting is K 0;
  - attempts, rounds, several judges and their averaging, fly-offs and classification;
  - noise tests, line pull tests, re-flights and parts falling off.
- Annex 4B (the Judges' Guide). It is not in the 2027 volume, so the deduction weights are this game's own,
  documented and tunable.
- F2B in Play in Abyssus. Play keeps Free flight.

## Capabilities

### New Capabilities

- `control-line-f2b-pattern`: an F2B flight. It covers:
  - the schedule and K-factors;
  - the order, pause, time and crash rules that give a 0;
  - the score as the sum of mark × K;
  - the Stunter's fuel for a full pattern;
  - the F2B score boards per assist level and their file.
- `control-line-f2b-judging`: how one manoeuvre is marked from the flown track. It covers:
  - the ideal track of each manoeuvre per its F2B description, and its anchor;
  - recognising which manoeuvre was flown, and how many repeats;
  - the mark from 1 to 10 in 0.1 steps from the deviations.

### Modified Capabilities

- `control-line-game-flow`:
  - Main menu: Free flight and F2B pattern replace Start.
  - New: an assist level screen.
  - Plane select: only stunt planes in F2B.
  - Flying: a HUD per mode and level.
  - GAME OVER: the score sheet in F2B.
  - Retry: keeps the mode and level.
  - Score table screen: tabs.
- `control-line-scoring`: its laps, maneuvers, combo and landing bonus apply to Free flight only.

## Impact

- `projects/app-game-control-line`:
  - a new `pattern` package: schedule, ideal tracks, sphere-path geometry, recognition, grader, judge and the F2B
    score table;
  - `track/SphereTrack` (the samples gain the plane's height above the ground);
  - `flight/Flight` and `FlightSession`, so a flight's scoring is chosen per mode;
  - `flight/FlightReport`;
  - `flow/GameFlow`, `screens/GameUi`, `ControlLineGame`, `Main`;
  - the bundled `Field.scene`.
- Tests: new tests in `pattern`; updates to `GameFlowTest` (Stunter fuel 360, the new screens), `ScoreTableTest` and
  `FlightTest` as needed.
- Docs: the app's `README.md` (modes, F2B rules, the new scores file, code table).
- No new dependencies, nothing in the plugins or libraries.
