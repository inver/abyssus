# Spec Delta

## ADDED Requirements

### Requirement: Grouped right-click menus on Assets and Scene rows

The right-click menu of the project's **Assets** row SHALL show two grouped sub-menus, in this order, each shown as a menu with a header:
- **Import Model**, containing Import FlightGear Aircraft and Import Model;
- **New**, containing New UI Asset, New Terrain and New Foliage.

The right-click menu of a **Scene** row (a standalone `.scene` or a scene listed under a project) SHALL show Rename Scene first and then one grouped sub-menu:
- **Add**, containing Add Asset, Add Path Arrangement, Add Light and Add Water.

Within each sub-menu the actions appear in the order listed. Each action keeps the row it is offered on and the conditions it is enabled under: an action whose row does not apply is hidden, and one that cannot run for the current state is shown disabled. A sub-menu is shown only while it has an enabled and visible action; when it has none, it is not shown at all.

#### Scenario: Assets row shows the two groups in order

- **WHEN** the user right-clicks the Assets row of a project
- **THEN** Import Model and New are shown as two sub-menus in that order, and Import FlightGear Aircraft, Import Model, New UI Asset, New Terrain and New Foliage are reachable through them

#### Scenario: Scene row shows Rename Scene then Add

- **WHEN** the user right-clicks a Scene row
- **THEN** Rename Scene is offered first, then Add as a sub-menu reaching Add Asset, Add Path Arrangement, Add Light and Add Water

#### Scenario: An action that cannot run stays listed but disabled

- **WHEN** a project whose assets contain no model or terrain has its Scene row opened
- **THEN** Add Asset and Add Path Arrangement are shown disabled, while Add Light and Add Water remain enabled

#### Scenario: A sub-menu with no enabled and visible action is not shown

- **WHEN** every action of a sub-menu is disabled or hidden for the selected row (for example, the Add actions on a scene whose text cannot be read as a native scene)
- **THEN** the sub-menu is not shown at all — neither as an empty nor as a disabled header

#### Scenario: Other rows are unaffected

- **WHEN** the user right-clicks an entity, a component, a UI asset, a foliage-bound terrain, a water surface or a path arrangement row
- **THEN** their menus are unchanged by this change

### Requirement: Rename Scene on a standalone scene

Rename Scene SHALL be offered on any row that stands for a scene, whether the scene is listed under a project or is a standalone `.scene` file at the top level of the view. It SHALL start from that scene's current `name` and change only that `name`, as one undoable command.

#### Scenario: Rename on a standalone scene

- **WHEN** the user right-clicks a standalone `.scene` row
- **THEN** Rename Scene is offered, the dialog starts with that scene's current `name`, and choosing a new name edits that scene's `name` as one undoable command

#### Scenario: Rename on a scene listed under a project

- **WHEN** the user right-clicks a scene listed under a project
- **THEN** Rename Scene is offered, and choosing it edits that scene's `name` as one undoable command

#### Scenario: Rename is not offered on other rows

- **WHEN** the user right-clicks the Assets row, a project row, an `.abss` asset row or an entity row
- **THEN** Rename Scene is not offered
