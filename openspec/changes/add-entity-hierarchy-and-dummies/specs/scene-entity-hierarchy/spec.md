# Spec Delta

## Purpose

Lets an entity sit in the frame of another entity, or of a named node of that entity's model, so it follows its parent
in the editor, in a game and in physics, and is authored relative to it.

## ADDED Requirements

### Requirement: A child's pose is local to its parent

An entity whose `ParentComponent.parentEntityId` names another entity of the scene SHALL be a child of that entity. Its
`PositionComponent` (`localPosition`, `localRotation`, `localScale`) SHALL be relative to the parent, and its world pose
SHALL be the parent's world pose combined with its own local pose, resolved through any number of parents. An entity
without a `ParentComponent`, or with `parentEntityId` `-1`, SHALL be a root, and its local pose SHALL be its world pose.
The Scene view, picking, gizmos, shadows, ray tracing and a game SHALL all draw and use an entity at its world pose.

#### Scenario: A child follows its parent

- **WHEN** in a copy of `Untitled` a dummy whose `localPosition` is (0, 1, 0) is the child of `Model 9` at
  (0, 21.632797, 0)
- **THEN** the dummy is drawn and picked at (0, 22.632797, 0)

#### Scenario: A rotated parent turns its child's offset

- **WHEN** a child with `localPosition` (1, 0, 0) has a parent at the origin turned a quarter turn about Y
- **THEN** the child's world position is (0, 0, -1)

#### Scenario: Two levels

- **WHEN** entity A at (1, 0, 0) is the parent of B with `localPosition` (1, 0, 0), and B is the parent of C with
  `localPosition` (1, 0, 0)
- **THEN** C's world position is (3, 0, 0)

#### Scenario: A scaled parent scales its child

- **WHEN** a parent at the origin has `localScale` (2, 2, 2) and its child has `localPosition` (1, 0, 0) and no scale
- **THEN** the child is at (2, 0, 0) and drawn at twice its size

### Requirement: A parent's stored pose is what its children follow

A child SHALL be placed from its parent's stored position, `localRotation` and `localScale` (and, for a parent's parent,
the same rule upward). An orientation that a parent gets from its `lookAtId` SHALL NOT turn its children. So a light or
camera aimed at its own child handle SHALL face that handle without a cycle.

#### Scenario: A light aimed at its child handle

- **WHEN** `Lights/scenes/Abyssus Lights.scene` is shown, where directional light `1` at (0, 10, 0) has no rotation and
  `lookAtId` 0, and handle `0` is its child with `localPosition` (0, -10, 0)
- **THEN** handle `0` is at the origin and the light shines straight down

#### Scenario: A camera's stored rotation turns its handle

- **WHEN** in `Untitled`, `Camera 4` has a stored `localRotation` and `lookAtId` 3, and handle `3` is its child
- **THEN** handle `3` is placed by the camera's stored position and rotation, and the camera faces handle `3`

### Requirement: A child can sit on a node of its parent's model

`ParentComponent` SHALL accept an optional `node`, the name of a node in the parent's model. When it is set and the
parent's model has a node of that name, the child's local pose SHALL be relative to that node's rest transform within
the model, and the child SHALL follow the parent as before. When the node is not found, or the parent has no model,
the child SHALL be placed relative to the parent itself and one warning naming the child and the node SHALL be logged.
A missing `node` SHALL be omitted from the file.

#### Scenario: On a wing

- **WHEN** a dummy with `localPosition` (0, 0, 0) is the child of the `Trainer` entity of Control Line's `Field.scene`
  with `node` `LeftWing`
- **THEN** it is drawn at the origin of the `LeftWing` node of `model_trainer`, where the plane is placed

#### Scenario: Unknown node

- **WHEN** a child names `node` `NoSuchNode` on a parent whose model has no such node
- **THEN** it is placed relative to the parent's own frame and one warning names the child and `NoSuchNode`

### Requirement: Broken parent links are roots

A `parentEntityId` that names no entity of the scene, names the entity itself, or closes a cycle of parents SHALL be
refused with one warning naming the entity, and that entity SHALL be treated as a root whose local pose is its world
pose. The file SHALL NOT be changed by the refusal.

#### Scenario: Missing parent

- **WHEN** an entity's `parentEntityId` is `99` in a scene that has no entity `99`
- **THEN** the entity is drawn at its `localPosition` as a world position and one warning names the entity

#### Scenario: A cycle

- **WHEN** entity A's parent is B and B's parent is A
- **THEN** the scene still opens and plays, both are treated as roots, and a warning names each

### Requirement: Hierarchy warnings are reported once

A warning about a hierarchy problem (missing parent, self-link, cycle, unknown node) SHALL be reported once per entity
and reason for each loaded scene, not on every update or rebuild.

#### Scenario: Repeated updates

- **WHEN** a scene with a cycle is updated 100 times
- **THEN** one warning names each entity of the cycle

### Requirement: A disabled parent disables its subtree

An entity whose own `enabled` flag is false, or whose ancestor is disabled, SHALL be treated as disabled in drawing,
systems and physics. Its flag SHALL be unchanged in the file. Enabling the parent SHALL restore the children that are
not disabled themselves.

#### Scenario: Disable a model with a dummy

- **WHEN** `Model 9` of a copy of `Untitled` is disabled and has an enabled child dummy
- **THEN** the dummy's marker is not drawn, and the dummy's `enabled` flag in the file is unchanged

### Requirement: Moving a parent carries its children

A gizmo move or turn of a parent, a drop of a parent and an edit of a parent's `PositionComponent` in the Properties
panel SHALL write only the parent's `PositionComponent`. Its children SHALL follow in the view at once, during the drag
and after the write, without their own `PositionComponent` changing.

#### Scenario: Drag a model with a dummy

- **WHEN** `Model 9` of a copy of `Untitled` has a child dummy and the user moves `Model 9` along X by 2 and releases
- **THEN** only `Model 9`'s `localPosition.x` changes in the file, and the dummy is drawn 2 further along X

#### Scenario: Undo carries the children back

- **WHEN** the user then presses Undo
- **THEN** `Model 9` and its dummy are drawn at their old places

### Requirement: Children are edited in local values

The Properties panel SHALL show and edit a child's `PositionComponent` as values local to its parent (or node), and
SHALL say that they are local. A gizmo move or turn of a child SHALL act along world axes, as for any entity, and SHALL
write the resulting local pose to the child only.

#### Scenario: Non-uniform parent scale under rotation

- **WHEN** a parent turned a quarter turn about X has `localScale` (1, 2, 1) and the user moves its child with the gizmo
- **THEN** the child's world position is exact, its rotation and scale are the best TRS fit, and one warning says the
  parent's scale shears the child

#### Scenario: Move a dummy along world Y

- **WHEN** a dummy is the child of a parent turned a quarter turn about X, and the user drags its Y arrow up by 1 and
  releases
- **THEN** the dummy is 1 higher in world space, and the file holds its new local pose under that parent; the parent is
  unchanged

### Requirement: Removing a parent removes its children

Remove Entity on an entity with children SHALL remove it together with all its descendants, in one undoable edit. It
SHALL be refused, with the relationship named and the file unchanged, when an entity outside that set still refers to
one of them, as for any referenced entity. A child's link to its own parent SHALL NOT by itself block the removal.

#### Scenario: Remove a model with a dummy

- **WHEN** `Model 9` of a copy of `Untitled` has one child dummy and the user removes `Model 9`
- **THEN** both are gone from the file in one edit, and Undo brings both back

#### Scenario: Remove a dummy

- **WHEN** the user removes only the dummy
- **THEN** the dummy is gone and `Model 9` is unchanged
