# scene-ecs-components Specification

## Purpose

Lets the plugin turn the `ecs` block of a scene file into typed entities and components, and write
it back unchanged in meaning, so later features work with scene content rather than raw JSON.

## Requirements

### Requirement: Scene ecs block loads into entities

The plugin SHALL load the `ecs` block of a supported native Abyssus scene file into an engine whose entities carry the
components named in the file, each addressable by the entity id it has in the file. References
between entities (look-at target, parent, point-to-point endpoints) SHALL resolve to the entities
with those ids. Component fields that are
absent in the file SHALL take their defaults: position at the origin, no rotation, unit scale,
no look-at target and no parent (`-1`).

#### Scenario: Entities of Main Scene

- **WHEN** `Main Scene` of the `Untitled` test project is loaded
- **THEN** the engine contains the entities present in the native fixture with their name, type, position and render
  components, and entity `0` is named `Model 0` with type `OBJECT`

#### Scenario: Missing transform fields

- **WHEN** an entity has an empty `PositionComponent` (`{}`)
- **THEN** its position is the origin, its rotation the identity and its scale `1,1,1`

#### Scenario: Component unknown to the plugin

- **WHEN** an entity carries a native component the plugin does not define (`PickableComponent` and
  `DependenciesComponent` in the native `Main Scene`)
- **THEN** loading still succeeds, the other components of that entity are loaded, the unknown
  component is kept unchanged with the entity, and the scene file on disk is not modified

#### Scenario: Editor-only renderable

- **WHEN** a native render component names an unknown kind such as `debug-marker`
- **THEN** the entity loads without a renderable, and the rest of the scene loads normally

#### Scenario: Reference to a missing entity

- **WHEN** a look-at, parent or point-to-point reference names an id that is not in the file
- **THEN** it is treated as no target (`-1`), the problem is logged once, and loading continues

#### Scenario: Light component

- **WHEN** a light entity's `LightComponent` holds `color` and `intensity` either directly or in a
  nested `light` object
- **THEN** the loaded light has that color and intensity in both cases

### Requirement: Render components resolve assets through the project

A native render component SHALL use `renderable.kind` equal to `asset`, omit `renderable.class`, and name its asset by type (`MODEL` or `TERRAIN`) and asset folder name, and
loading SHALL resolve it through the project's assets rather than embedding the asset data.

#### Scenario: Model asset reference

- **WHEN** an entity's render component has `asset.type` `MODEL` and an `assetName`
- **THEN** the loaded component refers to that asset type and name together with its `shaderKey`

#### Scenario: Asset cannot be resolved

- **WHEN** the named asset has no folder in the project's `assets`
- **THEN** the entity is loaded without a renderable, the problem is logged once, and the remaining
  entities load normally

### Requirement: Engine writes native ECS data

An engine SHALL write a native `ecs` block with stable short component identifiers and native renderable kinds. Unknown native components, renderable kinds, optional entity archetype ids and block archetypes/metadata SHALL round-trip unchanged. It SHALL NOT emit serialized Java class identifiers or `componentIdentifiers`. Derived state SHALL NOT be written.

#### Scenario: Native round trip
- **WHEN** a native scene is loaded and written back
- **THEN** reloading gives the same entities, components and field values

#### Scenario: Unknown data survives
- **WHEN** native `Main Scene` is loaded and written back
- **THEN** its unknown components, unknown native renderable kinds, archetypes and metadata survive unchanged

#### Scenario: Transient state is not written
- **WHEN** an engine is written
- **THEN** runtime transforms and light instances do not appear in the output

### Requirement: Foliage component is built in

A native foliage component SHALL load as a built-in component with an optional asset name. Loading and writing it
SHALL preserve unknown members, key order, numeric spelling and omitted defaults. Unresolved asset names SHALL be
retained and reported once during reference validation without rewriting the document. Malformed modeled values
SHALL remain raw with a warning rather than discarding extension data.

#### Scenario: Round trip
- **WHEN** entity `1` holds `"FoliageComponent": {"assetName": "foliage_meadow", "note": "x", "extra": {"weight": 2.50, "epsilon": 1.0E-4}}` and the supported scene is loaded and written
- **THEN** the bound asset is `foliage_meadow` and the component's unknown values, key order and number text are unchanged

#### Scenario: Unknown folder
- **WHEN** an asset name has no supported project foliage asset
- **THEN** validation warns once, other scene content remains available, and the original name round-trips unchanged

#### Scenario: Missing asset name
- **WHEN** a foliage component omits its asset name
- **THEN** it binds as none, produces no foliage entry and does not gain a default member on write
#### Scenario: Malformed asset name

- **WHEN** the component's asset name is a number
- **THEN** the unbound component remains raw with one warning and preserves that number on write

### Requirement: Disabled entities and components load but do not act

A disabled entity SHALL load with its components and contribute nothing to the scene. A disabled component SHALL load as data without its behavior, while the entity's other components stay active. The flag SHALL round-trip: a scene loaded and written by the engine SHALL keep `enabled: false` where it was.

#### Scenario: Disabled render component
- **WHEN** an entity has `RenderComponent` with `enabled: false` and an active `PositionComponent`
- **THEN** the entity exists and is positioned, and nothing is rendered for it

#### Scenario: Disabled entity
- **WHEN** an entity has `enabled: false`
- **THEN** none of its components renders, lights, collides or moves

#### Scenario: Round trip
- **WHEN** a scene with a disabled component is loaded and written back
- **THEN** the component keeps `enabled: false`
