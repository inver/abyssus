# Spec Delta

## ADDED Requirements

### Requirement: Show Dummies toggle

The Scene view toolbar SHALL hold a "Show Dummies" toggle with the other display toggles. It SHALL be off when a view
opens. While it is off, dummy markers are hidden, except the marker of a selected dummy, as the `scene-dummies`
capability describes.

#### Scenario: Off on open

- **WHEN** a Scene view opens
- **THEN** the Show Dummies toggle is not pressed

#### Scenario: Turned on

- **WHEN** the user presses Show Dummies on a scene with dummies
- **THEN** every enabled dummy's marker is drawn
