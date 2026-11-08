# Design

## Context

See proposal.md for the motivation. The current state that shapes the approach:

- **The editor read model treats `local*` as world.** `SceneContent` (lib-core-editor, `scene/SceneContent.kt`) decodes
  each entity on its own (`decode(id, entity)`), and everything downstream uses those values directly: picking, gizmos,
  `SceneModels` / `ModelEntity`, the physics overlay (`plugin/physics/PhysicsOverlayGeometry.kt`), shadows and
  `RaySceneSnapshot`. Look-at targets are read from a map of every entity's `localPosition`.
- **The runtime is the same.** `RuntimeSceneLoader` / `EcsLoader` bind `ParentComponent` and only check that the parent
  id exists (`resolveReferences`). Renderables, `LookAtSystem`, the camera sync, Control Line's `FieldRenderer` and
  `PhysicsWorld` all read `PositionComponent` as world.
- **Existing `ParentComponent` users:** 81 linked arrangement copies in `Field.scene` (and 80 in
  `toolsTest/fixtures/Field.original.scene`) under `GROUP` arrangements that have no `PositionComponent`, and six
  `HANDLE`s in plugin test fixtures under cameras and lights.
- **Edits:** `SceneTransformWriter` / `PositionCodec` write gizmo results. `EntityRemoval` refuses to remove an entity
  that anything refers to, and today that includes a child's `parentEntityId`. `EntityClipboard` drops `ParentComponent`.
- **Model nodes are readable without GL.** `ModelMeshLister` reads `ModelData` through `ModelLoader.prepare(...)`;
  `model_trainer` has named nodes, `model_racer` has one.
- **Precedent for one-off scene migrations:** Control Line's `tools/` source set (`FieldFoliage.kt`, the
  `generateFieldFoliage` JavaExec, tests in `toolsTest/`) rewrites `Field.scene` through the editing libraries, kept off
  the game's shipped classpath.

## Goals / Non-Goals

**Goals:**
- One pure world-pose resolver shared by the editor and the runtime, so both place every entity identically.
- No cost for scenes without children beyond one pass over the entities.
- A one-off rewrite of committed data that goes through DTOs, the printer and the editing API, never hand-built JSON.

**Non-Goals:**
- A scene graph object model, dirty flags or incremental updates. Scenes are small enough to resolve in full.
- Animated nodes. A `node` binding uses the node's rest transform.
- Copy/paste of subtrees. The clipboard keeps dropping `ParentComponent`, as it does today.

## Decisions

### 1. `WorldPoses`: one pure resolver in lib-core

`net.nevinsky.abyssus.lib.core.hierarchy.WorldPoses` is a plain class wired by constructor (no `object`), with no GL and
no Ashley dependency in its core:

```
resolve(
  locals:   Map<Long, LocalPose>,        // position, rotation, scale from PositionComponent
  parents:  Map<Long, ParentLink>,       // parentEntityId, node?
  nodeRest: (parentId, node) -> Matrix4?, // rest transform of a node in the parent's model, or null
): Resolved(world: Map<Long, Matrix4>, roots: Set<Long>, warnings: List<HierarchyWarning>)
```

- It walks each entity's parent chain, memoising results. A missing parent, a self-link or a cycle (found with an
  on-stack set) makes the entity a root and adds one warning naming it.
- World = parentWorld x nodeRest x TRS(local). Only the parent's **stored** pose is used: look-at orientation is applied
  later, by the consumer, to that entity's own drawing only. This is what breaks the light/handle cycle.
- An unknown node gives a warning and the identity transform.
- Warnings are deduplicated per (entity, reason) by the consumer that owns the scene load (the editor's content builder,
  the runtime's `HierarchySystem`), so repeated resolution does not repeat them. The resolver itself stays pure.
- A disabled entity (own flag or any ancestor's) is disabled for drawing, systems and physics. This is computed from the
  same parent links; the children's own flags are never written.
- Warnings are values. The editor shows them, as other load warnings are shown, and the runtime logs them.
- `LocalPose.of(PositionComponent)` and the inverse, `localFor(parentWorld, newWorld)`, live beside it. The inverse
  decomposes into TRS for gizmo write-back and detach.

Alternative considered: compose inside each consumer (SceneContent, systems, physics). Rejected because the editor and
the game would drift apart, and that is exactly the bug class this change exists to remove.

### 2. Editor: `SceneContent` carries world poses

`sceneContentOf` resolves `WorldPoses` once per scene snapshot. Placements (`AssetPlacement`, `LightPlacement`,
`CameraPlacement`, `PlacementTransform`) carry **world** transforms plus the entity's local pose and its parent's world
matrix, which the gizmo needs. The look-at map becomes world positions. Every existing consumer (picking, `SceneModels`,
the overlay, shadows, `RaySceneSnapshot`) already reads placements, so it gets world values without its own change.

**Node rest transforms in the editor:** `SceneContent` is built from the DTO without assets, so it takes a
`NodeTransforms` provider. Backed by `ModelMeshLister`'s cached `ModelData` (read off the EDT, no GL), it answers `null`
until the model has been read. In that case the child sits on the parent's frame and the content is rebuilt when the
read lands, as model loads already refresh the view. Tests pass a map.

### 3. Gizmo write-back and detach in local space

`SceneTransformWriter` receives the world result of a drag as today. For a child it writes
`WorldPoses.localFor(parentWorld, newWorld)` into the child's `PositionComponent`. For a root, the parent's world matrix
is identity, so the existing code path and written numbers are unchanged. The handle-aimed light rule still writes the
handle's position, now converted to local under the light. Arrangement detach writes the copy's world pose and removes
`ParentComponent` in the same `SceneEdit`. Properties shows the stored local values and a "local to <parent>" note.

### 4. Runtime: `WorldTransformComponent` and a hierarchy system first

lib-core adds a runtime-only `WorldTransformComponent` (a `Matrix4` plus a resolved flag). It is not registered in
`ComponentRegistry`, so it is never read from or written to a file. A `HierarchySystem` runs first in `SceneEngine`'s
fixed order. It runs `WorldPoses` over the engine's entities, with node rest transforms read from each parent's
`ModelInstance` nodes once the model is built (identity until then), and fills the component. `LookAtSystem`, the
renderable placement, the camera sync, Control Line's `FieldRenderer` and the play host read the world transform. Game
logic keeps writing `PositionComponent`, as it does today.

`Dummies` (lib-core) is a small constructor-wired helper: `find(engine, parentId, name)` returns the lowest-id child
whose header type is `DUMMY` and whose name matches, plus its local and world pose. It warns once per (parent, name)
when the name is shared.

### 5. Physics: roots own bodies; children are refused or static

`PhysicsWorld` setup reads world poses from `WorldTransformComponent`. Classification happens before native allocation:

- a child with a `RigidBodyComponent` is refused, with one warning;
- a child collider with no dynamic or kinematic ancestor becomes a static body at its world pose;
- a child collider under a dynamic or kinematic ancestor is skipped, with one warning. The compound-collider change
  will turn this case into sub-shapes.

Write-back goes only to roots, because only roots own moving bodies, so the world pose is the local pose. Dummies are
ignored.

### 6. The `DUMMY` type and Add Dummy

`DUMMY` is added to the header type enum in lib-core. `DummyEntities` is a `SceneEdit` in lib-core-editor. It builds the
entity from DTOs: `NameComponent`, an empty `PositionComponent` and `ParentComponent`. It picks a unique sibling name
(`Dummy`, `Dummy 2`...) from `EditorMessages` and is exposed through `HeadlessEditing.addDummy`. The plugin adds
`Abyssus.AddDummy` to `Abyssus.Menu.Add`, enabled on entity rows that are not arrangements or linked copies. It runs
through `editSceneDocument` and selects the new row.

The marker is drawn by a new `DummyOverlay` in `sceneview`, beside `PhysicsOverlay` and following its pattern: three
lines sized in screen pixels, drawn on top when selected. Its geometry (axis endpoints for a world matrix and a
pixel scale) is in a pure `DummyMarkerGeometry` in lib-core-editor, tested headlessly. Picking adds a screen-distance
hit test against the marker centre for visible dummies (CPU, no GL), ranked with the existing pick results. The
"Show Dummies" toggle joins the toolbar's display toggles and is off on open.

### 7. Tree nesting

`SceneTreeNodes` builds a parent-to-children index from the same `WorldPoses` link rules: a valid parent, and not a
squashed arrangement copy. It lists root entities under `ecs` and child rows first under each entity row. Broken links
fall back to the root level. Selecting from the scene view expands the ancestors through the existing tree selection
path.

### 8. Removal takes the subtree

`EntityRemoval` collects the target's descendants and removes them all in one `SceneEdit`. `EntityReferences` ignores
references that come from inside the removed set, so `parentEntityId` links and a light's `lookAtId` to its own child
handle no longer block the removal. References from outside the set still refuse it, as before.

### 9. One-off data rewrite

- **Handle fixtures (5 plugin scenes):** `HierarchyRebase`, a `SceneEdit` in lib-core-editor, rewrites each child's
  `PositionComponent` as `localFor(parentStoredWorld, oldWorld)`, where oldWorld is its current value read as world. A
  test-source runner in lib-core-editor (`RebaseFixtures`, run once by hand, then deleted in the same change) applies it
  to the listed files through `JsonProcessor`. `HierarchyRebaseTest` pins the math on the `Abyssus Lights` values
  ((0, -10, 0) and (0, -5, 0)) and on `Camera 4`.
- **Field scenes:** `tools/FieldLeadouts.kt` in Control Line's tools source set, with a `migrateFieldLeadouts`
  JavaExec, follows `FieldFoliage.kt`. For each `PlaneComponent` entity it adds two `DUMMY` children named
  `leadoutUp` / `leadoutDown` at the old values through `HeadlessEditing`, then removes those two fields from the
  plane's game-component payload through lib-core-editor's `DocumentEdit` API (the game component is held as raw JSON
  by the editor). It runs on `Field.scene` and `toolsTest/fixtures/Field.original.scene`. Arrangement copies need no
  rewrite, because their parents have identity poses. `FieldLeadoutsMigrationTest` (toolsTest) checks the result and
  that a rerun changes nothing.

The game component is held as raw JSON by the editor, so removing the two fields goes through lib-core-editor's
`DocumentEdit` API. This adds nothing to `JsonProcessor` and needs no field-by-field reading.

A dummy ignores render, collider and rigid-body components put on it. The clipboard keeps dropping `ParentComponent`,
so a pasted dummy is a root with the same local values; this is documented, not converted.

Alternative considered: a load-time migration. Rejected, because the format rules forbid implicit migration, and the
plugin has no production files to protect.

## Risks / Trade-offs

- [Non-uniform parent scale with rotation gives shear, which TRS cannot represent] -> `localFor` decomposes the best
  TRS, and the gizmo of such a child writes that approximation. Documented, and pinned by a test that asserts the
  position is exact.
- [A node binding is resolved asynchronously in the editor, so a child can sit briefly on the parent's frame] -> The
  content is rebuilt when the model read lands. The game resolves from the built model before its first frame.
- [Other open changes assume world-space entities (`add-scene-model-mesh-selection`, `add-lights-from-model-meshes`:
  baked lights read the model entity's pose)] -> A task re-reads their delta specs. Baked lights are created as roots,
  so they use the model's world pose, which `SceneContent` now supplies.
- [Tests that pin handle coordinates, or the top-level `ecs` row list, change] -> They are updated together with the
  fixture rewrite in one task, so the diff shows each expectation moving with its data.
- [Per-frame cost of resolving everything in the game] -> A few hundred entities with short chains. A baseline is
  recorded in the Control Line performance test before task 2.1 and compared after; if it regresses, only entities with
  a parent need re-resolving. `improve-control-line-performance-stability` is open and touches the same path, so
  its changes are read before 2.1 to avoid a clash.
- [Open changes may assume `localPosition` is world] -> Task 6.1 checked them. `add-scene-model-mesh-selection`
  ("Placement is honoured") and `add-lights-from-model-meshes` ("One point light per lamp": the entity's pose) were
  amended to say a child's pose is local and the world pose is used, and `ModelLights` now places its root lights from
  the model's world pose. `add-scene-raytracing` and `split-scene-ecs-file` make no such assumption.

## Migration Plan

1. Land the resolver, the editor and runtime consumers, and the rebase tool.
2. Run `RebaseFixtures` once on the five plugin fixtures and `migrateFieldLeadouts` once on the two Field scenes. Commit
   the rewritten files together with their test expectations, then delete the throwaway runner.
3. Rollback is a git revert. No user files exist outside the repo.
