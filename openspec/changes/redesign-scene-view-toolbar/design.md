# Design

## Context

The Scene View tab is `SceneFileEditor` hosting `SceneViewPanel`: a north `FlowLayout` toolbar of text buttons and combos
over a `GuardedGLCanvas` wrapped by `CanvasOverlayHost`. Gizmo mode lives in `SceneInteraction` (`GizmoMode { MOVE, ROTATE }`),
gizmo geometry and drag math in `lib-core-editor` (`GizmoHandles`, `GizmoDrag`, `GizmoHit`), and the view's state in
`SceneViewState`. The renderer draws the grid, fog, shadows and lights unconditionally from `SceneRenderParams`.
Icons are 36 SVGs in `plugin-abyssus/src/main/resources/icons/`. UI screenshot tests exist (`add-plugin-ui-tests`) but exclude
the GL viewport. See proposal.md for motivation and scope; the mockup is the "Scene viewer" board of the linked artifact.

## Goals / Non-Goals

**Goals:**
- One toolbar that matches the mockup's grouping and icons, built so a Remote Robot screenshot of it is deterministic.
- All new behavior (snap, local space, framing, orientation widget projection, stats text, toggle state) is pure logic
  in `lib-core-editor` or a Swing-free class in `sceneview`, with the Swing/GL parts thin.
- No document change: every new setting is view state.

**Non-Goals:**
- Persisting view settings, a wireframe mode, restyling the Properties panel, notifications.
- Pixel-comparing GL output.

## Decisions

### D1. Toolbar from the platform action toolbar, not hand-placed buttons
Build the toolbar as an `ActionToolbar` over `DefaultActionGroup`s (`ToggleAction`s for pressed controls, separators between groups),
as `overlayToolbar()` already does. This gives icon buttons, pressed backgrounds, tooltips with shortcuts, theming and
wrapping (`setLayoutStrategy(WRAP_IMMEDIATELY)`) from the platform. The text drop-downs (space, shading, camera, clouds) are
`ComboBoxAction`s so they show text and a chevron. *Alternative:* keep `JToggleButton`s and style them; rejected because it
repeats what the platform already themes and conflicts with the "use platform UI components" rule.
Actions keep no state of their own: they read and write a `SceneViewToolsModel` held by the panel and passed to them through
`DataContext` (a `DataKey`), so no `AnAction` field leaks. `getActionUpdateThread` is `EDT` (Swing state only).

### D2. A pure `SceneViewToolsModel`
One Swing-free class (`sceneview`, no GL) owns: tool (`SELECT | MOVE | ROTATE`), space (`WORLD | LOCAL`), snap on/step,
shading (`LIT | UNLIT`), layer toggles (shadows, fog, grid), and the derived `ToolbarState` (existing `toolbarState` grows
fields; it stays the single enabled-state function). It notifies one listener, `syncControls`. `GizmoMode` gains `SELECT`, and
`SceneInteraction.mode` stays the source of truth for the tool so keys and clicks agree; the model exposes it.

### D3. Snap and local space are drag-math parameters
`lib-core-editor` gets `SnapRound` (step rounding for translation along an axis, 15 degree rounding for angles) and
`GizmoSpace` (axes from an object rotation; identity for World). `GizmoHandles` takes the axes, `GizmoDrag` applies
`SnapRound` to its result before the preview and before the existing single `editSceneJson` write, so Escape and the
single-undo behavior are unchanged. Rotation in Local uses the object's own axis; the written value is converted to the
same `localRotation` fields the World path writes. Unit tests cover rounding, local axes at 0 and 90 degrees, and that
unchanged numbers keep their text.

### D4. Layer toggles and shading are render parameters
`SceneRenderer` already draws grid, shadows and fog from separate code paths. Add three booleans and a shading enum to a small
`ViewDisplay` value passed with `render(...)`: grid skips the grid batch, fog skips the fog environment, shadows skip the
shadow pass, Unlit uses an unlit environment (full ambient, no lights, no shadow pass). They never read or write
`SceneRenderParams.fogEnabled`. GL calls stay inside `GdxRuntime.withContext` on the AWT render thread; setting the value is a
plain EDT field write read on the next frame.

### D5. Frame selection and the orientation widget are CPU-only
`FrameSelection` (lib-core-editor, pure): from a bounding box and the camera's field of view and direction, returns the orbit
target and distance. It uses the picking bounds the view already keeps for the selection or all drawn boxes. The orientation
widget is a Swing component over the canvas drawn from the camera's view rotation (`OrientationProjection`, pure: three unit
axes to 2D points and depth order, and a hit test that returns the axis under a point). A click calls `OrbitCamera.lookAlong(axis)`.
It is a lightweight `JComponent` in the `CanvasOverlayHost` layer, so the existing IDE-overlay snapshot logic keeps working and
a screenshot of it is a component image. Picking ignores clicks outside its circle (`contains` is circular).

### D6. Stats strip is a Swing label strip
`StatsStrip` is a small non-opaque `JComponent` (not mouse-opaque) rendered above the canvas, text from `StatsText` (pure:
object count, tool, space, optional `<n> fps`). The frame rate comes from the sampling class of `add-project-fps-counter`;
this change moves that change's overlay from the upper-right corner into this strip (see the task that amends its delta).
The object count is `FrameSnapshot`'s drawn model and terrain count read after the frame, updated only when it changes.

### D7. Preview / source switch via the platform's split editor
Use `TextEditorWithPreview` semantics (the platform's layout switcher) rather than a hand-built toggle: the existing
`SceneFileEditor` becomes the preview and the platform text editor the source, in one `FileEditor` tab. They share the
`Document`, so Undo and unsaved text are shared and switching writes nothing. The switch icon strip is the platform's
(top-right of the editor), styled with the new icons where the API allows. *Alternative:* a hand-built `CardLayout` toggle
inside our panel; rejected because it would duplicate document wiring and break "plain text editor" behavior that
`SceneFormatListener` and Undo tests rely on. If the provider change breaks `SceneFileEditorProvider` tests that assume a
single editor, those tests are updated in the same task.

### D8. Icons
Convert the mockup's glyph paths into 16 px SVGs: new `select`, `move`, `rotate`, `frame`, `snap`, `shading`, `shadows`, `fog`
(existing `fog_icon.svg` reviewed against the mockup), `grid`, `camera`, `space` and `source`/`preview` glyphs, each with a
`_dark` file through the platform's `IconLoader` dark-suffix convention. Paths are copied from the artifact's SVG source
(24 px grid, 1.8 stroke), so they match the Icon set board. An `IconInventory` list in test code names every required icon;
a headless test fails on a missing or orphaned resource.

### D9. Screenshots
Swing components are captured as images with the existing `ScreenshotComparator` harness in the canonical Linux container.
The scene toolbar and tab switch are captured through a Remote Robot fixture that opens a scene fixture; the orientation
widget and stats strip are captured with the canvas replaced by a fixed color (a test-only `viewFactory` panel that builds the same
`SceneViewPanel` chrome around a stub canvas), so the capture never contains GL. A baseline per scenario is committed under
the existing screenshots folder and listed in `SCREENSHOT_SCENARIOS`. The icon inventory is a headless test and is part of `check`.

## Risks / Trade-offs

- [`TextEditorWithPreview` changes the editor's structure and shortcuts] → Spike in the first task group; if a test or
  `SceneFormatListener` depends on the old single editor, fall back to a `CardLayout` toggle in the panel and record why.
- [Action toolbar visuals differ by IDE theme and platform and make baselines brittle] → Baselines are only compared in the canonical
  container (as for the existing 15); other hosts run `-PuiScreenshots=off`.
- [Local rotate math can drift from World writes] → One conversion function with property-style tests against World results
  for unrotated and 90 degree objects.
- [Unlit changes can break the shadow atlas and cloud shadow paths] → Unlit skips the pass entirely and the toggle restores
  state; a GL test (`-Dabyssus.glTests=true`) renders once in each mode, and the runIde checklist covers it by eye.
- [Two open changes touch the FPS overlay] → An amend task edits `add-project-fps-counter`'s delta and tasks in this change's group 6.
- [Scope is large] → Groups are ordered so each lands working, tested chrome before the next: toolbar shell, tools, display, tab,
  screenshots. The toolbar shell ships alone if later groups slip.

## Migration Plan

No data migration: no file field is added. Rollback is reverting the change. The old text buttons are removed in the
toolbar-shell task, not kept behind a flag.
