# abyssus-scene-skybox Specification

## Purpose

Defines how the Abyssus view presents a scene's skybox and lets the user choose it from the skybox assets of the
scene's project, writing the choice back to the scene file.

## Requirements

### Requirement: Skybox row label

The Abyssus view SHALL label a scene's `skyboxName` property as `skybox`, shown as `skybox: <value>`. The scene file
SHALL keep the `skyboxName` key; only the displayed name changes. The row SHALL keep its skybox icon and its eye toggle
for `skyboxEnabled`.

#### Scenario: Null skybox is labeled skybox

- **WHEN** a user expands a scene whose `skyboxName` is `null`
- **THEN** the row reads `skybox: null` and no row reads `skyboxName`

#### Scenario: Named skybox is labeled skybox

- **WHEN** a scene has `"skyboxName": "skybox_default"` and `"skyboxEnabled": true`
- **THEN** the row reads `skybox: skybox_default` and shows the visible eye icon

#### Scenario: File key is unchanged

- **WHEN** the user toggles the skybox eye
- **THEN** the scene file still holds the `skyboxName` key and no `skybox` key is added

### Requirement: Skybox chooser button

The skybox row of a scene that belongs to a `.abss` project SHALL show a clickable `Choose` button (a thin rounded outline with a swap icon and the label "Choose") at the right edge of the
row, to the left of the eye icon. A scene outside any project SHALL NOT show the `Choose` button. Clicking the eye SHALL keep
flipping `skyboxEnabled`; clicking `Choose` SHALL NOT.

#### Scenario: Project scene shows the button and the eye

- **WHEN** a user expands a scene listed under a `.abss` project
- **THEN** its `skybox` row shows the `Choose` button and, to its right, the eye icon, each with a hand cursor on hover

#### Scenario: Standalone scene has no chooser

- **WHEN** a user expands a `.scene` file that is not in a project's `scenes` folder
- **THEN** its `skybox` row shows the eye icon but no `Choose` button

### Requirement: Skybox chooser dialog

Clicking `Choose` SHALL open a modal dialog titled "Choose a skybox" whose header shows how many skyboxes the current filter
matches ("N found"). Its list SHALL hold a "None" entry, described as "clear the field", followed by every asset folder
of the scene's project whose `meta.json` type is `SKYBOX`, `SKYBOX_PROCEDURAL` or `SKYBOX_HDR`, by folder name in
alphabetical order.

#### Scenario: Lists the project's skyboxes

- **WHEN** the user clicks `Choose` on a scene of the `Untitled` test project
- **THEN** the dialog is titled "Choose a skybox", reads "3 found", and lists "None", `skybox_default`, `skybox_hdr`
  and `skybox_physical`, and no model, terrain, texture, material or shader asset

#### Scenario: Project without skyboxes

- **WHEN** the scene's project has no `SKYBOX`, `SKYBOX_PROCEDURAL` or `SKYBOX_HDR` asset
- **THEN** the dialog reads "0 found" and lists only "None"

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

### Requirement: Filtering the skybox list

The dialog SHALL have a "Filter by name" text field. Typing SHALL narrow the skybox entries to folder names containing
the text, case-insensitively, and update the "N found" count. "None" SHALL always stay listed. When no skybox matches,
the dialog SHALL show "No asset of this type matches the filter."

#### Scenario: Filter narrows the list

- **WHEN** the project has skyboxes `nebula`, `dusk` and `abyss-night` and the user types `NIGHT`
- **THEN** the list shows "None" and `abyss-night`, and the header reads "1 found"

#### Scenario: Nothing matches

- **WHEN** the user types a filter no skybox folder contains
- **THEN** only "None" is listed, the header reads "0 found", and the no-match message is shown

### Requirement: Skybox selection and footer

The scene's current skybox SHALL be preselected when the dialog opens, or "None" when it is `null` or names no listed
skybox. Clicking an entry SHALL select it and highlight it. The footer SHALL show "Selected: <name>", or "Selected: none"
for "None", followed by **Cancel** and **Assign** buttons.

#### Scenario: Current skybox is preselected

- **WHEN** the scene's `skyboxName` is `skybox_default` and the user opens the dialog
- **THEN** `skybox_default` is selected and the footer reads "Selected: skybox_default"; with `skyboxName` null, "None"
  is selected and the footer reads "Selected: none"

#### Scenario: Selecting does not write

- **WHEN** the user clicks a different skybox entry
- **THEN** that entry is highlighted, the footer names it, and the scene file is not changed

### Requirement: Applying the chosen skybox

**Assign** SHALL set `skyboxName` in the scene's own `.scene` file to the selected folder name, or to `null` for "None",
change nothing else in the file, keep its formatting, be undoable, close the dialog and refresh the view, including the
unused marks. **Cancel**, closing the dialog, or assigning the current value SHALL leave the file untouched.
`skyboxEnabled` SHALL NOT be changed.

#### Scenario: Assigning a skybox writes the scene file

- **WHEN** a scene has `"skyboxName": null` and the user selects `skybox_default` and presses Assign
- **THEN** the scene file holds `"skyboxName": "skybox_default"`, every other value is unchanged, and the row reads
  `skybox: skybox_default`

#### Scenario: Assigning clears the unused mark

- **WHEN** `skybox_default` is marked unused and the user assigns it to a scene of the project
- **THEN** after the view refreshes, `skybox_default` under the project's assets is no longer marked unused

#### Scenario: Assigning None clears the skybox

- **WHEN** a scene has `"skyboxName": "skybox_default"` and the user selects "None" and presses Assign
- **THEN** the scene file holds `"skyboxName": null`

#### Scenario: Cancel leaves the file alone

- **WHEN** the user opens the dialog, selects another skybox, and presses Cancel
- **THEN** the scene file's content and modification stamp are unchanged

#### Scenario: Enabled flag is preserved

- **WHEN** a scene has `"skyboxEnabled": false` and the user assigns a skybox
- **THEN** `skyboxEnabled` stays false and the row stays grayed with the hidden eye
