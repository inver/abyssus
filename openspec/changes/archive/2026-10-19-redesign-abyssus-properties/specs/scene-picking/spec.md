# Spec Delta

## ADDED Requirements

### Requirement: Disabled entities are not picked

A disabled entity, and the models of its disabled components, SHALL NOT be drawn in the scene view and SHALL NOT be picked by a click. Selecting the entity in the tree SHALL still show it in Properties.

#### Scenario: Click through a disabled entity
- **WHEN** a disabled entity lies in front of an enabled one and the user clicks there
- **THEN** the enabled entity is picked

#### Scenario: Tree selection
- **WHEN** a disabled entity is selected in the tree
- **THEN** Properties shows it with its sections dim and no gizmo is drawn
