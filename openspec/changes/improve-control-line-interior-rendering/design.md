# Design

## Context

See proposal.md for the why. Current state that shapes the approach:

- `FieldRenderer` (game, `app-game-control-line/.../render`) draws into the default 4x MSAA framebuffer in this order: sky
  (display colour, depth off), sun shadow map (`FieldShadows`), terrain (`lib-render` `TerrainPass`, `GAME_SINGLE_SUN`), models,
  foliage and cables (`ModelBatch` + `lib-gdx` `ModelShaderProvider`), then lines (`ShapeRenderer`, `WireRenderer`). Every lit
  shader ends in `outputTransform()` (`lib-gdx` `shared/output_transform.glsl`) when `OutputTransformAttribute` is set.
- `FieldScene` keeps only the first directional light (`sunDirection` / `sunColor`, falling back to a white sun 45° up) and the ambient.
- The model programs already light with up to 256 point and 256 spot lights packed in a float texture (`LocalLightBindings`), fed from
  libGDX `PointLightsAttribute` / `SpotLightsAttribute`. The editor fills those from `LightSet`
  (`lib-core-editor/.../scene/SceneLighting.kt`), which the game cannot use (`checkShippedClasspath`).
- Sky lighting goes through `environment.setSkyLight(HdrEnvironment, …)` (`lib-render/light/SkyLight.kt`). `HdrEnvironmentBuild`
  (`lib-core/.../sky/hdr`) prefilters any `CubeSource` into irradiance and specular cubes.
- Emissive: `MaterialProcessor` (Assimp) reads only `AI_MATKEY_COLOR_EMISSIVE`, and `GltfWriter` writes only `emissiveFactor`. The import
  records `KHR_materials_emissive_strength` as skipped. libGDX `Color.set` clamps to 0–1, so the strength cannot be folded into the colour.
- Threads: the game renders on the LWJGL3 main thread, which owns the GL context. Nothing here runs in the IDE's `GdxRuntime`, except emissive
  strength in the shared model shaders, which runs where those shaders already run.

## Goals / Non-Goals

**Goals:** the hangar from its camera reads like the reference (lit pools under the lamps, glowing and blooming tubes, a reflective floor,
contact darkening), the Field with every effect off stays pixel-equal within 2/255, and the editor view is unchanged apart from emissive strength.

**Non-Goals:** a general render graph; deferred shading; changing `TerrainPass`'s lighting model; editor-side post effects.

## Decisions

### 1. Light conversion moves to `lib-core`; the game culls per model
The pure part of `lightSetOf` (filtering unusable lights, intensity × colour, cone half-angle and softness, nearest-first order) moves to
`lib-core` as a plain function over the `LightComponent` / `PositionComponent` values. Editor `LightSet` and the game call the same code.
The libGDX `Environment` filling stays at each host. *Alternative:* re-implementing it in the game, rejected because the editor and the
game would drift (spec: "read the way the editor's scene view reads them").

Per model, the game keeps the lights whose range sphere meets the instance's world bounds (from `ModelCulling`), ranked by estimated
contribution at the nearest point of the bounds (luminance × the shaders' range window ÷ (1 + d²)), capped at **64 point + 16
spot**. The cap is the lamps of a room, not a small number: the warehouse is one mesh that all 64 hangar lamps reach, so a smaller
cap would light the hall with an arbitrary subset. A small model gets only the few lights around it, and the model shaders skip a
light beyond its range before shading it (pixel-equal, as such a light contributes exactly 0), so the loop stays cheap. The selection is a pure `lib-render` function
(`LocalLightSelection`) with unit tests. Each instance is rendered with a per-instance `Environment` that shares the scene attributes and
holds only its selected lights. `LocalLightBindings` repacks only when the selection changes, so a static scene uploads once per instance.
*Alternative:* all 68 lights for every fragment, rejected for the GTX 750 Ti target. A new `LightProfile.GAME_SCENE_LIGHTS` names the budget;
the terrain keeps `GAME_SINGLE_SUN`.

No directional light: `FieldScene.sun` becomes nullable. With none, the sun light is black, `FieldShadows` is skipped and no shadow
attribute is set. Terrain gets a zero sun colour.

### 2. Emissive strength is a separate float attribute
A new `PbrAttributes.EmissiveIntensity` (float, default 1) becomes `u_emissiveIntensity` in `pbr.fragment.glsl` and `default.fragment.glsl`
under an `emissiveIntensityFlag`. A model without it compiles the same program as today, which keeps editor equivalence. `MaterialProcessor`
reads Assimp's `$mat.emissiveIntensity` (`AI_MATKEY_EMISSIVE_INTENSITY`, mapped from `KHR_materials_emissive_strength`). `GltfWriter`
writes the extension, and `extensionsUsed`, when the strength is not 1. The import stops listing it. The bundled warehouse is re-imported
from its GLB so its `model.glb` carries the strength.

### 3. HDR target with coverage alpha; the sky stays on the default framebuffer
`lib-render` `post.HdrFrameTarget`: a 4x MSAA FBO with an RGBA16F colour renderbuffer and a DEPTH24_STENCIL8 renderbuffer, resolved with
`glBlitFramebuffer` into single-sample RGBA16F colour and depth textures. Order per frame:

1. Default framebuffer: clear, then the sky as today (display colour).
2. HDR target: clear to (0,0,0,0). Draw terrain, models, foliage, cables and lines with a new `LinearTargetAttribute` / `linearTargetFlag`:
   inputs decoded exactly as under `outputTransformFlag`, but `outputTransform()` skipped. Opaque surfaces write alpha 1; blended
   ones use `glBlendFuncSeparate(…, ONE, ONE_MINUS_SRC_ALPHA)` so alpha holds coverage. Plain lines decode their colour from sRGB.
3. Resolve, then SSAO (decision 5), then bloom (decision 4).
4. Composite into the default framebuffer, with blending: `c = hdr.rgb / max(a, ε)`, then `out = grade(vignette(transform((c + bloom) · ao)))`,
   blended over the sky by `a`. Bloom where `a < 1` is added as display-space glow, `encode(transform(bloom))·(1−a)`, so lamps can glow over the sky.
5. UI and HUD after the composite, as today.

This keeps the spec's "display-referred sky shows as it is" without decoding the sky, and the per-shader path is unchanged for the editor
and the fallback. *Alternative:* draw the sky linearised into the target. Rejected: the tone map's shoulder (from 0.76) would change bright
sky pixels and break the sky scenario. *Alternative:* MSAA resolve in the composite shader (`sampler2DMS`). Rejected: it needs GL 3.2
texture-multisample, and blit is enough.

Fallback: `FieldRenderSettings.forContext` already drops the transform without GL 3.0. It also drops the post pass when
`GL_EXT_color_buffer_float` / a 3.0 float renderbuffer is missing or FBO completeness fails. The game then uses the per-shader transform
and logs the reason once.

### 4. Bloom: thresholdless dual-filter mip chain
Five levels down from half resolution, using a 13-tap downsample (Karis-average on the first level against fireflies) and a 3×3 tent
upsample, in RGBA16F textures that the pass owns. The result is mixed at `bloomStrength` (default 0.04) in linear light before exposure.
Thresholdless keeps it energy-plausible: only the lamps (emissive ≫ 1) visibly bloom. The mip sizes are a pure function with a unit test.

### 5. SSAO: half-res hemisphere, depth-only
Half-res, 12 samples in a view-space hemisphere with normals reconstructed from the depth texture, radius 0.5 m, a 4×4 noise
rotation, and a depth-aware 4×4 blur. Applied in the composite as `mix(1, ao, aoStrength)` and faded to 1 by luminance above 4 so emissive
stays unoccluded. *Alternative:* GTAO. Rejected for cost and complexity on the 750 Ti; it can replace this later behind the same setting.

### 6. Reflection probe for scenes without a sky
`lib-render` `ReflectionProbe`: after `FieldRenderer.loaded(field)` first returns true for a scene with no sky, render six 128 px
RGBA16F faces from the scene camera position (the hangar camera; the pilot for the Field, which never needs it). The faces hold static models
only, under the same lights, in linear (`linearTargetFlag`), without post. Then `HdrEnvironmentBuild(CubeSource)` → `HdrEnvironment` →
`environment.setSkyLight`. It is spread over three frames (two faces per frame) to avoid a hitch, and the flat ambient is used until it is done.

A scene gets a probe when it has no sky at all. A procedural sky that does not opt in to lighting cannot be told from one whose light
is not built yet, so such a scene keeps the flat ambient (a follow-up can read the sky's `lightsScene`). `FieldRenderSettings.reflectionProbe`
turns probes off.

Box projection: a `ProbeBoxAttribute` (world AABB of the static models plus the probe position) enables `envBoxFlag` in `pbr.fragment.glsl`,
which intersects the reflection ray with the box before sampling the specular cube. The math has a Kotlin reference with a unit test, as
`OutputTransform` has. *Alternative:* screen-space reflections. Out of scope and costly; the probe gives the dominant lamp streaks.

The warehouse's `WetConcrete` has roughness 1.0, so its reflection will be very blurry. The hangar retune (task group 7) first checks whether
the asset meta's `additional.materials` can override roughness. If not, the puddle mask from `add-after-rain-weather` provides the smooth
patches later. This is not a blocker for the spec scenarios, which only need brighter streaks.

### 6b. Light shadows in a scene without a sun
Added after the first look at plane select: with no sun, the hangar had no shadows at all, and then, with spot shadows, none on the
warehouse itself (its light is its lamps'). `FieldShadows.renderInterior` fills a separate 4096 map (the Field keeps its 2048 sun map
unchanged): the top-left quarter holds four 1024 tiles, one per spot light (the brightest first), drawn from all models every frame;
the rest holds 48 tiles of 512, the six cube faces of the eight point lights nearest the scene camera, drawn with radial depth from
the static models only and kept for the scene (the lamps and the building do not move; a plane in a lamp's light casts no lamp
shadow). The `ShadowAtlasAttribute` the model shaders already read for local lights matches each record to the libGDX light object
the models are lit with; the biases are the editor's. `FieldRenderSettings.localShadows` turns it off. In the hangar the spots became
the key light (the table spots intensity 10, the key spot 12) and the lamps a fill (intensity 3). The hangar's frame went from
p50 0.67 ms to about 2.3 ms (short smoke run), inside the budget.

### 7. Settings and budget
`FieldRenderSettings` gains `post: PostSettings?` (`bloom`, `bloomStrength`, `ao`, `aoStrength`, `vignette`, `grade`). `DisplaySettings`
gains `postQuality: HIGH | LOW`; LOW sets bloom and ao off, and the value is stored in the game's own settings file. `FrameSpan.POST` is added.
Budget on the recorded reference machine at 1280×720, loading excluded: the pilot view p50 ≤ 7.5 ms and p95 ≤ 11 ms (was 6.16 / 9.21); the
hangar p50 ≤ 7.5 ms. Measured (task 8.2; NVIDIA GeForce RTX 3070 Ti, Linux, CPU time of the whole frame, the harness's
defaults; `docs/perf/control-line-interior/{before,after}/control-line-linux.json`): the pilot view p50 4.16 → 4.91 ms, p95 6.91 →
7.51 ms (the `post` span 0.30 ms at p50); the hangar p50 0.65 → 0.67 ms, p95 1.15 → 0.89 ms (no sun shadow pass any more). Both are
within the budget. GPU time is not part of these numbers; on a GTX 750 Ti-class GPU the "High quality effects" switch drops bloom and
ambient occlusion.

### Testable without GL
`lib-core` light conversion, `LocalLightSelection`, bloom mip plan, box-projection reference math, `PostSettings` /
`DisplaySettings` round trip, `GltfWriter` emissive-strength round trip, import report (no skipped strength). GL tests (opt-in) cover pixels.

## Risks / Trade-offs

- [Composite over the sky gets MSAA edges wrong for un-premultiplied division at tiny alpha] → divide with ε = 1/255 and test edge pixels against the plain path (2/255).
- [Per-instance `Environment` churn allocates every frame] → keep one per instance slot, rebuilt only when its light selection changes.
- [Probe capture hitch on hangar entry] → three-frame split; plane select already holds a loading screen until `loaded`.
- [Shader variant growth: linearTarget × emissiveIntensity × envBox] → flags are prefix defines like `outputTransformFlag`; the GL equivalence test enumerates editor programs to prove they are unchanged.
- [Main spec `render-output-transform` is malformed (`## ADDED Requirements` header)] → task 1.1 repairs the header so this change's delta can be archived.
- [Removing the default sun darkens third-party scenes without a directional light] → called out as BREAKING in the proposal and the game README; the bundled Field has its sun.

## Migration Plan

Each task group ships on its own behind its setting; with `post = null` the game draws as today. Rollback is turning the setting default off.
The warehouse is re-imported once; the old `model.glb` simply lacks the strength.
