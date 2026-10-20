## MODIFIED Requirements

### Requirement: Skybox chooser button

The skybox row of a scene SHALL NOT show a `Choose` button. The chooser SHALL be opened from the skybox field of Abyssus
Properties (see the `object-properties-panel` capability) for a scene that belongs to a `.abss` project. A scene outside any
project SHALL offer no way to open the chooser. Clicking the eye on the skybox row SHALL keep flipping `skyboxEnabled`.

#### Scenario: Project scene shows the button and the eye

- **WHEN** a user expands a scene listed under a `.abss` project
- **THEN** its `skybox` row shows the eye icon at the right edge, with a hand cursor on hover, and no `Choose` button

#### Scenario: Standalone scene has no chooser

- **WHEN** a user selects a `.scene` file that is not in a project's `scenes` folder
- **THEN** its skybox field in Abyssus Properties shows no Browse button

### Requirement: Skybox chooser dialog

Pressing the Browse button of the skybox field SHALL open a modal dialog titled "Choose a skybox" whose header shows how
many skyboxes the current filter matches ("N found"). Its list SHALL hold a "None" entry, described as "clear the field",
followed by every asset folder of the scene's project whose `meta.json` type is `SKYBOX`, `SKYBOX_PROCEDURAL` or
`SKYBOX_HDR`, by folder name in alphabetical order.

#### Scenario: Lists the project's skyboxes

- **WHEN** the user presses Browse on the skybox field of a scene of the `Untitled` test project
- **THEN** the dialog is titled "Choose a skybox", reads "3 found", and lists "None", `skybox_default`, `skybox_hdr`
  and `skybox_physical`, and no model, terrain, texture, material or shader asset

#### Scenario: Project without skyboxes

- **WHEN** the scene's project has no `SKYBOX`, `SKYBOX_PROCEDURAL` or `SKYBOX_HDR` asset
- **THEN** the dialog reads "0 found" and lists only "None"
