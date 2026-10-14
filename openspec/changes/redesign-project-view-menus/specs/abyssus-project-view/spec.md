# Spec Delta

## ADDED Requirements

### Requirement: Grouped right-click menus on Assets and Scene rows

The right-click menu of the project's **Assets** row SHALL be two grouped sub-menus, each shown as a menu with a header:
- **Import Model**, containing Import FlightGear Aircraft and Import Model;
- **New**, containing New UI Asset, New Terrain and New Foliage.

The right-click menu of a **Scene** row (a standalone `.scene` or a scene listed under a project) SHALL be Rename and one grouped sub-menu:
- **Add**, containing Add Asset, Add Path Arrangement, Add Light and Add Water.

Rename comes first, then the Add sub-menu. Each action keeps the row it is offered on and the conditions it is enabled under; an action whose row does not apply is hidden, and a sub-menu that has no visible child is not shown.

#### Scenario: Assets row shows the two groups
- **WHEN** the user right-clicks the Assets row of a project
- **THEN** the menu shows Import Model and New as two sub-menus, and Import FlightGear Aircraft, Import Model, New UI Asset, New Terrain and New Foliage are reachable through them

#### Scenario: Scene row shows Rename and Add
- **WHEN** the user right-clicks a Scene row
- **THEN** the menu shows Rename and Add as a sub-menu, and Add Asset, Add Path Arrangement, Add Light and Add Water are reachable through it

#### Scenario: A sub-menu with no applicable child is not shown
- **WHEN** a project has no model or terrain and the user right-clicks its Scene row
- **THEN** Add Asset and Add Path Arrangement are hidden, and Add Light and Add Water are still reachable; the Add sub-menu is shown only while it has a visible child

#### Scenario: A sub-menu with no visible child is hidden
- **WHEN** a menu group has no visible child for the selected row
- **THEN** the group's header is neither shown nor shown disabled

#### Scenario: Other rows are unaffected
- **WHEN** the user right-clicks an entity, a component, a UI asset, a foliage-bound terrain, a water surface or a path arrangement row
- **THEN** their menus are unchanged by this change

### Requirement: Rename Scene on a standalone scene

Rename Scene SHALL be offered on any row that stands for a scene, whether the scene is listed under a project or is a standalone `.scene` file at the top level of the view.

#### Scenario: Rename on a standalone scene
- **WHEN** the user right-clicks a standalone `.scene` row
- **THEN** Rename Scene is offered, the dialog starts with that scene's current `name`, and choosing a new name edits that scene's `name` as one undoable command

#### Scenario: Rename on a scene listed under a project
- **WHEN** the user right-clicks a scene listed under a project
- **THEN** Rename Scene is offered, and choosing it edits that scene's `name`

#### Scenario: Rename is not offered on other rows
- **WHEN** the user right-clicks the Assets row, a project row, an `.abss` asset row or an entity row
- **THEN** Rename Scene is not offered
