# Spec Delta

## MODIFIED Requirements

### Requirement: Cloud assets are read-only in this version

Reading, listing or drawing a cloud asset SHALL NOT write any file. Explicit creation from a procedural sky SHALL
be available through the `weather-presets` workflow and SHALL write only the new asset folder. Cloud band editing
in Properties SHALL remain unavailable; fair, overcast and storm example metas SHALL remain templates rather than
built-in preset references.

#### Scenario: Files unchanged

- **WHEN** a scene whose sky names `clouds_storm` is opened in the scene view and the cloud asset is selected in the
  tree
- **THEN** no file in the project changes

#### Scenario: Explicit creation writes only a new asset

- **WHEN** the user creates a weather snapshot from a procedural sky with a readable native cloud asset reference
- **THEN** only the chosen new folder and its `meta.json` are created, and loading, listing and rendering existing
  assets continue without writing any file
