# Spec Delta

## ADDED Requirements

### Requirement: Component actions in the tree

In addition to the write actions already listed for asset nodes, an entity row of the `ecs` tree SHALL offer an "Add Component..." context menu action listing the modeled kinds the entity lacks, and a modeled component row SHALL offer "Remove Component". Both SHALL write through the rules of the `scene-component-editing` capability, and the tree SHALL refresh to show the result. An unmodeled component row SHALL NOT offer Remove Component.

#### Scenario: Add from the tree

- **WHEN** the user right-clicks an entity and chooses Add Component... > Light
- **THEN** the entity gains a `Light` child row and its component count increases by one

#### Scenario: Remove from the tree

- **WHEN** the user right-clicks the `Light` row and chooses Remove Component
- **THEN** the row disappears and the entity's component count decreases by one

#### Scenario: No action on unmodeled component

- **WHEN** the user right-clicks the `Pickable` row
- **THEN** the menu offers no Remove Component action

#### Scenario: Other rows

- **WHEN** the user right-clicks a scene, an asset or a scalar property
- **THEN** the menu has no Add Component or Remove Component action
