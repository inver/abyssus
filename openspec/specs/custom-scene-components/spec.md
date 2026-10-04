# custom-scene-components Specification

## Purpose

Lets a game keep data of its own on scene entities, such as a plane's line length, as components declared in its code
and stored in the scene file next to the built-in components.

## Requirements

### Requirement: Declared components load from the scene file

A component a game declares and registers SHALL load from `ecs.entities.<id>.components.<ShortName>` into the
entity with the values the file holds. A field the file omits SHALL take the default the game declares for it.

#### Scenario: Plane values

- **WHEN** a game that registers `PlaneComponent` loads the `Custom` test project's scene, whose entity `0` holds
  `"PlaneComponent": {"lineLength": 22, "kind": "STUNT"}`
- **THEN** entity `0`'s plane has line length `22`, kind `STUNT`, and every other field at its declared default

#### Scenario: Empty component

- **WHEN** an entity holds `"PlaneComponent": {}`
- **THEN** the entity has a plane whose every field is at its declared default

### Requirement: Supported field types

A declared field SHALL be one of: decimal number, whole number, true/false, text, a choice from a fixed list, a
3D vector, a color, a reference to another entity of the scene, or a reference to an asset folder of a given asset
type. Each type SHALL be stored in the file as the matching JSON value: a number, a boolean, a string, the choice's
name, an `{x, y, z}` object, an `{r, g, b, a}` object, an entity id (`-1` for none), or the asset folder name.

#### Scenario: Every type round-trips

- **WHEN** a component with one field of each type, set to values that differ from their defaults, is written and
  loaded again
- **THEN** every field has the value it was written with

#### Scenario: Whole decimal numbers

- **WHEN** a decimal field holds `25.0`
- **THEN** the file holds `25`, the way the scene file writes whole numbers

### Requirement: Defaults are not written

Writing a declared component SHALL leave out every field whose value equals its declared default, as built-in components do. A component whose every field is at its default SHALL be written as an empty object.

#### Scenario: Default line length

- **WHEN** a plane with line length `18` (the default) and kind `STUNT` is written
- **THEN** the file holds `"PlaneComponent": {"kind": "STUNT"}`

### Requirement: Unusable values fall back to the default

A field whose value in the file has the wrong type, is not one of the field's choices, or is outside the field's
declared limits SHALL take its default, and the problem SHALL be reported once to the loading log, naming the entity,
the component and the field. The rest of the scene SHALL load.

#### Scenario: Text where a number belongs

- **WHEN** entity `0` holds `"PlaneComponent": {"lineLength": "long"}`
- **THEN** its line length is `18`, the log receives one message naming entity `0`, `PlaneComponent` and
  `lineLength`, and every other entity loads

#### Scenario: Below the minimum

- **WHEN** `lineLength` is declared with a minimum of `5` and the file holds `2`
- **THEN** its line length is `18` and the log names the field and the limit

### Requirement: Unregistered components stay as they are

A component name a program has not registered SHALL be kept unchanged with its entity and written back as it was, as
for any component the runtime does not model.

#### Scenario: Scene opened without the game's components

- **WHEN** the `Custom` scene is loaded by a program that registers no game components, and written back
- **THEN** entity `0`'s `PlaneComponent` is written back with exactly the text values it had

### Requirement: Registration is checked

Registering a component SHALL fail, naming the component and the reason, when its short name is already taken by a
built-in component or another registered component, when a declared field has an unsupported type, or when the
component cannot be created with all its fields at their defaults. A failed registration SHALL load no scene.

#### Scenario: Taken name

- **WHEN** a game registers a component under the short name `NameComponent`
- **THEN** registration fails with a message saying `NameComponent` is a built-in component

### Requirement: Written components use their short name only

A declared component SHALL be written under its short name and SHALL NOT cause a class name, `ecs.componentIdentifiers`
or other Java-class bookkeeping to be written. A scene that already holds `ecs.componentIdentifiers` is refused by the
native document format before it reaches the writer.

#### Scenario: First plane in a scene

- **WHEN** a plane is added to entity `0` and the scene is written
- **THEN** entity `0` holds `"PlaneComponent": {}` and the `ecs` block gains no `componentIdentifiers` and no class name

### Requirement: A vector's limits hold for each axis

A minimum, maximum or exclusive minimum declared on a 3D vector field SHALL apply to each of its axes, when loading
and when editing. A vector with an axis outside the limits SHALL be unusable as a whole, and the problem SHALL name
the axis.

#### Scenario: Box half extents

- **WHEN** a collider's half extents are declared with an exclusive minimum of `0` and the file holds
  `{"x": 1, "y": 0, "z": 1}`
- **THEN** the half extents load as the default and the log names `halfExtents`, the axis `y` and the minimum

#### Scenario: Editing one axis

- **WHEN** the `y` of those half extents is set to `0` in the panel
- **THEN** the file is unchanged and a message names the field and its minimum
