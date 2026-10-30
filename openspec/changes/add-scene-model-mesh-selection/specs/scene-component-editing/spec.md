## ADDED Requirements

### Requirement: A model render component offers a mesh chooser

The Properties panel SHALL show, for a render component of asset type `MODEL`, an optional mesh field listing the
meshes of the named model asset and a whole-model entry. The meshes SHALL be read without blocking the UI thread and
without GL. Choosing a mesh SHALL write its id as `meshId`; choosing the whole-model entry SHALL remove `meshId`. Each
change SHALL be one undoable edit and SHALL reach the open scene view on its next frame. When the stored `meshId` is not
among the model's meshes, the field SHALL still show the stored id. The field SHALL be unavailable for a render
component of another asset type or whose model cannot be read.

#### Scenario: Choose a mesh

- **WHEN** a model entity's render component names an asset holding meshes `body` and `glass` and the user chooses
  `glass`
- **THEN** the file stores `meshId` `glass` for that entity as one undoable edit and the view draws only that mesh

#### Scenario: Back to the whole model

- **WHEN** the user chooses the whole-model entry on an entity that has `meshId` `glass`
- **THEN** `meshId` is removed from the file and the whole model is drawn

#### Scenario: Stored id the model lacks

- **WHEN** an entity stores `meshId` `old` and the model has no such mesh
- **THEN** the field shows `old` in a warning state saying the mesh is not in the model, and the other choices remain selectable

#### Scenario: Undo

- **WHEN** the user undoes a mesh choice
- **THEN** the file and the view are as before
