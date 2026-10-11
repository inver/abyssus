# Spec Delta

## MODIFIED Requirements

### Requirement: Unused asset marking

The view SHALL mark an asset as unused when neither a scene nor a registered UI asset of the project reaches it. Native project uiAssetNames entries naming supported UI assets SHALL be additional usage roots. Navigation targets in a reached UI asset SHALL reach their target UI assets transitively; local styling resource files are owned by their UI asset rather than separate assets. Missing, unsupported and cyclic UI references SHALL be diagnosed without blocking other usage results. A scene
references an asset directly when its ECS data names the asset's folder in an `assetName` or
`shaderKey` field, or when its `skyboxName` equals the folder name. A used asset in turn uses the assets whose
`uuid` appears in its own `meta.json` as a terrain splat texture (`splatMap`, `splatBase`,
`splatR`, `splatG`, `splatB`, `splatA`) or as a model material (`materials`), and a used foliage asset uses the
terrain named by its `additional.terrain` and the models named by its layers, transitively. The
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

#### Scenario: Foliage used through a terrain entity

- **WHEN** entity `1` of `Main Scene` has `FoliageComponent` with `assetName` `foliage_meadow`
- **THEN** the `foliage_meadow` asset is not marked as unused

#### Scenario: Model used only through foliage

- **WHEN** no scene names `tree`, and a used foliage asset has a layer whose models include `tree`
- **THEN** `tree` is not marked as unused

#### Scenario: Foliage nothing shows

- **WHEN** no scene's `FoliageComponent` names a foliage asset whose layers use `tree`, and nothing else uses `tree`
- **THEN** both the foliage asset and `tree` are marked as unused

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

#### Scenario: Registered menu assets

- **WHEN** a native project registers menus in uiAssetNames and menus navigates to a screen in settings
- **THEN** menus and settings are not marked unused even when no scene references them

#### Scenario: Unregistered menu asset

- **WHEN** an unregistered UI asset is not reached by any scene or registered UI asset
- **THEN** it is marked unused

