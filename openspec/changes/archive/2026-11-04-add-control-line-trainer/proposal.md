# Proposal

## Why

A beginner in Control Line crashes within seconds, long before learning how the handle moves the plane. Watch AI only
shows how to fly, and the Noob guide only shows where to fly. Nothing saves the student's mistakes or tells them in time
that one is coming. On a real field an instructor stands beside the student and takes the handle when the plane is
about to hit the ground. The game already has a bot that flies through the player's own handle, so it can play that
instructor.

## What Changes

- An **Instructor** toggle on plane select, for every mode (Free flight, F2B pattern at every assist level, and
  Scale). It is off at start, kept for the rest of the game session and kept by Retry. Watch AI ignores it.
- During a flight with the instructor on, the game measures **danger** every fixed step from what actually ends a flight:
  the height left to pull out of the plane's dive, flying inverted close to the ground, and lines that have been slack in
  the air. Danger runs from 0 to 1.
- **Coach:** once danger reaches a low warning level, the HUD shows a **ghost handle** next to the player's handle. It
  marks the tilt the instructor would set, with a one-word cue (Pull or Push), so the student can correct the plane before
  anything is taken from them.
- **Safety net:** as danger grows, the instructor takes a growing share of the handle, up to all of it. It flies the plane
  back to level flight at the base (and back upright, and into the bot's landing glide once the engine has stopped). Once
  danger has stayed low for a moment, it hands the handle back gradually. The HUD shows INSTRUCTOR while it holds at least
  half the handle. Such a span is a **takeover**.
- The judges score what was actually flown, assisted or not.
- **Untouched flights count.** A flight with the instructor on but no takeover is scored, named and saved like any human
  flight. A flight with one or more takeovers is **Assisted**: GAME OVER shows how many times the instructor took over and
  says the flight was not saved. It skips name entry and is not written to any score table, board or board recording.
- The F2B **debrief** of an assisted flight draws the parts of the flown path flown during a takeover in a third colour,
  named Instructor in the legend. A manoeuvre with a takeover in it says so above its deductions.

Native file fields: the change edits the bundled UI asset `projects/app-game-control-line/project/ControlLine/assets/screens/meta.json`
(adding the toggle, the ghost handle, the INSTRUCTOR flag and their bindings). The document keeps `format: "abyssus"` and
`formatVersion: 1`, is still validated by the shared UI asset loader, and keeps its unrelated keys and numbers. No
scene, project or component field changes. The score files and the F2B recording file keep their format and version:
an assisted flight is never written, so takeover spans live only in memory.

Out of scope:
- Instructor help that fades as the student improves.
- A lesson curriculum (level laps, then a wingover, ...) or demonstrations of each lesson.
- An instructor that follows the Noob guide or the figure the student means to fly. The instructor only recovers to safe
  flight.
- Controller rumble on a takeover.
- A separate Assisted score board.
- Saving the toggle between game sessions.
- The instructor in Abyssus Play (`PlayModule`).

## Capabilities

### New Capabilities

- `control-line-instructor`: the Instructor toggle, how danger is measured, the ghost handle and its cue, the
  takeover and handback, the HUD flag, the untouched-or-assisted rule for scores, and the debrief's takeover marks.

### Modified Capabilities

None. The instructor's rules are written as their own capability, as exceptions that apply only to flights with the
instructor on. The requirements of `control-line-game-flow`, `control-line-f2b-debrief`, `control-line-scoring`,
`control-line-f2b-pattern` and `control-line-scale` stay as written. This also avoids `MODIFIED` deltas that would
collide with the open changes `add-control-line-gamepad-support` (Flying) and `add-control-line-scale-mode` (plane
select).

## Impact

- `projects/app-game-control-line`:
  - `ai`: a new instructor control source and its danger measure. It reuses `F2bPilot`, `Handling`, `Parallel` and
    `F2bFigures`.
  - `flow`: `Screen.Flying` carries the instructor flag, and `FlightReport` carries the takeovers. `GameFlow` gets the
    toggle and the assisted rule.
  - `FlightRunner`: builds the instructor around `HumanControl`.
  - `screens`: new bindings and a new action in `GameUiContract` and `GameUiModel`, plus the edited UI asset.
  - `pattern` / `render`: the debrief model's takeover spans and the Instructor track colour.
  - The README's Controls section and a new Instructor section.
- New tests in the game module: the danger measure, takeover and handback, the bundled planes saved from a dive held at
  full down, the assisted rule in `GameFlow`, the bindings, and the debrief spans.
- No change to `lib-core`, `lib-physics`, `lib-game-link`, the plugin, file format versions or the shipped-game
  classpath.
