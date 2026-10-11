# object-properties-panel Specification

## Purpose

Lets users inspect an asset chosen in the Abyssus view by listing the properties of its Meta in a dedicated
panel, so they can be read without opening `meta.json`.

## Requirements

### Requirement: Properties tool window

The plugin SHALL provide an `Abyssus Properties` tool window, available without configuration,
that shows a Name / Value table for the selected asset.

#### Scenario: Tool window is available
- **WHEN** a user opens a project where the plugin is installed
- **THEN** an `Abyssus Properties` tool window can be opened from the tool window bar

### Requirement: Panel follows the asset selection

The panel SHALL show the Meta of the asset selected in the Abyssus view and SHALL update when the selection changes. For a selected entity or one of its components it SHALL show that entity's components or that component's fields instead (see "Entity and component properties"). For a selected project file it SHALL show the project's properties instead (see "Project properties").

#### Scenario: Select an asset
- **WHEN** the user selects an asset row in the Abyssus view
- **THEN** the panel lists the properties of that asset's `meta.json`

#### Scenario: Nothing selected
- **WHEN** nothing is selected
- **THEN** the panel shows `Nothing selected.` and a hint to select a skybox, model or terrain under Assets to see its `meta.json`

#### Scenario: Selection is not an asset
- **WHEN** the selected node is not an asset, an entity, a component or a project file (for example, a setting)
- **THEN** the panel shows `Nothing to show: <name> is <what it is>.` and the same hint

#### Scenario: Scene selection retains its properties
- **WHEN** the user selects a scene node in the Abyssus view
- **THEN** the panel keeps showing the scene's existing properties, including its ray tracing preferences

### Requirement: Asset header

Above the table the panel SHALL show an icon for the asset's type (skybox, HDR skybox, model, terrain, or a generic icon for any other type), the asset's name and the line `<type> asset`. Assets without supported editors SHALL retain the `read-only` suffix. Assets with supported editors SHALL show a note that asset edits affect every instance using the asset.

#### Scenario: Skybox header
- **WHEN** the skybox asset `nebula` is selected
- **THEN** the header shows the skybox icon, `nebula`, `skybox asset` and the shared-asset note

#### Scenario: HDR skybox header
- **WHEN** the asset `skybox_hdr` is selected
- **THEN** the header shows the HDR skybox icon, `skybox_hdr` and `skybox_hdr asset · read-only`

#### Scenario: Unknown type
- **WHEN** the selected asset's type has no dedicated icon
- **THEN** the header shows a generic asset icon and the type text from `meta.json` with the read-only suffix

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

For a `SKYBOX` asset the panel SHALL show a "Face previews" section with the six faces (`top`, `bottom`, `left`, `right`, `front`, `back`) as images read from the asset's folder, each labelled with its face name and file name. A face whose file is absent or cannot be read SHALL show a placeholder, not an error. Other asset types, including `SKYBOX_HDR`, SHALL NOT show the section.

#### Scenario: Skybox previews
- **WHEN** a skybox whose six faces name existing image files is selected
- **THEN** the panel shows six labelled images below the table

#### Scenario: Missing face file
- **WHEN** a skybox's `left` names a file that is not in the asset folder
- **THEN** the `left` cell shows a placeholder with its label and the other faces still show their images

#### Scenario: Not a skybox
- **WHEN** a model, terrain or `SKYBOX_HDR` asset is selected
- **THEN** no "Face previews" section is shown

### Requirement: Read-only first stage

Showing an asset SHALL NOT modify any file. Supported terrain, cube skybox and procedural sky properties SHALL be editable only through explicit user edits. Asset identity and bookkeeping fields and unsupported properties SHALL remain read-only. Entity component editing SHALL continue unchanged.

#### Scenario: Values are not editable
- **WHEN** the user double-clicks the `uuid` or an unsupported metadata value
- **THEN** no editor opens and no file changes

#### Scenario: Selection alone writes nothing
- **WHEN** the user selects a terrain asset or opens its generation controls
- **THEN** every project file remains byte-for-byte unchanged

### Requirement: Live refresh

The panel SHALL refresh when the selected asset's `meta.json` changes in the editor or on disk.

#### Scenario: Edit the meta text
- **WHEN** the user changes `additional.size` in an open `meta.json` while its asset is selected
- **THEN** the panel shows the new value without reselecting the node

### Requirement: Entity and component properties

When an entity of a scene is selected the panel SHALL show a header with its name and id and one section per component it has; when a component is selected it SHALL show only that component's section. A modeled component SHALL list its fields with editors suited to the value (number, text, choice, color, entity reference, asset reference); a schema-declared component SHALL list its fields the same way, also with editors for true/false and 3D vectors, under the field labels and in the groups its schema declares; an unmodeled component, including one whose schema is unknown, SHALL be shown as read-only JSON text with a note saying the plugin does not edit it.

#### Scenario: Entity selected

- **WHEN** entity `0` (`Model 0`) of `Main Scene` is selected
- **THEN** the header shows `Model 0` and `0`, and sections show its name, type, position and render components with their values

#### Scenario: Component selected

- **WHEN** the `Light` row of an entity is selected
- **THEN** the panel shows the light's color and intensity, any supported range field, cone angle and edge softness when the entity is a spotlight

#### Scenario: Unmodeled component

- **WHEN** a `PickableComponent` is selected
- **THEN** its JSON is shown without editors and with a note that it is not edited by the plugin

#### Scenario: Schema-declared component selected

- **WHEN** the `PlaneComponent` row of entity `0` of the `Custom` scene is selected
- **THEN** the panel shows its fields under their declared labels, grouped as declared (`lineLength` under `Lines`),
  with a choice editor for `kind`, a checkbox for `hasTipWeight` and x / y / z editors for `leadout`

### Requirement: Edit components in the panel

Changing a field in the panel SHALL update that component in the scene file as the `scene-component-editing` capability defines; a rejected value SHALL leave the previous value shown with the reason beside the field. The panel SHALL offer an "Add component" choice listing the modeled and schema-declared kinds the entity lacks and a "Remove" action on each modeled or schema-declared component's section.

#### Scenario: Change a value

- **WHEN** the user sets `intensity` of a selected light to `2` and confirms
- **THEN** the scene file holds `2` for it and the panel shows `2`

#### Scenario: Invalid value

- **WHEN** the user enters `abc` for a number field
- **THEN** the field returns to its previous value, a message appears beside it and the file is unchanged

#### Scenario: Add from the panel

- **WHEN** the user chooses Add component > Light on an entity without one
- **THEN** a light section with the default values appears and the file holds the new component

#### Scenario: Remove from the panel

- **WHEN** the user chooses Remove on the Light section
- **THEN** the section disappears and the component is gone from the file

#### Scenario: Add a schema-declared component from the panel

- **WHEN** the user chooses Add component > Plane on entity `1` of the `Custom` scene
- **THEN** a Plane section with the declared defaults appears and the file holds `"PlaneComponent": {}` for entity `1`

### Requirement: Entity panel follows the file

The entity and component views SHALL refresh when the scene file changes in an editor, on disk or by an edit made elsewhere in the plugin, and SHALL show a message when the selected entity or component no longer exists.

#### Scenario: Edit the scene text

- **WHEN** the user changes a light's `intensity` in the open `.scene` text while its entity is selected
- **THEN** the panel shows the new value without reselecting

#### Scenario: Component removed elsewhere

- **WHEN** the selected component is deleted in the text editor
- **THEN** the panel shows a message that it no longer exists

### Requirement: HDR skybox preview

For an `SKYBOX_HDR` asset the panel SHALL show a "Preview" section with the image chosen as described by the
`scene-hdr-skybox` capability, tone-mapped as the scene view draws it and scaled to the panel's width, labelled with its
file name and size. An image that is missing or cannot be read SHALL show a placeholder labelled with the reason, not an
error. The image SHALL be decoded off the UI thread.

#### Scenario: HDR preview
- **WHEN** `skybox_hdr` is selected
- **THEN** the panel shows a "Preview" section with one image labelled `sky.hdr · 64 × 32`

#### Scenario: Unreadable HDR
- **WHEN** an `SKYBOX_HDR` asset whose `.hdr` is truncated is selected
- **THEN** the "Preview" section shows a placeholder with the reason, and the Meta rows are still shown

### Requirement: Spotlight beam controls

For a selected spotlight entity or its light component, the panel SHALL expose editable Cone angle in degrees and Edge softness in percent. Cone angle SHALL mean the full cone width. These controls SHALL NOT appear for point or directional lights, and SHALL refresh after scene edits or Undo.

#### Scenario: Spotlight selected
- **WHEN** a spotlight or its Light component is selected
- **THEN** Cone angle and Edge softness controls show its effective values with their units

#### Scenario: Point light selected
- **WHEN** a point or directional light is selected
- **THEN** the panel does not offer spotlight cone or softness controls

#### Scenario: External scene edit
- **WHEN** the saved beam settings change in the scene text while the spotlight is selected
- **THEN** the controls and displayed beam refresh without reselecting it

### Requirement: Typed terrain properties

Terrain assets SHALL expose editors for positive integer `additional.size`, positive finite `additional.uv`, and nullable `splatMap`, `splatBase`, `splatR`, `splatG`, `splatB`, `splatA` texture references. Texture choices SHALL show folder names but store the chosen texture's UUID. Only readable texture assets with UUIDs SHALL be offered as new choices; existing unresolved references SHALL remain visible.

#### Scenario: Change world size
- **WHEN** the user changes size from `1600` to `800` on Untitled's `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b`
- **THEN** `additional.size` becomes `800`, the height file is unchanged and displayed terrain spans the new size

#### Scenario: Assign a texture
- **WHEN** the user selects a valid texture for `splatBase`
- **THEN** that texture's UUID is saved, its folder name is displayed, and choosing None saves null

### Requirement: Typed sky properties

Cube skyboxes SHALL expose folder-local image choices for `top`, `bottom`, `left`, `right`, `front`, `back`. Procedural skies SHALL expose the existing atmosphere parameters with effective defaults for omitted fields. Choosing an image SHALL update the face preview. File paths outside the asset folder SHALL be rejected.

#### Scenario: Choose a cube face
- **WHEN** the user assigns another readable local image to `skybox_default`'s `left`
- **THEN** only `additional.left` changes and its preview shows the chosen image

#### Scenario: Edit an omitted atmosphere field
- **WHEN** the user changes an omitted procedural sky `sunIntensity` from its displayed default to `25`
- **THEN** `additional.sunIntensity` becomes `25` and other omitted fields stay omitted

### Requirement: Validate asset properties

Rejected edits SHALL retain the previous value and show a reason beside the field without writing files. Atmosphere radii and scale heights SHALL be positive finite values, atmosphereRadius SHALL exceed planetRadius, scattering coefficients and sunIntensity SHALL be nonnegative finite values, betaRayleigh SHALL have three coefficients, and mieG SHALL be strictly between -1 and 1.

#### Scenario: Invalid size
- **WHEN** the user enters `0`, `abc`, or a fraction for terrain size
- **THEN** the previous value is retained, a validation reason is shown and the metadata is unchanged

#### Scenario: Invalid atmosphere combination
- **WHEN** the user sets atmosphereRadius less than or equal to planetRadius
- **THEN** the edit is rejected without changing the file

### Requirement: Preserve and undo asset metadata edits

Each accepted asset property edit SHALL be one undoable command preserving unrelated keys, key order, number text, formatting, identity and bookkeeping fields. Undo and Redo from the properties panel SHALL restore and reapply the edit. Equal-value edits SHALL write nothing. Edits based on superseded values SHALL be rejected and refreshed rather than overwriting newer changes.

#### Scenario: Undo texture repetition
- **WHEN** the user changes Untitled's terrain `uv` from `60.0` to `30` and invokes Undo
- **THEN** the original metadata text is restored, including `60.0`, and the panel and scene return to the previous repetition

#### Scenario: Concurrent metadata change
- **WHEN** a field changes externally after its editor was populated and the user commits the old editor's value
- **THEN** the newer value remains saved and the panel explains and refreshes the conflict

### Requirement: Project properties

Selecting a project's `.abss` node in the Abyssus view SHALL show the project's name and a Physics checkbox reflecting
`physicsEnabled`. Toggling the checkbox SHALL write the switch to the `.abss` file as one undoable edit. The panel SHALL
follow edits made to the `.abss` elsewhere, and SHALL show the switch read-only for an `.abss` file it does not support.

#### Scenario: Physics off by default
- **WHEN** the user selects `Untitled.abss` in the Abyssus view
- **THEN** the panel shows project `Untitled` with the Physics checkbox cleared

#### Scenario: Turn physics on and undo
- **WHEN** the user ticks Physics for a copy of `Untitled` and then uses Undo
- **THEN** `Untitled.abss` first gains `"physicsEnabled": true`, then is restored to its original text, and the
  checkbox follows both changes

#### Scenario: Physics fixture
- **WHEN** the user selects `Physics.abss`
- **THEN** the Physics checkbox is ticked

#### Scenario: Edit the project text
- **WHEN** `"physicsEnabled": true` is typed into the `.abss` in the text editor while the project is selected
- **THEN** the checkbox becomes ticked without reselecting

#### Scenario: Unsupported project
- **WHEN** the selected `.abss` has unsupported or missing native format markers and `physicsEnabled: true`
- **THEN** physics remains off, the panel explains the unsupported format and the checkbox cannot write the file
