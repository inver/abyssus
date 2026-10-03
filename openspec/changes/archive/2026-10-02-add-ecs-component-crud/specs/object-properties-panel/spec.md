# Spec Delta

## MODIFIED Requirements

### Requirement: Panel follows the asset selection

The panel SHALL show the Meta of the asset selected in the Abyssus view and SHALL update when the selection changes. For a selected entity or one of its components it SHALL show that entity's components or that component's fields instead (see "Entity and component properties").

#### Scenario: Select an asset
- **WHEN** the user selects an asset row in the Abyssus view
- **THEN** the panel lists the properties of that asset's `meta.json`

#### Scenario: Nothing selected
- **WHEN** nothing is selected
- **THEN** the panel shows `Nothing selected.` and a hint to select a skybox, model or terrain under Assets to see its `meta.json`

#### Scenario: Selection is not an asset
- **WHEN** the selected node is not an asset, an entity or a component (a scene, the project file or a setting)
- **THEN** the panel shows `Nothing to show: <name> is <what it is>.` and the same hint

### Requirement: Read-only first stage

The panel SHALL NOT modify any file while showing an asset; asset values are displayed only. Only the fields of an entity's components are editable.

#### Scenario: Values are not editable
- **WHEN** the user double-clicks a value cell of an asset's Meta
- **THEN** no editor opens and no file changes

## ADDED Requirements

### Requirement: Entity and component properties

When an entity of a scene is selected the panel SHALL show a header with its name and id and one section per component it has; when a component is selected it SHALL show only that component's section. A modeled component SHALL list its fields with editors suited to the value (number, text, choice, color, entity reference, asset reference); an unmodeled component SHALL be shown as read-only JSON text with a note saying the plugin does not edit it.

#### Scenario: Entity selected

- **WHEN** entity `0` (`Model 0`) of `Main Scene` is selected
- **THEN** the header shows `Model 0` and `0`, and sections show its name, type, position and render components with their values

#### Scenario: Component selected

- **WHEN** the `Light` row of an entity is selected
- **THEN** the panel shows the light's `color` and `intensity` only

#### Scenario: Unmodeled component

- **WHEN** a `PickableComponent` is selected
- **THEN** its JSON is shown without editors and with a note that it is not edited by the plugin

### Requirement: Edit components in the panel

Changing a field in the panel SHALL update that component in the scene file as the `scene-component-editing` capability defines; a rejected value SHALL leave the previous value shown with the reason beside the field. The panel SHALL offer an "Add component" choice listing the modeled kinds the entity lacks and a "Remove" action on each modeled component's section.

#### Scenario: Change a value

- **WHEN** the user sets `intensity` of a selected light to `2` and confirms
- **THEN** the scene file holds `2` for it and the panel shows `2`

#### Scenario: Invalid value

- **WHEN** the user enters `abc` for a number field
- **THEN** the field returns to its previous value, a message appears beside it and the file is unchanged

#### Scenario: Add from the panel

- **WHEN** the user chooses Add component > Light on an entity without one
- **THEN** a light section with the default values appears and the file holds the new component

#### Scenario: Remove from the panel

- **WHEN** the user chooses Remove on the Light section
- **THEN** the section disappears and the component is gone from the file

### Requirement: Entity panel follows the file

The entity and component views SHALL refresh when the scene file changes in an editor, on disk or by an edit made elsewhere in the plugin, and SHALL show a message when the selected entity or component no longer exists.

#### Scenario: Edit the scene text

- **WHEN** the user changes a light's `intensity` in the open `.scene` text while its entity is selected
- **THEN** the panel shows the new value without reselecting

#### Scenario: Component removed elsewhere

- **WHEN** the selected component is deleted in the text editor
- **THEN** the panel shows a message that it no longer exists
