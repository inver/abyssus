# scene-model-rendering Specification

## Purpose

Lets users see the 3D models a scene places, loaded from the project's own assets, inside the
read-only scene view, so the view shows what the scene looks like and not only its environment.

## Requirements

### Requirement: Placed models are displayed

The scene view SHALL display every model a scene places, using the model file of the asset the
scene names, at the entity's position, rotation and scale. A scene places a model through an
entity whose render component refers to an asset of type `MODEL` by the asset's folder name.

#### Scenario: Models of a scene appear

- **WHEN** a user opens `Main Scene` of the `Untitled` project, which places models from several
  `model_...` assets
- **THEN** each placed model is drawn in the view together with the grid and environment

#### Scenario: Placement is honoured

- **WHEN** an entity has a position, a rotation and a scale
- **THEN** its model is drawn translated, rotated and scaled accordingly; missing transform
  fields mean origin, no rotation and unit scale

#### Scenario: Same asset used twice

- **WHEN** two entities refer to the same model asset
- **THEN** both are drawn, at their own transforms

### Requirement: Models are loaded from the project assets

The view SHALL resolve a model through the `assets` folder beside the `.abss` file of the scene's
project, using the model file named by the asset folder's `meta.json`, and SHALL load the model
file together with the textures it references. Loading SHALL NOT freeze the editor UI.

#### Scenario: Textured model

- **WHEN** the model asset folder holds a glTF file and its textures
- **THEN** the model is drawn with its textures

#### Scenario: UI stays responsive while loading

- **WHEN** a scene with large models is opened
- **THEN** the view and the rest of the IDE remain interactive and models appear once loaded

### Requirement: Failures are isolated

A model that cannot be displayed (asset folder missing, unreadable `meta.json`, model file missing
or corrupt, unsupported format) SHALL be skipped without hiding the other models or the
environment, and SHALL NOT raise a user-facing error dialog. This requirement draws `MODEL`
renderables only; other renderable types are covered by their own capabilities, and unknown
types are ignored.

#### Scenario: Missing asset

- **WHEN** an entity names an asset folder that does not exist
- **THEN** that entity is not drawn and all other models still are

#### Scenario: Corrupt model file

- **WHEN** a model file cannot be parsed
- **THEN** the other models are still drawn and the problem is written to the IDE log

#### Scenario: Unknown renderable type

- **WHEN** a scene contains an entity whose renderable type is not known to the view
- **THEN** that entity is not drawn and nothing fails

### Requirement: View follows scene changes

The displayed models SHALL reflect the scene file: after the scene is edited or reloaded, models
that were added, removed or moved appear, disappear or move, without reopening the view.

#### Scenario: Entity added

- **WHEN** the scene file changes to include a new model entity
- **THEN** the view shows the new model after the change is picked up

#### Scenario: Entity removed

- **WHEN** an entity is removed from the scene file
- **THEN** its model is no longer drawn

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
