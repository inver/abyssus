# Proposal

## Why

The scene view is read-only and leaves out half of what a scene places. Camera entities (such as `Camera 4` in the
`Untitled` fixture's `Main Scene`) are loaded but never drawn, so you can't see where a camera sits or what it frames.
Lights are only felt through shading. Placing anything still means editing numbers in the `.scene` JSON by hand. Showing
cameras and letting the user move and rotate objects in the view makes the scene view usable for laying out a scene.

## What Changes

- **Camera display:** every entity with a `CameraComponent` is drawn as a small camera body plus a wireframe frustum
  built from its position, view direction (or its `lookAtId` target), near, far and field of view.
- **Look through a camera:** a camera selector in the scene view switches the viewport to any camera entity's view, and
  back to the free orbit view. Mouse navigation is paused while looking through a camera.
- **Light markers:** light entities get small markers (with a direction line for directional and spot lights) so they
  can be seen and clicked.
- **Selection in the view:** clicking a model, terrain, camera or light selects it in the view (it is still selected in
  the Abyssus tree, as today). The selected object gets a highlight and a gizmo. Clicking empty space clears the
  selection.
- **Move and rotate gizmos:** a Move mode shows X/Y/Z arrows and a Rotate mode shows X/Y/Z rings on the selected object.
  The modes are switched with a toolbar or the W / E keys. Dragging a handle previews the change live; releasing writes
  it to the `.scene` file, keeping the file's formatting, and the edit can be undone. Esc cancels a drag. Dragging
  anywhere other than a handle still orbits and pans the view.
- **What a drag writes:** the entity's `PositionComponent` `localPosition` / `localRotation`. For a camera it also
  writes its `CameraComponent.camera` `position` / `viewPointPosition`, so Mundus sees a consistent camera.
- Cameras with a `lookAtId` target and point lights have no rotate rings, because their orientation comes from the
  target or doesn't matter.

## Capabilities

### New Capabilities
- `scene-camera-display`: How the scene view shows camera entities and lets the user look through one.
- `scene-object-transform`: Selecting objects in the scene view and moving or rotating them with gizmos, written back
  to the scene file.

### Modified Capabilities
<!-- `scene-picking` lives in the in-flight change render-project-models, not under openspec/specs/, so it can't take a
     delta here. Its picking covers models and terrains, and any left-drag orbits. tasks.md amends that delta so cameras
     and lights are pickable and a drag that starts on a gizmo handle moves the object instead of orbiting. -->

## Impact

- `sceneview/`: `SceneRenderer` (camera and light markers, selection highlight, gizmo pass, look-through camera),
  `ScenePicker` (camera and light targets, gizmo handle hits), `SceneViewPanel` (toolbar, keys, drag routing),
  `SceneView` / `SceneFileEditor` (selection, write-back, undo), `SceneContent` (camera placements).
- New: a gizmo module (handle geometry, hit tests, drag math, no GL needed) and a scene transform writer.
- `projectView/EnabledToggle.kt`: its formatting-preserving JSON edit is shared with the new scene writes.
- Tests: math and hit-test unit tests, writer tests against the `Untitled` fixture, picking tests, GL render tests
  (skipped without a display, as now).
- No change to the `.scene` format, ECS loading, or the Abyssus tree.
