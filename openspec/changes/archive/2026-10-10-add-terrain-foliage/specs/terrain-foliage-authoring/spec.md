# Spec Delta

## Purpose

Lets users cover a terrain asset with many copies of model assets, such as forests, rocks and grass, defined by layers of
reproducible scatter settings and baked into a foliage asset that scenes show on their terrain entities.

## ADDED Requirements

### Requirement: Create a foliage asset

New Foliage... SHALL be available on a recognised project's Assets node when the project has a readable TERRAIN asset.
The user SHALL choose the terrain, a unique folder name (default `foliage_` plus the terrain folder name) and a mask
resolution from 16 through 2048 (default 512). Create SHALL write a foliage asset with no layers, select it in the
properties panel and refresh the tree. It SHALL NOT change any scene, terrain or project file.

#### Scenario: Create for the fixture terrain
- **WHEN** the user creates foliage `foliage_meadow` for `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` in a copy of Untitled
- **THEN** `assets/foliage_meadow/meta.json` exists with `format` `abyssus`, `formatVersion` `1`, `type` `FOLIAGE`, a fresh `uuid`, `additional.terrain` naming that folder, `maskResolution` `512` and no layers, and `assets/foliage_meadow/foliage.data` holds no copies

#### Scenario: No terrain
- **WHEN** a project has no TERRAIN asset
- **THEN** New Foliage... is disabled

#### Scenario: Name taken
- **WHEN** the chosen folder name already exists under `assets`
- **THEN** Create is disabled with a reason and nothing is written

#### Scenario: Resolution out of range
- **WHEN** the user enters a mask resolution of `8` or `4096`
- **THEN** Create is disabled with a reason and nothing is written

### Requirement: Foliage layers

A foliage asset SHALL hold an ordered list of layers. Each layer SHALL have a unique integer id, a kind (OBJECT or
DETAIL), one or more model assets with positive weights, a density in copies per square unit at full mask, a scale range,
a normal alignment from 0 through 1, an optional height range, an optional maximum slope in degrees, a seed and, for
DETAIL, a draw distance.

#### Scenario: Add a layer
- **WHEN** the user adds a layer to an empty foliage asset and picks `tree`
- **THEN** the layer gets id `1`, kind OBJECT, `tree` with weight `1`, and the panel shows the default density, scale range, alignment, seed and no height or slope limits

#### Scenario: Weighted models
- **WHEN** a layer holds `tree` with weight `3` and `model_900f6f61-6384-434a-be81-56ce303fbb56` with weight `1`
- **THEN** about three quarters of its generated copies use `tree` and the rest the other model

#### Scenario: Only model assets
- **WHEN** the user picks models for a layer
- **THEN** only the project's MODEL assets are offered

#### Scenario: Invalid value
- **WHEN** the user enters a negative density, a scale minimum above its maximum, an alignment of `2`, a maximum slope above `90`, a minimum height above the maximum height or a weight of `0`
- **THEN** the value is rejected with a reason beside the field and nothing is previewed or written

### Requirement: Deterministic scattering

Copies SHALL be generated from the layer settings, the layer's density mask and the terrain's heights and size alone.
Equal inputs SHALL produce equal copies. Changing one layer SHALL NOT change the copies of another layer. Each copy
SHALL get a model by weight, a random yaw, a scale within the range and a terrain-local position.

#### Scenario: Same inputs, same copies
- **WHEN** the same foliage settings, masks and terrain are generated twice
- **THEN** both bakes are byte-for-byte equal

#### Scenario: Another seed
- **WHEN** the user changes a layer's seed
- **THEN** that layer's copies move and the other layers' copies stay the same

#### Scenario: Spacing within a layer
- **WHEN** a layer is generated at any density
- **THEN** no two of its copies are closer than one fifth of the average spacing that its density gives

### Requirement: Scatter rules

A copy SHALL be kept only where the terrain-local height lies in the layer's height range, the slope is not above the
maximum slope, and the layer's mask allows it. The mask value from 0 to 255 SHALL scale the layer's density linearly,
and a layer without a mask SHALL behave as a full mask.

#### Scenario: Slope limit
- **WHEN** a layer has a maximum slope of `20`
- **THEN** no copy of that layer stands where the terrain is steeper than 20 degrees

#### Scenario: Height range
- **WHEN** a layer has a minimum height of `5` and a maximum height of `40`
- **THEN** every copy of that layer stands where the terrain height is from 5 through 40

#### Scenario: Empty mask
- **WHEN** a layer's mask is 0 everywhere
- **THEN** that layer has no copies

#### Scenario: Half mask
- **WHEN** a layer's mask is 128 over an area
- **THEN** that area gets about half the copies a full mask gives

### Requirement: Copy limit

A foliage asset SHALL hold at most 1,000,000 copies in total. The panel SHALL show each layer's copy count and the total
for the previewed settings. Apply, Re-bake and a paint stroke that would exceed the limit SHALL be refused with a reason
and write nothing.

#### Scenario: Too dense
- **WHEN** a DETAIL layer with density `1` and a full mask is previewed on the 1600-unit terrain `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b`
- **THEN** the panel shows about 2,560,000 copies, explains the limit, and Apply is disabled

### Requirement: Preview and apply layer edits

Edits to foliage settings SHALL show in every open Scene view that shows the foliage, without writing files. Apply SHALL
write the settings and the matching bake together. Cancel, selecting another asset or closing the panel SHALL discard the
preview and restore the shown copies.

#### Scenario: Preview density
- **WHEN** the user raises a layer's density in the panel
- **THEN** the Scene view of `Main Scene` shows more copies of that layer while `meta.json` and `foliage.data` are unchanged

#### Scenario: Apply
- **WHEN** the user applies the edit
- **THEN** `meta.json` holds the new density, `foliage.data` holds the copies the preview showed, and both happen as one operation

#### Scenario: Discard
- **WHEN** the user selects another asset before applying
- **THEN** the Scene view shows the copies of the saved settings again and nothing was written

### Requirement: Foliage metadata edits keep the file

Writing foliage settings SHALL change only the members that were edited. Unrelated keys, key order, number text,
unknown members and omitted defaults SHALL stay as they were. A foliage `meta.json` that is not a supported native
document SHALL be shown with a reason and SHALL NOT be edited, generated or baked.

#### Scenario: Unknown member kept
- **WHEN** a layer holds an unknown member `"note": "north slope"` and the user changes the layer's density
- **THEN** `"note": "north slope"` is still in the file at the same place

#### Scenario: Unsupported document
- **WHEN** a foliage `meta.json` has `formatVersion` `2`
- **THEN** the panel explains that the document is unsupported, offers no edits, and the file is unchanged

### Requirement: Stale bakes

The bake SHALL record a fingerprint of the inputs it was made from. When the settings, a mask or the terrain heights or
size no longer match it, the view SHALL show freshly generated copies, and the panel SHALL say the bake is out of date and
offer Re-bake. Re-bake SHALL rewrite only `foliage.data`.

#### Scenario: Terrain regenerated
- **WHEN** the terrain of a foliage asset is regenerated with another seed
- **THEN** the panel says the bake is out of date, the view shows copies generated against the new heights, and Re-bake writes a `foliage.data` that matches them

#### Scenario: Missing bake
- **WHEN** `foliage.data` is deleted
- **THEN** the view still shows the foliage, generated from the settings, and Re-bake writes the file again

### Requirement: Add foliage to a terrain entity

Add Foliage... SHALL be offered on a terrain entity, in the tree and in the Scene view toolbar while that entity is
selected. It SHALL list the foliage assets bound to the entity's terrain. Choosing one SHALL set the entity's
`FoliageComponent` to `{ "assetName": "<folder>" }` and change nothing else in the scene.

#### Scenario: Add to the fixture terrain
- **WHEN** the user chooses `foliage_meadow` for entity `1` (`Terrain`) of `Main Scene` in a copy of Untitled
- **THEN** entity `1` gains `"FoliageComponent": { "assetName": "foliage_meadow" }`, every other entity and member is unchanged, and the view shows the foliage on that terrain

#### Scenario: Replace
- **WHEN** the entity already has foliage and the user chooses another foliage asset
- **THEN** only `FoliageComponent.assetName` changes

#### Scenario: Nothing to add
- **WHEN** the entity's terrain has no foliage asset
- **THEN** Add Foliage... is disabled and explains that a foliage asset is created from the Assets node

#### Scenario: Not a terrain
- **WHEN** the selected entity is a model, a light or a camera
- **THEN** Add Foliage... is not offered

### Requirement: Undoable foliage operations

Create, Apply, Re-bake and Add Foliage SHALL each be one undoable operation. Undo SHALL restore the previous files, or
remove the new asset. Redo SHALL restore the same bytes. Undo or Redo SHALL refuse with a reason when the files changed
since, and Undo of Create SHALL refuse while a scene names the new asset.

#### Scenario: Undo Apply
- **WHEN** the user applies a layer edit and invokes Undo from the foliage panel
- **THEN** `meta.json` and `foliage.data` are as before and the view shows the previous copies

#### Scenario: Undo Create while used
- **WHEN** a scene's `FoliageComponent` names a foliage asset and the user undoes that asset's Create
- **THEN** Undo is refused with a reason and the folder stays
