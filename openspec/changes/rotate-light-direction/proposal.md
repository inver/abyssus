# Proposal

## Why

A light the Mundus editor creates does not take its direction from its own rotation. Its
`PositionComponent.lookAtId` names a direction handle: a child entity of type `HANDLE`. Mundus's look-at system
turns the light to face that handle every frame and overwrites `localRotation` (see the
`Lights/scenes/Mundus Lights.scene` fixture: directional light `1` looks at handle `0`, spot light `4` at handle `3`).

Abyssus ignores `lookAtId` on lights. It has two problems with such a light:

- It draws and lights the scene with the light facing along its rotated -Z, not toward its handle, so the view
  disagrees with Mundus. In the fixture both lights point straight down in Mundus but along -Z in Abyssus.
- A rotate-gizmo drag writes the light's `localRotation` and leaves the handle where it was. Mundus then turns the
  light back toward the handle, so the turn the user made does not stick when the scene is opened in Mundus.

Lights without a `lookAtId`, which include every light Abyssus's own Add Light creates, already work: a rotate drag
writes `localRotation` and the light's direction is derived from it. This change leaves them alone.

## What Changes

- A directional or spot light whose `lookAtId` names an existing entity faces that entity in the scene view: its
  direction line, its shading and its spot cone. This is the rule Abyssus already applies to look-at cameras.
- A completed rotate drag on a light that looks at a direction handle moves the handle so that the light faces the
  turned direction. The handle stays at the same distance from the light. The handle's
  `PositionComponent.localPosition` is written in the same undoable "Rotate Entity" edit, and the light's own
  `localRotation` is not written, because Mundus recomputes it from the handle.
- While the drag is in progress, the light's marker and lighting show the turned direction.
- A light whose `lookAtId` names an entity that is not a direction handle (a model, a camera) shows no rotate rings,
  as a look-at camera doesn't. Turning it would mean moving an unrelated object.

Out of scope:

- Lights without a `lookAtId`, or whose `lookAtId` does not resolve: they keep today's behavior.
- Point lights: they have no direction, and they keep having no rotate rings.
- Moving a look-at light (Move mode): the handle stays where it is, so the light re-aims at it, as a moved look-at
  camera does. Moving the handle along with the light is not part of this change.
- Selecting or dragging the handle entity itself: Abyssus does not draw or pick handles, and this change doesn't add
  that.
- Writing the light's look-at `localRotation` as Mundus would compute it. Mundus overwrites that value on load, so
  writing it is not needed.
- Spot cone angle, softness, range, color or intensity.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `scene-entity-lights`: a light whose `lookAtId` resolves faces its target.
- `scene-object-transform`: rotate rings for look-at lights; a rotate drag on a light aimed at a direction handle
  writes the handle's position instead of the light's rotation.

## Impact

- `src/main/kotlin/net/nevinsky/abyssus/sceneview/`: `SceneContent` (light look-at targets and which entities are
  handles), `gizmo/GizmoHandles.kt` (`canRotate`), `ScenePreview` (moving the handle during the preview),
  `SceneInteraction` (the edit a rotate drag produces), `SceneTransformWriter` (writing the handle's position).
  The edit still goes through `editSceneJson` via `SceneFileEditor.applyTransform`: no new write path.
- File fields read: `PositionComponent.lookAtId` and `PositionComponent.localPosition` of lights; `TypeComponent.type`
  and `PositionComponent.localPosition` of the look-at target. File field written: the target handle's
  `PositionComponent.localPosition`. The writer adds that object, with `x`, `y` and `z`, when the handle's
  `PositionComponent` is empty, as it already does for any entity.
- The Mundus file format does not change. No key is renamed, dropped or reordered, and no key Mundus doesn't write is
  added. Mundus itself stores a light's aim as its handle's position.
- Tests: the existing `Lights/scenes/Mundus Lights.scene` fixture is used as is (read-only in tests). Headless tests
  cover reading, `canRotate`, the preview, the edit and the write. `runIde` checks cover the drag itself.
- Docs: `docs/ai/file-formats.md` (`lookAtId` also applies to lights) and `sceneview/README.md` ("Objects without
  rotation", look-at lights).
