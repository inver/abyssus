# Proposal

## Why

In the scene view, a selected directional or spot light can be rotated with the rotate gizmo, but the
turn you drag is never written back: the editor records the light's new rotation in memory for the
preview, then drops the turned direction on release, so the file keeps the light pointing where it was.
Cameras already keep the direction you drag; lights need the same treatment so a dragged orientation
stays in the scene.

## What Changes

- After a rotate-gizmo drag on a directional or spot light, the editor writes the light's turned
  `PositionComponent.localRotation` into the scene file, as it already does for a camera.
- While the drag is in progress, the light's marker (its direction line and lighting) shows the exact
  direction being turned, not a direction re-derived from the new rotation.
- A small shared check decides whether an entity has a facing direction (directional and spot lights,
  and cameras) so the preview and the write path agree on which entities carry a direction.

Out of scope:

- Point lights: they have no direction, and the rotate gizmo is already unavailable for them.
- A separate direction field in the file: the light's direction stays derived from
  `PositionComponent.localRotation` as Mundus stores it; the dragged direction is not written to a new
  key.
- Spot cones, light range, or any other light property: only the orientation is affected.

## Capabilities

### New Capabilities

- `abyssus-scene-view`: the scene view's interactive behavior: selecting, the move/rotate gizmos, and the
  transform a completed drag writes to the scene file.

### Modified Capabilities

None. `openspec/specs/` is empty; no existing capability's requirements change.

## Impact

- `src/main/kotlin/net/nevinsky/abyssus/sceneview/`: `SceneInteraction` (which direction a rotate-drag keeps in
  the written edit), `ScenePreview` (which direction a previewed light shows), `SceneContent` (the shared
  "has a facing direction" check). No new write path: the edit still flows through `editSceneJson` via the
  existing transform writer.
- File fields read: `PositionComponent.localRotation`, `PositionComponent.localPosition`,
  `TypeComponent.type`, `LightComponent` (color/intensity/range). File field written:
  `PositionComponent.localRotation` (existing value, new numbers).
- The Mundus file format does not change: no key is renamed, dropped, reordered, or added; only the numbers of an
  existing `localRotation` field are changed, as Mundus itself writes that field when a light is rotated.
- Tests: a new `RotateLight` fixture (directional light `1`, spot light `4`, no `lookAtId` handles) under
  `src/test/testData/project/`; headless tests for the preview and the written edit; a `runIde` check for the
  drag itself.
