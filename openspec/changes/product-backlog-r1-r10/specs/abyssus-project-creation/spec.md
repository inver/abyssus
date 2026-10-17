# Spec Delta

## Purpose

Lets a creator start an Abyssus project and add scenes from the IDE, producing valid native documents without copying a
sample project by hand.

## ADDED Requirements

### Requirement: New project creation
The editor SHALL offer **New Abyssus Project...** on a folder in the Project view. It SHALL ask for a project name, and on
confirmation create inside the chosen folder a `<name>` folder holding `<name>.abss`, an `assets` folder with a procedural sky
asset, and a `scenes` folder with one starter scene named `Main Scene`. The `.abss` `name` and `activeSceneName` SHALL be the
project name and the starter scene's name.

#### Scenario: Create a project in an empty folder
- **WHEN** a user chooses **New Abyssus Project...** on an empty folder `games`, enters `Harbor` and confirms
- **THEN** `games/Harbor/Harbor.abss`, `games/Harbor/assets/<sky asset>/meta.json` and `games/Harbor/scenes/Main Scene.scene` exist
- **AND** the Abyssus view lists the project `Harbor` with one scene `Main Scene` and one asset

#### Scenario: Starter scene is visible without further steps
- **WHEN** the new project's `Main Scene` is opened in the scene view
- **THEN** the view shows the project's sky and a lit empty scene, and the scene has a camera and one sun light entity

### Requirement: Created documents are native version 1
Every document the editor creates for a project or scene SHALL carry `format: "abyssus"` and integral `formatVersion: 1`,
and SHALL pass the same validation the editor applies when reading a project. Component entries SHALL use short names and
asset renderables SHALL use `kind: "asset"`.

#### Scenario: Created files validate
- **WHEN** a project and its starter scene are created
- **THEN** the `.abss`, the `.scene` and the sky asset `meta.json` are each accepted as native documents of version 1

### Requirement: Names are checked and nothing is overwritten
The editor SHALL refuse a project or scene name that is empty, contains a path separator or a reserved file-name character,
or is only dots or spaces, and SHALL show the reason in the dialog. It SHALL NOT overwrite or modify an existing file or
folder: a project name whose target folder already exists, and a scene name that matches an existing scene of the project
when compared without case, SHALL be refused with confirmation disabled.

#### Scenario: Name with a path separator
- **WHEN** a user enters `a/b` as a project name
- **THEN** the dialog shows an error and Create stays disabled

#### Scenario: Existing project folder
- **WHEN** a user enters `Untitled` in a folder that already contains `Untitled`
- **THEN** the dialog refuses the name and no file in that folder changes

#### Scenario: Scene name differs only by case
- **WHEN** a user enters `main scene` for a project that already has `Main Scene`
- **THEN** the dialog refuses the name and no file is created

### Requirement: Failed creation leaves no partial project
If creating a project or scene fails after some files were written, the editor SHALL remove the files and folders it created
in that action, leave every pre-existing file unchanged, and show the failure reason. A project or scene SHALL appear in the
Abyssus view only when all of its files were written.

#### Scenario: Write failure midway
- **WHEN** the starter scene cannot be written after the `.abss` and the sky asset were created
- **THEN** the new project folder no longer exists, a message names the failure, and the Abyssus view lists no new project

### Requirement: New scene in an existing project
The editor SHALL offer **New Scene...** on a project row of the Abyssus view. It SHALL ask for a scene name and create
`scenes/<name>.scene` beside the project's `.abss` with default ambient light, fog and a unique numeric `id`, no entities,
and no skybox selected. It SHALL NOT change the project's `.abss`, including `activeSceneName`, and SHALL NOT change any
other scene.

#### Scenario: Add a scene to Untitled
- **WHEN** a user chooses **New Scene...** on a copy of `Untitled.abss`, enters `Harbor` and confirms
- **THEN** `scenes/Harbor.scene` exists with a numeric `id` different from every other scene's id, and `Main Scene.scene` and `Untitled.abss` are byte-identical to before
- **AND** the Abyssus view lists `Harbor` after `Main Scene` in file-name order

#### Scenario: Unsupported project document
- **WHEN** a user chooses **New Scene...** on a project whose `.abss` lacks `format: "abyssus"`
- **THEN** no scene is created and a message explains that the project is not a supported native document

### Requirement: Empty-state entry points
The Abyssus view SHALL show a **New Abyssus Project...** entry when no `.abss` file is found under the content roots. A
project that has no scene SHALL show a **New Scene...** entry in its `Scenes` row. Both entries run the same actions and
checks as the context-menu commands.

#### Scenario: View with no project
- **WHEN** the Abyssus view is opened in an IDE project with no `.abss` file
- **THEN** it shows a **New Abyssus Project...** entry instead of an empty tree

#### Scenario: Project without scenes
- **WHEN** a project's `scenes` folder holds no `.scene` file
- **THEN** its `Scenes` row offers **New Scene...**
