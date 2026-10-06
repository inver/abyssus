# Design

## Context

See proposal.md for the motivation. Things that already exist and constrain the approach:

- `AssetEntities` (`lib-core-editor` `components`) adds one model or terrain entity through `SceneEntityTree.insert`,
  which gives the next free id. Callers write the result through `editSceneJson` as one command.
- `ScenePicker.terrainDistance` and `TerrainTarget(entityId, TerrainData, world)` hit-test terrains on the CPU. That
  covers transformed terrains. `SceneQueries.rayAt` turns a screen point into a ray.
- `TerrainLoader.prepare` (`lib-core`) reads `terrain.data` into `TerrainData` without GL, so it may run off the AWT
  thread.
- `SceneInteraction` owns the view's gestures (`Gesture.Idle`, `Dragging`, `Cancelled`) and writes through an
  `onTransform` callback. `LineSink` draws editor lines and `SceneMarkers` draws markers.
- `TypeComponent.Type` already has `GROUP`. `ParentComponent(parentEntityId)` is loaded, written and editable, but
  nothing composes transforms through it.
- The runtime's `EcsLoader` binds only built-in and registered component classes. Anything else is carried raw with a
  warning. The built-in names are listed in `BUILT_IN_COMPONENTS` and `EcsJson`.
- `HeadlessEditing` must write byte-identical text to the plugin for the same edit
  (`headless-scene-editing`).

## Goals / Non-Goals

**Goals:**
- All arrangement logic (sampling, slot layout, heights, the JSON diff, detaching) lives in `lib-core-editor` and is
  tested without GL, Swing or the platform.
- One `editSceneJson` command per user action, including the regeneration it causes.
- The runtime and games see arrangements as plain data and the copies as ordinary entities.

**Non-Goals:**
- Live re-placement on terrain changes, transform hierarchy, curves (see proposal "Out of scope").
- Picking or hand-editing an arrangement through `HeadlessEditing`. Only the detach that a transform or component edit
  causes must match there, because those edits already exist headlessly.

## Decisions

### 1. Copies are baked into the scene file, not expanded at load

The arrangement stores its path and settings, and the editor writes real copy entities next to it.
- Alternative: store only the path and expand it in the runtime and the editor at load time. Rejected: renderer,
  picking, ray tracing, physics and every game would each need to learn arrangements, and the runtime would need
  terrain heights at load. Baking keeps all of them unchanged.

### 2. `PathArrangementComponent` is a built-in runtime component holding data only

`projects/lib-runtime` gets a data class bound by Jackson like the other built-ins, with no system:

```json
"PathArrangementComponent": {
  "points": [{"x": 0, "y": 1.2, "z": 0}, {"x": 40, "y": 0.8, "z": 0}],
  "closed": false,
  "asset": {"type": "MODEL", "assetName": "tree"},
  "spacing": 10, "offset": 0, "sides": "CENTER", "alignToPath": true, "yawOffset": 0,
  "slots": [12, 13, 14, 15, 16]
}
```

Default omission follows the existing writer rules: a default `spacing` and the other defaults may be omitted.
- Making it built-in, rather than a game component, keeps every runtime loading it without a warning and lets games
  read the line (for example, for traffic or AI paths).
- Alternative: an editor-only extension block that the runtime carries raw. Rejected: it logs a warning on every load,
  which the `scene-ecs-components` "Written file loads" scenario forbids.
- `lib-core-editor` adds the component's kind to `BuiltInComponentKinds` for the Properties panel. `points` and `slots`
  are shown read-only there; they change only through the view and regeneration.

### 3. The arrangement's `slots` list is the source of truth; `ParentComponent` on a copy confirms the link

A slot is filled only when `slots[k]` names an entity that exists **and** whose `ParentComponent.parentEntityId` is
the arrangement. Anything else counts as empty and is normalised to `-1` on the next write.
- With both checks, deleting a copy in the text editor, removing its `ParentComponent` in the tree, or detaching it
  all end up with the same result, and no separate bookkeeping is needed.
- `ParentComponent` also gives the tree and games the group link for free. Copies are stored in world coordinates, so
  a later transform hierarchy would have to treat arrangement children specially (see Risks).
- Alternative: a separate skip list plus a slot marker on each copy. Rejected: two lists that can disagree.

### 4. Layout is a pure function of path and settings

`PathLayout` (new, `lib-core-editor`, package `arrange`) maps `(points, closed, spacing, offset, sides, alignToPath,
yawOffset)` to an ordered list of `Slot(x, z, fallbackY, yawDegrees)`:
- Arc length is measured on X/Z. Station `i` is at `i * spacing`; an open line includes the end
  (`<= length + 1e-4`), a closed line stops before the full loop.
- The direction at a station is the direction of the segment the station falls in; a station exactly on an inner
  point uses the outgoing segment, and the end of an open line uses the last segment.
- Left is `up x direction`. With `BOTH`, slot `2i` is left and `2i+1` right. Right-side copies add 180 degrees when
  `alignToPath` is true.
- Yaw: the heading that turns local +X onto the direction, `atan2(-dz, dx)`, plus `yawOffset`. With `alignToPath`
  false, only `yawOffset` is used. Rotation is written as a quaternion about Y.
- Over 1000 slots is refused before anything else is computed. `spacing <= 0` and `offset < 0` are refused by the
  component field checks.

### 5. Heights come from a `GroundHeights` interface, filled from `TerrainTarget`s

`GroundHeights(terrains: List<TerrainTarget>)` casts a downward ray from above each slot at X/Z through
`ScenePicker.terrainDistance` and takes the highest hit, so rotated or scaled terrains work like picking does. When
nothing is hit, it uses the slot's `fallbackY`, interpolated from the points' Y.
- The plugin builds the targets from the Scene view's loaded terrains when a view is open. Otherwise, for example when
  editing from the Properties panel with no view open, it uses a project-level cache that reads terrains with
  `TerrainLoader.prepare` off the AWT thread, keyed by asset name and `AssetRevisions`. The edit waits for the
  heights in a modal progress, then writes on the AWT thread.
- Copies use their origin, not their lowest point (unlike Drop). That keeps the result independent of whether model
  bounds are loaded, so headless and plugin writes are identical. It assumes models have their origin at the base,
  which streetlights, poles and trees normally do.

### 6. Regeneration is one JSON transform: `ArrangementEdits`

`ArrangementEdits` (new, `lib-core-editor` `arrange`) works on the scene `JsonNode` the way `AssetEntities` does:
- `create(root, asset, points, closed, heights)` inserts the arrangement (`Path <id>`, `GROUP`, the component), then one
  copy per slot (`Path <id> #<slot>`, `OBJECT`, `PositionComponent`, `RenderComponent` with `shaderKey`
  `defaultShader`, `ParentComponent`), and writes `slots`.
- `regenerate(root, arrangementId, change, heights)` computes the new layout. For each slot `k` below the old size, a
  filled slot gets its `localPosition` and `localRotation` rewritten (and its `RenderComponent` asset when `asset`
  changed), and an empty slot stays `-1`. Slots past the old size get new copies. Filled old slots past the new size
  have their entity removed. Other components and the name of a kept copy are left untouched.
- Refill rule: if `spacing`, `sides` or `closed` changed, or `points[0]` changed or was removed, empty slots are filled
  too. `change` carries the old component so this is a comparison, not a flag.
- `detach(root, copyId)` removes the copy's `ParentComponent` and writes `-1` into the owning arrangement's slot. The
  existing transform write (`SceneTransformWriter`) and component field edit (`ComponentEditor`) call it when the
  target is a linked copy and the edit is a position or rotation change. Because those classes are shared with
  `HeadlessEditing`, headless transform and field edits detach the same way.
- Positions are written with the existing `PositionComponent` writer so number formatting and default omission match
  the other edits. Unchanged values are not rewritten, so Regenerate with no change writes nothing.

### 7. Scene view interaction: two new gestures in `SceneInteraction`

- `Gesture.DrawingPath(asset, points)`: a left click without dragging casts `rayAt` and adds the nearest terrain hit;
  Backspace removes the last point; Enter or a double click finishes with at least 2 points; a click within a few
  pixels of the first point finishes closed when there are at least 3 points; Esc cancels. Orbit and pan keep their
  existing mouse bindings.
- `Gesture.DraggingPathPoint(arrangementId, index, inserted)`: started by pressing on a point handle, or on a middle
  handle (which inserts a point). It moves the point to the terrain hit under the cursor, previews the slot layout,
  and on release calls a new `onArrangementEdit` callback with the new points. Esc restores the old ones.
- Handle hit tests (`PathHandles`, `lib-core-editor` `pick`) project points to the screen and use a pixel radius, like
  `GizmoHit`. Clicking the line selects the arrangement through the same screen-distance test against its segments.
  Copies win over the line, because box picking runs first.
- Preview: the path is drawn through `LineSink`, and each slot gets a small cross marker at its ground height, using
  heights from the view's own terrain targets. No model instances are drawn in the preview, which keeps it cheap and
  free of GL asset loading.
- Remove Point is offered on the point handle's right-click menu, built by the plugin from a `PathHandles` hit.

### 8. Threads

- Layout, heights from already loaded `TerrainData`, hit tests and JSON edits run on the AWT thread. They are CPU-only
  and touch no GL.
- Reading `terrain.data` for the no-view case runs on a pooled thread (it is the same kind of work as
  `AssetStorage.prepare`). The result is handed back to the AWT thread before `editSceneJson`.
- Line and marker drawing happen in the existing editor-overlay pass, inside `GdxRuntime.withContext` on the canvas
  thread, through `LineSink`.

### 9. UI text and actions

- `AddPathArrangementAction` mirrors `AddAssetAction` (toolbar and tree, models only) and opens or focuses the scene's
  Scene view in drawing mode.
- `RegenerateArrangementAction` sits on the arrangement's tree row and in the toolbar while an arrangement is selected.
- Names (`Path <id>`, `Path <id> #<slot>`) and the refusal messages go in `AbyssusEditorBundle.properties`. Action
  names and the drawing-mode hint go in `AbyssusBundle.properties`.

## Risks / Trade-offs

- [Corners: copies offset to the inside of a sharp bend can bunch up or cross] → Accepted for the first cut. Users
  can detach or delete the bad ones. A later change could use mitred offsets.
- [A regeneration on a long line rewrites many entities and makes a large diff] → The 1000-copy cap bounds it.
  Unchanged copies are not rewritten.
- [Spacing measured on X/Z looks uneven on steep slopes] → Documented. Measuring along the surface is out of scope.
- [A future transform hierarchy through `ParentComponent` would move copies twice] → Copies are in world coordinates.
  The hierarchy change must exclude arrangement children or convert them. The glossary entry and the component's
  KDoc will say so.
- [Terrain moved or regenerated: copies float or sink until Regenerate] → Explicit Regenerate action. Automatic
  re-placement is a follow-up.
- [Model origin not at its base: copies sink or float] → Documented. The user can detach and drop single copies; a
  height offset setting can follow.
- [Properties-panel edits with no Scene view open need terrain data] → A cached CPU read through `TerrainLoader.prepare`
  off the AWT thread. If a terrain cannot be read, its slots use the line's height, and a warning names the terrain.

## Migration Plan

None. The component is new and optional, the scene format version stays `1`, and scenes without arrangements are
untouched. Older builds load an arrangement scene with one warning and keep the component raw.
