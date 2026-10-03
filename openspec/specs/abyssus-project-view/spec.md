# abyssus-project-view Specification

## Purpose

Lets users browse Abyssus asset files in an Abyssus view inside the Project tool window, where
every `*.scene` and `*.abss` file found in the project is listed and each file can be expanded
to show the structure of the scene or project model it describes.

## Requirements

### Requirement: Abyssus view registration

The plugin SHALL expose an `Abyssus` view in the Project tool window so users can select it
from the view selector alongside the built-in project views. The view SHALL be available without any user configuration.

#### Scenario: View is selectable

- **WHEN** a user opens the Project tool window in a project where the plugin is installed
- **THEN** `Abyssus` is listed as one of the available views in the view selector


### Requirement: Asset file discovery

The Abyssus view SHALL display every file whose name ends with the `.scene` or `.abss`
extension found under the project's content roots, including files in directories that are
excluded from compilation but present on disk. Each `.abss` project SHALL appear as a
top-level node of the view; no directory nodes are shown. A `.scene` file in the `scenes` folder
next to an `.abss` file belongs to that project and SHALL NOT appear on its own; any other
`.scene` file appears as a top-level node. Files with other extensions SHALL NOT appear as asset
nodes.

#### Scenario: Assets across nested directories are listed

- **WHEN** a project contains `assets/levels/forest.scene`, `assets/ui/menu.scene`, and
  `assets/game.abss`
- **THEN** `game.abss`, `forest.scene` and `menu.scene` appear as top-level nodes, with no
  directory nodes between them and the view root

#### Scenario: Extension matching is case sensitive and suffix based

- **WHEN** a project contains `forest.scene`, `Forest.SCENE`, `forest.scene.bak`, `game.abss`,
  and `Game.ABSS`
- **THEN** only `forest.scene` and `game.abss` are treated as asset files

#### Scenario: Project scenes folder is not shown

- **WHEN** a project contains `Untitled.abss` and `scenes/Main Scene.scene`
- **THEN** the view's only top-level node is `Untitled.abss`, there is no `scenes` folder node,
  and `Main Scene` is listed inside the project's `scenes` property

#### Scenario: Assets in excluded source folders are listed

- **WHEN** a `.scene` or `.abss` file lives under a directory marked as Excluded in the
  project model
- **THEN** that file is still listed in the Abyssus view

### Requirement: Asset model reading

The view SHALL read each asset file into the model that corresponds to its type: a `.scene`
file into a scene model exposing identity (`id`, `name`), ambient light
(`ambientLightEnabled`, `ambientLight`), fog (`fogEnabled`, `fog`), skybox
(`skyboxEnabled`, `skyboxName`) and the ECS section (`ecs`); a `.abss` file into a project
model exposing `name` and `scenes`, where `scenes` are the `.scene` files in the `scenes`
folder beside the `.abss` file. Reading SHALL NOT modify the file on disk.

#### Scenario: Scene file is parsed into its model

- **WHEN** the view reads a valid `.scene` file
- **THEN** the resulting model exposes the identity, ambient light, fog, skybox, and ECS
  properties with the values declared in the file

#### Scenario: Project file is parsed into its model

- **WHEN** the view reads a valid `.abss` file
- **THEN** the resulting model exposes a `name` and one scene per `.scene` file in the `scenes`
  folder beside it, ordered by file name

#### Scenario: Project without a scenes folder

- **WHEN** the view reads a `.abss` file with no `scenes` folder beside it
- **THEN** the project model has an empty `scenes` list and the node still expands

#### Scenario: Files are never rewritten by reading

- **WHEN** the view reads a `.scene` or `.abss` file
- **THEN** the file's content on disk is byte-for-byte unchanged

#### Scenario: Unreadable file does not break the view

- **WHEN** an asset file is empty, malformed, or otherwise cannot be parsed
- **THEN** that file still appears as an asset node, shows a placeholder instead of its
  properties, and the remaining asset files in the view continue to load

### Requirement: Asset node structure

Each asset file SHALL appear as a single node that expands into a child node per model
property, showing the property name and its value. Properties whose value is a nested object
or a list of models SHALL be represented by a node that expands further into that value's own
properties, one child per list element. The `scenes` and `assets` lists SHALL be labelled `Scenes` and `Assets` with their element count as value, and an entity of the `ecs` tree SHALL be listed directly under `ecs`, labelled with its name and component count. A boolean `<x>Enabled` property SHALL NOT be a separate node: the property it gates (`<x>` or
`<x>Name`) SHALL show a clickable eye icon at the right edge of its row, shown as visible when enabled and
as hidden (with grayed text) when disabled. Clicking the eye SHALL set the `<x>Enabled` value in
the file the property was read from to the opposite value, changing nothing else in that file,
and the view SHALL refresh. A scene listed under a project SHALL be labeled `<name> (<id>)` (for example `Main Scene (6275127)`) and SHALL NOT repeat `id` and `name` as child rows; its name SHALL instead be changed through a right-click "Rename Scene..." action, which sets only `name` in that scene's file. A project scene's skybox row SHALL also offer a skybox chooser (see the `abyssus-scene-skybox` capability), which sets only `skyboxName` in that scene's file. These three actions are the only ones that write to an asset file. An asset node SHALL remain a single node even when its model is expanded
- expanding a file SHALL NOT add extra entries to the view root.

#### Scenario: File expands into its properties

- **WHEN** a user expands an asset node for a valid file
- **THEN** a child node is shown for each property of the model, displaying the property name
  and its parsed value

#### Scenario: Nested objects expand recursively

- **WHEN** a scene model contains a populated nested object such as `fog` or `ambientLight`
- **THEN** that node can be expanded to reveal the nested object's own properties and values

#### Scenario: Lists expand one child per element

- **WHEN** a model contains a list property with several elements
- **THEN** expanding that property node reveals one child per element, in declaration order,
  and each element node expands into that element's own properties

#### Scenario: Null values are shown

- **WHEN** a scene has `skyboxName` set to `null`
- **THEN** a `skybox` node is present and displays the value `null` (the view shows `skyboxName` as `skybox`)

#### Scenario: ECS data expands as a generic tree

- **WHEN** a user expands the `ecs` node of a scene
- **THEN** it shows `N entities` as its value and lists one child per entity directly (no `entities` level), each
  labelled with the entity's `NameComponent` name (its id when it has none) with `N components` as its value; an entity
  expands into one child per component, named without the `Component` suffix, and a component expands into its JSON
  objects as one child per key and its arrays as one indexed child per item, with scalar values shown next to their names

#### Scenario: Folder rows are labelled and counted

- **WHEN** a user expands a project with two scenes and four assets
- **THEN** its `scenes` and `assets` rows read `Scenes` and `Assets` with `2` and `4` as their values

#### Scenario: Enabled toggles become an eye icon

- **WHEN** a scene model has `fogEnabled` set to false while `fog` carries values
- **THEN** no `fogEnabled` node is shown, and the `fog` node shows the hidden eye icon at the
  right of its row with grayed text; with `fogEnabled` true it shows the visible eye icon

#### Scenario: Clicking the eye flips the option

- **WHEN** the user clicks the eye on the `fog` row of a scene whose `fogEnabled` is true
- **THEN** the scene file now has `"fogEnabled":false` and is otherwise byte-for-byte unchanged,
  and clicking again restores the original content; this also applies to scenes listed inside a
  `.abss` project, where the scene's own file is changed

#### Scenario: Expanding a file does not duplicate it

- **WHEN** a user expands an asset node
- **THEN** the view root still reports the same top-level nodes, and no additional copy of the
  asset node is inserted

### Requirement: Project file expansion

A `.abss` node SHALL expand into its project model: the project `name`, and a `scenes`
property whose expansion lists the scenes found in the `scenes` folder beside the `.abss`
file, ordered by file name. Each listed scene SHALL be presented as a scene model, expandable
into its scene properties. The system SHALL NOT navigate, open, or link to those `.scene`
files from the project node.

#### Scenario: Project node lists its scenes

- **WHEN** a user expands a `.abss` node whose `scenes` folder holds three `.scene` files
- **THEN** the expansion shows the project `name` and a `scenes` node listing the three scenes
  ordered by file name

#### Scenario: Project scenes expand inline

- **WHEN** a user expands a scene listed under a `.abss` node
- **THEN** its scene properties are shown from that scene's content

#### Scenario: Project scenes do not navigate to scene files

- **WHEN** a user selects a scene listed under a `.abss` node
- **THEN** no `.scene` file is opened or navigated to as a result

### Requirement: Presentation of asset nodes

Asset nodes SHALL be displayed with the name derived from their file name. Each kind of node (project, scene, ambient light, fog, skybox, ecs, folder, entity, component, and model, terrain and skybox assets) SHALL have its own icon, and secondary text (values, counts) SHALL use the gray text style. The view SHALL keep a stable identity per asset file so that
expanding, refreshing, and reopening the view do not lose or duplicate nodes.

#### Scenario: Node label uses the file name

- **WHEN** a `.scene` file named `forest.scene` and a `.abss` file named `game.abss` are listed
- **THEN** their nodes are labeled `forest.scene` and `game.abss`

#### Scenario: Reopening the view preserves the node set

- **WHEN** the user switches away from the Abyssus view and back
- **THEN** the same asset nodes are present, each appearing exactly once

#### Scenario: Added and removed asset files are reflected on refresh

- **WHEN** a `.scene` or `.abss` file is added to or deleted from the project and the view
  refreshes
- **THEN** the view reflects the new set of asset files

### Requirement: Asset file recognition

The plugin SHALL register `.scene` and `.abss` files as recognized file types so they are
distinguished from plain text and unknown types, and SHALL associate a distinct icon with each
so the two kinds are distinguishable in the view. Registering the file types SHALL NOT change
how existing file types (for example `.gltf`) are handled.

#### Scenario: Asset files get a distinct icon per kind

- **WHEN** the Abyssus view renders a scene node and a project node
- **THEN** the scene node uses the scene icon and the project node uses the project icon

#### Scenario: Existing file types are unaffected

- **WHEN** a `.gltf` file is opened or displayed
- **THEN** it is still handled as a Gltf file, unchanged by the asset file type registrations

### Requirement: Unused assets filter

The view SHALL offer a "Show Only Unused Assets" toggle among its toolbar options. While it is on, the `Assets` list of
every project SHALL contain only assets marked `unused`; scenes and other rows SHALL be unaffected. The choice SHALL
persist for the project and default to off.

#### Scenario: Filter on
- **WHEN** a project has four assets, two of them unused, and the user turns the filter on
- **THEN** the `Assets` row lists only the two unused assets and its count reads `2`

#### Scenario: Filter off
- **WHEN** the user turns the filter off again
- **THEN** all four assets are listed

### Requirement: Counts footer

Under the tree the view SHALL show a footer reading `N scenes`, `N assets` and `N unused`, summed over the projects in the view
(a standalone scene counts as one scene). The footer SHALL update when files change and SHALL NOT be affected by the unused filter.

#### Scenario: Footer counts
- **WHEN** the view shows one project with two scenes and four assets, two unused
- **THEN** the footer reads `2 scenes`, `4 assets` and `2 unused`

#### Scenario: Footer ignores the filter
- **WHEN** the unused filter is on
- **THEN** the footer still reads `4 assets`

### Requirement: Component actions in the tree

In addition to the write actions already listed for asset nodes, an entity row of the `ecs` tree SHALL offer an "Add Component..." context menu action listing the modeled kinds the entity lacks, and a modeled component row SHALL offer "Remove Component". Both SHALL write through the rules of the `scene-component-editing` capability, and the tree SHALL refresh to show the result. An unmodeled component row SHALL NOT offer Remove Component.

#### Scenario: Add from the tree

- **WHEN** the user right-clicks an entity and chooses Add Component... > Light
- **THEN** the entity gains a `Light` child row and its component count increases by one

#### Scenario: Remove from the tree

- **WHEN** the user right-clicks the `Light` row and chooses Remove Component
- **THEN** the row disappears and the entity's component count decreases by one

#### Scenario: No action on unmodeled component

- **WHEN** the user right-clicks the `Pickable` row
- **THEN** the menu offers no Remove Component action

#### Scenario: Other rows

- **WHEN** the user right-clicks a scene, an asset or a scalar property
- **THEN** the menu has no Add Component or Remove Component action
