# Tasks

Builds on archived `add-sky-clouds` (2026-10-09); its cloud field, assets and uniform raster sun dimming already exist.
All tasks below remain pending: those foundations do not implement this change's lighting cube or spatial shadows.
Manual checks use a copy of `projects/plugin-abyssus/src/test/testData/project/Untitled`, never the fixture itself.
GL tests require a display and `-Dabyssus.glTests=true`. Use module-scoped test tasks, for example
`./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneEnvironmentTest'`.
New test names below are planned coverage, not claims that those classes already exist.

## 1. Linear lighting cube and environment build

- [x] 1.1 Add `atmosphere_linear.frag` and a separate linear shell-cloud output path in `lib-core`, sharing existing
  coverage/radiance math but bypassing `cloudToneMap`, to render a 64²-per-face RGBA16F cube. Verify with planned
  `ProceduralLightingCubeGlTest`: linear values, blue clear zenith, greyer overcast, unchanged visible cloud output.
- [x] 1.2 Generalize `HdrEnvironmentBuild` for equirect and cube sources; separate HDR background ownership from reusable
  lighting results. Verify existing `HdrEnvironmentGlTest` / plugin `SceneRenderGlTest` and a cube-source case covering
  borrowed texture ownership, successful transfer and disposal after failure.
- [x] 1.3 Add pure `LightingRefreshPolicy` (1° sun threshold, 2 s drifting-cloud period, 1 s fade, hidden-view pause).
  Verify planned `LightingRefreshPolicyTest` covers cloud reload/visibility revisions, stationary clouds, obsolete
  build rejection, coalesced requests and bounded current/next/build state.
- [x] 1.4 Stage strict-boolean `lightsScene` through `ProceduralSkyMeta`, `PreparedProceduralSky` and the loader; add
  `ProceduralSkyLighting`, then expose completed lighting through `SceneSkybox.environment` / `SceneAmbient.of`.
  Verify `ProceduralSkyLoaderTest` and `SceneEnvironmentTest` cover absent/false/invalid opt-in, disabled sky/ambient,
  first-build ambient fallback, refresh failure retaining the last environment, live cloud reload and context restore.
- [x] 1.5 Document `lightsScene`, defaults and the custom-shader limitation in `docs/ai/file-formats.md` and the
  lighting ownership/refresh path in `docs/ai/architecture.md`; verify with `scripts/check-docs.sh`.

## 2. Cross-fade in the content shaders

- [x] 2.1 Add an optional second environment and blend weight to `lib-gdx`'s `EnvironmentLightAttribute`, `DefaultShader`
  and `PbrShader`; blend default-shader axis colors on the CPU. Verify `EnvironmentLightAttributeTest` covers copy,
  equality and shader compatibility, and a planned GL image test checks weights 0, 0.5 and 1 plus the non-fading path.
- [x] 2.2 Add the irradiance fade to plugin `terrain.frag` / `TerrainShader`. Verify planned `TerrainSkyFadeGlTest`
  covers both endpoints, intermediate light and restoration of ambient lighting; document the fade in the sceneview README.
- [ ] 2.3 Perform runIde check 1 with `./gradlew :plugin-abyssus:runIde -PideProject=<copy>`: enable `lightsScene`,
  compare clear noon with an overcast CLOUDS asset, make entity `7` the brightest usable rendered directional light,
  rotate it to sunset, observe gradual changes, then remove `lightsScene` and confirm the original ambient look.

## 3. Cloud shadows

- [x] 3.1 Add generic `DirectionalVisibilityAttribute` in `lib-gdx` with texture, directional index, world-to-map,
  edge fade and outside-area fallback. Sample it only for that light in `DefaultShader` / `PbrShader` and multiply
  its atlas visibility. Verify a planned GL test isolates the indexed light and `./gradlew :lib-gdx:check` passes.
- [x] 3.2 Add plugin `CloudShadowMap` (512² R16F, 4 km extent, centered on orbit target) using the existing shared
  coverage, wind, spherical band intersection, column/extinction and sun-transmittance floor. Verify planned
  `CloudShadowMapGlTest` matches `SunOcclusion.instant` within 1e-2, tracks wind/reload, and handles allocation failure.
- [x] 3.3 Integrate terrain/model visibility using the unscaled sun while a map is active; retain `frameLights` uniform
  fallback otherwise. Resolve sun entity identity to the budgeted `LightSet` index; disable stale visibility when
  clouds disappear or drawing techniques are exhausted. Verify `SunOcclusionTest`, `SceneLightingTest` and planned
  `CloudShadowSceneGlTest` cover edge/outside fallback, no double dimming, missing sun, unchanged other lights and atlas composition.
- [x] 3.4 Document extent/metre units, fallback and resource lifecycle in `docs/ai/file-formats.md`,
  `docs/ai/architecture.md` and the sceneview README; verify with `scripts/check-docs.sh`.
- [ ] 3.5 Perform runIde check 2 with a fair CLOUDS asset: patches drift with wind and match overhead coverage;
  `Spot Light 8` remains unchanged, `Model 0` still casts its scene shadow, moving beyond the map is smooth,
  and disabling/removing clouds clears their shadows.

## 4. Environment for other features

- [x] 4.1 Expose an on-demand cloudy-sky environment independent of `lightsScene`, with explicit ownership and revision.
  Verify a planned `ProceduralSkyTest` case builds it without ambient replacement, refreshes on cloud reload/wind,
  releases it with the view and avoids building without a consumer. Document the consumer contract in `lib-core`'s README.
- [ ] 4.2 Reconcile the still-open `add-realistic-water` and `add-scene-raytracing` artifacts with cloudy reflection
  fallbacks, ray misses and sun visibility. Verify each amended change with `openspec validate <change> --strict`;
  if either has archived, record follow-up work against its owning capability instead.
- [x] 4.3 Extend `lib-core-editor`'s `RaySkyBaker` / `RayViewFeed` and plugin bake-cache integration for cloudy,
  revisioned snapshots; distinguish display samples from linear lighting and carry current procedural diffuse light.
  Verify `RaySkyBakerGlTest` and ray snapshot/diff tests cover cloud time/reload, no `lightsScene`, disabled sky,
  lighting fades, immutable publication and environment changes invalidating accumulation.
- [x] 4.4 Extend `lib-raytracing` contracts and fake/Metal/Vulkan backends for immutable cloud-sun visibility data,
  map transform, sun identity and outside-area fallback. Verify `RayBackendConformanceKit` cases cover moving cloud
  shadows, only the sun attenuated, no double dimming and unchanged non-cloud scenes; run native cases on supported hosts.
- [x] 4.5 Update the sceneview and `lib-core-editor` / `lib-raytracing` READMEs, user section of root `README.md` and
  `CHANGELOG.md` for procedural scene light, cloud shadows and reflection limitations. Verify `scripts/check-docs.sh`.

## 5. Integration

- [ ] 5.1 Verify `openspec validate add-cloud-scene-lighting --strict` and every coordinated open delta pass; verify
  rendering/technique changes have not written scene or asset metadata in the runIde copies by comparing them before/after.
- [ ] 5.2 Run `./gradlew check`, the affected module GL tests with `-Dabyssus.glTests=true`, and
  `scripts/check-docs.sh`; record any host-specific native backend checks still awaiting their supported machines.

## Apply verification (2026-10-10)

- Baseline `./gradlew check --console=plain` passed before implementation (GL opt-in off).
- Task 1.1: `./gradlew :lib-core:test --tests '*ProceduralLightingCubeGlTest' --tests '*CloudTechniqueGlTest'
  -Dabyssus.glTests=true --console=plain` passed: 7 tests, none skipped. The three `AssetLoadingGlTest` failures seen
  then (lowercase `cumulus`, `"splatBase": null` and the `anim-1` uuid fixtures) were repaired in the commit
  that followed; the class passes now.

## Apply verification (2026-10-11)

- `./gradlew check -Pabyssus.requireShaders=true` passes (`checkNoSingletons`, `checkNoRunCatching`, `checkPackageCycles`,
  `checkNoJolt`, tests), and `scripts/check-docs.sh` passes.
- GL tests (`-Dabyssus.glTests=true -Dabyssus.metalTests=true`) pass, none skipped, in `lib-gdx` (64), `lib-core` (258, 1
  skipped), `lib-core-editor` (427, 4 skipped) and `lib-raytracing` (251, 64 skipped: the Vulkan and timing cases).
  The new cases ran: `HdrEnvironmentGlTest`, `ProceduralSkyLightingGlTest`, `ProceduralSkyTest`, `EnvironmentFadeGlTest`,
  `DirectionalVisibilityGlTest`, `TerrainSkyFadeGlTest`, `CloudShadowMapGlTest`, `CloudShadowSceneGlTest`,
  `RaySkyBakerGlTest`, and the cloud cases of `RayBackendConformanceKit` on the fake and Metal backends.
- `:plugin-abyssus:test -Dabyssus.glTests=true`: 700 tests, 6 failed, 15 skipped. The six are `SceneRenderGlTest`
  `hdrHighlightsAreDistinct`, `aBlueSkyTintsTheTerrain`, `lightEntitiesChangeTheShading`, `aRedSkyReplacesTheAmbientOnAModel`,
  `hdrMidGreyDrawsAt140` and `pbrModelIsBrighterUnderAnUpperSkyThanUnderALowerOne`. They fail identically on the commit
  before this work (a null `ObjectNode` cast in the HDR-sky helper, and mid grey drawn at 26 instead of 140), so they
  are outside this change; none was changed or skipped. This is why 5.2 stays open.
- `openspec validate add-cloud-scene-lighting --strict` and `add-scene-raytracing --strict` pass. `add-realistic-water`
  has the cloudy fallback amended, but `--strict` still fails on a delta this change did not touch: its
  `object-properties-panel` MODIFIED "Panel follows the asset selection" omits the scenario "Scene selection retains
  its properties" that the current main spec has. Copying that scenario into the block fixes it; left for the
  owner of that change. This is why 4.2 and 5.1 stay open.
- Not run here, awaiting supported hosts: the Vulkan backend's cloud cases (`VulkanRayBackendTest`, needs a Vulkan
  ray-query GPU on Linux or Windows). `scene.comp` compiles with `glslangValidator --target-env vulkan1.2` and the SPIR-V
  passes `spirv-val`. Metal ran on arm64 only; not on an Intel Mac.

### Manual runIde checks still to do

Use a copy of `projects/plugin-abyssus/src/test/testData/project/Untitled` (`./gradlew :plugin-abyssus:runIde -PideProject=<copy>`).
Create the cloud assets with **New Weather Preset from Sky...** or by hand (a `CLOUDS` asset with the fair or overcast
bands), add its `uuid` as `"clouds"` to `skybox_physical`'s `additional`, and compare the scene and asset files with
the originals afterwards (5.1: nothing may have been written by rendering).
- **Check 1 (2.3):** add `"lightsScene": true`; compare a clear noon with the overcast clouds (grey, even light on `Model 0` and
  the terrain); make entity `7` the brightest directional light, rotate it to sunset and watch the light change over about
  a second without a jump; then remove `lightsScene` and confirm the original ambient look.
- **Check 2 (3.5):** with a fair `CLOUDS` asset, patches drift with the low band's wind and match the clouds overhead;
  `Spot Light 8` is unchanged where a patch passes; `Model 0` still casts its scene shadow; orbit more than 2 km away
  and the shade fades smoothly into the uniform dimming; disable or remove the clouds and the shadows disappear.
- Also try Ray Tracing on: the reflection of the sky shows the clouds, and the terrain shows the same cloud shade.
