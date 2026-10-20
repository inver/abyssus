# Proposal

## Why

Players can fly and review an F2B pattern but cannot watch an autonomous pilot demonstrate the complete routine. A Watch AI option will show the actual plane taking off, flying the schedule and landing in the circle, using the same physics and judge as a player flight.

## What Changes

- Add Watch AI to F2B plane selection, alongside the existing player launch, using the selected stunt plane and assist display level.
- Add a deterministic local bot that controls the handle from flight feedback through startup, takeoff, all scheduled figures and pauses, fuel exhaustion and landing.
- Show AI demonstration status, preserve pause/resume, retry and results/debrief, and keep demonstrations out of player score boards and saved board recordings.
- Require a complete real-physics demonstration with the bundled Stunter: all 15 manoeuvres recognized and positively marked, landing inside the flying circle by 7:00. Perfect marks are not required.
- Preserve player flight controls and editor Play behavior.

## Capabilities

### New Capabilities

- `control-line-ai-pilot`: Autonomous full-pattern flight, honest judging, supported baseline and failure handling.

### Modified Capabilities

- `control-line-game-flow`: Watch AI launch, exclusive input ownership, demonstration identity, results and retry behavior.

## Impact

Targets `projects/app-game-control-line`: flight control boundary, `ControlLineGame`, `GameFlow`, `GameUiModel`, `GameUiContract`, pattern geometry integration, authored screens and tests. No external AI service, model downloads or new native dependencies.

Read existing native project asset registration and scene entity transforms, pilot settings and `PlaneComponent` values (including class, line length, fuel time and aerodynamic parameters). Change only the UI asset's screen/widget definitions, labels, bindings and actions in `project/ControlLine/assets/screens/meta.json`. Continue validating `.abss`, `.scene` and `meta.json` through `AbyssusDocumentFormat` before binding. Keep `format: "abyssus"`, integral `formatVersion: 1`, unknown extension data, unrelated key order and number text. No format version, scene tuning or persistence schema changes are planned; the runtime does not write the scene.

The implemented but unarchived `add-f2b-pattern-mode`, `add-f2b-noob-guide` and `add-f2b-pro-debrief` changes supply the pattern, guidance and review flow. Reconcile pattern-mode delta wording about universal human controls and score eligibility during implementation so archive order cannot undo the AI exceptions.

Out of scope: background menu flights, single-manoeuvre selection, player takeover, opponents, difficulty presets, learned policies, editor Play bot controls, arbitrary scene browsing, new engine-start simulation and guarantees for arbitrary modified aircraft. Startup means the existing stationary takeoff initialization with the engine running, not a new ignition interaction. Full completion is guaranteed against the committed Stunter/field baseline; other stunt planes use the same controller with honest failure results. Update the game README and changelog with the feature and its limits.
