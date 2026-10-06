# Proposal

## Why

A crash ends the flight with GAME OVER, but the plane looks unharmed: the Trainer comes to rest whole however hard it
hit the ground. A crash that looks as bad as it was shows the player what went wrong.

## What Changes

- **Crash clips on the Trainer:** `model_trainer/model.glb` gets three clips, added by the Trainer's importer after the
  FlightGear import (`importers/TrainerCrashAnimations.kt`; `animateTrainer` adds them to a freshly imported model):
  - `crash_little`: the landing gear breaks.
  - `crash_medium`: the left wing and the tail break.
  - `crash_full`: gear, propeller, both wings and the tail come off.

  For them the wings are cut at their roots and the tail cone behind the cabin, and every moving part turns about its
  own pivot under one `Trainer` node. The rest pose and the convex hull stay as they were.
- **Crash severity from the impact speed:** a flight that crashes into the ground records its impact speed (how fast the
  plane came down onto the ground) and a severity: below 6 m/s little, below 10 m/s medium, faster full.
- **The game plays the clip:** the crashed plane plays its severity's clip once and holds the last frame while it comes
  to rest and through GAME OVER. A plane whose model has no crash clips is drawn as before.

## Capabilities

### Modified Capabilities

- `control-line-flight`: a crash into the ground has an impact speed and a severity.
- `control-line-game-flow`: the crashed plane shows its crash.

## Impact

- `games/control-line`: `flight/CrashSeverity.kt`, `Flight`, `FieldRenderer`, `ControlLineGame`, the Trainer's
  importer and `model.glb`, tests (`CrashSeverityTest`, `FlightOutcomeTest`, `TrainerCrashAnimationsTest`, the opt-in
  `CrashClipGlTest`), README.
- No change to Play in Abyssus: it shows telemetry and the editor's scene view, which does not play the crash.
