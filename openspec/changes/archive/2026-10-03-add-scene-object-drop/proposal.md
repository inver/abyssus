# Proposal

## Why

The scene view can place an object anywhere with the move gizmo, but it can only leave it in mid-air. Placing a
model on a terrain, or stacking it on another model, means reading the height off the surface by eye, dragging the
Y arrow down, and watching the object clip through the ground to find the resting height. A single action that
settles an object onto whatever is beneath it removes that whole loop, which is the common case when dressing a
scene.

## What Changes

- The scene view gets a **Drop** action, from a toolbar button or the `D` key, that moves the selected model,
  camera or light along Y until its lowest point rests on the highest surface under it. Terrains are ground and
  cannot be dropped.
- Surfaces and footprints are bounding boxes, not meshes. The object's footprint is the outline of its own rotated
  box seen from above; a surface is a terrain's height field under that footprint, or another model, camera or light
  marker whose rotated box overlaps it. A rotated object therefore lands on what its own box is over, not on what
  its axis-aligned bounding box would reach.
- An object sunk into a surface — its lowest point inside a box, or under the terrain — rises onto that surface
  instead of falling through it. A box wholly above the object is ignored.
- The Drop action is **disabled** when the selection has no surface to rest on, and pressing `D` in that state or
  during a gizmo drag does nothing. There is no fallback ground: an object over empty space is left where it is, so
  Drop never invents a height the scene does not have.
- Dropping is idempotent. Heights within 0.0001 world units count as equal, so an object already resting on a
  surface is left alone and no edit is written.
- The move is written to the scene file exactly as a gizmo move is — same fields, same single undoable edit, same
  formatting. Reads are unchanged: `ecs/entities/<id>/components` as the view already reads it.
- **File fields written**: `PositionComponent.localPosition.y`, and for a camera entity also
  `CameraComponent.camera.position.y`, through the existing `editSceneJson` path. **The file format does not
  change**; no key is added, renamed, dropped or reordered.
- Out of scope: a hover ghost showing where the object would land; snapping rotation to a yaw grid; resting an
  object flat on a slope (it settles by height, so a tilted object may touch with one corner); mesh-accurate contact;
  dropping a terrain; a ground plane at `y = 0`; moving an object's children with it. `ParentComponent` is still ignored as it is everywhere else in the
  view, so a drop moves the one entity it was given, exactly as a move drag does.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `scene-object-transform`: new requirements for dropping an object onto the surface under it — the action, its
  triggers and which objects it applies to, what counts as a surface, the resting height it produces, the disabled
  state when there is none, and the edit it writes.

## Impact

- Code: a new pure `ScenePicker.restHeight`, next to the existing `pick`, with a plain `OrientedBox`, that turns a
  footprint and lists of oriented boxes and terrains into a resting height; the target-list construction currently
  inlined in `SceneRenderer.pick`, extracted so both callers share it; a `drawnVersion` on the renderer so the enabled
  state is re-checked when assets finish loading; a drop entry point and `canDrop` on `SceneInteraction` alongside
  `pressed` / `dragged` / `released`; a toolbar button and the `D` binding in `SceneViewPanel`, with its enabled state
  folded into the existing `syncControls`; two strings in `AbyssusBundle.properties`.
- The write reuses `SceneView.onTransform` and `SceneFileEditor.applyTransform` unchanged, so a drop needs no new
  write path and its undo reads as "Move Entity" alongside a gizmo move.
- Threading: the ground query is CPU-only and runs on the AWT thread outside `GdxRuntime.withContext`, like picking.
  It reads the last frame's drawn entities, so it has no answer until that frame's assets are loaded.
- No new dependencies, no change to the scene file format, and no change to the `scene-picking` capability.
- Tests: headless tests for the oriented-box and resting-height maths over hand-built boxes and terrain height
  fields (rotated, wide-over-narrow, sunk, bump between corners, non-uniform terrain scale, pitch/roll, clipped bilinear maxima, tolerance), plus
  interaction tests for the disabled state, the exclusions, the loading re-check, the no-op and the idempotent case.
