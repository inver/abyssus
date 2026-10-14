# Spec Delta

## MODIFIED Requirements

### Requirement: Move and rotate modes

The scene view SHALL have a Select, a Move and a Rotate mode, switched by a toolbar toggle or by Q (Select), W (Move) and E (Rotate) while
the view has focus; Move SHALL be the initial mode. In Move mode the selected object SHALL show X, Y and Z arrows along
the world axes, or along the object's own axes when the view's space is Local; in Rotate mode, X, Y and Z rings around them. In Select mode the selected object SHALL show no handles.
Handles SHALL keep a constant size on screen and be drawn on top of the scene.

#### Scenario: Switch with keys

- **WHEN** an object is selected and the user presses E, then W
- **THEN** the rotate rings are shown, then the move arrows

#### Scenario: Select shows no handles

- **WHEN** an object is selected and the user presses Q
- **THEN** the object stays highlighted and neither arrows nor rings are shown

#### Scenario: Handles stay visible

- **WHEN** the selected object is behind a terrain or far from the camera
- **THEN** its handles are still drawn on top, at the same screen size
