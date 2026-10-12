# Spec Delta

## Purpose

Lets users place many copies of one project model along a line drawn on the terrain, such as streetlights along a
road, and keep those copies following the line when its points or settings change.

## ADDED Requirements

### Requirement: Add Path Arrangement asks for a model

The plugin SHALL offer Add Path Arrangement in the Scene view toolbar and in the right-click menu of a scene row of the
Abyssus tree, next to Add Asset. It SHALL list the MODEL assets of the scene's project by folder name in name order. It
SHALL be unavailable when the scene cannot be read as a native scene, belongs to no project, or the project has no
model.

#### Scenario: The fixture's models

- **WHEN** the user opens Add Path Arrangement for `Main Scene` of the Untitled project
- **THEN** it lists `model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`, `model_828d51e4-8427-4769-bcb6-13f8f21f23e9`, `model_900f6f61-6384-434a-be81-56ce303fbb56`, `model_fc33e1f1-015b-4524-9b10-aa417acd273c` and `tree`, and no terrain or skybox

#### Scenario: Unreadable scene

- **WHEN** the scene file holds text that is not valid JSON
- **THEN** Add Path Arrangement is disabled and nothing is written

#### Scenario: Chosen from the tree

- **WHEN** the user chooses `tree` from the right-click menu of the `Main Scene` row
- **THEN** the Scene view of `Main Scene` opens in drawing mode for `tree`

### Requirement: Drawing a path on the terrain

After a model is chosen, the Scene view SHALL be in drawing mode. Each left click on a terrain SHALL add a point where the
click hits the terrain; a click that hits no terrain SHALL add nothing. While drawing, the view SHALL show the line so far
and markers where the copies would go, updated after every point. Camera navigation SHALL keep working.

#### Scenario: Points follow the clicks

- **WHEN** in drawing mode the user clicks the terrain of `Main Scene` at three places
- **THEN** the view shows a line through three points on the terrain surface and a marker for every copy along it

#### Scenario: A click in the sky

- **WHEN** in drawing mode the user clicks where no terrain is under the cursor
- **THEN** no point is added and nothing is written

#### Scenario: Remove the last point

- **WHEN** in drawing mode the user presses Backspace after adding three points
- **THEN** the line has two points and the markers are updated

### Requirement: Finishing or cancelling a drawing

Enter or a double click SHALL finish a drawing that has at least two points and write the arrangement. Clicking the
first point of a drawing that has at least three points SHALL finish it as a closed line. Esc SHALL end drawing mode and
write nothing. Finishing with fewer than two points SHALL do nothing and keep drawing mode.

#### Scenario: Finish an open line

- **WHEN** the user adds two points and presses Enter
- **THEN** one arrangement and its copies are written and drawing mode ends

#### Scenario: Close a loop

- **WHEN** the user adds four points and then clicks the first one
- **THEN** the arrangement is written with `closed` `true` and copies run along all four sides

#### Scenario: Cancel

- **WHEN** the user adds three points and presses Esc
- **THEN** drawing mode ends and the scene file is unchanged

#### Scenario: Too few points

- **WHEN** the user adds one point and presses Enter
- **THEN** nothing is written and drawing mode continues

### Requirement: What a new arrangement writes

Finishing a drawing SHALL add an arrangement entity named `Path <id>`, with type `GROUP` and a `PathArrangementComponent`
holding the points, `closed`, the chosen model as `asset`, the default settings and `slots`, followed by one entity per
copy. Ids SHALL count up from one more than the highest entity id. Everything else in the file SHALL stay as it was.

#### Scenario: A line of trees in Main Scene

- **WHEN** the user draws `tree` in `Main Scene` (highest entity id `10`) from (0, h0, 0) to (40, h1, 0) on the terrain and presses Enter
- **THEN** the file gains entity `11` named `Path 11` with type `GROUP`, whose `PathArrangementComponent` has the two points, `closed` `false`, `asset` `MODEL` `tree`, spacing `10` and `slots` `[12, 13, 14, 15, 16]`, and entities `12` to `16`; entities `0` to `10` are unchanged

#### Scenario: What a copy holds

- **WHEN** the arrangement above is written
- **THEN** entity `12` is named `Path 11 #0`, has type `OBJECT`, a `PositionComponent`, a render component of asset `MODEL` `tree` with shader key `defaultShader`, and a `ParentComponent` whose `parentEntityId` is `11`

#### Scenario: Selected afterwards

- **WHEN** an arrangement is written
- **THEN** the arrangement's row is selected in the Abyssus tree

### Requirement: Arrangement settings

An arrangement SHALL have these settings: `spacing` (distance between copies, default `10`), `offset` (distance from the
line, default `0`), `sides` (`CENTER`, `LEFT`, `RIGHT` or `BOTH`, default `CENTER`), `alignToPath` (default `true`) and
`yawOffset` (degrees, default `0`). A spacing that is not above `0`, a negative offset, or settings that would make more
than 1000 copies SHALL be refused with a message and leave the file unchanged.

#### Scenario: Spacing of zero

- **WHEN** the user sets `spacing` of `Path 11` to `0` in the Properties panel
- **THEN** the file is unchanged and a message names the field and its limit

#### Scenario: Too many copies

- **WHEN** the user sets `spacing` of a 2000-unit line to `1`
- **THEN** the file is unchanged and a message says the arrangement would make more than 1000 copies

### Requirement: Copies are spaced along the line

Copies SHALL stand at stations along the line, measured on the ground plane (X and Z) from the first point: at `0`,
`spacing`, `2 x spacing` and so on up to the end of an open line, or up to but not including the full length of a
closed line, which runs back from the last point to the first.

#### Scenario: Open line

- **WHEN** an open line runs 40 units from (0, 0, 0) to (40, 0, 0) with spacing `10`
- **THEN** there are five stations, at X `0`, `10`, `20`, `30` and `40`

#### Scenario: Closed square

- **WHEN** a closed line runs around a 20 by 20 square with spacing `10`
- **THEN** there are eight stations, two on each side, and none repeats the first point

#### Scenario: Line shorter than the spacing

- **WHEN** an open line is 5 units long with spacing `10`
- **THEN** there is one station, at the first point

### Requirement: Copies stand beside the line

With `sides` `CENTER` each station SHALL have one copy on the line. With `LEFT` or `RIGHT` it SHALL have one copy
`offset` away on that side, seen from above while facing along the line. With `BOTH` it SHALL have two copies, left then
right. The slot number SHALL count copies in that order along the line.

#### Scenario: Both sides of a road

- **WHEN** a line runs along +X from (0, 0, 0) to (10, 0, 0) with spacing `10`, `sides` `BOTH` and `offset` `3`
- **THEN** slot `0` is at (0, y, -3), slot `1` at (0, y, 3), slot `2` at (10, y, -3) and slot `3` at (10, y, 3)

#### Scenario: Centre ignores the offset

- **WHEN** `sides` is `CENTER` and `offset` is `3`
- **THEN** every copy stands on the line

### Requirement: Copies sit on the terrain

Each copy's position Y SHALL be the height of the highest terrain surface at its X and Z. A copy over no terrain SHALL
take the height of the line at its station, interpolated between the points' heights.

#### Scenario: On the fixture terrain

- **WHEN** a copy of an arrangement in `Main Scene` stands at X `20`, Z `0`, over the terrain of entity `1`
- **THEN** its position Y equals the height of that terrain at X `20`, Z `0`

#### Scenario: Off the terrain

- **WHEN** a station lies outside every terrain and the line there runs between points at Y `2` and Y `4`, halfway
- **THEN** the copy's position Y is `3`

### Requirement: Copies turn with the line

With `alignToPath` `true` a copy SHALL be turned about the vertical axis so that its local +X points along the line at
its station, then by `yawOffset` degrees. Right-side copies under `BOTH` SHALL be turned half a turn more, so the two
rows face each other. With `alignToPath` `false` a copy SHALL be turned by `yawOffset` alone.

#### Scenario: A line along +Z

- **WHEN** a line runs along +Z, `alignToPath` is `true` and `yawOffset` is `0`
- **THEN** each copy is turned a quarter turn about Y so that its local +X points along +Z

#### Scenario: Fixed turn

- **WHEN** `alignToPath` is `false` and `yawOffset` is `90`
- **THEN** every copy has the same rotation, a quarter turn about Y, wherever the line bends

#### Scenario: Facing rows

- **WHEN** a line runs along +X with `sides` `BOTH`
- **THEN** left copies have no rotation and right copies are turned half a turn about Y

### Requirement: Selecting an arrangement shows its path

Selecting an arrangement's row in the tree, or clicking its line in the Scene view, SHALL select it and show the line
with a handle on each point and a handle in the middle of each segment. Selecting anything else SHALL hide them.
Clicking a copy SHALL select that copy, not the arrangement.

#### Scenario: Select from the tree

- **WHEN** the user selects the `Path 11` row
- **THEN** the Scene view shows its line, two point handles and one middle handle

#### Scenario: Click a copy

- **WHEN** the user clicks copy `13` in the Scene view
- **THEN** entity `13` is selected and the path handles are hidden

### Requirement: Editing the path regenerates the copies

Dragging a point handle SHALL move that point over the terrain under the cursor, and dragging a middle handle SHALL
insert a new point there. Remove Point on a point handle's right-click menu SHALL remove it when at least two points
would remain. While dragging, the markers SHALL follow; releasing SHALL write the new points and regenerate the copies.

#### Scenario: Drag the end point

- **WHEN** the user drags the second point of `Path 11` from (40, h, 0) to (60, h, 0) and releases
- **THEN** the arrangement's points are updated, copies `12` to `16` keep their ids, two copies are added at X `50` and `60`, and `slots` lists seven ids

#### Scenario: Shorten the line

- **WHEN** the user drags the second point of `Path 11` back to (20, h, 0)
- **THEN** the copies in the last two slots are removed from the file and `slots` lists three ids

#### Scenario: Esc during a point drag

- **WHEN** the user drags a point handle, presses Esc, then releases
- **THEN** the point and the copies are back where they were and the file is unchanged

#### Scenario: Cannot remove below two points

- **WHEN** the arrangement has two points and the user right-clicks a point handle
- **THEN** Remove Point is disabled

### Requirement: Regeneration keeps copies in their slots

Regeneration SHALL update the position, rotation and render asset of the copy in each slot that still has one and keep
its id, name and other components. It SHALL add a copy for each new slot past the end of `slots`, and remove the copy of
each slot that no longer exists. Changing `asset` SHALL change the render asset of every copy.

#### Scenario: Change the model

- **WHEN** the user sets `asset` of `Path 11` to `model_900f6f61-6384-434a-be81-56ce303fbb56`
- **THEN** copies `12` to `16` keep their ids and names and their render components name that model

#### Scenario: Copy components are kept

- **WHEN** copy `12` carries a game component and the path is edited
- **THEN** copy `12` still carries that component unchanged

### Requirement: Hand edits detach a copy

Moving or turning a copy with the gizmo, dropping it, or changing its `PositionComponent` in the Properties panel SHALL
detach it in the same edit: its `ParentComponent` is removed and its slot in `slots` becomes `-1`. A detached copy is a
normal entity that regeneration leaves alone. Other edits to a copy SHALL NOT detach it.

#### Scenario: Move a streetlight off a driveway

- **WHEN** the user moves copy `14` (slot `2` of `Path 11`) along Z and releases
- **THEN** copy `14` has its new position and no `ParentComponent`, and `slots` of `Path 11` is `[12, 13, -1, 15, 16]`

#### Scenario: A rename does not detach

- **WHEN** the user renames copy `13`
- **THEN** copy `13` keeps its `ParentComponent` and its slot

#### Scenario: Detached copies stay put

- **WHEN** copy `14` was detached and the user then drags the second point of `Path 11`
- **THEN** copy `14` is unchanged and slot `2` stays `-1`

### Requirement: Empty slots

A slot whose entry is `-1`, whose entity is missing from the file, or whose entity no longer has a `ParentComponent`
pointing at the arrangement SHALL be empty: regeneration SHALL NOT add a copy there and SHALL write `-1` for it. All empty
slots SHALL be filled again when `spacing`, `sides` or `closed` changes or the first point moves or is removed.

#### Scenario: A copy deleted in the text editor

- **WHEN** entity `15` is deleted from the text of `Main Scene.scene` and the path of `Path 11` is then edited
- **THEN** no copy is added for slot `3` and `slots` holds `-1` there

#### Scenario: Spacing change refills

- **WHEN** slot `2` of `Path 11` is empty and the user changes `spacing` to `5`
- **THEN** every slot of the new layout holds a copy, and detached copy `14` is unchanged

#### Scenario: Moving a later point keeps empty slots

- **WHEN** slot `0` is empty and the user drags the last point of `Path 11`
- **THEN** slot `0` stays empty

### Requirement: Regenerate on demand

An arrangement's tree row and the Scene view toolbar, while the arrangement is selected, SHALL offer Regenerate. It
SHALL re-place every copy in its slot from the current terrain and settings without filling empty slots, and write
nothing when no copy would change.

#### Scenario: After the terrain changed

- **WHEN** the terrain under `Path 11` was regenerated and the user chooses Regenerate
- **THEN** each copy's position Y matches the new terrain height and empty slots stay empty

#### Scenario: Nothing changed

- **WHEN** the user chooses Regenerate on an arrangement whose copies already match
- **THEN** the scene file is not modified

### Requirement: Arrangement edits are one undoable edit

Writing a new arrangement, a path edit, a settings change with its regeneration, a detach, and Regenerate SHALL each be
one undoable command in the scene file's history, and Undo SHALL restore the file text from before it.

#### Scenario: Undo a new arrangement

- **WHEN** the user draws a new arrangement and chooses Undo
- **THEN** the scene file text equals what it was before, with neither the arrangement nor its copies

#### Scenario: Undo a path edit

- **WHEN** the user drags a point, which adds two copies, and chooses Undo
- **THEN** the old points are back and the two added copies are gone

### Requirement: The written scene stays loadable

A scene with arrangements SHALL load in the editor and in a game without new warnings. The arrangement SHALL load as
data, and its copies SHALL load and draw as ordinary model entities.

#### Scenario: Load in the Scene view

- **WHEN** `Main Scene` with `Path 11` is opened in the Scene view
- **THEN** copies `12` to `16` are drawn as `tree` models and the log has no warning about `PathArrangementComponent`

#### Scenario: Load in a game

- **WHEN** a game built on the runtime loads a scene with an arrangement
- **THEN** the arrangement entity carries its `PathArrangementComponent` and the copies are model entities with positions
