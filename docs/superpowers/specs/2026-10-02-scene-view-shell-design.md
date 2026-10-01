# Scene View Shell — Design

Sub-project 1 of 4 for the realtime scene "View" feature.
Later sub-projects (each with its own spec): (2) models via gdx-gltf, (3) terrain, (4) skybox.

## Intent

A "View" icon at the right edge of each scene row in the Abyssus project view. Clicking it opens an
editor tab with a live, continuously rendering, read-only libGDX view of that scene.

Success: from the `Untitled` test project, clicking View on `Main Scene` opens a tab that animates,
shows a grid, applies the scene's ambient light and fog, and supports orbit/pan/zoom camera control.

Out of scope: models, terrain, skybox, ECS entities (including editor-only handles/gizmos), editing.

## Entry point (project view)

- `EyeTree` in `AbyssusProjectViewPane.kt` generalizes from a single eye toggle to a row-action tree.
  A scene row (a standalone `.scene` node or a project's scene entry) gets a "View" icon
  (`icons/view_scene.svg`) at the right edge, with a tooltip. Existing eye toggles keep working.
- Click calls `FileEditorManager.openFile(sceneFile)` and selects our editor explicitly.

## Editor

- `SceneFileEditorProvider` is registered for `.scene` with policy `PLACE_AFTER_DEFAULT_EDITOR`, so the
  icon opens the view while double-click keeps the text editor.
- `SceneFileEditor : FileEditor` wraps `SceneViewPanel`. Read-only, disposable; reloads on file change
  via a VFS listener.
- If GL init fails, the tab shows the existing `glUnavailable` message.

## libGDX host (core on `AWTGLCanvas`)

- `GdxAwtHost` owns an `AWTGLCanvas` (lwjgl3-awt, already in the build). In `initGL` it installs
  `Gdx.gl20/gl30`, `Gdx.graphics` (size, delta time) and `Gdx.files` shims, then creates the renderer.
- A Swing `Timer` (~60 fps) drives `canvas.render()`. It stops when the tab is hidden
  (`removeNotify`) and on any GL exception, which is logged as a warning.
- New dependencies: `gdx` core and `gdx-platform` natives for macOS arm64, macOS, Windows, Linux.
  The shim lives in one class so a libGDX upgrade touches a single file.
- `Gdx.*` is process-global inside the IDE. Install it only while a scene view renders, restore the
  previous values afterwards, and serialize access across multiple open tabs.

## Scene to render state

- `SceneRenderer` consumes the parsed `SceneDto`:
  - clear color from the fog color, or a default when fog is disabled;
  - ambient `ColorAttribute` from `ambientLight` when `ambientLightEnabled`;
  - `Environment` fog from `fog` when `fogEnabled`.
- Camera: `PerspectiveCamera` initialized from the project's `mainCamera`; mouse gives orbit, pan, zoom.
- A ground grid is drawn so the view is not empty before models exist.

## Testing

- Unit: `SceneDto` to render parameters (pure code, no GL).
- Platform: provider accepts only `.scene`; `SceneFileEditor` disposes cleanly.
- Manual: `runIde` with the `Untitled` project; verify animation and camera controls.

## Risks

- The hand-written `Gdx` shim may diverge across libGDX versions (mitigated by isolating it in one class).
- macOS GL threading: rendering must stay on the AWT thread that lwjgl3-awt expects.
