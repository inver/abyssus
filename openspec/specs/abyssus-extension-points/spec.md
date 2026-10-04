# abyssus-extension-points Specification

## Purpose

Lets other IDE plugins add to what Abyssus edits and shows, so game-specific editor features can ship separately from
Abyssus itself.

## Requirements

### Requirement: Plugins contribute component schemas

Another installed plugin SHALL be able to contribute a component schema, in the format of the project schema file, to
every project Abyssus opens. Its components SHALL be edited like those of a project schema.

#### Scenario: Contributed component

- **WHEN** a test plugin contributes a schema declaring `MarkerComponent` and the `Untitled` project is open
- **THEN** "Add component" on entity `0` (`Model 0`) of `Main Scene` offers `MarkerComponent`

### Requirement: The project's schema wins

When a project schema and a contributed schema declare the same short name, Abyssus SHALL use the project's
declaration for that project and report the conflict once, naming the component and the contributing plugin.

#### Scenario: Two declarations

- **WHEN** the `Custom` project's schema and a contributed schema both declare `PlaneComponent`, with different fields
- **THEN** entity `0`'s plane shows the project's fields, and one message names `PlaneComponent` and the plugin

### Requirement: Contributions follow the plugin's lifetime

When a contributing plugin is unloaded or disabled, its components SHALL become unknown components, shown read-only,
and no scene file SHALL change.

#### Scenario: Plugin unloaded

- **WHEN** the plugin contributing `MarkerComponent` is unloaded while a scene with one is open
- **THEN** the component is shown as read-only JSON and the scene file is unchanged
