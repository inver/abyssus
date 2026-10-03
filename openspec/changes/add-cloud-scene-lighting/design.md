# Design

## Context

See `proposal.md`, and `add-sky-clouds`' design, which this builds on. It provides:
- `CloudSettings` / `CloudField` (Kotlin) and `clouds_common.glsl`;
- `SunOcclusion`;
- `SkyFrame` with time and technique;
- `SunDirection.sunLight`.

**Existing machinery this reuses:**
- `HdrEnvironmentBuild` (`core`) turns an HDR equirect image into an `HdrEnvironment`: a specular cube with mips, an
  irradiance cube and six axis colors. It works one step per frame on the GL thread and restores the framebuffer and
  state.
- `SceneAmbient.of` picks the sky's environment, and `SceneRenderer.renderContent` swaps
  `ColorAttribute.AmbientLight` for `EnvironmentLightAttribute` after drawing the grid.
- `ShadowAtlasAttribute` (`gdx-model`) already carries per-light shadow visibility into `DefaultShader` and
  `PbrShader`. `terrain.frag` samples the same atlas.

**The user's `sky.frag` writes display-ready values.** The fixture's shader applies `1 - exp(-color * EXPOSURE)` and
gamma 2.2 inside the shader, so its output is not linear radiance and can't be inverted reliably, because the exposure
is a constant private to that shader.

## Goals / Non-Goals

**Goals:**
- Cloud shadows that match the clouds the sky shows and attenuate the sun only.
- Opt-in sky lighting through the existing HDR lighting path, changing smoothly.
- One cloudy-sky environment that other features can sample.

**Non-Goals:**
- Volumetric self-shadowing in the lighting cube.
- Per-pixel agreement between a custom `sky.frag` and the lighting.
- Cloud effects on point or spot lights.

## Decisions

### 1. Lighting comes from a plugin-owned linear atmosphere, not the user's shader
The lighting cube is rendered by `core`'s own `atmosphere_linear.frag`, the same single-scattering model as the
fixture shader and the `SkyAmbientEstimate` from `add-sky-clouds`, driven by the asset's `AtmosphereParams`, with the
`shells` cloud pass on top. It renders into an `RGBA16F` cube of 64² per face, in linear radiance, without tone
mapping.

The visible background still comes from the user's shader. A custom shader that departs from the parameters can
therefore look different from the light it gives; this is documented as a limitation.

Shells is always used for the cube, whatever the view's technique: it agrees on coverage with every technique by
`add-sky-clouds`' parity rule, and six faces of volumetric would be too expensive.

*Alternative:* capture the user's shader and invert the tone map. Rejected: the exposure constant is unknown, and the
values are clipped.

### 2. `HdrEnvironmentBuild` accepts a cube source
`HdrEnvironmentBuild` is split into a source step and the existing prefilter and irradiance steps:
- `EquirectSource` is today's equirect upload and projection;
- `CubeSource` uses the cube from decision 1 directly.

`ProceduralSkyLighting` (in `core`, per view, owned by the `ProceduralSky` when `lightsScene` is true) keeps two
`HdrEnvironment`s, *current* and *next*:
- It rebuilds *next* when the sun direction changes by more than 1°, or every 2 s of cloud time while the bands have
  wind.
- The rebuild goes one build step per frame, as HDR skies already do.
- When *next* finishes, the two cross-fade over 1 s.

The fade happens in the content shaders through a blend weight on `EnvironmentLightAttribute`:
- `gdx-model` gains an optional second environment plus a weight.
- `terrain.frag` samples both irradiance cubes.
- The default shader's six axis colors are blended on the CPU.

Until the first build completes, the scene stays lit by its ambient color (spec: *Building in progress*).

`SceneAmbient.of` treats a `ProceduralSky` that has a built lighting environment like an `HdrSky`.

### 3. The cloud shadow map
`CloudShadowMap` (plugin, `sceneview/shadows/`, GL thread) renders a 512² `R16F` texture each frame. It is an
orthographic projection along the sun direction, centered on the orbit target, covering a 4 km square in world units
(the scene's units are taken as metres, as Mundus does).

Each texel holds the clouds' transmittance along the sun ray through every band. It is evaluated with
`clouds_common.glsl` at band mid-altitudes, the same low-frequency coverage that `SunOcclusion` uses, so the map and
the sky agree.

**Shaders:**
- `terrain.frag` and `gdx-model`'s default and PBR shaders multiply the sun light's contribution, and only that
  light's, by the map sample at the fragment's world position projected along the sun.
- `gdx-model` gets a generic `DirectionalVisibilityAttribute(texture, lightIndex, worldToMap)`. It names no clouds, so
  the library stays reusable.
- It multiplies with the light's atlas shadow, if any. A surface in both shadows loses the sun at most once, because
  both factors are 0 to 1.

**Area and uniform dimming:**
- Fragments outside the 4 km square use the uniform `SunOcclusion` scale instead, faded over the outer 10% of the
  square.
- Inside the square, `SunOcclusion`'s uniform scale is set to 1 (spec: *No double dimming*).
- `LightSet.applyTo` therefore receives the uniform scale for out-of-area fragments as a separate uniform.
- If the map can't be created, the view falls back to the uniform dimming everywhere and logs the reason once.

### 4. A shared cloudy-sky environment for reflections
The cube from decision 1 is exposed as `ProceduralSky.environmentCube` when clouds or lighting are on. It is built on
demand when a consumer asks, even without `lightsScene`.
- For `add-realistic-water`: reflection fallbacks beyond the budget sample this cube instead of the cloudless sky.
- For `add-scene-raytracing`: misses sample it, and the cloud shadow map is uploaded as a 2D texture for its sun
  visibility rays.
Both are amended through tasks in their own changes if they're still open. Otherwise follow-up tasks are recorded in
their main specs' owners.

### 5. Threading
- **GL thread inside `GdxRuntime.withContext`, only while `glSafe`:** the cube, the build steps, the shadow map and
  the cross-fade.
- **EDT:** the decisions about when to rebuild and fade weights live in the pure `LightingRefreshPolicy` (tested
  headless).
- **Pool thread:** nothing new. Parsing of `lightsScene` happens with the rest of the sky in `prepare`.

## Risks / Trade-offs

- Lighting differs from a custom `sky.frag` → documented; lighting follows the physical parameters, which the fixture
  shader shares.
- The extra cube rebuilds cost GPU time → rebuilds every 2 s at 64² per face, spread one step per frame and skipped
  while hidden.
- Adding cross-fade sampling to the content shaders costs one extra cube fetch → only while fading. The second
  environment is unbound otherwise, through a shader define.
- Cloud shadow aliasing at 4 km over 512² (about 8 m per texel) → clouds are soft, so bilinear sampling hides it; the
  extent and size are constants to tune in runIde.
- Units: the 4 km extent assumes metres. Scenes in other units get cloud shadows of a different apparent size. →
  documented; a scale field can be added later.
- Overlap with `scene-shadows` shaders and `design-review-refactor` → rebase onto whichever lands first, keeping
  `DirectionalVisibilityAttribute` independent of the atlas.

## Migration Plan

No migration. Without `lightsScene` and clouds, nothing changes. Rollback removes the attribute and passes;
`lightsScene` fields left in assets are ignored.
