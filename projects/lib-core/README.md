# lib-core

Native project DTOs, scene/ECS loading and asset loading, as a plain JVM library: the Abyssus plugin uses it, and so can any
libGDX tool or test that has a project folder and a GL context. Root package `net.nevinsky.abyssus.lib.core`; the asset
code is in `net.nevinsky.abyssus.lib.core.assets`.

## Rules

- **No IntelliJ or plugin imports.** Depends on `:lib-gdx`, libGDX, Ashley, SLF4J, Jackson and LWJGL TinyEXR for OpenEXR decoding.
- **Wired by constructors.** No `object` or `companion object` in `src/main` (a `data object` case of a sealed type is a
  value and is fine); pure constants are top-level `const val`. `./gradlew :lib-core:checkNoSingletons`, part of `check`,
  enforces it; `AbyssusProjectLayout` is its only exception. Collaborators are passed in; nothing
  is looked up globally.
- **Files go through `FileLoader`, metas through `AssetMetaLoader`.** Loaders and stores take them (one pair per project
  folder) instead of reading paths themselves.
- **GL only on the caller's thread.** `prepare` steps do IO and decoding with no GL; `upload`, `build` and every
  `draw` need a current GL context. The plugin calls them inside its `GdxRuntime.withContext`.

## Contents

| Package | What |
|---|---|
| `core.io`, `core.util` | `FileLoader` (an asset folder's files, refusing names that leave the assets folder), `AbyssusProjectLayout` (folder and file name constants), `JsonProcessor` (binds native JSON), `NativeJsonTree` (the lossless reader: key order, nulls and number text such as `2.50` and `1.0E-4` kept; `SceneJson` and `SceneLoader` both read through it), `createFullscreenTriangle` (GL-thread mesh factory), shared digest/angle helpers and GL constants |
| `core.defaults` | Top-level `NO_ENTITY` reference sentinel |
| `core.dto` | `ProjectDto`, `SceneDto`, camera, light and fog DTOs |
| `project`, `scene` (under `lib-core`) | `ProjectLoader`, `SceneLoader`, `RuntimeSceneLoader`, `SceneContext` |
| `core.ecs` | Component registration and loading, `SceneEngine`, `SceneEntityIds`, `SceneFoliageListing` (the terrain-bound foliage of a loaded engine: components only, no files or GL); `component/` holds Ashley classes and render-asset lookup, `system/` holds look-at and camera synchronization |
| `core.assets` | `AssetMeta` and `MetaType` (the `meta.json` model; `uuid` is null when a meta declares none), `AssetMetaBinder` (one injectable settings-class registration map and metadata binding rule), `AssetMetaLoader` (validates and reads saved `meta.json`, binds through the binder, caches by timestamp and size), `AssetIndex` (the asset folder of a `uuid`), `Asset`, `runCatchingKeepingCancellation`, `Throwables` |
| `core.ui` | The native UI asset: `ui.model` holds the immutable screen, widget, binding and resource definitions, `UiMeta` (the typed `additional` of a `UI` asset) and `UiAdmission`; `ui.validation` holds `UiCollectionDecoder`, which admits a `meta.json` tree (native markers, schema version 1, ids, geometry, limits) without IO. `project.UiProjectRegistry` resolves the project's `uiAssetNames`; `ui.layout` is the pure layout engine; `ui.state` holds typed bindings, validated write-back and navigation; `ui.render` is the shared Scene2D presenter; `ui.style` resolves styles; `assets.ui` prepares and owns the GL resources. See *UI assets*, *UI host contract* and *UI renderer and resources* below |
| `core.assets.loading` | `AssetLoader` (prepare / dependencies / upload / build / discard), `AssetStorage` (the cache and owner of built assets: load once, fail once, slice GPU work per frame, load dependencies first) with `BuiltAssets`, `TextureUploadQueue`, `LoadBook` (package-internal, GL-free: the current load and the replacement of each name, which generation owns a message, dependency satisfaction and failure, cycles, retain closure; `AssetStorage` keeps upload, build, discard and notifications), `ShaderStorage` (GLSL from a resource folder; `#pragma include` lines are expanded from `lib-gdx`'s shared GLSL) |
| `core.assets.model` | `ModelLoader` (glTF and other formats through `lib-gdx`'s Assimp loader), `ModelMeta`, `ModelPreparationTap` |
| `core.assets.terrain` | `TerrainLoader`, `TerrainData`, `TerrainMesh` (`splatTexture()` is the built splat map), `TerrainMeta`, `SplatTexture` (updates a rectangle of a built splat map `Texture` in place with `glTexSubImage2D` from RGBA bytes, so a live paint stroke re-sends only the changed texels; `packSplatRows` is its pure row packing) |
| `core.assets.foliage` | `FoliageLoader` (`FOLIAGE` assets: reads the meta, the masks and the bake off the GL thread and declares the terrain and layer models as dependencies), `FoliageMeta` and the layer model types, `FoliageMaskFile` (`layer-<id>.mask`: `maskResolution²` bytes z-major, a missing file is a full mask), `FoliageDataFile` (the `ABFO` bake: `FoliageBake`, `FoliageLayerBake`, `FoliageCopy`, read as stale when it does not fit), `FoliageFingerprint` (SHA-256 of the generation inputs), `FoliageRuntimeLoad` (the read policy of games and physics: metadata, terrain, bake and masks as written, never generated or written; a stale or unverifiable bake keeps its saved copies, copies that cannot be placed are dropped, and problems are returned as bounded warnings), `FoliageCollider` / `FoliageColliderReader` (a layer's optional neutral BOX/SPHERE/CAPSULE `collider`, with the `ColliderComponent` defaults; absent is told apart from invalid) and `FoliageDrawable` (the instanced GL side, reused by the runtime) |
| `core.assets.texture` | `TextureLoader` (`TEXTURE` and `PIXMAP_TEXTURE` assets: image decoded off the GL thread, uploaded as a mipmapped repeating texture), `PreparedTexture` (the decoded image; `release()` hands the `Pixmap` to a caller that uploads it itself) and `TextureMeta` |
| `core.assets.sky` | `SkyRenderer` (a drawable background); `cube/` six-face skyboxes, `procedural/` skies drawn by the asset's own GLSL, `hdr/` OpenEXR skies and their lighting environment. Each has a `*Loader` |
| `core.water` | Realistic water viewed from above, as plain constructor-wired classes with no IDE or editor imports: `WaterSettings` / `WaterPresets` / `WaterValidation` (the saved scalars, the Lake and Sea defaults and their range rules), the pure math the renderer and its tests share (`WaterWaves` and `WaterMeshPlanner` for the bounded wave grid, `WaterReflectionCamera`, `WaterDepthMath` for the packed linear depth, `WaterScreenArea`, `WaterPassPlanner` for the per-frame reflection budget), and `WaterDrawable` with its GLSL under `resources/shader/water` (`ShaderStorage.withWaterShaders()`). The scene view owns the offscreen targets and the pass order; see `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md` |
| `core.assets.sky.clouds` | The clouds of a procedural sky: `CloudMeta`, nested `CloudMeta.CloudBand`, `CloudType` (level and defaults), `CloudLevel` with its `CloudBandLimits`, `CloudTechnique`; settings decode through `AssetMetaBinder`; `CloudsLoader` and `Clouds` (the `CLOUDS` asset: bands, technique and the volumetric 3D noise from `CloudNoiseGenerator`, built on FastNoiseLite), which a procedural sky names by `uuid` and loads as a dependency; `CloudField`, the cloud techniques and `SunOcclusion` |

`ExrLoader.dimensions` and `HdrPreview.dimensions` read the EXR data-window dimensions without decoding pixels.
Header parsing is shared with decoding; native headers and images each have explicit cleanup. Chooser and panel
labels use original dimensions, while previews retain their existing reduction and tone mapping.

`SceneDto` does not bind `rayTracingEnabled` or `rayTracing`: those scene keys are editor-only data, read by
`lib-core-editor` from the validated document, and a game ignores them as unknown properties.

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
Cloud techniques are `CloudRenderer`s chosen by `CloudTechnique`: `layered` (one flat layer per band), `cumulus`
(`clouds_cumulus.frag`: the same flat layer shaded as heaped cloud, with an eroded ragged edge, a dome from the coverage
and a tap toward the sun that brightens the sun-facing side and greys the base; one pass per band), `shells` and
`volumetric` (`clouds_volumetric.frag`: a ray-marched band; a heaped band gets a stronger lift from the base noise, a harder detail erosion, a sharpened edge, a higher extinction and a brightened sunlit side, so cumulus heads stand round on a flat base, while other profiles keep the soft shape). A technique that cannot be built falls back volumetric, shells, cumulus, layered, none.

**Sky lighting and the cloudy-sky environment.** A procedural sky with `additional.lightsScene: true` owns a
`ProceduralSkyLighting` (a linear 64²-per-face cube from `atmosphere_linear.frag` and the shell clouds, turned into specular
and irradiance cubes by `HdrEnvironmentBuild` over a borrowed `CubeSource`, rebuilt as `LightingRefreshPolicy` says and faded
in); the host advances it with `ProceduralSky.updateLighting` every frame and lights with `ProceduralSky.lightingEnvironment`.
Any other feature that needs to *see* the sky from a surface (water reflection fallbacks, ray tracing misses) can ask for the
same cube without `lightsScene`:

- Call `ProceduralSky.acquireEnvironment()` once when the feature needs the cube, on the GL thread, and keep the returned
  `EnvironmentLease` open while it does; the host's per-frame `updateLighting` then keeps it current. Nothing is built for a
  sky nobody leases, and the scene's ambient color is not replaced.
- The lease's `environment` holds the linear `RGBA16F` cube (mips filled, display tone mapping not applied: not a
  picture of the sky), or null until the first build is done, and its `revision` grows with every rebuild. Rebuilds happen when the sun moves over a degree, every
  two seconds of cloud time while a band has wind, and when the cloud asset is reloaded, replaced or removed. Compare
  revisions to know when to refresh whatever was taken from it (a CPU snapshot, an accumulation).
- The cube belongs to the sky: do not dispose it, and do not keep it past the next update. Close the lease before the sky is
  disposed; closing the last lease releases the cube unless the asset opts in with `lightsScene`. It is disposed with the sky
  and abandoned with the GL context.

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

`EcsLoader().loadToEngine(scene.ecs, SceneEngine())` loads the typed `Map<Long, EntityDto>` that `SceneLoader(json, files, registry)` bound, and returns `EcsLoadingWarns`,
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
singleton. Lists (`list` widget, `list` bindings, `item.<field>` bindings, bound `style`) are expanded per update by `expandLists`
into a plain tree of row copies, so layout, painting, focus and hit testing know nothing about items; `UiValue.Rows` is
the value a host publishes. A screen with `navigation: "arrows"` moves focus with Up / Down and focuses its first usable
control when shown. `UiPresenter.display(screenId, assetName)` shows exactly one screen or overlay (a game's state
machine decides), reporting an unknown one in `lastNavigationFailure`; `publish` re-lays out only when the snapshot
differs from the last one. A project's `uiAssetNames` list (`ProjectDto.uiAssetNames`, empty when omitted) names the registered assets,
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
            settings.volume = write.value.value
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
  `padding`, `borderColor`, `borderWidth` (drawn whenever set, so a state override such as `focused` gives a focus ring), `uppercase` (a boolean: the text is measured and drawn in upper case; the widget's own text is unchanged). Hidden widgets (`visible` binding) hide their subtree.
- **Example.** Control Line's whole UI is a `UI` asset driven this way (`GameUiHost`, `GameUiModel`); see the
  [game README](../app-game-control-line/README.md#screens).

## Loading and caching

An `AssetLoader<P, U, T>` turns one asset into a GPU object in steps: `prepare(name)` (or `loadPrepared(meta)`) reads and
decodes with no GL and returns a `Prepared(model, staged)`, `dependencies(staged)` names the other assets it needs,
`upload` does one slice of GPU work, `build(staged, assets)` creates the object, `discard(model)` frees the model after
the `onPrepared` hooks have seen it (loaders with nothing to hand them use `Unit` and `Prepared(staged)`), and
`discardStaged` frees the staged value: after a successful build, or when it is dropped unbuilt.

`MetaAssetLoader` implements the shared metadata lookup and delegates to `loadPrepared` for model, texture, terrain,
skybox, HDR sky, procedural sky and clouds. Its constructor is internal; the base is public so the existing public
loaders can inherit it. `discard` defaults to no work for loaders with no prepared model to release.

`FoliageLoader` takes a CPU-only `FoliageReadPolicy`. `EditorFoliageReadPolicy` reads masks and computes the current
fingerprint so a stale bake can be regenerated; `RuntimeFoliageReadPolicy` uses `FoliageRuntimeLoad`, retaining saved
copies and reporting warnings without generating anything. The original editor constructor and the delegating
`RuntimeFoliageLoader` entry point remain available. Both policies share `foliageDependencies` and the same build path.

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

`StandardAssetLoaders(fileLoader, metaLoader, shaders)` (in `assets/standard/`) builds the loader of every drawn kind
once; hosts register its `byType` map instead of listing loaders themselves. A host that needs only some kinds can still
register a smaller map:

```kotlin
val assets = AssetStorage(log, executor, metaLoader::loadBaseMeta)
assets.registerAll(StandardAssetLoaders(fileLoader, metaLoader, shaders).byType)   // or a subset, for example:
assets.registerAll(mapOf(
    MetaType.MODEL to ModelLoader(metaLoader, assimp, fileLoader),
    MetaType.TERRAIN to TerrainLoader(fileLoader, metaLoader),
    MetaType.TEXTURE to TextureLoader(fileLoader, metaLoader),
))
assets.request("terrain_x")                     // its splat textures load first
assets.update()                                 // once per frame, GL current
val terrain = assets.getAs<TerrainMesh>("terrain_x")   // null until built
```

## Observing a prepared model

`ModelLoader` takes an optional `ModelPreparationTap`: `begin(name)` returns a `Capture` (or null), and the loader offers
it the parsed `ModelData` and decoded images once, on the prepare thread, before the textures are uploaded. A host uses it
to copy what the loader already holds (the editor's ray snapshots do); the loader keeps ownership and a cancellation from
`offer` disposes the prepared model. A game passes none.

## Model variants (one mesh of a model)

A model entity may name a `meshId` (`RenderComponent.meshId`). The asset storage then builds a *variant* of the model
asset: `ModelKey(asset, meshId).encode()` is the storage name (the bare asset name for the whole model, so callers never
join strings themselves) and `AssetStorage` reads the variant's `meta.json` from the base folder. `ModelLoader` cuts the
parsed `ModelData` down with `selectMesh` before images are decoded: the other meshes, the materials only they use and
the node parts that pointed at them go; the node tree, bones and animations stay, so the mesh sits where it does in the
whole model. A mesh id the model lacks fails only that variant, with the model's mesh ids in the log. `invalidate`
replaces every loaded variant of an asset, and `retain` keeps a variant whose encoded name is in its set. The hooks and
`ModelPreparationTap` receive the encoded name. The ray-tracing CPU snapshots themselves live in `lib-core-editor`
(`editor.ray.snapshot`), not here.

## Skies write display colour

A sky draws display-referred colour: the stock procedural atmosphere, a custom `sky.frag` such as Control Line's, cube and
HDR skies all tone map or encode inside their own shaders. A host that lights its models in linear space and ends them in
an `OutputTransform` (`lib-gdx`) therefore draws the sky first, untouched, and the transform applies only to what is drawn over
it; `SkyFrame` carries no transform. The sky's *lighting* cube is separate: it is linear radiance (see below).

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
test fixture. `HdrFixtures` and `RecordingLogger` are in `src/testFixtures`, shared with the plugin's tests. `LoadBookTest` covers the bookkeeping with fake loads; `AssetStorageTest` covers the loading pipeline with a fake loader and a
queued executor.

## Terrain format

A terrain's heights are big-endian floats, z-major, with no header; `TerrainLoader` reads them and infers a square
resolution. Metadata starts with `format: "abyssus"` and `formatVersion: 1`, then the existing metadata fields.
Generation and writing new terrains are in the plugin. The retained height encoding was originally compared against the
upstream implementation; see [model source provenance](../../docs/third-party/gdx-model-origin.md). This is historical
evidence, not a compatibility guarantee.

Cloud metadata uses lowercase technique/type keys, container-inferred band levels and `wind: [x, z]`.
`AssetMetaBinder` also accepts the existing enum-name and `windX`/`windZ` representation without rewriting it.
It resolves type defaults and logs/skips invalid bands independently; `CloudBandLimits` bound the built-in type defaults.
The snapshot editor and runtime share this decoder; metadata reading generates no noise or GL resources.
