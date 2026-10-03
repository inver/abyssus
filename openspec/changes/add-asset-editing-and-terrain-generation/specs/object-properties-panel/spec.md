# Spec Delta

## MODIFIED Requirements

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

### Requirement: Read-only first stage

Showing an asset SHALL NOT modify any file. Supported terrain, cube skybox and procedural sky properties SHALL be editable only through explicit user edits. Asset identity and bookkeeping fields and unsupported properties SHALL remain read-only. Entity component editing SHALL continue unchanged.

#### Scenario: Values are not editable
- **WHEN** the user double-clicks the `uuid` or an unsupported metadata value
- **THEN** no editor opens and no file changes

#### Scenario: Selection alone writes nothing
- **WHEN** the user selects a terrain asset or opens its generation controls
- **THEN** every project file remains byte-for-byte unchanged

## ADDED Requirements

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
