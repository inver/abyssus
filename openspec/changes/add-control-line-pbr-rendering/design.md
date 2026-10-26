# Design

## Context

See `proposal.md` for the why. Observed state of the raster game:

- `FieldRenderer` draws the sky, then `TerrainPass` (`terrain_game.{vert,frag}`: one sun, one shadow map, flat ambient,
  albedo used as stored), then the models and foliage through a stock `ModelBatch` with `ModelShaderProvider`, then
  `ShapeRenderer` `GL_LINES` for the control lines and the debrief and guide tracks.
- `ModelShaderProvider` already picks `PbrModelShader` (`pbr.fragment.glsl`) for PBR materials; `pbr.fragment.glsl` supports
  `EnvironmentLightAttribute` (irradiance and prefiltered specular cubes, with a fade) but the game never sets it. The
  plugin's `SceneContentPass` does, from `HdrEnvironment`s that `ProceduralSky.acquireEnvironment()` leases (lib-core).
- Every lit shader says "linear, no gamma, no tone mapping": textures are sampled as stored (sRGB-encoded) and the result is
  written as is. `hdrsky.frag` has its own tone map and gamma 2.2 (same constants as `HdrToneMap.kt`).
- The window has a 4x multisampled default framebuffer on a 3.3 core context (`Main.kt`); there is no offscreen HDR target.
- Scene values (sun intensity 1, ambient 0.4) are artistic multipliers, not photometric units.

Constraints that shape the approach: the editor view must stay byte-for-byte unchanged (it shares the shaders and `lib-render`
terrain code); `lib-gdx`, `lib-core` and `lib-render` stay plain JVM libraries wired by constructors; a shipped game carries
no editor code; Jolt and ray tracing are not touched.

## Goals / Non-Goals

**Goals:** a linear-light pipeline for the game with one display transform, sky light on every surface, terrain and foliage in
the same lighting model as models, and cable geometry for the control lines, all off for every other host.

**Non-Goals:** an HDR post chain (bloom, SSAO, TAA, auto-exposure), physical photometric units, new PBR terrain materials
(roughness or normal maps for terrain layers), editor parity, line dynamics, a quality menu.

## Decisions

### 1. Per-shader output transform, not a post pass

The transform is a GLSL include `output_transform.glsl` (exposure, tone map, sRGB encode, sRGB decode helpers) called as the
last line of each fragment shader the game draws with, behind a define that only exists when the transform is on.

- *Why:* the back buffer is 4x MSAA on the default framebuffer. A post pass needs a multisampled HDR target plus a resolve,
  a new full-screen pass and memory, all for a game that has no other post effect. Per-shader keeps one draw path and the
  editor path compiles to the same code as today (no define, no change).
- *Alternative:* HDR FBO + full-screen tone map. Rejected for now; correct MSAA resolve of HDR would be better on very bright
  edges (the sun and its glints). Cost accepted: edge pixels average tone mapped values. It can be added later without
  changing the specs (the transform is the same maths).
- *Alternative:* `GL_FRAMEBUFFER_SRGB` only. Rejected: it gives the encode but no tone map, and it is not uniformly available
  on the 3.3 core context across drivers.

### 2. How the setting reaches shaders

`OutputTransform` (exposure plus a fixed tone map; a plain data class) and its GLSL live in `lib-gdx` (`shader` package and
`shader/shared`), because `lib-core`'s sky and `lib-gdx`'s model shaders must use it and `lib-core` does not depend on
`lib-render`. `lib-render` carries it in `FrameInputs` and `TerrainPass` binds it. Model shaders read it from an
`OutputTransformAttribute` on the `Environment`, in the same way as `ClipPlaneAttribute`: the attribute's presence sets
`outputTransformFlag` in the program prefix, so programs with and without it are different shaders (a standard libGDX
attribute-mask difference). A host that never sets it compiles the old program.

- *Alternative:* a global uniform with a branch. Rejected: it changes every host's program and cannot meet "unchanged within
  1/255" by construction.

### 3. Colour linearisation is in the shader, not in the texture format

Under the flag, `diffuse`, `emissive`, vertex colour and fog colour are decoded from sRGB in the shader (the piecewise
sRGB function, not a bare 2.2). Data textures (normal, metallic-roughness, occlusion, splat weights) are not decoded.
Textures are not reloaded as `SRGB8_ALPHA8`.

- *Why:* textures are shared with the editor and cached by the shared `AssetStorage`; a format change would change the editor
  and the ray bridge's CPU snapshots. Shader decode costs a few ALU per fetch.
- *Trade-off:* texture filtering and mip generation then average encoded values (slightly darker minification than a sRGB
  texture format would give). Accepted and recorded.
- *Splat blends* (`terrain_game.frag`) mix layers in linear (decode each layer, then mix), which is the physically meaningful order.

### 4. Sky: one source for background, light and fog

The game leases the field sky's `HdrEnvironment` from `ProceduralSky.acquireEnvironment()` once per scene and calls
`updateLighting` each frame, as the plugin does. The leased environment goes to models as `EnvironmentLightAttribute` and to the
terrain as its irradiance ambient (diffuse only; sky specular on terrain is out of scope). The fog colour is the scene's fog
colour as authored (decoded); the clear colour and the sky's horizon follow it as today.

- Sky draw: each sky type is handled to write **linear** radiance when the transform is on and to be transformed once:
  procedural/atmosphere skies (linear already inside), cube skies (decode), HDR EXR skies (skip their private tone map and
  gamma). Task 4.1 lists which of the three the bundled `skybox_physical` and `weather_skybox_physical` assets are and checks
  each of them against this table before code is written.
- When the sky does not opt in (`lightsScene` false) or the first build is not finished, models and terrain use the flat
  ambient as today. The environment fades in over 1 s (existing behaviour of `LightingRefreshPolicy`); there is no visible
  pop.
- *Alternative:* lease nothing and build a game-side cube. Rejected: duplicates `lib-core`.

### 5. Exposure and the scene's numbers

The scene's `intensity` stays a colour multiplier. The game's exposure is a code setting (`FieldRenderSettings.exposure`)
with a default chosen so the bundled field reads as bright midday; the value and the way it was chosen are recorded in the
game's README. Sun, sky and ambient values in `Field.scene` are re-tuned in the project where the albedo change makes the
field too dark or too bright, as ordinary scene edits through Abyssus (tests assert on other fields of that scene, so only
the light values change). No file format change.

### 6. Terrain and foliage

- `terrain_game.frag` gets: decode of layer colours, sky irradiance (a `samplerCube`, optional, like the editor variant),
  the include, the flag. The `TerrainLighting.Sun` input gains an optional irradiance cube; absent means flat ambient.
- Foliage is drawn by `ModelBatch` through the same model shaders (instanced variants of `ModelShader`/`PbrModelShader`), so
  it follows from decision 2; the task verifies that the instanced program picks the flag.
- The sun's shadow stays one map from `FieldShadows`; no change.

### 7. Control lines as cable

A pure `CableGeometry` (game module, no GL) turns a polyline plus a world radius and a minimum-pixel-radius rule into tube
vertices and indices (6 sides, ring per point, normals from the path, smooth normals at joints). A `CableMesh` owns a `Mesh`
with a fixed capacity and rewrites its vertices each frame without allocating, and is drawn with a PBR material (dark steel,
roughness about 0.4, metallic 0.9, tuned in the look pass) through the same `ModelBatch` and `Environment`, so it is lit by
the sun, the sky and the shadow and goes through the output transform by decision 2. It does not cast a shadow (a 0.3 mm
line gives no readable shadow at 20 m and doubles the depth pass work).

- Radius: `max(lineDiameter / 2, pixelRadius)`, where `pixelRadius` is the world size of 0.75 px at the cable's distance for
  the current field of view and viewport height. Thin lines therefore stay visible and do not shimmer.
- The input is a polyline (`List<Vector3>`, 2 points now), so the later dynamics change can pass more points.
- Debrief and guide tracks and the ray mode view keep the existing `GL_LINES` overlay: they are display-space overlays, not
  scene geometry. `FieldView.draw` gains `cables`; `LineSegment` stays for overlays and ray mode.
- *Alternative:* screen-space ribbon shader. Rejected for now: needs its own program and does not share the PBR lighting.

### 8. Threads and tests

All of this runs on the game's render thread inside the libGDX application loop (no IDE, no `GdxRuntime`). Pure and
headless: tone map and sRGB maths (a Kotlin mirror used as the reference), `CableGeometry`, light-profile and
`FrameInputs` plumbing, the boundary and singleton checks. GL tests (`-Dabyssus.glTests=true`): a unit sphere under the
sun, a flat terrain against a model of equal albedo (parity within 5%), the editor-off equivalence (old programs unchanged
within 1/255), and one frame of the field. What only a person can judge (does it look right) is a numbered manual check list.

### 9. Performance budget

Baseline first (task 1): steady GPU and CPU frame time of the pilot view on this machine at its default resolution, loading
excluded. Budget proposed: not more than 20% above that baseline. The check is recorded in the game's README with hardware
and resolution; an unmeasured speed-up is never claimed. The 4x MSAA setting is kept.

## Risks / Trade-offs

- [Decoding albedo changes the whole field's look] → exposure and scene light values are retuned in one pass with before/after
  screenshots committed under the build screenshot tooling; the spec scenarios use measurable properties (no clipping,
  distinguishable surfaces), not a fixed look.
- [Editor view changes by accident] → flag-gated code, an equivalence GL test over PBR, default, terrain and foliage programs,
  and no edit to a shared default.
- [Double transform where passes overlap, e.g. sky under terrain] → each pass writes final colours over its own pixels; a
  test draws a frame and checks that a sky-only pixel equals one transform of the sky colour.
- [MSAA resolve of tone mapped values darkens bright edges] → documented; HDR FBO is the later fix (decision 1).
- [Ray mode diverges visually from raster] → by design, ray mode is unchanged and says so in the README.
- [Sky lighting not ready at the first frame] → flat ambient fallback and the existing fade (decision 4).
- [Cable shimmer or cost] → minimum pixel radius, fixed 6 sides, one dynamic mesh of about 100 vertices.

## Migration Plan

No file migration. Landing in order (tasks 2 to 6) keeps the game working: the flag is off until task 5 turns it on in the
game. Rollback is one setting (`FieldRenderSettings.outputTransform = null`) restoring the old path, which stays tested for
the editor in any case. The ray and raster default launch paths are unchanged.

## Open Questions

- The exact tone map curve (Narkowicz ACES fit or a neutral curve such as Khronos PBR Neutral) is chosen in task 2.2 by looking
  at the sun-lit asphalt and sky; the spec only needs a monotonic, 0-preserving curve, so either fits.
