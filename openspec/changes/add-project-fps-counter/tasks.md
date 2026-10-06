# Tasks

## 1. Sampling and project preference

- [ ] 1.1 Implement plain `FpsCounter` with explicit monotonic timestamps, frame-start/completion sampling, one-second windows, integer rounding and reset behavior. Verify `FpsCounterTest` cases for 60 frames/second, 10 frames over a stalled two-second window, fractional rounding, initial placeholder, independent counters, invalid timestamps and no completed frames with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.FpsCounterTest'`.
- [ ] 1.2 Add project-scoped `SceneViewSettings` backed by `PropertiesComponent`, default off, unchanged-value suppression and disposable EDT listeners. Verify `SceneViewSettingsTest` cases for default, stored-value reload, separate project storage and listener disposal with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewSettingsTest'`.

## 2. Project properties control

- [ ] 2.1 Add project-row routing, `PanelState.ProjectDetails`, `ProjectDetailsView`, localized `Show FPS` and settings explanation; use the existing view disposable and clear obsolete asset editor state. Update `AssetPropertiesPanelTest`'s project empty-state expectation and add `ProjectFpsSettingsTest` cases for initial state, toggling with no scene open, reselecting, selection switching and listener disposal. Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.properties.ProjectFpsSettingsTest' --tests 'net.nevinsky.abyssus.properties.AssetPropertiesPanelTest' --tests 'net.nevinsky.abyssus.properties.SceneRaySwitchTest'`.
- [ ] 2.2 Verify project controls do not change game data: snapshot `.abss`, scene and representative `meta.json` document text and disk bytes before both toggle directions in `ProjectFpsSettingsTest`; run `./gradlew :test --tests 'net.nevinsky.abyssus.properties.ProjectFpsSettingsTest'`.
- [ ] 2.3 Reconcile `add-realistic-water`'s `Panel follows the asset selection` delta and affected design/tasks so it preserves project view settings, available scene controls and future water controls. Verify by reviewing the full resulting requirement/scenarios and running `openspec validate add-realistic-water --strict` and `openspec validate add-project-fps-counter --strict`.
- [ ] 2.4 Document selecting the `.abss` project row and the remembered IDE-project scope in README's user-facing feature description and CHANGELOG's Unreleased section, preserving the plugin-description markers. Verify the rendered text describes default off and no game-file changes, and run `scripts/check-docs.sh`.

## 3. Editor binding and viewport overlay

- [ ] 3.1 Extend `SceneView` with the settings operation and bind `SceneFileEditor` to the saved preference and disposable changes, applying it to later/replaced views. Add fake-view cases in `SceneFileEditorTest` for initial saved state, updates to multiple editors, replacement and disposed editors. Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneFileEditorTest'`.
- [ ] 3.2 Integrate sampling into `SceneViewPanel` before rendering and after successful swap, resetting on disable, unsafe surface, hide, failure, context replacement and disposal. Keep sampling independent of ray-image production. Add `SceneViewPanelFpsTest` using a GL-free lifecycle/presentation seam and extend `FpsCounterTest` to cover a ten-second pause and re-enable; verify with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewPanelFpsTest' --tests 'net.nevinsky.abyssus.plugin.sceneview.FpsCounterTest'`.
- [ ] 3.3 Implement localized `FpsOverlay` using a core-profile-compatible text batch/shader, contrast backing and framebuffer-scaled placement. Draw after content/loading feedback; create/dispose only with a safe current context and drop references on abandonment. Verify `FpsOverlayGlTest` cases for visible placeholder/numeric glyphs, upper-right placement at two framebuffer sizes/scales, loading composition, off state and context re-creation with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.FpsOverlayGlTest' -Dabyssus.glTests=true`.
- [ ] 3.4 Update `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md` for preference delivery, completed-viewport-frame semantics and overlay lifecycle; update relevant architecture/testing notes if the new seams make them incomplete. Verify paths with `scripts/check-docs.sh` and compare the notes with the implemented threading and ownership.

## 4. Integration verification

- [ ] 4.1 Perform runIde checks 1–5 below using a temporary copy of the fixture; record results and leave this task unchecked if any required manual check cannot be performed.
- [ ] 4.2 Run `openspec validate add-project-fps-counter --strict`, `./gradlew check` and `scripts/check-docs.sh`; record any unrelated failures without changing their scope.

### Numbered runIde checks for task 4.1

1. Copy `src/test/testData/project/Untitled` to a temporary directory and launch `./gradlew runIde -PideProject=<copy>`. Select `Untitled.abss`; verify `Show FPS` starts unchecked, can be checked with no scene open, and opening `Main Scene` shows a placeholder followed by a numeric value.
2. Uncheck/recheck with the view open; verify the overlay disappears/reappears without reopening and the checkbox retains its state after selecting an asset or scene. Reopen the IDE project and verify persistence. Open a second IDE project and verify its default remains off.
3. Check a second scene view in the same project receives the preference and has its own measurement. Orbit, select `Model 0`, drag its gizmo and Undo. Resize and test available display scaling/light and dark scene backgrounds; verify readable upper-right placement and readable text over loading feedback.
4. Hide/show the view, minimize/restore the window, reduce/restore its size and close/reopen the tab. Verify safe rendering and fresh placeholders rather than stale FPS or samples incorporating inactive time. Confirm no counter-driven activity persists after closing views.
5. On a supported machine, enable Ray Tracing from the existing scene properties control; verify the same counter continues to display viewport FPS and fallback/off keeps it working. Compare game-file bytes before/after toggling alone (separate from intentional gizmo edits); verify no FPS settings appear in Mundus files.
