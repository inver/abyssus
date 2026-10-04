# Spec Delta

## MODIFIED Requirements

### Requirement: Schema export

A game SHALL be able to export the components it registers to `<project>/abyssus/components.schema.json`. The file
SHALL list each component's short name, class name and display label, and each field's name, label, type, default,
group, limits (for numbers and 3D vectors), choices (for a choice) and asset type (for an asset reference). The
`abyssus` folder SHALL be created when missing.

#### Scenario: Plane schema

- **WHEN** a game that registers `PlaneComponent` exports its schema to a copy of the `Custom` project
- **THEN** the file lists `PlaneComponent` with its class name and the field `lineLength` as a decimal number with
  default `18`, minimum `5`, maximum `30` and group `Lines`

#### Scenario: Scenes untouched

- **WHEN** the schema is exported
- **THEN** no `.scene` or `.abss` file of the project changes
