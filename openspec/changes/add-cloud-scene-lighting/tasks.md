# Tasks

Starts after `add-sky-clouds` is applied. Manual checks use a copy of `Untitled` (never the fixture itself). GL tests
need `-Dabyssus.glTests=true`. Single plugin tests: `./gradlew :test --tests '<class>'`.

## 1. Linear lighting cube and environment build

- [ ] 1.1 Add `atmosphere_linear.frag` and a cube renderer for the atmosphere plus the shells clouds into an `RGBA16F`
  64² cube. Verify with the opt-in `ProceduralLightingCubeGlTest`: a high sun gives a bluer zenith than horizon,
  overcast is greyer and more even, and the values are linear (no tone curve).
- [ ] 1.2 Split `HdrEnvironmentBuild` into `EquirectSource` / `CubeSource` and the existing steps. Verify that the
  `core` HDR tests and `SceneRenderGlTest` HDR images are unchanged, and add a `CubeSource` build case.
- [ ] 1.3 Add `LightingRefreshPolicy` (1° sun threshold, 2 s cloud period, 1 s fade, no rebuild while hidden). Verify
  with `LightingRefreshPolicyTest`.
- [ ] 1.4 Add `ProceduralSkyLighting` with current and next environments, read `lightsScene` in `prepare`, and make
  `SceneAmbient.of` treat a lit procedural sky like an HDR sky. Verify with `SceneEnvironmentTest`:
  - `skybox_physical` without `lightsScene` keeps the ambient color;
  - with it, the sky lights the scene;
  - the scene keeps the ambient color while the environment builds.

## 2. Cross-fade in the content shaders

- [ ] 2.1 Add an optional second environment and blend weight to `gdx-model`'s `EnvironmentLightAttribute`,
  `DefaultShader` and `PbrShader`. Verify with `EnvironmentLightAttributeTest` and an opt-in GL image test: weight 0
  and 1 equal single-environment images, and 0.5 lies between them.
- [ ] 2.2 Add the same fade to `terrain.frag` / `TerrainShader`. Verify with the opt-in `TerrainSkyFadeGlTest`.
- [ ] 2.3 Do runIde check 1 on a copy of `Untitled` with `lightsScene: true`:
  1. A clear noon sky gives blue sky light.
  2. `builtin:overcast` gives greyer, even light.
  3. Rotating entity `7` to sunset gives warm light.
  4. The changes fade without jumps.
  5. Removing `lightsScene` returns the original look.

## 3. Cloud shadows

- [ ] 3.1 Add `DirectionalVisibilityAttribute` to `gdx-model` (texture, light index, world-to-map), sampled by
  `DefaultShader` and `PbrShader` only for that light and multiplied with its atlas shadow. Verify with a
  `gdx-model` opt-in GL test (only the indexed light is attenuated) and `./gradlew :gdx-model:check` (no plugin
  imports).
- [ ] 3.2 Add `CloudShadowMap` (512² `R16F`, 4 km orthographic along the sun, centered on the orbit target, using
  `clouds_common.glsl`) with failure fallback to uniform dimming. Verify with the opt-in `CloudShadowMapGlTest`: the
  map matches `CloudField` transmittance at sample points within 1e-2, wind moves the pattern, and a forced allocation
  failure falls back.
- [ ] 3.3 Sample the map in `terrain.frag` and set `SunOcclusion`'s uniform scale to 1 inside the area, with an
  edge fade to uniform dimming outside it. Verify with `SunOcclusionTest` (inside the area the scale is 1) and the
  opt-in `CloudShadowSceneGlTest`:
  - a terrain patch under a cloud is darker from the sun only;
  - the spot light is unchanged;
  - a model shadow and a cloud shadow combine without going below the no-sun level;
  - no cloud shadows without clouds.
- [ ] 3.4 Do runIde check 2 on a copy of `Untitled` with `builtin:fair`:
  1. Patches drift across the terrain along the wind.
  2. They match the clouds overhead.
  3. `Spot Light 8`'s area keeps its spot light.
  4. `Model 0`'s shadow still shows.

## 4. Environment for other features and docs

- [ ] 4.1 Expose `ProceduralSky.environmentCube` on demand. If `add-realistic-water` is still open, add a task and a
  delta scenario there for clouds in reflection fallbacks. If `add-scene-raytracing` is still open, add a task and a
  delta scenario there for clouds in ray misses and cloud shadow visibility. Verify with
  `openspec validate <change> --strict` for each amended change, and with a `ProceduralSkyTest` case that the cube
  builds without `lightsScene` when requested.
- [ ] 4.2 Document `lightsScene`, the cloud shadow extent and units, and the custom-shader lighting limitation in
  `docs/ai/file-formats.md`, `docs/ai/architecture.md`, the sceneview README, the user section of `README.md` and the
  changelog. Verify with `scripts/check-docs.sh`.

## 5. Integration

- [ ] 5.1 Verify that `./gradlew check` passes and that the GL tests pass with `-Dabyssus.glTests=true`.
- [ ] 5.2 Verify that `openspec validate add-cloud-scene-lighting --strict` passes, and that no file in the runIde
  copies was written by the plugin (`git status` on the copy).
