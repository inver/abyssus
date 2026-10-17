## MODIFIED Requirements

### Requirement: Placed models are displayed

The scene view SHALL display every model a scene places, using the model file of the asset the scene names, at the
entity's position, rotation and scale. A scene places a model through an entity whose render component refers to an
asset of type `MODEL` by the asset's folder name.

A model entity's render component MAY name a `meshId`. When it does, the view SHALL draw only that mesh of the model
(with its material) instead of the whole model. When it does not, the whole model is drawn as before.

#### Scenario: Models of a scene appear

- **WHEN** a user opens `Main Scene` of the `Untitled` project, which places models from several `model_...` assets
- **THEN** each placed model is drawn in the view together with the grid and environment

#### Scenario: Placement is honoured

- **WHEN** an entity has a position, a rotation and a scale
- **THEN** its model is drawn translated, rotated and scaled accordingly; missing transform fields mean origin, no
  rotation and unit scale

#### Scenario: Same asset used twice

- **WHEN** two entities refer to the same model asset
- **THEN** both are drawn, at their own transforms

#### Scenario: A concrete mesh of a shared model

- **WHEN** two entities refer to the same model asset, one naming mesh `body` and the other naming mesh `glass`
- **THEN** the first is drawn as only the `body` mesh with its material and the second as only the `glass` mesh with
  its material, each at its own transform

#### Scenario: Unknown meshId is skipped

- **WHEN** an entity names a `meshId` that its model asset does not contain
- **THEN** that entity is not drawn and all other models still are, with no user-facing error dialog
