# Proposal

## Why

Users need a quick way to see scene viewport performance while editing. An optional FPS counter makes the current frame rate visible without keeping diagnostics on screen for every project.

## What Changes

- The project properties shown for a native `.abss` project row in the Abyssus view (which already has the Physics checkbox) gain a `Show FPS` checkbox.
- Default the checkbox to off and remember it per IntelliJ project across IDE sessions; it applies to all scene views in that IDE project, including views opened later.
- When enabled, show the frame rate at the start of the stats strip in the lower-left corner of each scene viewport (the strip of `redesign-scene-view-toolbar`). Each view measures its own completed viewport frames over approximately one second, including time spent rendering and presenting.
- Apply enable/disable immediately without reopening views; reset measurement after a hidden view resumes or the counter is enabled.
- Read no additional document fields and write none: `.abss`, `.scene` and `meta.json` remain unchanged. The Abyssus format does not change.
- Out of scope: GPU timing, ray-backend throughput, frame-time graphs, profiling, FPS limits, benchmark logging, scene-specific toggles, and a new toolbar toggle.

## Capabilities

### New Capabilities

- `scene-fps-display`: Optional viewport FPS display, measurement semantics, project preference scope and lifecycle.

### Modified Capabilities

- `object-properties-panel`: the project properties gain a `Show FPS` view setting (an added requirement; the row routing already exists).

## Impact

- Plugin properties selection/state and a project settings view, project-scoped preference storage, and the scene editor/view/GL overlay lifecycle.
- Headless sampling tests, platform tests for preferences and panel routing, and GL/manual checks for overlay appearance and interaction.
- Reuse project-scoped IDE preference storage as already used by `UnusedFilter`; introduce no dependencies or changes to `core`, `gdx-model` or ray backend APIs.
- Update user documentation and scene-view package notes during implementation.
