# Tasks

## 1. Clips on the Trainer

- [x] 1.1 `importers/TrainerCrashAnimations.kt`: cut the wings and the tail, pivot the parts under `Trainer`, sample the
  three clips above the ground; run it from `importTrainer`, and as `animateTrainer`. Verify: the rest pose's bounds
  match the import's part by part.
- [x] 1.2 Add the clips to the bundled `model.glb`. Verify: `TrainerCrashAnimationsTest` (three clips through Assimp,
  the right parts moved, each starting from rest; the hull keeps the 1.0 m span and the wheels at -0.136 m).

## 2. Severity

- [x] 2.1 `CrashSeverity`, `crashSeverity`, `Flight.impactSpeed` and `Flight.crash`. Verify: `CrashSeverityTest`, and
  `FlightOutcomeTest` touch-downs at 4.5, 8 and 14 m/s, and a landing with none.

## 3. Playing it

- [x] 3.1 `FieldRenderer.draw` plays the clips it is given once and holds the last frame; `ControlLineGame` gives it the
  crashed plane's clip. Verify: `CrashClipGlTest` (`-Dabyssus.glTests=true`): each clip moves its parts and holds the
  last frame.
- [x] 3.2 README: the clips and the crash speeds.
