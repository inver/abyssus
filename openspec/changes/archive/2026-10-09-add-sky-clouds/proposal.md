# Proposal

## Why

A procedural sky (`SKYBOX_PROCEDURAL`) is a physically lit but cloudless atmosphere. The `add-procedural-sky` change
left clouds out of scope. Real skies have clouds in distinct height bands (low cumulus and stratus, mid altocumulus,
high cirrus) that drift with the wind and can hide the sun. Users want to set that up per sky asset, and to trade
realism for speed while working in the IDE.

This change covers the clouds themselves (drawn in the sky, and dimming the sun). Cloud shadows on the scene and sky
lighting follow in `add-cloud-scene-lighting`.

## What Changes

- **Cloud assets.** A new Abyssus-only asset type `CLOUDS` holds the weather and how it is drawn:
  - `technique`;
  - up to three bands, `low`, `mid` and `high`, each with a cloud `type` valid for its band, `base` and `top`
    altitudes in metres, `coverage`, `density`, and `wind` (metres per second along x and z).
  - It also owns the volumetric technique's 3D noise (FastNoiseLite), made when it is read and uploaded when built.
- **Clouds on procedural skies.** A `SKYBOX_PROCEDURAL` asset's `meta.json` `additional` gains an optional `clouds`:
  the `uuid` of a cloud asset. The sky loads it as a dependency, the way a terrain loads its splat textures, and reads
  the built cloud asset each time it draws. Several skies can share one cloud asset.
- **Drawing.** The view draws the clouds over the asset's own atmosphere, lit by the same sun, drifting with each
  band's wind. Assets without `clouds`, or naming no usable cloud asset, look exactly as today.
- **Three techniques**, all fed by the same cloud description, so they show the same clouds at different quality:
  - `layered`: a 2D cloud layer per band;
  - `shells`: 2.5D slabs with thickness, darker bases and parallax;
  - `volumetric`: ray-marched 3D clouds.
- **Technique override.** `technique` in the cloud asset's `meta.json` is its intended look. A per-view **Clouds** toolbar
  choice (*Asset*, *Layered*, *Shells*, *Volumetric*) overrides it for that view only, writes nothing, and resets when
  the view reopens.
- **Automatic fallback.** When volumetric clouds keep the view over its frame budget, the view switches its override
  to shells automatically and says so in the toolbar. Choosing volumetric again is allowed; after a second fallback the
  view stays on shells until it reopens.
- **Sun occlusion.** Clouds between the orbit target and the sun dim the scene's sun: the brightest directional light,
  the one that already positions the procedural sun. Other lights are unaffected.
- **Cloud assets in the project.**
  - Cloud assets are hand-written in this change. A creation action follows in `add-weather-preset-creation`; `core`
    ships fair, overcast and storm example metas as templates only.
  - The Abyssus tree shows cloud assets with their own icon.
  - A cloud asset named by a used sky counts as used for the unused mark, as any `uuid` reference does.
- **Skybox chooser.** A procedural sky that names a cloud asset reads `procedural sky · clouds` in the chooser.

**Fields read/written:**
- **Read:** the `type` of every asset `meta.json`; for a `SKYBOX_PROCEDURAL`, `additional.clouds` and the existing
  atmosphere fields; for a `CLOUDS` asset, `uuid`, `additional.technique`, `low`, `mid` and `high`; the scene's `skyboxName`,
  `skyboxEnabled` and light entities (for the sun).
- **Written:** nothing. This change never writes a file.

**File format.**
- No `.scene` or `.abss` change.
- `additional.clouds` lives only in the plugin's own `SKYBOX_PROCEDURAL` type.
- `CLOUDS` is a native Abyssus asset type, like `SKYBOX_PROCEDURAL` and `SKYBOX_HDR`. Its `meta.json`
  carries the native `format` / `formatVersion` markers.

### Out of scope

- Cloud shadows on terrain and models, and procedural or cloudy skies lighting the scene (`add-cloud-scene-lighting`).
- A UI action to create cloud assets (`add-weather-preset-creation`), and typed editors for cloud fields in the Properties
  panel.
- Clouds on `SKYBOX` (cube) and `SKYBOX_HDR` skies.
- Rain, snow, lightning, fog banks, stars, the moon and the time of day.
- Clouds in water reflections and in ray-traced reflections. The open changes `add-realistic-water` and
  `add-scene-raytracing` draw or sample the sky without clouds until `add-cloud-scene-lighting` supplies a cloudy-sky
  cube.
- Writing a technique choice back to `meta.json` from the toolbar.
- Supporting other editors reading clouds or cloud assets.

## Capabilities

### New Capabilities
- `scene-sky-clouds`: clouds on procedural skies:
  - the cloud description in `meta.json` and its bands;
  - the three techniques and the per-view override;
  - the automatic fallback;
  - wind drift;
  - failure isolation.
- `cloud-assets`: the `CLOUDS` asset type, how a sky names it by `uuid` and loads it as a dependency, the volumetric
  noise it owns, and how cloud assets appear in the Abyssus view and its unused marking.

### Modified Capabilities
- `scene-entity-lights`: new requirement. Clouds covering the sun dim the scene's sun light.
- `abyssus-scene-skybox`: "Skybox list entries" adds the `procedural sky · clouds` detail line.

## Impact

- **`core`:**
  - the sky loading (`ProceduralSkyMeta` / `ProceduralSkyLoader` / `ProceduralSky`) and the `Sky` drawing contract,
    which gains time;
  - new cloud shaders under `core/src/main/resources/shader/sky`;
  - a CPU cloud density model;
  - `MetaType.CLOUDS` and its `CloudsLoader`, a dependency of `ProceduralSkyLoader`;
  - the volumetric noise generator on `FastNoiseLite`.
- **Plugin:**
  - `SceneSkybox` / `SceneRenderer` (the cloud pass, and the volumetric offscreen target);
  - `SceneLighting` / `LightSet` (the sun scale);
  - the `SceneViewPanel` toolbar;
  - `SkyboxChoices` and the chooser detail line;
  - `AssetIcons` (a cloud icon) and the unused-asset `uuid` references;
  - `AbyssusBundle` strings.
- **Coordination with open changes:**
  - `show-project-assets` (archived) owns the unused rule; a sky's `clouds` is one more `uuid` reference, stated in
    this change's `cloud-assets` requirement.
  - `design-review-refactor` changes `SkyLoader` and splits `SceneRenderer`. Whichever lands second rebases.
  - `add-asset-editing-and-terrain-generation` adds typed sky editors and asset reloads. Cloud fields are not added
    to its editors here.
- **Docs:** `docs/ai/file-formats.md` (`clouds`, `CLOUDS`), `docs/ai/architecture.md`, `core/README.md`,
  `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md`, the user section of `README.md`, and the changelog.
- **No new dependencies.**
