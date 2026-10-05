# Spec Delta

## ADDED Requirements

### Requirement: Admission is consistent across loading entry points

Native project, scene and asset documents SHALL use the same format admission rules whether read from saved files, unsaved editor text or outside the editor. Rejected input SHALL remain unchanged with a format reason, and supported siblings SHALL stay usable. Raw ECS input and output SHALL refuse reserved identifier-table and renderable class fields without requiring an enclosing document header.

#### Scenario: Native fixture outside the editor
- **WHEN** Untitled's project, Main Scene and model metadata are read outside the editor
- **THEN** they are accepted without changing their bytes or inserting defaults

#### Scenario: Invalid metadata from either source
- **WHEN** saved or unsaved asset metadata has a missing marker, foreign format, fractional version or future version
- **THEN** it is refused before its settings or source assets are loaded, with the same format reason

#### Scenario: Raw ECS legacy payload
- **WHEN** an ECS block contains componentIdentifiers or a built-in Render component's renderable contains class
- **THEN** loading or writing refuses it, including when the built-in component is named by its fully qualified name

#### Scenario: Extension fields stay opaque
- **WHEN** an unknown component or a marker's nested payload contains class or componentIdentifiers
- **THEN** those extension fields remain accepted and unchanged
