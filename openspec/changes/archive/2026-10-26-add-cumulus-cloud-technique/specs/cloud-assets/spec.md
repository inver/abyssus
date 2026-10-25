# Spec Delta

## MODIFIED Requirements

### Requirement: Cloud asset

A folder under the project's `assets` whose `meta.json` has `"type": "CLOUDS"` SHALL be a cloud asset. Its
`additional` SHALL hold `technique` (`layered`, `cumulus`, `shells` or `volumetric`; `shells` when missing) and up to
three bands `low`, `mid` and `high`, with the fields, types and limits of the `scene-sky-clouds` band requirement. Its
`meta.json` carries the native `format` / `formatVersion` markers and a `uuid`, by which skies name it.

#### Scenario: Cloud asset is recognized

- **WHEN** a copy of `Untitled` gets `assets/clouds_storm/meta.json` with `"type": "CLOUDS"`, a `uuid`, a low
  `stratocumulus` and a mid `altostratus` band
- **THEN** the project lists `clouds_storm` as a cloud asset

#### Scenario: Cumulus technique

- **WHEN** a cloud asset has `"technique": "cumulus"` and a sky naming it is drawn
- **THEN** the sky draws its bands with the cumulus technique

#### Scenario: Unreadable cloud asset

- **WHEN** a cloud asset's `meta.json` is not valid JSON
- **THEN** it is listed as an asset whose metadata could not be read, and skies naming it draw without clouds
