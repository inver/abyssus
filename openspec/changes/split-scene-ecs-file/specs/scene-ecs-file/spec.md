# Spec Delta

## Purpose

Defines the separate document that holds a scene's entities as a JSON array, how it pairs with the `.scene`, and how
the pair is edited as one unit.

## ADDED Requirements

### Requirement: A scene's entities live in a sibling array file

A scene SHALL keep its entities in `<Scene>.ecs.json`, next to its `.scene`, as a JSON array. Each element SHALL have an
integral `id` and MAY have `type`, `flags`, `disabledComponents` and `components`, with the meaning those members have
in an entity today. The file SHALL carry no `format` markers; it is supported only beside a supported `.scene`.

#### Scenario: Entities load from the array

- **WHEN** `Field.scene` is supported and `Field.ecs.json` holds `[{"id":0,"type":"TERRAIN","components":{...}}]`
- **THEN** the scene loads one entity with id `0`, its type and its components

#### Scenario: Sparse ids are kept

- **WHEN** the array holds ids `0`, `5` and `300`
- **THEN** the entities keep those ids, and references such as `lookAtId` resolve by id, not by position

### Requirement: Ids are unique integers

Every element's `id` SHALL be an integral JSON number, unique within the array. A missing, non-integral or duplicate
`id` makes the scene unsupported for editing.

#### Scenario: Duplicate id

- **WHEN** two elements have `"id": 4`
- **THEN** the scene is reported as unsupported and neither file is changed by a plugin edit or by formatting

### Requirement: Missing and malformed ECS files

A scene without an ECS file SHALL load with no entities. An ECS file whose root is not an array, or that holds an
element the entity rules reject, SHALL make the scene unsupported for editing.

#### Scenario: No ECS file

- **WHEN** `Empty.scene` has no `Empty.ecs.json`
- **THEN** it loads with its environment values and no entities

#### Scenario: Object root

- **WHEN** `Field.ecs.json` holds a JSON object instead of an array
- **THEN** the scene is unsupported and both files stay unchanged

### Requirement: A scene's entity edits and settings edits are one command

An edit that changes the `.scene`, the ECS file or both SHALL be one undoable command. A file the edit does not change
SHALL NOT be rewritten, and an edit that adds the first entity SHALL create the ECS file.

#### Scenario: Entity edit only

- **WHEN** the user drags an object in the scene view
- **THEN** only the ECS file changes, and one Undo restores it

#### Scenario: Edit across both files

- **WHEN** an edit changes a scene setting and removes an entity
- **THEN** one Undo restores both files

### Requirement: Rename Scene moves the pair

Renaming a scene SHALL rename its ECS file with it as one undoable command.

#### Scenario: Rename

- **WHEN** `Field` is renamed to `Airfield`
- **THEN** `Airfield.scene` and `Airfield.ecs.json` exist, and the old names do not
