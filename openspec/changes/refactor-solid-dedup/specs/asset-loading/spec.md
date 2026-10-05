# Spec Delta

## ADDED Requirements

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
