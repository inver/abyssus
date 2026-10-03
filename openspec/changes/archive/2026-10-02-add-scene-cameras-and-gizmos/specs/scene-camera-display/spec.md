# Spec Delta

## Purpose

Defines how the scene view shows the camera entities a scene places, as a body and frustum, and how the user can look
through one of them instead of the free orbit view.

## ADDED Requirements

### Requirement: Camera entities are drawn

Every entity with a `CameraComponent` SHALL be drawn as a small camera body at its position and a wireframe frustum that
spans its near and far planes at its field of view. The frustum SHALL point along the camera's view direction, or at the
position of its `lookAtId` entity when that resolves. The frustum's aspect SHALL be the scene view's current aspect.

#### Scenario: Fixture camera is visible

- **WHEN** the `Untitled` project's `Main Scene` is opened in the scene view
- **THEN** a camera body is drawn at about (-17.7, 6.1, 0.6) with a frustum from 1 to 100 units at 67° pointing at
  entity 3

#### Scenario: Camera without a target

- **WHEN** a camera entity has no `lookAtId` (or -1) and `viewPointPosition` (1, 0, 0)
- **THEN** its frustum points along +X

#### Scenario: Unreadable camera is skipped

- **WHEN** a camera entity's `CameraComponent` has no `camera` object
- **THEN** it is drawn with libGDX camera defaults at its `PositionComponent` position, and the rest of the scene still
  draws

### Requirement: Camera follows scene edits

A drawn camera SHALL update when the scene file changes: a moved camera, a moved `lookAtId` target, or changed near, far
or field of view SHALL show in the next frame without reopening the view.

#### Scenario: Target moves

- **WHEN** entity 3 of `Main Scene` is moved
- **THEN** `Camera 4`'s frustum turns to keep pointing at it

### Requirement: Look through a camera

The scene view SHALL offer a camera selector listing "Free camera" and every camera entity by its `NameComponent` name
(its entity id when unnamed). Choosing a camera SHALL render the viewport from that camera's position, direction, near,
far and field of view. Choosing "Free camera" SHALL return to the orbit view as it was before.

#### Scenario: Switch to the fixture camera

- **WHEN** the user picks `Camera 4` in the selector
- **THEN** the view renders from `Camera 4`, looking at entity 3, and that camera's own body and frustum are not drawn

#### Scenario: Back to the free view

- **WHEN** the user picks "Free camera" again
- **THEN** the orbit view returns with the same target, distance and angles it had before switching

#### Scenario: Selected camera is removed

- **WHEN** the camera being looked through disappears from the scene file
- **THEN** the view switches back to "Free camera"

### Requirement: Navigation while looking through a camera

While a camera is looked through, mouse orbit, pan and zoom SHALL NOT move the view, and the scene file SHALL NOT change
because of them. Clicking objects to select them, and dragging gizmo handles, SHALL keep working.

#### Scenario: Drag does nothing

- **WHEN** the user drags on empty space while looking through `Camera 4`
- **THEN** the view does not move and nothing is written
