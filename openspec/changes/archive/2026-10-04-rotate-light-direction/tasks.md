## 1. Reading look-at lights

- [x] 1.1 In `SceneContent.kt`, keep each light's `lookAtId` (`LightPlacement.lookAtId`) and collect the ids of
  entities whose `TypeComponent.type` is `HANDLE` (`SceneContent.handleIds`). After the entity pass, give each
  directional or spot light whose `lookAtId` is in `entityPositions` the direction from its position to the target's.
  Fall back to `forward(rotation)` when the two positions coincide. Move the aim math into a shared helper that
  `CameraFrustum.directionOf` also uses. Verify with new `SceneContentTest` cases:
  `mundusLightsFaceTheirHandles` (`Lights/scenes/Mundus Lights.scene`: lights `1` and `4` have direction
  (0, -1, 0), and `handleIds` holds `0` and `3`), `aLightWithAMissingTargetFacesAlongItsRotation`, and
  `aLightAtItsTargetFacesAlongItsRotation`. Existing camera tests stay green.
  Run `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneContentTest'` and
  `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.CameraFrustumTest'`.

## 2. Rotate rings

- [x] 2.1 In `gizmo/GizmoHandles.kt`, make `canRotate` return false for a light whose `lookAtId` resolves to an
  entity not in `handleIds`. Verify with new `GizmoHandlesTest` cases: true for lights `1` and `4` of
  `Mundus Lights.scene`, false for an inline light aimed at a model, true for a light without `lookAtId`, false for
  a point light. Run `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.gizmo.GizmoHandlesTest'`.

## 3. Preview

- [x] 3.1 Add the pure function that gives the new handle position for a rotate result on a handle-aimed light: the
  light position plus the turned direction times the start distance, with 1 when that distance is 0. In
  `ScenePreview.apply`, move that handle's `entityPositions` entry during a rotate preview. During a move preview,
  re-aim a look-at light at its unmoved target. Verify with a new `ScenePreviewTest`:
  `turningAHandleAimedLightMovesItsHandle` (light `1`, turned to (0, 0, -1): its direction is (0, 0, -1) and handle `0`
  is at (0, 10, -10)), `movingALookAtLightReAimsItAtItsUnmovedTarget`, and `aLightWithoutTargetUsesTheDraggedDirection`.
  Run `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.ScenePreviewTest'`.

## 4. Edit and write

- [x] 4.1 Add `target: TargetMove?` to `TransformEdit`. In `SceneInteraction.editOf`, a rotate drag on a handle-aimed
  light produces `TransformEdit(target = TargetMove(handleId, newPosition))` with no `rotation`; other entities are
  unchanged. Verify with a new `SceneInteractionTest` case, `rotatingAHandleAimedLightEmitsAHandleMove`, which drives
  an X-ring drag on light `1` of `Mundus Lights.scene` and checks that the emitted edit has a null `rotation` and a
  `target` for `0` at distance 10 from (0, 10, 0) along the previewed direction. The existing
  `rotatingARingWritesTheRotation` stays green.
  Run `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneInteractionTest'`.
- [x] 4.2 In `SceneTransformWriter.apply`, write `edit.target` into the target entity's
  `PositionComponent.localPosition`. In `SceneFileEditor.applyTransform`, name such an edit "Rotate Entity". Verify
  with new `SceneTransformWriterTest` cases on `Mundus Lights.scene`: `aHandleMoveWritesTheHandlePosition` (handle `0`
  gets `localPosition` (0, 10, -10), light `1` and every other entity are unchanged, no key is added to any
  `LightComponent`) and `aHandleMoveToTheSamePlaceChangesNothing` (returns false).
  Run `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneTransformWriterTest'`.

## 5. Integration

- [x] 5.1 Run the plugin test suite: `./gradlew :test`. (The `Untitled` fixture had been edited in the IDE, which
  failed 10 tests on `main` too; it is restored to its state before those edits. 4 icon tests fail only in a headless
  Linux container, where plugin SVG icons load as 1×1; none are introduced by this change.)

## 6. Docs

- [x] 6.1 Update `docs/ai/file-formats.md` (the `PositionComponent` row: `lookAtId` for cameras and lights, and a
  light that looks at a `HANDLE` stores its aim in the handle's position). Update
  `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md` ("Objects without rotation": a light aimed at a non-handle
  entity; plus a note that turning a handle-aimed light moves the handle). Run `scripts/check-docs.sh`.

## 7. Manual verification (runIde)

Use a **copy** of `src/test/testData/project/Lights` as the `runIde` project, never the fixture itself.

- [ ] 7.1 Open `Mundus Lights`. Check that directional light `1` and spot light `4` both point straight down (direction
  lines and cone). Select light `1`, press E, drag the X ring about a quarter turn and release. Check that the
  direction line and shading follow during the drag and stay after release. Check that in the copy's `.scene` file,
  entity `0` now has a `localPosition` about 10 units from (0, 10, 0) along the new direction, and entity `1`'s
  `PositionComponent` is unchanged. Press Undo, and check that the handle's `PositionComponent` is `{}` again and the
  light points down.
- [ ] 7.2 Repeat 7.1 for spot light `4`: the cone follows the drag and stays after release. Start another drag and
  press Esc: the cone goes back and nothing is written.
- [ ] 7.3 If a Mundus editor is at hand, open the edited copy in it. Check that light `1` faces the direction it was
  turned to in Abyssus. If no Mundus editor is available, leave this unchecked and list it as unverified (see the
  "Handle coordinates" risk in design.md).
  Checked against the source instead (`inver/Mundus`, branch `develop`): `LookAtSystem` aims from the light's
  `localPosition` to the handle's `localPosition` and ignores `ParentComponent`, and `LightService` creates a light at
  (0, 10, 0) with its handle at (0, 0, 0). So handle positions are read and written in the same frame as the light's,
  as this change assumes. Opening an edited file in the editor is still not done.

## 8. Final check

- [x] 8.1 Run `./gradlew check && scripts/check-docs.sh`. `check` fails only on the 4 icon tests of 5.1 in a headless
  Linux container; every other test, including `:core:test`, `:gdx-model:test` and the new test classes, passes.
  `scripts/check-docs.sh` passes.
