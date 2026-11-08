# Spec Delta

## ADDED Requirements

### Requirement: Dummy markers are picked by screen distance

When dummy markers are drawn, a click within a few pixels of a dummy marker's centre SHALL pick that dummy. The test
SHALL use CPU data only. When a marker and a model both lie under the click, the marker SHALL win, because it is drawn
on top. A hidden marker (Show Dummies off, dummy not selected) SHALL NOT be picked.

#### Scenario: Marker over a model

- **WHEN** Show Dummies is on and the user clicks a dummy marker that lies in front of its parent's model
- **THEN** the dummy is picked, not the model

#### Scenario: Hidden marker

- **WHEN** Show Dummies is off and the user clicks where an unselected dummy's marker would be
- **THEN** the dummy is not picked
