# core

Asset reading and loading for native Abyssus projects, as a plain JVM library: the Abyssus plugin uses it, and so can any
libGDX tool or test that has a project folder and a GL context. Root package `net.nevinsky.abyssus.lib.core`; the asset
code is in `net.nevinsky.abyssus.lib.core.assets`.

## Rules

- **No IntelliJ or plugin imports.** Depends on `:lib-gdx-model`, libGDX, SLF4J, Jackson and LWJGL TinyEXR for OpenEXR decoding.
- **Wired by constructors.** No `object` or `companion object` in `src/main` (a `data object` case of a sealed type is a
  value and is fine); pure constants are top-level `const val`. `./gradlew :lib-core:checkNoSingletons`, part of `check`,
  enforces it; `AbyssusProjectLayout` and `GeometryUtils` are its only exceptions. Collaborators are passed in; nothing
  is looked up globally.
- **Files go through `FileLoader`, metas through `AssetMetaLoader`.** Loaders and stores take them (one pair per project
  folder) instead of reading paths themselves.
- **GL only on the caller's thread.** `prepare` steps do IO and decoding with no GL; `upload`, `build` and every
  `draw` need a current GL context. The plugin calls them inside its `GdxRuntime.withContext`.

## Contents

| Package | What |
|---|---|
| `core` | `FileLoader` (an asset folder's files, refusing names that leave the assets folder), `AbyssusProjectLayout` (folder and file name constants), `JsonProcessor` (binds native JSON), `GeometryUtils` |
| `core.assets` | `AssetMeta` and `MetaType` (the `meta.json` model; `uuid` is null when a meta declares none), `AssetMetaBinder` (one injectable settings-class registration map and metadata binding rule), `AssetMetaLoader` (validates and reads saved `meta.json`, binds through the binder, caches by timestamp and size), `AssetIndex` (the asset folder of a `uuid`), `Asset`, `runCatchingKeepingCancellation`, `Throwables` |
| `core.assets.loading` | `AssetLoader` (prepare / dependencies / upload / build / discard), `CompositeAssetLoader` (one loader for every asset kind, by `MetaType`), `AssetStorage` (the cache and owner of built assets: load once, fail once, slice GPU work per frame, load dependencies first) with `BuiltAssets`, `RaySnapshotStore` with `RaySnapshotLoader`, `RaySnapshot` and the leases (see below), `TextureUploadQueue`, `ShaderSource` (GLSL from a resource folder) |
| `core.assets.model` | `ModelLoader` (glTF and other formats through `gdx-model`'s Assimp loader), `ModelMeta`, the ray model snapshot types and `ModelRaySnapshotLoader` |
| `core.assets.terrain` | `TerrainLoader`, `TerrainData`, `TerrainMesh`, `TerrainMeta`, `RayTerrainSnapshot` and `TerrainRaySnapshotLoader` |
| `core.assets.texture` | `TextureLoader` (`TEXTURE` and `PIXMAP_TEXTURE` assets: image decoded off the GL thread, uploaded as a mipmapped repeating texture), `PreparedTexture` (the decoded image; `release()` hands the `Pixmap` to a caller that uploads it itself) and `TextureMeta` |
| `core.assets.sky` | `Sky` (a drawable background) and `RaySkySnapshot`; `cube/` six-face skyboxes, `procedural/` skies drawn by the asset's own GLSL, `hdr/` OpenEXR skies and their lighting environment. Each has a `*Loader` and a `*RaySnapshotLoader` |

`ExrLoader.dimensions` and `HdrPreview.dimensions` read the EXR data-window dimensions without decoding pixels.
Header parsing is shared with decoding; native headers and images each have explicit cleanup. Chooser and panel
labels use original dimensions, while previews retain their existing reduction and tone mapping.

`Scene.rayTracing` retains explicit null limits for editor validation; omitted limits keep their defaults.
`RayTracing` limit properties are nullable integers. Standalone consumers compiled against primitive accessors
must rebuild against this core version.

`core.project` holds filesystem `Project` / `ProjectLoader`; the `core` scene package holds scene DTOs and `SceneLoader`.
These and `AssetMetaLoader` validate native identity with `core.format.AbyssusDocumentFormat` before binding.
Unsupported project/scene documents throw; unsupported metadata returns null and reports the reason once per file revision.
Admission checks do not modify document text or write files.
The vendored Java noise implementation is under `src/main/java/`; generator and recipe orchestration live in the plugin.

Sky shaders are in `src/main/resources/shader/sky/`.

Terrain generation, noise, the `meta.json` field editor and the composition root (`AssetLoading`) are not here: they
live in the plugin (`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/terrain/`, `AssetMetaEditor.kt`, `AssetLoading.kt`).

## References between assets

An asset's `meta.json` names another asset by its `uuid`: a terrain's `splatMap`, `splatBase`, `splatR`, `splatG`,
`splatB` and `splatA` are texture assets. `AssetIndex(fileLoader, metaLoader).folder(uuid)` finds the folder that
declares a `uuid`. It keeps no state: each call lists the asset folders and reads their metas through `AssetMetaLoader`
(cached per file), so an added, replaced or deleted texture is seen on the next call with nothing to refresh. The first
folder by name wins a `uuid` two folders share.

An asset loader never calls another asset loader. What an asset needs from another asset it names in
`AssetLoader.dependencies`, and `AssetStorage` loads those first; the loader reads the built result from the
`BuiltAssets` it is handed. `TerrainLoader` resolves each splat field to a texture folder in `prepare` (an unknown `uuid`
leaves the layer out), names the folders as its dependencies, and its `TerrainMesh` reads the textures from the storage
on every draw: a replaced texture is picked up without rebuilding the terrain, and the mesh never owns or disposes one.
The splat map is set to linear filtering and clamped edges the first time a texture is drawn as one.

## Loading and caching

An `AssetLoader<P, T>` turns one asset into a GPU object in steps: `prepare(name)` (or `loadPrepared(meta)`) reads and
decodes with no GL, `dependencies(prepared)` names the other assets it needs, `upload` does one slice of GPU work,
`build(prepared, assets)` creates the object, and `discard` releases a prepared value that was never built.

`CompositeAssetLoader(metaLoader, loaders)` is the loader of a whole project: it reads an asset's `meta.json` and hands
the asset to the loader registered for its `MetaType`. One `AssetStorage<PreparedAsset, Disposable>(executor, composite,
log)` over it owns the built assets of every kind, keyed by asset folder name, which is what lets assets depend on each
other. `AssetStorage` itself works over any single `AssetLoader`.

- `request(name)` starts a load on the executor; every name loads once and is shared, and a failure is logged once and
  remembered, so it is not retried every frame.
- `pump(maxSteps)` (GL thread) advances `upload` and `build`, one asset at a time, and returns true when something
  changed. `get(name)` / `getAs<T>(name)` is null until the asset is built.
- Once an asset is prepared, the storage requests its `dependencies` and holds its upload and build until each is built
  or has failed (a failed dependency is simply absent from `BuiltAssets`). Assets that depend on each other in a cycle
  (including an asset that needs itself) cannot be built before one another, so every member fails with a
  `dependency cycle: a -> b -> a` error as soon as the last of them is prepared; whatever merely needs a member is
  not part of the cycle and carries on without it.
- `isLoading()` / `isLoading(name)` say whether anything, or one asset, is still loading.
- `invalidate(names)` marks names as changed on disk: a loaded asset stays in use until its replacement is built, then
  the two swap in one step; a superseded load is discarded. `version(name)` changes when `get` returns another asset.
- `retain(names)` disposes everything neither named nor needed by a named asset, `abandon()` forgets assets without
  disposing them (the GL context is gone), `dispose()` releases everything.

`AssetIndex` stays a pure lookup (asset folder of a `uuid`); the storage, not the index, owns the built assets.

```kotlin
val composite = CompositeAssetLoader(metaLoader, mapOf(
    MetaType.MODEL to ModelLoader(metaLoader, assimp, fileLoader),
    MetaType.TERRAIN to TerrainLoader(fileLoader, metaLoader),
    MetaType.TEXTURE to TextureLoader(fileLoader, metaLoader),
))
val assets = AssetStorage(executor, composite, log)
assets.request("terrain_x")                     // its splat textures load first
assets.pump()                                   // once per frame, GL current
val terrain = assets.getAs<TerrainMesh>("terrain_x")   // null until built
```

## Optional CPU companions (ray snapshots)

`RaySnapshotStore<S, D>(executor, metaLoader, loader, label, maxBytes)` is one shared, reference-counted cache of
immutable CPU snapshots for one asset kind of one project, keyed by asset name. The plugin builds one each for models,
terrains and skies. `S` is the snapshot (a `RaySnapshot` with a `byteSize`), `D` is the source a raster preparation
already holds, and `label` ("model", "terrain", "sky") names the kind in failure messages.

- `acquire(name)` returns a closeable `RaySnapshotLease`: `snapshot` is null while preparation is pending, `failure`
  explains an unreadable or over-budget asset. Close leases on ray-mode disable, asset removal and project/view
  replacement. Repeated instances/views share the same immutable snapshot; the last close removes the cached bytes. A
  closed lease holds no snapshot, and results arriving after release are dropped.
- `invalidate(name)` drops the old revision and any read in flight, then allows a fresh acquisition.
- `RaySnapshotLoader<S, D>` builds the snapshot: `load(meta)` reads the asset afresh (the fallback when ray mode starts
  after the raster asset is cached), and `capture(source)` copies a `D` that raster preparation offers. Kinds with no
  raster capture (skies) leave `capture` at its default and use `D = Nothing`.
- Ordinary raster preparation retains nothing when no lease requests the asset. When one does, `ModelLoader` calls
  `store.preparation(name)?.offer(source)` (`RayModelSource`) before upload disposes the Pixmaps; an offer after cancellation or reacquisition is a no-op. The fallback `load` disposes its own
  decoded images after copying, including failures. It never reads back, reloads or disposes a GPU resource.
- The terrain store has no raster capture (`D = Nothing`): the raster terrain keeps no pixels once its textures are
  on the GPU, so `TerrainRaySnapshotLoader` reads heights (through a `TerrainLoader`) and splat images (through a
  `TextureLoader`) afresh. It is a CPU companion read, not an asset load, so it uses both loaders directly.

## Optional CPU model companions

Snapshots copy 32-bit indices, interleaved vertex data/layout, mesh parts, material
parameters, node transforms/material assignments/bind transforms, and RGBA8888 image
bytes. Mutable libGDX objects and GL handles never cross this boundary. Image rows
retain Pixmap upload order; texture bindings keep UV transforms and the raster
renderer's Linear/Repeat/no-mipmap sampling. Raster default/PBR shaders sample all
these bytes linearly, including diffuse/emissive images, so snapshots apply no sRGB
conversion. Animation pose/deformation and native material conversion remain later
ray-renderer work.

Default bounds are 128 MiB geometry/image payload per model (`ModelRaySnapshotLoader`) and 256 MiB retained
payload across the store. Exceeding them reports a CPU companion failure without
breaking raster loading. These account for retained array payload, not native/GPU
allocations or temporary decoder memory; backend/application budgets are separate.

## Optional CPU terrain companions

The terrain store has the same lease lifecycle, with separate default bounds of 128 MiB per terrain
(`TerrainRaySnapshotLoader`) and 256 MiB retained across the store. It copies heights, the raster mesh's 32-bit triangle indices, interleaved
positions/normals/layer UVs, and decoded splat image bytes before upload/disposal.
Late acquisition loads the terrain through the plain `TerrainLoader` without touching cached GPU resources.
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

The sky store hands out leases of an immutable `RaySkySnapshot`: the scene's sky as an equirectangular RGBA float image
(centre column faces -Z, top row is +Y), at most `RAY_SKY_MAX_WIDTH` (1024) wide. `HdrSkyRaySnapshotLoader` decodes an
HDR sky again on the CPU and box-filters it down, keeping linear radiance far above 1; `SkyboxRaySnapshotLoader`
resamples a cube skybox with the GL cube lookup the raster sky uses (faces are back, front, left, right, bottom, top =
+X, -X, +Y, -Y, +Z, -Z) and keeps its display values. `ProceduralSkyRaySnapshotLoader` returns null: a procedural sky is
the asset's own GLSL and has no CPU form, so it reads as unavailable and the caller falls back to the background colour.
Leases share one read per asset, drop the bytes with the last reference (default bound 128 MiB in the plugin's wiring),
and never touch the GPU caches or GL.

## Tests

`./gradlew :lib-core:test`; GL tests opt in with `-Dabyssus.glTests=true`. Tests read the repository's
`projects/plugin-abyssus/src/test/testData/project` fixtures through `testProject(name)`; `testFileLoader` and `testMetaLoader` build a project's
loaders, and `exrFixture()` gives the bundled EXR sky as a file (`TestData.kt`). The GL context is `gdx-model`'s `TestGl`
test fixture. `HdrFixtures` and `RecordingLogger` are in `src/testFixtures`, shared with the plugin's tests. `AssetStorageTest` covers the loading pipeline with a fake loader and a
queued executor.

## Terrain format

A terrain's heights are big-endian floats, z-major, with no header; `TerrainLoader` reads them and infers a square
resolution. Metadata starts with `format: "abyssus"` and `formatVersion: 1`, then the existing metadata fields.
Generation and writing new terrains are in the plugin. The retained height encoding was originally compared against the
upstream implementation; see [model source provenance](../docs/third-party/gdx-model-origin.md). This is historical
evidence, not a compatibility guarantee.
