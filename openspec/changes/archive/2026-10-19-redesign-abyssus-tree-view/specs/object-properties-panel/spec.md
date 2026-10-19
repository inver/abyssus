## ADDED Requirements

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
