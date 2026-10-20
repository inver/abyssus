## Context

The Scene view shades what it draws through `ViewDisplay.shading` (`SceneViewToolsModel.kt`), an enum `Shading { LIT, UNLIT }` read on the EDT and pushed to `SceneRenderer.display` each frame via `SceneViewPanel.syncControls()`. `SceneRenderer.render()` draws skybox → grid → content (terrains, models, foliage) → [water] → overlays. The shading combo in `SceneToolsToolbar.ShadingAction` lists `Shading.entries`. `ViewDisplay` and the tools model are per view and never saved. No `glPolygonMode` is used anywhere in the codebase. The GL context is libGDX 3.3 core, so `GL20.glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)` is available.

## Goals / Non-Goals

**Goals:**
- Wireframe is a third entry in the shading dropdown (Lit / Unlit / Wireframe).
- In Wireframe mode all scene geometry (models, terrain meshes, foliage OBJECT layers, skybox cube, water) draws as edges only.
- Wireframe stays per view, never saved to any document.
- No library or module-boundary changes.

**Non-Goals:**
- Combining Wireframe with Lit (no lit wireframe).
- Persisting wireframe per scene or project.
- Wireframe inside Ray Tracing mode (raster only).
- Any document format change.

## Decisions

### 1. `Shading` gains `WIREFRAME`, mutually exclusive with Lit / Unlit

Keeps the dropdown simple (one value controls everything) and matches the user's choice: wireframe replaces nothing in the dropdown, it adds a third slot.

Alternatives: an independent wireframe toggle beside Shadows/Fog/Grid — rejected because it needs combinations (lit wireframe, unlit wireframe) that were not requested.

### 2. `glPolygonMode(GL_LINE)` scopes each geometry pass

`GL20.glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)` scopes, in `SceneRenderer.render()`/`renderContent()` when `shading == Shading.WIREFRAME`: the skybox draw, the terrain pass (`TerrainPass` via `TerrainShader`), the content batch (models and foliage OBJECT layers), and the water surfaces; each pass restores `GL_FILL` in a `finally`. The ray-tracing branch is left untouched — while a ray frame is presented the ray image shows as usual, so wireframe is raster only. The grid (line geometry), markers, selection box and gizmos (`LineBatch`) are unaffected by polygon mode.

Alternatives: per-shader wireframe (rejected, touches every shader); post-process edge detect (rejected, softens one-pixel gizmo/grid lines).

### 3. No persistence

Follows `ViewDisplay`'s existing pattern: set on the EDT, read on the next frame, nothing written to `.abss`/`.scene`/`meta.json`.

## Risks / Trade-offs

[Skybox draws as a cube outline] → acceptable; wireframe is an inspection mode. The skybox is wrapped at its own draw calls (`render()`:420, `drawReflected()`:696), not through the content batch, so it needs its own `GL_LINE` scope.
[Water in wireframe is hard to read] → accepted; requested scope covers all geometry.
[`glPolygonMode` state leaks if not restored] → mitigated by restoring `GL_FILL` in a `finally` block around each pass; `GL20` is available on the libGDX 3.3 core context.
[Shadows disappear in Wireframe] → by design: `ViewDisplay.drawsShadows` stays `shadows && shading == LIT`, so the shadow pass does not run in Wireframe and the Shadows toggle has no effect until Lit is chosen again; no noisy wireframe shadow atlas.
[Mode switch leaves stale lights] → `SceneRenderer.display`'s setter already clears `lightsKey`/`frameLightsKey` when `shading` changes (SceneRenderer.kt:164-168), so the next frame re-applies lights.
