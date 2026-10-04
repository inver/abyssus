# Spec Delta

## ADDED Requirements

### Requirement: Changed assets reload

Changed metadata and asset files SHALL refresh affected assets in open scene views without reopening them, including Undo and Redo. Changes to a referenced texture SHALL refresh dependent terrains. Unrelated assets and other projects SHALL remain usable. Refresh SHALL use unsaved metadata text when available, matching the properties panel.

#### Scenario: Terrain regeneration
- **WHEN** new heights are applied to Untitled's terrain asset
- **THEN** every open scene using it displays the new surface and terrain picking uses the new heights

#### Scenario: Referenced texture changed
- **WHEN** a texture referenced by terrain metadata changes or is replaced
- **THEN** the terrain displays the new texture without reopening the scene

#### Scenario: Unsaved metadata edit
- **WHEN** the user changes terrain size in its open metadata editor without saving
- **THEN** the properties panel and open scene use the edited size, and reverting the text restores the previous size

### Requirement: Refresh uses the latest asset revision

An older load finishing after a newer edit SHALL NOT replace the latest requested asset revision. Missing or broken changed assets SHALL remain isolated and be retried after a subsequent relevant change, with errors reported once per revision. Hidden scene views SHALL defer resource replacement until they can safely render.

#### Scenario: Rapid regeneration
- **WHEN** an asset load is pending and a second regeneration is applied
- **THEN** the final scene displays the second regeneration and cannot revert to the first when its load finishes

#### Scenario: Repair a terrain
- **WHEN** a previously unreadable terrain data file is replaced with valid data
- **THEN** that terrain loads without reopening the view and the other scene assets remain visible
