# Tasks

Manual checks use a copy of `Untitled` (never the fixture itself) with `skybox_physical` given a `clouds` object.
Single plugin tests: `./gradlew :test --tests '<class>'`. `core` tests: `./gradlew :core:test --tests '<class>'`.
GL tests need `-Dabyssus.glTests=true` and a display.

## 1. Cloud description and presets (core, headless)

- [ ] 1.1 Add `CloudType` (band and defaults), `CloudBand`, `CloudBandLimits` and `CloudSettings`, and a
  `CloudSettingsReader` that validates bands and reports skipped ones through `AssetLog`. Verify with `CloudSettingsReaderTest`:
  - cumulus defaults (base 800, top 2000, coverage 0.4, density 0.8);
  - a missing `technique` means `shells`;
  - a missing `clouds` or `enabled: false` means no clouds;
  - a wrong-band type, base not below top, an out-of-band altitude and a non-numeric value each skip only their band.
- [ ] 1.2 Add `MetaType.WEATHER_PRESET`, `WeatherPresetReader`, constructor-injected `BuiltinPresets` (resources
  `clouds/builtin-*.json`) and band-by-band preset merging. Make `AssetFiles` reject folder names containing `:`.
  Verify with `WeatherPresetResolutionTest`: preset bands, a sky band replacing a preset band, a missing or unreadable
  preset logged once and falling back to the sky's bands, and the three built-ins loading.
  Also run `./gradlew :core:checkNoSingletons`.
- [ ] 1.3 Extend `ProceduralSkyAdditional` / `ProceduralSkyLoader.prepare` to read and resolve clouds on the pool
  thread. Verify that `ProceduralSkyLoaderTest` keeps passing unchanged for a sky without `clouds` (spec: *Fixture sky
  stays cloudless*), and add cases for enabled clouds and for a preset.
- [ ] 1.4 Document `clouds`, the band limits and `WEATHER_PRESET` in `docs/ai/file-formats.md` and `core/README.md`.
  Verify with `scripts/check-docs.sh`.

## 2. Shared coverage field and sun occlusion

- [ ] 2.1 Add `CloudField` in Kotlin (integer-hash gradient fbm per type, coverage remap, height profile, wind offset
  with float-safe wrapping). Verify with `CloudFieldTest`: golden values at fixed points and times, wind translation,
  coverage 0 is empty and 1 is full, and output deterministic across runs.
- [ ] 2.2 Write `clouds_common.glsl` with the same field. Verify with the opt-in `CloudFieldParityGlTest`, which
  renders a grid of points to a float target and compares against `CloudField` within 1e-3.
- [ ] 2.3 Promote the test-side `AtmosphereModel` (`core/src/test/.../procedural/AtmosphereModel.kt`) to main as
  `SkyAmbientEstimate`, cached per sun direction. Verify with `AtmosphereModelTest` (moved) and a new case: zenith
  bluer than horizon at noon, warmer horizon at sunset.
- [ ] 2.4 Add `SunOcclusion` and `SunDirection.sunLight`, and apply the per-frame sun scale in
  `SceneRenderer.applyLights` / `LightSet.applyTo` and `TerrainShader`. Verify with:
  - `SunOcclusionTest`: a cloud on the sun ray dims it, a clear ray gives 1, the storm floor is 0.1, smoothing over
    about 0.5 s, and no clouds gives 1;
  - `SunDirectionTest`: `sunLight` picks the same light as `of`;
  - `SceneLightingTest`: only the sun's entry is scaled, and spot/point/ambient are unchanged.

## 3. Cloud techniques (GL)

- [ ] 3.1 Change `Sky.draw` to take a `SkyFrame(sun, timeSeconds, technique)`, and update `SkyboxCube`, `HdrSky` and
  `SceneSkybox`. Add a per-view `SkyClock` with a fixed-time test hook. Verify with `./gradlew :core:test` and
  `SceneRenderGlTest` (cube and HDR images unchanged) with GL tests on.
- [ ] 3.2 Add the `CloudRenderer` strategy and `LayeredClouds` (premultiplied blend over the atmosphere, band spheres
  from `planetRadius`, high-to-low order, HG and Beer-Lambert lighting with `SkyAmbientEstimate`). Verify with the
  opt-in `CloudTechniqueGlTest.layered`:
  - no clouds leaves the image identical to the cloudless sky;
  - coverage 1 covers the zenith;
  - a sunset sun warms the clouds;
  - a model drawn after the sky stays in front.
- [ ] 3.3 Add `ShellClouds` (8 slices, darker bases, parallax). Verify with `CloudTechniqueGlTest.shells`. The test
  compares the cloud mask with `layered`'s and requires at least 90% of covered pixels to agree (spec: *Same sky,
  three techniques*).
- [ ] 3.4 Add `VolumetricClouds`:
  - 3D noise generated in `prepare` and uploaded on the GL thread;
  - a half-resolution `RGBA16F` target;
  - jittered ray-march, reprojection history with the reset rules, bilinear upsample;
  - abandon and dispose like other GL resources.
  Verify with `CloudTechniqueGlTest.volumetric` (mask agreement with layered, history reset on a camera jump, no
  history leak after resize) and `RenderLoopTest` hide/show.
- [ ] 3.5 Add the failure chain (volumetric to shells to layered to none, logged once). Verify with
  `CloudTechniqueGlTest.fallbackChain`, which injects a failing build per technique.
- [ ] 3.6 Tune the default values per type. Do runIde check 1 on a copy of `Untitled`:
  1. Fair, overcast and storm built-ins.
  2. A three-band sky with all three techniques.
  3. A sunset sun.
  4. Ten seconds of drift.
  Record chosen defaults in `docs/ai/file-formats.md`.

## 4. View controls and fallback

- [ ] 4.1 Add `CloudViewState` and `CloudFrameBudget`. Verify with:
  - `CloudFrameBudgetTest`: 2 s over 33 ms triggers, gaps over 250 ms are ignored, the budget holds within limits;
  - `CloudViewStateTest`: *Asset* resolves to the asset's technique, the first fallback goes to shells, choosing
    volumetric again is allowed, the second fallback is sticky, and a new state starts at *Asset*.
- [ ] 4.2 Add the toolbar Clouds combo and the inline fallback note to `SceneViewPanel` (bundle strings), disabled
  without enabled clouds. Verify with `SceneViewPanelTest`:
  - default *Asset*;
  - two views keep independent choices;
  - the combo is disabled for `skybox_default` and for cloudless skies;
  - after a simulated fallback the note is shown and the combo reads *Shells*;
  - no file changes.
- [ ] 4.3 Do runIde check 2 on a copy of `Untitled`:
  1. Switch the techniques in one view while a second view is open; only the first changes.
  2. Reopen the view; the choice resets to *Asset*.
  3. Force a slow volumetric (a large window on a weak GPU, or the `-Dabyssus.clouds.simulateSlow=true` debug flag
     added in this task); it falls back to shells twice, the second time sticky.

## 5. Presets in the tree, unused marking and the chooser

- [ ] 5.1 Add the weather preset icon to `AssetIcons` and `NodeIcons`. Verify with `NodeIconsTest`.
- [ ] 5.2 Add name references from a used sky's `clouds.preset` to the unused walk. If `show-project-assets` is still
  open, amend its delta spec's reference list and validate it with `openspec validate show-project-assets --strict`.
  Verify with `ProjectAssetsTest` cases: a preset used through a sky, a preset no sky uses, and a `builtin:` name that
  references nothing.
- [ ] 5.3 Add the `procedural sky · clouds` detail line to `SkyboxChoices`. Verify with `SkyboxChoicesTest`
  (enabled, disabled and absent clouds).
- [ ] 5.4 Update `docs/ai/architecture.md` (the cloud pass, `SkyFrame`, threads), the sceneview README, the user
  section of `README.md` and the changelog. Verify with `scripts/check-docs.sh`.

## 6. Integration

- [ ] 6.1 Verify that `./gradlew check` passes and that the GL tests pass with `-Dabyssus.glTests=true`.
- [ ] 6.2 Do runIde check 3 on a copy of `Untitled`:
  1. A preset folder `weather_storm` named by `skybox_physical`; neither is marked unused.
  2. The chooser shows `procedural sky · clouds`.
  3. A cloud passing the sun dims entity `7`'s lighting while `Spot Light 8` is unchanged.
  4. `git status` on the copy shows no file written by the plugin.
- [ ] 6.3 Verify that `openspec validate add-sky-clouds --strict` passes.
