# Tasks

## 1. Icons

- [ ] 1.1 Copy the mockup's glyph paths (Scene viewer and Icon set boards of the linked artifact) into 16 px SVGs for select, move, rotate, frame, snap, space, shading, shadows, grid, camera, preview and source, plus `_dark` variants; review `fog_icon.svg` against the mockup. Verify with `IconInventoryTest` (headless, in `projects/plugin-abyssus/src/test`): `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.sceneview.IconInventoryTest'` passes and fails with the file name when a listed icon is missing or an unlisted `sceneview` icon exists.
- [ ] 1.2 List the icons in `docs/ai/glossary.md`-adjacent notes in `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md` (icon source and naming). Verify with `scripts/check-docs.sh`.

## 2. Toolbar shell (headless-testable logic)

- [ ] 2.1 Add `SceneViewToolsModel` (tool, space, snap, shading, layer toggles, listener) and extend `toolbarState` with the new enabled fields, Swing- and GL-free. Verify with `SceneViewToolsModelTest` and the extended `SceneViewControlsTest`: editing controls disabled while playing or experimenting, display controls enabled, Shadows disabled with no shadow-casting light and when Unlit.
- [ ] 2.2 Add `GizmoMode.SELECT` and wire `Q`, W, E in `SceneInputForwarder`; Select draws no handles and a drag orbits or pans. Verify with `ScenePickerTest`/`SceneInteractionTest` cases in `lib-core-editor` (`./gradlew :lib-core-editor:test --tests 'net.nevinsky.abyssus.lib.core.editor.pick.SceneInteractionTest'`): click selects, drag in Select mode leaves the document untouched and moves the orbit camera.
- [ ] 2.3 Build the toolbar from `ActionToolbar` groups (D1) in `SceneViewPanel`: tool group, space/snap, display, camera and Frame, then the existing actions in their order; replace the text `JToggleButton`/`JButton` controls and keep the component names `add-light`, `add-asset`, `add-path-arrangement`, `regenerate-arrangement`, `add-foliage`, `path-drawing-hint`. Update `SceneViewPanelTest` and run `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewPanelTest'` green; strings go to `AbyssusBundle.properties`.
- [ ] 2.4 Add the tooltip text with key hints and the wrapping behavior at 900 px. Verify with a `SceneViewPanelTest` case that lays the panel out at 900 px and asserts no two toolbar components overlap.
- [ ] 2.5 Update `sceneview/README.md` Pieces table and `openspec/specs/scene-object-transform` references once archived. Verify with `scripts/check-docs.sh`.
- [ ] 2.6 runIde check, on a copy of `Untitled` (never the committed folder): open `Main Scene`; the toolbar shows the mockup's order; hover tooltips; Play disables the editing group; switch Dark and Light themes and see the icons recolor. Record the result in the PR.

## 3. Tools: Local/World and Snap

- [ ] 3.1 Add `SnapRound` and `GizmoSpace` in `lib-core-editor` and thread them through `GizmoHandles` and `GizmoDrag`. Verify with `SnapRoundTest` (0.5 step: 2.31 gives 2.5; 15 degree: 37 gives 30; off gives the raw value) and `GizmoSpaceTest` (axes at 0 and 90 degrees about Y).
- [ ] 3.2 Make the finished Local drag write the same position and rotation fields as World, keeping unrelated number text. Verify with a `SceneTransformWriterTest` case: a model rotated 90 degrees about Y dragged 2 units on its local X changes the position by 2 along world Z in exactly one edit and leaves all other numbers' text byte-identical; Escape during a snapped drag writes nothing (`SceneInteractionTest`).
- [ ] 3.3 Wire the Local/World drop-down and Snap toggle to the model and show `Move · Local · Snap 0.5` in the status bar (`StatsText`). Verify with `StatsTextTest` and `SceneFileEditorTest` that the status string follows the model.
- [ ] 3.4 runIde check: drag with Snap on and off, Local on a rotated model, Escape mid-drag, Undo after each; each is one undo step and the toggles are not saved.

## 4. Display: layers, shading, Frame, orientation widget, stats

- [ ] 4.1 Add `ViewDisplay` and pass it into `SceneRenderer.render`; implement Grid, Fog, Shadows toggles and Unlit (D4), leaving `SceneRenderParams.fogEnabled` untouched. Verify with a renderer test under `-Dabyssus.glTests=true` that draws once in each mode without a GL error (`./gradlew :plugin-abyssus:test -Dabyssus.glTests=true --tests '*SceneRendererTest'`), plus a headless assertion that no toggle edits the document.
- [ ] 4.2 Add `FrameSelection` and `OrbitCamera.lookAlong` and wire Frame selection and `F`. Verify with `FrameSelectionTest` (a unit box at the origin and at (10, 0, 0): target on the box, distance fits the field of view; no selection frames all boxes) and an `OrbitCameraTest` case in `lib-core-editor`; Frame does nothing while looking through a scene camera or while playing.
- [ ] 4.3 Add `OrientationProjection` (axes to 2D points, depth order, circular hit test) and the widget component over the canvas in `CanvasOverlayHost`'s layer; clicking an axis calls `lookAlong`. Verify with `OrientationProjectionTest` (camera rotated 90 degrees about Y turns the X and Z points; hit test inside and outside the circle) and a `SceneViewPanelTest` case that a click outside the circle reaches picking.
- [ ] 4.4 Add `StatsStrip` and `StatsText` (object count, tool, space, optional `<n> fps`), non-mouse-opaque and above the loading overlay. Verify with `StatsTextTest` (count for `Main Scene` of `Untitled`, fps shown only when enabled, `--` before the first sample if the sampler reports none) and a panel test that a click on the strip's area selects the object behind it.
- [ ] 4.5 Update the key list and toolbar description in `sceneview/README.md`. Verify with `scripts/check-docs.sh`.
- [ ] 4.6 runIde check: toggle each layer and Unlit; Frame with and without a selection; click each orientation axis; resize the editor; open a notification above the viewport and confirm the strip and widget stay put.

## 5. Preview / source switch

- [ ] 5.1 Spike D7: make the `.scene` editor a preview-plus-text editor sharing the document, with preview as the default layout. Verify with `SceneFileEditorProviderTest` that the provider opens one tab with both states and that switching leaves the file bytes unchanged; if the spike fails, record the fallback (a `CardLayout` toggle) in `design.md` and use it.
- [ ] 5.2 Keep `SceneFormatListener`, Undo and unsaved-text reload working across the switch. Verify with `SceneFileEditorTest` cases: edit in source then return to preview shows the edit; Undo reverts it; `SceneFormatListener` still pretty-prints a `.scene` opened in the text editor.
- [ ] 5.3 Update `docs/ai/architecture.md`'s editor description. Verify with `scripts/check-docs.sh`.
- [ ] 5.4 runIde check: switch both ways with unsaved edits and Undo; open a second scene tab.

## 6. FPS counter reconciliation

- [ ] 6.1 Amend `openspec/changes/add-project-fps-counter` (`specs/scene-fps-display/spec.md`, `design.md`, `tasks.md`): the counter shows `<n> fps` at the start of the stats strip in the lower-left corner instead of `FPS: <n>` in the upper-right corner, keeps `FPS: --`-style placeholder behavior as `-- fps`, and stays hidden when `Show FPS` is off. Verify with `openspec validate add-project-fps-counter --strict` and `openspec validate redesign-scene-view-toolbar --strict`.

## 7. Screenshot tests

- [ ] 7.1 Add the scene scenarios to `SCREENSHOT_SCENARIOS` (`scene-toolbar-default`, `scene-toolbar-playing`, `scene-toolbar-narrow`, `scene-toolbar-light-theme` if the harness supports it, `scene-tab-switch`, `scene-orientation-widget`, `scene-stats-strip`) and add `SceneViewUiTest` page objects in `src/uiTest`. Verify the interaction half in `scripts/run-ui-tests-docker.sh --tests '*SceneViewUiTest'` with `-PuiScreenshots=off`: tool buttons press and release, tooltips name their keys, the switch shows source and back, the toolbar has no overlapping bounds at 900 px.
- [ ] 7.2 Add the stub-canvas chrome fixture for the widget and strip captures (D9) so no capture contains GL. Verify the capture is identical across two consecutive runs (`ScreenshotReadinessUiTest`-style stability check) and contains no GL pixels.
- [ ] 7.3 Add `IconRenderTest` to the UI tests: render every toolbar icon at 16 px and compare to `icons/` baselines in the same canonical environment, so a changed glyph fails. Verify by altering one icon in a disposable copy and seeing the run fail with expected/actual/diff PNGs, then restoring it.
- [ ] 7.4 Generate and commit the Linux baselines in `projects/plugin-abyssus/src/uiTest/resources/screenshots/linux/` with `ABYSSUS_UI_PLATFORM=linux/amd64 scripts/run-ui-tests-docker.sh -- -PupdateUiScreenshots=true -PuiScreenshotScenarios=<the new names>`; inspect every image for correct icons, order, no clipping and no incidental IDE UI. Verify `BaselineInventoryTest` passes and a second comparison run passes without writes.
- [ ] 7.5 Update `docs/ai/testing.md` (new scenarios in the screenshot and scenario tables, the stub-canvas fixture, the icon inventory) and note the dependency on `add-plugin-ui-tests` task 4.1 for the CI job. Verify with `scripts/check-docs.sh`.

## 8. Integration

- [ ] 8.1 Run `./gradlew check`, `./gradlew :plugin-abyssus:verifyPlugin` if available, and `scripts/check-docs.sh` after all edits; run `scripts/run-ui-tests-docker.sh` once in comparison mode. Verify all pass, `git status` shows no committed fixture changes, and report unrelated failures without fixing them silently.
- [ ] 8.2 Run `openspec validate redesign-scene-view-toolbar --strict`. Verify it passes, then confirm no `.scene`, `.abss` or `meta.json` writer was added (only `editSceneJson` and `SceneFormatListener` write scenes) and no IntelliJ or Swing import entered `lib-core-editor` (`NoPlatformClasspathTest`, `checkNoSingletons`).
