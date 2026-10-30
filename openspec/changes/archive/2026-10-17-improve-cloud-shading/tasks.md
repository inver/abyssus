# Tasks

## 1. Shared lighting model

- [x] 1.1 Rework `clouds_light.glsl`: the three-octave `cloudSunTransmittance`, the `mix(cloudHg(mu, 0.72),
  cloudHg(mu, -0.2), 0.25)` phase, the new `cloudPowder(density)` term, the stronger underside occlusion
  (`1 - 0.5 * underside`), and `cloudAerial(radiance, t, d)` with the sun-ward warm haze; add `CloudLightingGlTest`
  (opt-in GL, modeled on `CloudFieldParityGlTest`) evaluating the pure functions into a float target: transmittance
  strictly decreasing in tau with `T(0) >= 1.5` and `T(12) >= 0.01`, powder increasing with `powder(0) < 0.5` and
  `powder(1) > 0.7`, and `phase(1) > 3 * phase(0) > phase(-1) > 0`. Verify:
  `./gradlew :lib-core:test -Dabyssus.glTests=true --tests
  'net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudLightingGlTest'`.
- [x] 1.2 Update the three technique shaders to the new terms: `clouds_volumetric.frag` (powder from the marched
  sample's density), `clouds_shells.frag` (powder from the shell's density, including the `CLOUD_LINEAR_OUTPUT` path),
  `clouds_layered.frag` (powder from the column density), all passing the view direction to `cloudAerial`. Verify:
  `./gradlew :lib-core:test -Dabyssus.glTests=true --tests
  'net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechniqueGlTest'` (agreement, overcast, sunset warmth,
  lighting-cube, fallback and history cases all pass).
- [x] 1.3 Add `thickCloudsStayLuminous` to `CloudTechniqueGlTest`: an overcast low band at noon keeps its zenith
  luminance above a threshold calibrated so the pre-change shaders fail it (record that pre-change value in the test
  or its commit). Verify the case passes with the new shaders and fails with
  `git stash` of the shader edits.
- [x] 1.4 Add the remaining scenario cases to `CloudTechniqueGlTest`, one per spec scenario: `cloudSurfacesShowDepth`
  (scattered cumulus, sun low to the side: the darkest decile of covered pixels is at least 25% darker than the
  brightest decile), `silverLining` (low sun at sunset: covered pixels in the half of the frame around the sun are
  brighter and redder than in the opposite half), and `techniquesLightAlike` (layered, shells and volumetric of one sky:
  mean covered luminance within 25% of each other). Calibrate thresholds from the new shaders' output and record the
  measured values in the assertion messages. Verify the same `CloudTechniqueGlTest` command as 1.2.

## 2. Volumetric light march

- [x] 2.1 Dither the sun light-march offsets in `clouds_volumetric.frag` with the existing frame-shifted
  `cloudDither`, at most half a light step, (covered by the volumetric case plus a new
  `volumetricIsStableAcrossFrames` check: two consecutive frames after warm-up differ by a mean per-channel delta under
  4/255, so the dither does not shimmer). Verify:
  `./gradlew :lib-core:test -Dabyssus.glTests=true --tests
  'net.nevinsky.abyssus.lib.core.assets.sky.clouds.CloudTechniqueGlTest'` (volumetric agreement, history reset on
  turn, no leak after resize).

## 3. Docs

- [x] 3.1 Check the cloud wording in `projects/lib-core/README.md`, `docs/ai/file-formats.md` and
  `docs/ai/architecture.md` against the new shading (they name the shaders and the shared coverage field, not
  shading formulas); update anything the change makes wrong. Verify: `scripts/check-docs.sh`.

## 4. Integration checks

^- [x] 4.1 Run `./gradlew check` and the cloud GL suites the change touches:
  `./gradlew :lib-core:test -Dabyssus.glTests=true --tests
  'net.nevinsky.abyssus.lib.core.assets.sky.clouds.*'` and, for the untouched shadow paths,
  `./gradlew :plugin-abyssus:test -Dabyssus.glTests=true --tests
  'net.nevinsky.abyssus.plugin.sceneview.shadows.*'`.
- [x] 4.2 Manual `runIde` check on a copy of `projects/app-game-control-line/project/ControlLine` (never the fixture):
  1. volumetric clouds at noon look luminous with defined edges; 2. a low sun shows a warm glow on the sun-side
  clouds; 3. `Layered` and `Shells` overrides shade consistently with volumetric; 4. the volumetric frame-budget
  fallback still triggers with `-Dabyssus.clouds.simulateSlow` (if the fallback cannot be observed, leave this task
  open with these steps).
- [x] 4.3 Final gate: `./gradlew check` and `scripts/check-docs.sh`.
