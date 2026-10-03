# Spec Delta

## Purpose

Lets one weather setup (the clouds of each band) be written once and reused by several procedural skies, with
built-in starting points for fair, overcast and stormy weather.

## ADDED Requirements

### Requirement: Weather preset asset

A folder under the project's `assets` whose `meta.json` has `"type": "WEATHER_PRESET"` SHALL be a weather preset. Its
`additional` SHALL hold up to three bands `low`, `mid` and `high`, with the same fields, types and limits as a sky's
cloud bands. A preset holds no technique and no `enabled` flag.

#### Scenario: Preset is recognized

- **WHEN** a copy of `Untitled` gets `assets/weather_storm/meta.json` with `"type": "WEATHER_PRESET"` and a low
  `stratocumulus` and mid `altostratus` band
- **THEN** the project lists `weather_storm` as a weather preset asset

#### Scenario: Invalid preset

- **WHEN** a preset's `meta.json` is not valid JSON
- **THEN** it is listed as an asset whose metadata could not be read, and skies naming it draw their own bands only

### Requirement: Sky names a preset

A sky's `clouds.preset` SHALL name a weather preset by folder name, or a built-in preset as `builtin:fair`,
`builtin:overcast` or `builtin:storm`. The sky's clouds SHALL be the preset's bands, with each band the sky itself
defines replacing the preset's band of the same level. A preset that is missing or unreadable SHALL be logged once,
and the sky SHALL draw its own bands.

#### Scenario: Preset bands

- **WHEN** `skybox_physical`'s clouds are `{"enabled": true, "preset": "weather_storm"}`
- **THEN** the sky shows the preset's stratocumulus and altostratus bands

#### Scenario: Sky overrides one band

- **WHEN** the same sky also defines `"high": {"type": "cirrus"}` and `"low": {"type": "cumulus", "coverage": 0.2}`
- **THEN** the sky shows the preset's altostratus, its own cirrus, and its own cumulus instead of the preset's
  stratocumulus

#### Scenario: Missing preset

- **WHEN** `clouds.preset` names a folder that does not exist
- **THEN** the sky shows only its own bands and the problem is logged once

### Requirement: Built-in presets

The plugin SHALL provide three read-only presets that need no asset folder: `builtin:fair` (scattered low cumulus and
thin high cirrus), `builtin:overcast` (low stratus over most of the sky and mid altostratus) and `builtin:storm`
(dense, thick low stratocumulus and mid altostratus with strong wind). A project asset folder named `builtin:...` cannot
exist, so a built-in name never refers to a folder.

#### Scenario: Fair weather

- **WHEN** a sky's clouds are `{"enabled": true, "preset": "builtin:fair"}`
- **THEN** most of the sky is clear, with scattered cumulus low and thin cirrus high

#### Scenario: Storm

- **WHEN** the preset is `builtin:storm`
- **THEN** most of the sky is covered by dark, thick clouds that move noticeably faster than in fair weather

### Requirement: Presets in the Abyssus view

The Abyssus view SHALL list weather presets among a project's assets with a weather preset icon and their `type`. A
preset SHALL count as used when a used sky names it in `clouds.preset`; otherwise it SHALL be marked unused like any
other asset. Built-in presets SHALL NOT appear as assets.

#### Scenario: Preset used through a sky

- **WHEN** `Main Scene` uses `skybox_physical`, whose `clouds.preset` is `weather_storm`
- **THEN** neither `skybox_physical` nor `weather_storm` is marked unused

#### Scenario: Preset no sky uses

- **WHEN** no used sky names `weather_fair`
- **THEN** `weather_fair` is marked unused

### Requirement: Presets are read-only in this version

Reading, listing or applying a preset SHALL NOT write any file.

#### Scenario: Files unchanged

- **WHEN** a scene whose sky names `weather_storm` is opened in the scene view and the preset is selected in the tree
- **THEN** no file in the project changes
