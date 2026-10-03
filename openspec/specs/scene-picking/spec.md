# scene-picking Specification

## Purpose

Lets users find an entity they see in the scene view in the Abyssus project view, by clicking it,
without editing anything.

## Requirements

### Requirement: Clicking an entity selects it in the project view

A click (press and release without dragging) on a displayed entity — a model, a terrain, a camera or a light — SHALL
select that entity's node in the Abyssus project view, expanding and scrolling the tree as needed.
When several entities lie under the click, the one nearest the camera SHALL be selected.

#### Scenario: Click a model

- **WHEN** a user clicks a displayed model in the scene view of `Main Scene`
- **THEN** the Abyssus view selects the `ecs/entities/<id>` node of that entity under the scene

#### Scenario: Click the terrain

- **WHEN** a user clicks the terrain where no model is in front of it
- **THEN** the terrain entity's node is selected

#### Scenario: Nearest wins

- **WHEN** two models overlap under the cursor
- **THEN** the model nearer the camera is selected

### Requirement: Picking does not interfere with navigation or content

Dragging to orbit or pan SHALL NOT select anything. A left-drag that starts on a gizmo handle of the
selected object moves or rotates that object instead of orbiting (see the `scene-object-transform`
capability). A click on empty space SHALL leave the Abyssus project view selection unchanged. Picking
itself SHALL NOT modify the scene file or any project file.

#### Scenario: Orbit by dragging

- **WHEN** a user presses on a model, drags and releases
- **THEN** the camera orbits and the project view selection does not change

#### Scenario: Click on empty space

- **WHEN** a user clicks where no entity is drawn
- **THEN** the project view selection is unchanged

#### Scenario: Entity not in the tree

- **WHEN** the clicked entity's node cannot be found in the Abyssus view (for example the project
  view is not showing the Abyssus pane)
- **THEN** the Abyssus view is opened if possible, otherwise nothing happens and nothing fails
