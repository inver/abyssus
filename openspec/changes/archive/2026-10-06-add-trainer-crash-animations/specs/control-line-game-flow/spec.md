# Spec Delta

## ADDED Requirements

### Requirement: The crash shows on the plane

When a flight ends as a crash with a severity, the game SHALL play the crashed plane's model clip for that severity
once (`crash_little`, `crash_medium` or `crash_full`), from the moment of the crash, and SHALL hold its last frame
while the plane comes to rest and through GAME OVER. A plane whose model has no such clip SHALL be drawn as before.

#### Scenario: The Trainer crashes hard

- **WHEN** `Trainer` crashes with a medium severity
- **THEN** its left wing folds down at the root and its tail breaks off, and they stay so on GAME OVER

#### Scenario: A plane without crash clips

- **WHEN** `Racer` crashes
- **THEN** it is drawn whole, as before

### Requirement: The Trainer's crash clips

The bundled `Trainer` model SHALL have three crash clips, each starting from its rest pose: `crash_little`, where the
landing gear breaks; `crash_medium`, where the left wing and the tail break; and `crash_full`, where the gear, the
propeller, both wings and the tail come off.

#### Scenario: The clips are in the model

- **WHEN** the game loads `model_trainer`
- **THEN** it has `crash_little`, `crash_medium` and `crash_full`, and its rest pose is the whole plane
