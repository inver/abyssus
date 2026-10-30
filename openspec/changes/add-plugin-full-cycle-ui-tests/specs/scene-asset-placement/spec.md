# Spec Delta

## MODIFIED Requirements

### Requirement: Adding an asset creates a new entity

Choosing an asset SHALL add one new entity to the scene file. Its id SHALL be one more than the highest numeric entity
id of the scene. It SHALL hold:
- a name: `Model <id>` for a model, `Terrain <id>` for a terrain;
- a type: `OBJECT` for a model, `TERRAIN` for a terrain;
- a position;
- a render component naming the asset (`kind` `asset`, `asset.type` `MODEL` or `TERRAIN`, `asset.assetName` the folder,
  and `shaderKey` `defaultShader` for a model or `terrain` for a terrain).

Everything else in the file SHALL stay as it was. The addition SHALL be one undoable edit.

#### Scenario: Add a model to the fixture scene

- **WHEN** `tree` is chosen in `Main Scene`, whose highest entity id is `10`
- **THEN** the file gains entity `11` named `Model 11` with type `OBJECT` and a render component of asset `MODEL` `tree` with shader key `defaultShader`, and entities `0` to `10` are unchanged

#### Scenario: Add a terrain

- **WHEN** `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` is chosen in `Main Scene`
- **THEN** the new entity is named `Terrain 11`, has type `TERRAIN` and a render component of asset `TERRAIN` with shader key `terrain`

#### Scenario: Undo

- **WHEN** the user chooses Undo after adding an asset
- **THEN** the scene file is as it was before

#### Scenario: Empty scene

- **WHEN** an asset is added to a scene that has no entities
- **THEN** the new entity gets id `0`
