# Spec Delta

## MODIFIED Requirements

### Requirement: Skybox chooser dialog

Clicking `Choose` SHALL open a modal dialog titled "Choose a skybox" whose header shows how many skyboxes the current filter
matches ("N found"). Its list SHALL hold a "None" entry, described as "clear the field", followed by every asset folder
of the scene's project whose `meta.json` type is `SKYBOX` or `SKYBOX_PROCEDURAL`, by folder name in alphabetical order.

#### Scenario: Lists the project's skyboxes

- **WHEN** the user clicks `Choose` on a scene of the `Untitled` test project
- **THEN** the dialog is titled "Choose a skybox", reads "2 found", and lists "None", `skybox_default` and
  `skybox_physical`, and no model, terrain, texture, material or shader asset

#### Scenario: Project without skyboxes

- **WHEN** the scene's project has no `SKYBOX` or `SKYBOX_PROCEDURAL` asset
- **THEN** the dialog reads "0 found" and lists only "None"

### Requirement: Skybox list entries

Each skybox entry SHALL show the skybox icon, the folder name, and a detail line: for a `SKYBOX`, the number of face
files named in its `meta.json` `additional` and their file extensions (for example `6 faces · png`); for a
`SKYBOX_PROCEDURAL`, the words `procedural sky`. It SHALL show an "unused" badge when the Abyssus view marks the asset
unused, and otherwise "used by N scene" / "used by N scenes", N being how many of the project's scenes reference it.

#### Scenario: Detail line from the meta file

- **WHEN** the dialog lists `skybox_default`, whose six faces are all `skybox_default.png`
- **THEN** its detail line reads `6 faces · png`

#### Scenario: Procedural detail line

- **WHEN** the dialog lists `skybox_physical`
- **THEN** its detail line reads `procedural sky`

#### Scenario: Mixed face formats

- **WHEN** a skybox's faces are four `.png` and two `.jpg` files
- **THEN** its detail line reads `6 faces · jpg, png`

#### Scenario: Unused skybox shows the badge

- **WHEN** no scene of the project references `skybox_default`
- **THEN** its entry shows the "unused" badge, matching the unused mark in the Abyssus view

#### Scenario: Used skybox shows its scene count

- **WHEN** two scenes of the project have `"skyboxName": "nebula"`
- **THEN** the `nebula` entry reads "used by 2 scenes" and shows no badge; with one scene it reads "used by 1 scene"
