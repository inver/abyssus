## ADDED Requirements

### Requirement: A model render component may name one mesh

A native render component of asset type `MODEL` MAY carry an optional non-empty `meshId` beside `assetName`
(`renderable.asset.meshId`), naming one mesh of the model asset. Loading SHALL resolve it through the model asset, and a
`meshId` the model does not contain SHALL leave that entity without a drawable renderable, log the problem once and
load the other entities normally. An absent `meshId` SHALL mean the whole model. A `meshId` on a `TERRAIN` render
component SHALL be ignored and preserved. Writers SHALL omit `meshId` when it is absent or cleared and SHALL keep its
text and the surrounding formatting otherwise.

#### Scenario: Mesh of a model asset

- **WHEN** an entity's render component has `asset.type` `MODEL`, an `assetName` and `meshId` `glass`
- **THEN** the loaded component refers to that asset together with mesh `glass`

#### Scenario: Unknown mesh

- **WHEN** the named model asset holds no mesh `missing` and an entity names `meshId` `missing`
- **THEN** that entity has no drawable renderable, the problem is logged once, and the other entities load normally

#### Scenario: No meshId

- **WHEN** a model render component has no `meshId`
- **THEN** the whole model is loaded, as before this capability

#### Scenario: Round trip

- **WHEN** a scene whose render component holds `meshId` is loaded and written without changes to that entity
- **THEN** the `meshId` text and the formatting around it are unchanged
