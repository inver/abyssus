# Spec Delta

## ADDED Requirements

### Requirement: Typing is followed after a pause

When the user types in the text tab of the open `.scene`, or of its project's `.abss`, the scene view SHALL show the
unsaved text once typing pauses, instead of re-reading the scene on every keystroke. The view SHALL catch up within
300 ms of the last keystroke.

#### Scenario: A burst of typing is read once

- **WHEN** the user types ten characters in quick succession into `Main Scene.scene`'s text tab while its scene view
  is open
- **THEN** the scene view re-reads the scene once after the typing stops, not ten times

#### Scenario: The view shows the final text

- **WHEN** the user changes `Model 0`'s `localPosition.x` from -3.035308 to 1 in the text tab and stops typing
- **THEN** within 300 ms the view draws `Model 0` at x 1, without the file being saved

#### Scenario: A brief invalid state is not shown

- **WHEN** the text is invalid JSON for part of a typing burst and valid again before the pause ends
- **THEN** the view never shows the parse error message

### Requirement: Plugin edits are shown at once

An edit made through the plugin SHALL reach the open scene view on its next frame, without waiting for the typing
pause. This covers a gizmo drag, Drop, Add Light, a component edit in the Properties panel or tree, the eye toggle, the
skybox chooser, Rename Scene, and Undo or Redo of any of them.

#### Scenario: Component edit

- **WHEN** `Model 0`'s `localPosition.x` is changed in the Properties panel
- **THEN** the view draws `Model 0` at the new position on its next frame

#### Scenario: Undo

- **WHEN** a move of `Model 0` is undone from the scene view tab
- **THEN** the view draws `Model 0` at its previous position on its next frame
