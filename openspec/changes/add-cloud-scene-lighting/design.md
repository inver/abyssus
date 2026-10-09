# Design

## Context

See `proposal.md` and archived `openspec/changes/archive/2026-10-09-add-sky-clouds/design.md`.
The current sources provide:
- `CloudMeta` / `CloudField` (Kotlin) and `clouds_common.glsl`;
- `SunOcclusion`;
- `SkyFrame` with time and technique;
- `SunDirection.sunLight`.

**Existing machinery this reuses:**
- `HdrEnvironmentBuild` (`lib-core`) turns an HDR equirect image into an `HdrEnvironment`: a specular cube with mips, an
  irradiance cube and six axis colors. It works one step per frame on the GL thread and restores the framebuffer and
  state.
- `SceneSkybox.environment` currently returns only a built `HdrSky` environment; `SceneAmbient.of` selects it,
  and `SceneRenderer.renderContent` swaps
  `ColorAttribute.AmbientLight` for `EnvironmentLightAttribute` after drawing the grid.
- `ShadowAtlasAttribute` (`lib-gdx`) already carries per-light shadow visibility into `DefaultShader` and
  `PbrShader`. `terrain.frag` samples the same atlas.

**Current cloud and ray integration:** `ProceduralSkyMeta` / `PreparedProceduralSky` have no `lightsScene` field.
`ProceduralSky` reads its built `CLOUDS` dependency on every draw, so cloud reloads do not recreate the sky.
`SceneRenderer.applySunScale` makes `frameLights` via `LightSet.withSunScale`; terrain and models share that globally
dimmed raster set. The ray frame context receives the original `lights`. `RaySkyBaker` in `lib-core-editor` draws
`SkyFrame(..., clouds = false)` into an RGBA8888 target, and `SceneRenderer` caches the bake by sky name, project and
sun direction. Neither cloud time nor cloud-asset revision participates in that cache. No spatial cloud-shadow pass,
procedural lighting environment or environment cross-fade exists yet.

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

### 1. Lighting comes from an Abyssus-owned linear atmosphere, not the user's shader
The lighting cube is rendered by `lib-core`'s own `atmosphere_linear.frag`, the same single-scattering model as the
fixture shader and the `SkyAmbientEstimate` from `add-sky-clouds`, driven by the asset's `AtmosphereParams`, with a linear shell-cloud pass on top. It renders into an `RGBA16F` cube of 64² per face, in linear radiance, without tone
mapping.

The visible background still comes from the user's shader. A custom shader that departs from the parameters can
therefore look different from the light it gives; this is documented as a limitation.

Shells is always used for the cube, whatever the view's technique: it agrees on coverage with every technique by
`add-sky-clouds`' parity rule, and six faces of volumetric would be too expensive.

**Existing shell shaders also tone-map:** `clouds_shells.frag` calls `cloudToneMap` from `clouds_light.glsl`
inside its integration. Add a separate linear output variant sharing coverage, extinction, wind and radiance math;
compose radiance before tone mapping. Reusing the display cloud pass directly would produce non-linear lighting.
The existing visible sky/cloud output must remain unchanged.

*Alternative:* capture the user's shader and invert the tone map. Rejected: the exposure constant is unknown, and the
values are clipped.

### 2. `HdrEnvironmentBuild` accepts a cube source
`HdrEnvironmentBuild` is split into a source step and the existing prefilter and irradiance steps:
- `EquirectSource` is today's equirect upload and projection;
- `CubeSource` uses the cube from decision 1 directly.

`ProceduralSkyLighting` (in `lib-core`, per view, owned by the `ProceduralSky` when `lightsScene` is true) keeps two
lighting environments, *current* and *next*:
- It rebuilds *next* when the sun direction changes by more than 1°, or every 2 s of cloud time while the bands have
  wind.
- The rebuild goes one build step per frame, as HDR skies already do.
- When *next* finishes, the two cross-fade over 1 s.

The fade happens in the content shaders through a blend weight on `EnvironmentLightAttribute`:
- `lib-gdx` gains an optional second environment plus a weight.
- `terrain.frag` samples both irradiance cubes.
- The default shader's six axis colors are blended on the CPU.

Until the first build completes, the scene stays lit by its ambient color (spec: *Building in progress*).

`SceneSkybox.environment` exposes a completed opt-in procedural environment as well as HDR lighting, so
`SceneAmbient.of` can select it. Extend `ProceduralSkyMeta`, `PreparedProceduralSky` and the loader to stage a
strict boolean opt-in without GL calls.

`HdrEnvironment` currently owns a mandatory equirectangular background texture as well as lighting cubes.
Separate the reusable lighting result from that HDR background ownership; a cube-source build must not fabricate
an equirect image or dispose a borrowed cube. Preserve the HDR background, exposure, filtering and disposal path.
The first environment failure retains ambient lighting; later build failures retain the last completed environment.
Dispose failed/incomplete builds and superseded resources once. Invalidate on sky replacement, cloud-asset reload,
cloud visibility changes, project change and context loss, including stationary clouds with zero wind. Bound work
to current, next and one active build; coalesce newer requests and do not publish a result from an obsolete asset
revision. Pause refresh work while hidden and rebuild after context restoration.

### 3. The cloud shadow map
`CloudShadowMap` (plugin, `sceneview/shadows/`, GL thread) renders a 512² `R16F` texture each frame. It is an
orthographic projection along the sun direction, centered on the orbit target, covering a 4 km square in world units
(the scene's units are taken as metres).

Each texel holds the clouds' transmittance along the sun ray through every band. It is evaluated with
`clouds_common.glsl` at band mid-altitudes, the same low-frequency coverage that `SunOcclusion` uses, so the map and
the sky agree.

**Shaders:**
- `terrain.frag` and `lib-gdx`'s default and PBR shaders multiply the sun light's contribution, and only that
  light's, by the map sample at the fragment's world position projected along the sun.
- `lib-gdx` gets a generic `DirectionalVisibilityAttribute(texture, lightIndex, worldToMap)`. It names no clouds, so
  the library stays reusable.
- It multiplies with the light's atlas shadow, if any. A surface in both shadows loses the sun at most once, because
  both factors are 0 to 1.

**Area and uniform dimming:**
- Fragments outside the 4 km square use the uniform `SunOcclusion` scale instead, faded over the outer 10% of the
  square.
- Inside the square, `SunOcclusion`'s uniform scale is set to 1 (spec: *No double dimming*).
- Keep the original sun color in the lighting set while the map is active. Carry the out-of-area uniform scale
  separately to the shaders; `LightSet.withSunScale` must not dim that set as well. Continue using the existing
  `frameLights` path when no map is available.
- Resolve `SunDirection.sunLight` by entity id to the directional index in `LightSet`, which has a separate nearest-light
  budget. Never shade another directional light if the chosen sun is absent from that set; no usable directional
  light means no direct cloud shadow even though the background has a default sun direction.
- Share `SunOcclusion`'s spherical band intersection, column/extinction model, wind and transmittance floor with the
  GPU map; use the same cloud time and asset revision as the visible sky. Determine applicability from the built
  cloud asset and available drawing technique, so exhausted cloud techniques cannot leave stale cloud shadows.
- If the map can't be created, the view falls back to the uniform dimming everywhere and logs the reason once.

### 4. A shared cloudy-sky environment for reflections
The cube from decision 1 is exposed as `ProceduralSky.environmentCube` when clouds or lighting are on. It is built on
demand when a consumer asks, even without `lightsScene`.
- For `add-realistic-water`: reflection fallbacks beyond the budget sample this cube instead of the cloudless sky.
- For `add-scene-raytracing`: adapt the existing `RaySkySnapshot` / `RayViewFeed` path to transport cloudy-sky
  samples, procedural diffuse lighting and sun visibility. Ray backends consume immutable snapshots rather than GL
  textures: capture/read back on the render thread, then pass CPU data with sky/cloud revision, time, map transform,
  sun entity identity and outside-area fallback. Extend the contracts in `lib-raytracing` and verify fake, Metal and
  Vulkan backend behavior; there is no existing cloud-shadow texture contract to reuse.
- Keep display-ready background/reflection samples distinct from linear lighting radiance; the existing RGBA8888
  `RaySkyBaker` result is not a lighting source. Replace its cloudless bake/cache for cloud-aware consumers. Invalidate
  ray accumulation when a published environment or visibility snapshot changes; bound refresh frequency and exclude
  partially built or obsolete revisions.
Both are amended through tasks in their own changes if they're still open. Otherwise record follow-up work against their main capability owners. The current request revises only this
change; coordinated delta edits remain implementation tasks.

### 5. Threading
- **GL thread inside `GdxRuntime.withContext`, only while `glSafe`:** the cube, the build steps, the shadow map and
  the cross-fade.
- **AWT render thread / EDT:** evaluate the pure `LightingRefreshPolicy` from the per-view sky clock; it has no
  Swing, platform or GL dependency and is tested headless. Frame callbacks own all GPU work and fade weights.
- **Pool thread:** parsing of `lightsScene` happens with the rest of the sky in `prepare`; CPU snapshot conversion
  consumes immutable render-thread captures and never touches GL. Constructors inject collaborators; preserve the
  plain-JVM and singleton rules of `lib-core`, `lib-core-editor` and `lib-gdx`.

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
- The scene-shadow atlas and module refactor are already applied → integrate with their existing classes, keeping
  `DirectionalVisibilityAttribute` independent of the atlas.
- Animated cloud snapshots can reset ray accumulation frequently → publish only completed, revisioned snapshots
  on a bounded schedule and test accumulation invalidation with the existing ray environment conformance cases.

## Migration Plan

No migration. Without `lightsScene` and clouds, nothing changes. Rollback removes the attribute and passes;
`lightsScene` fields left in assets are ignored.
