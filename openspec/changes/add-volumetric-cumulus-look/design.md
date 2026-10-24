# Design

## Context

`VolumetricClouds` marches each band in `clouds_volumetric.frag` (48 steps, 6 light steps) at half resolution into an
`RGBA16F` target, with temporal reprojection (history blend 0.15, reset on a camera turn over 10 degrees), then composites
over the atmosphere. Density is the band's `cloudShape` profile times a base noise (already Perlin-Worley, the billowy
look of heaped clouds) and an eroding detail noise. The scene view steps volumetric down to shells when frames are slow
(`CloudControls`, editor only). Control Line currently draws the asset's technique with no step-down of its own. See
proposal.md for why; the in-flight `add-cumulus-cloud-technique` adds `cumulus` as the fallback below shells.

## Goals / Non-Goals

**Goals:**
- Heaped volumetric clouds that read as the photo: round heads, flat grey bases, bright tops, silver rim.
- The game runs them and protects its frame rate with its own step-down.

**Non-Goals:**
- No new noise textures, noise generator change or `meta.json` field; no change to the other techniques.
- No terrain shadows from clouds; no change to how a band's coverage is chosen.

## Decisions

1. **Shape in the shader, heaped bands only.** For `CLOUD_PROFILE_HEAPED` the density gets a height shaping that
   flattens the base (a sharp ramp in the lowest tenth) and lets the base noise lift heads upward (the noise pushes the
   top boundary instead of eroding evenly), with a stronger detail erosion near the surface for crisp edges. Other
   profiles keep today's code path, so cirrus and layered bands look as before. Alternative: new noise textures;
   rejected, the existing Perlin-Worley is the right kind of noise.
2. **Lighting: more of what exists.** Raise the silver-lining lobe for heaped bands (the existing phase function already
   has the forward lobe), and take the base's grey from a stronger "light from above" term (sky light dimmed by the
   density above). A cheap extra: scale the sun optical depth by a step count that is larger for the first light step,
   so shadows inside a head are crisp.
3. **Game step-down in `FieldRenderer`, not lib-core.** The renderer already owns the technique override: it keeps a
   rolling frame time, and after two seconds over 33 ms sets its override to the next technique from `simplerThan`
   (logging once). The decision is a small plain class (`FrameBudget` already exists in the editor; the game gets its own
   tiny one since the game must not depend on the editor). Alternative: reuse the editor's budget; rejected, the game
   ships no editor code (`checkShippedClasspath`).
4. **Asset switch.** `clouds_fair` becomes `"technique": "VOLUMETRIC"`. The game keeps coverage 0.33 and density 0.7 to
   start; they are tuned against the screenshot.
5. **Measure first.** The `FieldSkyGlTest` adds `VOLUMETRIC` and reports time per frame. If volumetric costs more than
   about 6 ms at 1280 x 720 on the development GPU, reduce the march steps for the game only through a uniform from the
   renderer (not a constant change) before accepting; record the numbers in the README.

Testable without GL: the step-down rule (frame times in, technique out) is a plain Kotlin class with a unit test. The
shader is checked by a GL test that renders a fixed heaped band: a bright top, a darker base, and an edge sharper than
shells (the share of mid-grey pixels along the edge). All GL work stays on the GL thread.

## Risks / Trade-offs

- [Temporal smoothing smears fast camera moves in a flying game] → the existing reset on a big turn; check by flying
  and by a test that moves the camera between frames and compares against a still frame.
- [Volumetric is the costliest technique and the game was GPU-bound with shells] → measure first, the step-down, and
  a march-step uniform if needed (decision 5).
- [Noise textures are generated at load, which can delay the first frame] → measure load time; the noise is already
  built off the GL thread for the cloud asset.
- [Plane silhouettes in front of half-resolution clouds can show a halo] → the composite already works over a depth-free
  sky, drawn before the terrain; verify in a screenshot with the plane in view.

## Migration Plan

Additive. Revert by setting `clouds_fair` back to `CUMULUS` and removing the step-down; no file format changes.
Apply `add-cumulus-cloud-technique` first.
