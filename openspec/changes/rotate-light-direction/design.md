# Design

## Context

See proposal.md for motivation. The current code:

- `SceneContent.of` reads every light with `direction = forward(localRotation)` and does not read `lookAtId` for
  lights. It already collects `entityPositions` (every entity with a `PositionComponent`, handles included) and does
  read `lookAtId` for cameras. `CameraFrustum.directionOf` turns a camera toward its resolving target.
- Every consumer of a light's direction reads `LightPlacement.direction`: `SceneLighting` (shading, spot cones),
  `SceneMarkers` (direction line), `ScenePreview.selected` (the gizmo's start direction).
- `canRotate` (`gizmo/GizmoHandles.kt`) refuses look-at cameras and point lights, and accepts every other light.
- A rotate drag yields `DragResult(transform, direction)`. `GizmoDrag` builds the rotation as `delta * start` and the
  direction as `delta(startDirection)`, so the two agree exactly when the start direction is `forward(start)`.
- `SceneInteraction.editOf` sends `TransformEdit(rotation, direction = camera ? direction : null)`.
  `SceneTransformWriter.apply` writes `localRotation` for any entity and uses `direction` only for a camera's
  `viewPointPosition`. So a light without `lookAtId` already round-trips. Only look-at lights are wrong.
- Mundus (and the port in `ecs/system/Systems.kt`, `LookAtSystem`) sets the light's `localRotation` from
  `target.localPosition - light.localPosition` and treats `localPosition` as world coordinates. The scene view already
  ignores `ParentComponent` and does the same.

## Goals / Non-Goals

**Goals:**

- Show a look-at light facing its target, matching Mundus.
- Make a rotate drag on a light aimed at a direction handle stick: move the handle, in the same undoable edit.
- Refuse rotate rings for a light aimed at something other than a handle.

**Non-Goals:** see proposal.md, "Out of scope".

## Decisions

### 1. Resolve a light's look-at target when the scene is read

`SceneContent.of` keeps, for each light, its `lookAtId` (as `LightPlacement.lookAtId`). It also builds a set of the
entity ids whose `TypeComponent.type` is `HANDLE` (`SceneContent.handleIds`). After the entity pass, so that every
position is known, each directional or spot light whose `lookAtId` names an entity in `entityPositions` gets
`direction = normalize(target - position)`. A light at its target's position (zero vector) falls back to
`forward(rotation)`.

Resolving at read time gives every existing consumer (lighting, cones, markers, gizmo start direction) the right
direction without changing them. The rule is the one `CameraFrustum.directionOf` applies. The vector math moves into
a shared helper in `SceneContent` (for example `SceneContent.aim(from, to): Vec3?`), and both use it.

Alternative: resolve in each consumer, as cameras do through `CameraFrustum.directionOf`. Rejected: lights have three
consumers, and a direction that is already resolved can't be forgotten in one of them.

### 2. Which lights get rotate rings

`canRotate` returns false for a light whose `lookAtId` resolves to an entity that is not in `handleIds`. A light
aimed at a handle, and a light with no resolving `lookAtId`, keep their rings. A point light still has none. The
function stays the single place that answers "does this entity get rotate rings".

### 3. The edit a rotate drag on a handle-aimed light produces

`TransformEdit` gets an optional `target: TargetMove(entityId, position)`. For a rotate drag on a light whose
`lookAtId` names a handle, `SceneInteraction.editOf` builds:

- `rotation = null`: the light's own `localRotation` is left as it is (Mundus recomputes it).
- `target = TargetMove(handleId, lightPosition + turnedDirection * distance)`, where `distance` is the light-to-handle
  distance when the drag started. A distance of zero (a degenerate handle) uses 1.

For every other entity `editOf` is unchanged. The computation is a pure function (`ScenePreview.aimedTarget(content,
id, result)` or similar). `editOf` and the preview both call it, so they agree.

`SceneTransformWriter.apply(root, entityId, edit)` writes `edit.target` as the target entity's
`PositionComponent.localPosition`, using the existing `setVec` (only the differing fields when the object exists; all
three when it is missing). It returns true when anything changed. The whole edit is still one `editSceneJson` command,
"Rotate Entity" (because the command name keys on a rotate, `SceneFileEditor.applyTransform` checks
`edit.rotation != null || edit.target != null`).

Alternative: write the light's `localRotation` as Mundus's look-at system would compute it, as well as the handle.
Rejected: Mundus overwrites it on load, and the project rule is to change only the values an edit is about.

Alternative: give look-at lights no rotate rings, like look-at cameras. Rejected: every light Mundus creates has a
handle, so Mundus lights could never be turned in Abyssus.

### 4. The preview

`ScenePreview.apply` for a light already takes `result.direction` when it is non-null. For a handle-aimed light it
also moves the handle's entry in `entityPositions` to the target position from Decision 3, so anything that reads
target positions during the drag agrees with the drawn direction. During a Move drag (`result.direction` comes from
the start), a look-at light is re-aimed from its new position at its unmoved target. That is what the file will
say after release, so the light doesn't jump on release.

### 5. Fixture

Tests use `src/test/testData/project/Lights/scenes/Mundus Lights.scene` as is: real Mundus output. Directional light
`1` is at (0, 10, 0) and looks at handle `0` at (0, 0, 0). Spot light `4` is at (0, 5, 0) and looks at handle `3` at
(0, 0, 0). Both handles have an empty `PositionComponent`. Tests parse it into memory and never write it back. A
light aimed at a non-handle entity is built from inline JSON in the test, as `SceneInteractionTest` already does for
a point light.

## Risks / Trade-offs

- [Moving a Mundus light now re-aims it] Today a moved Mundus light keeps facing along -Z. After this change it
  keeps facing its handle, so its direction changes as it moves. This matches Mundus, where the handle stays put
  under the same rule. Moving the handle with the light is out of scope.
- [Handle coordinates] The fixture's handles are children (`ParentComponent`) with world-valued `localPosition`, as
  the ported `LookAtSystem` treats them. If some Mundus version stored handle positions relative to the parent, both
  the read and the write would be off by the light's position. Mitigation: runIde check 7.3 opens the edited copy in
  Mundus, or compares with a Mundus-saved file, when one is at hand. Otherwise it is listed as unverified.
- [Writing an empty `PositionComponent`] The handle's `{}` becomes `{"localPosition": {x, y, z}}`. That is what Mundus
  writes for a moved handle, and what the writer already does for any entity.

## Threads

Everything new is CPU-only, with no GL and no Swing: `SceneContent.of` (wherever the scene is parsed today),
`canRotate`, `ScenePreview` and the edit computation (on the AWT thread that renders the canvas, inside the existing
`SceneInteraction` callbacks), and `SceneTransformWriter` (inside the `editSceneJson` command, as today). No GL call
is added: the marker and lights are drawn from `LightPlacement.direction` as before.

## Verification

- Headless: `SceneContentTest` checks the look-at directions read from `Mundus Lights.scene`.
- Headless: `GizmoHandlesTest` checks `canRotate`.
- Headless: a new `ScenePreviewTest` checks the preview, including the moved handle.
- Headless: `SceneInteractionTest` checks the edit a rotate drag produces.
- Headless: `SceneTransformWriterTest` checks the write.
- runIde: tasks 7.x.
