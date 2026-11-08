# Tasks

## 0. Housekeeping first

- [x] 0.1 Fix the stray `## ADDED Requirements` headers in the main specs `abyssus-project-view`, `scene-ecs-systems`,
  `physics-components`, `object-properties-panel` and `scene-picking`, by folding those requirements into
  `## Requirements`. Also remove the stray THEN line "its header holds buttons named Collapse All…" from the
  `scene-path-arrangements` main spec, and move "Rotation applied before drawing" out from under the eye requirement in
  `scene-ecs-systems`. Verify that `openspec validate add-entity-hierarchy-and-dummies --strict` reports no "Archive
  would refuse" info. Failures here are in scope for this change.
- [x] 0.2 Record the Control Line performance baseline (read `improve-control-line-performance-stability` first). Keep
  the numbers in the commit message of 2.1.

## 1. World-pose resolver (lib-core)

- [x] 1.1 Add `ParentComponent.node` (optional string, omitted when null) and the `DUMMY` header type. Verify that
  `./gradlew :lib-core:test --tests '*SceneRoundTrip*'` and a new `ParentComponentNodeTest` both pass: a scene with and
  without `node` reads back and prints unchanged.
- [x] 1.2 Add `hierarchy.WorldPoses`, with `LocalPose` and `localFor`, as in design decision 1. Verify
  `./gradlew :lib-core:test --tests 'net.nevinsky.abyssus.lib.core.hierarchy.WorldPosesTest'`. It covers a root, two
  levels, a rotated parent, a scaled parent, a stored pose (not look-at) being used, a node rest transform, an unknown
  node, a missing parent, a self-link and a cycle, a disabled ancestor, once-only warnings on repeated resolves, plus a `localFor` round trip, including the non-uniform-scale case
  where the position is exact.
- [x] 1.3 Document the new `ParentComponent` meaning, `node` and `DUMMY` in `docs/ai/file-formats.md` (the component
  table row and the header `type` list), and the terms in `docs/ai/glossary.md`. Verify with `scripts/check-docs.sh`.

## 2. Runtime hierarchy (lib-core, physics, play host)

- [x] 2.1 Add the runtime-only `WorldTransformComponent` and `HierarchySystem`, first in `SceneEngine`'s order, with
  node rest transforms taken from the parent's built model. Switch `LookAtSystem`, renderable placement and the camera
  sync to world transforms. Verify `./gradlew :lib-core:test --tests '*HierarchySystemTest'`: a child's renderable is
  placed at (1, 5, 0) in the same update its parent moves, and look-at aims at a child's world position. Also run
  `./gradlew :lib-core:checkNoSingletons`.
- [x] 2.2 Add the `Dummies` lookup (lowest id wins, plus one warning on a shared name). Verify
  `./gradlew :lib-core:test --tests '*DummiesTest'`.
- [x] 2.3 In `PhysicsWorld` setup, read world poses, refuse a child rigid body, make a child collider with no moving
  ancestor static, skip one under a moving ancestor (with a warning), write back to roots only, and ignore dummies.
  Verify `./gradlew :lib-physics:test --tests '*ChildEntitiesTest'`, which covers the four spec scenarios on a copy of
  the `Physics` fixture. Also run `./gradlew :lib-physics:checkNoSingletons`.
- [x] 2.4 Make the play host and `lib-game-link` read world transforms where they read poses. Verify with
  `./gradlew :lib-game-link:test`.
- [x] 2.5 Update `docs/ai/architecture.md` (system order, world transforms) and `projects/lib-core/README.md` /
  `projects/lib-physics/README.md`. Verify with `scripts/check-docs.sh`.

## 3. Editor read model and edits (lib-core-editor)

- [x] 3.1 Resolve `WorldPoses` in `sceneContentOf`, with a `NodeTransforms` provider backed by `ModelMeshLister`'s
  cached `ModelData`. Placements carry world transforms, the local pose and the parent's world matrix, and the look-at
  map holds world positions. Verify `./gradlew :lib-core-editor:test --tests '*SceneContentHierarchyTest'`: a dummy
  under `Model 9` is at (0, 22.632797, 0), and the handle-aimed lights of `Abyssus Lights` (after 5.1) shine down.
- [x] 3.2 Warn once when a child under a rotated, non-uniformly scaled parent is written (position exact, TRS best
  fit). Make `SceneTransformWriter` write `localFor(parentWorld, newWorld)` for a child and leave roots unchanged.
  The handle-aimed light rotation writes the handle's local position. Verify
  `./gradlew :lib-core-editor:test --tests '*SceneTransformWriterTest'`, extended with a child move to (2, 1, 0), a
  world-Y move under a parent turned about X, and the light turn giving handle `0` a local position of (0, 0, -10).
- [x] 3.3 Make arrangement detach write the world pose before removing `ParentComponent`. Read and write points and
  copies in the arrangement's frame. Verify the existing arrangement tests in lib-core-editor (`*Arrangement*`) still
  pass, plus a new case with an arrangement that has a `PositionComponent`.
- [x] 3.4 Make `EntityRemoval` remove the subtree, ignoring references from inside it. Verify
  `./gradlew :lib-core-editor:test --tests '*EntityRemovalTest'`: a model with a dummy goes in one edit, a light with
  its handle goes, and an outside reference still refuses.
- [x] 3.5 Add the `DummyEntities` `SceneEdit` (unique sibling names, strings in `AbyssusEditorBundle.properties`),
  `HeadlessEditing.addDummy`, and `DummyMarkerGeometry`. Verify
  `./gradlew :lib-core-editor:test --tests '*DummyEntitiesTest' --tests '*DummyMarkerGeometryTest' --tests '*HeadlessEditingTest'`.
  The scenario "What lands in the file" is asserted on a copy of `Untitled`.
- [x] 3.6 Add the `HierarchyRebase` `SceneEdit`. Verify `./gradlew :lib-core-editor:test --tests '*HierarchyRebaseTest'`:
  `Abyssus Lights` handles become (0, -10, 0) and (0, -5, 0), `Camera 4`'s handle maps back to the origin, and roots
  and identity-parent copies are unchanged.
- [x] 3.7 Update `projects/lib-core-editor/README.md`. Verify with `scripts/check-docs.sh`.

## 4. Plugin (tree, Scene view, actions)

- [x] 4.1 Nest child rows under their parent in `SceneTreeNodes`, before the component rows. The value reads
  `N components, M children`, broken links stay at the root, arrangement copies stay squashed, and dummies get an
  icon. Verify `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.ChildEntityRowsTest'`
  (the `Untitled` handle under `Camera 4`, `ecs` still `11 entities`, the broken link). Update `RowTextTest` and
  `NodeIconsTest` expectations as needed.
- [x] 4.2 Add the `Abyssus.AddDummy` action to `Abyssus.Menu.Add` in `plugin.xml`, with its string in
  `AbyssusBundle.properties`. It is disabled on arrangements and copies, and selects the new row. Verify
  `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.AddDummyActionTest'` (add, undo,
  `Dummy 2`).
- [x] 4.3 Make Properties show a child's `PositionComponent` with a "local to <parent>" note (a root has none), as the
  `object-properties-panel` delta describes. Verify with `*PropertiesPanel*` test cases for a dummy and a root.
- [x] 4.4 Add `DummyOverlay`, the "Show Dummies" toolbar toggle (off on open; the selected dummy is always drawn; see the
  `scene-view-toolbar` delta) and dummy marker picking (see the `scene-picking` delta). Verify `./gradlew :plugin-abyssus:test --tests '*SceneViewToolbarTest' --tests '*DummyPickTest'`
  for the headless parts.
- [x] 4.5 Make selecting a child in the Scene view expand its ancestors in the tree. Verify with a `*TreeSelection*`
  test case.
- [x] 4.6 Update the `sceneview` and `projectView` READMEs. Verify with `scripts/check-docs.sh`.
- [ ] 4.7 runIde check (manual, on a copy of `Untitled`): (1) Add Dummy on `Model 9`; it appears under it, selected,
  with a marker. (2) Drag `Model 9`; the marker follows and only `Model 9` changes in the file. (3) Drag the dummy;
  only the dummy changes, and Undo reverts it. (4) Turn the toggle off; the marker hides unless the dummy is selected.
  (5) Move `Camera 4`; its handle row stays under it and the camera keeps its aim. (6) Remove `Model 9`; both go, and
  Undo restores both.

## 5. Data rewrite and Control Line

- [x] 5.1 (One commit with the rewritten files and the test expectations.) Run a throwaway `RebaseFixtures` test-source runner with `HierarchyRebase` once over `Untitled`, `Physics`,
  `Tree`, `Lights/Abyssus Lights` and `Lights/Creation Baseline`, then delete the runner. Update the tests that pin
  handle coordinates in the same commit. Verify with `./gradlew :plugin-abyssus:test` and `./gradlew :lib-core-editor:test`.
- [ ] 5.2 (One commit with the rewritten scenes and `FieldLeadoutsMigrationTest`. Old leadout values are read from the
  file, not from `PlaneComponent`, so this runs before 5.3.) Add `tools/FieldLeadouts.kt` and the `migrateFieldLeadouts` JavaExec in the tools source set, following
  `FieldFoliage.kt`, plus `FieldLeadoutsMigrationTest` in `toolsTest` (dummies at the old values, fields removed,
  rerun unchanged). Run it on `Field.scene` and `toolsTest/fixtures/Field.original.scene`. Verify the toolsTest task
  for the migration and `./gradlew :app-game-control-line:checkShippedClasspath`.
- [x] 5.3 Remove `leadoutUp` / `leadoutDown` from `PlaneComponent`. Make `LineRig` read the dummies through `Dummies`,
  and make `GameFlow` leave out (with a warning) a plane missing either dummy. `FieldRenderer` draws from world
  transforms. Verify `./gradlew :app-game-control-line:test`, including `LineRigTest` (`Trainer` lines at
  (0.48, 0, 0.02) / (0.48, 0, -0.04)) and a new `GameFlow` case for a plane without `leadoutDown`.
- [x] 5.4 Update the Control Line README (leadouts are dummies; the migration task). Verify with
  `scripts/check-docs.sh`.
- [ ] 5.5 runIde and game check (manual, on a copy of the Control Line project): (1) Each plane shows `leadoutUp` /
  `leadoutDown` under it, with markers at the leadouts. (2) Move the `Trainer`'s `leadoutUp` to (0.5, 0, 0.02), run
  `./gradlew :app-game-control-line:run` against the copy, and the up line runs to the new point. (3) The arrangements
  in `Field.scene` look exactly as before.

## 6. Other open changes and integration

- [x] 6.1 Re-read the delta specs of the open changes `add-scene-model-mesh-selection`, `add-lights-from-model-meshes`,
  `add-scene-raytracing` and `split-scene-ecs-file` for assumptions that `PositionComponent` / `localPosition` is a
  world position or placement, and amend any that contradict this change. If none is found, say so and drop the
  matching risk in design.md. Verify with `openspec validate --all`.
- [x] 6.2 Confirm the housekeeping of 0.1 still holds after 6.1 (`openspec validate add-entity-hierarchy-and-dummies --strict`
  reports no "Archive would refuse" info).
- [ ] 6.3 Run `./gradlew check` and `scripts/check-docs.sh`. Both pass; report any failure outside this change instead
  of fixing it silently. Failures from the 0.1 cleanup are in scope.
