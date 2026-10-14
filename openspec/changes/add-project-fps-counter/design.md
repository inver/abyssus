# Design

## Context

See `proposal.md` for motivation and the two delta specs for the behavior contract. `AssetPropertiesPanel.show` currently routes `.abss` selections to `PanelState.Empty`; scene rows already use `SceneDetailsView` for runtime Ray Tracing controls. `AbyssusAssetNode` exposes the selected file, so project rows can be recognized by `ProjectLayout.PROJECT_EXTENSION` without parsing project JSON.

`SceneFileEditor` owns a replaceable `SceneView` and passes project-aware collaborators to `SceneViewPanel`. The panel has a Swing frame timer and performs drawing inside `GdxRuntime.withContext`, then calls `swapBuffers`. `GuardedGLCanvas` guards hidden, minimized and empty surfaces. `LoadingOverlay` documents why a Swing component cannot be placed over the heavyweight canvas.

Design is included because preferences, properties selection, editor binding and graphics lifecycle cross several components. The working tree already includes ongoing ray-tracing changes; implementation must preserve those controls and the existing renderer branches.

## Goals / Non-Goals

**Goals:**
- Keep sampling and lifecycle decisions independent of GL, Swing and IntelliJ so time-based behavior is deterministic in headless tests.
- Bind project preference changes through the editor's disposable lifetime; bind GPU resources to the canvas context lifetime.
- Preserve the existing timer, guarded surface checks and render modes.

**Non-Goals:**
- Change animation delta time, scheduling, swap interval, or ray-backend diagnostics.
- Add project JSON settings, change module boundaries, or refactor the rendering subsystem.

## Decisions

### 1. Store one preference per IDE project

Introduce a small project service, `SceneViewSettings`, backed by project-scoped `PropertiesComponent` with a namespaced `showFps` key and default `false`, following the storage precedent in `UnusedFilter`. Expose the value, a setter and disposable listeners. Read/write and notify on the EDT. Save before notification and avoid notifications when the value is unchanged.

Assumption: "for project" means the IntelliJ project's view preference. When one IDE project contains several `.abss` files, each project row exposes the same checkbox and all scene views share it, including standalone scenes. Separate IDE projects have separate storage. This keeps the preference local to the user's IDE and avoids introducing a new document field. A per-`.abss` map adds unnecessary scope; per-view transient state would lose the user's project preference.

This is an IDE settings write, not a scene/project file edit, so `editSceneJson` is not involved. No `.abss`, `.scene` or asset fields are newly read or written.

### 2. Add the checkbox to the existing project properties view

The `.abss` row already routes to `PanelState.Project` and `ProjectDetailsView` (name, `Physics` checkbox, problems). Add a `Show FPS` checkbox and its explanation below the Physics controls. It reads and updates `SceneViewSettings`, which is an IDE setting, not the `.abss`: it works for an unsupported project too, and it never goes through `AbyssusProjectSettings`. The checkbox registers a listener on the view's disposable so it follows the setting if another view of it changes it. Construction and updates run on the EDT. Put the text in `AbyssusBundle.properties` and use the stable component name `show-fps` for tests. Scene controls and asset/entity routing are untouched.

### 3. Bind settings through the editor, sample independently per viewport

Add a default no-op `setFpsEnabled(Boolean)` operation to `SceneView`, implemented by `SceneViewPanel`. `SceneFileEditor` subscribes to settings with itself as the disposable parent, applies changes to its current view, and applies the saved preference to every newly created/replaced view. Default no-op behavior keeps existing test fakes and other implementations compatible. Projectless panel/harness construction starts disabled.

Implement `FpsCounter` as a plain Kotlin class in the plugin scene-view package. Supply monotonic nanosecond timestamps explicitly, so tests need no clock sleeps. Keep only a baseline, completed-frame count and nullable integer display value. Begin the sample immediately before the first eligible frame; count after a successful `swapBuffers`. When elapsed time is at least one second, compute `round(completedFrames * 1e9 / elapsedNanos)`, publish that value and start a new window from that completion. Use wide numeric types and handle zero/backward timestamps defensively without publishing invalid values. Do not use animation's clamped delta or process-global `Gdx.graphics.framesPerSecond`.

The overlay draws the latest published value; a value computed after swapping is shown on the next frame. This counts actual viewport presentations and includes swap/render delays, including the first frame. Ray mode redraws of the latest image still count as viewport frames. Sampling does not add a timer or schedule extra renders.

Settings delivery and sampling run on the AWT/EDT thread. Reset on disable/enable, hidden or unsafe timer ticks, `removeNotify`, failure, disposal, context abandonment and re-creation. Resume with the placeholder; never fold hidden time into the next sample. Pure state/reset behavior belongs in `FpsCounter` and its tests; the panel supplies lifecycle events.

### 4. Show the value in the stats strip drawn by the scene view

The scene view draws a stats strip in the lower-left corner (`redesign-scene-view-toolbar`): Swing paints it into an image and a small GL pass (`CornerOverlay`) shows it as a quad after the loading overlay, so no bitmap-font batch or text shader is needed here. This change supplies the sampled integer to `statsLine(fps, ...)`, which puts `<n> fps` (or `-- fps` before the first sample) first when the counter is enabled and omits it otherwise. The overlay stays at the panel level, so raster and ray rendering share it, and it follows the existing safe-surface and context-lifecycle rules of `CornerOverlay`. Padding, fonts and HiDPI scale come from the strip's Swing painting.

## Risks / Trade-offs

- [Users read viewport FPS as GPU or ray-backend throughput] → Describe the metric as scene-view FPS in the control explanation and documentation; it includes timer pacing and repeated ray-image presentation.
- [Core-profile font shader or HiDPI sizing is wrong] → Use the existing shader-loading pattern and add an opt-in real-GL composition check plus numbered runIde checks for scaling, resize and light/dark scenes.
- [Settings listeners retain replaced/closed panels] → Attach panel listeners to the existing per-view disposable and editor listeners to the editor; verify disposal in platform tests.
- [Resume reports stale or artificially low FPS] → Reset all unsafe/hidden lifecycle transitions and verify with explicit timestamps.
- [The runtime extraction proposal lands first] → Attach sampling at the equivalent viewport presentation boundary; keep it outside asset loading and backend production.

## Migration Plan

No document migration is needed. Existing IDE projects start with the counter off. Removing the feature leaves only an unused namespaced IDE preference; reverting does not require touching game files. Implementation updates the user-facing README/Unreleased notes and scene-view package notes, preserving README plugin-description markers.
