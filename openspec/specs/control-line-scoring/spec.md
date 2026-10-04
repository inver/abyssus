# control-line-scoring Specification

## Purpose

Scores a control-line flight for laps and for the real control-line maneuvers the pilot flies, and keeps the best
flights in a local score table.

## Requirements

### Requirement: Laps

Each full turn around the pilot that the plane flies while airborne, in its flying direction, SHALL count as one lap
and score 10 points. A lap SHALL NOT be multiplied by the combo.

#### Scenario: Three laps

- **WHEN** a plane flies three full turns around the pilot in the air
- **THEN** the lap count is 3 and the laps have scored 30 points

### Requirement: Maneuvers are detected from the flight path

The game SHALL detect these maneuvers from the plane's path around the pilot: an inside loop (its climb angle turns
through a full circle upward and it returns to level flight below 30° of elevation within 8 s), an outside loop (the
same, downward), an inverted lap (a full lap flown upside down below 45° of elevation), a wingover (it climbs above
80° of elevation, passes over the pilot and returns to level flight below 20° within 6 s on the other side), and a
figure 8 (an inside loop followed by an outside loop that starts within 2 s).

#### Scenario: Inside loop

- **WHEN** a recorded path climbs from level flight through vertical, over the top and back to level flight at 10° of
  elevation in 5 s
- **THEN** one inside loop is detected

#### Scenario: Half a loop is not a loop

- **WHEN** a recorded path climbs through vertical and then dives back the way it came
- **THEN** no loop is detected

#### Scenario: Figure 8 replaces its loops

- **WHEN** an inside loop is followed by an outside loop starting 1 s after it ended
- **THEN** one figure 8 is detected, and the two loops do not also score

### Requirement: Maneuver points and combo

Maneuvers SHALL score inside loop 50, outside loop 50, inverted lap 80, wingover 100 and figure 8 150, times the combo
multiplier at the moment they complete. The multiplier SHALL start at 1, rise by 1 for each maneuver completed within
10 s of the previous one, up to 5, and fall back to 1 when 10 s pass without a maneuver or when the lines go slack.

#### Scenario: Chained loops

- **WHEN** three inside loops are completed, each 4 s after the previous one
- **THEN** they score 50, 100 and 150 points, and the best combo is 3

#### Scenario: Combo lost

- **WHEN** 12 s pass after a maneuver with no other maneuver
- **THEN** the multiplier is 1 again

### Requirement: Landing bonus

A flight that ends as a landing SHALL score 200 extra points. A crash SHALL keep the points scored so far and add
none.

#### Scenario: Landing after laps

- **WHEN** a flight scores 30 lap points and ends as a landing
- **THEN** its final score is 230

### Requirement: Local score table

The game SHALL keep the 10 best flights in `~/.abyssus-control-line/scores.json`, each with name, plane, score, laps,
best combo, flight time and date, best score first, and earlier flights first among equal scores. A flight SHALL enter
the table when it beats the lowest entry or the table has fewer than 10.

#### Scenario: Eleventh flight

- **WHEN** the table holds 10 flights with lowest score 120 and a flight scores 150
- **THEN** the new flight enters, the flight with 120 leaves, and the file holds 10 flights in order

### Requirement: A damaged score file does not stop the game

A score file that cannot be read SHALL be renamed to `scores.json.bad` (replacing an older one), the table SHALL start
empty, and the score table screen SHALL say the old scores could not be read.

#### Scenario: Not JSON

- **WHEN** `scores.json` holds text that is not JSON and the game starts
- **THEN** the file becomes `scores.json.bad`, the table is empty, and the score table screen shows that the old scores
  could not be read
