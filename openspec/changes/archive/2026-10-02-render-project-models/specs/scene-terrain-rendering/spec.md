# Spec Delta

## Purpose

Lets users see the terrain of a scene in the read-only scene view, built from the terrain assets
stored in the project, so the ground the models stand on is visible.

## ADDED Requirements

### Requirement: Terrain entities are displayed

The scene view SHALL display every entity whose render component refers to an asset of type
`TERRAIN`, using the terrain asset folder of the same name in the project's `assets` folder, at the
entity's position, rotation and scale.

#### Scenario: Terrain of a scene appears

- **WHEN** a user opens `Main Scene`, which places the terrain asset `terrain_2cf70bf7-...`
- **THEN** a terrain surface of the size and heights stored in that asset is drawn with the models

#### Scenario: Terrain without textures

- **WHEN** the terrain asset has no splat textures (all splat fields are null)
- **THEN** the terrain is still drawn, with a plain material

#### Scenario: Terrain with splat textures

- **WHEN** the terrain asset names a splat map and layer textures that exist in the project
- **THEN** the terrain is drawn blended from those textures

### Requirement: Terrain failures are isolated

A terrain that cannot be displayed (missing folder, unreadable `meta.json` or terrain data,
unsupported data version) SHALL be skipped and logged without hiding the rest of the scene.

#### Scenario: Corrupt terrain data

- **WHEN** `terrain.data` is truncated
- **THEN** no terrain is drawn for that entity, the models and environment still are, and the
  problem is written to the IDE log
