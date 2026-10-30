# Tasks

## 0. Check the fixture

- [x] 0.1 Before writing code, confirm the spec's named scenarios are valid drops in
      `src/test/testData/project/Untitled/scenes/Main Scene.scene`: through `TerrainData`, compute the terrain's highest
      height under the footprints of `Model 0`, `Model 2` and `Camera 4`, and check each differs from the entity's lowest
      point by more than 0.0001. Check that `Camera 4`'s `localPosition` equals its `camera.position`. Already checked
      for the terrain: it is flat, height 0.0, under all of `Model 0`, `Model 2`, `Camera 4` and `Model 6`, and
      `Camera 4` (y 12.3) is a valid drop; a model's own lowest point needs its loaded bounds, which only a GL context
      gives, so a model's validity is confirmed in 4.2. Do not expect the fixture to show a rotated-versus-axis-aligned
      or bump difference. Verify: a
      throwaway test or scratch script prints the heights; if any entity is already resting or has nothing under it,
      replace it in the spec scenarios and in 4.2 with one that is a valid drop, and re-run
      `openspec validate add-scene-object-drop --strict`

## 1. The resting-height query

- [x] 1.1 Add `OrientedBox`: 8 world corners from a local `BoundingBox` and a `Matrix4`, with its x/z convex hull,
      `bottom` and `top`, and a separating-axis overlap test between two hulls. Keep it free of Swing, GL and the
      platform. Verify: new cases in `ScenePickerTest` — `anIdentityMatrixLeavesTheCornersUnmoved`,
      `rotatingTheBoxRotatesItsCorners`, `bottomAndTopUseAllEightCorners`, `overlappingHullsOverlap`,
      `aRotatedBarDoesNotOverlapABoxOnlyItsAxisAlignedBoundsReach` — pass with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.ScenePickerTest'`
- [x] 1.2 Add `ScenePicker.restHeight(footprint, boxes, terrains): Float?` over box surfaces only: a box counts when its
      hull overlaps the footprint and its `bottom < low + eps`, and offers its `top`; the result is the maximum or
      `null`. Verify: `restsOnTheHighestBoxBelow`, `aBoxWhollyAboveIsIgnored`, `aSunkObjectRisesOntoTheBox`,
      `aWideFootprintOverANarrowBoxRestsOnIt`, `noSurfaceBelowReturnsNull` in `ScenePickerTest` pass with the same
      command
- [x] 1.3 Add terrain surfaces by maximising world Y on each transformed bilinear grid cell within the footprint,
      using feasible corners, constraint intersections, boundary stationary points and interior stationary points.
      Cull disjoint cells; return null outside the terrain or for a singular transform. Verify:
      `restsOnTheTerrainHeightUnderTheFootprint`, `restsOnTheHigherOfTerrainAndBox`, `findsABumpBetweenTheCorners`,
      `staysExactUnderANonUniformTerrainScale`, `aFootprintOffTheTerrainGetsNoTerrainHeight`,
      `anObjectUnderTheTerrainRisesToItsSurface`, `aTiltedTerrainUsesTheActualWorldColumn`,
      `aClippedBilinearCellIncludesItsBoundaryMaximum` and `aSingularTerrainOffersNoSurface` in `ScenePickerTest`
      pass with the same command
- [x] 1.4 Cover the tolerance the specs depend on: an object whose lowest point is within 0.0001 of the rest height is
      already resting, on either side. Verify: `aRestHeightWithinEpsAboveIsResting`,
      `aRestHeightWithinEpsBelowIsResting`, `aSecondDropAfterARotatedFirstDropIsANoOp` in `ScenePickerTest` pass with
      the same command

## 2. Supplying the query's inputs

- [x] 2.1 Extract the target-list construction currently inlined in `SceneRenderer.pick` into one place that `pick` and
      the drop both use: each drawn model's local box and matrix, the marker boxes from `SceneMarkers.targets` (which
      already skips the view camera), and the terrain targets. `pick` derives its world-axis `BoxTarget`s from it,
      leaving picking behaviour unchanged. Verify: the existing picking cases in `ScenePickerTest` and
      `SceneInteractionTest` pass, and a case in `SceneMarkersTest` (`theViewCameraHasNoMarkerTarget`) that
      `targets(content, skipCamera = "4")` leaves out `Camera 4`, with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.ScenePickerTest' --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneInteractionTest' --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneMarkersTest'`
- [x] 2.2 Add `SceneRenderer.groundBelow(entityId): Float?`: `null` for a terrain, for the view camera and for an
      entity not drawn; otherwise the entity's `OrientedBox` as footprint against every other target. Do not reuse
      `boundsOf`, which returns the world axis-aligned box. Verify: a case in `SceneRenderGlTest` that a drawn model
      over the terrain reports the terrain's height and a terrain reports `null`, passing with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneRenderGlTest' -Dabyssus.glTests=true` on a machine
      with a display; if no display is available, record that and leave the runIde check in 4.2 to cover it
- [x] 2.3 Add `SceneRenderer.drawnVersion`, bumped when `models.drawn` or `terrains.drawn` gains or loses an entity.
      Verify: a case in `SceneRenderGlTest` that the version changes once the fixture's models finish loading and not
      on a frame that draws the same entities, with the same command and the same no-display rule

## 3. The drop in the interaction layer

- [x] 3.1 Add `SceneInteraction.canDrop` and `drop()`. `canDrop` is false with no selection, during a drag, or when the
      ground lookup returns `null` or a height within 0.0001 of the lowest point. `drop()` re-runs the lookup and, when
      it moves the object, sets the preview and calls `onTransform` with a `TransformEdit(position = ...)` that changes
      only `y`. Take the ground lookup as an injectable function defaulting to the renderer, like `onPick` and
      `onTransform`. Verify: new cases in `SceneInteractionTest` — `canDropIsFalseWithNothingSelected`,
      `canDropIsFalseWithNothingBelow`, `canDropIsFalseDuringADrag`, `canDropIsFalseForAnAlreadyRestingObject`, `dropWithNothingBelowReportsNoTransform`,
      `anAlreadyRestingObjectReportsNoTransform`, `dropReportsAYOnlyPositionTransform`,
      `dropCanRaiseASunkObject` — pass with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneInteractionTest'`
- [x] 3.2 Re-check `canDrop` after a frame whose `drawnVersion` differs from the last seen one, firing `onStateChanged`
      only when the answer flips. Verify: `canDropTurnsOnWhenTheDrawnListsChange` and
      `anUnchangedDrawnVersionFiresNoStateChange` in `SceneInteractionTest` pass with the same command
- [x] 3.3 Confirm the drop's edit writes what the spec requires through the existing writer, with no new write path and
      no new command string. Verify: new cases in `SceneTransformWriterTest` — a `TransformEdit` whose position differs
      only in `y` changes only `PositionComponent.localPosition.y` and keeps the `x` and `z` text, and for an entity
      with a `CameraComponent` whose `camera.position` equals `localPosition` it changes only `camera.position.y`, by
      the same amount — pass with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneTransformWriterTest'`; existing
      `SceneFileEditorTest` cases pass unchanged
- [x] 3.4 Confirm the drop reaches the file as one undoable command and that Undo in the scene view tab reverts it.
      Verify: a new case in `SceneFileEditorTest` invoking `applyTransform` with the drop's edit, asserting one command
      and an Undo back to the original text, passes with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneFileEditorTest'`

## 4. The button and the key in the view

- [x] 4.1 Add the Drop toolbar button (a plain button, not a toggle) after Move and Rotate, bound to
      `interaction.canDrop` in `syncControls`, and bind `D` in `bindKeys` beside `W` and `E`, calling
      `interaction.drop()`. Add `sceneViewDrop` and `sceneViewDropTooltip` to `AbyssusBundle.properties`, matching the
      existing tooltips' trailing key hint ("... (D)"). Verify: `./gradlew :test` passes; the button logic is all in
      `canDrop`, covered by 3.1 and 3.2, so no assertion is added to Swing code
- [ ] 4.2 In a copy of the test project (never `src/test/testData/project/Untitled` itself), run `./gradlew runIde` and
      check by hand: (1) selecting `Model 0` in `Main Scene` enables Drop and pressing `D` settles it on the terrain,
      with only `localPosition.y` changed in the file; (2) with the free camera, selecting `Camera 4` and pressing `D`
      moves the camera marker and changes `localPosition.y` and `camera.position.y` by the same amount; (3) selecting
      the rotated `Model 2` and pressing `D` settles it on the flat terrain, and (with a model placed under only its
      axis-aligned box in the copy) not on that model; (4) selecting `Terrain` shows a
      disabled Drop; (5) an object with nothing beneath it shows a disabled Drop and `D` does nothing; (6) dropping the
      same object twice leaves it where the first drop put it and the second adds no Undo step; (7) Undo after a drop
      restores the file and the object's place; (8) reopening the scene with an object selected, Drop becomes enabled
      once the loading overlay goes away, without re-selecting

      Manual verification status: sandbox IDE startup succeeded with a fixture copy at
      `/tmp/abyssus-drop-ide-03l622om/Untitled`. macOS denied osascript accessibility access, so the eight hand checks
      above could not be performed and this task remains open. Re-launch the final build with
      `./gradlew runIde -PideProject=/tmp/abyssus-drop-ide-03l622om/Untitled`, perform checks (1)-(8), then check this box.

## 5. Documentation

- [x] 5.1 Update `sceneview/README.md`: a `Drop` row in the pieces table, and a note under "Things that are not obvious"
      recording that the resting height is an area query over oriented boxes and transformed bilinear terrain cells rather than a
      `pickRay` call — `pick` returns one entity at one point, `intersectRayBounds` reports a hit at the ray origin when
      it starts inside a box, and the terrain march is bounded by `camera.far` and assumes uniform scale — and that
      heights within 0.0001 count as equal so a drop is idempotent. Verify: the note is present and
      `scripts/check-docs.sh` passes
- [x] 5.2 Update `docs/ai/architecture.md` ("Clicks, drags and writes") with the drop action alongside the click and
      drag paths, and `docs/ai/testing.md` if the new test cases change what it says is covered headlessly. Verify:
      `scripts/check-docs.sh` passes and no statement in either file contradicts the implemented behavior

## 6. Full check

- [x] 6.1 `./gradlew check` and `scripts/check-docs.sh` both pass. If `./gradlew check` fails for reasons outside this
      change, report the failing tests and their cause rather than fixing them silently
