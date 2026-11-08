# Proposal

## Why

Control Line's planes need hinged control surfaces, starting with the trainer's flaps. A model's parts (glTF nodes such
as `LeftFlap` / `RightFlap` of `model_trainer`) are invisible to the editor today: the tree shows the model entity as
one thing, picking only tests the entity's bounding box, and a node's origin is wherever the exporter put it. To make
a flap swing about its hinge line, the author needs to see and select the part, move its origin onto the hinge (with
its X axis along the hinge), say how far it may deflect, and check the result in the Scene view. All of that is a fact
about the model, so it belongs in the model asset, where the editor and the game read it the same way.

This change builds on `add-scene-model-mesh-selection` (meshes of a model) and `add-entity-hierarchy-and-dummies`
(children bound to a model node with `ParentComponent.node`), and is planned after both are archived.

## What Changes

- **Model parts are shown.** Under a model entity's row the Abyssus tree lists a `Parts` row with the model's nodes
  that carry geometry, nested as in the model. Selecting a part (in the tree, or Alt+click in the Scene view) outlines
  that part in the view and shows a part section in Properties. A part with a pivot or a hinge is marked in the tree.
- **Parts are picked by their triangles.** Alt+click in the view hits the triangles of the parts of model entities,
  not their bounding boxes, and selects the nearest part hit.
- **A part's pivot can be moved, as a full frame.** A model asset's `meta.json` may give a node a pivot (position and
  rotation, relative to the node's frame in the model file). When the model is read, the node's origin and axes move to
  the pivot and its geometry and child nodes are moved back by the inverse, so the part stays where it is drawn. The
  same rewrite happens in the Scene view, the game, picking, ray-tracing snapshots and node lookups for children bound
  with `ParentComponent.node`. The model file itself is never rewritten.
- **Edit Pivot mode.** With a part selected, an Edit Pivot toggle on the Scene view toolbar lets the move and rotate
  gizmo drag the pivot frame instead of the entity. Two snaps set the frame from the part's geometry under the cursor:
  **Edge** (position on the nearest triangle edge, X along that edge) and **Face** (position on the face hit, Y along
  its normal). Each finished drag or snap is one undoable edit of the model's `meta.json`. Properties offers the same
  pivot as numbers and a Reset.
- **Children bound to a re-pivoted node keep their place.** The same undoable edit rewrites the local pose of every
  entity in the scene shown in the view whose `ParentComponent.node` names that node of that model.
- **Hinges.** A part's `meta.json` entry may hold a hinge: a minimum and maximum deflection in degrees about the pivot's
  X axis. Properties edits them and offers a **Preview** slider between the limits that turns the part in the Scene
  view only. The preview is never written, resets when the selection changes or the view closes, and wins over a
  playing animation for that node.
- **Refusals.** A pivot is refused with one warning naming the model and node, and the node is left as in the file,
  when the node's mesh is used by another node, its mesh's parts are shared with another node, the mesh is skinned,
  the node has non-uniform scale, the node is animated, or no node of that name exists.
- **Trainer flaps authored.** `model_trainer` in Control Line gets pivots on its hinge lines and hinges for `LeftFlap`
  and `RightFlap`.

### Native file fields

- New in a MODEL asset's `meta.json`, under `additional`: `parts`, an object keyed by node name. Each entry has an
  optional `pivot` (`position` `{x,y,z}`, `rotation` `{x,y,z,w}`, both in the node's own frame in the model file) and an
  optional `hinge` (`min`, `max`, degrees). `parts` is omitted when empty, and an entry is removed when it has neither.
- Reads and writes `PositionComponent.localPosition` / `localRotation` of scene entities whose `ParentComponent.node`
  names a re-pivoted node (compensation only).
- Meta files are validated with `AbyssusDocumentFormat` (integral `formatVersion: 1`) before reading or editing, bound
  to the `ModelMeta` DTO and printed by `JsonProcessor.pretty`. Scenes are edited as `SceneDto`. `formatVersion` stays
  `1`; there is no importer and no migration. A model without `parts` loads exactly as before.

## Out of scope

- The game deflecting the flaps from input (the Control Line flap / elevator coupling), and any runtime hinge API
  beyond the asset data and the loader's re-pivot.
- Authoring ailerons, elevators and rudder of the trainer (they need no new code; only data).
- Animated hinges, hinge axes other than the pivot's X, and limits that depend on another part.
- Writing a re-pivoted `.glb`; re-pivoting a mesh shared by several nodes (it would need splitting the mesh).
- Compensating bound children in scenes other than the one in the view.
- Picking parts of terrain, foliage or anything that is not a model entity.

## Capabilities

### New Capabilities

- `model-parts`: showing a model's parts in the tree, selecting one in the tree or by Alt+click on its triangles, and
  outlining the selected part in the Scene view.
- `model-part-pivots`: the `parts.<node>.pivot` field, the read-time re-pivot every host sees, its refusals, Edit Pivot
  mode with Edge and Face snaps, the numeric pivot fields and Reset, and compensation of bound children.
- `model-part-hinges`: the `parts.<node>.hinge` limits, their validation and the view-only deflection preview.

### Modified Capabilities

- `abyssus-project-view`: a model entity row gets a `Parts` row with its node tree.
- `object-properties-panel`: a part selection shows a part section (pivot, hinge, preview).
- `scene-view-tools`: an Edit Pivot toggle with Edge and Face snap choices.
- `scene-model-animation`: a hinge preview wins over the playing animation for that node.

## Impact

- `lib-core`: `ModelMeta` gains `parts` (new DTOs); a CPU re-pivot of `ModelData` run by `ModelLoader` before
  `selectMesh`; warnings through the loader's log.
- `lib-core-editor`: part listing (node tree with geometry) in `ModelMeshLister`; triangle picking of parts; pivot frame
  math and Edge/Face snap; meta edits through `AssetMetaTextEditor`; bound-child compensation as a `SceneEdit`; hinge
  validation; strings in `AbyssusEditorBundle.properties`.
- `plugin-abyssus`: the `Parts` tree rows, part selection, outline and preview in `sceneview`, the Edit Pivot toolbar
  toggle and gizmo target, the Properties part section, a two-document undoable write (meta and scene), strings in
  `AbyssusBundle.properties`.
- `app-game-control-line`: `model_trainer/meta.json` data only.
- Docs: `docs/ai/file-formats.md` (`parts`), `docs/ai/glossary.md` (part, pivot, hinge), `docs/ai/architecture.md`,
  `projects/lib-core-editor/README.md`, `projects/lib-core/README.md`, the `sceneview` and `projectView` READMEs.
- Open changes: the `scene-entity-hierarchy` delta of `add-entity-hierarchy-and-dummies` ("A child can sit on a node of
  its parent's model") is checked for the re-pivoted rest transform, and amended in a task if needed.
