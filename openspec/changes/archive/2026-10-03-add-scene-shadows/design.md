# Design

## Context

See proposal.md for motivation. SceneRenderer draws terrain with a dedicated TerrainShader and models with gdx-model's custom 32-bit-index ModelBatch. Spot lights currently share the point-light list. Model shaders accept one legacy environment shadowMap and multiply all direct contributions by its visibility; that cannot represent independent shadows from multiple lights. No custom model depth shader is present. Terrain already uses texture units through 7 for splat layers and HDR irradiance.

## Goals / Non-Goals

**Goals:** Share beam and shadow conventions across default, PBR and terrain shading; bound GPU work and allocations; keep projection, allocation and beam math testable without GL.

**Non-Goals:** See proposal.md. No dependency on a new render engine and no changes to asset-loading ownership.

## Decisions

### 1. Persist beam settings with an explicit extension fallback

User confirmed scene persistence and authorized Abyssus-specific fields if Mundus equivalents are absent. Verify the relevant upstream serialization or a Mundus-produced fixture before implementing the codec. Prefer native equivalents with a documented mapping when verified; otherwise use `coneAngle` in full degrees and `edgeSoftness` as a fraction 0..1 under the existing light object (or flat representation). No workflow configuration change is required for this user-authorized exception.

Proposed defaults: 45 degrees and 0.2 softness. UI displays softness as 20 percent. Omit fallback default values, preserve unknown keys and never normalize unrelated numeric text. Round-trip nested and flat representations. Do not claim Mundus understands or preserves the extension; switching editors may lose these settings. IDE preferences or sidecar files were rejected because the user requested scene persistence.

Verified on 2026-10-03 against the local Mundus checkout at commit
`128175e064a915e043f024a565f935d4c6883292`: `projects/lib-commons/src/main/java/com/mbrlabs/mundus/commons/core/ecs/component/LightComponent.java`
marks `light` transient, and `projects/lib-commons/src/main/java/com/mbrlabs/mundus/commons/env/lights/SpotLight.java`
adds only position to DirectionalLight, with no cone or softness parameters. The editor-produced
`src/test/testData/project/Lights/scenes/Mundus Lights.scene` has spotlight entity 4 with an empty LightComponent;
its companion README records the runtime and generation provenance, including the possible checkout/jar revision mismatch.
These sources do not establish native persisted beam fields. Use the authorized extension:
`LightComponent.light.coneAngle` (full degrees, default 45, finite and strictly between 0 and 180) and
`LightComponent.light.edgeSoftness` (fraction, default 0.2, finite in [0,1]), or the same keys directly in
an existing flat LightComponent. Resetting defaults omits only those keys. Mundus rendering and preservation of
the extension remain unverified; the inspected transient implementation cannot establish round-trip support.

### 2. Separate spot lights and preserve entity identity

Carry entity id, kind, direction, range, cone angle and softness to the selected light set. Use stable entity identity to associate shadow records with lights; never associate solely by mutable list index. Keep the current two-directional and five-local-light ceilings, with point and spot sharing the local budget. Select local lights nearest the active view target with stable id tie-breaking; directional selection should be reconciled with the existing nearest-target requirement rather than silently retaining intensity sorting.

Pure `SpotCone` math uses cos(outerHalfAngle) and cos(innerHalfAngle), where innerHalfAngle = outerHalfAngle * (1 - softness). Smooth interpolation gives full intensity inside the inner cone and zero outside the outer cone; handle softness zero separately to avoid division by zero. Range limits must apply consistently to models and terrain. Rotations define the beam axis using SceneContent's existing forward convention.

### 3. Bounded shadow atlas and independent visibility

Use one packed-depth 2D atlas with depth attachment to avoid adding one sampler per light and to avoid a GL30-only texture-array dependency. Atlas: 4096 square, at most sixteen views. Small light sets use larger tiles: one view gets 4096 square; two to four get 2048; five to nine get 1365; larger sets get 1024. The grid grows when capacity requires it and remains stable until no shadow lights remain. Memory and maximum depth views stay bounded. Allocate at most one directional light, two point lights (six tiles each), and three spotlights, subject to the existing lighting ceilings. Stable selection and tile assignments avoid flicker. Unsupported or overflow lights still illuminate without shadows.

Directional lights use an orthographic map fitted to a bounded camera-visible receiver region, including relevant off-screen casters. Snap its projection to texel increments. Point lights use six 90-degree perspective faces and radial depth; receiver face selection and depth comparison must use the same convention. Spotlights use one perspective view matching the full cone angle and range. A separate tile allocator and projection math operate on CPU data without Swing, platform or GL.

Each light contribution is multiplied by its own visibility before summing. Ambient, HDR diffuse/specular environment terms and emissive output remain unshadowed. Atlas sampling treats points outside coverage as lit and clamps each tap to tile texel centers. Four bilinear PCF samples (sixteen comparisons) interpolate shadow visibility, with receiver-plane depth gradients correcting individual taps and a small scale-aware numerical bias. Packed depth uses base-255 encoding matched to RGBA8 quantization; depth rendering disables color dithering and sRGB output. Filtering is fixed; spotlight edge softness does not control shadow penumbra.

Alternative: one cubemap per point light. Simpler sampling, but sampler pressure and more resource bindings alongside PBR/terrain textures. Alternative: shadow every supported light every frame. Rejected for excessive depth passes on the AWT thread. Atlas limits and resolutions are proposed implementation defaults, not user-selected quality settings.

### 4. One displayed geometry snapshot per frame

Update loaded models, animation and terrain transforms once before both shadow and color passes. Render identical posed and transformed geometry in each pass, including drag previews. Depth shaders in gdx-model must support its custom mesh/index path, skinning and alpha-tested cutouts. Alpha-blended materials do not cast in this first version but can receive shadows; document this limit. Terrain uses a depth path compatible with its mesh and world transform.

Frame order:

    update geometry and lights --> allocate/fit shadows --> render depth tiles
               --> restore main framebuffer and viewport --> draw sky/grid/content/overlays

The grid, sky and overlays never enter the caster set. gdx-model owns reusable model depth shaders and a shadow environment attribute; the plugin orchestrates per-view resources and terrain integration. Keep the existing legacy shadowMap path usable for plain library consumers; the new atlas path must not apply it again or combine unrelated light visibility.

### 5. Lifecycle and threading

CPU validation, cone math, deterministic allocation and projection fitting are plain classes usable in headless tests. Properties edits run on EDT via editSceneJson. GPU allocation, shader compilation, all passes and disposal occur only on the AWT rendering thread inside GdxRuntime.withContext while GuardedGLCanvas.glSafe permits rendering. No shadow work enters AssetCache.prepare.

Shadow resources belong to the canvas context. Release on safe disposal; abandon references when the context is abandoned, then rebuild on create. Restore framebuffer, viewport, scissor, culling, depth and blending state even after failure. Use runCatchingKeepingCancellation for fallible work that can be cancelled. Log a failed shadow configuration once and fall back to unshadowed lighting, retrying only after a meaningful context/configuration change.

## Risks / Trade-offs

- Up to sixteen depth views plus the normal pass can be expensive → cull casters per view, skip empty tiles and bound shadow distances; measure in runIde before raising budgets.
- Atlas consumes video memory → one per context with fixed limits and prompt disposal; no global cache.
- Texture unit pressure from terrain/PBR → inspect actual sampler counts and GL limits before choosing the atlas unit; never overlap 2D and cube samplers.
- Acne, detached shadows and cube-face seams → projection/radial-depth CPU tests plus GL images for all six point faces, tile edges and grazing angles.
- Directional coverage trades distant detail for stability → bounded receiver fit and texel snapping; camera motion checks.
- Add-light-entities says cone controls are out of scope → revise its planning statements during implementation to refer to this change, preserve its creation/range scope and avoid duplicate work.
- Mundus may drop extension fields → document that limitation and verify with a real saved scene when available; do not promise cross-editor persistence.

## Migration Plan

No automatic file migration. Missing beam fields use defaults; scene opening writes nothing. Returning a field to its default removes only its extension key. Existing scenes gain shadows without edits. Rollback may leave extension fields in files, which older Abyssus versions must carry as unknown data; verify preservation with codec tests.
