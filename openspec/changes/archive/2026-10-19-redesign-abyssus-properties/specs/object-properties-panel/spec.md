# Spec Delta

## MODIFIED Requirements

### Requirement: Properties tool window

The plugin SHALL provide an `Abyssus Properties` tool window, available without configuration,
that shows the selected node's header and sections of labelled fields. Its title bar SHALL offer Options and Hide.

#### Scenario: Tool window is available
- **WHEN** a user opens a project where the plugin is installed
- **THEN** an `Abyssus Properties` tool window can be opened from the tool window bar

#### Scenario: Title bar actions
- **WHEN** the tool window is shown
- **THEN** its title bar offers Options and Hide, in that order

### Requirement: Asset header

Above the sections the panel SHALL show an icon for the asset's type, the asset's name and the line `<type> asset`. Assets without supported editors SHALL show a `read-only` badge. Assets with supported editors SHALL show a note that asset edits affect every instance using the asset. An asset that no scene uses SHALL also show a `no scene uses this` badge.

#### Scenario: Skybox header
- **WHEN** the skybox asset `nebula` is selected
- **THEN** the header shows the skybox icon, `nebula`, `SKYBOX asset` and the shared-asset note

#### Scenario: HDR skybox header
- **WHEN** the asset `skybox_hdr` is selected
- **THEN** the header shows the HDR skybox icon, `skybox_hdr`, `SKYBOX_HDR asset` and a `read-only` badge

#### Scenario: Unknown type
- **WHEN** the selected asset's type has no dedicated icon
- **THEN** the header shows a generic asset icon, the type text from `meta.json` and a `read-only` badge

#### Scenario: Unused asset
- **WHEN** an asset that no scene of the project uses is selected
- **THEN** the header shows the badge `no scene uses this` beside the title

## ADDED Requirements

### Requirement: Breadcrumb and subtitle

The header SHALL show, above the title, the path of the selected node from the project down to its parent, each step as text, and a subtitle that names the kind of node: `Project`, `Folder`, `Scene`, `Scene setting`, `ecs`, `Entity <id> · <type>`, `Component` or `Game component` with the class name and the entity, `UI screen`, or `<type> asset`.

#### Scenario: Entity header
- **WHEN** entity 3 of type `OBJECT` named `Racer` in scene `Field` is selected
- **THEN** the breadcrumb reads the project file, `Field`, `ecs`; the title is `Racer`; the subtitle is `Entity 3 · OBJECT`

#### Scenario: Game component header
- **WHEN** the `Plane` component of `Racer`, registered by the game project, is selected
- **THEN** the subtitle is `Game component · PlaneComponent on Racer`

#### Scenario: Project header
- **WHEN** the project file is selected
- **THEN** the breadcrumb is empty and the subtitle reads `Project · <file name>`

### Requirement: Section frame

Each section SHALL have a collapse chevron, a colored icon of its kind, a title and a hairline. A component registered by the game project SHALL show a `game` badge, and a component section SHALL show its class name as a hint. A section SHALL start expanded, and its collapsed state SHALL persist while the selection stays on the same node and SHALL reset when the selection changes to another node.

#### Scenario: Collapse a section
- **WHEN** the user clicks a section's chevron
- **THEN** its fields hide and the chevron turns; a second click shows them again

#### Scenario: Collapse survives a refresh
- **WHEN** a section is collapsed and the scene file changes
- **THEN** the section stays collapsed

#### Scenario: Game component section
- **WHEN** an entity with `PlaneComponent` from the game project is selected
- **THEN** its section shows the `game` badge and the hint `PlaneComponent`; a built-in component shows no badge

### Requirement: Enabled check box

A section of a togglable component, and the section of a scene setting that has an `<x>Enabled` flag, SHALL show an `Enabled` check box that reflects the flag. Unchecking it SHALL write the flag as one undoable edit, dim the section's icon, title and fields, and keep the fields editable. A component or setting with no flag SHALL show no check box.

#### Scenario: Disable a component
- **WHEN** the user unchecks `Enabled` on the Collider section
- **THEN** the component entry gets `enabled: false` in one undoable edit and the section is drawn dim

#### Scenario: Undo
- **WHEN** the user undoes that edit
- **THEN** the flag is removed, the check box is checked and the section is drawn normally

#### Scenario: Scene setting
- **WHEN** the user unchecks `Enabled` on the Fog section
- **THEN** `fogEnabled` becomes `false` and the other fog fields are unchanged

#### Scenario: Name component
- **WHEN** an entity's Name section is shown
- **THEN** it has no `Enabled` check box

### Requirement: Field row kinds

Fields SHALL be drawn as rows with a 150 px label column and a 24 px control. The kinds SHALL be: text, number with an optional unit, vector with one labelled box per axis (`x y z`, or `x y z w` for a rotation), choice, check box, color with a swatch, asset reference with a Browse button, read-only monospace text, link, note, sub-heading and read-only table. Editing a field SHALL behave as `Asset and component fields edit the same way` defines; a rejected value SHALL keep the previous value and show the reason beside the field.

#### Scenario: Number with unit
- **WHEN** a rigid body is shown
- **THEN** its mass is a number field followed by the unit `kg`

#### Scenario: Vector axes
- **WHEN** a position is shown
- **THEN** its value is three boxes labelled `x`, `y` and `z`; a rotation has a fourth `w`

#### Scenario: Asset reference
- **WHEN** a render component's `assetName` is shown
- **THEN** the field shows the asset's icon and name and a Browse button that opens the asset chooser

#### Scenario: Table
- **WHEN** a FOLIAGE asset with six layers is shown
- **THEN** the layers appear as a read-only table with a header row and one row per layer, and the section states the count

### Requirement: Defaults are shown dim

A field whose value is not written in the file, so that its default applies, SHALL be drawn dim, and the panel footer SHALL say that dim values are defaults and are not written. Editing a dim field to a value SHALL write it; reverting to the default SHALL follow the component's existing default-omission rule. Showing a panel SHALL NOT write any default.

#### Scenario: Missing mass
- **WHEN** a rigid body has no `mass`
- **THEN** the mass field shows the default `1` dim and the file is unchanged

#### Scenario: Written value
- **WHEN** a rigid body has `mass: 0.5`
- **THEN** the mass field shows `0.5` in the normal color

#### Scenario: Edit a default
- **WHEN** the user enters `2` in the dim mass field
- **THEN** `mass: 2` is written as one undoable edit and the field is no longer dim

### Requirement: Layouts per selection

The panel SHALL lay out each kind of selection with its own sections: the project (project settings, main camera), a folder (read-only name and item count), a scene (scene, ambient light, fog, skybox), one scene setting (its section only), `ecs` (entity and archetype counts with the type counts as a note, read-only), an entity (one section per component), one component (that section), a UI screen (name, owning asset and a link `Open in Screen Editor`), and an asset (a `meta.json` section with `version`, `lastModified`, `uuid` and `type`, then its `additional` section). A footer note SHALL state that asset `meta.json` identity fields are read-only, or that dim values are defaults and edits save as one undoable edit.

#### Scenario: Folder
- **WHEN** the `Assets` folder row is selected
- **THEN** the panel shows the subtitle `Folder · <n> items`, a read-only Folder section and the `read-only` badge, and no editor

#### Scenario: ecs row
- **WHEN** the `ecs` row of a scene with 127 entities is selected
- **THEN** a read-only Entities section shows the entity and archetype counts and a note with the count of each entity type

#### Scenario: UI screen
- **WHEN** a screen row under a UI asset is selected
- **THEN** the panel shows the screen's name, its asset and a link that opens the Screen Editor on that screen

#### Scenario: Asset with meta and additional
- **WHEN** a TERRAIN asset is selected
- **THEN** the panel shows the `meta.json` section and an `additional` section whose supported fields remain editable

### Requirement: Empty state

When nothing that the panel can show is selected, the panel SHALL show an icon, the line `Nothing is selected.` and a hint to select a scene, an entity, a component or an asset in the Abyssus tree. A selected node that has no properties SHALL keep the message of `Panel follows the asset selection`.

#### Scenario: Nothing selected
- **WHEN** no node is selected
- **THEN** the panel shows the icon, `Nothing is selected.` and the hint

#### Scenario: Selection disappears
- **WHEN** the selected entity is deleted from the scene file
- **THEN** the panel shows the existing message that the entity no longer exists
