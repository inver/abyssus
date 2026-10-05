# Tasks

## 1. Setting and fallback logic

- [ ] 1.1 Add plain `AntialiasingLevel` (Off, 2×, 4×, 8×; default 4×) with stored-text parsing and sample counts, and
  `SampleFallback` producing the ordered sample counts to try. Verify `AntialiasingLevelTest` (default, round trip,
  missing and unknown text read as 4×) and `SampleFallbackTest` (8× gives 8, 4, 2, 0; 2× gives 2, 0; Off gives 0) with
  `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.AntialiasingLevelTest' --tests 'net.nevinsky.abyssus.sceneview.SampleFallbackTest'`.
- [ ] 1.2 Add the application service `SceneViewAntialiasing` backed by application-level `PropertiesComponent`:
  default 4×, save before notifying, no notification for an unchanged value, listeners removed with their disposable.
  Verify `SceneViewAntialiasingTest` cases for default, stored-value reload, unchanged-value suppression and listener
  disposal with `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneViewAntialiasingTest'`.

## 2. Settings page

- [ ] 2.1 Register an `applicationConfigurable` under Tools titled Abyssus with the **Scene view antialiasing** combo box
  and explanation, all text in `AbyssusBundle.properties`. Verify `AntialiasingConfigurableTest` cases for initial 4×,
  `isModified`, `apply` writing the service, `reset`, and that `Untitled.abss`, `Main Scene.scene` and a representative
  `meta.json` keep their exact text, with `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.AntialiasingConfigurableTest'`.

## 3. Scene view

- [ ] 3.1 Add the default no-op `SceneView.setAntialiasing(level)` and bind `SceneFileEditor` to the service with itself as
  disposable parent, applying the current level to new and replaced views. Add fake-view cases to `SceneFileEditorTest`
  for initial level, a change reaching two editors, a replaced view and a disposed editor. Verify with
  `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneFileEditorTest'`.
- [ ] 3.2 In `SceneViewPanel`, pass the sample count into `glData`, generalise `replaceAbandonedCanvas` into canvas
  replacement used by `setAntialiasing` (deferred to `addNotify` while hidden), and retry with the next `SampleFallback`
  count when context creation fails before `initGL`, logging once. Keep camera, selection, look-through camera, mode,
  Play and Ray Tracing mode. Cover the retry and deferral decisions through a GL-free seam in `SceneViewPanelAntialiasingTest`
  (refused 8× moves to 4×; failure after the context exists still calls `onFailure`; hidden view replaces only on show).
  Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneViewPanelAntialiasingTest'`.
- [ ] 3.3 Add `SceneViewAntialiasingGlTest` creating a panel with 4× and with Off and reading `GL_SAMPLES` from the
  current context (at least 1 for 4× when the machine supports it, 0 for Off), and switching level at run time without
  `onFailure`. Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.SceneViewAntialiasingGlTest' -Dabyssus.glTests=true`.

## 4. Docs

- [ ] 4.1 Update README's user-facing feature list (keeping the `<!-- Plugin description -->` markers), CHANGELOG
  Unreleased, `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md` (pixel format, canvas replacement, fallback) and
  `docs/ai/architecture.md` if its threading or extension notes become incomplete. Verify with `scripts/check-docs.sh`.

## 5. Manual checks

- [ ] 5.1 Perform the runIde checks below on a copy of the fixture; record results and leave this task open if a check
  cannot be performed.

### Numbered runIde checks for task 5.1

1. Copy `src/test/testData/project/Untitled` to a temporary directory and run `./gradlew runIde -PideProject=<copy>`.
   Open `Main Scene`; verify grid lines, gizmos and model edges look smooth.
2. In Settings | Tools | Abyssus, check the default is 4×. Apply Off, then 8×, with `Main Scene` open and an object
   selected; verify the view changes each time without reopening and keeps the selection and camera.
3. With Ray Tracing on, change the setting; verify the view stays in Ray Tracing and restarts accumulation.
4. Open `Main Scene` in a background tab, change the setting, switch to the tab; verify it draws with the new choice.
5. Restart the sandbox IDE; verify the choice is kept. Open a second project; verify it shows the same choice.
6. On macOS where available, repeat check 2 and minimise/restore the IDE between changes; verify no crash or stale image.

## 6. Verification

- [ ] 6.1 Run `./gradlew check` and `scripts/check-docs.sh`; record any unrelated failures without changing their scope.
