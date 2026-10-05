# core

Asset reading and loading for native Abyssus projects, as a plain JVM library: the Abyssus plugin uses it, and so can any
libGDX tool or test that has a project folder and a GL context. Root package `net.nevinsky.abyssus.core`; the asset
code is in `net.nevinsky.abyssus.core.assets`.

## Rules

- **No IntelliJ or plugin imports.** Depends on `:gdx-model`, libGDX, SLF4J and Jackson only.
- **Wired by constructors.** No `object` or `companion object` in `src/main` (a `data object` case of a sealed type is a
  value and is fine); pure constants are top-level `const val`. `./gradlew :core:checkNoSingletons`, part of `check`,
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
| `core.assets` | `AssetMeta` and `MetaType` (the `meta.json` model), `AssetMetaLoader` (reads a `meta.json`, cached by timestamp and size), `Asset`, `runCatchingKeepingCancellation`, `Throwables` |
| `core.assets.loading` | `AssetLoader` (prepare / upload / build / discard), `AssetStorage` (load once, fail once, slice GPU work per frame), `RaySnapshotStore` with `RaySnapshotLoader`, `RaySnapshot` and the leases (see below), `TextureUploadQueue`, `ShaderSource` (GLSL from a resource folder) |
| `core.assets.model` | `ModelLoader` (glTF and other formats through `gdx-model`'s Assimp loader), `ModelMeta`, the ray model snapshot types and `ModelRaySnapshotLoader` |
| `core.assets.terrain` | `TerrainLoader`, `TerrainData`, `TerrainMesh`, `TerrainMeta`, `RayTerrainSnapshot` and `TerrainRaySnapshotLoader` |
| `core.assets.sky` | `Sky` (a drawable background) and `RaySkySnapshot`; `cube/` six-face skyboxes, `procedural/` skies drawn by the asset's own GLSL, `hdr/` Radiance and EXR skies and their lighting environment. Each has a `*Loader` and a `*RaySnapshotLoader` |

Sky shaders are in `src/main/resources/shader/sky/`.

Terrain generation, noise, the `meta.json` field editor and the composition root (`AssetLoading`) are not here: they
live in the plugin (`src/main/kotlin/net/nevinsky/abyssus/terrain/`, `AssetMetaEditor.kt`, `AssetLoading.kt`).

## Loading and caching

An `AssetLoader<P, T>` turns one asset into a GPU object in steps: `prepare(name)` (or `loadPrepared(meta)`) reads and
decodes with no GL, `upload` does one slice of GPU work, `build` creates the object, `discard` releases a prepared value
that was never built. `AssetStorage<P, T>(executor, loader, log)` runs them:

- `request(name)` starts a load on the executor; every name loads once and is shared, and a failure is logged once and
  remembered, so it is not retried every frame.
- `pump(maxSteps)` (GL thread) advances `upload` and `build`, one asset at a time, and returns true when something
  changed. `get(name)` is null until the asset is built.
- `invalidate(names)` marks names as changed on disk: a loaded asset stays in use until its replacement is built, then
  the two swap in one step; a superseded load is discarded. `version(name)` changes when `get` returns another asset.
- `retain(names)` disposes everything else, `abandon()` forgets assets without disposing them (the GL context is gone),
  `dispose()` releases everything.

One `AssetStorage` serves one asset kind of one project; the plugin owns one per scene view.

```kotlin
val storage = AssetStorage(executor, ModelLoader(metaLoader, assimp, fileLoader), log)
storage.request("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")
storage.pump()                                          // once per frame, GL current
val model = storage.get("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")   // null until built
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
- Ordinary raster preparation retains nothing when no lease requests the asset. When one does, `ModelLoader` and
  `TerrainLoader` call `store.preparation(name)?.offer(source)` (`RayModelSource`, `RayTerrainSource`) before upload
  disposes the Pixmaps; an offer after cancellation or reacquisition is a no-op. The fallback `load` disposes its own
  decoded images after copying, including failures. It never reads back, reloads or disposes a GPU resource.
- A `TerrainRaySnapshotLoader` wraps a plain `TerrainLoader` built without a store (it must not publish snapshots
  itself), so the store-aware `TerrainLoader` is built after the store and the snapshot loader.

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

`./gradlew :core:test`; GL tests opt in with `-Dabyssus.glTests=true`. Tests read the repository's
`src/test/testData/project` fixtures. `HdrFixtures` (in `src/testFixtures`) is shared with the plugin's tests; the GL
context is `gdx-model`'s `TestGl` test fixture. `AssetStorageTest` covers the loading pipeline with a fake loader and a
queued executor.

## Terrain format

A terrain's heights are big-endian floats, z-major, with no header; `TerrainLoader` reads them and infers a square
resolution. Metadata starts with `format: "abyssus"` and `formatVersion: 1`, then the existing metadata fields.
Generation and writing new terrains are in the plugin. The retained height encoding was originally compared against the
upstream implementation; see [model source provenance](../docs/third-party/gdx-model-origin.md). This is historical
evidence, not a compatibility guarantee.
