# Proposal

## Why

The only skybox the scene view can draw is a cube of six image faces. That is a box in two senses: it is built
from a cube mesh, and its look is baked into pictures, so it cannot follow the sun, cannot be seen from the
inside of a clear sky to the horizon without visible seams, and carries no physics. A procedural sky computed
from atmospheric scattering needs no images, has no cube, and responds to where the sun is.

## What Changes

- A new asset type, `SKYBOX_PROCEDURAL`, is recognized: a folder under `assets` whose `meta.json` has
  `"type": "SKYBOX_PROCEDURAL"`, whose `additional` names a vertex and a fragment GLSL file in the folder
  (`vertex`, `fragment`) and holds the sky's physical parameters (planet and atmosphere radii, Rayleigh and Mie
  coefficients and scale heights, Mie anisotropy, sun intensity).
- The view draws it as the scene background with a single fullscreen triangle and the asset's own shaders: a
  single-scattering Rayleigh + Mie atmosphere, ray-marched per pixel from the camera's view direction. No cube
  mesh is built.
- The sun direction is the scene's brightest directional light; a scene with none uses a fixed default sun.
- The existing `skyboxEnabled` / `skyboxName` fields select it. The skybox chooser offers it.
- A fixture asset `skybox_physical` is added to `src/test/testData/project/Untitled/assets` with `meta.json`,
  `sky.vert` and `sky.frag`.
- A sky whose shaders are missing or do not compile is skipped and logged; the rest of the scene still renders.

**File format.** `SKYBOX_PROCEDURAL` is a type Mundus does not define, so Mundus will not load such an asset.
This is a deliberate, user-approved departure from "write only values Mundus writes", limited to the new asset
folder the plugin's fixture ships. Existing `.scene`, `.abss` and `SKYBOX` / `MODEL` / `TERRAIN` `meta.json`
files are unchanged, and the plugin still writes scene files only through `editSceneJson` (choosing the sky
writes only `skyboxName`).

## Capabilities

### New Capabilities

- `scene-procedural-sky`: reading a `SKYBOX_PROCEDURAL` asset, drawing its atmosphere as the scene background
  from its own shaders, driven by the scene's sun, and failing without hiding the rest of the scene.

### Modified Capabilities

- `scene-skybox-rendering`: the background is no longer only the six faces of a `SKYBOX` asset; a scene may
  name a `SKYBOX_PROCEDURAL` asset, and the failure rule covers its shaders.
- `abyssus-scene-skybox`: the chooser lists `SKYBOX_PROCEDURAL` assets beside `SKYBOX` ones and describes them
  without face counts.

## Impact

**Mundus files read, one fixture added.** `assets/<name>/meta.json` (`type`, `additional`) and the folder's two
GLSL files; the scene's `skyboxEnabled`, `skyboxName` and its directional lights. Nothing is written by the
plugin beyond the existing `skyboxName` choice. New fixture folder `assets/skybox_physical/`.

**Plugin.** `MetaType` gains `SKYBOX_PROCEDURAL`; new `ProceduralSkyLoader` / `ProceduralSky` in
`sceneview/skybox`; `SceneSkybox` draws either kind; `SceneRenderer` passes the sun direction; `SkyboxChoices`,
the chooser entry detail line and the type tables in `docs/ai/file-formats.md` learn the type.

**Threading.** Reading `meta.json` and the GLSL text runs on the pool thread in `AssetCache.prepare`; shader
compilation and drawing run on the render thread inside `GdxRuntime.withContext`.

**Overlap.** The open change `add-hdr-skybox-ibl` also modifies `scene-skybox-rendering` and
`abyssus-scene-skybox` and adds `SKYBOX_HDR`. The deltas touch the same requirements; whichever archives second
must be reconciled (see tasks 5.1).

**Out of scope.**

- Lighting the scene from the sky (image based lighting, ambient from the sky). Background only.
- User-settable time of day, exposure or sun in the UI, and any new scene field; the sun comes from an existing
  light entity.
- Clouds, stars, moon, multiple scattering, aerial perspective on models, and tone mapping of scene content.
- Making Mundus load the new type, and authoring or generating sky assets from the plugin.
