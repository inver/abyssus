# Spec Delta

## Purpose

Lets games load and draw terrain foliage authored in Abyssus from committed bake data without the IDE or a runtime
copy generator, with predictable failures and independent terrain placements.

## ADDED Requirements

### Requirement: Foliage of a loaded scene is listed

A loaded scene SHALL list one foliage entry per entity with a terrain render asset and a nonempty foliage asset name.
Each entry SHALL identify the entity, foliage folder and terrain folder. Listing SHALL require neither graphics nor
asset-file access. A foliage component on a non-terrain entity SHALL be omitted with one warning.

#### Scenario: The migrated field
- **WHEN** Control Line's migrated Field scene is loaded
- **THEN** entity `0` lists `foliage_airfield_site` on `terrain_airfield_site` and entity `300` lists `foliage_airfield_outer` on `terrain_airfield_outer`

#### Scenario: Component on a model
- **WHEN** a model entity carries a foliage component
- **THEN** it is not listed and one warning names the entity

### Requirement: Games draw foliage as baked

Games SHALL load foliage without the IDE and draw committed copies using their camera and lighting. A matching bake
SHALL give the same placement as the committed editor view. Copies SHALL stand on current terrain heights using
current layer alignment, and DETAIL copies SHALL end at their draw distance. Two terrain entities using the same
asset SHALL retain independent transforms and visibility.

#### Scenario: Same copies as the editor
- **WHEN** a matching foliage bake is drawn by a game and the committed editor view with the same terrain and layer metadata
- **THEN** their copy world transforms agree within 0.001 units

#### Scenario: Loaded without the IDE
- **WHEN** a program without IDE classes reads the Foliage fixture
- **THEN** its readable baked copy count equals the committed bake count without creating graphics resources

#### Scenario: Shared asset on two terrains
- **WHEN** two differently transformed entities use the same foliage asset
- **THEN** both show correctly transformed copies, independently of draw order and culling

### Requirement: The bake is used as written

Runtime foliage SHALL never generate copies or write assets. A readable stale or unverifiable bake SHALL retain its
saved x/z, yaw, scale and model indices under current terrain/layer metadata, with one warning per loaded asset
revision. It SHALL NOT claim to recover historical model identities or the editor's regenerated distribution.
Missing, corrupt or unsupported bakes SHALL show no copies with one warning.

#### Scenario: Out-of-date bake
- **WHEN** density changes after the last bake
- **THEN** the game retains the saved copies, warns once, and changes no file

#### Scenario: Changed model mapping
- **WHEN** the current model list differs from the list used to bake
- **THEN** saved indices map to current models, unresolvable indices or removed layers are skipped, and the stale warning explains that rebaking is required

#### Scenario: Unreadable density mask
- **WHEN** a bake and terrain are readable but a mask prevents fingerprint verification
- **THEN** the game retains the bake and reports unverifiable inputs once

#### Scenario: Missing bake
- **WHEN** the bake is deleted
- **THEN** other scene content loads and draws, and one warning names the missing foliage bake

### Requirement: Foliage failures are isolated in games

Missing foliage, unsupported metadata, wrong or unreadable terrain and unreadable model dependencies SHALL not stop
other scene content or keep the game's loading state pending. A missing model SHALL drop only its copies. Diagnostics
SHALL be bounded to one warning per distinct problem in each loaded asset revision, rather than repeated per frame.

#### Scenario: Unsupported foliage document
- **WHEN** a foliage metadata document has `formatVersion` 2
- **THEN** that foliage is skipped once while the remaining scene reaches a playable loaded state

#### Scenario: Missing model
- **WHEN** one model in a layer is missing
- **THEN** other models' copies draw and one warning names the missing model

### Requirement: Foliage participates in game sun shadows

OBJECT foliage SHALL cast and receive the game's sun shadows; DETAIL foliage SHALL receive but never cast them.
Casters SHALL be selected independently of viewer visibility, so an off-screen OBJECT copy can shadow visible terrain.
Closing or replacing the scene SHALL release its foliage resources without invalidating another placement.

#### Scenario: Off-screen tree shadow
- **WHEN** a tree lies outside the viewer frustum but its shadow reaches visible ground
- **THEN** the shadow is present while grass adds no depth caster

#### Scenario: Scene replacement
- **WHEN** a game repeatedly replaces a scene with two placements of one foliage asset
- **THEN** no old placement draws or retains its resources, and the next scene's placements remain valid
