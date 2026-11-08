# Spec Delta

## Purpose

Lets an author mark named points and frames on an entity (for example where a control line enters a plane) as child
entities that are seen, picked and moved in the Scene view, and that a game reads by name.

## ADDED Requirements

### Requirement: A dummy is a named child entity

An entity whose `type` is `DUMMY` SHALL be a dummy. It SHALL hold a `NameComponent`, a `PositionComponent` and a
`ParentComponent` (optionally with a `node`), and nothing else is needed. A dummy SHALL draw nothing in a game and SHALL
take no part in physics. A dummy without a valid parent SHALL be kept, shown and treated as a root.

A dummy SHALL ignore any render, collider or rigid-body component put on it: nothing is drawn by them in a game and
no body is created. Copying a dummy and pasting it SHALL create a root dummy that keeps its local values, as the
clipboard drops `ParentComponent`.

#### Scenario: What lands in the file

- **WHEN** a dummy named `Dummy` is added to `Model 9` (entity `9`) of a copy of `Untitled`
- **THEN** the file holds a new entity with `"type": "DUMMY"`, `"NameComponent": {"name": "Dummy"}`, an empty
  `"PositionComponent": {}` and `"ParentComponent": {"parentEntityId": 9}`, with the next free id

#### Scenario: Extra components are ignored

- **WHEN** a dummy has a `ColliderComponent` and the `Physics` scene is simulated
- **THEN** no body is created for the dummy

#### Scenario: A pasted dummy

- **WHEN** a dummy under `Model 9` is copied and pasted
- **THEN** the new dummy has no `ParentComponent` and the same local values as the original

### Requirement: Add Dummy

The right-click menu of an entity row in the Abyssus tree SHALL offer "Add Dummy" (in its Add group). It SHALL add one dummy as a child of that entity at its origin, named `Dummy` (or `Dummy 2`,
`Dummy 3`... when a sibling already has that name), in one undoable edit, and SHALL select the new dummy. It SHALL NOT
be offered on a path arrangement or on one of its linked copies.

#### Scenario: Add and undo

- **WHEN** the user chooses Add Dummy on `Model 9` and then presses Undo
- **THEN** the dummy appears under `Model 9` and is selected, and after Undo the file is as it was before

#### Scenario: Second dummy

- **WHEN** `Model 9` already has a child named `Dummy` and the user chooses Add Dummy again
- **THEN** the new dummy is named `Dummy 2`

### Requirement: Dummies are drawn as markers

With "Show Dummies" on, the Scene view SHALL draw each enabled dummy as a three-axis marker at its world pose: a red
X, a green Y and a blue Z line, each a fixed size on screen. The selected dummy's marker SHALL be brighter and drawn on
top of other geometry. "Show Dummies" SHALL be a Scene view toolbar toggle, off when a view opens. Selecting a dummy
in the tree SHALL show its marker even while the toggle is off. A disabled dummy SHALL NOT be drawn.

#### Scenario: Off by default

- **WHEN** a Scene view opens on a scene with dummies
- **THEN** no dummy markers are drawn until Show Dummies is turned on or a dummy is selected

#### Scenario: Turning the parent turns the marker

- **WHEN** Show Dummies is on and the dummy's parent is turned a quarter turn about Y
- **THEN** the dummy's red X line points along world -Z

### Requirement: Dummies are picked and moved like entities

A drawn dummy marker SHALL be pickable by clicking near its centre, which selects the dummy in the tree and in the
Properties panel. A selected dummy SHALL get the move and rotate gizmos, and their writes SHALL follow the hierarchy
rules for children.

#### Scenario: Click a marker

- **WHEN** Show Dummies is on and the user clicks a dummy's marker
- **THEN** the dummy's row is selected in the tree, and the move gizmo appears at the marker

### Requirement: A game finds a dummy by its parent and name

A game SHALL be able to look up, for an entity, its child dummy of a given name and read that dummy's world pose and
its pose in the parent's frame, both updated as the parent moves. When two children of the entity share the name,
the one with the lowest id SHALL be found and one warning naming the entity and the name SHALL be logged.

#### Scenario: Leadout lookup

- **WHEN** a game asks the `Trainer` entity of `Field.scene` for its dummy `leadoutUp`
- **THEN** it gets that dummy's pose in the plane's frame, (0.48, 0, 0.02)
