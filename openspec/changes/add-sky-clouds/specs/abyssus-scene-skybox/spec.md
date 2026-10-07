# Spec Delta

## MODIFIED Requirements

### Requirement: Skybox list entries

Each skybox entry SHALL show the icon of its asset type, the folder name, and a detail line: for a `SKYBOX`, the number
of face files named in its `meta.json` `additional` and their file extensions (for example `6 faces · png`); for a
`SKYBOX_PROCEDURAL`, the words `procedural sky`, followed by ` · clouds` when its `additional.clouds` names a cloud
asset; for a `SKYBOX_HDR`, `HDR` followed by the image size read from the
`.hdr` header (for example `HDR · 64 × 32`), or `HDR` alone when the header cannot be read. In place of the six face
thumbnails, an `SKYBOX_HDR` entry SHALL show one tone-mapped thumbnail of its image. It SHALL show an "unused" badge
when the Abyssus view marks the asset unused, and otherwise "used by N scene" / "used by N scenes", N being how many of
the project's scenes reference it.

#### Scenario: Detail line from the meta file

- **WHEN** the dialog lists `skybox_default`, whose six faces are all `skybox_default.png`
- **THEN** its detail line reads `6 faces · png`

#### Scenario: Procedural detail line

- **WHEN** the dialog lists `skybox_physical`
- **THEN** its detail line reads `procedural sky`

#### Scenario: Procedural sky with clouds

- **WHEN** the dialog lists a `SKYBOX_PROCEDURAL` asset whose `additional.clouds` holds a cloud asset's `uuid`
- **THEN** its detail line reads `procedural sky · clouds`

#### Scenario: HDR detail line

- **WHEN** the dialog lists `skybox_hdr`, whose `sky.hdr` is 64 x 32
- **THEN** its entry shows the HDR skybox icon, the detail line `HDR · 64 × 32`, and one thumbnail

#### Scenario: HDR header unreadable

- **WHEN** the dialog lists an `SKYBOX_HDR` asset whose `.hdr` is not a Radiance image
- **THEN** its detail line reads `HDR` and its thumbnail is a placeholder

#### Scenario: Mixed face formats

- **WHEN** a skybox's faces are four `.png` and two `.jpg` files
- **THEN** its detail line reads `6 faces · jpg, png`

#### Scenario: Unused skybox shows the badge

- **WHEN** no scene of the project references `skybox_default`
- **THEN** its entry shows the "unused" badge, matching the unused mark in the Abyssus view

#### Scenario: Used skybox shows its scene count

- **WHEN** two scenes of the project have `"skyboxName": "nebula"`
- **THEN** the `nebula` entry reads "used by 2 scenes" and shows no badge; with one scene it reads "used by 1 scene"
