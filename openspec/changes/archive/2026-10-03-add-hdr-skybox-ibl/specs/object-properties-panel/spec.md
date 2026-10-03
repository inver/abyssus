# Spec Delta

## MODIFIED Requirements

### Requirement: Asset header

Above the table the panel SHALL show an icon for the asset's type (skybox, HDR skybox, model, terrain, or a generic icon for any other type), the asset's name and the line `<type> asset · read-only`.

#### Scenario: Skybox header
- **WHEN** the skybox asset `nebula` is selected
- **THEN** the header shows the skybox icon, `nebula` and `skybox asset · read-only`

#### Scenario: HDR skybox header
- **WHEN** the asset `skybox_hdr` is selected
- **THEN** the header shows the HDR skybox icon, `skybox_hdr` and `skybox_hdr asset · read-only`

#### Scenario: Unknown type
- **WHEN** the selected asset's type has no dedicated icon
- **THEN** the header shows a generic asset icon and the type text from `meta.json`

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

## ADDED Requirements

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
