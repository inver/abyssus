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

Assumption: "for project" means the IntelliJ project's view preference. When one IDE project contains several `.abss` files, each project row exposes the same checkbox and all scene views share it, including standalone scenes. Separate IDE projects have separate storage. This keeps the preference local to the user's IDE and avoids introducing a field Mundus does not write. A per-`.abss` map adds unnecessary scope; per-view transient state would lose the user's project preference.

This is an IDE settings write, not a Mundus scene/project file edit, so `editSceneJson` is not involved. No `.abss`, `.scene` or asset fields are newly read or written.

### 2. Add a project details state and view

Route valid `.abss` `AbyssusAssetNode` selections to `PanelState.ProjectDetails` before the non-asset fallback. Render a focused `ProjectDetailsView` with the project file name, a view-settings explanation and `Show FPS`. Its checkbox reads and updates `SceneViewSettings`, and listens for changes only while the details view is active. Selection, construction and updates run on the EDT; no background file read is necessary. On switching views, reuse `viewDisposable` cleanup and release any asset undo-editor/terrain state.

Keep scene controls and asset/entity routing intact. Put checkbox, explanation and FPS text format in `AbyssusBundle.properties`. Use a stable component name for platform tests.

The existing main selection requirement lists project and scene rows as empty. This change explicitly replaces that scenario while preserving the scene controls already present in the working tree. `add-realistic-water` separately modifies that same requirement: add an implementation task to amend its delta and related artifacts to preserve project settings and available scene controls along with water properties, rather than letting a later archive restore the empty-state behavior.

### 3. Bind settings through the editor, sample independently per viewport

Add a default no-op `setFpsEnabled(Boolean)` operation to `SceneView`, implemented by `SceneViewPanel`. `SceneFileEditor` subscribes to settings with itself as the disposable parent, applies changes to its current view, and applies the saved preference to every newly created/replaced view. Default no-op behavior keeps existing test fakes and other implementations compatible. Projectless panel/harness construction starts disabled.

Implement `FpsCounter` as a plain Kotlin class in the plugin scene-view package. Supply monotonic nanosecond timestamps explicitly, so tests need no clock sleeps. Keep only a baseline, completed-frame count and nullable integer display value. Begin the sample immediately before the first eligible frame; count after a successful `swapBuffers`. When elapsed time is at least one second, compute `round(completedFrames * 1e9 / elapsedNanos)`, publish that value and start a new window from that completion. Use wide numeric types and handle zero/backward timestamps defensively without publishing invalid values. Do not use animation's clamped delta or process-global `Gdx.graphics.framesPerSecond`.

The overlay draws the latest published value; a value computed after swapping is shown on the next frame. This counts actual viewport presentations and includes swap/render delays, including the first frame. Ray mode redraws of the latest image still count as viewport frames. Sampling does not add a timer or schedule extra renders.

Settings delivery and sampling run on the AWT/EDT thread. Reset on disable/enable, hidden or unsafe timer ticks, `removeNotify`, failure, disposal, context abandonment and re-creation. Resume with the placeholder; never fold hidden time into the next sample. Pure state/reset behavior belongs in `FpsCounter` and its tests; the panel supplies lifecycle events.

### 4. Draw a GL text overlay owned by the canvas

Introduce `FpsOverlay`, created lazily when enabled with a safe current context, drawing after scene content and loading feedback and before swapping. Keep it at the panel canvas level so raster and ray rendering use the same overlay and do not depend on ray frame production. The experimental render branch can use this same final draw without changing its measurements or control flow.

Use libGDX's existing bitmap-font support with a batch and shader compatible with the established GL 3.2 core profile; do not assume the default text shader works on that profile. Draw a compact translucent dark backing and light text at the upper-right, inset from the edge. Convert logical padding/font size to framebuffer coordinates using the canvas's logical-to-framebuffer ratio. Restore any GL state the draw changes; handle tiny viewports by keeping drawing within bounds. Avoid per-frame text texture uploads and do not construct GPU resources while disabled.

All creation, drawing and safe disposal occur only inside `GdxRuntime.withContext` on the rendering AWT thread. Dispose alongside renderer resources in `disposeGL`; on abandonment drop references without GL calls and recreate when a safe context returns. Existing guarded rendering stays authoritative. A Swing label in the toolbar is simpler but does not provide the requested viewport counter; a Swing overlay cannot reliably cover this canvas.

## Risks / Trade-offs

- [Users read viewport FPS as GPU or ray-backend throughput] → Describe the metric as scene-view FPS in the control explanation and documentation; it includes timer pacing and repeated ray-image presentation.
- [Core-profile font shader or HiDPI sizing is wrong] → Use the existing shader-loading pattern and add an opt-in real-GL composition check plus numbered runIde checks for scaling, resize and light/dark scenes.
- [Settings listeners retain replaced/closed panels] → Attach panel listeners to the existing per-view disposable and editor listeners to the editor; verify disposal in platform tests.
- [Resume reports stale or artificially low FPS] → Reset all unsafe/hidden lifecycle transitions and verify with explicit timestamps.
- [Concurrent open proposals overwrite selection behavior] → Reconcile the water delta during implementation and validate both changes. If the runtime extraction proposal lands first, attach sampling at the equivalent viewport presentation boundary; keep it outside asset loading and backend production.

## Migration Plan

No Mundus data migration is needed. Existing IDE projects start with the counter off. Removing the feature leaves only an unused namespaced IDE preference; reverting does not require touching game files. Implementation updates the user-facing README/Unreleased notes and scene-view package notes, preserving README plugin-description markers.
