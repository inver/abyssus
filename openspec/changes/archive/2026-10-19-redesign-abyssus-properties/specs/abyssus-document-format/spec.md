# Spec Delta

## ADDED Requirements

### Requirement: Optional enabled flag

An entity entry and a component entry of a scene's `ecs` block MAY carry an `enabled` boolean. A missing flag SHALL mean enabled, and writers SHALL add the flag only as `false` and remove it when the entity or component is enabled again. A flag that is not a boolean SHALL make the scene unsupported for editing and leave it unchanged. `formatVersion` SHALL remain `1`, and documents that do not use the flag SHALL be unchanged by any edit.

#### Scenario: Missing flag
- **WHEN** a native scene has no `enabled` anywhere
- **THEN** every entity and component is enabled and the scene opens as before

#### Scenario: Non-boolean flag
- **WHEN** a component entry has `"enabled": "no"`
- **THEN** the scene is reported as unsupported for editing and its text is unchanged by plugin edit attempts

#### Scenario: Extension data kept
- **WHEN** a disabled component's entry carries unknown extension keys and the flag is removed
- **THEN** the unknown keys, key order and number text stay as they were
