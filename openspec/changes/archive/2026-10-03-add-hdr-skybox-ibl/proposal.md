# Proposal

## Why

The scene view can only show a skybox as a background: it blits six LDR face images (or, after
`add-procedural-sky`, a computed atmosphere) and lights nothing. A high dynamic range environment
therefore cannot be previewed at all, and the PBR material support in `gdx-model` documents itself as
*"ambient from the environment ambient cubemap (no image based lighting)"* - the sky is never a light
source. Authors who want to judge how a scene reads under a real captured sky get a flat, uniformly lit
preview instead.

## What Changes

- A new asset type, `SKYBOX_HDR`, is read: a folder under `assets` whose `meta.json` has
  `"type": "SKYBOX_HDR"` and which holds a Radiance `.hdr` (equirectangular, RGBE) image. The file is
  found by extension, so no `additional` field name has to be assumed.
- The scene's existing `skyboxEnabled` / `skyboxName` fields select it. The view draws it as the
  background with a fixed exposure and a named tone curve, so values above white stay visible instead of
  clipping.
- The same image becomes an image based light: a cosine-convolved irradiance cube for diffuse light and
  a roughness-indexed mip chain for specular reflections. While an HDR sky is lit, it **replaces** the
  scene's ambient color for every lit object: PBR models get diffuse and specular, terrain gets diffuse,
  and models on the default shader get the sky's irradiance through their ambient cubemap. Light entities
  still add on top. Scenes without an HDR sky keep their current lighting exactly.
- The skybox chooser lists `SKYBOX_HDR` assets with an HDR detail line and one preview; the properties
  panel shows an `.hdr` preview instead of six face previews.
- A sky that cannot be decoded, is the wrong shape, or cannot be built on the current GPU is skipped and
  logged; the scene then falls back to the clear color and its own ambient color.

No file format change. The change reads asset `meta.json` and `.hdr` files and writes nothing.

## Capabilities

### New Capabilities

- `scene-hdr-skybox`: reading an `SKYBOX_HDR` asset - locating and decoding its `.hdr`, the supported
  Radiance subset and size limits, and building the environment textures - and failing without hiding
  the rest of the scene.
- `scene-environment-lighting`: the scene content - PBR models, default-shader models and terrain - lit
  by an HDR sky in place of the ambient color, with the previous ambient path kept when no HDR sky is lit.

### Modified Capabilities

- `scene-skybox-rendering`: the background may be an `SKYBOX_HDR` asset, drawn with exposure and a tone
  curve; failures of either kind are isolated the same way.
- `scene-entity-lights`: light entities add to the sky's light when an HDR sky is lit, instead of to the
  ambient color.
- `abyssus-scene-skybox`: the chooser lists `SKYBOX_HDR` assets and describes them by their image size
  instead of a face count.
- `object-properties-panel`: an `SKYBOX_HDR` asset shows its HDR icon and an `.hdr` preview instead of
  six face previews.

## Impact

**Mundus files read, none written.** `assets/<name>/meta.json` (`type`, and the folder's `.hdr`);
the scene's `skyboxEnabled` and `skyboxName`. No key is added, renamed or reordered anywhere, and the
plugin still writes only through `editSceneJson`, which this change does not need.

**Plugin.** `MetaType.SKYBOX_HDR` and its project-view icon already exist, and the Abyssus tree already
counts a scene's `skyboxName` as a use of any asset; neither changes. New: a pure Radiance decoder and
equirectangular maths in `sceneview/skybox`, an `HdrSkyLoader` added as a third branch of the
`SkyLoader` / `PreparedSky` dispatch that `add-procedural-sky` introduces, a GPU environment builder
(cube projection, specular prefilter, irradiance), an HDR background program, a frame environment
passed from `SceneRenderer` to the model and terrain shaders, and `SkyboxChoices` / the properties panel
learning the type.

**`gdx-model`** (plain JVM, no plugin imports). `pbr.fragment.glsl` gains an optional image based
lighting path behind a shader flag, and a new environment attribute carries the cubemaps and the six
ambient colors so the plugin can fill them. A model rendered without the attribute compiles to today's
shader.

**Threading.** Decoding runs on the pool thread in `AssetCache.prepare`; upload, cube projection and the
prefilter passes run on the render thread inside `GdxRuntime.withContext`, one step per frame through
`AssetLoader.upload`.

**Order.** This change builds on `add-procedural-sky` (its `SkyLoader` / `PreparedSky` dispatch and its
deltas to `scene-skybox-rendering` and `abyssus-scene-skybox`) and must be applied and archived after
it. The MODIFIED requirements here are written against that change's text.

**Out of scope.**

- Making Mundus load `SKYBOX_HDR`. Whether the Mundus runtime knows the type is not assumed; like
  `SKYBOX_PROCEDURAL`, the plugin reads it and Mundus may ignore it.
- Writing or generating skybox assets. The plugin authors no Mundus content.
- Rotating the sky, or an exposure or intensity the user can set. The scene format has no field for it,
  and inventing one would break the file format; exposure is a fixed constant.
- Tone mapping the scene content. Only the background is tone mapped (see design); tone mapping models
  and terrain is a separate change to every shader.
- Specular reflections on terrain and on default-shader models; they get diffuse sky light only.
- Lighting the grid, the camera markers, the gizmos and the selection highlight. Those are overlays.
- `SKYBOX` and `SKYBOX_PROCEDURAL` as light sources. They stay backgrounds only, exactly as today.
