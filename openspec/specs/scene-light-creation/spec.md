# scene-light-creation Specification

## Purpose

Lets users add a directional light, a sun or a spot light to a scene from the plugin, so a scene can be lit without
opening Mundus and without writing entity JSON by hand.

## Requirements

### Requirement: Add Light offers directional, sun and spot

The plugin SHALL offer an Add Light action with three choices, Directional, Sun and Spot, in the Scene view toolbar and
in the right-click menu of a scene row of the Abyssus tree. The action SHALL be unavailable when the scene file cannot be
read as a scene.

#### Scenario: Toolbar choices

- **WHEN** the user opens Add Light in the toolbar of the `Main Scene` Scene view
- **THEN** the choices are Directional, Sun and Spot

#### Scenario: Tree choices

- **WHEN** the user right-clicks the `Main Scene` row in the Abyssus tree
- **THEN** the menu has Add Light with the same three choices

#### Scenario: Unreadable scene

- **WHEN** the scene file holds text that is not valid JSON
- **THEN** Add Light is disabled and nothing is written

### Requirement: Adding a light creates a new entity

Choosing a kind SHALL add one new entity to the scene file holding a name, a type of `LIGHT_DIRECTIONAL` (Directional and
Sun) or `LIGHT_SPOT` (Spot), a position and a light component. Its id SHALL be one more than the highest numeric entity id
of the scene, and its name the kind's label and that id. Everything else in the file SHALL stay as it was.

#### Scenario: Add a directional light to the fixture scene

- **WHEN** Directional is chosen in `Main Scene`, whose highest entity id is `6`
- **THEN** the file gains entity `7` named `Directional Light 7` with type `LIGHT_DIRECTIONAL` and a light of color `1,1,1,1` and intensity `1`, and entities `0` to `6` are unchanged

#### Scenario: Add a sun

- **WHEN** Sun is chosen
- **THEN** the new entity has type `LIGHT_DIRECTIONAL`, a warm light color, an intensity above `1` and a rotation that points it down and low, so it differs from a Directional light only in those starting values

#### Scenario: Add a spot light

- **WHEN** Spot is chosen
- **THEN** the new entity has type `LIGHT_SPOT`, sits 5 units above its placement point and points straight down

#### Scenario: Empty scene

- **WHEN** a light is added to a scene that has no entities
- **THEN** the new entity gets id `0`

### Requirement: A new light is placed where the user is looking

A light added from the Scene view SHALL be placed at the point the view orbits around; one added from the tree SHALL be
placed at the origin of the scene. A light added from either SHALL be selected afterwards.

#### Scenario: From the Scene view

- **WHEN** a Sun is added while the view orbits the point `(10, 0, -4)`
- **THEN** the new entity's position is that point

#### Scenario: From the tree

- **WHEN** a Spot is added from the tree
- **THEN** its position is `(0, 5, 0)`

#### Scenario: Selected afterwards

- **WHEN** a light has been added
- **THEN** it is the selected object and the properties panel shows it

### Requirement: The light takes effect in the view

A light added by the plugin SHALL appear in the Scene view as a light marker and SHALL light the scene as any light entity of
the scene does, with no reopening of the file.

#### Scenario: Sun lights the scene

- **WHEN** a Sun is added to a scene that had no light entities
- **THEN** the Scene view shows its marker and surfaces are shaded by its direction

#### Scenario: Procedural sky follows

- **WHEN** a Directional light is added to a scene whose skybox is a procedural sky
- **THEN** the sun of the sky is opposite the new light's direction

### Requirement: Adding a light is one undoable edit

Each Add Light SHALL be a single command named in the scene file's undo history, saved to disk, and Undo SHALL restore the
file text from before it.

#### Scenario: Undo

- **WHEN** a Sun is added and the user chooses Undo
- **THEN** the scene file text equals what it was before, and the Scene view no longer shows the light

### Requirement: The written scene stays loadable

An added light SHALL use the plugin-defined Name, Type, Position and Light component structure, keeping the scene's own key order, number text and
formatting, and leaving its `ecs` bookkeeping valid, so the scene loads again in the plugin without new warnings.

#### Scenario: Reload

- **WHEN** a light has been added and the scene is reopened
- **THEN** it loads without warnings and the entity is listed under its name

#### Scenario: Unrelated data survives

- **WHEN** a light is added to `Main Scene`
- **THEN** the `PickableComponent`, `DependenciesComponent`, editor-only renderables, `archetypes`, `componentIdentifiers` and `metadata` of the other entities are as before
