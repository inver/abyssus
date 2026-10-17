# Proposal

## Why

Scene view lines and silhouettes need multisample antialiasing, with a choice that can be saved and shared with a
native Abyssus project. The user revised the original IDE-wide preference to a project document setting.

## What Changes

- Scene views use 4× multisampling by default, with Off, 2×, 4× and 8× choices.
- **Scene view antialiasing** appears in Abyssus Properties when a native project is selected. Remove the IDE Settings page.
- Save the choice in that project's `.abss` as `sceneViewAntialiasing` (`OFF`, `X2`, `X4`, `X8`). Omit the default `X4`.
- Validate native markers before reading or writing. Use `editSceneJson` for an isolated undoable edit, preserving unknown
  data, formatting, key order and unrelated number text. Scene and asset files remain unchanged.
- Every open scene view belonging to that native project follows its setting, including unsaved text edits and Undo/Redo.
  Other native projects are independent, even within one IDE project. Standalone scenes use 4×.
- Keep camera, selection, look-through camera, tools, Play and Ray Tracing mode during changes. Unsupported samples
  fall back through lower choices to Off without rewriting the requested value.
- Old application preferences are ignored; no implicit migration or eager document writes.
- Out of scope: post-process antialiasing, per-scene preferences, a toolbar toggle, changing the ray image's own
  accumulation, game/play-host rendering, and changing GL test harness pixel formats.

## Capabilities

### New Capabilities

- `scene-view-antialiasing`: Default multisampling, a native project setting, live view updates and GPU fallback.

### Modified Capabilities

None. Existing picking, rendering, Play and ray requirements remain unchanged.

## Impact

- Plugin project settings reader/service, project Properties view, scene editor bindings and canvas lifecycle.
- Remove `AntialiasingConfigurable`, `SceneViewAntialiasing` and the `applicationConfigurable` registration.
- No game or runtime module dependency changes; the runtime ignores the editor preference.
- Update README, CHANGELOG, file-format and architecture docs, and package notes.
