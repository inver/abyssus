# Spec Delta

## ADDED Requirements

### Requirement: Project view settings

The project properties view (see "Project properties") SHALL also show a `Show FPS` checkbox reflecting the saved preference defined by `scene-fps-display`. The checkbox SHALL be usable with no scene open. The view SHALL explain that these are IDE view settings, and changing the checkbox SHALL leave `.abss`, `.scene` and asset files unchanged.

#### Scenario: New project preference
- **WHEN** `Untitled.abss` is selected in an IDE project with no saved FPS preference
- **THEN** `Show FPS` is unchecked

#### Scenario: Toggle before opening a scene
- **WHEN** the user checks `Show FPS` with no scene view open and then opens `Main Scene`
- **THEN** the scene view shows the FPS value and the project, scene and asset files are unchanged

#### Scenario: Reselect the project
- **WHEN** the user checks `Show FPS`, selects an asset and then reselects `Untitled.abss`
- **THEN** `Show FPS` remains checked
