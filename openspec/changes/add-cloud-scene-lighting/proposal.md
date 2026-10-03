# Proposal

## Why

`add-sky-clouds` puts clouds in the sky and dims the sun uniformly while a cloud covers it. A real sky does more:
clouds cast moving shadows across the ground, and an overcast sky lights a scene with flat grey light where a clear
one gives blue light from above. Without these, the scene below doesn't match the sky above it. This change adds both,
and lets other features that reflect the sky show the clouds.

Depends on `add-sky-clouds`.

## What Changes

- **Cloud shadows.**
  - While a procedural sky draws enabled clouds, the clouds cast moving shadows on terrain and models: dark patches
    that drift with each band's wind and match where the clouds are.
  - They attenuate only the sun light (the brightest directional light), following the per-light rule of
    `scene-shadows`.
  - Where cloud shadows are drawn, they replace `add-sky-clouds`' uniform sun dimming, so no surface is dimmed twice.
  - Beyond the cloud shadow area, the uniform dimming still applies.
- **Opt-in sky lighting.**
  - A `SKYBOX_PROCEDURAL` asset with `additional.lightsScene: true` lights the scene from the sky, as an HDR sky does
    today: it replaces the ambient color for models and terrain, and PBR models reflect it.
  - The light follows the sun and the clouds. A clear noon sky gives cool blue light from above; an overcast sky
    gives flat grey light; a sunset gives warm light.
  - Changes in the clouds or the sun fade in over about a second instead of popping.
  - Without `lightsScene`, or with it false, the scene is lit by its ambient color exactly as before.
  - The fixture's `skybox_physical` has no `lightsScene`, so `Main Scene` doesn't change.
- **A cloudy-sky environment for reflections.** The same sky-plus-clouds environment is made available to the
  sky-reflecting features of the open changes:
  - water reflections beyond their reflection budget (`add-realistic-water`);
  - ray-traced reflection misses (`add-scene-raytracing`).
  Tasks amend those changes' deltas if they are still open.

**Mundus fields:**
- **Read:** `additional.lightsScene` and `additional.clouds` of `SKYBOX_PROCEDURAL` assets; the atmosphere
  parameters; the scene's `skyboxName`, `skyboxEnabled`, ambient light and light entities.
- **Written:** nothing.

**File format:** no change beyond the new optional `lightsScene` boolean in the plugin's own `SKYBOX_PROCEDURAL` type.

### Out of scope

- Shadows of clouds on clouds beyond what each technique already draws.
- Clouds dimming point or spot lights, or casting shadows from any light other than the sun.
- Lighting from cube (`SKYBOX`) skies.
- Changing how HDR skies light the scene.
- A UI to toggle `lightsScene`. It is hand-edited, like the cloud fields.
- Matching lighting to a user's custom `sky.frag` that departs from the physical atmosphere parameters (see the
  design).

## Capabilities

### New Capabilities

None. `scene-sky-clouds` comes from `add-sky-clouds`.

### Modified Capabilities
- `scene-shadows`:
  - "Scene geometry casts and receives shadows" lets clouds cast shadows, though other sky backgrounds still don't.
  - New requirement for cloud shadows on the sun light.
- `scene-environment-lighting`: "An HDR sky replaces the ambient color" also covers a procedural sky with
  `lightsScene` true. The PBR and default-shader/terrain requirements then apply to it as to an HDR sky.

## Impact

- **`core`:**
  - a linear atmosphere shader for lighting, plus the cloud field, rendered into a cube;
  - `HdrEnvironmentBuild` accepts a cube source as well as an equirect image;
  - double-buffered environments with a cross-fade.
- **Plugin:**
  - `SceneRenderer` / `SceneAmbient` (a lighting procedural sky);
  - a cloud shadow map pass;
  - `TerrainShader` and the terrain shaders (a cloud factor on the sun light);
  - `SunOcclusion` (turned off where shadows apply).
- **`gdx-model`:** a generic per-directional-light visibility texture attribute for `DefaultShader` and `PbrShader`.
  It is not cloud-specific, so `gdx-model` stays a plain reusable library.
- **Coordination:**
  - `scene-shadows` atlas code;
  - `add-realistic-water` and `add-scene-raytracing` deltas (cloudy-sky environment);
  - `design-review-refactor` (`SceneRenderer` split).
- **Docs:** `docs/ai/file-formats.md` (`lightsScene`), `docs/ai/architecture.md`, the sceneview README, the user
  section of `README.md`, and the changelog.
