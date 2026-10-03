# Design

## Context

See proposal.md - Why.

The scene view already answers "what is the first thing along this ray" twice: `ScenePicker.pick` for a click, and
`GizmoDrag` for a handle. A drop looks like the same question with a vertical ray, so the tempting implementation is to
call `pick` with a downward ray. That does not work, for reasons that shape the whole design:

- **`pick` answers the wrong question.** It returns the nearest entity id, not a height, and only one hit. A drop needs
  the highest surface under a whole footprint, not under one point.
- **One point is not enough.** A ray from the object's origin misses a table the object overhangs and sinks it into a
  bump it only partly covers. The unit of work is an area, not a ray.
- **A ray starting inside a box hits at its own origin.** `Intersector.intersectRayBounds` (libGDX 1.13.5,
  `Intersector.intersectRayBounds`) returns `ray.origin` when the box contains it. Its face test is exact otherwise, but for an
  object sunk into a table the "hit" is the object's own position, not the tabletop.
- **The terrain search is bounded by a camera property and assumes uniform scale.** `pick` stops at `camera.far`, and
  `ScenePicker.terrainDistance` normalises a direction in terrain-local space, which its own comment says assumes
  uniform scale.

So the drop gets its own function and does not reuse `pick`.

Two further constraints shape the result. The view draws placements straight from the `ecs` JSON and ignores
`ParentComponent` (`sceneview/README.md`), and the boxes it draws come from the last rendered frame's loaded entities
(`ModelEntity.localBounds` under `instance.transform` for models, a world box for markers, the height field for
terrains).

## Goals / Non-Goals

**Goals:**

- A resting height that is exact for boxes, including rotated ones, and exact to the height grid for a terrain under
  any scale and rotation.
- The whole decision in a plain class with no Swing, GL or platform, so it is unit-testable headlessly — the same reason
  `ScenePicker`, `GizmoDrag` and `SceneMarkers` are separated out (`docs/ai/conventions.md`).
- No new write path: the same `editSceneJson` command, the same fields and the same formatting behaviour as a move drag.

**Non-Goals:**

- Resting an object *flat* on a slope (see Risks). Dropping settles by height, not by a support polygon.
- Mesh-accurate contact. Surfaces are bounding boxes; a table's box top is its tabletop, but a chair's box top is the
  top of its back.
- Any hover or ghost affordance showing where the object would land.
- Dropping a terrain. It is the ground, and its box spans the whole terrain.
- Changing `pick`, how bounds are obtained, or making the view hierarchy-aware.

## Decisions

### 1. An area query, not a ray

The drop asks: over the object's footprint seen from above, what is the highest surface? Each candidate offers one world
Y, and the answer is their maximum. The footprint is a convex polygon in world x/z; nothing is cast.

**Alternative rejected:** call `pick` with a downward ray from the object's origin. Rejected for the reasons in Context.

### 2. Footprints and surfaces are rotated boxes

Every box in the query — the dropped object's and each candidate's — is an **oriented box**: the 8 corners of a local
box under a world matrix. Its outline from above is the convex hull of the 8 corners' (x, z); its bottom and top are the
minimum and maximum Y over the same corners.

```
   local box (8 corners)     world matrix               8 world corners
   +--------------+  position, rotation, scale  +-------------------------------+
   |  o-------o   | --------------------------> |  outline: hull of (x, z)        |
   |  |       |   |                             |  bottom / top: min(y) / max(y)  |
   |  o-------o   |                             +-------------------------------+
   +--------------+
```

All 8 corners are used, not the 4 of the local "bottom" face: under rotation the lowest point is often an edge or a
vertex rather than a corner of whichever face was down locally.

The same shape is used on both sides. A thin bar rotated 45° has a fat square axis-aligned box; using the oriented
outline means the bar neither lands on a table it is not over nor counts as a table under something it is not under.

- **Models:** `ModelEntity.localBounds` under `instance.transform`, the pair `SceneRenderer.pick` already combines.
- **Camera and light markers:** the axis-aligned box the view already draws (`SceneMarkers.boundsOf`), as 8 corners.
- **Terrains** are never a footprint (they cannot be dropped) and are surfaces by decision 4, not by their box.

The overlap test between two convex outlines is a separating-axis test over the edges of both hulls (at most 8 edges
each), a dozen lines of 2D arithmetic.

**Alternative rejected:** sample the columns at the footprint's corners. Simplest, but a wide object over a narrow
model has no corner over it, and a terrain bump between the corners is missed, so the object sinks into it.

### 3. Which surfaces count

Let `low` be the object's lowest point and `eps = 0.0001` world units.

- A **box surface** counts when its outline overlaps the footprint and its `bottom < low + eps`. It offers its `top`.
  A box wholly above the object is ignored; a box the object is sunk into (`bottom < low < top`) counts and offers its
  top, so the object rises onto it rather than falling through it.
- A **terrain** counts wherever the footprint is over its height field. It offers the highest height under the
  footprint (decision 4). Since a height field has no underside, an object below it rises to its surface.

The resting height is the maximum offered value; `null` when nothing is offered. The drop moves the object by
`rest - low`. When `|rest - low| < eps` the object is already resting: no move, no edit, and the button is disabled, as
if there were nothing below. `eps` is what makes the drop idempotent: after a drop, `low` is recomputed through a
rotated matrix and lands within float error of `rest`, on either side. A strict comparison would write a tiny edit on
one side and drop through to the next surface on the other.

The dropped entity and the camera the view looks through are never candidates.

**Alternative rejected:** only surfaces strictly below `low`. It falls through any surface the object is sunk into,
which is the usual starting state of an object placed by eye, and it is not idempotent under float error.

**Alternative rejected:** a fallback ground plane at `y = 0`. The grid is drawn there but is not a pick target and is not
in the scene file, so inventing a surface the file does not describe would make Drop move objects in a way nothing else
in the view would agree with.

### 4. The terrain height under a footprint

The terrain is a bilinear height field under a world matrix that may be rotated and non-uniformly scaled.
Projecting a column onto localY = 0 and then substituting heightAt is incorrect for pitch or roll: the substituted
height changes the world X/Z too. A 45-degree tilt of a constant local height 2 illustrates this: the zero-plane
method returns world Y 1.414 at the wrong column, whereas the actual column intersects at Y 2.828.

Instead, operate on each transformed bilinear grid cell directly. Parameterise its surface by u,v in [0,1]:
S(u,v) = A + B*u + C*v + D*u*v. Each footprint edge becomes a bilinear inequality in u,v. The highest world Y
inside these inequalities is the highest intersection with any vertical column under the footprint, without a
finite sampling grid that can miss peaks. Cull cells whose projected corner bounds do not overlap the footprint.

On each cell, evaluate feasible corners, intersections of constraint boundaries, stationary points of world Y
along each boundary, and interior stationary points. Boundary intersections reduce to a linear equation and a
quadratic; choose all real roots inside the cell and footprint. The maximum is exact for the bilinear height grid,
including maxima on clipped cell edges and tilted terrain. Singular world transforms offer no surface. This also
handles a valid 90-degree rotation; an inverse direction's local Y being zero does not imply a singular transform.

**Alternative rejected:** capped column sampling and zero-plane projection. Sampling can miss a terrain-grid peak
between samples even on an untilted terrain, and zero-plane projection fails under pitch or roll.

### 5. The maths is a pure function; the renderer only supplies the lists

`ScenePicker.restHeight(footprint, boxes, terrains): Float?` sits next to `ScenePicker.pick`, takes plain data — an
`OrientedBox` footprint, oriented box surfaces and `TerrainTarget`s — needs no GL and no platform, and is
unit-testable with hand-built boxes and height fields. `OrientedBox` (8 world corners from a local `BoundingBox` and a
`Matrix4`, with its hull, bottom and top) is a plain class beside it.

The target construction currently inlined in `SceneRenderer.pick` (`models.drawn` and `SceneMarkers.targets` to
`BoxTarget`, `terrains.drawn` to `TerrainTarget`) is extracted so both callers build from the same drawn lists. Picking
keeps its world-axis `BoxTarget`s; the drop needs the oriented boxes, so the extraction yields each model's local box and
matrix and lets each caller derive what it needs. `boundsOf` is not reused for the footprint, because it returns the
world axis-aligned box.

**Alternative rejected:** putting the query on `SceneRenderer` next to `pick`, which reads tidier but drags the whole
GL-populated renderer into every unit test.

### 6. The write is a move, and reuses the move's path

The Y arithmetic uses Double before converting back to Float to avoid cancellation when dropping from a high origin.
The drop produces a `TransformEdit(position = ...)` whose position is the entity's current position with only `y`
changed, and hands it to the existing `SceneView.onTransform`, which reaches `SceneFileEditor.applyTransform` and
`editSceneJson` unchanged. `SceneTransformWriter` writes only the fields that differ, so `x` and `z` keep their text.
`applyTransform` names the undo command from `edit.rotation != null`, so a drop's undo reads as "Move Entity" beside a
gizmo move's — correct, since it is one. No new command string, no new writer, and the preview the view already applies
for drags is reused so the object does not jump back before the document listener re-reads the file.

### 7. The enabled state, and when it is re-checked

`SceneInteraction` exposes `canDrop`: the selection is droppable (decision 3's exclusions, no drag in progress) and the
query yields a height that differs from `low` by at least `eps`. The Drop button binds that one property in
`syncControls`; the panel holds no decision of its own. `drop()` re-runs the same query rather than trusting a cached
answer.

`syncControls` runs on `onStateChanged`, which today fires only for a selection, mode, view-camera or params change. The
query's inputs are also frame state: when a model finishes loading, the drawn lists change with no event, and the button
would stay disabled until the user re-selected. So the renderer counts changes to its drawn lists (`drawnVersion`,
bumped when `models.drawn` or `terrains.drawn` gains or loses an entity), and `SceneInteraction` re-evaluates `canDrop`
after a frame whose `drawnVersion` differs from the last one it saw, firing `onStateChanged` only when the answer flips.
The cost is one query per loading event, not per frame. A scene params change also invalidates the next frame's
query: its immediate query still sees the previous drawn model/terrain transforms, while the next frame updates the
same entity ids without changing drawnVersion. This ensures text edits and Undo refresh Drop after geometry updates.

The ground lookup reaches `SceneInteraction` as an injectable function defaulting to the renderer's, the way `onPick`
and `onTransform` already do. Everything above it — `canDrop`, the exclusions, the no-op rule and the edit the drop
produces — is then testable with no canvas and no assets.

### Threads

| Piece | Thread | GL / context |
|---|---|---|
| `ScenePicker.restHeight`, `OrientedBox` | any, incl. AWT | none |
| target lists, `renderer.groundBelow`, `drawnVersion` | AWT | none; reads last frame's data, as `pick` already does |
| `SceneInteraction.drop`, `canDrop` re-check | AWT | none |
| `SceneFileEditor.applyTransform` | caller (AWT) | none; document write |

Nothing new runs inside `GdxRuntime.withContext`, and no new GL is touched. The query is called from a key press, a
button, `syncControls` and the post-frame re-check, all on the AWT thread that also renders, so the drawn lists cannot
change under it.

## Risks / Trade-offs

- **The fixture terrain is flat.** `Untitled`'s terrain is height 0 everywhere under the named entities, so the fixture
  proves only simple drops. Rotated, bumpy, stretched and stacked cases are proved by hand-built boxes and height
  fields in unit tests, and the spec's scenarios for them name no fixture entity. → Accepted.
- **0.0001 is near float resolution far from the origin.** A 32-bit float has a spacing of about 0.0001 near 1000, so
  idempotence is only guaranteed for scenes within a few hundred units of the origin. → Accepted: a drop that lands
  one ulp off writes at most one more tiny edit, then settles.
- **A tilted object may rest on one corner.** Settling by height rests the object's lowest corner on the highest
  surface under the footprint, so on a slope the other side floats. It does not sink into the slope, to the precision of
  the terrain grid. → Accepted. A support-polygon fit is a different algorithm and a separate change; the seam is the
  same pure function.
- **Boxes are coarser than meshes.** An object dropped over a chair rests on the top of the chair's box. → Accepted and
  stated in the spec; mesh-accurate contact would need triangle data on the CPU, which the view does not keep.
- **Sunk objects rise.** An object whose lowest point is inside a surface's box or under the terrain moves *up*. That
  is usually what the user wants after placing by eye, but it means "Drop" can raise. → Accepted and specified.
- **Markers are surfaces.** A light can be landed on. → Kept for uniformity; reversible by filtering marker targets out
  of the list, which touches one function.
- **A drop can traverse the whole scene.** It is not limited by `camera.far`. → Accepted: it is still one undoable edit,
  and the target is a real surface.
- **Camera position is written whole.** `SceneTransformWriter` sets `CameraComponent.camera.position` to the full
  `localPosition` vector, as a move does. Mundus keeps the two equal, so only `y` changes; a hand-edited file where
  they differ would have `camera.position` brought in line. → Accepted: it is the move's existing behaviour, and the
  spec scenario states the precondition.
- **The enabled state is frame-dependent** (decision 7). → Mitigated by the `drawnVersion` re-check.

## Migration Plan

None. No file format change, no stored state, no new dependency. The undo command name is reused, so undo history from
an earlier session is unaffected.
