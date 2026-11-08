# Design

## Context

See proposal.md for motivation; the specs in this change hold the required behavior.

What exists, and what this change relies on:

- `ModelLoader.read` (lib-core) parses a model with Assimp into `ModelData` off the GL thread, runs `selectMesh` for a
  `meshId` variant, then decodes images. `build` turns it into a `Model` on the GL thread. Every host goes through it:
  the editor's `ViewAssets`, the game's `FieldRenderer`, `ModelMeshLister` (geometry only, `decodeTextures = false`),
  and ray snapshots via `ModelPreparationTap` (`RaySnapshotStore.asPreparationTap`).
- `ModelData` is libGDX-shaped: `ModelNode` with translation / rotation / scale, `parts` (mesh part id + material) and
  children; `ModelMesh` with one interleaved `vertices` array and `parts` as index ranges.
- `ModelMeta` (`additional` of a MODEL meta) has `file`, `format`, `binary`, `materials`. `JsonProcessor` writes with
  `ALL_NON_NULL`, so an empty map would be printed as `{}`; a null one is omitted.
- Meta edits: `AssetMetaTextEditor.edit(text, type, change)` returns a `TextEditOutcome`; the plugin writes one document
  with `editTextDocument` as one undoable command. Scenes: `SceneTextEditor` / `editSceneDocument`.
- Scene view: `SceneModels` keeps one `ModelInstance` per entity, with an optional looping `AnimationController`.
  Selection highlight is `SelectionBox` (a box). Picking (`ScenePicker`, `SnapshotSceneQueries`) tests world boxes and
  terrain heights only. The gizmo math (`GizmoDrag`, `GizmoMath`) works on a `PlacementTransform` and is written back by
  `SceneTransformWriter`.
- From `add-entity-hierarchy-and-dummies`: `ParentComponent.node` binds a child to a node's rest transform, read from
  `ModelMeshLister.nodes` in the editor and from the `ModelInstance` nodes in the runtime.

Fixtures: `model_trainer` (Control Line) has one mesh per node, no skins, no node scale, and its flap origins already
lie on the flaps' leading edge (vertex z from -0.040 to 0). `Untitled`'s `model_900f6f61-...` (Piper, `Model 6`) has
192 mesh nodes under one root, none shared, no scale, and nodes such as `flap_L01_Cube.012`.

## Goals / Non-Goals

**Goals:**

- One CPU re-pivot, in lib-core, applied before anything else sees the model, so no host can disagree.
- All math (re-pivot, frame conversion, snaps, triangle hits, hinge limits, compensation) in plain classes with headless
  tests.
- The view's preview and Edit Pivot state live in view state; files change only through the undoable writers.

**Non-Goals:**

- A runtime hinge API for games (Control Line reads `parts` later, in the flap-driving change).
- Re-pivoting while the model is drawn without a reload; a pivot edit reloads the model like any meta edit.

## Decisions

### D1. Data: `ModelMeta.parts: Map<String, ModelPartMeta>?`

```
ModelMeta(..., parts: Map<String, ModelPartMeta>? = null)
ModelPartMeta(pivot: PartPivot? = null, hinge: PartHinge? = null)
PartPivot(position: Vec3Dto? = null, rotation: QuatDto? = null)   // node-local, file frame
PartHinge(min: Float, max: Float)                                  // degrees
```

The map is nullable so that an empty one is omitted; the editing helpers normalize (drop empty entries, null an empty
map). The vector and quaternion DTOs reuse the scene's `{x,y,z}` / `{x,y,z,w}` DTOs if they are reachable from lib-core,
otherwise small new ones with the same JSON shape. Bound by `JsonProcessor.bind`, printed by `JsonProcessor.pretty`; no
hand-written JSON.

*Why keyed by node name:* the node is what has a frame; the glTF mesh name may be shared or differ. *Why relative to the
file frame:* re-snapping replaces the value instead of stacking, and removing `parts` restores the file exactly.

### D2. Re-pivot on `ModelData` in lib-core (`ModelPivots`)

A plain function over `ModelData`, called in `ModelLoader.read` right after `assimp.loadData` and before `selectMesh`
(selectMesh keeps the node tree, so the order is safe, and every variant shares the pivots). For a node with frame
F = T(p) R(q):

```
node TRS            := TRS x F       translation += R*s*p, rotation := R*q, scale unchanged (uniform s)
mesh positions      := q^-1 (v - p)
normals, tangents   := q^-1 n         (tangent w kept; binormals rotated if present)
each child TRS      := F^-1 x child   translation := q^-1 (t - p), rotation := q^-1 * r
```

Refusal checks run first for each entry (spec "Pivots that cannot be applied are refused"): name lookup, the node's
mesh drawn by another node, a mesh part referenced by another node, bones on a node part, non-uniform scale
(`|sx-sy|`, `|sy-sz|` beyond 1e-5 relative), an animation channel on the node. A refused entry logs one warning through
the loader's `Logger` and is skipped. The function returns the list of refusals so `ModelMeshLister` can report the
reason to Properties.

*Alternatives:* rewriting the `.glb` with `GltfWriter` (destructive, lost on re-import, undo means restoring binaries);
rotating only (keep the origin) and storing a hinge point (choice A in exploration, rejected by the user, who wants a
real origin move).

*Vertex sharing:* a `ModelMesh` vertex array may be indexed by parts of several nodes; the shared-mesh and shared-part
checks guarantee every vertex touched belongs to the one node.

### D3. Parts listing and node frames (`ModelMeshLister`)

The lister already prepares `ModelData` through `ModelLoader`, so its node rest matrices include the pivots. It gains:

- `parts(asset)`: the node tree pruned to nodes with geometry and their ancestors, with per node: name, children,
  local bounds of its own geometry, pivot status (applied / refused with reason).
- `fileFrame(asset, node)`: the node's rest transform in the model **without** its own pivot (`rest x F^-1`), needed
  to turn a dragged world frame back into a file-frame pivot.
- Part triangles for picking and snaps: positions per node in node space, index triples, read once per asset and kept
  with the rest of the cached answer (dropped by `invalidate`).

Runs off the UI thread (it parses the file), like today. The tree and Properties show "reading" until it answers.

### D4. Part picking and snaps (lib-core-editor `pick`)

`PartPicker`: given a ray, the model entities' world matrices, node rest matrices and part triangles, returns the
nearest `(entityId, node, triangle, point)` hit (Moller-Trumbore, with an entity box test first). Disabled entities
are skipped as for entity picking. Pure CPU, no GL.

`PivotSnaps`: `edge(hit, currentFrame)` and `face(hit, currentFrame)` return a new world frame per the spec (edge:
closest point on the closest of the 3 edges to the hit point, X along the edge signed toward current X, Y/Z by the
smallest rotation taking current X to the new X; face: Y = triangle normal, X = current X projected onto the plane,
falling back to the edge direction when degenerate).

`PivotFrames`: `toFilePivot(entityWorld, fileFrame, worldFrame)` = `(entityWorld x fileFrame)^-1 x worldFrame`, split into
position and normalized rotation; and the inverse for drawing the gizmo.

The view's selection gains an optional part `(entityId, node)` in `SceneViewState`; the AWT-thread input handler maps
Alt+click to `PartPicker`. The tree and the view exchange part selection through the existing entity selection path
extended with the node name.

### D5. Edit Pivot gizmo

`SceneViewState` gets `editPivot: Boolean` and `pivotSnap: NONE|EDGE|FACE`. With Edit Pivot on, the gizmo's target
transform is the part's world pivot frame (`entityWorld x nodeRest`) instead of the entity's. `GizmoDrag` runs unchanged
on that `PlacementTransform`; on release the plugin converts the result with `PivotFrames.toFilePivot` and writes the
meta (D7). During the drag the gizmo is drawn at the dragged frame; the model is not rebuilt until the write reloads it,
so the part never visibly moves (its geometry is pivot-invariant).

### D6. Hinge preview

The preview angle is view state keyed by `(entityId, node)`, never written. Each frame, after the entity's
`AnimationController.update` and before `calculateTransforms`, the renderer sets the node's local rotation to
`restRotation * axisAngle(X, angle)` for a previewed node and recomputes the instance's transforms. Doing it after the
animation update is what makes the preview win (spec `scene-model-animation`); other nodes keep the animated values.
Bound entities follow because the hierarchy resolver reads node transforms; in the editor it reads rest matrices from
the lister, so the view passes the previewed node matrix to the pose resolver as an override for that frame. Picking
and gizmo keep using rest matrices (spec).

Hinge validation (`HingeLimits`: `min <= 0 <= max`, within +-180, finite) is a plain lib-core-editor class used by
both Properties and the loader-side warning in lib-core (a three-line check duplicated in lib-core is acceptable;
lib-core cannot depend on lib-core-editor).

### D7. Writing: one command over the meta and the scene

A pivot edit changes `meta.json` and, when bound children exist in the view's scene, that `.scene` too, in one undo
step. `editTextDocument` covers one document, so the plugin gets `editAssetMetaAndScene(project, meta, scene?, name,
metaTransform, sceneEdit?)` next to it in `SceneDocumentWriter.kt`: it computes both new texts first (refusing
entirely if either refuses or a text changed meanwhile), then sets both documents in one `WriteCommandAction` marked
global so Undo works from the view, the tree or Properties. *Why a new writer:* undo of a pivot without its compensation
would move bound dummies; two commands would let the user undo half. Scenes are still edited only through
`SceneTextEditor`, as a `SceneEdit` (`CompensateBoundChildren`), so the "scenes only through `editSceneDocument`" rule
is kept in substance; the new function is listed in AGENTS.md next to it.

`CompensateBoundChildren` (lib-core-editor `hierarchy`): for each entity whose parent draws the asset and whose
`ParentComponent.node` is the node, `local' = (nodeRestNew)^-1 x nodeRestOld x local` (positions and rotations;
scale untouched), via the existing `PositionCodec`. Hinge-only and Reset-without-children edits use plain
`editTextDocument` on the meta.

The meta edit itself is `AssetMetaTextEditor.edit<ModelMeta>(…, MetaType.MODEL)` with a `ModelPartEdits` helper
(set pivot, clear pivot, set hinge, clear hinge, normalize). Reload follows from the existing meta change refresh
(`asset-loading` "Changed assets reload"); the lister must be invalidated on the same signal (task 3.4).

### D8. Threads

| Piece | Thread |
|---|---|
| `ModelPivots` | wherever `ModelLoader.read` runs (prepare, off GL) |
| lister `parts` / triangles | background (pooled, as the mesh lister today) |
| `PartPicker`, `PivotSnaps`, `PivotFrames` | AWT input thread, CPU only, on cached data; a miss in the cache means no hit |
| gizmo, preview pose, outline | AWT render thread inside `GdxRuntime.withContext` |
| meta/scene write | EDT, `WriteCommandAction` |

## Risks / Trade-offs

- [Bound children in other scenes shift after a pivot edit] -> Out of scope by decision; noted in docs. Today no scene
  binds a node, and the leadout dummies bind to the plane, not to a node.
- [The reload after each pivot edit rebuilds the whole model (texture upload one per frame)] -> Acceptable for an edit
  action; the drag itself does not reload. If it stutters on large models, a later change can rebuild only geometry.
- [Float drift: vertices moved by F^-1 then drawn by F] -> Errors are about 1e-7 of the model size; tests compare world
  positions with a tolerance.
- [Assimp may merge or rename nodes, so node names in `parts` stop matching after re-export] -> Unknown names warn and
  are ignored (spec), and Properties marks them.
- [The hierarchy change is not archived yet] -> This change starts after it; task 1.1 checks and amends its delta.
- [Triangle data per asset costs memory (Piper: 192 parts)] -> Positions and indices only, cached per asset, dropped on
  invalidate.

## Migration Plan

No migration: models without `parts` are unchanged. Rollback is removing `parts` from a meta.
