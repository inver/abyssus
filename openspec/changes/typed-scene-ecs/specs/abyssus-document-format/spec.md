## ADDED Requirements

### Requirement: Entity shape is validated with the document

Document validation SHALL treat a scene whose `ecs` has an `entities` wrapper, an entity with a non-object `components`, a
non-integral `flags`, or an entity-level `enabled` key as unsupported. The reserved `class` and identifier-table checks
still apply.

#### Scenario: Fractional flags

- **WHEN** an entity has `"flags": 1.5`
- **THEN** the scene is unsupported and its text and disk bytes stay unchanged under editing and formatting

#### Scenario: Extension payload stays opaque

- **WHEN** a custom component's value holds a `class` string
- **THEN** the scene is still supported and the payload is untouched

### Requirement: Entity header

An entity of a scene's `ecs` block MAY carry a `type` (`GROUP`, `LIGHT_DIRECTIONAL`, `LIGHT_POINT`, `LIGHT_SPOT`, `OBJECT`, `TERRAIN`, `CAMERA` or `HANDLE`) beside `flags` and `disabledComponents`. A `TypeComponent` in `components` and an unknown `type` SHALL make the scene unsupported.

#### Scenario: Old type component
- **WHEN** an entity has `"TypeComponent": {"type": "OBJECT"}` in its components
- **THEN** the scene is unsupported and its text is unchanged under editing and formatting

## MODIFIED Requirements

### Requirement: Native identifiers and extensions

Component map keys such as `PositionComponent` SHALL be stable schema identifiers independent of source packages. Model and terrain render components SHALL be flat (`type`, `assetName` and optional `shaderKey`), without `class` and without a nested `renderable`. Unknown short component identifiers SHALL remain raw and round-trip without being instantiated.

#### Scenario: Model reference
- **WHEN** a render component holds `type` `MODEL` and an `assetName`
- **THEN** it resolves the asset without a serialized Java class name

#### Scenario: Unknown native content
- **WHEN** a native scene holds a custom component `WindComponent`
- **THEN** editing another component preserves its payload and loading does not instantiate it

### Requirement: Optional enabled flag

An entity of a scene's `ecs` block MAY carry an integer `flags` whose bit `1` disables it, and a `disabledComponents` list naming its disabled components by their `components` keys. A missing flag or list SHALL mean enabled. Writers SHALL set or clear only bit `1` of `flags`, keep its other bits, add `flags` only when a bit is set and remove it when no bit remains, and add `disabledComponents` only for its first entry and remove it with its last. A non-integral `flags`, a `disabledComponents` that is not a list of strings or an entity-level `enabled` SHALL make the scene unsupported for editing and leave it unchanged. `formatVersion` SHALL remain `1`, and documents that do not use these keys SHALL be unchanged by any edit.

#### Scenario: Missing flag
- **WHEN** a native scene has no `enabled` or `flags` anywhere
- **THEN** every entity and component is enabled and an edit adds neither key

#### Scenario: Non-boolean flag
- **WHEN** an entity has `"disabledComponents": [1]` or `"flags": 1.5`
- **THEN** the scene is reported as unsupported for editing and its text is unchanged by plugin edit attempts

#### Scenario: Extension data kept
- **WHEN** a component is enabled again and its entity's `disabledComponents` held only that name
- **THEN** the key is removed and the unknown keys, key order and number text stay as they were
