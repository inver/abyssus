# Spec Delta

## Purpose

Lets users put one of their project's models or terrains into a scene in one step, from the Abyssus tree or the Scene
view, without writing entity JSON or adding a render component by hand.

## ADDED Requirements

### Requirement: Add Asset lists the project's models and terrains

The plugin SHALL offer Add Asset in the Scene view toolbar and in the right-click menu of a scene row of the Abyssus
tree, next to Add Light. It SHALL list the MODEL assets of the scene's project under Models and its TERRAIN assets under
Terrains, each by folder name in name order. Other asset types SHALL NOT be listed. Add Asset SHALL be unavailable when
the scene file cannot be read as a native scene, when the scene belongs to no project, or when the project has no model
or terrain.

#### Scenario: The fixture's assets

- **WHEN** the user opens Add Asset for `Main Scene` of the Untitled project
- **THEN** Models lists `model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`, `model_828d51e4-8427-4769-bcb6-13f8f21f23e9`, `model_900f6f61-6384-434a-be81-56ce303fbb56`, `model_fc33e1f1-015b-4524-9b10-aa417acd273c` and `tree`, Terrains lists `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b`, and no skybox is listed

#### Scenario: Tree and toolbar

- **WHEN** the user right-clicks the `Main Scene` row in the Abyssus tree, or opens the Scene view of `Main Scene`
- **THEN** both offer Add Asset with the same choices

#### Scenario: Unreadable scene

- **WHEN** the scene file holds text that is not valid JSON
- **THEN** Add Asset is disabled and nothing is written

#### Scenario: A scene outside a project

- **WHEN** a scene file has no `.abss` project above it
- **THEN** Add Asset is disabled

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

- **WHEN** `tree` is chosen in `Main Scene`, whose highest entity id is `8`
- **THEN** the file gains entity `9` named `Model 9` with type `OBJECT` and a render component of asset `MODEL` `tree` with shader key `defaultShader`, and entities `0` to `8` are unchanged

#### Scenario: Add a terrain

- **WHEN** `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` is chosen in `Main Scene`
- **THEN** the new entity is named `Terrain 9`, has type `TERRAIN` and a render component of asset `TERRAIN` with shader key `terrain`

#### Scenario: Undo

- **WHEN** the user chooses Undo after adding an asset
- **THEN** the scene file is as it was before

#### Scenario: Empty scene

- **WHEN** an asset is added to a scene that has no entities
- **THEN** the new entity gets id `0`

### Requirement: A new asset is placed where the user is looking

An asset added from the Scene view SHALL be placed at the point the view orbits around; one added from the tree SHALL be
placed at the origin of the scene. A model's position SHALL be that point. A terrain SHALL be centred on it: its position
SHALL be the point minus half its `size` along X and Z. A new entity SHALL be selected afterwards.

#### Scenario: A model from the Scene view

- **WHEN** `tree` is added while the view orbits the point `(10, 0, -4)`
- **THEN** the new entity's position is `(10, 0, -4)`

#### Scenario: A terrain from the tree

- **WHEN** the 1600-unit terrain `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` is added from the tree
- **THEN** the new entity's position is `(-800, 0, -800)`, so the terrain's centre is at the origin

#### Scenario: Selected after adding

- **WHEN** an asset is added from the tree or the Scene view
- **THEN** the new entity's row is selected in the Abyssus tree
