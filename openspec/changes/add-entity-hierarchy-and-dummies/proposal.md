# Proposal

## Why

Points and parts that belong to a model cannot be authored as part of it. Control Line's leadouts (where the control
lines enter the plane) are bare numbers in `PlaneComponent.leadoutUp` / `leadoutDown`: invisible in the Scene view and
unrelated to the model. Planned per-part colliders, aero surfaces and hinged control surfaces (ailerons, elevator,
rudder) need the same thing first: a child entity that sits in its model's frame, follows the model and is shown under
it in the tree. Today `ParentComponent` is only a link. The Scene view "ignores parents and uses `local*` as world
values", and the tree lists every entity flat.

This change makes `ParentComponent` a real local-transform hierarchy and adds **dummies** (named attach points) as its
first user. The plugin is not in production, so the committed scenes and fixtures are rewritten once rather than
versioned.

## What Changes

- **BREAKING (semantics, no version bump):** an entity's `PositionComponent` (`localPosition`, `localRotation`,
  `localScale`) is now relative to the entity named by its `ParentComponent.parentEntityId`. World pose = parent's world
  pose x local pose, resolved recursively. This applies in the Scene view, in picking, in gizmos, in the Properties
  panel, in ray-tracing snapshots, in the game runtime (`RuntimeSceneLoader` / `SceneEngine`) and in physics. A parent
  cycle, or a parent missing from the scene, is refused with one warning naming the entity, and that entity is treated
  as a root.
- **Composition uses the parent's stored pose only**: its `PositionComponent` position, `localRotation` and scale. It
  never uses an orientation derived from `lookAtId`. This breaks the cycle between a handle-aimed light and its
  `HANDLE` child: moving a light or camera carries its handle along, so the aim is kept.
- **Optional node binding:** `ParentComponent` gains an optional `node`, the name of a node in the parent's model
  (e.g. `LeftWing` of `model_trainer`). When it is set, the child is local to that node's transform within the model.
  An unknown node gives one warning and falls back to the parent's frame.
- **Gizmo and Properties work in local space for children:** a drag writes the child's local pose, and the world pose
  is derived from it. Moving a parent moves its children without writing them.
- **Tree nesting:** in the Abyssus tree, an entity with a valid parent is listed as a child row under its parent's
  entity row, not at the top level. Its components stay one level below it, as today.
- **Arrangements:** path `points` and copies are read in the arrangement entity's local space. Arrangement groups have
  no `PositionComponent` today, so the existing numbers are unchanged. **Detach** writes the copy's world pose into
  its `localPosition` as it removes the `ParentComponent`, so the copy does not move.
- **Dummies:** a new entity `type` value `DUMMY` with `NameComponent`, `PositionComponent` and `ParentComponent`. "Add
  Dummy" is offered on an entity row (tree context menu) and in the Scene view. It creates a dummy as a child of that
  entity, at the entity's origin, in one undoable edit. The Scene view draws a dummy as a small three-axis marker,
  under its own "Show Dummies" toggle that is off when a view opens, and a dummy can be picked and moved like any
  entity. A game finds a dummy by its parent and its name.
- **Control Line leadouts become dummies:** each plane in `Field.scene` gets two `DUMMY` children named `leadoutUp` and
  `leadoutDown`, placed at the current leadout values. `PlaneComponent.leadoutUp` / `leadoutDown` are removed, and
  `LineRig` reads the dummies. A plane without them is refused with one warning naming the plane.
- **One-time rewrite of committed data:** every `HANDLE` with a parent in `Untitled`, `Physics`, `Tree`, `Lights`
  (`Abyssus Lights`, `Creation Baseline`) gets `localPosition = inverse(parent stored pose) x its old world position`.
  `Field.scene` and `toolsTest/fixtures/Field.original.scene` keep their arrangement copies as they are, because the
  parents have identity poses, and get the leadout dummies. `formatVersion` stays `1`, and there is no importer or
  load-time migration.
- **Physics:** a child carrying its own `RigidBodyComponent` is refused with one warning. A child collider becomes a
  static body at its world pose when no ancestor moves. Under a dynamic or kinematic ancestor it is skipped with one
  warning, until the compound-collider change. Physics writes poses back only to root entities.
- **Warnings are reported once** per entity and reason for each loaded scene (missing parent, self-link, cycle, unknown
  node), not on every update.
- **A disabled parent disables its subtree** in drawing, systems and physics; the children's own flags are not written.
- **Pasting a dummy** makes a root dummy with the same local values, as the clipboard drops `ParentComponent`.
- **Removal takes the subtree:** Remove Entity on a parent removes its descendants in the same undoable edit. A
  child's link to its parent no longer blocks the removal.

### Native file fields

- Reads and writes `ParentComponent.parentEntityId` (meaning changes to "local to this entity") and the new optional
  `ParentComponent.node` (a string, omitted when unset).
- Reads and writes `PositionComponent.localPosition` / `localRotation` / `localScale` of child entities as local values.
- Adds the entity `type` value `DUMMY`. No new component.
- Removes `PlaneComponent.leadoutUp` / `leadoutDown` from Control Line's game component. This is a game component, not
  part of the Abyssus format.
- Scenes are validated with `AbyssusDocumentFormat` before every read and edit, edited as `SceneDto` through
  `editSceneDocument` / `SceneTextEditor` and printed by `JsonProcessor.pretty`. `formatVersion` stays `1`.

## Out of scope (later changes)

- **Compound part colliders:** child `ColliderComponent`s becoming sub-shapes of the parent's body (horizontal
  stabilizer, wing and fin boxes). Planned as `add-compound-part-colliders`.
- **Aero surfaces:** per-part lift and drag surfaces that Control Line's `Aero` sums. This includes making those game
  components editable in Abyssus. Planned as `add-plane-aero-surfaces`.
- **Hinged control surfaces:** rotating a model node (aileron, elevator, rudder) by a deflection, with its child box and
  surface following, plus any editor deflection preview. Planned as `add-hinged-control-surfaces`.
- Reparenting by drag and drop in the tree. A "Set Parent" action may come later; parents are set by the creating
  action or in Properties.
- Animated node transforms. A `node` binding uses the model's rest pose.
- Copying and pasting a subtree. The clipboard keeps dropping `ParentComponent`, as today.
- Dummies on a terrain, and dummies drawn in a shipped game.

## Capabilities

### New Capabilities
- `scene-entity-hierarchy`: parent-local transforms, recursion, cycle and missing-parent handling, the optional `node`
  binding, and composition from the parent's stored pose only.
- `scene-dummies`: the `DUMMY` entity type, Add Dummy, the marker overlay with its toggle, picking, and lookup by name.

### Modified Capabilities
- `abyssus-project-view`: entity rows nest under their parent's row.
- `scene-object-transform`: gizmo moves and turns of a child write its local pose; moving a parent moves its children.
- `scene-path-arrangements`: points and copies are local to the arrangement, and detach keeps the copy's world pose.
- `scene-entity-lights`: a handle's position is local to its light or camera, which carries the handle when moved.
- `scene-ecs-systems`: the runtime resolves world poses through parents.
- `physics-simulation`: a child collider uses its world pose; a child rigid body is refused; write-back goes to roots
  only.
- `control-line-flight`: leadouts come from the plane's `leadoutUp` / `leadoutDown` dummies.
- `scene-view-toolbar`: a Show Dummies toggle, off on open.
- `scene-picking`: dummy markers are picked by screen distance.
- `object-properties-panel`: a child's position is shown as local, with a note.
- `scene-physics-overlay`: collider outlines are drawn at world pose.
- `headless-scene-editing`: `HeadlessEditing.addDummy`.

## Impact

- `lib-core`: a world-pose resolver over `ParentComponent` (plain, constructor-wired, CPU only), `ParentComponent.node`,
  the `DUMMY` header type, model node lookup by name from `ModelData` without GL, and the runtime transform system.
- `lib-core-editor`: `SceneContent` world poses, picking, gizmo write-back in local space (`SceneTransformWriter`,
  `PositionCodec`), arrangement read/regenerate/detach, an Add Dummy `SceneEdit`, `HeadlessEditing`, ray snapshots, and
  strings in `AbyssusEditorBundle.properties`.
- `lib-physics`: world pose for child colliders, refusal of child bodies, root-only write-back.
- `plugin-abyssus`: tree nesting (`projectView`), the Scene view's renderer, overlay and toolbar toggle (`sceneview`),
  Add Dummy actions, and strings in `AbyssusBundle.properties`.
- `app-game-control-line`: `PlaneComponent`, `LineRig`, `Field.scene`, `toolsTest/fixtures/Field.original.scene` and
  their tests.
- Fixtures: the five test scenes listed above (handle rewrites). Tests that pin handle coordinates change.
- Docs: `docs/ai/file-formats.md` (the `ParentComponent` row and `DUMMY`), `docs/ai/glossary.md`,
  `docs/ai/architecture.md`, `projects/lib-core-editor/README.md`, the `sceneview` and `projectView` READMEs, and the
  Control Line README. Open changes whose delta specs assume world-space children
  (e.g. `add-scene-model-mesh-selection`, `add-lights-from-model-meshes`) are checked and amended in a task.
