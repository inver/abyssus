# Proposal

## Why

The Scene View toolbar is a `FlowLayout` of text buttons and combo boxes (Move, Rotate, Drop, Add Light, ..., camera,
Clouds, Play) with no icons, no grouping, and no view controls. The viewport has no orientation cue, and the editor tab
has no way to flip between the render and the scene's JSON. A design mockup now exists for the whole screen:
https://claude.ai/artifact/9mBTh6DatEKs5QqM4Lfkns (board "Scene viewer · IntelliJ components"). It shows an icon toolbar
with a segmented Select / Move / Rotate group, Local/World and Snap, a shading choice, Shadows / Fog / Grid toggles,
Frame selection, an orientation widget and a stats strip. The same artifact's Icon set defines the glyphs the Abyssus
tree and Properties already use. This change brings the Scene View to that design and adds the tools it implies.

## What Changes

- The scene toolbar is rebuilt from IntelliJ components in the mockup's grouping: a segmented **Select / Move / Rotate**
  tool group, then **Local / World** and **Snap**, then **shading (Lit)** with **Shadows**, **Fog** and **Grid**
  toggles, then the camera chooser and **Frame selection**. Controls are icon buttons with tooltips (name and key) except
  the drop-downs, which show text and a chevron. Icons are the artifact's 24 px stroke glyphs, added as SVG resources.
- Every control that exists today stays reachable: Drop, Add Light / Asset / Path Arrangement / Foliage, Regenerate,
  foliage paint, Clouds, Play / Pause / Step / Stop and the overlay actions, grouped after the mockup's controls.
- New **Select** tool: click selects and drags orbit or pan, with no gizmo handles. Key `Q`.
- New **Local / World** gizmo space and **Snap** (step 0.5 by default): move drags round to the step, rotate drags round
  to 15 degrees. Both are per-view state.
- New **shading** choice (Lit / Unlit), and **Shadows / Fog / Grid** view toggles that hide those layers in the view only.
- New **Frame selection** (`F`): the orbit camera frames the selected object, or the whole scene when nothing is selected.
- New **orientation widget** in the viewport's upper-right corner (X, Y, Z axes that follow the camera). Clicking an axis
  snaps the orbit camera to look along it.
- New **stats strip** in the lower-left corner: object count and the current tool and space. It also shows the frame rate
  when `Show FPS` of `add-project-fps-counter` is on, which moves that counter from the upper-right corner into this strip.
- The editor tab gets a **Preview / source** switch (the render or the scene's JSON in the text editor), as in the mockup.
- UI screenshot tests (Remote Robot, the existing canonical Linux container) compare the toolbar, the tab switch, and the
  stats strip and orientation widget as component images, plus an icon inventory test that fails on a missing or orphaned icon.
- Documentation: `sceneview/README.md`, `docs/ai/testing.md` and the key list in the scene specs.

No native document field is read or written by the new view state: Local/World, Snap, shading, the toggles and the camera
framing live in the view only and are not saved in `.scene`, `.abss` or `meta.json`. The existing gizmo writes still go
through `editSceneJson` unchanged, so no versioned format change and no new document validation is needed beyond the
existing `AbyssusDocumentFormat` check the view already performs.

## Capabilities

### New Capabilities

- `scene-view-toolbar`: layout, grouping, icons, tooltips and enabled state of the scene toolbar; the tab's Preview / source
  switch; every existing action stays reachable.
- `scene-view-tools`: Select tool, Local/World gizmo space and Snap, and their keys.
- `scene-view-display`: shading choice, Shadows / Fog / Grid view toggles, Frame selection, the orientation widget and the
  stats strip.

### Modified Capabilities

- `scene-object-transform`: "Move and rotate modes" gains a Select mode and the `Q` key; gizmo drags honor Snap and Local/World.

## Impact

- Code: `projects/plugin-abyssus/.../plugin/sceneview/` (`SceneViewPanel` toolbar, `SceneRenderer` layer toggles and shading,
  overlay components), `projects/plugin-abyssus/.../SceneFileEditorProvider` for the tab switch, new SVGs in
  `projects/plugin-abyssus/src/main/resources/icons/`, strings in `AbyssusBundle.properties`.
- Pure, testable logic in `lib-core-editor`: snap rounding, local/world axes, framing distance, orientation-widget projection.
- Tests: toolbar state tests, `lib-core-editor` math tests, new Remote Robot scenarios and baselines under
  `projects/plugin-abyssus/src/uiTest/`.
- Depends on: `add-plugin-ui-tests` (screenshot harness, task 4.1 CI job). Conflicts with `add-project-fps-counter`: its
  overlay position is amended by a task here.
- No new dependencies.

## Out of scope

- Anything the mockup's other boards show: the Screen Editor, the Picker, the Abyssus tree and Properties re-layout, the
  Logic and ECS boards. The Properties panel beside the scene in the mockup is not restyled here.
- The mockup's notification balloon (the IDE's notifications already cover it) and its Project / Properties tool window strip.
- Persisting any view setting across sessions or in the scene file; a wireframe or other shading modes beyond Lit / Unlit.
- Pixel comparison of the GL viewport. Screenshots cover Swing components only (GL under Xvfb is not deterministic).
