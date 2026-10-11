# lib-core

Native project DTOs, scene/ECS loading and asset loading, as a plain JVM library: the Abyssus plugin uses it, and so can any
libGDX tool or test that has a project folder and a GL context. Root package `net.nevinsky.abyssus.lib.core`; the asset
code is in `net.nevinsky.abyssus.lib.core.assets`.

## Rules

- **No IntelliJ or plugin imports.** Depends on `:lib-gdx`, libGDX, Ashley, SLF4J, Jackson and LWJGL TinyEXR for OpenEXR decoding.
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
| `core.io`, `core.util` | `FileLoader` (an asset folder's files, refusing names that leave the assets folder), `AbyssusProjectLayout` (folder and file name constants), `JsonProcessor` (binds native JSON), `NativeJsonTree` (the lossless reader: key order, nulls and number text such as `2.50` and `1.0E-4` kept; `SceneJson` and `SceneLoader` both read through it), `SourceBackedComponent` (a component written back from the object it was read from), `GeometryUtils` |
| `core.defaults` | Top-level `NO_ENTITY` reference sentinel |
| `core.dto` | `ProjectDto`, `SceneDto`, camera, light, fog and ray preference DTOs |
| `project`, `scene` (under `lib-core`) | `ProjectLoader`, `SceneLoader`, `RuntimeSceneLoader`, `SceneContext` and `SceneEnvironment` |
| `core.ecs` | Component registration and loading, `SceneEngine`, `SceneEntityIds`, `SceneFoliageListing` (the terrain-bound foliage of a loaded engine: components only, no files or GL); `component/` holds Ashley classes and render-asset lookup, `system/` holds look-at and camera synchronization |
| `core.assets` | `AssetMeta` and `MetaType` (the `meta.json` model; `uuid` is null when a meta declares none), `AssetMetaBinder` (one injectable settings-class registration map and metadata binding rule), `AssetMetaLoader` (validates and reads saved `meta.json`, binds through the binder, caches by timestamp and size), `AssetIndex` (the asset folder of a `uuid`), `Asset`, `runCatchingKeepingCancellation`, `Throwables` |
| `core.ui` | The native UI asset: `ui.model` holds the immutable screen, widget, binding and resource definitions, `UiMeta` (the typed `additional` of a `UI` asset) and `UiAdmission`; `ui.validation` holds `UiCollectionDecoder`, which admits a `meta.json` tree (native markers, schema version 1, ids, geometry, limits) without IO. `project.UiProjectRegistry` resolves the project's `uiAssetNames`; `ui.layout` is the pure layout engine; `ui.state` holds typed bindings, validated write-back and navigation; `ui.render` is the shared Scene2D presenter; `ui.style` resolves styles; `assets.ui` prepares and owns the GL resources. See *UI assets*, *UI host contract* and *UI renderer and resources* below |
| `core.assets.loading` | `AssetLoader` (prepare / dependencies / upload / build / discard), `AssetStorage` (the cache and owner of built assets: load once, fail once, slice GPU work per frame, load dependencies first) with `BuiltAssets`, `RaySnapshotStore` with `RaySnapshotLoader`, `RaySnapshot` and the leases (see below), `TextureUploadQueue`, `ShaderStorage` (GLSL from a resource folder) |
| `core.assets.model` | `ModelLoader` (glTF and other formats through `lib-gdx`'s Assimp loader), `ModelMeta`, the ray model snapshot types and `ModelRaySnapshotLoader` |
| `core.assets.terrain` | `TerrainLoader`, `TerrainData`, `TerrainMesh`, `TerrainMeta`, `RayTerrainSnapshot` and `TerrainRaySnapshotLoader` |
| `core.assets.foliage` | `FoliageLoader` (`FOLIAGE` assets: reads the meta, the masks and the bake off the GL thread and declares the terrain and layer models as dependencies), `FoliageMeta` and the layer model types, `FoliageMaskFile` (`layer-<id>.mask`: `maskResolution²` bytes z-major, a missing file is a full mask), `FoliageDataFile` (the `ABFO` bake: `FoliageBake`, `FoliageLayerBake`, `FoliageCopy`, read as stale when it does not fit), `FoliageFingerprint` (SHA-256 of the generation inputs), `FoliageRuntimeLoad` (the read policy of games and physics: metadata, terrain, bake and masks as written, never generated or written; a stale or unverifiable bake keeps its saved copies, copies that cannot be placed are dropped, and problems are returned as bounded warnings), `FoliageCollider` / `FoliageColliderReader` (a layer's optional neutral BOX/SPHERE/CAPSULE `collider`, with the `ColliderComponent` defaults; absent is told apart from invalid) and `FoliageDrawable` (the instanced GL side, reused by the runtime) |
| `core.assets.texture` | `TextureLoader` (`TEXTURE` and `PIXMAP_TEXTURE` assets: image decoded off the GL thread, uploaded as a mipmapped repeating texture), `PreparedTexture` (the decoded image; `release()` hands the `Pixmap` to a caller that uploads it itself) and `TextureMeta` |
| `core.assets.sky` | `SkyRenderer` (a drawable background) and `RaySkySnapshot`; `cube/` six-face skyboxes, `procedural/` skies drawn by the asset's own GLSL, `hdr/` OpenEXR skies and their lighting environment. Each has a `*Loader` and a `*RaySnapshotLoader` |
| `core.assets.sky.clouds` | The clouds of a procedural sky: `CloudMeta`, nested `CloudMeta.CloudBand`, `CloudType` (level and defaults), `CloudLevel` with its `CloudBandLimits`, `CloudTechnique`; settings decode through `AssetMetaBinder` / `CloudSettingsReader`; `CloudsLoader` and `Clouds` (the `CLOUDS` asset: bands, technique and the volumetric 3D noise from `CloudNoiseGenerator`, built on FastNoiseLite), which a procedural sky names by `uuid` and loads as a dependency; `CloudField`, the cloud techniques and `SunOcclusion` |

`ExrLoader.dimensions` and `HdrPreview.dimensions` read the EXR data-window dimensions without decoding pixels.
Header parsing is shared with decoding; native headers and images each have explicit cleanup. Chooser and panel
labels use original dimensions, while previews retain their existing reduction and tone mapping.

`SceneDto.rayTracing` retains explicit null limits for editor validation; omitted limits keep their defaults.
`RayTracingDto` limit properties are nullable integers. Standalone consumers compiled against primitive accessors
must rebuild against this core version.

`core.dto` holds `ProjectDto` / `SceneDto`; `core.project` holds `ProjectLoader`, and the `scene` package holds
`SceneLoader` / `RuntimeSceneLoader`.
These and `AssetMetaLoader` validate native identity with `core.format.AbyssusDocumentFormat` before binding.
Unsupported project/scene documents throw; unsupported metadata returns null and reports the reason once per file revision.
Admission checks do not modify document text or write files.
The vendored Java noise implementation is under `src/main/java/`; terrain generation and recipe orchestration live in `lib-core-editor`.

Sky shaders are in `src/main/resources/shader/sky/`: the defaults of `ShaderStorage`, which finds a shader file in the
asset's own folder first (`withAssets`), then in classpath folders the host adds (`withResources(path, anchor)`), then in those defaults,
and compiles programs from it. `projects/lib-core/src/main/resources/clouds/templates/` holds fair, overcast and
storm examples of `CLOUDS` metas. The IDE does not create cloud assets from these templates.

**Sky lighting and the cloudy-sky environment.** A procedural sky with `additional.lightsScene: true` owns a
`ProceduralSkyLighting` (a linear 64²-per-face cube from `atmosphere_linear.frag` and the shell clouds, turned into specular
and irradiance cubes by `HdrEnvironmentBuild` over a borrowed `CubeSource`, rebuilt as `LightingRefreshPolicy` says and faded
in); the host advances it with `ProceduralSky.updateLighting` every frame and lights with `ProceduralSky.lightingState`.
Any other feature that needs to *see* the sky from a surface (water reflection fallbacks, ray tracing misses) can ask for the
same cube without `lightsScene`:

- Call `ProceduralSky.updateEnvironment(sun, timeSeconds)` once per frame while the feature needs it, on the GL thread. The
  first call builds the cube; nothing is built for a sky nobody asks of, and the scene's ambient color is not replaced.
- The returned `SkyEnvironment` holds the linear `RGBA16F` cube (mips filled, display tone mapping not applied: not a
  picture of the sky) and a `revision` that grows with every rebuild. Rebuilds happen when the sun moves over a degree, every
  two seconds of cloud time while a band has wind, and when the cloud asset is reloaded, replaced or removed. Compare
  revisions to know when to refresh whatever was taken from it (a CPU snapshot, an accumulation).
- The cube belongs to the sky: do not dispose it, and do not keep it past the next update or `releaseEnvironment()`. It is
  disposed with the sky and abandoned with the GL context.

Terrain generation and `AssetMetaEditor` live in `lib-core-editor`; their dialogs and document commands live in the
plugin. `BaseCtx` is the standalone composition root; the IDE builds its own graph through `AssetLoading`.

## Scene loading and registration

`JsonProcessor(log)` owns Jackson configuration. `ComponentRegistry()` starts with the built-in Ashley components;
register additional classes with `register(name, clazz)` or `registerAll(map)` before loading. Classes must be
Jackson-bindable; there is no annotation-based schema export API in this source set.

`FoliageComponent(assetName)` is built in. It keeps the object it was read from, so unknown members, key order and number
text survive a write and only a changed `assetName` is patched; a name that is not a string leaves the component raw with
one warning. `SceneLoader` keeps the scene's `ecs` block as `NativeJsonTree` read it, so that spelling reaches the binding.
`SceneFoliageListing(warn).list(engine)` lists terrain entities with a nonempty foliage name; a foliage component on an
entity that renders no terrain is reported once.

`PathArrangementComponent` (`points`, `closed`, `asset`, `spacing`, `offset`, `sides`, `alignToPath`, `yawOffset`,
`slots`) is built in and data only: no system reads it. Its points and the copies it lists in `slots` are in world
coordinates. Defaults are omitted when it is written.

`EcsLoader(json, registry).loadToEngine(ecs, SceneEngine())` reads `ecs.entities` and returns `EcsLoadingWarns`,
including raw components that could not bind. `RuntimeSceneLoader(sceneLoader, ecsLoader, log)` combines native
scene admission, an independent engine per load, and look-at/camera systems. `load(sceneName)` reads a file from
`scenes/`; `loadFromText(text)` reads supplied text without writing it.

`BaseCtx(projectDir, shadersPath, shadersAnchor, logger)` supplies file/meta loaders, shaders, an asset storage and
executor, registry and scene/ECS loaders. A headless caller may pass null shader path/anchor and never request
GL assets. The host must shut down `executor` and dispose built assets in a valid GL context (or abandon them
when that context has been lost). `EcsWriter` lives in `lib-core-editor`; neither core nor physics depends on it.

The native fixtures and required cloud format do not yet match every Jackson binding path. The
[cloud asset spec](../../openspec/specs/cloud-assets/spec.md) describes the required behavior.

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

## UI assets

A `UI` asset (`MetaType.UI`) holds screens and overlays under `additional.ui`; the schema, limits and a full example are in
[file-formats](../../docs/ai/file-formats.md). `AssetMetaBinder` binds such a meta to `UiMeta(UiAdmission)`: either
`Admitted` (a `UiCollection` plus warnings such as an unknown widget kind or an unresolved binding) or `Refused` (the
reasons, as `UiIssue` codes with a location, no text). `UiCollectionDecoder.decode` is pure: it never modifies the tree,
and unknown widgets keep their authored object. Wire it by constructor (`UiCollectionDecoder(format)`); there is no
singleton. A project's `uiAssetNames` list (`ProjectDto.uiAssetNames`, empty when omitted) names the registered assets,
and `UiProjectRegistry(json, fileLoader).entries(project)` resolves them in order.

## UI host contract

A game hosts authored UI with four collaborators, all passed in by constructor and all used on the host's single UI
thread (the GL thread for rendering). Nothing is global, and the UI never pauses gameplay or starts game code.

- **Snapshot (host to UI).** `UiBindingSession.publish(UiSnapshot)` once per update. A value that is missing or has the
  wrong type shows the binding's declared sample and a diagnostic. Publishing never calls the writer, so there is no
  feedback loop.
- **Write (UI to host).** `UiValueWriter.write(UiWrite)` runs synchronously on the UI thread and answers
  `Accepted(authoritativeValue)` (possibly normalized) or `Rejected(reason)`. The session clamps and snaps a proposal to the
  binding's `min`, `max`, `step` and `maxLength` first, and commits once per gesture (`commitsOn`: slider on release or
  key step, checkbox on toggle, text field on Enter or focus loss). The host must still check authorization and game
  rules. Slow work is scheduled by the host, which answers immediately.
- **Action (UI to host).** `UiActionSink.emit(name)` receives each `emitAction` once per activation; return false when
  nothing handles it and the UI reports it and stays usable. `UiCollectionSource` resolves other registered UI assets for
  cross-asset navigation.
- **Layout.** `UiLayoutEngine(UiMeasurer)` resolves rectangles in the screen's reference size (top-left origin) and
  `fitReference` fits it uniformly into the viewport, centered with letterboxing. A panel places children by anchor,
  pivot and offsets; a row or column positions its children (padding, spacing, `grow`, cross-axis `align`), so free
  offsets do not apply there. Children clip to their parent; too little space clips and reports `OVERFLOW` and never
  changes authored sizes. Hit testing uses the same rectangles (`UiLayoutResult.hit`).

<!-- ui-host-example -->
```kotlin
class HostSettings(var volume: Double = 0.5)

fun runHost(metaTree: com.fasterxml.jackson.databind.JsonNode, settings: HostSettings, started: MutableList<String>): UiBindingSession {
    // 1. Admit the asset: nothing runs from a refused collection.
    val admission = UiCollectionDecoder().decode(metaTree) as UiAdmission.Admitted
    val collection = admission.collection

    // 2. The host owns the callbacks. Writes are synchronous; schedule slow work yourself and answer now.
    val session = UiBindingSession(collection) { write ->
        if (write.binding == "volume" && write.value is UiValue.Num) {
            settings.volume = (write.value as UiValue.Num).value
            UiWriteResult.Accepted(UiValue.Num(settings.volume))
        } else UiWriteResult.Rejected("read-only")
    }
    val navigator = UiNavigator("menus", collection, { null }, { name -> started += name; true })

    // 3. Once per update, publish an immutable snapshot. Publishing never writes back.
    session.publish(UiSnapshot(mapOf("volume" to UiValue.Num(settings.volume))))

    // 4. Lay out for the real viewport; the same engine runs in the editor preview.
    val screen = collection.screen(navigator.currentScreen()!!.screenId)!!
    val layout = UiLayoutEngine(UiMeasurer { UiSizeF(100f, 24f) }).resolve(screen, 1280f, 720f)
    check(layout.diagnostics.isEmpty())

    // 5. Report input: a slider commits once on release, a button activates its action.
    val slider = screen.root.children[0]
    if (commitsOn(UiWidgetType.SLIDER, UiEditGesture.RELEASE)) session.commit(slider, UiValue.Num(0.8))
    navigator.activate(screen.root.children[1].actions.getValue("click"))
    return session
}
```

## UI renderer and resources

`ui.render.UiPresenter` draws and drives one UI collection for the editor preview and for games alike: layers (the
standalone screen and its overlays) are laid out by `UiLayoutEngine`, drawn through one Scene2D `Stage` per layer with a
`FitViewport` of the screen's reference size, and fed pointer and keyboard input through `touchDown` / `touchUp` /
`touchDragged` / `pointerMoved` / `keyDown` / `keyTyped` (screen pixels, top-left origin). A press the UI consumes
returns true; false hands it to the game (only possible below a nonmodal overlay).

- **Lifecycle.** The constructor builds a `SpriteBatch` and a 1x1 pixel texture, so it needs a current GL context;
  `resize`, `render` and the input methods run on the GL thread, and `dispose` (once) releases the stages, batch and
  pixel. The presenter owns neither the resources nor the callbacks. Call `publish(UiSnapshot)` once per update.
- **Resources.** Off the GL thread, `UiResourcePreparer(UiResourceResolver(assetFolder)).prepare(collection)` checks every
  declared resource (CPU only); on the GL thread `UiBuiltResources.Builder().build(prepared)` creates the textures and
  fonts and `dispose()` releases them, once, by the same owner. A failed resource is absent and reported in
  `prepared.issues`; the widget falls back to a placeholder (images) or the default font. `forRenderer(defaultFont)`
  adapts them to the presenter. The default font is shared through `UiBundledFont` leases (`acquire` / `release`); it needs
  `Gdx.files`. Licenses of what is bundled: [ui-bundled-resources](third-party/ui-bundled-resources.md).
- **Formats.** Images are PNG or JPEG, at most 8192 pixels on a side. Fonts are libGDX bitmap fonts in the text BMFont
  `.fnt` format with PNG texture pages; every file must resolve inside the owning UI asset folder (no `..`, absolute
  paths, symbolic links out of it or network URLs). Binary `.fnt`, TrueType and other formats are not supported yet.
- **Host GL setup.** libGDX's own sprite-batch shader is legacy GLSL. In a 3.2 core context the host sets
  `ShaderProgram.prependVertexCode` / `prependFragmentCode` as Control Line and the plugin's `GdxRuntime` do, or the
  presenter's constructor fails to compile the shader.
- **Styling.** `UiStyleResolver` merges the bundled theme (`src/main/resources/ui/default-theme.json`), the widget's named
  style, its `properties.styleOverrides`, then state overrides (disabled over pressed over hover over focused over
  normal). Keys read by the renderer: `color`, `fontSize`, `font` (a resource id), `background`, `fill`, `knob`, `tint`,
  `padding`, `borderColor`, `borderWidth`. Hidden widgets (`visible` binding) hide their subtree.
- **Demo.** `./gradlew :app-game-control-line:runUiDemo` runs the standalone host on its own fixture project; see the
  [game README](../app-game-control-line/README.md#ui-demo).

## Loading and caching

An `AssetLoader<P, U, T>` turns one asset into a GPU object in steps: `prepare(name)` (or `loadPrepared(meta)`) reads and
decodes with no GL and returns a `Prepared(model, staged)`, `dependencies(staged)` names the other assets it needs,
`upload` does one slice of GPU work, `build(staged, assets)` creates the object, `discard(model)` frees the model after
the `onPrepared` hooks have seen it (loaders with nothing to hand them use `Unit` and `Prepared(staged)`), and
`discardStaged` frees the staged value: after a successful build, or when it is dropped unbuilt.

One `AssetStorage(log, executor, metas)` loads every kind of asset of a project. It reads an asset's base `meta.json`
through `metas` and hands the asset to the loader registered for its `MetaType` (`register(loader, MetaType.X)` or
`registerAll(map)`); a name with no meta or no loader for its type fails as absent. The storage owns the built assets of
every kind, keyed by asset folder name, which is what lets assets depend on each other. `register(loader, handles)`
chooses a loader by name instead, and `AssetStorage(executor, loader, log)` registers one loader for every name.

- `request(name)` starts a load on the executor; every name loads once and is shared, and a failure is logged once and
  remembered, so it is not retried every frame.
- `update(maxSteps)` (GL thread) advances `upload` and `build`, one asset at a time, and returns true when something
  changed. `get(name)` / `getAs<T>(name)` is null until the asset is built.
- Once an asset is prepared, the storage requests its `dependencies` and holds its upload and build until each is built
  or has failed. An asset whose dependency failed fails with a `DependencyFailedException`. Assets that depend on each
  other in a cycle (including an asset that needs itself) cannot be built before one another, so every member fails
  with a `CyclicDependencyException` as soon as the last of them is prepared, and whatever needs a member fails too.
- `isLoading()` / `isLoading(name)` say whether anything, or one asset, is still loading.
- `invalidate(names)` marks names as changed on disk: a loaded asset stays in use until its replacement is built, then
  the two swap in one step; a superseded load is discarded. `version(name)` changes when `get` returns another asset.
- `retain(names)` disposes everything neither named nor needed by a named asset, `abandon()` forgets assets without
  disposing them (the GL context is gone), `dispose()` releases everything.

`AssetIndex` stays a pure lookup (asset folder of a `uuid`); the storage, not the index, owns the built assets.

```kotlin
val assets = AssetStorage(log, executor, metaLoader::loadBaseMeta)
assets.registerAll(mapOf(
    MetaType.MODEL to ModelLoader(metaLoader, assimp, fileLoader),
    MetaType.TERRAIN to TerrainLoader(fileLoader, metaLoader),
    MetaType.TEXTURE to TextureLoader(fileLoader, metaLoader),
))
assets.request("terrain_x")                     // its splat textures load first
assets.update()                                 // once per frame, GL current
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
(centre column faces -Z, top row is +Y), at most `RAY_SKY_MAX_WIDTH` (1024) wide (a procedural sky baked by the view is 2048 wide, bilinear, as 8-bit `rgba8()` data). `HdrSkyRaySnapshotLoader` decodes an
HDR sky again on the CPU and box-filters it down, keeping linear radiance far above 1; `SkyboxRaySnapshotLoader`
resamples a cube skybox with the GL cube lookup the raster sky uses (faces are back, front, left, right, bottom, top =
+X, -X, +Y, -Y, +Z, -Z) and keeps its display values. `ProceduralSkyRaySnapshotLoader` returns null: a procedural sky is
the asset's own GLSL and has no CPU form, so it reads as unavailable and the caller falls back to the background colour.
Leases share one read per asset, drop the bytes with the last reference (default bound 128 MiB in the plugin's wiring),
and never touch the GPU caches or GL.

## Procedural sky light and the cloudy environment

A `SKYBOX_PROCEDURAL` asset with the boolean `additional.lightsScene: true` (anything else does not opt in) gives the
scene the light of its sky. `ProceduralSky` then owns a per-view `ProceduralSkyLighting`: an Abyssus-owned linear
atmosphere plus shell clouds (`atmosphere_linear.frag`) is rendered into a 64²-per-face `RGBA16F` cube by
`ProceduralLightingCube`, and `HdrEnvironmentBuild` turns that `CubeSource` into an `HdrEnvironment` (specular and
irradiance cubes and six axis colors), one step per frame. The pure `LightingRefreshPolicy` decides when to rebuild
(sun moved 1° or more, every 2 s of cloud time while a band has wind, cloud asset reload or visibility change), coalesces
newer requests, retries failures with bounded backoff, pauses while hidden, and fades the new environment in over 1 s.

Other features that reflect or sample the sky (water reflection fallbacks, ray tracing misses) use the same cube
without opting the scene's ambient light in:

```kotlin
sky.acquireEnvironment().use { lease ->      // GL thread; starts building it, nothing is built without a lease
    val environment = lease.environment      // borrowed HdrEnvironment, or null while the first build is under way
    val revision = lease.revision            // changes when a new environment replaced the old one
}
```

The sky owns every environment and disposes it; a consumer borrows it for the frame and must not dispose it or keep it
across a changed `revision`. `ProceduralSky.updateLighting` (called once per frame by the owner) drives the builds, only
while a lease is open or the asset opts in; the last `close()` releases the lighting of a sky that does not opt in.
Closing a lease twice is harmless, and after the sky is disposed a lease hands out nothing. A custom `sky.frag` that departs
from the asset's atmosphere parameters can look different from this light; that is a documented limitation.

## Tests

`./gradlew :lib-core:test`; GL tests opt in with `-Dabyssus.glTests=true`. Tests read the repository's
`projects/plugin-abyssus/src/test/testData/project` fixtures through `testProject(name)`; `testFileLoader` and `testMetaLoader` build a project's
loaders, and `exrFixture()` gives the bundled EXR sky as a file (`TestData.kt`). The GL context is `lib-gdx`'s `TestGl`
test fixture. `HdrFixtures` and `RecordingLogger` are in `src/testFixtures`, shared with the plugin's tests. `AssetStorageTest` covers the loading pipeline with a fake loader and a
queued executor.

## Terrain format

A terrain's heights are big-endian floats, z-major, with no header; `TerrainLoader` reads them and infers a square
resolution. Metadata starts with `format: "abyssus"` and `formatVersion: 1`, then the existing metadata fields.
Generation and writing new terrains are in the plugin. The retained height encoding was originally compared against the
upstream implementation; see [model source provenance](../../docs/third-party/gdx-model-origin.md). This is historical
evidence, not a compatibility guarantee.

Cloud metadata uses lowercase technique/type keys, container-inferred band levels and `wind: [x, z]`.
`CloudSettingsReader` also accepts the existing enum-name and `windX`/`windZ` representation without rewriting it.
It resolves type defaults, checks finite values and band limits, and logs/skips invalid bands independently.
The snapshot editor and runtime share this decoder; metadata reading generates no noise or GL resources.
