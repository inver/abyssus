# Tasks

## 1. Camera placements

- [x] 1.1 Add `CameraPlacement` (`entityId`, `name`, `position`, `direction`, `lookAtId`, `near`, `far`,
  `fieldOfView`) and `SceneContent.cameras`, plus an `entityPositions` map (entity id → `localPosition`) for look-at
  targets. Verify with `SceneContentTest` cases: the `Untitled` `Main Scene` yields one camera `Camera 4` at about
  (-17.7, 6.1, 0.6) with `lookAtId` 3, near 1, far 100, fov 67; a camera with no `camera` object gets libGDX defaults; a
  camera without `lookAtId` keeps `viewPointPosition` as its direction; cameras are not listed as lights or models
- [x] 1.2 Add `CameraFrustum.of(placement, positions, aspect)` (no GL): the eight corner points and the resolved
  direction (toward the `lookAtId` position when it resolves, else `direction`). Verify with unit tests: a camera at the
  origin looking +X with fov 90, aspect 1, near 1 has near corners at x = 1, y/z = ±1; a resolvable target overrides
  `direction`; an unknown target falls back to `direction`

## 2. Gizmo math (no GL)

- [x] 2.1 Add `sceneview/gizmo/GizmoHandles` (axis segments for Move, rings for Rotate, sized to a constant number of
  pixels from eye distance, fov and view height) and `canRotate` (false for a camera with a resolvable `lookAtId` and for
  a point light). Verify with unit tests: handle length doubles when the eye distance doubles; `canRotate` is false for
  `Camera 4` and a point light, true for a model, terrain, directional and spot light
- [x] 2.2 Add `GizmoHit` (nearest handle under a ray within a pixel tolerance). Verify with unit tests: a ray through
  the X arrow's tip hits X; a ray between the arrows hits nothing; a ray through the Y ring's rim hits Y; with two
  overlapping handles the nearer one wins
- [x] 2.3 Add `GizmoDrag` for Move (closest point on the axis line) and Rotate (signed angle about the axis in the ring
  plane), returning the new `PlacementTransform` and, for cameras, the turned direction. Verify with unit tests: dragging
  X from a ray hitting x = 0 to one hitting x = 5 moves (0,0,0) to (5,0,0) with y and z unchanged; a quarter turn on Y
  rotates the forward axis -Z to -X (or +X, by the sign convention, asserted); rotation keeps the position; a camera's
  direction turns by the same rotation

## 3. Writing transforms

- [x] 3.1 Move `editJson` from `projectView/EnabledToggle.kt` to a public `editSceneJson(project, file, commandName,
  mutate)` and switch `toggleEnabled`, `renameScene` and `setSkybox` to it. Verify the existing
  `SceneEditFormattingTest`, `AbyssusViewTest` and `SkyboxChoicesTest` still pass unchanged
- [x] 3.2 Add `SceneTransformWriter.apply(root, entityId, edit)`: it writes `PositionComponent.localPosition` and/or
  `localRotation` (adding missing objects or fields), and for a camera also `CameraComponent.camera.position` /
  `viewPointPosition`; it returns false when nothing changed or the entity is missing. Verify with unit tests on the
  `Main Scene` JSON: moving entity 0 by +2 on X changes only `localPosition.x`; moving `Camera 4` by +1 on Y changes both
  positions' `y`; rotating an entity without `localRotation` adds `x`,`y`,`z`,`w`; an unknown entity returns false; an
  edit equal to the current values returns false. Then verify through `editSceneJson` on a pretty file that the
  indentation and every other value are kept

## 4. Rendering

- [x] 4.1 Add `SceneMarkers` to `SceneRenderer`: a camera body plus a per-frame frustum for each camera
  (look-at resolved per frame), and light markers tinted by light color with a direction line for directional and spot
  lights; add their world bounds to `pick` as `BoxTarget`s. Verify with `ScenePickerTest` cases that a ray through a
  camera's body or a light's marker picks that entity and the nearest target still wins, and with a `SceneRenderGlTest`
  case (`-Dabyssus.glTests=true`) that `Main Scene` draws one camera marker
- [x] 4.2 Add the selection highlight (wire bounds in the accent color) and the gizmo pass (depth test off, X/Y/Z in
  red/green/blue, hovered handle brighter) for the selected id and mode, plus `gizmoHit(x, y, w, h)` on the CPU camera
  of the last frame, and a preview override (entity id → transform) applied over placements, lights included. Verify
  with unit tests of `gizmoHit` through a renderer whose camera was set by a headless frame, and with a GL test that a
  selected model renders its handles without errors
- [x] 4.3 Add look-through: when `viewCamera` is set, build the frame's camera from that placement instead of the orbit,
  and skip that camera's own markers. Verify with a unit test that the renderer's camera position and direction equal
  `Camera 4`'s (looking at entity 3), and that clearing `viewCamera` restores the orbit eye exactly

## 5. View interaction

- [x] 5.1 In `SceneViewPanel`: keep `selectedId` (a click hit selects and still calls `onPick`; a miss clears; it is
  cleared when new params drop the entity), `mode` (Move initially) and a toolbar with the Move/Rotate toggle and the
  camera combo ("Free camera" plus cameras by name, else id); bind W/E/Esc while the view has focus. Verify with
  `SceneFileEditorTest`-style tests on a fake renderer: click hit / miss / removed entity update `selectedId`; W and E
  set the mode; the combo lists `Free camera` and `Camera 4` for `Main Scene`; removing the looked-through camera
  switches back to Free camera
- [x] 5.2 Route drags: a press on a gizmo handle starts a `GizmoDrag` that updates the preview override; other drags
  orbit or pan unless a camera is looked through, in which case they do nothing; Esc restores the start transform and
  ends the drag; release with a change calls the new `SceneView.onTransform(entityId, edit)`. Verify with unit tests on
  a fake renderer and fake events: handle-press then drag then release emits one `onTransform` and does not touch the
  orbit; empty-space drag orbits; a press and release without movement emits nothing; Esc emits nothing
- [x] 5.3 Wire `onTransform` in `SceneFileEditor` to `editSceneJson` with `SceneTransformWriter`, named "Move Entity" /
  "Rotate Entity", and implement `DocumentReferenceProvider` returning the scene document. Verify with a
  `SceneFileEditorTest` case using a `FakeView`: invoking `onTransform` changes only that entity's position in the file
  and the fake view receives params with the moved placement; `UndoManager.getInstance(project).undo(editor)` restores
  the original text

## 6. Integration

- [x] 6.1 Amend `openspec/changes/render-project-models/specs/scene-picking/spec.md`: picking also covers cameras and
  lights, and a left-drag that starts on a gizmo handle moves the object instead of orbiting. Verify `openspec validate
  render-project-models` and `openspec validate add-scene-cameras-and-gizmos` pass
- [x] 6.2 Run `./gradlew test` (and `./gradlew :test -Dabyssus.glTests=true` on a machine with a display) and verify all
  tests pass
- [ ] 6.3 Verify in `./gradlew runIde` on the `Untitled` project's `Main Scene`:
  - `Camera 4`'s body and frustum point at entity 3, and light markers show.
  - Clicking `Camera 4` selects it in the view and in the tree.
  - W/E switch arrows and rings, and `Camera 4` shows no rings.
  - Dragging Model 0's X arrow moves it live and writes `localPosition.x` on release.
  - Rotating Model 0 about Y writes `localRotation`.
  - Esc cancels a drag.
  - Undo in the scene view tab restores the file.
  - Picking `Camera 4` in the combo looks through it, orbit drags do nothing, and Free camera restores the old view.
