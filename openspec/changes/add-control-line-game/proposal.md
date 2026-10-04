# Proposal

## Why

Abyssus needs a real game to prove the editor-for-games chain end to end: a scene authored in Abyssus, game data in
custom components, Jolt physics, and Play inside the editor running the game's own code. A control-line flight
simulator fits well. The pilot controls only the elevator through two lines, so input is simple. But the flight
depends entirely on line tension, which is a real physics problem. Planes being entities in a scene means adding or
tuning a plane is an edit in Abyssus, not a code change.

## What Changes

- **A game module `games/control-line`** (`:games:control-line`): a libGDX desktop application (LWJGL3 backend) on
  `runtime` and `physics`, with its own bundled native Abyssus project (a flying field with terrain, a sky, the pilot circle
  and the planes parked beside it).
- **Game components,** declared with `@SceneComponent` / `@Field` and editable in Abyssus:
  - `PilotComponent`: handle height and line spacing at the handle.
  - `PlaneComponent`: class, line length and diameter, leadout anchors, engine thrust and fuel time, aerodynamic
    coefficients (wing area, lift slope, zero-lift drag, elevator effect, pitch damping), rudder and engine offset.
  - A plane's display name is its `NameComponent`, and its mass and collider are the physics components from
    `add-jolt-physics`.
- **Flight.** The plane is a dynamic Jolt body. At takeoff, game code creates two lines from the pilot's handle (a
  kinematic body) to the plane's leadouts as distance constraints of 0 to L, so they pull when taut and go slack.
  The elevator follows the handle tilt only through taut lines. Each physics step adds thrust, lift, drag, the
  elevator moment and line drag. The ground is a height field built from the field's terrain. Line tension comes
  from the physics library's rope tension readout and drives the HUD.
- **Ending a flight.** The flight ends in GAME OVER on ground contact at speed or with the plane inverted. Lines slack
  past a time limit also end it. Fuel running out is not a crash: a landing that touches down gently scores a bonus
  and ends the flight.
- **Scoring.** Free flight. Laps score points, and detected maneuvers (inside loop, inverted lap, wingover, figure 8)
  add bonuses with a combo multiplier. Maneuvers are detected from the plane's track in sphere coordinates around the
  pilot, with no shape judge.
- **Screens.**
  - Main menu: start, scores, quit.
  - Plane select: the camera moves between the parked planes of the field scene, showing each plane's name, class and
    key values.
  - Game: the pilot's-eye camera and a HUD with tension, laps, score, combo and fuel.
  - GAME OVER: the score; name entry when it makes the top 10; then Retry (same plane), Score table or Main menu.
  - Score table: the top 10.
- **Scores** are kept locally in a JSON file under the user's home directory (`~/.abyssus-control-line/scores.json`):
  name, plane, score, laps, best combo, flight time and date.
- **Play in Abyssus.** The module implements `PlayModule`. Its export task writes `components.schema.json` and
  `play.json` into the bundled project, so pressing Play in Abyssus flies the plane selected in the tree (or the first
  parked plane) with the same input.

**Fields read/written.**
- **Read:** the bundled project's `.abss` and field `.scene`: `NameComponent`, `TypeComponent`, `PositionComponent`,
  `RenderComponent` (plane models and the field terrain), the terrain's `.terra` data, sky assets, and the custom
  `PilotComponent`, `PlaneComponent` and physics components.
- **Written:** the game itself writes no project, scene or asset file, only its score file. Its scene data is authored in Abyssus under
  the `add-custom-components` rules. No further format change.

**Out of scope.**

- A judged stunt pattern (competition mode), combat and racing classes.
- Wind and weather, sound, multiplayer, online score tables.
- Mobile and web builds.
- Prefabs or a separate hangar scene.
- Gamepad support beyond what the input mapping decides in design. Keyboard and mouse are in scope.

## Capabilities

### New Capabilities

- `control-line-flight`: the line-tethered flight model: lines as slack-capable constraints, control only through
  taut lines, aerodynamic forces, tension readout, crash and landing detection.
- `control-line-scoring`: laps, maneuver detection, combos, the landing bonus and the local top-10 score table.
- `control-line-game-flow`: the screens and their transitions (main menu, plane select, game, GAME OVER with Retry,
  score table), and plane choice from the parked planes of the field scene.

### Modified Capabilities

None.

## Impact

- **Build:** `settings.gradle.kts` includes `:games:control-line`; the module depends on `:runtime`, `:physics` and
  the libGDX LWJGL3 backend with desktop natives, and gets a `run` task and the schema / play export task.
  `./gradlew check` runs its headless tests (flight, scoring, maneuver detection, screen flow logic). Anything that
  opens a window stays behind `-Dabyssus.glTests=true`.
- **Assets:** a small bundled native Abyssus project under the module (`format: "abyssus"`, `formatVersion: 1`) with plane models, a field terrain and a sky. Asset
  licensing must be recorded.
- **Docs:** `AGENTS.md` (layout and commands), `docs/ai/architecture.md`, a `games/control-line/README.md` with how to
  open the bundled project in Abyssus (as a copy, like the `runIde` fixture rule) and how to play.
- **Depends on:** `extract-scene-runtime`, `add-custom-components`, `add-jolt-physics`.
