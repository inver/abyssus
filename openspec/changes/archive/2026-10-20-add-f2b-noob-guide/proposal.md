# Proposal

## Why

The F2B schedule is hard to learn. Squares, triangles, hourglass and clover have exact sizes and angles that a new
pilot cannot picture from a name on the HUD. The judge from `add-f2b-pattern-mode` already builds every manoeuvre's
ideal track. The Noob assist level draws that track in the sky ahead of the plane, so a beginner can fly the
schedule by following lines, before moving to Mid prompts and then flying blind at Pro.

This change completes the plan of three changes. It is applied after `add-f2b-pattern-mode` and
`add-f2b-pro-debrief`.

## What Changes

- **Noob** joins Pro and Mid on the assist level screen: "the ideal track is drawn ahead of you".
- During a Noob flight the game draws, in the 3D view, the ideal track of the next manoeuvre:
  - **Where it appears.** As soon as the 1½-lap pause after the previous manoeuvre is done, the track appears, with
    its start a quarter lap ahead of the plane, marked by a start gate (a short vertical bar at the base).
  - **It waits for the pilot.** The track stays at that place on the circle, so a pilot who is not ready passes the
    gate and starts on the next lap.
  - **Progress.** Once the judge sees the manoeuvre start, the part of the track already flown dims and the part
    ahead stays bright.
  - **Moving on.** When the manoeuvre is scored, its track disappears.
  - **Other tracks.** Take-off shows its climb and the two laps at the base from the start. The landing glide is shown
    once the engine has stopped after the clover.
- A faint ring at the base and a fainter one at the 45° parallel are drawn around the whole circle, all flight long.
- Noob has everything Mid has: the next manoeuvre with K, the pause count, the clock, the height warning and the mark
  toasts.
- Noob is judged exactly like Pro and Mid, from where the pilot actually flew. Flying along the drawn track earns a
  high mark because the track is the ideal one.
- Noob flights go on their own Noob board in `f2b-scores.json`, already reserved by `add-f2b-pattern-mode`. The score
  table gets a Noob tab. GAME OVER offers Debrief after Noob flights too, with recordings as for Pro and Mid.

### Native files

None. Nothing new is read from the scene or written to a native document. `f2b-scores.json` gains entries on the
`noob` board, which its version 1 format already has.

### Out of scope

- Steering help of any kind: autopilot, input smoothing or slowing time. Noob shows the track; the pilot flies.
- Colouring the track by how close the plane is to it, a trail of the plane's path, or audio cues.
- Choosing where the figure is placed (wind, a judges' side). The game always places it a quarter lap ahead.
- Drawing tracks at Pro or Mid during the flight.

## Capabilities

### New Capabilities

- `control-line-f2b-guide`: the Noob guide drawn during an F2B flight. It covers:
  - when the next manoeuvre's ideal track appears, where it is placed, and its start gate;
  - waiting for the pilot, dimming the flown part, and removal once scored;
  - the take-off and landing guides;
  - the base and 45° rings.

### Modified Capabilities

- `control-line-game-flow`: added requirements for the Noob level:
  - offered on the assist level screen, with Mid's guidance;
  - its score table tab;
  - its debrief.

  `add-f2b-pattern-mode`'s "Assist level" and "Score table screen" and `add-f2b-pro-debrief`'s "Debrief from GAME
  OVER" name only Pro and Mid. A task updates those deltas if they are still open, and otherwise the added
  requirements extend them.

## Impact

- `projects/app-game-control-line`:
  - a new GL-free `pattern.GuidePlanner`: anchor placement, gate, visible segments, dimming;
  - `PatternJudge`, which exposes the expected manoeuvre's state (pause done, started, progress), mostly already
    public for the Mid HUD;
  - `flow/GameFlow`: `NOOB` offered;
  - `screens/GameUi`: the assist entry, the Noob HUD (Mid's), and the Noob tab;
  - `ControlLineGame`: the guide's segments added to the lines drawn while flying.

  It reuses `IdealTracks`, `LineSegment` with width, and the debrief's world-space conversion.
- Tests: `GuidePlannerTest` and `GameFlowTest` additions.
- Docs: the app's `README.md` (assist levels).
