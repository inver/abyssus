# Tasks

## 1. Sampling and project preference

- [x] 1.1 Implement plain `FpsCounter` with explicit monotonic timestamps, frame-start/completion sampling, one-second windows, integer rounding and reset behavior. Verify `FpsCounterTest` cases for 60 frames/second, 10 frames over a stalled two-second window, fractional rounding, initial placeholder, independent counters, invalid timestamps and no completed frames with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.FpsCounterTest'`.
- [x] 1.2 Add project-scoped `SceneViewSettings` backed by `PropertiesComponent`, default off, unchanged-value suppression and disposable EDT listeners. Verify `SceneViewSettingsTest` cases for default, stored-value reload, separate project storage and listener disposal with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewSettingsTest'`.

## 2. Project properties control

- [x] 2.1 Add the `Show FPS` checkbox (name `show-fps`) and its explanation to `ProjectDetailsView`, backed by `SceneViewSettings`, with a listener on the view's disposable. Add `ProjectFpsSettingsTest` cases for the initial state, toggling with no scene open, reselecting, an unsupported project still working, and listener disposal. Verify with `./gradlew :plugin-abyssus:test --tests '*ProjectFpsSettingsTest' --tests '*AssetPropertiesPanelTest'`.
- [x] 2.2 Verify project controls do not change game data: snapshot `.abss`, scene and representative `meta.json` document text and disk bytes before both toggle directions in `ProjectFpsSettingsTest`; run `./gradlew :plugin-abyssus:test --tests '*ProjectFpsSettingsTest'`.
- [x] 2.3 Reconciliation with `add-realistic-water` is no longer needed: this change no longer modifies `Panel follows the asset selection` (the `.abss` routing already exists), so only an added requirement remains. Verify with `openspec validate add-project-fps-counter --strict`.
- [x] 2.4 Document selecting the `.abss` project row and the remembered IDE-project scope in README's user-facing feature description and CHANGELOG's Unreleased section, preserving the plugin-description markers. Verify the rendered text describes default off and no game-file changes, and run `scripts/check-docs.sh`.

## 3. Editor binding and viewport overlay

- [x] 3.1 Extend `SceneView` with the settings operation and bind `SceneFileEditor` to the saved preference and disposable changes, applying it to later/replaced views. Add fake-view cases in `SceneFileEditorTest` for initial saved state, updates to multiple editors, replacement and disposed editors. Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneFileEditorTest'`.
- [x] 3.2 Integrate sampling into `SceneViewPanel` before rendering and after successful swap, resetting on disable, unsafe surface, hide, failure, context replacement and disposal. Keep sampling independent of ray-image production. Add `SceneViewPanelFpsTest` using a GL-free lifecycle/presentation seam and extend `FpsCounterTest` to cover a ten-second pause and re-enable; verify with `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewPanelFpsTest' --tests 'net.nevinsky.abyssus.plugin.sceneview.FpsCounterTest'`.
- [x] 3.3 Feed the sampled value into the stats strip that `redesign-scene-view-toolbar` draws in the lower-left corner (`statsLine(fps, ...)`, shown as `<n> fps`, `-- fps` before the first sample, omitted when off); no separate overlay or font shader is added. Verify `StatsTextTest`-style cases for the placeholder, numeric and off states, and `./gradlew :plugin-abyssus:test --tests '*SceneRenderGlTest.cornerOverlaysPaintTheirCornersOnly' -Dabyssus.glTests=true` for the strip in its corner.
- [x] 3.4 Update `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md` for preference delivery, completed-viewport-frame semantics and overlay lifecycle; update relevant architecture/testing notes if the new seams make them incomplete. Verify paths with `scripts/check-docs.sh` and compare the notes with the implemented threading and ownership.

## 4. Integration verification

- [ ] 4.1 Perform runIde checks 1–5 below using a temporary copy of the fixture; record results and leave this task unchecked if any required manual check cannot be performed.
- [ ] 4.2 Run `openspec validate add-project-fps-counter --strict`, `./gradlew check` and `scripts/check-docs.sh`; record any unrelated failures without changing their scope.

### Numbered runIde checks for task 4.1

1. Copy `src/test/testData/project/Untitled` to a temporary directory and launch `./gradlew runIde -PideProject=<copy>`. Select `Untitled.abss`; verify `Show FPS` starts unchecked, can be checked with no scene open, and opening `Main Scene` shows a placeholder followed by a numeric value.
2. Uncheck/recheck with the view open; verify the overlay disappears/reappears without reopening and the checkbox retains its state after selecting an asset or scene. Reopen the IDE project and verify persistence. Open a second IDE project and verify its default remains off.
3. Check a second scene view in the same project receives the preference and has its own measurement. Orbit, select `Model 0`, drag its gizmo and Undo. Resize and test available display scaling/light and dark scene backgrounds; verify readable lower-left placement in the stats strip and readable text over loading feedback.
4. Hide/show the view, minimize/restore the window, reduce/restore its size and close/reopen the tab. Verify safe rendering and fresh placeholders rather than stale FPS or samples incorporating inactive time. Confirm no counter-driven activity persists after closing views.
5. On a supported machine, enable Ray Tracing from the existing scene properties control; verify the same counter continues to display viewport FPS and fallback/off keeps it working. Compare game-file bytes before/after toggling alone (separate from intentional gizmo edits); verify no FPS settings appear in Mundus files.
