# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
