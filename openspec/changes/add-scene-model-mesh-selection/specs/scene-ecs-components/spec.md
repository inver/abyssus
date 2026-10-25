## ADDED Requirements

### Requirement: A model render component may name one mesh

A native render component of asset type `MODEL` MAY carry an optional non-empty `meshId` beside `assetName`
(`renderable.asset.meshId`, or `meshId` in the flat shape), naming one mesh of the model asset. Loading SHALL parse it
without consulting the model asset; a `meshId` the model does not contain is handled when the model is built (see
`scene-model-rendering`) and does not stop other entities from loading. An absent `meshId` SHALL mean the whole model. A `meshId` on a `TERRAIN` render
component SHALL be ignored and preserved. Writers SHALL omit `meshId` when it is absent or cleared and SHALL keep its
text and the surrounding formatting otherwise.

#### Scenario: Mesh of a model asset

- **WHEN** an entity's render component has `asset.type` `MODEL`, an `assetName` and `meshId` `glass`
- **THEN** the loaded component refers to that asset together with mesh `glass`

#### Scenario: Flat shape

- **WHEN** a render component stores `type`, `assetName` and `meshId` directly on the component
- **THEN** the loaded component refers to that asset together with the mesh

#### Scenario: No meshId

- **WHEN** a model render component has no `meshId`
- **THEN** the whole model is loaded, as before this capability

#### Scenario: Round trip

- **WHEN** a scene whose render component holds `meshId` is loaded and written without changes to that entity
- **THEN** the `meshId` text and the formatting around it are unchanged
