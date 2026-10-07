# Spec Delta

## ADDED Requirements

### Requirement: Debrief from GAME OVER

After an F2B flight, at Pro and at Mid, GAME OVER SHALL offer Debrief next to Retry, Scores and Main menu, after any
name entry. Debrief SHALL open the debrief of the flight just flown, whether or not it entered a board, and Escape
there SHALL return to GAME OVER. Free flight SHALL NOT offer Debrief.

#### Scenario: Debrief after a crash

- **WHEN** a Pro flight with `Stunter` crashes during the triangles and the player chooses Debrief on GAME OVER
- **THEN** the debrief opens on Take-off, and the manoeuvres from Two inside triangular loops on show "crashed"

#### Scenario: Free flight

- **WHEN** a Free flight ends
- **THEN** GAME OVER offers Retry, Scores and Main menu only

### Requirement: Debrief from the score table

On an F2B tab of the score table screen, Up and Down SHALL select a row, and Enter or a click SHALL open that flight's
debrief. Escape there SHALL return to the score table on the same tab and row.

#### Scenario: Replaying the best Pro flight

- **WHEN** the player opens Scores, switches to the Pro tab and presses Enter on the first row
- **THEN** the debrief of that flight opens, and Escape returns to the Pro tab with the first row selected
