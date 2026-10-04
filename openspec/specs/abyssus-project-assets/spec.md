# Abyssus Project Assets Specification

## Purpose

Lets users see the assets an Abyssus project owns directly in the Abyssus view, and tell at a
glance which assets are not referenced by any scene of the project.

## Requirements

### Requirement: Project assets listing

A `.abss` project node SHALL expose an `assets` property after `scenes`, with one entry per
sub-folder of the `assets` folder beside the `.abss` file, ordered by folder name. Each entry
SHALL show the folder name and the asset `type` read from the folder's `meta.json`. A folder
without a readable `meta.json` type SHALL still be listed, with an unknown type, and SHALL NOT
break the view. Files directly inside `assets` are not assets and SHALL NOT be listed.

#### Scenario: Assets are listed under the project

- **WHEN** a user expands `Untitled.abss`, whose `assets` folder holds `skybox_default`, `tree`,
  `terrain_2cf70bf7-...` and four `model_...` folders
- **THEN** the `assets` property has seven entries in name order, each showing its type

#### Scenario: Project without an assets folder

- **WHEN** a `.abss` file has no `assets` folder beside it
- **THEN** its `assets` property is empty and the node still expands

#### Scenario: Asset with unreadable metadata

- **WHEN** an asset folder has a missing or malformed `meta.json`
- **THEN** the asset is still listed, with an unknown type, and other assets load normally

### Requirement: Unused asset marking

The view SHALL mark an asset as unused when no scene of the project reaches it. A scene
references an asset directly when its ECS data names the asset's folder in an `assetName` or
`shaderKey` field, or when its `skyboxName` equals the folder name. A used asset in turn uses the assets whose
`uuid` appears in its own `meta.json` as a terrain splat texture (`splatMap`, `splatBase`,
`splatR`, `splatG`, `splatB`, `splatA`) or as a model material (`materials`), transitively. The
marker SHALL be visible in the asset's row without expanding it.

#### Scenario: Referenced asset is not marked

- **WHEN** `Main Scene` has a render component whose `assetName` is
  `model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`
- **THEN** that asset is shown without the unused marker

#### Scenario: Unreferenced asset is marked

- **WHEN** no scene references the `tree` asset and no used asset references it
- **THEN** the `tree` entry is marked as unused

#### Scenario: Reference from any scene counts

- **WHEN** an asset is referenced only by the second of two scenes in the project
- **THEN** the asset is not marked as unused

#### Scenario: Skybox is used through skyboxName

- **WHEN** a scene sets `skyboxName` to `skybox_default`
- **THEN** the `skybox_default` asset is not marked as unused

#### Scenario: Project shader used through shaderKey

- **WHEN** a scene renderable has `shaderKey` `myShader` and the project has a `SHADER` asset in
  the folder `myShader`
- **THEN** that shader asset is not marked as unused

#### Scenario: Shader no scene names

- **WHEN** no `shaderKey` in any scene equals the folder name of a project `SHADER` asset
- **THEN** that shader asset is marked as unused

#### Scenario: Bundled shader key is ignored

- **WHEN** a scene uses `shaderKey` `pbr` and the project has no `pbr` asset folder
- **THEN** no entry or error is produced for it and other assets are unaffected

#### Scenario: Splat texture used through a terrain

- **WHEN** a scene uses a terrain asset whose `meta.json` `splatMap` holds the `uuid` of a texture
  asset that no scene names directly
- **THEN** the texture asset is not marked as unused

#### Scenario: Material used through a model

- **WHEN** a scene uses a model asset whose `materials` list holds the `uuid` of a material asset
- **THEN** the material asset is not marked as unused

#### Scenario: Referenced only by an unused asset

- **WHEN** a material asset is listed in the `materials` of a model that nothing uses
- **THEN** both the model and the material are marked as unused

#### Scenario: Unknown or cyclic references are tolerated

- **WHEN** a `meta.json` holds a `uuid` that matches no asset, or assets reference each other in a
  cycle
- **THEN** the unknown reference is ignored, the cycle does not hang the view, and usage of the
  other assets is unaffected

#### Scenario: Unreadable scene does not hide usage state

- **WHEN** one scene of the project is malformed
- **THEN** assets are still listed, and references from the remaining readable scenes are honored

### Requirement: Asset reading is read-only

Listing assets and computing their usage SHALL NOT modify any file on disk.

#### Scenario: Files unchanged by reading assets

- **WHEN** the view builds a project node including its assets
- **THEN** every file under the project folder is byte-for-byte unchanged