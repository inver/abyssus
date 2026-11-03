# Spec Delta

## MODIFIED Requirements

### Requirement: Panel follows the asset selection

The panel SHALL show the Meta of the asset selected in the Abyssus view and SHALL update when the selection changes. For a selected entity or one of its components it SHALL show that entity's components or that component's fields instead (see "Entity and component properties"). For a selected scene it SHALL show the scene's Rendering settings, including its runtime Ray Tracing switch and saved raytracing limits.

#### Scenario: Select an asset
- **WHEN** the user selects an asset row in the Abyssus view
- **THEN** the panel lists the properties of that asset's `meta.json`

#### Scenario: Nothing selected
- **WHEN** nothing is selected
- **THEN** the panel shows `Nothing selected.` and a hint to select a skybox, model or terrain under Assets to see its `meta.json`

#### Scenario: Selection is not an asset
- **WHEN** the selected node is not an asset, an entity, a component or a scene (the project file or a setting)
- **THEN** the panel shows `Nothing to show: <name> is <what it is>.` and the same hint

#### Scenario: Select a scene
- **WHEN** the user selects Untitled's Main Scene
- **THEN** the panel shows the scene header, Ray Tracing switch and the four effective saved raytracing limits

## ADDED Requirements

### Requirement: Scene raytracing integer controls

The scene Rendering section SHALL expose Target samples per pixel, Maximum rays per frame, Maximum reflection bounces and Maximum refraction bounces as editable integers. Help SHALL distinguish accumulated quality from per-submission work and identify supported ranges. Invalid or conflicting edits SHALL explain the issue beside the field; refresh and Undo/Redo SHALL follow the saved scene.

#### Scenario: Quality and budget are distinct
- **WHEN** the user inspects Main Scene's Rendering settings
- **THEN** samples and rays have distinct labels and help, and reflection and refraction each have their own bounce control

#### Scenario: Rendering unavailable
- **WHEN** the GPU cannot raytrace the scene
- **THEN** the switch explains unavailability while the saved integer settings remain editable

### Requirement: Per-instance transmission and IOR editors

A selected model entity or its Render component SHALL offer material identifiers with Transmission in percent and IOR editors for PBR materials. The panel SHALL identify these as scene-instance overrides used by Ray Tracing, refresh after edits and Undo, and expose unresolved overrides without silently retargeting them. Non-PBR materials and ambiguous material identifiers SHALL have an explanation instead of an optical editor.

#### Scenario: Edit glass
- **WHEN** the user selects a model's opaque PBR material and sets Transmission to 100 percent and IOR to 1.5
- **THEN** its scene instance stores transmission 1 with default IOR omitted, and other material properties and assets remain unchanged

#### Scenario: Undo optical edit
- **WHEN** Transmission is changed and the user chooses Undo from Properties
- **THEN** the original scene text, field and rendered material are restored
