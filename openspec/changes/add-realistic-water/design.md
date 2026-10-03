# Design

## Context

See proposal.md for motivation and scope. SceneContent currently extracts models, terrains, lights and cameras from ECS plus one sky. SceneRenderer draws sky/grid, then updates and renders terrain/models, then overlays. Models use gdx-model's custom 32-bit-index batch. Current updates inside renderContent cannot be repeated for a reflection pass: they advance animations. The open `add-scene-shadows` design already requires a single geometry snapshot and extra depth views; share that extraction if present, otherwise introduce it here without implementing shadows.

The properties panel's PanelState distinguishes assets and ECS targets. Water requires a distinct target, not fabricated component identifiers. Existing scene edits already pass through editSceneJson and SceneJson. Plugin-only sky asset types are precedent for extensions, but this feature deliberately stores procedural water in scene data rather than creating assets.

## Goals / Non-Goals

**Goals:** deterministic above-water composition; independent surfaces; all edits and projection/hit math testable headlessly; GPU resources owned by a view/context; bounded extra passes; reusable plain JVM water code.

**Non-Goals:** a general render graph framework, new ECS archetypes, generic entity creation, asset duplication, or a promise of Mundus persistence. Water is a rendering surface, not physics ground. See proposal for visual exclusions.

## Decisions

### 1. Namespaced scene data, not a new ECS component

Store `abyssus.waterSurfaces` as an ordered object keyed by generated UUID strings. Preserve unrelated `abyssus` members and unknown surface fields. Each surface has the fields listed in scene-water; `position` is `{x,y,z}` at its centre, with y the mean water level. `tint` is `{r,g,b}` in [0,1]. Width and length are world units along X and Z; no rotation or scale field. `preset` is `LAKE` or `SEA` and records the starting choice, not a live link that overwrites edits. Changing presets after creation is not an action in this version.

Proposed defaults (implementation choices, not claims of user-selected values):

| Field | Lake | Sea |
|---|---|---|
| width / length | 100 / 100 | 2000 / 2000 |
| tint | 0.03, 0.22, 0.25 | 0.02, 0.12, 0.20 |
| clarity (attenuation distance) | 8 | 15 |
| waveAmplitude / waveLength / waveSpeed | 0.08 / 4 / 0.5 | 0.6 / 24 / 2 |
| foamAmount / foamWidth | 0.5 / 0.6 | 0.8 / 1.5 |
| enabled | true | true |

Write explicit preset values on creation so future default tuning does not change saved appearance. Missing individual fields use the recorded preset's defaults without writes. Reject unknown preset, malformed containers or non-finite values; positive extent, clarity, wavelength and foam width; amplitude/speed non-negative; foam amount in [0,1]. Malformed saved surfaces remain visible as invalid rows for removal but do not render. Never replace an incompatible `abyssus` container when adding; report rejection. Keep an empty waterSurfaces object after deleting its final member to avoid altering unrelated namespace contents.

Alternative: WaterComponent in ECS would require foreign class/archetype bookkeeping and risk existing codecs treating it as a Mundus entity. Alternative: sidecar avoids scene extensions but needs file association, extra watchers and multi-document undo. The explicitly authorized Abyssus-only extension is simpler; document that another editor may remove it.

### 2. Editing, identity and selection

Use pure WaterEdits over SceneJson for add/update/rename/toggle/remove and pure core WaterSettings/WaterValidation for scalar rules. SceneDto retains the raw extension; a codec produces immutable settings for SceneContent. Water row identity is `(scene file, water id)`, separated from ECS ids by a sealed scene-selection target. Adapt callers of selectedId rather than passing UUIDs to ECS writers. WaterProperties is a dedicated PanelState and editor view with labels/commands/errors from AbyssusBundle.

Add Water in SceneViewPanel uses orbit target; a tree action uses origin. Water group is shown only when saved surfaces exist; no empty extension is written merely to expose actions. New surfaces get unique display names Lake/Sea with a numeric suffix. Use the shared selection path after the document refresh. Properties set position/size; only move gizmos are supported. Water drag previews update immutable settings and commit only position via editSceneJson. Rotation and Drop are disabled; deleting the selected surface clears view selection.

WaterHit intersects the bounded mean-level rectangle on CPU. Reject hits covered by nearer scene geometry using the existing terrain/model/marker pick distances; nearest target wins. This intentionally approximates wave crests by the mean level. Hidden, invalid, fully occluded or below-view surfaces are not selectable through their back face. Outline the selected extent as an overlay; visible submerged objects are selected through tree rows in this first version. Water never joins restHeight terrain/box ground lists.

### 3. Frame snapshot and pass sequence

Extract updateFrame from renderContent: load assets, advance each model once, apply preview transforms, resolve lights and snapshot geometry. Pass functions draw the snapshot without mutating animation. Additional cameras must not overwrite the main camera used by picking. Per-frame work:

```text
update geometry/animation once --> optional existing shadow passes
   --> above-water reflection views (bounded)
   --> main opaque scene color + sampleable scene depth
   --> terrain-only depth for shoreline contact
   --> main sky/grid/opaque scene to canvas
   --> water composite --> ordinary transparent models --> editor overlays
```

Offscreen color excludes editor overlays/grid and all water; sky is background color but never a depth/terrain sample. Opaque and alpha-tested geometry participates; alpha-blended models render after water and are excluded from its reflection/refraction. That is an explicit compositing limitation. If there is no visible valid water, use the existing main path without extra buffers. Model pass filtering must retain skinning, 32-bit meshes and alpha-cutout semantics.

If shadows exist, share their posed snapshot and reusable depth support; main-camera shadow coverage can limit distant reflected geometry, which remains lit outside coverage. Do not generate a second shadow atlas per reflected camera. This change adds no shadow behavior for water itself and does not amend scene-shadows' opaque-geometry contract.

### 4. Planar reflection and wave lighting

Mirror position, direction and up vector across each mean-level plane with pure WaterReflectionCamera math; verify projection orientation/winding. Clip geometry below mean level plus a small scale-aware bias in fragment shaders using a reusable optional world clip-plane uniform/attribute in gdx-model and TerrainShader. Avoid platform GL clip-plane functions. Draw sky from the mirrored orientation, preserving main light selection and environment.

Group surfaces only when their mean levels are equal. Initial budget: two reflection views at half viewport resolution, maximum 1024 per dimension. Select visible level groups by projected area with stable id tie breaks and hysteresis; reuse pool targets and invalidate when camera, scene or context changes. Unbudgeted groups reflect the current sky through existing sky/environment representations where available, otherwise tint; fallback is deterministic and documented. Reflections exclude water to prevent recursion.

Use a bounded grid mesh and a small deterministic sum of travelling waves with analytic height derivatives for normals; world-space phase makes animation stable while moving the camera. Adapt vertex density within a fixed mesh cap to wave length and extent; small ripples add analytic fragment normals. Wave displacements are vertical (no fluid simulation). Fresnel blends reflected radiance with refracted scene color; directional-light highlights and existing ambient/environment light integrate the water with scene lighting. Apply scene fog once to final water color. Packaged shaders are loaded via ShaderSource; no image generation or external texture assets are needed.

Alternative: screen-space reflection misses offscreen geometry and breaks at borders. Full ray tracing is outside this renderer. Planar reflection matches horizontal surfaces and has predictable cost.

### 5. Shallows, refraction and foam

Render sampleable depth to an RGBA color target using depth-test attachments, with a reserved empty-depth value; this works with the project's GL20 path without requiring depth textures or multiple render targets. Reuse one general geometry depth target and a separate terrain-only target. Depth shaders follow current skinning and alpha testing. WaterDepthMath defines packing/reconstruction and the inverse-view-projection conventions, tested independently. Buffer size is capped at 2048 per dimension and coordinates account for the viewport/target scale.

Reconstruct bottom position from scene depth; attenuation is based on water-to-bottom distance, with clarity as a positive distance scale and tint defining absorption. Distort refraction UVs using wave normals; clamp to texture bounds and reject samples whose reconstructed position is above the local water surface, falling back to undistorted samples to avoid pulling dry land into water. Empty depth represents deep water, not a shoreline. Terrain depth alone drives shallow-contact foam so models, grid, sky and extent edges cannot create false shores. Foam uses positive vertical separation from mean water level, width and animated world-space noise; attenuate wave amplitude near contact to limit shore penetration. This approximates shore distance by depth rather than true horizontal distance; foam width is a world-unit depth-band control.

Projection/depth math must use the active view camera's near/far values, handle grazing rays and empty pixels, and avoid NaNs at the mean surface. Shared main-depth occlusion prevents water covering above-level terrain. Water surfaces composite far-to-near with depth testing/writes so intersecting finite extents are deterministic; equal-level overlap gets stable id order and is documented as redundant surfaces, not a physically merged fluid.

### 6. Resources, fallback and threads

Core WaterSettings, WaterValidation, WaterWaves, WaterReflectionCamera, WaterDepthMath and WaterPassPlan are plain constructor-wired classes without Swing, platform or GL dependencies; WaterHit and WaterEdits are pure plugin-side math/JSON classes. Immutable parsing may run on the existing scene-read path. Property commits, selection and drag handling run on EDT/AWT; CPU-only picking needs no graphics context.

All libGDX operations, framebuffer allocation/resize, shader compilation, mesh updates, draws and safe disposal happen only on the AWT render thread inside GdxRuntime.withContext and while GuardedGLCanvas.glSafe is true. No GL is called from AssetCache.prepare. Core owns water drawables/shaders; plugin orchestration owns per-view pass targets and cameras; gdx-model owns optional reusable model pass capabilities and remains plain JVM.

Capture and restore framebuffer binding (the caller's framebuffer, not assumed zero), viewport, scissor, depth mask/test, culling, blend and active texture state in finally. Assign sampler units after checking limits and avoid aliasing cube/2D samplers. Context abandonment drops references without GL calls; recreation rebuilds. Pool/reuse targets, release unused targets safely and skip zero-sized/hidden views.

Reflection failure falls back to sky/tint. Color/depth failure uses a simple animated tinted surface without refraction/foam; water shader/mesh failure skips water only. Log each configuration/context failure once, retry after meaningful resize/config/context changes and show no repeated modal errors. Cancellation-aware work uses runCatchingKeepingCancellation. At/below mean level, skip that surface's above-water rendering; navigation continues with the normal scene and no underwater visual promise.

## Risks / Trade-offs

- Extra passes on the AWT thread can stall input -> fixed target/view/mesh caps, skip invisible surfaces, pool resources and profile one/two/many-level scenes before tuning caps.
- Large sea precision and shallow depth artifacts -> finite extents, camera-relative shader calculations where needed, depth reconstruction tests and GL checks at near/far extremes.
- Model clipping/depth support can break existing shaders -> optional pass attributes default off; regression tests for no-water scenes and alpha-tested/animated meshes.
- Planar reflection ignores wave geometry and transparent models -> distort reflected image with wave normals and document bounded visual scope.
- Shared renderer edits overlap add-scene-shadows -> inspect its current implementation at apply time and integrate into its snapshot/pass entry points; avoid independent frame-update pipelines.
- Existing tree spec says only three actions write files; show-project-assets is still open -> water delta removes that stale exclusivity, and implementation must reconcile any conflicting open delta without deleting its asset behavior.
- Another editor may discard `abyssus` data -> explicitly document Abyssus-only persistence and make no Mundus compatibility claim.

## Migration Plan

No automatic migration or file rewrite. Existing scenes render identically with no water passes. Add creates only the namespaced extension; Undo restores the original text. Older Abyssus versions may display the namespace as unknown data and ignore rendering. Removal affects one surface, retaining other extension members. Rollback leaves extension data intact for a future supporting version.
