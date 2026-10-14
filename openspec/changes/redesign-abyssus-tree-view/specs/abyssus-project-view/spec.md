## MODIFIED Requirements

### Requirement: Project file expansion

A `.abss` node SHALL expand into exactly three folder rows, in this order: `Scenes`, `UI` and `Assets`, each showing its
element count as secondary text. The project's `name` SHALL NOT be a row. `Scenes` SHALL list the scenes found in the
`scenes` folder beside the `.abss` file, ordered by file name, each presented as a scene model expandable into its scene
properties. `UI` SHALL list the project's assets whose type is `UI`, each expandable into its screens. `Assets` SHALL group
the project's other assets under one folder row per asset type present (for example `Models`, `Terrains`, `Foliage`,
`Textures`, `Skyboxes`, `Clouds`), ordered by type and then by asset folder name, each group showing its count. Every scene,
asset and screen SHALL be listed; no list is truncated. The system SHALL NOT navigate, open, or link to the `.scene` files
from the project node. A folder row exists only in the view: it SHALL NOT write anything and SHALL NOT change the file it
groups.

#### Scenario: Project node lists its folders in order

- **WHEN** a user expands `Untitled.abss`
- **THEN** its children are `Scenes`, `UI` and `Assets` in that order and no row reads `name`

#### Scenario: Project node lists its scenes

- **WHEN** a user expands a `.abss` node whose `scenes` folder holds three `.scene` files
- **THEN** the `Scenes` folder reads `3` and lists the three scenes ordered by file name

#### Scenario: Project scenes expand inline

- **WHEN** a user expands a scene listed under `Scenes`
- **THEN** its scene properties are shown from that scene's content

#### Scenario: Project scenes do not navigate to scene files

- **WHEN** a user selects a scene listed under a `.abss` node
- **THEN** no `.scene` file is opened or navigated to as a result

#### Scenario: Assets are grouped by type

- **WHEN** a project holds three MODEL, one TERRAIN and one SKYBOX_PROCEDURAL asset and the user expands `Assets`
- **THEN** it holds the group rows `Models 3`, `Terrains 1` and `Skyboxes 1`, and each asset appears once, inside its group

#### Scenario: UI assets are listed under UI

- **WHEN** a project holds a UI asset with ten screens
- **THEN** the asset is listed under `UI`, not under `Assets`, and expands into ten screen rows

#### Scenario: Nothing is truncated

- **WHEN** a scene has 127 entities or a project has 41 assets
- **THEN** every entity and asset has a row

#### Scenario: Project without a scenes folder

- **WHEN** the view reads a `.abss` file with no `scenes` folder beside it
- **THEN** `Scenes` reads `0`, has no children, and the node still expands

### Requirement: Asset node structure

Each asset file SHALL appear as a single node that expands into a child node per model property, showing the property name
and its value. Properties whose value is a nested object or a list of models SHALL be represented by a node that expands
further into that value's own properties, one child per list element. An entity of the `ecs` tree SHALL be listed directly
under `ecs`, labelled with its name and component count. A boolean `<x>Enabled` property SHALL NOT be a separate node: the
property it gates (`<x>` or `<x>Name`) SHALL show a clickable eye icon at the right edge of its row, shown as visible when
enabled and as hidden (with grayed text) when disabled. Clicking the eye SHALL set the `<x>Enabled` value in the file the
property was read from to the opposite value, changing nothing else in that file, and the view SHALL refresh. A scene
listed under a project SHALL be labeled `<name> (<id>)` (for example `Main Scene (6275127)`) and SHALL NOT repeat `id` and
`name` as child rows; its name SHALL instead be changed through a right-click "Rename Scene..." action, which sets only
`name` in that scene's file. The eye toggle, "Rename Scene..." and the skybox assignment of `abyssus-scene-skybox` are the
only actions that write to an asset file from the tree. An asset node SHALL remain a single node even when its model is
expanded; expanding a file SHALL NOT add extra entries to the view root.

#### Scenario: File expands into its properties

- **WHEN** a user expands an asset node for a valid file
- **THEN** a child node is shown for each property of the model, displaying the property name and its parsed value

#### Scenario: Nested objects expand recursively

- **WHEN** a scene model contains a populated nested object such as `fog` or `ambientLight`
- **THEN** that node can be expanded to reveal the nested object's own properties and values

#### Scenario: Lists expand one child per element

- **WHEN** a model contains a list property with several elements
- **THEN** expanding that property node reveals one child per element, in declaration order, and each element node expands into that element's own properties

#### Scenario: Null values are shown

- **WHEN** a scene has `skyboxName` set to `null`
- **THEN** a `skybox` node is present and displays the value `null` (the view shows `skyboxName` as `skybox`)

#### Scenario: ECS data expands as a generic tree

- **WHEN** a user expands the `ecs` node of a scene
- **THEN** it shows `N entities` as its value and lists one child per entity directly, each labelled with the entity's `NameComponent` name (its id when it has none) with `N components` as its value; an entity expands into one child per component, named without the `Component` suffix, and a component expands into its JSON objects as one child per key and its arrays as one indexed child per item, with scalar values shown next to their names

#### Scenario: Enabled toggles become an eye icon

- **WHEN** a scene model has `fogEnabled` set to false while `fog` carries values
- **THEN** no `fogEnabled` node is shown, and the `fog` node shows the hidden eye icon at the right of its row with grayed text; with `fogEnabled` true it shows the visible eye icon

#### Scenario: Clicking the eye flips the option

- **WHEN** the user clicks the eye on the `fog` row of a scene whose `fogEnabled` is true
- **THEN** the scene file now has `"fogEnabled":false` and is otherwise byte-for-byte unchanged, and clicking again restores the original content; this also applies to scenes listed inside a `.abss` project, where the scene's own file is changed

#### Scenario: Expanding a file does not duplicate it

- **WHEN** a user expands an asset node
- **THEN** the view root still reports the same top-level nodes, and no additional copy of the asset node is inserted

#### Scenario: Folder rows are labelled and counted

- **WHEN** a user expands a project with two scenes and four assets
- **THEN** its `Scenes` and `Assets` rows read `Scenes` and `Assets` with `2` and `4` as their values

#### Scenario: Eye geometry and color

- **WHEN** a row has an eye
- **THEN** the eye is 14 px, blue when on and gray when off, and sits at the right edge of the row, right of any `unused` text

### Requirement: Presentation of asset nodes

Asset nodes SHALL be displayed with the name derived from their file name. Rows SHALL be 24 px high with a 16 px indent per
level and a 16 px icon. Each kind of node (project, `Scenes` / `UI` / `Assets` folders, type groups, scene, UI screen,
ambient light, fog, skybox, ecs, entity, component, and model, terrain, foliage, texture, procedural sky, cube skybox, HDR
skybox, clouds and UI assets) SHALL have its own icon, and secondary text (values, counts) SHALL use the gray text style. A
component registered by the game project (not built in) SHALL carry a `game` badge after its name. A row whose eye is off, or
that lies under such a row, SHALL show gray text and a dimmed icon. The selected row SHALL use the design's selection color.
An asset that no scene uses SHALL show the word `unused` in amber at the right edge and its row SHALL be dimmed. The view
SHALL keep a stable identity per asset file and per folder row so that expanding, refreshing, and reopening the view do not
lose or duplicate nodes.

#### Scenario: Node label uses the file name

- **WHEN** a `.scene` file named `forest.scene` and a `.abss` file named `game.abss` are listed
- **THEN** their nodes are labeled `forest.scene` and `game.abss`

#### Scenario: Reopening the view preserves the node set

- **WHEN** the user switches away from the Abyssus view and back
- **THEN** the same nodes are present, each appearing exactly once

#### Scenario: Added and removed asset files are reflected on refresh

- **WHEN** a `.scene` or `.abss` file is added to or deleted from the project and the view refreshes
- **THEN** the view reflects the new set of asset files

#### Scenario: Foliage asset has its own icon

- **WHEN** a project's assets hold a FOLIAGE asset next to a TERRAIN and a MODEL asset
- **THEN** the foliage entry and its `Foliage` group use the foliage icon, which differs from the terrain and model icons

#### Scenario: Each asset type has its own icon

- **WHEN** a project's assets hold a FOLIAGE, a TERRAIN, a MODEL, a TEXTURE, a CLOUDS and a SKYBOX_PROCEDURAL asset
- **THEN** each asset row and each type-group row uses an icon that differs from the others

#### Scenario: Folder rows have their own icons

- **WHEN** the user expands a project
- **THEN** `Scenes`, `UI` and `Assets` use three distinct folder icons and the type groups use the plain folder icon

#### Scenario: Entity icons follow their type

- **WHEN** an entity under `ecs` has a `TypeComponent` using its short or fully qualified class name
- **THEN** its row uses a distinct SVG icon for each of GROUP, LIGHT_DIRECTIONAL, LIGHT_POINT, LIGHT_SPOT, OBJECT,
  TERRAIN, CAMERA and HANDLE, and a type change updates the icon on refresh even when its label and component count stay the same
- **AND** an absent, null or unknown type uses the generic ECS icon

#### Scenario: Component icons follow their kind

- **WHEN** an entity lists Name, Position, Render, Parent, RigidBody, Collider, Light and Foliage components
- **THEN** each component row uses the icon of its kind and the icons differ from one another where the Icon set draws them differently

#### Scenario: Game components are badged

- **WHEN** an entity has a `PlaneComponent` registered by the game project and a built-in `RenderComponent`
- **THEN** the `Plane` row shows a `game` badge and the `Render` row does not

#### Scenario: Unused asset

- **WHEN** an asset is marked unused
- **THEN** its row shows `unused` in amber at the right edge and is dimmed, and a used asset shows neither

#### Scenario: Row geometry

- **WHEN** the tree shows a project expanded to its scene's `ecs` node
- **THEN** every row is 24 px high at scale 1, and each level's icon starts 16 px to the right of its parent's

### Requirement: Unused assets filter

The view SHALL offer a "Show Only Unused Assets" toggle among its toolbar options; while on, the button SHALL show the
pressed amber state. While it is on, each type group of `Assets` and the `UI` folder SHALL contain only assets marked
`unused`, type groups with none SHALL be hidden, and the counts of the remaining groups and of `Assets` SHALL match what is
listed; scenes and other rows SHALL be unaffected. The choice SHALL persist for the project and default to off.

#### Scenario: Filter on
- **WHEN** a project has four assets, two of them unused and in the same type group, and the user turns the filter on
- **THEN** `Assets` lists only that group with the two unused assets and its count reads `2`

#### Scenario: Group without unused assets is hidden
- **WHEN** the filter is on and no TERRAIN asset is unused
- **THEN** no `Terrains` group is shown

#### Scenario: Filter off
- **WHEN** the user turns the filter off again
- **THEN** all four assets are listed

### Requirement: Counts footer

Under the tree the view SHALL show a 28 px footer reading `N scenes`, `N assets` and `N unused` (`1 scene` for one), the
last in amber, summed over the projects in the view (a standalone scene counts as one scene). The footer SHALL update when
files change and SHALL NOT be affected by the unused filter.

#### Scenario: Footer counts
- **WHEN** the view shows one project with two scenes and four assets, two unused
- **THEN** the footer reads `2 scenes`, `4 assets` and `2 unused`

#### Scenario: Footer ignores the filter
- **WHEN** the unused filter is on
- **THEN** the footer still reads `4 assets`

## ADDED Requirements

### Requirement: Tree header toolbar

The Abyssus view's header SHALL offer, as icon buttons with tooltips and accessible names, Expand All, Collapse All, the
unused filter, Options and Hide, in that order, using the Icon set glyphs.

#### Scenario: Header buttons
- **WHEN** the Abyssus view is shown
- **THEN** its header holds buttons named Expand All, Collapse All, Show Only Unused Assets, Options and Hide in that order

#### Scenario: Expand and collapse all
- **WHEN** the user presses Collapse All and then Expand All
- **THEN** the tree first shows only the project row's children collapsed, then every folder, scene, entity and asset expanded
