# Spec Delta

## ADDED Requirements

### Requirement: Noob assist level

The assist level screen SHALL offer Noob after Pro and Mid, described as drawing the ideal track ahead. A Noob flight
SHALL have Mid's guidance (next manoeuvre with K, pause count, clock, height warning and mark toasts) and the Noob
guide. It SHALL be judged like Pro and Mid.

#### Scenario: Choosing Noob

- **WHEN** the player chooses F2B pattern, then Noob, then `Stunter`
- **THEN** a Noob flight starts with the take-off guide drawn and the HUD showing Take-off (K 2) next

### Requirement: Noob board and debrief

Noob flights SHALL be saved on the Noob board and shown on a Noob tab of the score table screen, after the Pro and Mid
tabs. GAME OVER of a Noob flight SHALL offer Debrief, and a Noob row SHALL open its debrief, as for Pro and Mid.

#### Scenario: A Noob flight on the board

- **WHEN** a Noob flight scores 845.20 and enters the Noob board
- **THEN** the Noob tab lists it highlighted, the Pro and Mid boards are unchanged, and its row opens its debrief
