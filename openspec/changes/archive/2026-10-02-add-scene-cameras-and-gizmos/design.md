# Design

## Context

- **Data path:** `SceneFileEditor` reads the scene (and its project's `mainCamera`) through
  `SceneParamsSource.EDITOR_TEXT` into `SceneRenderParams`, and re-reads on every document change. `SceneContent.of`
  turns the `ecs` JSON into `models`, `terrains` and `lights` placements. Camera entities fall through. In the
  `Untitled` fixture, entity 4 has `TypeComponent` `CAMERA`, an editor-only `RenderComponent`, `lookAtId` 3 and
  `CameraComponent.camera` (`position`, `viewPointPosition` = view direction, `near` 1, `far` 100, `fieldOfView` 67).
- **Rendering:** `SceneRenderer.render(width, height, orbit, delta)` sets a `PerspectiveCamera` from `OrbitCamera` and
  draws the skybox, grid, terrains and models. `pick(...)` builds a ray from the last frame's camera and tests model
  bounds and terrain heights on the CPU (`ScenePicker`), returning an entity id.
- **Input:** `SceneViewPanel` gets AWT mouse events on the GL canvas. A `ClickGesture` tells a click from a drag, a left
  drag orbits, any other drag pans, and the wheel zooms. A click is reported through `SceneView.onPick` to
  `selectEntityInAbyssusView`. The view keeps no selection.
- **Writing:** `projectView/EnabledToggle.kt` has a private `editJson(project, file, mutate)`. It parses the document,
  mutates the Jackson tree, re-serializes in the file's own style (`SceneJson.inStyleOf`), writes in a
  `WriteCommandAction`, saves, and refreshes the Abyssus pane. Its edits change only the touched values.
- **Coordinates:** placements use `localPosition` / `localRotation` / `localScale` as world values; `ParentComponent`
  is not applied anywhere in the view.

## Goals / Non-Goals

**Goals:**
- Keep gizmo math (handle geometry, ray hits, drag → transform) free of GL and Swing, so it is unit-tested like
  `ScenePicker`.
- One write path for scene edits, the same formatting-preserving edit the tree already uses.
- A drag is one undoable edit, written once on release, not per mouse move.

**Non-Goals:**
- Scale handles, local-space handles, snapping, multi-select, typed numeric input.
- Applying `ParentComponent` hierarchies: an entity is moved in the same space the view already draws it in
  (`localPosition` as world).
- Selecting in the view from the Abyssus tree (tree → view sync). The view's selection comes from its own clicks.
- Creating or deleting entities, or editing camera near/far/fov.
- Following a moving camera's view frame by frame while looking through it beyond re-reading after each write.

## Decisions

### Camera placements in `SceneContent`
Add `cameras: List<CameraPlacement>` with `entityId`, `name`, `position`, `direction`, `lookAtId`, `near`, `far` and
`fieldOfView`. The position comes from `PositionComponent.localPosition`, falling back to `camera.position`, and
`direction` from `camera.viewPointPosition`. The look-at target is resolved per frame in the renderer from the other
placements' positions, plus the positions of entities that only have a `PositionComponent` (kept in a small
`entityPositions` map), so moving entity 3 turns `Camera 4`. Missing fields use libGDX `PerspectiveCamera` defaults.
*Alternative:* load the ECS (`SceneEcsLoader`) and run `SynchronizeCameraComponentSystem`. Rejected for now: the view's
content path is the JSON placements; mixing in the engine would mean two models of the same scene.

### Markers and highlight
- **Drawn per frame:** `SceneMarkers` writes lines into a shared `LineBatch` (one dynamic mesh with its own `lines`
  shader). Each camera gets a body (box plus lens) and a frustum computed from its placement and the view aspect. Light
  markers are small octahedra tinted with the light color; directional and spot lights get a 2-unit direction line.
- **Passes:** markers are drawn with depth testing on. The selection highlight and the gizmo are drawn afterwards in a
  second pass with depth testing off.
- **Picking:** markers have CPU-side world bounds, which `pick` adds as `BoxTarget`s, so cameras and lights are
  pickable with the existing nearest-hit rule.
- **Highlight:** the selected object gets a wire bounding box in the accent color. It uses the model bounds for
  models, the terrain extent for terrains, and the marker box for cameras and lights.

### Gizmo module (no GL)
`sceneview/gizmo/`:
- **`GizmoHandles`:** for an origin, mode, eye position and pixel scale, it gives three axis segments (Move) or three
  circles (Rotate). Their length or radius is `GIZMO_PIXELS * distance * tan(fov/2) * 2 / viewHeight`, so the gizmo
  keeps a constant size on screen.
- **`GizmoHit`:** the handle under a ray. For segments it is the closest distance between the ray and the segment
  within a pixel tolerance turned into world units. For rings it intersects the ray with the ring's plane and checks
  `|r - radius|` within the tolerance. The nearest hit wins.
- **`GizmoDrag`:** started with the handle, the object's start transform and the start ray.
  - *Move:* project each new ray onto the axis line (closest point between two lines), and the translation is the
    difference from the start point along the axis.
  - *Rotate:* intersect each new ray with the ring plane; the angle is the signed angle between the start and current
    vectors from the origin, about the axis.
  - It returns a new `PlacementTransform` (and, for cameras, the rotated direction).
- **Depth:** handles are drawn after content with depth testing off, X/Y/Z in red/green/blue, and the hovered handle
  brighter.

### Which objects rotate
`canRotate` is false for a camera whose `lookAtId` resolves, and for a point light. They only get Move handles. Spot
and directional lights, models, terrains and cameras without a target rotate. Rotating a camera turns `direction` by
the same quaternion delta.

### Selection and modes in the view
`SceneInteraction` (no Swing, no GL) holds the logic: `selectedId`, `mode` (MOVE initially), `viewCamera` and the
active `GizmoDrag`, with the state mirrored on the renderer. `SceneViewPanel` only forwards mouse and key events to it.
On a click it picks; a hit sets `selectedId` and still calls `onPick` (tree selection as today), and a miss clears it.
When new params no longer contain `selectedId` or the looked-through camera, they are cleared. `LightPlacement` and
`CameraPlacement` carry their `rotation` so a drag can start from it.

### Toolbar and keys
A small Swing panel at the top has a Move/Rotate toggle pair and a camera combo box ("Free camera" plus the cameras by
name, or their id when unnamed). W and E work through `registerKeyboardAction` on the panel, with
`WHEN_ANCESTOR_OF_FOCUSED_COMPONENT`, and the canvas requests focus on a mouse press. Esc cancels an active drag.
*Alternative:* IDE actions with keymap entries. Rejected: W/E would collide with typing elsewhere; panel-scoped
bindings only fire while the view is focused.

### Drag routing
On press with a selection, `SceneInteraction` asks the renderer for a gizmo hit under the cursor, using the CPU camera
from the last frame like `pick`. A hit starts a `GizmoDrag`, and moves update a *preview override* (entity id →
transform) that the renderer applies over the placements. A miss falls back to today's orbit and pan. On release with a
changed transform, it calls `SceneView.onTransform(entityId, edit)`, which returns whether the scene was written; when
it was not, the preview is dropped. Otherwise the override stays until the next params arrive, so the object doesn't
jump back between release and re-read.

### Writing the edit
- **`SceneTransformWriter`:** a pure function `apply(root, entityId, edit: TransformEdit): Boolean` over the scene JSON.
  It sets `localPosition` and/or `localRotation` on `ecs.entities.<id>.components.PositionComponent` (creating missing
  objects or fields), and for a camera also `CameraComponent.camera.position` / `viewPointPosition`. It returns false
  when nothing changed. Numbers are written as floats.
- **Shared edit:** `SceneFileEditor` wires `onTransform` to a shared `editSceneJson(project, file, commandName, mutate)`,
  which is today's private `editJson` moved to a public helper. The tree toggle, Rename Scene and the skybox chooser
  call the same helper, so every write keeps formatting and refreshes the pane identically.
- **Re-read:** the write changes the document, and `SceneFileEditor`'s document listener re-reads it.

### Undo inside the scene view
`SceneFileEditor` implements `DocumentReferenceProvider` and returns the scene document. That way Undo/Redo in the
scene view tab reach the `WriteCommandAction` edits made there. Each edit is one command named "Move Entity" or
"Rotate Entity".
*Alternative:* our own undo stack. Rejected: the platform already records the document edit, and both the text tab and
the scene view would see the same history.

### Look through a camera
The panel keeps `viewCamera: String?`. When it is set, the renderer builds its `PerspectiveCamera` from that camera's
placement (target resolved as for drawing) instead of the orbit, skips that camera's own markers, and the mouse
handler ignores orbit, pan and zoom. Picking and gizmo drags still use the active camera. When the camera disappears
from new params, `viewCamera` resets to null. The orbit state is never touched while looking through, so switching
back restores it.

## Risks / Trade-offs

- **[Parent hierarchies are ignored]** → Moving a child writes its `localPosition` as if it were world. This matches
  how the view already draws it. A later change that applies `ParentComponent` must convert drag deltas into the
  parent's space.
- **[`DocumentReferenceProvider` undo not reached]** if the platform doesn't route undo for a non-text editor →
  verify in `runIde`. The fallback is an `UndoableAction` registered through `UndoManager.undoableActionPerformed`.
- **[Float formatting churn]** → only the written fields change, and `SceneJson` keeps the original number text
  elsewhere. Written values may read like `5.0000005`; that's acceptable for Mundus.
- **[Thin lines under HiDPI]** → handles use pixel tolerances scaled by the framebuffer/component ratio, the same ratio
  `pick` already applies.
- **[A drag racing an external file change]** → on release, the writer reads the current document text and applies only
  the dragged fields to that entity. If the entity is gone, nothing is written and the drag ends.
- **[Overlap with render-project-models' `scene-picking` delta]** → that delta says every left-drag orbits and only
  models and terrains are picked. tasks.md amends it in place so the two changes agree when archived.
