## MODIFIED Requirements

### Requirement: Adding an asset creates a new entity

Choosing an asset SHALL add one new entity to the scene file. Its id SHALL be one more than the highest numeric entity
id of the scene. It SHALL hold:
- a name: `Model <id>` for a model, `Terrain <id>` for a terrain;
- a type: `OBJECT` for a model, `TERRAIN` for a terrain;
- a position;
- a render component naming the asset (`kind` `asset`, `asset.type` `MODEL` or `TERRAIN`, `asset.assetName` the folder,
  and `shaderKey` `defaultShader` for a model or `terrain` for a terrain).

When adding a model that holds more than one mesh, Add Asset SHALL offer each of its meshes besides placing the whole
model; choosing a mesh records that mesh's id as the render component's `meshId`, so the placed entity draws only that
mesh with its materials. A model with a single mesh SHALL be offered only as a whole. When the whole model is chosen,
`meshId` is absent. The headless editing API SHALL accept the same optional `meshId`.

Everything else in the file SHALL stay as it was. The addition SHALL be one undoable edit.

#### Scenario: Add a model to the fixture scene

- **WHEN** `tree` is chosen in `Main Scene`, whose highest entity id is `8`
- **THEN** the file gains entity `9` named `Model 9` with type `OBJECT` and a render component of asset `MODEL` `tree`
  with shader key `defaultShader`, no `meshId`, and entities `0` to `8` are unchanged

#### Scenario: Add a model with a chosen mesh

- **WHEN** a model asset holding meshes `body` and `glass` is chosen in `Main Scene`, whose highest entity id is `8`,
  and the user chooses mesh `glass` from the model's mesh choices
- **THEN** the file gains entity `9` named `Model 9` with type `OBJECT` and a render component of asset `MODEL`
  `<asset>` with shader key `defaultShader` and `meshId` `glass`, and entities `0` to `8` are unchanged

#### Scenario: A single-mesh model

- **WHEN** Add Asset lists a model that holds one mesh
- **THEN** it offers only the whole model and no mesh choices

#### Scenario: Add a terrain

- **WHEN** `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` is chosen in `Main Scene`
- **THEN** the new entity is named `Terrain 9`, has type `TERRAIN` and a render component of asset `TERRAIN` with shader
  key `terrain`

#### Scenario: Undo

- **WHEN** the user chooses Undo after adding an asset
- **THEN** the scene file is as it was before

#### Scenario: Empty scene

- **WHEN** an asset is added to a scene that has no entities
- **THEN** the new entity gets id `0`
