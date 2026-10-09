# Tasks

Manual checks use a copy of `Untitled` (never the fixture itself) with a `CLOUDS` asset (copied from
`projects/lib-core/src/main/resources/clouds/templates/`) whose `uuid` `skybox_physical`'s `clouds` names.
Single plugin tests: `./gradlew :plugin-abyssus:test --tests '<class>'`. `core` tests:
`./gradlew :lib-core:test --tests '<class>'`.
GL tests need `-Dabyssus.glTests=true` and a display.

## 1. Cloud description and the cloud asset (core, headless)

- [x] 1.1 Add `CloudType` (band and defaults), `CloudBand`, `CloudBandLimits` and `CloudMeta`, and a
  `CloudMetaReader` that validates bands and logs skipped ones. Verify with `CloudMetaReaderTest`:
  - cumulus defaults (base 800, top 2000, coverage 0.4, density 0.8);
  - a missing `technique` means `shells`;
  - no bands means nothing to draw, and the fair, overcast and storm templates are valid cloud assets;
  - a wrong-band type, base not below top, an out-of-band altitude and a non-numeric value each skip only their band.
- [x] 1.2 Add `MetaType.CLOUDS`, `CloudsLoader` (prepare reads the technique and bands and makes the 3D noise with
  `CloudNoiseGenerator` on `FastNoiseLite`; build uploads it into `Clouds`) and the fair, overcast and storm templates
  in `core`'s resources. Verify with `CloudsLoaderTest` (bands and technique, a bad band skipped and logged once, no
  noise without bands, an unreadable asset), `CloudNoiseTest` (sizes, full range, every face tiles, deterministic) and
  `./gradlew :lib-core:checkNoSingletons`.
- [x] 1.3 Make `ProceduralSkyLoader` resolve `additional.clouds` (a `uuid`) to a folder through `AssetIndex` in
  `prepare` and name it in `dependencies`; `ProceduralSky` reads the built `Clouds` from `BuiltAssets` on every draw.
  Verify that `ProceduralSkyLoaderTest` keeps passing for a sky without `clouds` (spec: *Fixture sky stays cloudless*),
  with cases for a cloud dependency found by `uuid`, an unknown `uuid` logged once, and a `clouds` value of the wrong
  kind; and with the opt-in `AssetLoadingGlTest.aSkyLoadsItsCloudAssetFirstAndDrawsFromIt`.
- [x] 1.4 Document `clouds`, the band limits and `CLOUDS` in `docs/ai/file-formats.md` and `core/README.md`.
  Verify with `scripts/check-docs.sh`.

## 2. Shared coverage field and sun occlusion

- [x] 2.1 Add `CloudField` in Kotlin (integer-hash gradient fbm per type, coverage remap, height profile, wind offset
  with float-safe wrapping). Verify with `CloudFieldTest`: golden values at fixed points and times, wind translation,
  coverage 0 is empty and 1 is full, and output deterministic across runs.
- [x] 2.2 Write `clouds_common.glsl` with the same field. Verify with the opt-in `CloudFieldParityGlTest`, which
  renders a grid of points to a float target and compares against `CloudField` within 1e-3.
- [x] 2.3 Promote the test-side `AtmosphereModel` (`core/src/test/.../procedural/AtmosphereModel.kt`) to main as
  `SkyAmbientEstimate`, cached per sun direction. Verify with `AtmosphereModelTest` (moved) and a new case: zenith
  bluer than horizon at noon, warmer horizon at sunset.
- [x] 2.4 Add `SunOcclusion` and `SunDirection.sunLight`, and apply the per-frame sun scale in
  `SceneRenderer.applyLights` / `LightSet.applyTo` and `TerrainShader`. Verify with:
  - `SunOcclusionTest`: a cloud on the sun ray dims it, a clear ray gives 1, the storm floor is 0.1, smoothing over
    about 0.5 s, and no clouds gives 1;
  - `SunDirectionTest`: `sunLight` picks the same light as `of`;
  - `SceneLightingTest`: only the sun's entry is scaled, and spot/point/ambient are unchanged.

## 3. Cloud techniques (GL)

- [x] 3.1 Change `Sky.draw` to take a `SkyFrame(sun, timeSeconds, technique)`, and update `SkyboxCube`, `HdrSky` and
  `SceneSkybox`. Add a per-view `SkyClock` with a fixed-time test hook. Verify with `./gradlew :core:test` and
  `SceneRenderGlTest` (cube and HDR images unchanged) with GL tests on.
- [x] 3.2 Add the `CloudRenderer` strategy and `LayeredClouds` (premultiplied blend over the atmosphere, band spheres
  from `planetRadius`, high-to-low order, HG and Beer-Lambert lighting with `SkyAmbientEstimate`). Verify with the
  opt-in `CloudTechniqueGlTest.layered`:
  - no clouds leaves the image identical to the cloudless sky;
  - coverage 1 covers the zenith;
  - a sunset sun warms the clouds;
  - a model drawn after the sky stays in front.
- [x] 3.3 Add `ShellClouds` (8 slices, darker bases, parallax). Verify with `CloudTechniqueGlTest.shells`. The test
  compares the cloud mask with `layered`'s and requires at least 90% of covered pixels to agree (spec: *Same sky,
  three techniques*).
- [x] 3.4 Add `VolumetricClouds`:
  - 3D noise textures read from the cloud asset (`Clouds`), drawing failing over to shells without them;
  - a half-resolution `RGBA16F` target;
  - jittered ray-march, reprojection history with the reset rules, bilinear upsample;
  - abandon and dispose like other GL resources.
  Verify with `CloudTechniqueGlTest.volumetric` (mask agreement with layered, history reset on a camera jump, no
  history leak after resize) and `RenderLoopTest` hide/show.
- [x] 3.5 Add the failure chain (volumetric to shells to layered to none, logged once). Verify with
  `CloudTechniqueGlTest.fallbackChain`, which injects a failing build per technique.
- [ ] 3.6 Tune the default values per type. Do runIde check 1 on a copy of `Untitled`:
  1. Cloud assets made from the fair, overcast and storm templates.
  2. A three-band sky with all three techniques.
  3. A sunset sun.
  4. Ten seconds of drift.
  Record chosen defaults in `docs/ai/file-formats.md`.

## 4. View controls and fallback

- [x] 4.1 Add `CloudViewState` and `CloudFrameBudget`. Verify with:
  - `CloudFrameBudgetTest`: 2 s over 33 ms triggers, gaps over 250 ms are ignored, the budget holds within limits;
  - `CloudViewStateTest`: *Asset* resolves to the asset's technique, the first fallback goes to shells, choosing
    volumetric again is allowed, the second fallback is sticky, and a new state starts at *Asset*.
- [x] 4.2 Add the toolbar Clouds combo and the inline fallback note to `SceneViewPanel` (bundle strings), disabled
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

## 5. Cloud assets in the tree, unused marking and the chooser

- [x] 5.1 Add the cloud asset icon to `AssetIcons` and `NodeIcons`. Verify with `NodeIconsTest`.
- [x] 5.2 Add a sky's `clouds` `uuid` to the asset references the unused walk follows (`ProjectAssetListing`).
  `show-project-assets` is archived, so this change's `cloud-assets` requirement states the rule. Verify with
  `ProjectAssetsTest`: a cloud asset used through a sky, and one only an unused sky names.
- [x] 5.3 Add the `procedural sky · clouds` detail line to `SkyboxChoices`. Verify with `SkyboxChoicesTest`
  (a `uuid`, a blank value, an object and no `clouds`).
- [x] 5.4 Update `docs/ai/architecture.md` (the cloud pass, `SkyFrame`, threads), the sceneview README, the user
  section of `README.md` and the changelog. Verify with `scripts/check-docs.sh`.

## 6. Integration

- [ ] 6.1 Verify that `./gradlew check` passes and that the GL tests pass with `-Dabyssus.glTests=true`.
- [ ] 6.2 Do runIde check 3 on a copy of `Untitled`:
  1. A cloud asset `clouds_storm` named by `skybox_physical`; neither is marked unused.
  2. The chooser shows `procedural sky · clouds`.
  3. A cloud passing the sun dims entity `7`'s lighting while `Spot Light 8` is unchanged.
  4. `git status` on the copy shows no file written by the plugin.
- [x] 6.3 Verify that `openspec validate add-sky-clouds --strict` passes.

## 7. Cloud assets in other open changes

- [x] 7.1 Amend `add-cloud-scene-lighting`: its scenarios and runIde checks name cloud assets made from the templates
  instead of `builtin:` presets. Verify with `openspec validate add-cloud-scene-lighting --strict`.
- [ ] 7.2 Amend `add-weather-preset-creation`, which creates `WEATHER_PRESET` folders from a sky's resolved inline
  bands: neither exists any more. Decide what it creates now (a `CLOUDS` asset from a template, or from a sky's cloud
  asset) and rewrite its proposal, design and `weather-presets` delta against `cloud-assets`. Verify with
  `openspec validate add-weather-preset-creation --strict`.
