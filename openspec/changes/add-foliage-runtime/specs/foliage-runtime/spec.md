# Spec Delta

## Purpose

Lets games built on the Abyssus runtime load and draw the foliage authored in the editor, exactly as baked, without the
IDE or the generator.

## ADDED Requirements

### Requirement: Foliage of a loaded scene is listed

The runtime SHALL list a loaded scene's foliage as one entry per entity that has both a terrain render component and a
`FoliageComponent`. Each entry SHALL give the entity, its foliage asset folder and its terrain asset folder. Listing
SHALL need no GL and SHALL NOT read asset files.

#### Scenario: The migrated field
- **WHEN** Control Line's `Field.scene` is loaded
- **THEN** the list has two entries: entity `0` with `foliage_airfield_site` on `terrain_airfield_site`, and entity `300` with `foliage_airfield_outer` on `terrain_airfield_outer`

#### Scenario: Component on a model
- **WHEN** a model entity carries a `FoliageComponent`
- **THEN** that entity is not listed and one warning names it

### Requirement: Games draw foliage as baked

A game SHALL be able to load a listed foliage asset off the GL thread and draw it with the camera and lighting it
already uses. The copies SHALL be the ones in `foliage.data`, at the same places, models, yaw, scale and tilt as the
editor's scene view shows. Each copy SHALL stand on the terrain surface, and DETAIL layers SHALL end at their draw
distance.

#### Scenario: Same copies as the editor
- **WHEN** the same foliage asset and terrain entity are drawn by the game and by the editor's scene view
- **THEN** every copy has the same world transform in both, to within 0.001 units

#### Scenario: Loaded without the IDE
- **WHEN** a JVM program with no IntelliJ classes loads a project's foliage through the runtime and the core asset loaders
- **THEN** the foliage loads and its copy count equals that of its `foliage.data`

### Requirement: The bake is used as written

The runtime SHALL draw `foliage.data` as it is and SHALL NOT generate copies. A bake whose fingerprint no longer matches
its inputs SHALL still be drawn, with one warning naming the foliage asset. A missing, truncated or unsupported bake SHALL
draw no copies for that foliage, with one warning.

#### Scenario: Out-of-date bake
- **WHEN** a foliage asset's settings were changed after its last bake
- **THEN** the game draws the copies of the bake and logs one warning that the bake is out of date

#### Scenario: Missing bake
- **WHEN** `foliage.data` is deleted
- **THEN** the game draws the scene without that foliage and logs one warning

### Requirement: Foliage failures are isolated in games

Foliage that cannot be shown SHALL be skipped with one warning without stopping the scene. This covers a missing folder,
an unsupported `meta.json`, a terrain other than the entity's, and a missing layer model. A missing model SHALL drop only
its own copies.

#### Scenario: Unsupported foliage document
- **WHEN** a foliage `meta.json` has `formatVersion` `2`
- **THEN** that foliage is skipped with one warning, and the terrain, the models and other foliage are drawn

#### Scenario: Missing model
- **WHEN** one model of a layer has no asset folder
- **THEN** the other copies are drawn and one warning names the missing model
