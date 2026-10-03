# Spec Delta

## Purpose

Lets users inspect an asset chosen in the Abyssus view by listing the properties of its Meta in a dedicated
panel, so they can be read without opening `meta.json`.

## ADDED Requirements

### Requirement: Properties tool window

The plugin SHALL provide an `Abyssus Properties` tool window, available without configuration,
that shows a Name / Value table for the selected asset.

#### Scenario: Tool window is available
- **WHEN** a user opens a project where the plugin is installed
- **THEN** an `Abyssus Properties` tool window can be opened from the tool window bar

### Requirement: Panel follows the asset selection

The panel SHALL show the Meta of the asset selected in the Abyssus view and SHALL update when the selection changes.

#### Scenario: Select an asset
- **WHEN** the user selects an asset row in the Abyssus view
- **THEN** the panel lists the properties of that asset's `meta.json`

#### Scenario: Nothing selected
- **WHEN** nothing is selected
- **THEN** the panel shows `Nothing selected.` and a hint to select a skybox, model or terrain under Assets to see its `meta.json`

#### Scenario: Selection is not an asset
- **WHEN** the selected node is not an asset (a scene, a project file, an entity, a property row)
- **THEN** the panel shows `Nothing to show: <name> is <what it is>.` (a scene, the project file, or an entity or setting) and the same hint

### Requirement: Asset header

Above the table the panel SHALL show an icon for the asset's type (skybox, model, terrain, or a generic icon for any other type), the asset's name and the line `<type> asset · read-only`.

#### Scenario: Skybox header
- **WHEN** the skybox asset `nebula` is selected
- **THEN** the header shows the skybox icon, `nebula` and `skybox asset · read-only`

#### Scenario: Unknown type
- **WHEN** the selected asset's type has no dedicated icon
- **THEN** the header shows a generic asset icon and the type text from `meta.json`

### Requirement: Meta property rows

The panel SHALL list the top-level Meta fields (`version`, `lastModified`, `uuid`, `type`, in file order) and then
the fields of `additional`, shown under an `additional` heading. Each row SHALL show the property name and its value:
a scalar as its text, a null as `null`, a list as a summary of its item count and `lastModified` as a date-time rather than epoch milliseconds. Any field present in `meta.json` SHALL
be listed, whatever the asset type; a field missing from the file SHALL NOT be listed.

#### Scenario: Skybox
- **WHEN** the selected asset is a skybox whose Meta has `top`, `bottom`, `left`, `right`, `front` and `back` set to `skybox_default.png`
- **THEN** the panel shows `type` = `SKYBOX` and six rows under `additional`, each with the value `skybox_default.png`

#### Scenario: Terrain with null values
- **WHEN** the selected asset is a terrain with `size: 1600` and `splatMap: null`
- **THEN** the panel shows `size` = `1600` and `splatMap` = `null` under `additional`

#### Scenario: List value
- **WHEN** the selected asset is a model whose `materials` is an empty list
- **THEN** the `materials` row shows a summary naming zero items

#### Scenario: Last modified
- **WHEN** the selected asset's `lastModified` is `1663444124794`
- **THEN** the panel shows that moment as a readable date-time and `meta.json` is unchanged

#### Scenario: Asset without uuid
- **WHEN** the selected asset's `meta.json` has no `uuid` field
- **THEN** no `uuid` row is shown

#### Scenario: Missing or unreadable meta
- **WHEN** the selected asset has no `meta.json` or it cannot be parsed
- **THEN** the panel shows a message saying the asset's Meta cannot be read, with the reason for a parse error

### Requirement: Skybox face previews

For a skybox asset the panel SHALL show a "Face previews" section with the six faces (`top`, `bottom`, `left`, `right`, `front`, `back`) as images read from the asset's folder, each labelled with its face name and file name. A face whose file is absent or cannot be read SHALL show a placeholder, not an error. Other asset types SHALL NOT show the section.

#### Scenario: Skybox previews
- **WHEN** a skybox whose six faces name existing image files is selected
- **THEN** the panel shows six labelled images below the table

#### Scenario: Missing face file
- **WHEN** a skybox's `left` names a file that is not in the asset folder
- **THEN** the `left` cell shows a placeholder with its label and the other faces still show their images

#### Scenario: Not a skybox
- **WHEN** a model or terrain asset is selected
- **THEN** no "Face previews" section is shown

### Requirement: Read-only first stage

The panel SHALL NOT modify any file in this stage; values are displayed only.

#### Scenario: Values are not editable
- **WHEN** the user double-clicks a value cell
- **THEN** no editor opens and no file changes

### Requirement: Live refresh

The panel SHALL refresh when the selected asset's `meta.json` changes in the editor or on disk.

#### Scenario: Edit the meta text
- **WHEN** the user changes `additional.size` in an open `meta.json` while its asset is selected
- **THEN** the panel shows the new value without reselecting the node
