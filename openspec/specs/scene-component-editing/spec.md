# scene-component-editing Specification

## Purpose

Lets users add, change and remove the components of a scene entity from the plugin, so scene content can
be edited without writing component JSON by hand and without disturbing anything else in the file.

## Requirements

### Requirement: Add a component to an entity

The plugin SHALL add a component of any modeled kind (Name, Type, Parent, Position, Camera, Light,
Point2Point, Render), or of any kind a known component schema declares, to an entity of a scene file, initialized
with the defaults a scene load gives that kind. An entity SHALL NOT receive a second component of a kind it already
has.

#### Scenario: Add a light

- **WHEN** a `LightComponent` is added to an entity that has none
- **THEN** the entity in the file gains a `LightComponent` with color `1,1,1,1` and intensity `1`, and its other components are unchanged

#### Scenario: Add a render component

- **WHEN** a `RenderComponent` is added and the user picks the model asset `tree` of the project
- **THEN** the entity gains a render component naming asset type `MODEL` and asset name `tree`

#### Scenario: Component already present

- **WHEN** a `PositionComponent` is added to an entity that has one
- **THEN** nothing is written and the user is told the entity already has it

#### Scenario: Unmodeled kind is not offered

- **WHEN** the user opens the list of components that can be added
- **THEN** it holds only modeled and schema-declared kinds the entity lacks, and never `PickableComponent` or
  `DependenciesComponent`

#### Scenario: Add a schema-declared component

- **WHEN** a `PlaneComponent`, declared by the `Custom` project's schema, is added to entity `1` of its scene
- **THEN** entity `1` gains `"PlaneComponent": {}` and nothing else in the file changes

### Requirement: Add a component as a new entity

Add Component... SHALL also be offered on the **ecs** row of a scene in the Abyssus tree, including a scene whose
entities sit in an older wrapped `entities` block. It SHALL list every modeled or schema-declared kind except Name; Render SHALL
offer the project's models and terrains as it does on an entity row. Choosing a kind SHALL add one new entity:
- its id SHALL be one more than the highest numeric entity id;
- it SHALL have a `NameComponent` `Entity <id>`;
- it SHALL hold the chosen component, from its defaults or the chosen asset, by the same rules as adding to an existing
  entity.

In a wrapped scene, its `archetype` SHALL name an archetype listing exactly its components, reusing a matching one.
The addition SHALL be one undoable edit, nothing else in the file SHALL change, and the new entity's row SHALL be
selected. It SHALL be unavailable when the scene cannot be read as a native scene.

#### Scenario: A camera as a new entity

- **WHEN** the user right-clicks the `ecs` row of `Main Scene` of the Untitled project, whose highest entity id is `8`, and chooses Add Component... → Camera
- **THEN** the file gains entity `9` named `Entity 9` with a camera component of default values, and entities `0` to `8` are unchanged

#### Scenario: A render component as a new entity

- **WHEN** the user chooses Add Component... → Render → model `tree` on the `ecs` row of `Main Scene`
- **THEN** the new entity `9` holds a render component of asset `MODEL` `tree`

#### Scenario: Name is not offered

- **WHEN** the user opens Add Component... on the `ecs` row
- **THEN** Name is not among the choices, since every new entity is named

#### Scenario: Undo

- **WHEN** the user chooses Undo after adding a component as a new entity
- **THEN** the scene file is as it was before

#### Scenario: Unreadable scene

- **WHEN** the scene file holds text that is not valid JSON
- **THEN** Add Component... is not offered on its `ecs` row and nothing is written

### Requirement: Update a component field

The plugin SHALL change one field of a modeled or schema-declared component of an entity and write only that change
into the file. A value that does not fit the field, including one outside a schema field's declared limits or not
among its choices, SHALL be rejected with a message and leave the file unchanged.

#### Scenario: Change a camera field

- **WHEN** the `fieldOfView` of an entity's camera is set to `60`
- **THEN** the file holds `60` for it and every other value of the entity is unchanged

#### Scenario: Not a number

- **WHEN** a numeric field is set to `abc`
- **THEN** the file is unchanged and a message names the field and the problem

#### Scenario: Value equals the default

- **WHEN** a position's `localScale.y` is set back to `1`
- **THEN** the file follows the Abyssus version 1 default omission rules, leaving no field that only repeats a default

#### Scenario: No change

- **WHEN** a field is set to the value it already has
- **THEN** the file is not modified

#### Scenario: Change a schema-declared field

- **WHEN** entity `0`'s `PlaneComponent` `lineLength` in the `Custom` scene is set to `25`
- **THEN** the file holds `25` for it and the plane's other fields are unchanged

#### Scenario: Outside a declared limit

- **WHEN** `lineLength`, declared with a minimum of `5`, is set to `2`
- **THEN** the file is unchanged and a message names the field and its minimum

### Requirement: References between entities stay valid

An update that sets a look-at, parent or point-to-point entity, or an entity-reference field of a schema-declared
component, SHALL accept only the id of an entity of the scene or `-1` (none), and SHALL reject an entity as its own
parent and any parent chain that would loop.

#### Scenario: Unknown target

- **WHEN** a look-at target is set to the id `99` and the scene has no entity `99`
- **THEN** the value is rejected and the file is unchanged

#### Scenario: Parent cycle

- **WHEN** entity `1` has parent `2` and the parent of entity `2` is set to `1`
- **THEN** the value is rejected and the file is unchanged

#### Scenario: Clear a reference

- **WHEN** a look-at target is set to `-1`
- **THEN** the file no longer holds a `lookAtId` for that entity

#### Scenario: Schema entity reference

- **WHEN** the `pilot` field of entity `0`'s `PlaneComponent` is set to `99` and the scene has no entity `99`
- **THEN** the value is rejected and the file is unchanged

### Requirement: Remove a component

The plugin SHALL remove a modeled or schema-declared component from an entity. Removing the `PositionComponent` of an entity
that another entity looks at, has as parent or uses as a point-to-point endpoint SHALL be refused while
anything refers to it.

#### Scenario: Remove a light

- **WHEN** the `LightComponent` of an entity is removed
- **THEN** the entity no longer has it in the file and keeps all its other components in their order

#### Scenario: Target of a look-at

- **WHEN** the `PositionComponent` of entity `3` is removed and entity `5` looks at entity `3`
- **THEN** the removal is refused with a message naming entity `5`, and the file is unchanged

#### Scenario: Unmodeled component

- **WHEN** the user selects a `PickableComponent`
- **THEN** no remove action is offered for it

#### Scenario: Remove a schema-declared component

- **WHEN** entity `0`'s `PlaneComponent` is removed
- **THEN** the entity no longer has it, keeps its other components in their order, and nothing else in the file changes

### Requirement: Edits preserve the rest of the scene file

Every create, update and remove SHALL leave everything it does not target as it was: other entities,
unmodeled components, `archetype` ids, the `ecs` block's `archetypes`, `metadata` and the native document markers,
the order of entities and components, and the formatting style of the file.

#### Scenario: Unmodeled data survives

- **WHEN** a component is changed in `Main Scene`
- **THEN** reloading the file shows the same `PickableComponent`, `DependenciesComponent`, unknown native renderable kinds, `archetypes`, `metadata` and the native document markers as before

#### Scenario: Written file loads

- **WHEN** any sequence of adds, updates and removes has been applied
- **THEN** the scene loads again without new warnings and the Scene view shows the result

### Requirement: Each change is one undoable edit

Each add, update or remove SHALL be a single named command in the scene file's undo history, saved to
disk, and Undo SHALL restore the file text from before it.

#### Scenario: Undo an update

- **WHEN** a camera's `near` is changed and the user chooses Undo
- **THEN** the scene file text equals what it was before the change

#### Scenario: Scene view follows

- **WHEN** a component is added, changed or removed while the scene's Scene view tab is open
- **THEN** the tab shows the result without being reopened

### Requirement: Edit a light's range

The plugin SHALL let the user change the `range` of a light component, the distance a point or spot light reaches. A range
equal to the default of `100` SHALL leave no `range` key in the file, and a range that is not a positive number SHALL be
rejected with a message and leave the file unchanged.

#### Scenario: Set a range

- **WHEN** the range of a spot light entity's light is set to `30`
- **THEN** the file holds `range` `30` in its light and every other value of the entity is unchanged

#### Scenario: Back to the default

- **WHEN** a range of `30` is set back to `100`
- **THEN** the light no longer holds a `range` key

#### Scenario: Not a positive number

- **WHEN** the range is set to `0` or to `abc`
- **THEN** the file is unchanged and a message names the field and the problem

#### Scenario: Range follows in the view

- **WHEN** a point light's range is changed while the Scene view is open
- **THEN** the surfaces it lights change to match without reopening the tab

### Requirement: Persist spotlight beam edits

The plugin SHALL save each spotlight's cone angle and edge softness in its scene, restore both on reopening, and apply accepted edits immediately. Each edit SHALL be one undoable command preserving unrelated scene text. Defaults SHALL follow the agreed storage format's omission convention.

#### Scenario: Reopen spotlight settings
- **WHEN** a spotlight is set to a 60-degree cone and 25-percent edge softness and the scene is saved and reopened
- **THEN** the fields and displayed beam retain those values

#### Scenario: Undo a beam edit
- **WHEN** the cone angle is changed and the user chooses Undo
- **THEN** the scene text and displayed beam return to their previous state

#### Scenario: Preserve existing scene content
- **WHEN** a spotlight beam field is edited in a copy of Untitled's Main Scene
- **THEN** unrelated entities, number text, key order, formatting and unmodeled components remain unchanged

### Requirement: Validate spotlight beam edits

Cone angle edits SHALL accept only finite numbers greater than 0 and less than 180 degrees. Edge softness edits SHALL accept only finite numbers from 0 through 100 percent. Invalid edits SHALL leave the scene unchanged and explain the rejected value.

#### Scenario: Invalid angle
- **WHEN** cone angle is set to 0, 180, a nonfinite value or nonnumeric text
- **THEN** the edit is rejected and the scene remains unchanged

#### Scenario: Invalid softness
- **WHEN** edge softness is set below 0, above 100, to a nonfinite value or to nonnumeric text
- **THEN** the edit is rejected and the scene remains unchanged

### Requirement: Native spotlight storage

A native spotlight SHALL store `coneAngle` as full degrees and `edgeSoftness` as a fraction from 0 to 1 in its existing nested or flat light representation. Defaults SHALL be 45 degrees and 0.2 softness, omitted when reset. Documentation SHALL describe these as native Abyssus fields.

#### Scenario: Save native beam fields
- **WHEN** a nested spotlight is saved with a 60-degree cone and 25-percent softness
- **THEN** its light object holds `coneAngle: 60` and `edgeSoftness: 0.25`, with unrelated content preserved

#### Scenario: Reset native defaults
- **WHEN** beam settings are reset to 45 degrees and 20-percent softness
- **THEN** those keys are omitted and the effective values remain the defaults
