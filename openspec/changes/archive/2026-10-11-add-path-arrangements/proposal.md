# Proposal

## Why

Putting many copies of one model along a road, fence line or field edge (streetlights, poles, trees) means adding and
moving each copy by hand, and redoing all of them when the road changes. Abyssus needs a way to describe the line once
and have the copies follow it, sitting on the terrain, whenever the line or its settings change.

## What Changes

- New **path arrangement**: a scene entity of type `GROUP` that holds a polyline drawn on the terrain (open or closed)
  and the settings that place one project model along it: spacing, side offset, which sides of the line, whether
  copies turn to follow the line, and an extra turn.
- New Scene view action **Add Path Arrangement** (toolbar and the scene row of the Abyssus tree), which asks for a
  model and then lets the user click points on the terrain, with a live preview of the copies, Enter to finish and Esc
  to cancel.
- The arrangement **generates its copies as ordinary entities** in the scene file. Each copy sits on the terrain below
  it. The arrangement records which entity fills each slot along the line.
- **Editing regenerates**: dragging, inserting or removing a path point in the Scene view, or changing a setting in
  the Properties panel, rewrites the copies in the same undoable edit. Existing copies keep their ids, missing ones
  are added and extra ones are removed. **Regenerate** re-places the copies on demand, for example after the terrain
  changed.
- **Hand edits detach**: moving or turning a copy with the gizmo, dropping it, or editing its position in the
  Properties panel makes it a normal entity. Its slot is then left empty on later regenerations. A copy deleted from
  the file leaves its slot empty in the same way. Empty slots are filled again when the spacing, the first point or
  the direction of the line changes.
- Native fields written: a new built-in component `PathArrangementComponent` on the arrangement entity, with
  `points`, `closed`, `asset` (`type` `MODEL`, `assetName`), `spacing`, `offset`, `sides`, `alignToPath`, `yawOffset`
  and `slots`. Each copy gets the existing `NameComponent`, `TypeComponent` (`OBJECT`), `PositionComponent`,
  `RenderComponent` (`kind` `asset`) and `ParentComponent` pointing at the arrangement. The scene keeps
  `format: "abyssus"`, `formatVersion: 1`. The document is validated with the existing native-format check before any
  read or edit, and refused documents stay unchanged. No format version change and no migration: the component is new
  and optional, and a runtime that predates it carries it raw with one warning.

Out of scope:
- Freehand drawing, curves (splines, Bezier), and ready-made shapes such as a circle or a rectangle tool. A closed
  polyline covers loops.
- More than one model per arrangement, random choice, random yaw or position jitter.
- Automatic re-placement when a terrain is regenerated or moved. The user runs Regenerate.
- Following the slope (tilting to the terrain normal), spacing measured along the slope, and resting on objects other
  than terrain.
- Arranging lights, cameras or terrains. Only models are arranged.
- A transform hierarchy: copies are stored in world coordinates, and moving the arrangement entity itself does not
  move them.
- Runtime behavior for games beyond loading the component as data.
- An entity delete action. Deleting a copy is done in the text editor; the arrangement copes with it.

## Capabilities

### New Capabilities

- `scene-path-arrangements`: creating a path arrangement in a scene, how its copies are placed along the line and on
  the terrain, editing its path and settings with regeneration, detaching and empty slots, and what it writes to the
  scene file.

### Modified Capabilities

- `scene-object-transform`: a completed drag or drop on an arranged copy also detaches it from its arrangement in the
  same edit ("Writing a completed drag", "Writing a completed drop").
- `scene-component-editing`: updating a field of an arrangement regenerates its copies in the same edit, and updating
  an arranged copy's `PositionComponent` detaches it ("Update a component field").

## Impact

- `projects/lib-runtime`: new data-only `PathArrangementComponent`, added to the built-in components (`BUILT_IN_COMPONENTS`,
  `EcsJson`), loaded and written like the others. No system.
- `projects/lib-core-editor`: path sampling and slot layout, terrain height lookup for slots, the regeneration diff
  over the scene JSON, detaching inside the transform and component-field edits (so `HeadlessEditing` writes the same
  text), the arrangement's component kind for the Properties panel, path handle hit tests and the drawing gesture
  state.
- `projects/plugin-abyssus`: Add Path Arrangement action (toolbar and tree), drawing mode and preview drawing in the
  Scene view, path-point handles, Regenerate action, messages in `AbyssusBundle.properties` and
  `AbyssusEditorBundle.properties`.
- Docs: `docs/ai/file-formats.md` (new component), `docs/ai/glossary.md` (arrangement, slot, detach), the scene view
  package README.
