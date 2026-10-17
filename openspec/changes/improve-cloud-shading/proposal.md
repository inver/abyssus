# Proposal

## Why

Clouds are a headline feature of the scene view, but their shared lighting is a first approximation: thick cloud
interiors fall to a hard-coded floor, cloud surfaces have little depth (no powder), the phase function's forward lobe is weak, and far clouds fade to one flat horizon color. The result looks flat next to the atmosphere behind
it. All three cloud techniques (`layered`, `shells`, `volumetric`) share that lighting, so improving it improves every
sky that draws clouds at once.

## What Changes

- Rework the shared cloud lighting (`clouds_light.glsl`) used by every technique:
  - **Multiple scattering**: the sun transmittance becomes a sum of scattering octaves, so thick clouds stay luminous
    and soft instead of clipping to a constant floor.
  - **Phase**: an energy-conserving dual-lobe Henyey-Greenstein (the existing 0.6 / -0.25 lobes become 0.72 / -0.2) with a stronger forward lobe, giving the sun-side
    silver lining and a more directional look.
  - **Powder**: a per-sample term that darkens thin, near-surface cloud edges, the "cauliflower" depth cue.
  - **Ambient**: a stronger underside occlusion and height-aware sky light, so cloud bases read darker than tops.
  - **Aerial perspective**: clouds toward a low sun take a warm haze tint, matching the atmosphere at sunset.
- Volumetric clouds additionally dither their sun light-march offsets per pixel and frame, so light shafts resolve
  through the existing temporal filter without banding.
- The same terms are applied by `clouds_layered.frag`, `clouds_shells.frag` and the linear (`CLOUD_LINEAR_OUTPUT`)
  shells used by the sky lighting cube, so all techniques stay visually consistent.
- No change to coverage, band geometry, wind, noise or technique selection: clouds stay in the same places at the same
  altitudes (the existing "same sky, three techniques" contract).
- No native file is read or written, no document field changes, no `formatVersion` change, no UI change.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `scene-sky-clouds`: the "Clouds look like the sky they are in" requirement gains concrete lighting expectations:
  thick clouds stay luminous (the soft multiple-scattering look), thin sunlit edges are darker (depth), the sun side
  glows (silver lining), and all three techniques share the same lighting model.

## Impact

- Shaders: `projects/lib-core/src/main/resources/shader/sky/clouds_light.glsl` (shared lighting functions),
  `clouds_volumetric.frag`, `clouds_shells.frag`, `clouds_layered.frag`. `clouds_common.glsl` and the coverage field
  are untouched, so `CloudField`/`CloudFieldParityGlTest` parity is unaffected.
- Rendering: `ProceduralSky` (all three techniques), `ProceduralLightingCube` (`LinearShellClouds`), and therefore
  `ProceduralSkyLighting` colors change (thin clouds gain up to 1.75x direct light from the multiple-scattering sum, which the lighting cube reads linearly); cloud shadow mapping (`cloud_shadow.frag`, `SunOcclusion`) is
  untouched.
- Tests: `CloudTechniqueGlTest` gains assertions for the new look; existing agreement, overcast, sunset and fallback
  cases must keep passing.
- Docs: cloud descriptions in `docs/ai/file-formats.md`, `docs/ai/architecture.md` and `projects/lib-core/README.md`
  updated only if their wording becomes wrong.

**Out of scope:** new cloud techniques, changes to cloud shape/coverage/noise, per-band lighting parameters in
`meta.json`, an authoring UI for shading, stars/moon, and any change to the frame-budget/fallback behavior.
