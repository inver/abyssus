## MODIFIED Requirements

### Requirement: Panel follows the asset selection

The panel SHALL show the Meta of the asset selected in the Abyssus view and SHALL update when the selection changes. For a selected entity or one of its components it SHALL show that entity's components or that component's fields instead (see "Entity and component properties"). For a selected project file it SHALL show the project's properties instead (see "Project properties").

#### Scenario: Select an asset
- **WHEN** the user selects an asset row in the Abyssus view
- **THEN** the panel lists the properties of that asset's `meta.json`

#### Scenario: Nothing selected
- **WHEN** nothing is selected
- **THEN** the panel shows `Nothing selected.` and a hint to select a skybox, model or terrain under Assets to see its `meta.json`

#### Scenario: Selection is not an asset
- **WHEN** the selected node is not an asset, an entity, a component or a project file (for example, a setting)
- **THEN** the panel shows `Nothing to show: <name> is <what it is>.` and the same hint

#### Scenario: Scene selection retains its properties
- **WHEN** the user selects a scene node in the Abyssus view
- **THEN** the panel keeps showing the scene's existing properties, including its ray tracing preferences

## ADDED Requirements

### Requirement: Project properties

Selecting a project's `.abss` node in the Abyssus view SHALL show the project's name and a Physics checkbox reflecting
`physicsEnabled`. Toggling the checkbox SHALL write the switch to the `.abss` file as one undoable edit. The panel SHALL
follow edits made to the `.abss` elsewhere, and SHALL show the switch read-only for an `.abss` file it does not support.

#### Scenario: Physics off by default
- **WHEN** the user selects `Untitled.abss` in the Abyssus view
- **THEN** the panel shows project `Untitled` with the Physics checkbox cleared

#### Scenario: Turn physics on and undo
- **WHEN** the user ticks Physics for a copy of `Untitled` and then uses Undo
- **THEN** `Untitled.abss` first gains `"physicsEnabled": true`, then is restored to its original text, and the
  checkbox follows both changes

#### Scenario: Physics fixture
- **WHEN** the user selects `Physics.abss`
- **THEN** the Physics checkbox is ticked

#### Scenario: Edit the project text
- **WHEN** `"physicsEnabled": true` is typed into the `.abss` in the text editor while the project is selected
- **THEN** the checkbox becomes ticked without reselecting

#### Scenario: Unsupported project
- **WHEN** the selected `.abss` has unsupported or missing native format markers and `physicsEnabled: true`
- **THEN** physics remains off, the panel explains the unsupported format and the checkbox cannot write the file
