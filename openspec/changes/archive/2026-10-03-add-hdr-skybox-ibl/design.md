# Design

> **Moved by `extract-core-asset-module`.** The loading code this change added now lives in the `core` module
> (`core/src/main/kotlin/net/nevinsky/abyssus/assets/`): `RadianceDecoder` (with the header reading of
> `RadianceHeader`), `HdrSkyFiles`, `EquirectProjection` (was `Equirect`), `ToneCurve` (was `HdrToneMap`),
> `HdrPreview`, `HdrSkyLoader`, `HdrEnvironmentBuild` and `HdrSky` in `assets.sky.hdr`; `SkyLoader` in `assets.sky`;
> the `hdr_*` and `hdrsky.*` shaders in `core/src/main/resources/shader/sky/`; their tests and `HdrFixtures` in
> `core/src/test`, run with `./gradlew :core:test`. Paths below are where the code was when each task was done;
> `SceneSkybox`, `SceneRenderer`, `TerrainShader`, `SceneAmbient` and `SceneRenderGlTest` stay in the plugin.

## Context

See proposal.md - Why. This change lands after `add-procedural-sky` and builds on what it leaves:

- `SkyLoader` dispatches on `meta.json` `type` to a per-kind loader and wraps the result in `PreparedSky`;
  `SceneSkybox` draws whichever kind was built. A procedural sky is drawn as a fullscreen triangle whose
  per-pixel view direction comes from the inverse view-projection.
- Assets load through `AssetLoader`: `prepare` on a pool thread with no GL, then `upload` called once per frame on
  the render thread until it returns true, then `build`.
- The GL canvas is a core profile, GLSL `#version 150`; libGDX's GLSL 1.20 shaders run through the prefixes in
  `GdxRuntime` (`texture2D` and `textureCube` map to `texture`; there is no `textureCubeLod` mapping). `gl30` may be
  null.
- Scene ambient reaches models as `ColorAttribute.AmbientLight` in the shared `Environment`, which `gdx-model`'s
  `DefaultShader` and `PbrShader` fold into a six-color ambient cubemap (`u_ambientCubemap[6]`). Terrain is drawn by the
  plugin's own `TerrainShader`, which takes the ambient as one `u_ambient` color.
- Model shaders are cached per attribute mask, so an attribute that is present or absent selects a different program.

## Goals / Non-Goals

**Goals:**

- Every piece that can be computed without GL - Radiance decoding, header reading, downsampling, the
  equirectangular direction mapping, the tone curve, the choice of image in a folder - in plain classes in
  `sceneview/skybox` with no Swing, GL or platform, unit-tested headlessly (`docs/ai/conventions.md`).
- The GPU work split into steps of bounded cost, one per frame, so a large sky never freezes the IDE.
- A model drawn without a sky compiles to exactly today's shader.

**Non-Goals:**

- A CPU prefilter or spherical harmonics for the shaders. The cubes are built on the GPU.
- A BRDF lookup texture. The PBR shader keeps its existing analytic environment BRDF approximation.
- Sharing the environment between scene views. Each view's `AssetCache` builds its own.

## Decisions

### 1. Decode on the pool thread, streaming, into half floats

`RadianceDecoder` reads the header (`RadianceHeader`, also used alone by the chooser and the panel), rejects an
unsupported or oversized image from the header before allocating, then decodes scanline by scanline. When the image
is wider than 4096 it averages 2 x 2 blocks while reading, holding only two source rows at a time, so an 8192 x 4096
file never exists as floats in memory. The result is RGB half floats in a `ShortArray` (4096 x 2048 x 3 x 2 bytes =
48 MiB at most), the same layout the GPU upload takes.

**Alternative rejected:** decode to a full float array and downsample after. An 8192 x 4096 image would take 384 MiB
of heap in the IDE process for one step.

**Alternative rejected:** upload RGBE bytes and decode in a shader. It halves upload size but makes filtering wrong
(RGBE cannot be linearly interpolated) unless every sample does four fetches.

### 2. The image choice and the mapping are pure functions

`HdrSkyFiles.choose(folder listing, additional)` implements the spec's lookup order and returns the file plus an
optional warning. `Equirect` maps a direction to `(u, v)` and back: `u = 0.5 + atan2(d.x, -d.z) / 2π`,
`v = acos(d.y) / π`, so `u = 0.5` faces `-Z` and `v = 0` is straight up. The same formula is written once in GLSL, in
a shared include used by the background, the projection pass, and a GL test that checks it against the Kotlin one.

### 3. The background samples the equirectangular image directly

The background is the procedural sky's fullscreen triangle with a different fragment program: it maps each pixel's
view direction to `(u, v)` and samples the equirectangular texture at level 0, with linear filtering, `REPEAT` in `u`
and `CLAMP_TO_EDGE` in `v`. It then applies exposure 1.0, the Narkowicz ACES fit and gamma 1/2.2 (`HdrToneMap`, with
the same constants in GLSL).

Sampling the source image rather than the specular cube keeps the background at the image's full resolution; the
cube is 256 per face, which would visibly blur a 4096-wide sky. The texture has no mipmaps: `atan2` jumps at `u = 0`,
and mipmapped sampling would pick the smallest level along that column and draw a seam.

**Alternative rejected:** draw the background from the cube, as `SKYBOX` does. Simpler, but blurry at any useful
cube size, and a 2048-per-face cube to fix it costs 150 MiB of video memory.

### 4. The environment is built on the GPU, one step per `upload` call

`HdrSkyLoader.upload` advances a small state machine, each step one frame:

| Step | Work | Target |
|---|---|---|
| 1 | upload the half-float image as an `RGB16F` 2D texture | equirect texture |
| 2 | render each cube face from the equirect texture; `glGenerateMipmap` | specular cube, `RGB16F`, 256, 6 levels |
| 3-7 | prefilter level `n` = 1..5 for GGX roughness `n / 5`: 128 importance samples per texel, each read from the source mip chosen by its sample's solid angle, so a small bright sun does not alias into fireflies | specular cube level `n` |
| 8 | convolve irradiance: cosine-weighted hemisphere integral, read from the source cube's 32-pixel level | irradiance cube, `RGB16F`, 32 |
| 9 | read back the irradiance at the six axis directions with `glReadPixels` (6 texels) | six ambient colors |

Level 0 of the specular cube stays the unfiltered projection (roughness 0). Before step 1 the loader checks
`gl30 != null` and, in step 2, that the `RGB16F` framebuffer is complete; either failure ends the build as a failed
sky (spec: GPU cannot build the environment). `GL_TEXTURE_CUBE_MAP_SEAMLESS` is enabled once per context so mips do
not show face edges. The equirect texture is kept for the background; the intermediate framebuffer is disposed when
the build ends.

**Alternative rejected:** prefilter all levels in one frame. At 256 per face and 128 samples, the six levels take
long enough on an integrated GPU to stall the AWT thread visibly.

### 5. One environment attribute in `gdx-model`

`gdx-model` gains `EnvironmentLightAttribute` (in `core/shader`, no plugin imports): the specular cube, the irradiance
cube, the specular level count and the six axis colors. The renderer sets it on the shared `Environment` while an HDR
sky lights the scene and removes `ColorAttribute.AmbientLight` at the same time; when the sky stops lighting, it
removes the attribute and restores the ambient color. The two are never present together, which is how the sky
*replaces* the ambient color.

- `DefaultShader`: when the attribute is present, the six axis colors fill `u_ambientCubemap` in place of the ambient
  color. No new shader code; the existing cubemap blend does the rest.
- `PbrShader`: the attribute's presence adds `#define environmentLightFlag`, which compiles a path sampling the
  irradiance cube by normal and the specular cube by reflection at `roughness * (levels - 1)`, feeding the existing
  `ambient` and `ambientSpecular` terms. Absent, the program is today's, since the mask, and so the cache key and the
  source, are unchanged.
- `pbr.fragment.glsl` defines its own `textureCubeLodCompat`: `textureLod` when `__VERSION__ >= 130`, otherwise
  `textureCubeLod`, so it compiles in this plugin's core profile and in a plain GLSL 1.20 libGDX app.

**Alternative rejected:** a plugin-side PBR shader subclass. It would fork shader code that `gdx-model` owns, and other
libGDX users of `gdx-model` would not get the feature.

### 6. Terrain reads the irradiance cube

`TerrainShader.draw` gains an optional irradiance cube. `terrain.frag` gets `uniform samplerCube u_irradiance` and
`uniform int u_hasSky`, the same switch pattern as its `u_hasSplat`; with the sky, the ambient term is
`texture(u_irradiance, normal)` in place of `u_ambient`. One program serves both cases.

### 7. Previews share the decoder and the tone curve

The chooser's thumbnail and the panel's preview decode the `.hdr` on a pooled thread with the same `RadianceDecoder`,
downsampled while reading to the thumbnail width, then tone-map with `HdrToneMap` into a `BufferedImage`. The chooser
already fills its thumbnails after the dialog opens; the panel follows the same pattern.

### Threads

| Piece | Thread | GL / context |
|---|---|---|
| `RadianceHeader`, `RadianceDecoder`, `Equirect`, `HdrToneMap`, `HdrSkyFiles` | any | none |
| `HdrSkyLoader.prepare` | pool (`AssetCache.prepare`) | none |
| `HdrSkyLoader.upload` steps, background draw, environment set on `Environment` | AWT render thread, inside `GdxRuntime.withContext` | yes |
| chooser thumbnail, panel preview | pooled background thread, then EDT to show | none |

## Risks / Trade-offs

- **The background is tone mapped and the content is not.** A model lit by the sky looks brighter or flatter against a
  tone-mapped background than it will in a tone-mapped game. → Accepted and stated out of scope; content tone mapping
  is a change to every shader.
- **Video memory.** A 4096 x 2048 sky holds about 48 MiB of equirect texture plus about 2 MiB of cubes. → Bounded by
  the 4096-wide cap; released when the sky changes or the view closes.
- **Fixed exposure.** A sky captured much darker or brighter than about 1.0 average radiance looks too dark or blown out
  and lights the scene to match. → Accepted; the scene format has no exposure field.
- **Specular cube resolution.** 256 per face is soft for a mirror-like PBR material. → Accepted for a preview; the
  size is one constant.
- **Overlap with `add-procedural-sky`.** Both edit `SkyLoader`, `SceneSkybox` and the same requirements. → This change
  is applied after it, and its MODIFIED requirements are written against that change's text.

## Migration Plan

None. No file format change, no stored state, no new dependency. A project with no `SKYBOX_HDR` asset renders exactly
as before.
