# Spec Delta

## MODIFIED Requirements

### Requirement: Adding a light creates a new entity

Choosing a kind in a native scene SHALL add one new entity to the scene file holding a name, a type of `LIGHT_DIRECTIONAL` (Directional and
Sun) or `LIGHT_SPOT` (Spot), a position and a light component. Its id SHALL be one more than the highest numeric entity id
of the scene, and its name the kind's label and that id. Everything else in the file SHALL stay as it was.

#### Scenario: Add a directional light to the fixture scene

- **WHEN** Directional is chosen in `Main Scene`, whose highest entity id is `7`
- **THEN** the file gains entity `8` named `Directional Light 8` with type `LIGHT_DIRECTIONAL` and a light of color `1,1,1,1` and intensity `1`, and entities `0` to `7` are unchanged

#### Scenario: Add a sun

- **WHEN** Sun is chosen
- **THEN** the new entity has type `LIGHT_DIRECTIONAL`, a warm light color, an intensity above `1` and a rotation that points it down and low, so it differs from a Directional light only in those starting values

#### Scenario: Add a spot light

- **WHEN** Spot is chosen
- **THEN** the new entity has type `LIGHT_SPOT`, sits 5 units above its placement point and points straight down

#### Scenario: Empty scene

- **WHEN** a light is added to a scene that has no entities
- **THEN** the new entity gets id `0`

### Requirement: The written scene stays loadable

An added light SHALL use the plugin-defined Name, Type, Position and Light component structure, keeping the scene's own key order, number text and
formatting, and leaving its native `ecs` data valid without creating `componentIdentifiers` or Java class names, so the scene loads again in the plugin without new warnings.

#### Scenario: Reload

- **WHEN** a light has been added and the scene is reopened
- **THEN** it loads without warnings and the entity is listed under its name

#### Scenario: Unrelated data survives

- **WHEN** a light is added to `Main Scene`
- **THEN** the `PickableComponent`, `DependenciesComponent`, unknown native renderable kinds, `archetypes`, native markers and `metadata` of the other entities are as before
