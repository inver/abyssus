# Spec Delta

## MODIFIED Requirements

### Requirement: Panel follows the asset selection

The panel SHALL show the Meta of the asset selected in the Abyssus view and SHALL update when the selection changes. For a selected entity or one of its components it SHALL show that entity's components or that component's fields instead (see "Entity and component properties"). A selected water surface SHALL show editable water properties (see "Water properties").

#### Scenario: Select an asset
- **WHEN** the user selects an asset row in the Abyssus view
- **THEN** the panel lists the properties of that asset's `meta.json`

#### Scenario: Nothing selected
- **WHEN** nothing is selected
- **THEN** the panel shows `Nothing selected.` and a hint to select a skybox, model or terrain under Assets to see its `meta.json`

#### Scenario: Selection is not an asset
- **WHEN** the selected node is not an asset, an entity, a component or a water surface (a scene, the project file or a setting)
- **THEN** the panel shows `Nothing to show: <name> is <what it is>.` and the same hint

## ADDED Requirements

### Requirement: Water properties

Selecting a water surface SHALL show its name and Abyssus-only status, position including water level, width, length, tint, clarity, wave amplitude, wavelength and speed, and foam amount and width. Confirmed valid edits SHALL refresh the scene; invalid edits SHALL restore the previous value with a reason and leave the file unchanged.

#### Scenario: Change water level
- **WHEN** a user sets a selected Lake's water level to 3
- **THEN** only its `position.y` changes to 3 and the shoreline and rendering refresh

#### Scenario: Invalid extent
- **WHEN** the user sets width to zero or enters a non-finite number
- **THEN** the edit is rejected, the previous width remains and a reason appears beside the field

#### Scenario: Text edits refresh properties
- **WHEN** a selected surface's clarity changes in the scene text
- **THEN** its property editor shows the new clarity without reselecting

#### Scenario: Removed surface
- **WHEN** the selected surface is deleted elsewhere
- **THEN** the panel shows that it no longer exists rather than editing a different surface

