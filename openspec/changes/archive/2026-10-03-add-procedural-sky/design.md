# Design

## Context

The scene view draws a `SKYBOX` through `SceneSkybox` (a cube mesh, program `/shader/scene/skybox.*`, loaded by
`SkyboxLoader` via `SceneAssets` / `AssetCache`: `prepare` off-thread without GL, `upload` on the render thread).
Asset `meta.json` is bound with `MetaBase<T>` and `MetaType`. `LightSet.of` already picks the brightest directional
lights. `Shaders.load` reads plugin-bundled GLSL from `/shader/scene/`; asset-folder GLSL is new. See proposal.md
for motivation and the file-format departure.

## Goals / Non-Goals

**Goals:**
- A fullscreen-triangle atmosphere with no cube, shaders read from the asset folder.
- Physical model in pure, testable Kotlin as well as GLSL, so the shader's constants and sun math are checked
  without GL.

**Non-Goals:**
- Lighting scene content from the sky, clouds, stars, multi-scattering, aerial perspective.
- Sandboxing user GLSL; a bad shader only fails that sky.

## Decisions

**Single-scattering Rayleigh + Mie, ray-marched in the fragment shader.** Per pixel: intersect the view ray with
the atmosphere sphere (planet radius 6360 km, atmosphere 6420 km), march 16 primary samples, at each march 8 light
samples toward the sun, accumulate optical depth with Rayleigh (beta 5.8e-6, 13.5e-6, 33.1e-6 per m, scale height
8 km) and Mie (beta 21e-6, scale height 1.2 km, g = 0.76) and the Rayleigh and Henyey-Greenstein phase functions;
add a sun disc from the Mie phase. Alternative: Preetham/Hosek-Wilkie closed form - cheaper but wrong near the
horizon and at sunset, which is where "physically correct" is visible. The user chose the scattering model.

**Fullscreen triangle, view ray from the inverse view-projection.** The vertex shader emits three vertices from
`gl_VertexID`-free position data (a 3-vertex `Mesh` with positions) and the fragment shader reconstructs the
camera-space ray with `u_invViewProj` (translation removed, as the cube path does). Alternative: keep the cube and
only change the shader - rejected, the cube is what the request asks to remove.

**Shaders live in the asset folder, parameters in `meta.json`.** `additional` = `{vertex, fragment, ...params}`.
Parameters are passed as uniforms (`u_planetRadius`, `u_atmosphereRadius`, `u_betaRayleigh`, `u_betaMie`,
`u_hRayleigh`, `u_hMie`, `u_mieG`, `u_sunIntensity`); each is optional with the Earth-like default, so a minimal
folder works. The plugin supplies `u_invViewProj`, `u_sunDir`, `u_cameraHeight`. Alternative: bundle the shader in
the plugin and make the asset parameters only - rejected, the request puts the shaders in the assets folder, and
this keeps the sky editable by the game author.

**Sun from the scene.** `LightSet` already ranks directional lights; the sun direction is the negated direction of
the strongest one, normalized. None -> a constant default (elevation 45 degrees). No new scene field.

**Draw order and state match `SceneSkybox`:** depth test and mask off, culling off, drawn first. Below the
horizon the shader returns a dark ground tint (`ground albedo * sun irradiance` scaled), avoiding the empty
half-space.

**Exposure.** The shader outputs `1 - exp(-I * exposure)` (fixed exposure constant), since the LDR framebuffer
clips values above one; same constraint and approach as `add-hdr-skybox-ibl`.

**Types.** `MetaType` gains `SKYBOX_PROCEDURAL`. `ProceduralSkyMeta` / `ProceduralSkyAdditional` mirror
`SkyboxMeta`. `ProceduralSkyLoader` implements `AssetLoader<PreparedProceduralSky, ProceduralSky>`;
`SceneSkybox` is generalized to a small `SkyDrawable` interface (`draw(camera, sun, program state)`) implemented by
`SkyboxCube` and `ProceduralSky`, chosen by the meta type, so the cache stays one kind of asset.

**Testable without GL (headless):** `AtmosphereParams` (defaults, parsing from `additional`), `SunDirection`
(light list -> unit vector, default), and a CPU reference `AtmosphereModel.radiance(...)` mirroring the shader's
math, used to assert blue zenith, red-shifted horizon at sunset, and non-negative finite output. The shader and
the model share constants through `AtmosphereParams`; a test compares the fixture's `meta.json`/GLSL defaults to
them.

**Threads.** `prepare` (off-thread, no GL): parse `meta.json`, read both GLSL files as text. `upload` (render
thread, in `GdxRuntime.withContext`): compile the `ShaderProgram`, build the triangle mesh. `draw` on the render
thread. Compile failure is caught with `runCatchingKeepingCancellation` in `upload`, logged once, and the asset
is cached as failed so it is not recompiled each frame.

**Chooser and docs.** `SkyboxChoices` accepts both type strings; the detail line has a new branch. The type
table and the "recognized but not drawn" row in `docs/ai/file-formats.md` and `sceneview/README.md` are updated.
The properties panel is unchanged: it shows `type` and `additional` rows for any type and its face-preview
section already appears only for `SKYBOX`.

## Risks / Trade-offs

- [Mundus cannot load `SKYBOX_PROCEDURAL`] -> user-approved; documented in file-formats.md as a plugin-only type.
- [Ray-march cost at 16x8 samples fullscreen] -> one triangle, no overdraw; constants are uniforms-free `const`
  loops; acceptable on the integrated GPUs the IDE runs on; drop to 12x6 if runIde shows lag.
- [macOS GLSL 1.20 / GL 2 context limits] -> write GLSL compatible with `ShaderProgram`'s default version, loops with
  constant bounds, no `gl_VertexID`; verify in runIde on macOS.
- [User shader injected into the IDE process] -> a bad shader can only fail compile (caught) or hang the GPU; same
  trust as opening any project; noted, not mitigated.
- [Conflict with `add-hdr-skybox-ibl` deltas] -> reconcile on second archive (task 5.1).
