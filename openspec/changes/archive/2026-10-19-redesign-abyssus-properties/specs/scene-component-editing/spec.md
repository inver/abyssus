# Spec Delta

## ADDED Requirements

### Requirement: Enable and disable an entity or component

The editor SHALL offer to enable or disable an entity or a component of an entity. Disabling SHALL write `enabled: false` on that entry only; enabling SHALL remove the key. Each change SHALL be one undoable edit that preserves the rest of the scene file, and it SHALL refuse a document that is unsupported for editing.

#### Scenario: Disable
- **WHEN** a component is disabled
- **THEN** its entry gains `enabled: false` and no other byte of the file changes except the necessary separators

#### Scenario: Enable
- **WHEN** a disabled component is enabled
- **THEN** the `enabled` key is removed

#### Scenario: Undo
- **WHEN** the user undoes the change
- **THEN** the file text is exactly as before it

#### Scenario: Unsupported document
- **WHEN** the scene is unsupported for editing
- **THEN** the change is refused and the file is unchanged
