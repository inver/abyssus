# Spec Delta

## Purpose

Lets one weather setup (the clouds of each band and how they are drawn) be a project asset of its own, written once
and drawn by every procedural sky that names it, loaded the way a terrain loads its textures.

## ADDED Requirements

### Requirement: Cloud asset

A folder under the project's `assets` whose `meta.json` has `"type": "CLOUDS"` SHALL be a cloud asset. Its
`additional` SHALL hold `technique` (`layered`, `shells` or `volumetric`; `shells` when missing) and up to three bands
`low`, `mid` and `high`, with the fields, types and limits of the `scene-sky-clouds` band requirement. Its `meta.json`
carries the native `format` / `formatVersion` markers and a `uuid`, by which skies name it.

#### Scenario: Cloud asset is recognized

- **WHEN** a copy of `Untitled` gets `assets/clouds_storm/meta.json` with `"type": "CLOUDS"`, a `uuid`, a low
  `stratocumulus` and a mid `altostratus` band
- **THEN** the project lists `clouds_storm` as a cloud asset

#### Scenario: Unreadable cloud asset

- **WHEN** a cloud asset's `meta.json` is not valid JSON
- **THEN** it is listed as an asset whose metadata could not be read, and skies naming it draw without clouds

### Requirement: Sky loads its cloud asset as a dependency

A procedural sky SHALL load the cloud asset it names as a dependency of its own load, the way a terrain loads the
texture assets its splat fields name: the `uuid` is resolved to an asset folder when the sky is read, the cloud asset is
loaded first by the same asset storage, and the sky reads the built cloud asset each time it is drawn and never owns
it. Several skies, and several views of one sky, SHALL share one built cloud asset per view. The cloud asset SHALL own
the volumetric technique's 3D noise, made off the GL thread when it is read and uploaded when it is built.

#### Scenario: Shared cloud asset

- **WHEN** two procedural skies of a project name the same cloud asset
- **THEN** both draw its clouds, and the cloud asset is read once per view

#### Scenario: Cloud asset loads first

- **WHEN** a scene view loads a sky that names `clouds_storm`
- **THEN** `clouds_storm` is loaded before the sky is built, and the sky draws its bands

### Requirement: Cloud assets in the Abyssus view

The Abyssus view SHALL list cloud assets among a project's assets with a cloud icon and their `type`. A cloud asset
SHALL count as used when a used sky names it in `clouds`, as any `uuid` reference does; otherwise it SHALL be marked
unused like any other asset.

#### Scenario: Cloud asset used through a sky

- **WHEN** `Main Scene` uses `skybox_physical`, whose `clouds` holds the `uuid` of `clouds_storm`
- **THEN** neither `skybox_physical` nor `clouds_storm` is marked unused

#### Scenario: Cloud asset no used sky names

- **WHEN** only an unused sky names `clouds_fair`
- **THEN** `clouds_fair` is marked unused

### Requirement: Cloud assets are read-only in this version

Reading, listing or drawing a cloud asset SHALL NOT write any file. Creating cloud assets from the IDE follows in
`add-weather-preset-creation`; the plugin ships fair, overcast and storm example metas as templates only.

#### Scenario: Files unchanged

- **WHEN** a scene whose sky names `clouds_storm` is opened in the scene view and the cloud asset is selected in the
  tree
- **THEN** no file in the project changes
