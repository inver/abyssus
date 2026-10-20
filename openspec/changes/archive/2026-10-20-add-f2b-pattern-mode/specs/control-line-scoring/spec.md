# Spec Delta

## ADDED Requirements

### Requirement: Free flight only

The laps, maneuvers, combo and landing bonus of this capability, and the score table in `scores.json`, SHALL apply to
Free flight and to Play in Abyssus. An F2B pattern flight SHALL NOT score laps, maneuver points, combo or the landing
bonus, and SHALL NOT be saved to `scores.json`. It is scored by the F2B rules instead.

#### Scenario: Laps in an F2B flight

- **WHEN** an F2B flight with `Stunter` flies its two take-off laps
- **THEN** no lap points are scored and `scores.json` does not change when the flight ends

#### Scenario: Free flight unchanged

- **WHEN** a Free flight with `Stunter` flies three laps
- **THEN** the laps score 30 points as before
