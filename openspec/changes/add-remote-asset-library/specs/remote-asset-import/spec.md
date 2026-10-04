# Spec Delta

## Purpose

Lets users import a verified remote asset and its included dependencies into an Abyssus project without overwriting existing content, with rollback and Undo.

## ADDED Requirements

### Requirement: Explicit import destination

Import SHALL require an explicitly selected supported `.abss` project and target its sibling `assets` folder. With one eligible project it SHALL be preselected; with multiple projects the user SHALL choose. Browsing and download-only SHALL work with no eligible project. Import SHALL NOT edit project or scene documents or automatically place an object.

#### Scenario: Import into Untitled
- **WHEN** the user selects a native-format copy of `Untitled.abss` and imports a model package
- **THEN** the package's model and dependencies are added under its `assets`, while `.abss` and `Main Scene.scene` remain byte-identical

#### Scenario: No destination
- **WHEN** no supported Abyssus project is available
- **THEN** browsing and downloads remain available and Import explains that a destination is required

### Requirement: Complete native package

Import SHALL accept only package schema version 1 containing `manifest.json`, one selected root asset and its complete dependency closure under `assets/<folder>/`. Every asset SHALL have supported native metadata, a unique nonempty UUID and its required files. Manifest identities, folder names, types, dependencies and checksums SHALL match the version descriptor and metadata. No legacy conversion SHALL occur.

#### Scenario: Model with dependencies
- **WHEN** a valid model package includes its referenced materials and textures
- **THEN** all included assets import together with metadata and payload bytes preserved

#### Scenario: Incomplete or unsupported package
- **WHEN** a dependency or referenced payload is missing, a metadata identity disagrees, or a native/package version is unsupported
- **THEN** Import explains the validation failure and writes nothing to the project

### Requirement: Bounded package extraction

Import SHALL reject paths escaping staging or the asset destination, absolute paths, symbolic links, duplicate or case-colliding entries, and packages exceeding documented download/extraction limits. Package content SHALL remain outside the project until validation succeeds.

#### Scenario: Unsafe archive
- **WHEN** a ZIP contains `../outside.txt`, an absolute path or a symbolic link
- **THEN** it is rejected without creating or changing any destination file

#### Scenario: Excessive package
- **WHEN** the download or actual extracted bytes exceed the client limits
- **THEN** processing stops with a size-limit reason and temporary extraction is removed

### Requirement: No collision overwrites

Import SHALL reject any included asset folder name or UUID already present in the destination, including filesystem case collisions. It SHALL report the conflicts before writing. Unreadable destination metadata that prevents reliable UUID checking SHALL block import with a reason. Repeated imports SHALL NOT overwrite, merge or silently skip existing assets.

#### Scenario: Existing asset folder
- **WHEN** a package would create the existing `tree` folder in a copy of Untitled
- **THEN** the conflict names `tree` and all project files remain unchanged

#### Scenario: UUID conflict with another folder
- **WHEN** an included asset UUID already belongs to a differently named local asset
- **THEN** the conflict identifies that local asset and the whole import is refused

### Requirement: Undoable all-or-nothing import

Import SHALL add the complete validated package as one undoable operation, rechecking destination state immediately before writing. A write failure SHALL roll back changes and report any rollback failure. Cancellation before writing SHALL leave the destination unchanged. Undo SHALL remove all imported files only while they remain unchanged and unreferenced; Redo SHALL restore them without redownloading.

#### Scenario: Successful import and Undo
- **WHEN** a valid new package imports and the user invokes Undo before using or editing its assets
- **THEN** all imported folders are removed; Redo restores their exact bytes and preexisting files stay unchanged

#### Scenario: New reference blocks Undo
- **WHEN** a scene or existing local asset begins referencing an imported asset and the user invokes Undo
- **THEN** Undo is refused with a dependency reason and the imported assets remain intact

#### Scenario: Destination changes during download
- **WHEN** a conflicting asset appears after download starts but before commit
- **THEN** Import reports the conflict and performs no writes

#### Scenario: Write failure
- **WHEN** a filesystem write fails during import
- **THEN** previously written import files are restored or removed, and any files that could not be restored are explicitly reported

### Requirement: Imported assets become local assets

After successful import the existing local asset tree SHALL refresh and show the new folders using its normal type and usage rules. Unreferenced imports SHALL appear unused. No preview or asset code SHALL execute merely because a remote catalog item is selected.

#### Scenario: Newly imported model
- **WHEN** a model is imported without adding a scene reference
- **THEN** the model and its dependencies appear in the local asset list and are marked unused according to existing reachability rules
