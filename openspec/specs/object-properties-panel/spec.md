# object-properties-panel Specification

## Purpose

Lets users inspect an asset chosen in the Abyssus view by listing the properties of its Meta in a dedicated
panel, so they can be read without opening `meta.json`.

## Requirements

### Requirement: Properties tool window

The plugin SHALL provide an `Abyssus Properties` tool window, available without configuration,
that shows the selected node's header and sections of labelled fields. Its title bar SHALL offer Options and Hide.

#### Scenario: Tool window is available
- **WHEN** a user opens a project where the plugin is installed
- **THEN** an `Abyssus Properties` tool window can be opened from the tool window bar

#### Scenario: Title bar actions
- **WHEN** the tool window is shown
- **THEN** its title bar offers Options and Hide, in that order

### Requirement: Panel follows the asset selection

The panel SHALL show the Meta of the asset selected in the Abyssus view and SHALL update when the selection changes. For a selected entity or one of its components it SHALL show that entity's components or that component's fields instead (see "Entity and component properties"). For a selected project file it SHALL show the project's properties instead (see "Project properties"). A selected water surface SHALL show editable water properties (see "Water properties").

#### Scenario: Select an asset
- **WHEN** the user selects an asset row in the Abyssus view
- **THEN** the panel lists the properties of that asset's `meta.json`

#### Scenario: Nothing selected
- **WHEN** nothing is selected
- **THEN** the panel shows `Nothing selected.` and a hint to select a skybox, model or terrain under Assets to see its `meta.json`

#### Scenario: Selection is not an asset
- **WHEN** the selected node is not an asset, an entity, a component, a project file or a water surface (for example, a setting)
- **THEN** the panel shows `Nothing to show: <name> is <what it is>.` and the same hint

#### Scenario: Scene selection retains its properties
- **WHEN** the user selects a scene node in the Abyssus view
- **THEN** the panel keeps showing the scene's existing properties, including its ray tracing preferences

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

### Requirement: Asset and component fields edit the same way
An editable field in the Abyssus Properties panel SHALL behave the same whether it belongs to an asset's `meta.json` or
to an entity's component. A text field SHALL save when the user presses Enter or moves focus away. A choice field
SHALL save when the user picks a value. A value equal to the current one SHALL write nothing. A refused value SHALL put
the previous value back in the field and show the reason beside it, with nothing written. An accepted value SHALL clear
any reason shown beside that field.

#### Scenario: Confirm an asset field by leaving it
- **WHEN** the user selects the `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` asset of the `Untitled` project, types
  `30` into `uv` and moves focus to another field
- **THEN** the `meta.json` holds `30.0` for `uv` and the field shows `30.0`

#### Scenario: Confirm a component field by leaving it
- **WHEN** the user selects `Spot Light 8` in `Main Scene` of the `Untitled` project, types `2` into the light's
  `intensity` and moves focus to another field
- **THEN** the scene file holds `2` for that light's `intensity` and the field shows `2`

#### Scenario: Refused values look the same in both panels
- **WHEN** the user enters `abc` into `uv` of that terrain, and then into `intensity` of `Spot Light 8`, pressing Enter
  each time
- **THEN** each field shows its previous value again (`60.0` and `1`), a reason appears beside each, and neither file
  changes

#### Scenario: Equal value writes nothing
- **WHEN** the user presses Enter in `intensity` of `Spot Light 8` without changing `1`
- **THEN** the scene file is not written and no Undo entry is added

#### Scenario: Light field labels are unchanged
- **WHEN** the user selects `Spot Light 8`
- **THEN** its range, cone angle and edge softness fields show the same labels as before this change, and the range
  field shows the same tooltip

### Requirement: Project view settings

The project properties view (see "Project properties") SHALL also show a `Show FPS` checkbox reflecting the saved preference defined by `scene-fps-display`. The checkbox SHALL be usable with no scene open. The view SHALL explain that these are IDE view settings, and changing the checkbox SHALL leave `.abss`, `.scene` and asset files unchanged.

#### Scenario: New project preference
- **WHEN** `Untitled.abss` is selected in an IDE project with no saved FPS preference
- **THEN** `Show FPS` is unchecked

#### Scenario: Toggle before opening a scene
- **WHEN** the user checks `Show FPS` with no scene view open and then opens `Main Scene`
- **THEN** the scene view shows the FPS value and the project, scene and asset files are unchanged

#### Scenario: Reselect the project
- **WHEN** the user checks `Show FPS`, selects an asset and then reselects `Untitled.abss`
- **THEN** `Show FPS` remains checked

### Requirement: Water properties

Selecting a water surface SHALL show its name and Abyssus-only status, position including water level, width, length, tint, clarity, wave amplitude, wavelength and speed, and foam amount and width. Confirmed valid edits SHALL refresh the scene; invalid edits SHALL restore the previous value with a reason and leave the file unchanged.

#### Scenario: Change water level
- **WHEN** a user sets a selected Lake's water level to 3
- **THEN** only its `position.y` changes to 3 and the shoreline and rendering refresh

#### Scenario: Invalid extent
- **WHEN** the user sets width to zero or enters a non-finite number
- **THEN** the edit is rejected, the previous width remains and a reason appears beside the field

#### Scenario: Text edits refresh properties
- **WHEN** a selected surface's clarity changes in the scene text
- **THEN** its property editor shows the new clarity without reselecting
#### Scenario: Removed surface

- **WHEN** the selected surface is deleted elsewhere
- **THEN** the panel shows that it no longer exists rather than editing a different surface

### Requirement: Scene skybox field

For a selected scene that belongs to a `.abss` project, Abyssus Properties SHALL show its skybox as a field holding the
current `skyboxName` (or `none` when null) with a Browse button at its right edge. The Browse button SHALL carry a tooltip
and an accessible name, and SHALL open the skybox chooser of the `abyssus-scene-skybox` capability. Assigning from the
chooser SHALL update the field and the tree's `skybox` row. A scene outside any project SHALL show the field without a
Browse button.

#### Scenario: Browse opens the chooser

- **WHEN** the user selects `Main Scene` of a copy of `Untitled` and presses Browse on the skybox field
- **THEN** the "Choose a skybox" dialog opens with the scene's current skybox preselected

#### Scenario: Assignment is reflected

- **WHEN** the user assigns `skybox_default` from that dialog
- **THEN** the field reads `skybox_default`, the tree row reads `skybox: skybox_default`, and Undo restores the previous value in both

#### Scenario: Standalone scene

- **WHEN** the user selects a `.scene` file outside a project
- **THEN** the skybox field is shown without a Browse button

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
