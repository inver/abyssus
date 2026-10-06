# asset-loading Specification

## Purpose

Lets the scene view, its tests and any other libGDX tool turn a native Abyssus project's asset folders into drawable models,
terrains and skies the same way, without needing a running IDE.

## Requirements

### Requirement: Assets load without the IDE

Loading a project's assets SHALL work the same inside the IDE and in a program or test that runs no IDE: given the
project folder and a GL context for the GPU steps, every model, terrain and sky asset the project holds SHALL load
into a drawable object, reading its `meta.json` and the files it names exactly as the scene view does.

#### Scenario: Main Scene's content outside the IDE

- **WHEN** a test with no IDE running loads the assets that `Main Scene` of the `Untitled` project places
- **THEN** the models of `Model 0`, `Model 2` and `Model 6` (`model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`,
  `model_fc33e1f1-015b-4524-9b10-aa417acd273c`, `model_900f6f61-6384-434a-be81-56ce303fbb56`) and the terrain
  `terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b` all load, and the scene view drawn from them shows three models and
  one terrain

#### Scenario: Every sky kind outside the IDE

- **WHEN** a test with no IDE running loads `skybox_default`, `skybox_physical` and `skybox_hdr` of the `Untitled`
  project
- **THEN** each loads into a sky that can be drawn: a six-face cube, a procedural sky and an HDR sky

#### Scenario: Same result inside the IDE

- **WHEN** the scene view opens `Main Scene` in the IDE
- **THEN** the same assets load and the view looks as it did before this change

### Requirement: Load problems go to the caller's log

A problem loading an asset SHALL be reported once, naming the asset, to the log that whoever started the loading
provided, and SHALL NOT stop the other assets from loading. In the IDE that log SHALL be the IDE log, as before.

#### Scenario: Missing asset folder

- **WHEN** a scene names an asset folder that does not exist and the loading was started with a test's own log
- **THEN** that log receives one message naming the folder, and every other asset of the scene loads

#### Scenario: Broken asset reported once

- **WHEN** an asset's file is corrupt and the scene view keeps drawing frames
- **THEN** the problem is reported once, not once per frame

### Requirement: Independent loaders share nothing

Two loaders started for two projects SHALL keep separate caches, separate logs and separate settings: what one loads,
reports or releases SHALL NOT affect the other.

#### Scenario: Two projects at once

- **WHEN** one loader loads `Main Scene` of `Untitled` and another loads `Main.scene` of `Animated` at the same time
- **THEN** each loads its own project's assets, each log receives only its own project's problems, and releasing one
  leaves the other's loaded assets usable

### Requirement: Changed assets reload

Changed metadata and asset files SHALL refresh affected assets in open scene views without reopening them, including Undo and Redo. Changes to a referenced texture SHALL refresh dependent terrains. Unrelated assets and other projects SHALL remain usable. Refresh SHALL use unsaved metadata text when available, matching the properties panel.

#### Scenario: Terrain regeneration
- **WHEN** new heights are applied to Untitled's terrain asset
- **THEN** every open scene using it displays the new surface and terrain picking uses the new heights

#### Scenario: Referenced texture changed
- **WHEN** a texture referenced by terrain metadata changes or is replaced
- **THEN** the terrain displays the new texture without reopening the scene

#### Scenario: Unsaved metadata edit
- **WHEN** the user changes terrain size in its open metadata editor without saving
- **THEN** the properties panel and open scene use the edited size, and reverting the text restores the previous size

### Requirement: Refresh uses the latest asset revision

An older load finishing after a newer edit SHALL NOT replace the latest requested asset revision. Missing or broken changed assets SHALL remain isolated and be retried after a subsequent relevant change, with errors reported once per revision. Hidden scene views SHALL defer resource replacement until they can safely render.

#### Scenario: Rapid regeneration
- **WHEN** an asset load is pending and a second regeneration is applied
- **THEN** the final scene displays the second regeneration and cannot revert to the first when its load finishes

#### Scenario: Repair a terrain
- **WHEN** a previously unreadable terrain data file is replaced with valid data
- **THEN** that terrain loads without reopening the view and the other scene assets remain visible

### Requirement: Saved and unsaved metadata read the same

An asset's `meta.json` SHALL be read by one rule whether its text comes from the saved file or from an unsaved editor
buffer. The same text SHALL give the same asset type, the same typed settings and the same identifier, so editing
a `meta.json` in the text editor and saving it does not change what the asset loads as.

#### Scenario: Unsaved text matches the saved file

- **WHEN** the text of a model asset's `meta.json` in the Untitled fixture is open unsaved with no change, and the
  scene view loads the asset
- **THEN** the asset loads with the same type and settings as it does from the saved file

#### Scenario: A new asset kind needs one registration

- **WHEN** a developer adds an asset kind with its own settings block
- **THEN** one registration makes both saved and unsaved `meta.json` of that kind bind to the new settings

#### Scenario: Unreadable identifier

- **WHEN** a `meta.json` holds a `uuid` that is not a valid identifier
- **THEN** the asset still loads, with no identifier, as it does today, and nothing is written to the file
