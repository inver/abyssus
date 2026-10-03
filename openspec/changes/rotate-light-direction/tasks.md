## 1. Fixture

- [ ] 1.1 Create `src/test/testData/project/RotateLight/` with a `.abss` project file and a `.scene`
  file containing: a ground model `0`, a directional light `1` at `(0, 10, 0)` with identity rotation and
  no `lookAtId`, a spot light `4` at `(0, 5, 0)` with identity rotation and no `lookAtId`, and a point
  light `8` at `(0, 7, 0)` with identity rotation and no `lookAtId`. All lights have a `LightComponent`
  with a non-default `intensity`. Verify by opening the scene in `SceneContentTest` (or a new
  `RotateLightFixtureTest`) and asserting the three lights parse with the expected positions and that
  `ScenePreview.selected` returns a non-null `direction` for `1` and `4` and a null `direction` for `8`.
  Run `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.*'`.

## 2. Shared "has a facing direction" predicate

- [ ] 2.1 Add a function in `SceneContent.kt` (next to `canRotate` and `forward`) that returns true for
  directional and spot lights and for cameras whose `lookAtId` does not resolve to an entity; false for
  point lights, models, and terrains. Refactor `canRotate` to call this function. Verify by running the
  existing `canRotate`-related tests and adding a case to `SceneContentTest` that asserts the predicate
  is true for a directional light, a spot light, and a camera without `lookAtId`, and false for a point
  light, a model, and a camera with a resolving `lookAtId`. Run
  `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneContentTest'`.

## 3. Write path

- [ ] 3.1 In `SceneInteraction.editOf`, change the `direction` gate from
  `ScenePreview.isCamera(...)` to the new shared predicate from task 2.1, so a rotate drag on a
  directional or spot light carries the turned `direction` into the written `TransformEdit`. Verify by
  adding a case to `SceneInteractionTest` that drives a rotate drag on a directional light in
  `RotateLight` and asserts the `TransformEdit` emitted to `onTransform` has the turned `rotation` and
  the turned `direction`. Run
  `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneInteractionTest'`.

## 4. Preview path

- [ ] 4.1 In `ScenePreview.apply`, make the light branch explicitly prefer `result.direction` when it is
  non-null and fall back to `SceneContent.forward(t.rotation)` only when it is null, so the previewed
  light shows the exact dragged direction during a rotate drag. Verify by adding a case to a
  `ScenePreviewTest` (new file) that applies a `DragResult` with a non-null `direction` to a content
  containing a directional light and asserts the previewed light's `direction` equals the dragged one,
  and a second case that applies a move-only `DragResult` (null `direction`) and asserts the previewed
  light's `direction` equals `forward(rotation)`. Run
  `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.ScenePreviewTest'`.

## 5. File write

- [ ] 5.1 Verify that `SceneTransformWriter.apply` writes the rotation from a `TransformEdit` carrying
  both a `rotation` and a `direction` into `PositionComponent.localRotation` of the entity and into
  `CameraComponent.camera.viewPointPosition` only when the entity has a camera, and leaves all other keys
  of the scene file untouched. Add a case to `SceneTransformWriterTest` using the `RotateLight` fixture
  that applies a `TransformEdit(rotation = ..., direction = ...)` to light `1` and asserts the
  `localRotation` field is the new quaternion, no new key was added to `LightComponent`, and the other
  entities are byte-identical. Run
  `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneTransformWriterTest'`.

## 6. Integration

- [ ] 6.1 Run the full plugin test suite and confirm no regressions:
  `./gradlew :test`.

## 7. Manual verification (runIde)

- [ ] 7.1 In a `runIde` session opened on a **copy** of `RotateLight` (never the fixture itself), select
  the directional light `1`, switch the gizmo to rotate mode, drag one of the rings, and release.
  Confirm the light's direction line moved to the turned direction and the scene file on disk now has
  the new `PositionComponent.localRotation`. Leave this task unchecked if the check could not be
  performed, and list the exact steps for the user.
- [ ] 7.2 Repeat task 7.1 for the spot light `4`. Confirm the same behavior.
- [ ] 7.3 Select the point light `8` in rotate mode and confirm no rotate handles are drawn and no
  rotate drag can start.

## 8. Docs and final check

- [ ] 8.1 If `docs/ai/architecture.md`, `docs/ai/glossary.md`, or any package `README.md` under
  `src/main/kotlin/net/nevinsky/abyssus/sceneview/` describes the rotate-gizmo behavior or the
  "which entities have a direction" rule, update it in the same change to reflect that directional and
  spot lights now keep the turned direction. Run `scripts/check-docs.sh` and confirm it passes.
- [ ] 8.2 Run the full verification: `./gradlew check && scripts/check-docs.sh`. Confirm both pass.
