# Spec Delta

## ADDED Requirements

### Requirement: Tree follows document and filesystem changes

The Abyssus tree SHALL refresh automatically after edits, Undo/Redo, and detected filesystem changes to its projects, scenes and asset metadata. Open unsaved document text SHALL take precedence over saved text. Refresh SHALL display the latest state without reopening the view, rewriting files or creating undo entries.

#### Scenario: Undo and redo a scene rename
- **WHEN** the user renames `Main Scene` in a copy of Untitled, undoes that edit, and redoes it
- **THEN** the scene label follows each resulting name without manually refreshing the tree
- **AND** refresh itself creates no additional edit or undo entry

#### Scenario: Unsaved fog edit
- **WHEN** the user changes `fogEnabled` in the open text document of Untitled's `Main Scene.scene` without saving
- **THEN** the tree's fog eye reflects the unsaved value
- **AND** undoing the edit restores the eye without saving the document

#### Scenario: External scene replacement
- **WHEN** a saved scene is replaced externally and the IDE detects the change, with no conflicting unsaved document
- **THEN** its displayed values reflect the replacement without reopening the view

#### Scenario: Files and metadata change
- **WHEN** a scene or asset folder is added, removed or renamed, or asset metadata changes, and the IDE detects it
- **THEN** the tree's membership, labels, applicable counts and unused markings reflect the resulting project

#### Scenario: Unsupported replacement remains unchanged
- **WHEN** a displayed native scene is replaced with malformed text or an unsupported format version
- **THEN** the tree displays its existing unreadable-file presentation and keeps other files usable
- **AND** refresh does not repair or rewrite the replacement

### Requirement: Refresh preserves surviving navigation

Automatic refresh SHALL preserve selection and expansion for entries that still exist. Removed entries SHALL disappear without duplicate nodes or stale details. Updates to a closed project SHALL not reopen its view.

#### Scenario: Selected entity survives an unrelated edit
- **WHEN** entity `0` of Untitled's Main Scene is selected and expanded while another entity's name changes
- **THEN** entity `0` remains selected and expanded after refresh

#### Scenario: Selected entry is removed
- **WHEN** the selected entity is removed from the scene and the tree refreshes
- **THEN** that entity no longer appears and its old details are no longer presented as the current selection

#### Scenario: Close with pending changes
- **WHEN** the project is closed while a refresh is pending
- **THEN** the pending change does not reopen the project or display a tree for it
