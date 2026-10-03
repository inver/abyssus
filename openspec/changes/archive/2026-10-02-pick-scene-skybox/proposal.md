# Proposal

## Why

The Abyssus view shows a scene's skybox as the raw JSON key `skyboxName`, and the only way to change which skybox a
scene uses is to hand-edit the `.scene` file and type an asset folder name correctly. Picking from the skyboxes the
project actually has removes typos and makes the skybox as easy to change as toggling it with the eye.

## What Changes

- The scene's skybox row in the Abyssus view is labeled `skybox` instead of `skyboxName` (for example
  `skybox: skybox_default`, `skybox: null`). Display only: the `.scene` file keeps the `skyboxName` key, so files stay
  compatible with Mundus.
- The skybox row of a scene that belongs to a `.abss` project gets a clickable `...` icon at the right edge of its row,
  next to (left of) the existing eye icon.
- Clicking `...` opens a "Choose a skybox" dialog laid out after the "Choose asset for scene" board of the Abyssus Panel
  Design (https://claude.ai/artifact/FCGmbq4BAJW9PK6uvBDfke): a header with "N found", a "Filter by name" field, and a
  list with "None" ("clear the field") first, then every skybox asset of the project (asset folders whose `meta.json`
  type is `SKYBOX`). Each skybox shows its icon, folder name, a detail line such as `6 faces · png`, and either an
  **unused** badge or "used by N scenes". The scene's current skybox is preselected. A footer shows "Selected: …" with
  **Cancel** and **Assign**.
- Nothing is written until **Assign**: it writes the chosen folder name (or `null` for "None") to the scene file's
  `skyboxName`, changing nothing else in the file, and the view refreshes, including the unused marks. Cancelling
  changes nothing. `skyboxEnabled` is not touched.
- The design's Scene properties panel, its "Choose…" button and the `postShader` field are not part of this change; the
  dialog opens from the tree's `...` icon.
- The Abyssus view's row-icon mechanism grows from one icon per row to several, so a row can carry both the eye and `...`.

## Capabilities

### New Capabilities
- `abyssus-scene-skybox`: How the Abyssus view presents a scene's skybox and lets the user choose it from the project's
  skybox assets.

### Modified Capabilities
<!-- `abyssus-project-view` is still an in-flight change (add-abyssus-project-view), not a main spec under
     openspec/specs/, so it cannot take a delta here. Its "Asset node structure" requirement says the eye and Rename
     Scene are the only actions that write to an asset file, and its "Null values are shown" scenario names the
     `skyboxName` node; tasks.md includes reconciling that spec text. -->

## Impact

- `projectView/AbyssusNodes.kt` (row label), `projectView/AbyssusProjectViewPane.kt` (multiple row icons),
  `projectView/EnabledToggle.kt` (writing `skyboxName`), a new skybox chooser dialog and its list model in `projectView/`,
  `AbyssusBundle` messages.
- Tests: `AbyssusViewTest` expectations that read `skyboxName: null` change to `skybox: null`; new tests for the skybox
  list and the file edit.
- No change to `SceneDto`, `SceneContent`, rendering, or the scene file format.
