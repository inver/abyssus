# Spec Delta

## Purpose

Lets the plugin turn the `ecs` block of a scene file into typed entities and components, and write
it back unchanged in meaning, so later features work with scene content rather than raw JSON.

## ADDED Requirements

### Requirement: Scene ecs block loads into entities

The plugin SHALL load the `ecs` block of a scene file into an engine whose entities carry the
components named in the file, each addressable by the entity id it has in the file. References
between entities (look-at target, parent, point-to-point endpoints) SHALL resolve to the entities
with those ids. Component fields that are
absent in the file SHALL take their defaults: position at the origin, no rotation, unit scale,
no look-at target and no parent (`-1`).

#### Scenario: Entities of Main Scene

- **WHEN** `Main Scene` of the `Untitled` test project is loaded
- **THEN** the engine contains its 7 entities with their name, type, position and render
  components, and entity `0` is named `Model 0` with type `OBJECT`

#### Scenario: Missing transform fields

- **WHEN** an entity has an empty `PositionComponent` (`{}`)
- **THEN** its position is the origin, its rotation the identity and its scale `1,1,1`

#### Scenario: Component unknown to the plugin

- **WHEN** an entity carries a component the plugin does not define (`PickableComponent` and
  `DependenciesComponent` in `Main Scene`)
- **THEN** loading still succeeds, the other components of that entity are loaded, the unknown
  component is kept unchanged with the entity, and the scene file on disk is not modified

#### Scenario: Editor-only renderable

- **WHEN** a render component's renderable names a class that only exists in the Mundus editor
  (`CameraBodyRenderDelegate`, `DirectionHandleRenderDelegate`, `DirectionLineRenderDelegate`)
- **THEN** the entity loads without a renderable, and the rest of the scene loads normally

#### Scenario: Reference to a missing entity

- **WHEN** a look-at, parent or point-to-point reference names an id that is not in the file
- **THEN** it is treated as no target (`-1`), the problem is logged once, and loading continues

#### Scenario: Light component

- **WHEN** a light entity's `LightComponent` holds `color` and `intensity` either directly or in a
  nested `light` object
- **THEN** the loaded light has that color and intensity in both cases

### Requirement: Render components resolve assets through the project

A render component SHALL name its asset by type (`MODEL` or `TERRAIN`) and asset folder name, and
loading SHALL resolve it through the project's assets rather than embedding the asset data.

#### Scenario: Model asset reference

- **WHEN** an entity's render component has `asset.type` `MODEL` and an `assetName`
- **THEN** the loaded component refers to that asset type and name together with its `shaderKey`

#### Scenario: Asset cannot be resolved

- **WHEN** the named asset has no folder in the project's `assets`
- **THEN** the entity is loaded without a renderable, the problem is logged once, and the remaining
  entities load normally

### Requirement: Engine writes back in the Mundus format

An engine SHALL be writable as an `ecs` block that Mundus can read, including camera (`position`,
`viewPointPosition`, `far`, `near`, `fieldOfView`) and light (`color`, `intensity`) data. Anything
loaded but not modeled (unknown components, editor-only renderables, entity `archetype` ids, and the
block's `archetypes`, `componentIdentifiers` and `metadata`) SHALL be written back unchanged.

#### Scenario: Round trip

- **WHEN** a scene's `ecs` block is loaded and written back
- **THEN** reloading the result yields the same entities, components and field values

#### Scenario: Mundus data the plugin does not model survives

- **WHEN** `Main Scene`'s `ecs` block is loaded and written back
- **THEN** the output equals the original block apart from key order, including
  `PickableComponent`, `DependenciesComponent`, the editor-only renderables, `archetypes`,
  `componentIdentifiers` and `metadata`

#### Scenario: Transient state is not written

- **WHEN** an engine is written
- **THEN** derived state (the combined transform matrix, a light's runtime instance) does not
  appear in the output
