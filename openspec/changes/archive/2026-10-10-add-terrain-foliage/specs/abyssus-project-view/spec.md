# Spec Delta

## MODIFIED Requirements

### Requirement: Presentation of asset nodes

Asset nodes SHALL be displayed with the name derived from their file name. Each kind of node (project, scene, ambient light, fog, skybox, ecs, folder, entity, component, and model, terrain, foliage and skybox assets) SHALL have its own icon, and secondary text (values, counts) SHALL use the gray text style. The view SHALL keep a stable identity per asset file so that
expanding, refreshing, and reopening the view do not lose or duplicate nodes.

#### Scenario: Node label uses the file name

- **WHEN** a `.scene` file named `forest.scene` and a `.abss` file named `game.abss` are listed
- **THEN** their nodes are labeled `forest.scene` and `game.abss`

#### Scenario: Reopening the view preserves the node set

- **WHEN** the user switches away from the Abyssus view and back
- **THEN** the same asset nodes are present, each appearing exactly once

#### Scenario: Added and removed asset files are reflected on refresh

- **WHEN** a `.scene` or `.abss` file is added to or deleted from the project and the view
  refreshes
- **THEN** the view reflects the new set of asset files

#### Scenario: Foliage asset has its own icon

- **WHEN** a project's `assets` list holds a FOLIAGE asset next to a TERRAIN and a MODEL asset
- **THEN** the foliage entry uses the foliage icon, which differs from the terrain and model icons
