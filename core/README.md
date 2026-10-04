# core

Asset reading and loading for Mundus projects, as a plain JVM library: the Abyssus plugin uses it, and so can any
libGDX tool or test that has a project folder and a GL context. Root package `net.nevinsky.abyssus.assets`.

## Rules

- **No IntelliJ or plugin imports.** Depends on `:gdx-model`, libGDX and Jackson only.
- **Wired by constructors.** No `object` or `companion object` in `src/main` (a `data object` case of a sealed type is a
  value and is fine); pure constants are top-level `const val`. `./gradlew :core:checkNoSingletons`, part of `check`,
  enforces it. Collaborators are passed in; nothing is looked up globally.
- **Kotlin only, with one exception.** The vendored, MIT-licensed `FastNoiseLite.java` (pinned commit, license and hash
  in `third-party/fastnoiselite/`) is the only Java source; it is used through the `NoiseSampler` interface.
- **GL only on the caller's thread.** `prepare` steps do IO and decoding with no GL; `upload`, `build` and every
  `draw` need a current GL context. The plugin calls them inside its `GdxRuntime.withContext`.

## Contents

| Package | What |
|---|---|
| `assets` | `AssetLoading` (the composition root), an SLF4J `Logger` (where progress and problems go), `ShaderSource` (GLSL from a resource folder), `AssetLayout` constants, `runCatchingKeepingCancellation` |
| `assets.files` | `AssetFiles` (an asset folder's files and `meta.json`), `MetaBase`, `MetaType`, `Asset`, `TerrainFiles` |
| `assets.json` | `JsonProcessor` (binds Mundus JSON) and `JsonNode` helpers |
| `assets.loading` | `AssetLoader` (prepare / upload / build / discard), `AssetCache` (load once, fail once, slice GPU work per frame), `SceneAssets` (a cache per project folder) |
| `assets.model` | `ModelLoader` (glTF and other formats through `gdx-model`'s Assimp loader), optional immutable ray model snapshots and their leases |
| `assets.terrain` | `TerrainLoader`, `TerrainDataReader`, `TerrainData`, `TerrainMesh`, optional immutable ray terrain snapshots and their leases |
| `assets.terrain.generation` | `TerrainGenerator` and `TerrainGenerationSettings` (seeded heights; defaults: seed 12345, feature size 200, heights 0..120, 5 octaves, persistence 0.5, lacunarity 2), `TerrainHeightEncoder`, `TerrainAssetWriter` (a new terrain's files), `TerrainRecipeCodec` and `TerrainRecipe` (the separate `abyssus-terrain.recipe.json`), `TerrainGenerationDraft` (preview state, no threads) |
| `assets.terrain.noise` | `NoiseSampler` and its factory (injected), `FastNoiseSampler` over the vendored FastNoiseLite |
| `assets.edit` | `AssetFieldDescriptions` and `AssetMetaEditor`: the editable `meta.json` fields, their validation and one-key edits on a JSON tree |
| `assets.sky` | `Sky` (a drawable background), `SkyLoader` (picks a loader by `meta.json` type); `cube/` six-face skyboxes, `procedural/` skies drawn by the asset's own GLSL, `hdr/` Radiance `.hdr` skies and their lighting environment; optional ray tracing companions `RaySkySnapshot`, `RaySkySnapshotReader`, `RaySkySnapshots` |

Sky shaders are in `src/main/resources/shader/sky/`.

## Use

```kotlin
val loading = AssetLoading(JsonProcessor(), org.slf4j.LoggerFactory.getLogger("assets"), executor,
    ShaderSource("/shader/sky", AssetLoading::class.java))
val models = loading.assets(loading.models)    // one per scene view: owns its caches
models.update(projectDir, setOf("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"))  // once per frame, GL current
val model = models.get("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")           // null until built
```

## Optional CPU model companions

`AssetLoading.rayModels` owns a constructor-wired store shared by that loading graph's
views. `acquire(files, assetName)` returns a closeable lease: `snapshot` is null
while preparation is pending; `failure` explains an unreadable or over-budget asset.
Close leases on ray-mode disable, asset removal and project/view replacement. Repeated
instances/views share the same immutable snapshot; the last close removes the cached
bytes. A closed lease holds no snapshot, and results arriving after release are dropped.
Different project folders have independent entries.

Ordinary raster preparation retains nothing when no lease requests the model. When
requested, `ModelLoader` offers CPU data before texture upload disposes its Pixmaps.
Enabling ray mode after raster assets are already cached prepares a CPU-only companion
on the supplied executor; it does not read back, reload or dispose their GPU resources.
The fallback reader disposes its own decoded images after copying, including failures.

Snapshots copy 32-bit indices, interleaved vertex data/layout, mesh parts, material
parameters, node transforms/material assignments/bind transforms, and RGBA8888 image
bytes. Mutable libGDX objects and GL handles never cross this boundary. Image rows
retain Pixmap upload order; texture bindings keep UV transforms and the raster
renderer's Linear/Repeat/no-mipmap sampling. Raster default/PBR shaders sample all
these bytes linearly, including diffuse/emissive images, so snapshots apply no sRGB
conversion. Animation pose/deformation and native material conversion remain later
ray-renderer work.

Default bounds are 128 MiB geometry/image payload per model and 256 MiB retained
payload across the store. Exceeding them reports a CPU companion failure without
breaking raster loading. These account for retained array payload, not native/GPU
allocations or temporary decoder memory; backend/application budgets are separate.

## Optional CPU terrain companions

`AssetLoading.rayTerrains` provides the same optional lease lifecycle as `rayModels`,
with separate default bounds of 128 MiB per terrain and 256 MiB retained across its
store. It copies heights, the raster mesh's 32-bit triangle indices, interleaved
positions/normals/layer UVs, and decoded splat image bytes before upload/disposal.
Late acquisition uses the same terrain reader without touching cached GPU resources.
Snapshots stay terrain-local and can be shared across transformed instances; the
scene conversion supplies world transforms and inverse-transpose normal transforms.

Splat-map coordinates are local xz divided by terrain size, with linear filtering
and clamp-to-edge wrapping. Layers use the terrain's repeated UVs, Repeat wrapping,
linear magnification and trilinear mipmap minification; snapshots retain base-level
bytes and the mipmap requirement for later backend upload. All images follow the
raster shader's linear color convention and Pixmap row order. Layer bindings retain
base/R/G/B/A order: the shader mixes each present channel sequentially rather than
normalizing the weights. Missing or unreadable images are omitted, matching raster
loading: absent base means neutral gray, absent channel means skip that mix. Native
sampling/shading remains later ray-renderer work. Cancellation drops late results,
including raster preparations that began before a request was closed and reacquired.

## Optional CPU sky companions

`AssetLoading.raySkies` hands out leases of an immutable `RaySkySnapshot`: the scene's sky as an equirectangular RGBA
float image (centre column faces -Z, top row is +Y), at most `RAY_SKY_MAX_WIDTH` (1024) wide. An HDR sky is decoded again
on the CPU and box-filtered down, keeping linear radiance far above 1; a cube skybox is resampled with the GL cube lookup
the raster sky uses (faces are back, front, left, right, bottom, top = +X, -X, +Y, -Y, +Z, -Z) and keeps its display
values. A procedural sky is the asset's own GLSL and has no CPU form, so it reads as unavailable and the caller falls
back to the background colour. Leases share one read per project and asset, drop the bytes with the last reference
(default bound 128 MiB), and never touch the GPU caches or GL. `RaySkySnapshotTest` covers HDR range, downsampling, the
six face orientations, procedural and missing skies, and lease lifecycle.

## Tests

`./gradlew :core:test`; GL tests opt in with `-Dabyssus.glTests=true`. Tests read the repository's
`src/test/testData/project` fixtures through `testProject(name)` and build the graph with `testLoading(...)`.
`AssetLoadingGlTest` covers the `asset-loading` spec. `HdrFixtures` (in `src/testFixtures`) is shared with the
plugin's tests; the GL context is `gdx-model`'s `TestGl` test fixture.

## Terrain generation and format

`assets.terrain.generation` makes heights from `TerrainGenerationSettings` (`TerrainGenerator`), writes them
(`TerrainHeightEncoder`: big-endian floats, z-major, no header) and builds the files of a new terrain asset
(`TerrainAssetWriter`). The output was checked against the Mundus writer at commit
`128175e064a915e043f024a565f935d4c6883292` (the commit `gdx-model` was forked from):
`projects/app-editor/src/main/com/mbrlabs/mundus/editor/core/assets/EditorTerrainService.java` (`createAndSaveAsset`:
`terrain.data` written with `DataOutputStream.writeFloat`, `meta.json` fields `terrainFile`, `size`, `uv`),
`projects/lib-commons/.../assets/meta/Meta.java` (`version` 1, `lastModified`, `uuid`, `type`, `additional`, in that
order), `.../assets/terrain/TerrainMeta.java` (`terrainFile`, `size`, `uv`, `splatMap`, `splatBase`, `splatR`, `splatG`,
`splatB`, `splatA`) and `.../assets/meta/MetaService.java` (`save`: compact JSON, nulls kept). The one deliberate
difference is the starting `uv`: Mundus writes `60`, Abyssus writes `1.0` (a value, not a convention).
