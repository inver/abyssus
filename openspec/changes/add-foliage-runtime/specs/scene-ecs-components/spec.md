# Spec Delta

## ADDED Requirements

### Requirement: Foliage component is built in

A native foliage component SHALL load as a built-in component with an optional asset name. Loading and writing it
SHALL preserve unknown members, key order, numeric spelling and omitted defaults. Unresolved asset names SHALL be
retained and reported once during reference validation without rewriting the document. Malformed modeled values
SHALL remain raw with a warning rather than discarding extension data.

#### Scenario: Round trip
- **WHEN** entity `1` holds `"FoliageComponent": {"assetName": "foliage_meadow", "note": "x", "extra": {"weight": 2.50, "epsilon": 1.0E-4}}` and the supported scene is loaded and written
- **THEN** the bound asset is `foliage_meadow` and the component's unknown values, key order and number text are unchanged

#### Scenario: Unknown folder
- **WHEN** an asset name has no supported project foliage asset
- **THEN** validation warns once, other scene content remains available, and the original name round-trips unchanged

#### Scenario: Missing asset name
- **WHEN** a foliage component omits its asset name
- **THEN** it binds as none, produces no foliage entry and does not gain a default member on write

#### Scenario: Malformed asset name
- **WHEN** the component's asset name is a number
- **THEN** the unbound component remains raw with one warning and preserves that number on write
