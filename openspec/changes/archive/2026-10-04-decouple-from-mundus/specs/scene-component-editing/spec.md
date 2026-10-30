# Spec Delta

## MODIFIED Requirements

### Requirement: Update a component field

The plugin SHALL change one field of a modeled component of an entity and write only that change into the
file. A value that does not fit the field SHALL be rejected with a message and leave the file unchanged.

#### Scenario: Change a camera field

- **WHEN** the `fieldOfView` of an entity's camera is set to `60`
- **THEN** the file holds `60` for it and every other value of the entity is unchanged

#### Scenario: Not a number

- **WHEN** a numeric field is set to `abc`
- **THEN** the file is unchanged and a message names the field and the problem

#### Scenario: Value equals the default

- **WHEN** a position's `localScale.y` is set back to `1`
- **THEN** the file follows the Abyssus version 1 default omission rules, leaving no field that only repeats a default

#### Scenario: No change

- **WHEN** a field is set to the value it already has
- **THEN** the file is not modified

### Requirement: Edits preserve the rest of the scene file

Every create, update and remove SHALL leave everything it does not target as it was: other entities,
unmodeled components, `archetype` ids, the `ecs` block's `archetypes`, `metadata` and the native document markers,
the order of entities and components, and the formatting style of the file.

#### Scenario: Unmodeled data survives

- **WHEN** a component is changed in `Main Scene`
- **THEN** reloading the file shows the same `PickableComponent`, `DependenciesComponent`, unknown native renderable kinds, `archetypes`, `metadata` and the native document markers as before

#### Scenario: Written file loads

- **WHEN** any sequence of adds, updates and removes has been applied
- **THEN** the scene loads again without new warnings and the Scene view shows the result

## REMOVED Requirements

### Requirement: Spotlight extension compatibility

**Reason**: Spotlight parameters are native Abyssus fields and do not depend on upstream equivalents or compatibility.
**Migration**: Native scenes store the same units and omitted defaults under the Abyssus version 1 contract. Legacy scenes are not imported.

## ADDED Requirements

### Requirement: Native spotlight storage

A native spotlight SHALL store `coneAngle` as full degrees and `edgeSoftness` as a fraction from 0 to 1 in its existing nested or flat light representation. Defaults SHALL be 45 degrees and 0.2 softness, omitted when reset. Documentation SHALL describe these as native Abyssus fields.

#### Scenario: Save native beam fields
- **WHEN** a nested spotlight is saved with a 60-degree cone and 25-percent softness
- **THEN** its light object holds `coneAngle: 60` and `edgeSoftness: 0.25`, with unrelated content preserved

#### Scenario: Reset native defaults
- **WHEN** beam settings are reset to 45 degrees and 20-percent softness
- **THEN** those keys are omitted and the effective values remain the defaults
