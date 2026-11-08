# Spec Delta

## Purpose

Lets the author move a model part's origin and axes (its pivot) to where the part should turn, such as a flap's hinge
line, stored in the model asset and seen the same way by the editor and the game, without rewriting the model file.

## ADDED Requirements

### Requirement: A part's pivot is stored in the model asset

A MODEL asset's `meta.json` SHALL accept, under `additional`, a `parts` object keyed by node name. An entry MAY hold a
`pivot` with a `position` `{x, y, z}` and a `rotation` `{x, y, z, w}` (a unit quaternion), both relative to the node's
own frame in the model file, never to an earlier pivot. A missing `position` SHALL mean the node's origin and a missing
`rotation` no rotation. `parts` SHALL be omitted from the file when it has no entries, and an entry SHALL be removed
when it holds neither a pivot nor a hinge. A model without `parts` SHALL load exactly as before. The file SHALL keep
`formatVersion: 1`.

#### Scenario: Pivot on the trainer's flap

- **WHEN** `model_trainer/meta.json` holds `parts.LeftFlap.pivot` with `position` (0, -0.002, 0) and no `rotation`
- **THEN** the flap's origin is 0.002 below its origin in `model.glb`, with the same axes

#### Scenario: No parts

- **WHEN** the meta of `Untitled`'s `model_900f6f61-6384-434a-be81-56ce303fbb56` is read
- **THEN** it has no `parts` and the model is drawn as before this change

### Requirement: A pivot moves the origin, not the part

When a model is read, every node with a valid pivot SHALL get its origin and axes at the pivot frame, and its geometry
and child nodes SHALL be moved back by the inverse of that frame, so that in the rest pose every vertex and every child
node stays where it was in the model file (within float rounding), with normals and tangents still matching the
surface. Every place that reads the model SHALL see the same result: the Scene view, the game, part picking and
outlines, ray-tracing snapshots, and the node a child is bound to with `ParentComponent.node`.

#### Scenario: Same picture, new origin

- **WHEN** `LeftFlap` of `model_trainer` gets a pivot with a 90 degree rotation about Z at position (0, 0, -0.02)
- **THEN** the Scene view draws the trainer as before, and the flap's node origin is 0.02 behind its old origin with
  its X axis along the old Y axis

#### Scenario: Game and editor agree

- **WHEN** Control Line runs `Field.scene` with the same pivot
- **THEN** the trainer looks as in the Scene view, and a deflection about the flap's X axis turns it about that frame

### Requirement: Pivots that cannot be applied are refused

A pivot SHALL be refused, with one warning naming the model and the node, and that node SHALL be left as in the model
file, when no node has that name, the node's mesh is drawn by another node too, a mesh part of the node is used by
another node, the node's mesh is skinned, the node's own scale is not uniform, or an animation of the model moves the
node. A refused pivot SHALL NOT affect the other nodes. Edit Pivot SHALL NOT be offered for such a part, and Properties
SHALL show the reason.

#### Scenario: Unknown node

- **WHEN** `model_trainer/meta.json` names a part `NoSuchNode` with a pivot
- **THEN** the trainer loads, its other pivots apply, and one warning names `model_trainer` and `NoSuchNode`

#### Scenario: Non-uniform scale

- **WHEN** a model's node has scale (1, 2, 1) and its meta gives it a pivot
- **THEN** the node is drawn as in the model file, one warning is logged, and Properties shows that its scale is not
  uniform

### Requirement: Edit Pivot mode drags the pivot frame

With a part selected whose pivot can be applied, the user SHALL be able to turn on Edit Pivot. While it is on, the move
arrows and rotate rings SHALL be drawn at the part's pivot frame and SHALL drag that frame (Local and World as for
entities, Snap as for entities); the part's geometry SHALL stay where it is. A finished drag SHALL write the part's
`pivot` in the model's `meta.json` as one undoable command named for the part. A cancelled drag SHALL write nothing.
Turning Edit Pivot off, or selecting another entity or part, SHALL return the gizmo to the entity.

#### Scenario: Drag the flap pivot forward

- **WHEN** Edit Pivot is on for `LeftFlap` of the trainer in `Field.scene` and the user drags the pivot's Z arrow
- **THEN** the flap does not move, the gizmo moves, and after release `parts.LeftFlap.pivot.position` in
  `model_trainer/meta.json` holds the new place; Undo restores the file

#### Scenario: Gizmo back to the entity

- **WHEN** Edit Pivot is on and the user clicks `Model 0` without Alt
- **THEN** Edit Pivot is off and the gizmo is on `Model 0`

### Requirement: Edge and Face snaps set the pivot from the geometry

In Edit Pivot, the user SHALL be able to choose a snap and click the selected part. **Edge** SHALL set the pivot
position to the point on the nearest edge of the triangle under the cursor that is closest to the click, and the X
axis along that edge, pointing the way closest to the current X axis; Y and Z SHALL turn as little as possible from the
current frame. **Face** SHALL set the position to the point hit and the Y axis to the face's outward normal; X SHALL be
the current X axis projected onto the face. A click that misses the part SHALL change nothing. Each snap SHALL be one
undoable edit like a drag.

#### Scenario: Flap hinge on the leading edge

- **WHEN** Edge snap is chosen and the user clicks near the leading edge of `LeftFlap`
- **THEN** the pivot sits on that edge with X along it, the flap does not move, and the meta holds the pivot

#### Scenario: Missing the part

- **WHEN** Face snap is chosen and the user clicks the wing next to the flap
- **THEN** nothing is written

### Requirement: Pivot in Properties

The part section in Properties SHALL show the pivot position and rotation as editable numbers in the node's frame of
the model file, and a Reset that removes the pivot. Edits SHALL be written as for a drag, and invalid numbers (not
finite) SHALL be refused without writing.

#### Scenario: Reset

- **WHEN** the user presses Reset on the pivot of `LeftFlap`
- **THEN** `parts.LeftFlap` loses its `pivot` (and is removed when it has no hinge) in one undoable edit

### Requirement: Children bound to a re-pivoted node keep their place

A pivot edit SHALL also rewrite, in the same undoable command, the local position and rotation of every entity of the
scene shown in the view whose `ParentComponent.node` names that node and whose parent draws that model, so that each
keeps its world pose. Entities in other scenes SHALL NOT be rewritten. Undo SHALL restore both the meta and the scene.

#### Scenario: A dummy on the flap

- **WHEN** a dummy under the `Trainer` entity of `Field.scene` has `node` `LeftFlap`, and the user moves the
  `LeftFlap` pivot
- **THEN** the dummy stays where it was drawn, its `localPosition` changes, and one Undo restores both files
