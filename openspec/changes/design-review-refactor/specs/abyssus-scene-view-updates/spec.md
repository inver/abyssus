# Spec Delta

## Purpose

Defines when the scene view picks up changes to a scene and its project file, and keeps the IDE responsive while it
works out which scene actions are available.

## ADDED Requirements

### Requirement: Typing in the text editor updates the view after a pause
When the user types in the text tab of the open `.scene`, or of its project's `.abss`, the scene view SHALL show the
unsaved text once typing pauses, without re-reading the scene on every keystroke. The pause SHALL be short enough to
feel live (at most 300 ms).

#### Scenario: A burst of typing is read once
- **WHEN** the user types ten characters in quick succession into `Main Scene.scene`'s text tab while its scene view
  is open
- **THEN** the scene view re-reads the scene once after the typing stops, not ten times

#### Scenario: The result matches the final text
- **WHEN** the user changes `Model 0`'s `localPosition.x` from -3.035308 to 1 in the text tab and stops typing
- **THEN** within 300 ms the scene view draws `Model 0` at x 1, without the file being saved

#### Scenario: A malformed intermediate text is not shown
- **WHEN** the text is briefly invalid JSON while typing, and then valid again before the pause ends
- **THEN** the scene view never shows the parse error message

### Requirement: Plugin edits update the view at once
An edit made through the plugin (gizmo drag, Drop, Add Light, a component edit in the Properties panel or tree, the
eye toggle, the skybox chooser, Rename Scene) SHALL reach the open scene view without waiting for the typing pause.

#### Scenario: Component edit reaches the view
- **WHEN** `Model 0`'s `localPosition.x` is changed in the Properties panel
- **THEN** the scene view draws `Model 0` at the new position on its next frame

#### Scenario: Undo reaches the view
- **WHEN** a move of `Model 0` is undone from the scene view tab
- **THEN** the scene view draws `Model 0` at its previous position on its next frame

### Requirement: Changes on disk update the view
A change to the scene or project file on disk SHALL update the open scene view, as it does today.

#### Scenario: External change
- **WHEN** `Main Scene.scene` is changed by another program while its scene view is open
- **THEN** the scene view shows the new content after the IDE notices the change

### Requirement: Action availability reflects the current scene
Add Light, in the tree and in the scene view toolbar, SHALL be enabled exactly when the scene can be read and can take
another light, judged from the scene's current text. Repeated availability checks of an unchanged scene SHALL NOT
re-read it.

#### Scenario: Unreadable scene disables Add Light
- **WHEN** `Main Scene.scene`'s text is made invalid JSON
- **THEN** Add Light is disabled in the tree and in the toolbar
- **AND** once the text is valid again, Add Light is enabled again

#### Scenario: Unchanged scene is not re-read
- **WHEN** the IDE asks for Add Light's availability many times while `Main Scene.scene` does not change
- **THEN** the scene's text is parsed at most once for all of those checks
