# Spec Delta

## ADDED Requirements

### Requirement: Persist spotlight beam edits

The plugin SHALL save each spotlight's cone angle and edge softness in its scene, restore both on reopening, and apply accepted edits immediately. Each edit SHALL be one undoable command preserving unrelated scene text. Defaults SHALL follow the agreed storage format's omission convention.

#### Scenario: Reopen spotlight settings
- **WHEN** a spotlight is set to a 60-degree cone and 25-percent edge softness and the scene is saved and reopened
- **THEN** the fields and displayed beam retain those values

#### Scenario: Undo a beam edit
- **WHEN** the cone angle is changed and the user chooses Undo
- **THEN** the scene text and displayed beam return to their previous state

#### Scenario: Preserve existing scene content
- **WHEN** a spotlight beam field is edited in a copy of Untitled's Main Scene
- **THEN** unrelated entities, number text, key order, formatting and unmodeled components remain unchanged

### Requirement: Validate spotlight beam edits

Cone angle edits SHALL accept only finite numbers greater than 0 and less than 180 degrees. Edge softness edits SHALL accept only finite numbers from 0 through 100 percent. Invalid edits SHALL leave the scene unchanged and explain the rejected value.

#### Scenario: Invalid angle
- **WHEN** cone angle is set to 0, 180, a nonfinite value or nonnumeric text
- **THEN** the edit is rejected and the scene remains unchanged

#### Scenario: Invalid softness
- **WHEN** edge softness is set below 0, above 100, to a nonfinite value or to nonnumeric text
- **THEN** the edit is rejected and the scene remains unchanged

### Requirement: Spotlight extension compatibility

When verified Mundus equivalents are unavailable, the scene SHALL store coneAngle in full degrees and edgeSoftness as a fraction from 0 to 1 in the light's existing nested or flat representation. The documented defaults SHALL be 45 degrees and 0.2 softness, omitted when reset. Documentation SHALL identify these as Abyssus extensions with unverified Mundus support.

#### Scenario: Save fallback fields
- **WHEN** a nested spotlight is saved with a 60-degree cone and 25-percent softness using the extension format
- **THEN** its light object holds coneAngle 60 and edgeSoftness 0.25, with unrelated content preserved

#### Scenario: Reset extension defaults
- **WHEN** extension settings are reset to 45 degrees and 20-percent softness
- **THEN** their keys are omitted and the effective values remain those defaults
