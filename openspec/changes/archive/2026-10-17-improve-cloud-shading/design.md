# Design

## Context

Every cloud technique draws with `clouds_common.glsl` (the coverage field, unchanged) plus `clouds_light.glsl`, which
holds the shared band uniforms and all lighting: `cloudHg`/`cloudPhase`, `cloudSunTransmittance`, `cloudRadiance`,
`cloudDistanceFade`, `cloudAerial`, `cloudToneMap`. `clouds_volumetric.frag`, `clouds_shells.frag` (and its
`CLOUD_LINEAR_OUTPUT` variant used by `ProceduralLightingCube`) and `clouds_layered.frag` call those functions with
values they compute per sample. See proposal.md - Why for the motivation.

Constraints that shape the approach:

- Cloud geometry, coverage and wind are contract ("same places", `CloudField`/GPU parity) and stay untouched.
- `cloud_shadow.frag` + `SunOcclusion` are a GPU/CPU pair for sun occlusion and stay untouched, so the change cannot
  move shadows. They do not include `clouds_light.glsl`.
- Shaders compile and draw on the GL render thread inside `GdxRuntime.withContext`; there is no CPU twin of the cloud
  lighting and this change adds none.
- The volumetric technique has a 33 ms frame budget with an automatic fallback to shells; extra per-step work must be
  kept to a few scalar operations.

## Goals / Non-Goals

**Goals:**

- A single, consistent lighting model shared by all three techniques: multiple scattering, a directional phase with a
  silver lining, powder (edge depth), stronger underside ambient occlusion, and sun-ward warm aerial haze.
- Keep the existing technique agreement, overcast and sunset properties.
- No new uniforms, no Kotlin changes, no file-format or UI changes.

**Non-Goals:**

- New cloud techniques, froxel/production-grade multi-scattering, light shafts from geometry, temporally reprojected
  shadows.
- Per-band shading parameters in `meta.json` or any authoring UI.
- Changes to coverage, shape profiles, noise, wind, or the cloud shadow map.

## Decisions

### 1. Lighting changes live in `clouds_light.glsl`, not per technique

All three fragment shaders already call `cloudRadiance`; changing it once gives the consistency the delta spec
requires. Alternatives (upgrade only volumetric; separate looks per technique) were rejected: layered/shells would
visibly diverge from volumetric, and `ProceduralLightingCube` uses the linear shells, so consistency also keeps the
scene lighting matching the visible sky.

### 2. Multiple scattering: a three-octave sum instead of a clipped single term

```glsl
float cloudSunTransmittance(float tau) {
    float total = 0.0;
    float contribution = 1.0;
    for (int o = 0; o < 3; o++) {
        total += contribution * exp(-tau);
        contribution *= 0.5;
        tau *= 0.5;
    }
    return total;
}
```

Each octave stands in for light scattered one more time: half the energy and half the extinction. This is the standard
cheap approximation (Frostbite / Wrenninge style). At low `tau` it lifts clouds toward luminous white; at high `tau` it
keeps roughly the old `0.25 * exp(-tau/4)` floor and adds softer terms, so thick bases stay dark but not clipped.

### 3. Phase: stronger forward lobe, weak back lobe

`cloudPhase(mu) = mix(cloudHg(mu, 0.72), cloudHg(mu, -0.2), 0.25)` - a mixture of two Henyey-Greenstein lobes, each of
which integrates to 1 over the sphere, so total energy is unchanged in form. The forward lobe now peaks about 2.3x
higher than before toward the sun (1.32 vs 0.57 at mu = 1; the old mix was 0.6 / -0.25 at 0.3) (silver lining and sun-side glow); the back lobe keeps anti-solar clouds visible.
Alternatives: a single lobe (too flat or too sharp), Cornette-Shanks (more realistic but with no back-scatter control
and similar cost), a Mie-like lobe (over-engineering here).

### 4. Powder applied to the direct sun term only

```glsl
float cloudPowder(float density) {
    return 0.35 + 0.65 * (1.0 - exp(-2.0 * density));
}
```

`density` is the sample's density with the band's `density` factor included (0..~1). Thin near-surface cloud gets
little direct sun (dark edges); dense cores keep most of it. The multiple-scatter and sky terms are not powdered, so
bases never go black. Per technique: volumetric passes the marched sample's density, shells the shell's density,
layered the column density.

Alternative considered: powder from the accumulated optical depth from the viewer (more physical). Rejected: with 48
coarse steps it suppresses all near-surface light, needs per-sample march state, and the density form delivers the same
depth cue at one `exp`.

### 5. Ambient and aerial

- `cloudRadiance` keeps the zenith/horizon mix and raises the underside occlusion from `1 - 0.4 * underside` to
  `1 - 0.5 * underside`, so bases read darker than tops.
- `cloudAerial(radiance, t, d)` gains the view direction: toward a low sun the haze picks up the sun's color,
  `u_sunlight * pow(max(dot(d, sun), 0.0), 6.0)`, on top of the existing `u_horizon * 1.5` fade. At noon the term is
  small and warm-colored; at sunset it is the horizon glow. Callers already hold `d`.

### 6. Volumetric: dither the sun light-march offsets

The march jitter (`u_frame`) already varies along the primary ray; the 6 light samples are at fixed offsets, which
bands under thick cloud. Offset them by `(cloudDither(pixel + frame * k) - 0.5)` steps along the sun direction. The
existing temporal accumulation (`clouds_resolve.frag`) averages the dither; the bake path averages `BAKE_SAMPLES`
already. No new uniforms (reuses `u_frame`).

### 7. Verification strategy

- `CloudTechniqueGlTest` covers technique agreement, overcast grey, sunset warmth, fallback and history. New cases:
  `CloudLightingGlTest` pins the pure functions (transmittance, powder, phase); `CloudTechniqueGlTest` gains
  thick-cloud luminance, cloud depth (lit sun side vs dark base), silver lining (sun-side vs anti-sun brightness at
  sunset) and cross-technique brightness agreement, one test per new spec scenario.
- No CPU-testable logic changes: all edits are GLSL; `CloudField` and `SunOcclusion` are untouched, so parity and
  occlusion tests are expected to pass unchanged.
- Visual tuning beyond the test properties is a `runIde` check on a project copy.

## Risks / Trade-offs

- [Multiple scattering + stronger forward lobe blow out cloud highlights near the sun] -> tone mapping compresses
  highlights; the overcast and sunset cases pin the acceptable range; octave weights and the forward-lobe weight are
  the tuning knobs if the GL tests show clipping.
- [Sunset warmth no longer passes the 1.2x ratio] -> the sun-ward aerial glow and warm `u_sunlight` tie cloud color to
  the atmosphere; the case runs in `CloudTechniqueGlTest`.
- [Volumetric light jitter adds temporal variance, shimmering through the history clamp] -> jitter amplitude is at most
  half a light step and the history blend already smooths per-pixel dither; the existing history reset/no-leak cases
  guard behavioral regressions.
- [`T(0)` is 1.75, so thin clouds and the linear lighting cube (no tone map) get brighter than before] -> expected and
  bounded by the octave weights; the `lightingCubeLeavesVisibleShellsUnchanged` and sunset cases run, and the cube's
  ambient shift is checked by eye in `runIde` (task 4.2). If it is too strong, scale the sum by 1/1.75 or lower the
  octave weights.
- [Extra `exp` per step costs frame time] -> one extra `exp` in the transmittance and one powder `exp`; the
  `CloudFrameBudget` fallback to shells is unchanged and remains the safety net.
- [Per-view look changes for every user with clouds] -> intended; no file or setting changes, so a revert is a shader
  revert.

## Migration Plan

No persisted state and no format changes; nothing to migrate. Rollback is reverting the four shader files.

## Open Questions

- Final constants (forward lobe strength, powder floor, glow power) are visual judgement. They can be adjusted during
  apply without changing the specs, the approach, or the task breakdown; the GL test properties are the guard rails.
