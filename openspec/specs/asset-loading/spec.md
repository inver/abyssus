# asset-loading Specification

## Purpose

Lets the scene view, its tests and any other libGDX tool turn a Mundus project's asset folders into drawable models,
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
