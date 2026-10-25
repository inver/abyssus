# Design

## Context

Clouds are drawn per band by a `CloudRenderer` chosen from the `CloudTechnique` enum in `lib-core`
(`ProceduralSky` builds it, `CloudTechniques` caches it and falls back through `simplerThan`). `layered` draws one flat
layer from 2D coverage only (`clouds_layered.frag`); `shells` stacks eight noise layers per band at full window size.
All techniques share `clouds_common.glsl` (the coverage field, with a Kotlin twin in `CloudField.kt`) and
`clouds_light.glsl` (sun, sky and haze terms), so they look lit by the same sun. Control Line forces `layered` through
`GAME_CLOUDS` in `FieldRenderer`, because `shells` left its 3.2 core context near 35 fps. See proposal.md for why.

## Goals / Non-Goals

**Goals:**
- A cloud look close to the reference photo at a cost near `layered`.
- Reuse the shared coverage field and lighting, so coverage and wind match the other techniques.

**Non-Goals:**
- No new coverage noise or change to `CloudField.kt` (its GL parity test stays untouched).
- No cloud shadows, no new band fields, no editor UI beyond one toolbar entry.

## Decisions

1. **A new `CUMULUS` enum value and `clouds_cumulus.frag`, not a tweak of `layered`.** Existing skies keep their
   look and the technique can be compared against `layered` in the editor. Alternative: retune `layered`; rejected
   because it silently changes every project's sky.
2. **One coverage sample, extra detail from a cheap erosion term.** The fragment samples `cloudBandCoverageLod` once,
   then derives puffiness from a single extra high-frequency `cloudFbmLod` octave pair eroding the coverage near its
   edge (ragged edges) and a dome-shaped height term from the coverage value (thick centre, thin rim). Alternative:
   several layers (that is `shells`); rejected on cost.
3. **Bright tops and grey bases from the sun angle, not a height march.** The lit side is found from the gradient of
   the coverage along the sun's horizontal direction (two extra coverage taps), brightening the sun-facing rim and
   darkening the side away from it, plus a base darkening that grows with the dome term. Taps use the same LOD
   footprint fade as `layered`, so distant clouds do not shimmer.
4. **Fallback order volumetric, shells, cumulus, layered.** `cumulus` is cheaper than `shells` and richer than
   `layered`, so `simplerThan(SHELLS)` becomes `CUMULUS` and `simplerThan(CUMULUS)` is `LAYERED`.
5. **Game switches by asset, not by override.** `clouds_fair` gets `"technique": "cumulus"` and `GAME_CLOUDS` is
   removed so `FieldRenderer` takes the asset's technique. If the measured frame rate is not clearly above `shells`,
   the task list keeps the override as a fixed `CUMULUS` instead.
6. **Atmosphere tuning is data only.** Deeper blue comes from `skybox_physical/meta.json` values
   (`betaRayleigh`, `sunIntensity`) and not from changing `sky.frag`. If values alone cannot reach the colour, the
   project's own `sky.frag` (a game asset, not shared) may change its exposure constant.

Testable pieces: the technique mapping and fallback are plain Kotlin (`CloudTechniques` unit test, no GL). The shader
is checked by a GL test (`-Dabyssus.glTests=true`) that renders a fixed camera and sun and asserts on pixels: the
brightest cloud pixels are near white, a darker base exists below them, and the sky outside clouds is bluer than before.
All GL work runs on the GL thread inside `GdxRuntime.withContext` as the other techniques do; picking and loading code is
unchanged.

## Risks / Trade-offs

- [Flat layer cannot show real thickness from below] → accepted; the look relies on shading, and `shells` remains for
  scenes that need parallax.
- [Look judged by eye against a photo] → the screenshot beside the photo is a task, and pixel asserts guard only the
  coarse properties (bright top, grey base, blue sky).
- [Frame rate gain not reached on the target machine] → measure first; keep decision 5's fallback.
- [New technique value unknown to older readers] → no migration by rule; documented in the cloud README.
- [Toolbar gains an entry despite "leave editor UI alone"] → required by the enum; the only UI change, one message key.

## Migration Plan

Additive. Existing assets keep their technique. Control Line's `clouds_fair` changes technique in the same commit as
the game code; reverting that commit restores `layered`.
