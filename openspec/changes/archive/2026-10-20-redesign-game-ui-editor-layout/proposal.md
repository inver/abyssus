# Proposal

## Why

The Game UI editor tab lays out screens, widget tree, palette, canvas and a three-tab side panel in a fixed arrangement
that needs a wide editor. On a 14" MacBook (1512 x 982 scaled) with the Abyssus project tree open, the editor area is
about 1190 px wide; the left column (screens, tree and palette stacked) and the 350 px side panel squeeze the canvas and
leave the widget tree only a few rows. See the mockup: https://claude.ai/artifact/9mBTh6DatEKs5QqM4Lfkns

## What Changes

- The screens list becomes a toolbar drop-down (entry and overlay marks, Add Screen, Add Overlay, Rename, Delete, and
  the arrow-focus switch) instead of a permanent list.
- The widget palette becomes an **Add** popup on the Widgets header, titled with the container it adds into; Duplicate
  and Delete become icon buttons beside it. Up / Down and the list empty-state actions stay reachable from the same header.
- The widget tree takes the whole left column; side columns narrow (about 216 px widgets, about 292 px properties).
- The canvas toolbar gains the preview resolution picker and a fit-zoom indicator; the key hints move to a slim strip
  under the canvas.
- Edit / Preview stays a toggle; controls use IntelliJ (JB) components and the IDE theme, no custom painting.
- No change to the document model, edits, `editUiMeta`, stale-edit refusal, threading or Preview behavior.

## Capabilities

### New Capabilities
- `game-ui-editor-layout`: how the Game UI editor tab arranges its parts and keeps every action reachable at the
  reference editor width.

### Modified Capabilities
<!-- None in openspec/specs/. `game-ui-authoring` is still a delta of the open change add-game-ui-authoring;
     a task reconciles it with this change. -->

## Impact

- Code: `projects/plugin-abyssus/.../plugin/gameui/` (`UiEditorPanel`, `UiEditorViews`, `UiCanvasView` sizing only).
- Strings in `AbyssusBundle.properties`; `gameui/README.md`.
- Tests: `UiEditorViewsTest` and any test that names the removed screen list or palette grid.
- Depends on: `add-game-ui-authoring` (the editor being re-laid-out).
