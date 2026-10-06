# component-schemas Specification

## Purpose

Describes a game's components as data, in a schema file inside the project, so the editor can show and edit
them without running the game's code.

## Requirements

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

### Requirement: Export is stable

Exporting the same components twice SHALL produce byte-identical files: components in registration order, fields in
declaration order, and the same formatting each time.

#### Scenario: Repeat export

- **WHEN** the schema is exported twice without changes to the game
- **THEN** the second file is byte-identical to the first

### Requirement: Abyssus reads the project's schema

When a project holds `abyssus/components.schema.json`, Abyssus SHALL know the components it declares for that
project's scenes, and SHALL pick up a changed or removed schema file without reopening the project.

#### Scenario: Schema present

- **WHEN** the `Custom` test project is open in the IDE
- **THEN** Abyssus edits entity `0`'s `PlaneComponent` as a known component

#### Scenario: Schema exported again

- **WHEN** the game adds a field to `PlaneComponent` and exports again while the project is open
- **THEN** the new field appears for entity `0`'s plane without reopening anything

### Requirement: A broken schema is reported, not fatal

A schema file that cannot be read, or a component in it that is invalid, SHALL be reported once to the user, naming
the file and the problem. The components it fails to declare SHALL behave as unknown components; everything else SHALL
work as before.

#### Scenario: Not JSON

- **WHEN** the project's schema file is not valid JSON
- **THEN** one notification names `components.schema.json` and the problem, and `PlaneComponent` is shown as read-only
  JSON like any unknown component

### Requirement: Every field type behaves the same everywhere

A game component field of any type (decimal, whole, boolean, text, choice, entity reference, asset reference,
vector, color) SHALL decode, encode, default and show in the Properties panel by one rule, whether the component is
read when the scene loads, edited in the panel or written back to the file. Omitted defaults, unrelated number text
and key order SHALL stay as the file has them.

#### Scenario: Vector round trip

- **WHEN** a component's vector field is edited in the Properties panel from `{"x": 1, "y": 2, "z": 3}` to `x` 4
- **THEN** the file holds `{"x": 4, "y": 2, "z": 3}`, with `y` and `z` written as before, and a scene load reads
  the same values

#### Scenario: Default stays omitted

- **WHEN** a field is edited back to its default value
- **THEN** the key is omitted from the file as it is today

#### Scenario: Value outside a limit

- **WHEN** a field with a minimum is given a smaller value
- **THEN** the edit is refused with the same message as today and the file is unchanged
