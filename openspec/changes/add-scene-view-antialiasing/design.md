# Design

## Context

See `proposal.md` for motivation and `specs/scene-view-antialiasing/spec.md` for the behavior contract.

`SceneViewPanel` builds a `GuardedGLCanvas` from `glData()` (GL 3.3 core, 24-bit depth, swap interval 0) and renders
straight into the canvas's default framebuffer; there is no offscreen color buffer. The context is created lazily on
the first `canvas.render()` from the Swing frame timer, which then calls `initGL` (renderer resources) and `paintGL`. A
throw from `render()` currently stops the loop and reports `onFailure`. The panel already knows how to swap in a fresh
canvas: `replaceAbandonedCanvas` removes the old one and adds `newCanvas()` with re-attached input, and `initGL` on the
new context recreates the renderer's GL resources.

The shadow atlas and the ray sky baker render into their own framebuffers; the ray-traced image is a fullscreen
textured quad. The GL test harnesses (`GlHarness`, `TestGl`) build their own `GLData` and read pixels back.

Design covers native project preference persistence and the canvas/context lifecycle, which is fragile on macOS.

## Goals / Non-Goals

**Goals:**
- Smooth lines and silhouettes with the least moving GL state: a multisampled default framebuffer.
- Keep the setting, its stored form and the fallback order free of Swing, GL and the platform, so headless tests cover them.
- Reuse the existing canvas replacement path; never make a context current on an unsafe surface.

**Non-Goals:**
- Offscreen multisampled framebuffers with a resolve pass, or post-process antialiasing.
- Changing the GL test harnesses' pixel format; pixel-reading tests stay single-sampled and deterministic.

## Decisions

### 1. Multisample the canvas's own pixel format

`glData(samples)` sets `samples` (and `sampleBuffers = 1` when `samples > 0`) on the existing `GLData`. Everything the
renderer draws into the default framebuffer, including the grid, `LineBatch` lines, markers, gizmos, overlays and the
loading overlay, is antialiased with no renderer change. Framebuffers the renderer creates itself are unaffected.

Alternative: render into a multisampled FBO and blit it to the canvas. It allows changing samples without a new
context, but adds a buffer that must follow resizes and `glSafe`, and an extra full-screen copy per frame. Replacing the
canvas on a setting change is rare and already supported, so the simpler pixel format wins. FXAA was rejected because it
softens one-pixel gizmo and grid lines, which are the main thing to fix.

### 2. One setting per native project

The existing `AbyssusProjectSettings` service reads and edits each native `.abss` independently, using its document
cache and `ProjectSettingsListener` notifications. Extend `ProjectSettings` with the plain `AntialiasingLevel` value.
Keep the enum in a neutral plugin settings package so DTO parsing does not depend on the scene-view package.

The root key `sceneViewAntialiasing` stores `OFF`, `X2`, `X4` or `X8`. Missing means `X4`; unsupported or wrongly typed
values show a localized project-settings problem and render with `X4` without changing text. Unsupported native
markers disable editing. `setAntialiasing` uses `editSceneJson`, removes only this key for the default, suppresses
unchanged writes, and preserves unrelated numbers, ordering, extension fields and formatting.

`ProjectDetailsView` adds the localized **Scene view antialiasing** combo beside the existing project controls.
The existing properties panel refresh and undo editor handle text edits and Undo/Redo. Remove the IDE Settings page
and application preference service; do not migrate their stored values or write anything on opening a project.

### 3. Fallback order in a pure class

`SampleFallback` turns a requested level into the ordered list of sample counts to try: the requested count, then each
lower supported count, then 0 (for 8×: 8, 4, 2, 0; for Off: just 0). It is plain Kotlin, tested headlessly.

The panel keeps the remaining attempts for its current canvas. A pixel format with samples is refused when context
creation throws on the first `canvas.render()`, before `initGL` has run (no `gdx` context yet). In that case the panel
logs one warning naming the refused count, replaces the canvas with the next count in the list and keeps the timer
running. A throw after the context exists, or with 0 samples, keeps today's behavior: stop the loop and call
`onFailure`. A driver that silently grants fewer samples is accepted as is.

The panel exposes the effective sample count of its current canvas (`internal`, for tests and the log).

### 4. Applying a change to open views

`SceneFileEditor` subscribes to `ProjectSettingsListener` with itself as owner, and only applies events for its
scene's owning `.abss`. New and replaced views read the current document-backed value; standalone scenes use `X4`.
Remove `sceneViewAntialiasing` from the editor's render-content comparison, as for `physicsEnabled`, so an antialiasing
edit recreates the canvas without stopping Play or reloading scene content. Reuse the default no-op
`SceneView.setAntialiasing(level)` for fake or alternate views.

`SceneViewPanel.setAntialiasing` stores the level, resets the fallback list, and replaces the canvas through the same
path as `replaceAbandonedCanvas` (generalised to `replaceCanvas`): the old canvas is disposed with `disposeCanvas()`,
whose `GuardedGLCanvas` override already releases GL resources only when the surface is safe and abandons the context
otherwise. If the panel is not displayed, it only marks the canvas stale and replaces it on the next `addNotify`, so no
context is created for a hidden view.

Camera, selection, look-through camera, interaction mode and Play state live outside the GL context and are kept. The
ray feed is reset as on any context loss, so Ray Tracing restarts accumulation in the same mode. The renderer rebuilds
its GL resources in `initGL`; assets that must be uploaded again show the existing loading overlay briefly.

### Threads

- Project controls, document edits, settings notifications, editor binding and `setAntialiasing`: EDT.
- Canvas replacement and the fallback retry: the EDT, which is the AWT thread that runs the frame timer and renders the
  canvas. GL resource creation and release stay inside `initGL` / `disposeGL` under `GdxRuntime.withContext`, as today.
- `AntialiasingLevel`, its parsing and `SampleFallback`: no thread affinity, no Swing, GL or platform code.

## Risks / Trade-offs

- [macOS context churn] Replacing a canvas while shown is the riskiest step on macOS → reuse `GuardedGLCanvas.disposeCanvas`
  and defer replacement of hidden views to `addNotify`; cover by runIde checks on macOS where available.
- [Driver accepts samples but renders wrongly] Not detectable automatically → the user can pick Off in project properties.
- [Cost of 8× on large views] Fill-rate cost grows with samples → default stays 4×, matching the game.
- [Depth read-back with multisampling] Production code does not read the canvas's depth; tests that do use their own
  single-sampled harness.
- [Ray Tracing] Multisampling does not refine the ray-traced image itself; only lines and overlays drawn over it gain.

## Open Questions

None.
