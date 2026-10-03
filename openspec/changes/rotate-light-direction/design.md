# Design

## Context

See proposal.md for motivation. The current state that shapes the approach:

- A rotate drag already produces `DragResult(transform, direction)` where `transform.rotation` is the
  turned rotation and `direction` is the start direction turned by the same delta. Both are computed in
  `GizmoDrag` (CPU, no GL).
- On release, `SceneInteraction.editOf` builds the `TransformEdit` that is written. It keeps
  `direction` only when the entity is a camera:
  `TransformEdit(rotation = result.transform.rotation, direction = result.direction.takeIf { ScenePreview.isCamera(...) })`.
  For lights, `direction` is dropped, so the file keeps the old rotation.
- While the drag is in progress, `ScenePreview.apply` recomputes the previewed light's direction as
  `result.direction ?: SceneContent.forward(t.rotation)`. Because the light's `result.direction` is
  always present during a rotate drag, the preview already shows the exact dragged direction — but only
  because of the `?:` fallback branch, not because the code intends it. The fallback is used for
  non-rotate edits (e.g. a move) where `result.direction` is null.
- The shared "which entities have a facing direction" question is answered ad hoc: `canRotate`
  (SceneContent.kt) checks cameras-without-lookAt and non-point lights; `ScenePreview.selected` returns
  a null `direction` for models/terrains/point-lights; `editOf` checks `ScenePreview.isCamera`. Three
  slightly different predicates answer the same question.

## Goals / Non-Goals

**Goals:**

- Write the turned rotation of a directional/spot light to the file on a completed rotate drag.
- Show the exact dragged direction in the preview while the drag is in progress.
- Use one shared predicate for "this entity has a facing direction" so the preview and the write path
  agree.

**Non-Goals:**

- No new file field for the light's direction; the direction stays derived from `localRotation`.
- No change to point lights (no rotate handles, no direction written).
- No change to spot cones, light range, or other light properties.
- No change to the camera path (already correct).

## Decisions

### 1. Keep the direction on the write path for directional and spot lights

`SceneInteraction.editOf` currently gates `direction` on `ScenePreview.isCamera`. Extend the gate to
also pass the direction through when the entity is a directional or spot light. The written
`TransformEdit` then carries `direction`, and `SceneTransformWriter` already writes the rotation into
`PositionComponent.localRotation` for any entity; the `direction` field on the edit is what tells the
preview (and any future consumer) that this light's direction is the turned one.

Alternative considered: write a new `direction` key into `LightComponent`. Rejected — it would add a
Mundus file field that Mundus does not write, violating the file-format compatibility constraint. The
direction is derivable from `localRotation`, which is what Mundus writes.

### 2. Prefer the exact dragged direction in the preview

`ScenePreview.apply`'s light branch currently computes
`direction = result.direction ?: SceneContent.forward(t.rotation)`. The `?:` already prefers
`result.direction` when present, so the preview already shows the exact dragged direction during a
rotate drag. Make this explicit and robust by changing the branch to use `result.direction` whenever it
is non-null (the rotate case) and only fall back to `forward(t.rotation)` when it is null (the move
case, where the rotation is unchanged and `forward` of the unchanged rotation is the correct direction).
This is a no-op behaviorally for the rotate case but documents intent and protects against a future
change to `GizmoDrag` that stops populating `direction`.

Alternative considered: leave the `?:` as is. Rejected — the current code works but the fallback branch
is not obviously "the move case"; a reader might think the fallback is a bug. Making the preference
explicit costs nothing.

### 3. One shared predicate for "has a facing direction"

Add a small function in `SceneContent` (next to `canRotate` and `forward`) that answers "does this
entity have a facing direction the user can turn?" and returns true for directional and spot lights and
for cameras (excluding cameras whose `lookAtId` resolves, whose direction is not user-controlled). Use
it in:

- `canRotate` (already encodes this for the rotate-gizmo availability).
- `SceneInteraction.editOf` (to decide whether to keep `direction` in the written edit).
- `ScenePreview.selected` (to decide whether to return a non-null `direction`).

This replaces the three ad-hoc predicates with one. The predicate is CPU-only, no GL, no Swing, so it
belongs in the same file as the existing `forward` and `canRotate` helpers.

Alternative considered: keep `ScenePreview.isCamera` and add a parallel `ScenePreview.isDirectionalOrSpotLight`. Rejected — two predicates that must be kept in sync are a maintenance hazard; one predicate is clearer.

### 4. The `RotateLight` fixture

Create `src/test/testData/project/RotateLight/` with a single `.scene` file (the `RotateLight.abss`
project file references it) containing:

- A ground model at the origin.
- A directional light at `(0, 10, 0)`, identity rotation, no `lookAtId`.
- A spot light at `(0, 5, 0)`, identity rotation, no `lookAtId`.
- A point light at `(0, 7, 0)`, identity rotation, no `lookAtId`.

No `lookAtId` on any light: the `Lights` fixture uses `lookAtId` to point the lights at handle
entities, which would make their direction not user-controlled and would block the rotate gizmo.
Identity rotation gives a known start value (`forward(identity) = (0, 0, -1)`) for tests that rotate the
light and assert on the result.

The fixture is a new directory, not a change to `Lights` or `Untitled`, so existing tests that pin
values in those fixtures are unaffected.

## Risks / Trade-offs

- [The written rotation and the turned direction disagree by a second-order amount] The file stores
  `localRotation`; a re-parse derives the direction as `forward(localRotation)`. Because the rotation is
  `startRotation * delta` and the direction is `delta.transform(startDirection)`, the two agree up to
  the non-commutativity of quaternion rotation in 3D. The difference is below rendering precision and is
  the same pragmatic stance cameras already take (cameras store `viewPointPosition` directly, so they
  round-trip exactly; lights cannot without a new file field). Mitigation: document this in the spec
  ("within rendering precision") and in the fixture notes.
- [A future change to `GizmoDrag` that stops populating `direction` would silently regress the
  preview] The explicit preference in `ScenePreview.apply` (Decision 2) makes the fallback branch
  visible, so a regression would be caught in code review.
- [The new predicate could drift from `canRotate` if one is updated and not the other] By having
  `canRotate` call the shared predicate, they cannot drift.

## Threads

All new logic is CPU-only and runs on the AWT thread that renders the canvas, inside the existing
interaction callbacks (`SceneInteraction.pressed`/`dragged`/`released`) and the preview
(`ScenePreview.apply`). No new GL calls are introduced; the light's marker line is already drawn from
the preview content in `SceneRenderer.drawOverlays`. No new thread is created.

## Verification

- Headless: `SceneTransformWriterTest` (or a new case) asserts that a `TransformEdit` with a rotation
  and a direction writes the rotation into `PositionComponent.localRotation` and leaves the rest of the
  file untouched.
- Headless: a `SceneInteractionTest` case asserts that a rotate drag on a directional light in
  `RotateLight` produces a `TransformEdit` whose `rotation` is the turned rotation and whose `direction`
  is the turned direction.
- Headless: a `ScenePreviewTest` case asserts that a previewed light shows the exact `result.direction`
  when present.
- runIde: open `RotateLight`, select the directional light, switch to rotate mode, drag a ring, release;
  confirm the direction line moved and the scene file now has the new `localRotation`. Confirm the same
  for the spot light. Confirm the point light has no rotate handles.
