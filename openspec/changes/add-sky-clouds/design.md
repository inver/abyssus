# Design

## Context

See `proposal.md` for the motivation and the delta specs for the behavior.

**Today's procedural sky:**
- `ProceduralSkyLoader.prepare` (pool thread) reads `ProceduralSkyMeta` and the asset's `sky.vert` / `sky.frag`.
- `ProceduralSky` compiles the asset's GLSL and draws one fullscreen triangle per frame through
  `Sky.draw(camera, sun)`.
- `SceneSkybox.draw` turns depth testing, depth writes and culling off around it, before anything else is drawn.
- The sun direction comes from `SunDirection.of(lights)`, which picks the brightest usable directional light.
- `LightSet.of(lights, target)` builds the directional, point and spot lights that `SceneRenderer.applyLights` caches
  by `(lights, orbit target)`.

**The asset's shader is the user's.** The asset's GLSL computes the atmosphere and is not plugin code. Clouds must
therefore be a plugin-owned pass that works with any asset shader.

**One sky per view.** Each scene view gets its own `SceneAssets` caches from `AssetLoading.assets`, so every view
holds its own built `Sky` instance. Per-view cloud state (the technique, the volumetric history) can live with that
sky or with the view.

**Reloading.** Today a sky asset is not reloaded when its `meta.json` changes; the view must reopen. The open change
`add-asset-editing-and-terrain-generation` adds reloads of changed assets. This change relies on whatever reloading
exists and adds none.

**Unused marking.** The unused rule lives in the open change `show-project-assets` (`ProjectReader.usedAssets` walks
references by `uuid` from a root set of folder names). A sky names its cloud asset by `uuid`, so it is one more reference.

## Goals / Non-Goals

**Goals:**
- One cloud description and one coverage model shared by all three techniques and by sun occlusion.
- Techniques that can be switched per frame without reloading the asset.
- A volumetric technique that stays within a frame budget or steps down.
- Nothing written to any file.

**Non-Goals:**
- Exact equality between techniques' pixels. They agree on where clouds are, not on how they look in detail.
- Cloud shadows and sky lighting (`add-cloud-scene-lighting`).
- Clouds in water or ray-traced reflections.

## Decisions

### 1. The cloud description lives in `core`, parsed in `prepare`
Add `core/.../sky/clouds/`. Every type here is pure, immutable and unit-tested:
- `CloudSettings(technique, bands)`.
- `CloudBand(level, type, base, top, coverage, density, windX, windZ)`.
- `CloudType`: an enum with its band and defaults.
- `CloudBandLimits`.

**Clouds are an asset, loaded like a terrain's textures.** `MetaType` gains `CLOUDS`; its `additional` holds
`technique` and the bands. `CloudsLoader.prepare` (pool thread) reads them with `CloudSettingsReader`, which validates
the bands, skipping and logging invalid ones (spec: *Invalid band*), and makes the volumetric noise; `build` uploads the
noise as 3D textures into the built `Clouds`.

`ProceduralSkyMeta.clouds` stays a raw `JsonNode`, so a value of the wrong kind never fails the sky; a textual value is
the cloud asset's `uuid`. `ProceduralSkyLoader.prepare` resolves it to a folder through `AssetIndex` (an unknown `uuid`
is logged and the sky has no clouds) and returns it from `dependencies`, so the one `AssetStorage` loads the cloud asset
first. `ProceduralSky` reads the built `Clouds` from `BuiltAssets` on every draw, as `TerrainMesh` reads its splat
textures: a reloaded cloud asset is picked up without rebuilding the sky, and the sky never owns or disposes it.

*Alternatives:* inline bands in the sky with folder-named presets and built-ins (the first version of this change).
Replaced: one weather setup is shared by `uuid` like every other asset reference, the unused walk needs no name rule,
and the noise is made once per cloud asset instead of once per sky. Built-in presets are dropped; their metas stay in
`core`'s resources as templates for `add-weather-preset-creation`.

### 2. One coverage field, written twice (Kotlin and GLSL)
`CloudField` is the deterministic coverage model. For a band and a world point `(x, z)` at time `t`, it computes
`coverage(x + windX*t, z + windZ*t)`. That is a fixed-seed, integer-hashed gradient-noise fbm per type, remapped by
the band's `coverage`, then a height profile per type between `base` and `top`.

It is implemented once in Kotlin (`CloudField`, for sun occlusion and tests) and once in GLSL
(`core/src/main/resources/shader/sky/clouds_common.glsl`, used by every technique). Both use the same 32-bit integer
hash and float operations.

`CloudFieldParityGlTest` (opt-in GL) renders the field for a grid of points into a float target and compares the
result with the Kotlin values (tolerance 1e-3). That keeps "same places in all three techniques" and "the sun dims
where a cloud is" honest. The volumetric technique adds detail noise only inside the coverage the field allows, never
outside it.

*Alternative:* read coverage back from the GPU with a 1×1 `glReadPixels`. Rejected: it stalls the EDT pipeline.

### 3. Cloud drawing is a strategy inside the sky pass
The sky contract gains a frame description:
- `Sky.draw(camera, sun)` becomes `Sky.draw(camera, frame: SkyFrame)`.
- `SkyFrame(sun, timeSeconds, technique)`.
- Cube and HDR skies ignore the new fields.

`ProceduralSky` draws the asset's atmosphere as today, then blends clouds over it (premultiplied alpha) through a
`CloudRenderer` strategy: `LayeredClouds`, `ShellClouds` and `VolumetricClouds`. Each is built lazily the first time
its technique is requested and kept until the sky is disposed.

Bands are spheres at `planetRadius + altitude`, using the asset's atmosphere parameters, so clouds meet the horizon
naturally. The camera sits at the existing `CAMERA_HEIGHT` above the planet. Layers are drawn far to near (high, mid,
low), so lower bands hide higher ones.

**Lighting:**
- Henyey-Greenstein forward scattering toward the sun, plus Beer-Lambert extinction through the band's thickness.
- An ambient term from a small CPU estimate of the atmosphere's zenith and horizon colors. `SkyAmbientEstimate`
  computes them with single scattering from the same `AtmosphereParams` and caches them per sun direction.
- This gives warm sunsets and grey undersides without calling the user's shader.

**The techniques:**
- **Layered:** one ray-sphere hit at mid-band and 2D fbm coverage.
- **Shells:** 8 ray-sphere slices from `base` to `top`, with a height profile per type and darker bases.
- **Volumetric:**
  - Ray-marched in an offscreen `RGBA16F` target at half the view's framebuffer size.
  - A 64³ base noise (Perlin-Worley) and a 32³ detail noise (inverted Worley), both `R8`, from `FastNoiseLite`. It does
    not repeat, so a margin at each face fades into the copy shifted by one volume, which makes them tile. They belong
    to the cloud asset: generated in its `prepare` on the pool thread and uploaded as 3D textures through `Gdx.gl30`
    (available in the 3.2 core context) in its `build`.
  - 48 primary steps and 6 light steps, with blue-noise jitter.
  - Temporal accumulation with reprojection by the camera's previous view-projection matrix. The history resets when
    the camera jumps (look-through switch, resize, more than 10° of rotation in one frame).
  - Upsampled with bilinear filtering into the sky pass. The sky has no depth, so no edge-aware upsample is needed.

Per-view volumetric resources live in the sky instance, which is already per view. They follow `AssetCache.abandon`
and `dispose` like other GL resources.

*Alternative:* inject the cloud code into the user's `sky.frag`. Rejected: it couples three techniques to arbitrary
user shaders.

### 4. Per-view technique choice and the frame budget
`CloudViewState` is a pure class owned by `SceneViewPanel`, on the EDT. It holds the override (`ASSET`, `LAYERED`,
`SHELLS`, `VOLUMETRIC`), the fallback count and the sticky flag, and resolves the effective technique from the asset's
`technique`.

`CloudFrameBudget` is a pure class. It takes frame intervals: the wall time between consecutive rendered frames,
ignoring gaps over 250 ms (hidden, paused or loading views). It reports when a volumetric view has spent a continuous
two seconds over 33 ms per frame. The first report sets the override to *Shells*. The second sets it and makes it
sticky, and the combo then refuses *Volumetric* with a tooltip.

The note is a short label in the toolbar (`AbyssusBundle`), never a dialog. The toolbar combo is disabled when the
current sky names no cloud asset it can draw. The sky reports `hasClouds` once built.

### 5. Sun occlusion scales the sun light per frame
`SunOcclusion` is a pure class. Each frame it:
1. integrates `CloudField` extinction through each band along the sun direction from the orbit target (one sample
   per band at the ray's mid-band point, times thickness and density);
2. turns that into transmittance, clamped to at least 0.1 (spec: *Storm floor*);
3. smooths it with an exponential filter (time constant 0.5 s).

The sun is chosen by one shared rule, `SunDirection.sunLight(lights)`, the same brightest-usable-directional rule
`SunDirection.of` uses, so the sky's sun and the dimmed light can't diverge.

`SceneRenderer.applyLights` keeps its `(lights, target)` cache for building `LightSet`. Applying the light set to the
environment takes a per-frame `sunScale` for that light's entity id. `TerrainShader` receives the same scaled set.
Shadows, other lights, ambient and HDR are untouched (spec: *Other lights unaffected*).

The time source is the view's `SkyClock` (EDT): it advances by the frame delta and can be set to a fixed time in tests.

### 6. Cloud assets in the tree, unused marking and the chooser
- `AssetIcons` gains a cloud icon, and the tree shows `CLOUDS` like other typed assets.
- `ProjectAssetListing` adds a sky's textual `clouds` to the asset's `uuid` references, so the unused walk follows it
  like a terrain's splat fields. `show-project-assets` is archived; this change's `cloud-assets` requirement states it.
- `SkyboxChoices` reads `clouds` from the metas it already loads, for the `· clouds` detail line.

### 7. Threading summary
- **Pool thread:** parsing, cloud asset reading and noise generation.
- **AWT render thread inside `GdxRuntime.withContext`:** every GL call (shader builds, 3D texture upload, offscreen
  targets, drawing), only while `GuardedGLCanvas.glSafe`.
- **EDT, no GL:** `CloudViewState`, `CloudFrameBudget`, `SunOcclusion` and `SkyClock`.

## Risks / Trade-offs

- Volumetric cost at high DPI → half-resolution target, temporal accumulation, frame budget with automatic fallback.
- The Kotlin and GLSL noise could drift apart → one shared hash spec, the parity GL test, and golden CPU values in
  unit tests.
- Clouds look flat with the `layered` technique → by design; it's the fast option, and the spec only requires
  coverage to agree.
- A 3D texture or `RGBA16F` render target may be unavailable on some drivers → the failure chain steps down to
  shells, then layered, then no clouds.
- Sun dimming flickers as clouds drift → exponential smoothing; occlusion uses the low-frequency coverage only.
- `design-review-refactor` changes `SkyLoader` and splits `SceneRenderer` → whichever lands second rebases. The
  `CloudRenderer` strategy and `SkyFrame` don't depend on its internals.
- Cloud asset edits don't show until the cloud asset reloads → documented. They follow `add-asset-editing-and-terrain-generation`'s
  reload once it lands.

## Migration Plan

No migration. Skies without `clouds` are unchanged, and nothing is written. Rollback removes the feature; any
`clouds` objects users added stay in their `meta.json` and are ignored.

## Open Questions

- The exact default values per cloud type (coverage, density, wind) beyond those in the spec's scenarios. They will be
  tuned visually in task 3.6 with the runIde check; the spec fixes only the cumulus defaults.
