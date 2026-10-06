# Spec Delta

## ADDED Requirements

### Requirement: Foliage component is built in

`FoliageComponent` SHALL be a built-in component holding an `assetName`. It SHALL load into the engine and write back with
its unknown members and number text unchanged. A missing `assetName` SHALL load as none. A name that is not one of the
project's asset folders SHALL load with one warning, and the component SHALL be kept.

#### Scenario: Round trip
- **WHEN** a scene whose terrain entity `1` holds `"FoliageComponent": {"assetName": "foliage_meadow", "note": "x"}` is loaded and written back
- **THEN** entity `1` has a foliage component naming `foliage_meadow`, and the written block holds the same object, `note` included

#### Scenario: Unknown folder
- **WHEN** `assetName` names a folder the project does not have
- **THEN** the scene loads, one warning names the entity and folder, and the component is written back unchanged
