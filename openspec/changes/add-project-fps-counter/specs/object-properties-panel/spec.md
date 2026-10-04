# Spec Delta

## MODIFIED Requirements

### Requirement: Panel follows the asset selection

The panel SHALL show the Meta of the asset selected in the Abyssus view and SHALL update when the selection changes. For a selected entity or one of its components it SHALL show that entity's components or that component's fields instead (see "Entity and component properties"). For a selected `.abss` project row it SHALL show project view settings (see "Project view settings"). Scene rows SHALL retain their available scene view controls.

#### Scenario: Select an asset
- **WHEN** the user selects an asset row in the Abyssus view
- **THEN** the panel lists the properties of that asset's `meta.json`

#### Scenario: Nothing selected
- **WHEN** nothing is selected
- **THEN** the panel shows `Nothing selected.` and a hint to select a skybox, model or terrain under Assets to see its `meta.json`

#### Scenario: Selection is not an asset
- **WHEN** the selected node is a setting without supported editors
- **THEN** the panel shows `Nothing to show: <name> is <what it is>.` and the same hint

#### Scenario: Select the project
- **WHEN** the user selects `Untitled.abss` in the Abyssus view
- **THEN** the panel shows project view settings with `Show FPS` rather than the empty-state message

#### Scenario: Return to a scene
- **WHEN** the user selects `Main Scene` after changing `Show FPS`
- **THEN** the panel shows the available scene view controls and the project FPS preference remains in effect

## ADDED Requirements

### Requirement: Project view settings

The project properties view SHALL show the selected project's name and a `Show FPS` checkbox reflecting the saved preference defined by `scene-fps-display`. The checkbox SHALL be usable with no scene open. The view SHALL explain that these are IDE view settings, and changing the checkbox SHALL leave `.abss`, `.scene` and asset files unchanged.

#### Scenario: New project preference
- **WHEN** `Untitled.abss` is selected in an IDE project with no saved FPS preference
- **THEN** `Show FPS` is unchecked

#### Scenario: Toggle before opening a scene
- **WHEN** the user checks `Show FPS` with no scene view open and then opens `Main Scene`
- **THEN** the scene view shows the FPS overlay and the project, scene and asset files are unchanged

#### Scenario: Reselect the project
- **WHEN** the user checks `Show FPS`, selects an asset and then reselects `Untitled.abss`
- **THEN** `Show FPS` remains checked
