## ADDED Requirements

### Requirement: Entities are id-keyed with components and flags

In a supported native scene, `ecs` SHALL be an object keyed by entity id. Each entity SHALL have `components`, an object
keyed by component name (a built-in or registered component, short or fully qualified), and MAY have the header members `type`, `flags` (an integer) and `disabledComponents` (a list of component names). The header is loaded as one `HeaderComponent` together with the entity id.
The `entities` wrapper object is not part of the format.

#### Scenario: Entity 0 of Main Scene

- **WHEN** `Main Scene` of the `Untitled` test project is loaded
- **THEN** entity `0` has a `NameComponent` whose `name` is `Model 0`, and the engine holds the same name on that entity

#### Scenario: Old wrapper

- **WHEN** a scene has an `entities` wrapper in `ecs`
- **THEN** the scene is unsupported: it opens as text with an explanation, and no plugin edit or format pass changes it

### Requirement: Entity flags

`flags` bit `1` SHALL mean the entity is disabled; an entity without `flags` has `0` and is enabled. An edit SHALL keep the
bits it does not own, and SHALL NOT insert `flags` into an entity that never had it unless a bit is set.

#### Scenario: Disabled entity

- **WHEN** an entity has `"flags": 1`
- **THEN** it loads as data, is neither drawn nor picked, and does nothing in the engine

#### Scenario: Unknown bit kept

- **WHEN** an entity has `"flags": 5` and the user enables it
- **THEN** its `flags` becomes `4`

### Requirement: Unmodeled components round-trip

A component whose name is not built in or registered or whose value cannot be bound SHALL be kept
as a raw component that stores its exact payload as JSON text and reported with one warning. Writing the scene back SHALL leave it unchanged, in place.

#### Scenario: Unknown component

- **WHEN** an entity carries a `PickableComponent` and the user moves the entity
- **THEN** the component is still present, in the same position, with identical text

## MODIFIED Requirements

### Requirement: Render components resolve assets through the project

A native render component SHALL be flat: `type` (`MODEL` or `TERRAIN`), the asset folder name `assetName` and an optional `shaderKey`, with no nested `renderable` object, and loading SHALL resolve it through the project's assets rather than embedding the asset data. The editor-only `rayTracingMaterials` member SHALL be kept on the component and ignored by games.

#### Scenario: Model asset reference

- **WHEN** an entity's render component has `type` `MODEL` and an `assetName`
- **THEN** the loaded component refers to that asset type and name together with its `shaderKey`

#### Scenario: Asset cannot be resolved

- **WHEN** the named asset has no folder in the project's `assets`
- **THEN** the entity is loaded without a renderable, the problem is logged once, and the remaining
  entities load normally

### Requirement: Engine writes native ECS data

An engine SHALL write a native `ecs` block with stable short component identifiers: the id-keyed entity map, each entity with `components` and, when a bit is set, `flags`. Unknown components, unbindable components and the other bits of `flags` SHALL round-trip unchanged. It SHALL NOT emit serialized Java class identifiers, `componentIdentifiers`, an `entities` wrapper, archetypes or metadata. Derived state SHALL NOT be written.

#### Scenario: Native round trip
- **WHEN** a native scene is loaded and written back
- **THEN** reloading gives the same entities, components and field values

#### Scenario: Unknown data survives
- **WHEN** native `Main Scene` is loaded and written back
- **THEN** its unknown components survive unchanged

#### Scenario: Transient state is not written
- **WHEN** an engine is written
- **THEN** runtime transforms and light instances do not appear in the output

### Requirement: Disabled entities and components load but do not act

A disabled entity (bit `1` of `flags`) SHALL load with its components and contribute nothing to the scene. A disabled component (named in its entity's `disabledComponents`) SHALL load as data without its behavior, while the entity's other components stay active. The flags SHALL round-trip: a scene loaded and written by the engine SHALL keep `type`, `flags` and `disabledComponents` where they were.

#### Scenario: Disabled render component
- **WHEN** an entity lists `RenderComponent` in `disabledComponents` and has an active `PositionComponent`
- **THEN** the entity exists and is positioned, and nothing is rendered for it

#### Scenario: Disabled entity
- **WHEN** an entity has `"flags": 1`
- **THEN** none of its components renders, lights, collides or moves

#### Scenario: Round trip
- **WHEN** a scene with a disabled component is loaded and written back
- **THEN** the component is still listed in `disabledComponents`
