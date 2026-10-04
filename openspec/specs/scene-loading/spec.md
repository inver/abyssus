# scene-loading Specification

## Purpose

Lets the editor, a game, its tests and any other libGDX program read a Mundus project's scenes into entities the same
way, from a project folder, without needing a running IDE.

## Requirements

### Requirement: Projects and scenes load without the IDE

Given a Mundus project folder, a program that runs no IDE SHALL read the project's name from its `.abss` file, list the
`.scene` files of its `scenes` folder by name, and load any of them, top-level fields and `ecs` block, into entities
carrying the same components and values the plugin shows for that scene.

#### Scenario: Untitled project outside the IDE

- **WHEN** a test with no IDE running reads the `Untitled` project folder
- **THEN** the project's name is `Untitled` and its only scene is `Main Scene`

#### Scenario: Main Scene's entities outside the IDE

- **WHEN** a test with no IDE running loads `Main Scene` of the `Untitled` project
- **THEN** the scene's name is `Ololo`, its sky is `skybox_physical`, it holds the entities with ids 0 to 8, entity
  `0` is `Model 0` of type `OBJECT` rendering `model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`, and entity `8` is
  `Spot Light 8` of type `LIGHT_SPOT`

#### Scenario: Same result inside the IDE

- **WHEN** the Abyssus view, the properties panel and the scene view show `Main Scene` in the IDE
- **THEN** they show the same entities, components and values as before this change

### Requirement: A scene loads from unsaved text

A scene SHALL also load from text given together with its project folder, so a caller can load what an editor holds
before it is saved. The result SHALL be the same as loading the saved file with that text, and the file on disk SHALL
NOT be read or changed.

#### Scenario: Unsaved rename

- **WHEN** `Main Scene`'s text with entity `0` renamed to `Plane` is loaded with the `Untitled` project folder
- **THEN** entity `0` is named `Plane`, every other entity is as in the file, and `Main Scene.scene` on disk is
  unchanged

### Requirement: Scene load problems go to the caller's log

A problem met while reading a project or loading a scene SHALL be reported once, naming the file and what is wrong, to
the log the caller provided, and SHALL NOT stop the rest of the project or scene from loading. In the IDE that log
SHALL be the IDE log, as before.

#### Scenario: Unmodeled component

- **WHEN** `Main Scene` of `Untitled` is loaded with a test's own log
- **THEN** that log receives one message for `PickableComponent` and one for `DependenciesComponent`, each saying it
  is kept unchanged, and all entities load

#### Scenario: Unreadable scene

- **WHEN** a copy of `Untitled` also holds a scene file whose text is not JSON
- **THEN** the log receives one message naming that file, and `Main Scene` still loads

### Requirement: Independent scene loads share nothing

Two loads started with different logs SHALL keep separate entities, separate warnings and separate logs: what one
loads or reports SHALL NOT appear in the other.

#### Scenario: Two projects at once

- **WHEN** one load reads `Main Scene` of `Untitled` and another reads `Main.scene` of `Animated` with its folder, at
  the same time
- **THEN** each holds only its own scene's entities, and each log receives only its own scene's messages
