# Proposal

## Why

Asset loading lives inside the plugin and reaches its collaborators through IntelliJ singletons:
`ProjectAssetFiles` parses every `meta.json` through `service<JsonProcessor>()`, and `AssetCache` logs through
IntelliJ's `Logger`. Outside a running IDE that lookup fails, so every asset reads as "missing or unreadable". Since
commit `29c73ef` every scene GL test loads nothing (`mainSceneDrawsModelsAndTerrain` draws 0 of 3 models), and the
`add-hdr-skybox-ibl` change cannot verify its rendering tasks. Moving loading into its own plain JVM module, wired by
constructors instead of global lookups, makes it run the same in the IDE, in GL tests and in any other libGDX tool.

## What Changes

- A new Gradle module `core` (root package `net.nevinsky.abyssus.assets`), a plain JVM library like `gdx-model`:
  Kotlin, libGDX, Jackson and `:gdx-model`, with no IntelliJ dependency. The plugin depends on it.
- Moved into `core`, behaviour unchanged:
  - asset folder and `meta.json` reading (`ProjectAssetFiles`' file part as `AssetFiles`; `MetaBase`, `MetaType`,
    `Asset`; the asset-folder constants of `ProjectLayout`) and `JsonProcessor`;
  - the loading pipeline: `AssetLoader`, `AssetCache`, `SceneAssets`;
  - every loader with the drawables it builds: `ModelLoader`; `TerrainLoader`, `TerrainData`, `TerrainMesh`;
    `SkyLoader` with the six-face cube, the procedural sky and the HDR sky (decoder, environment build, tone curve,
    preview), and the sky shaders they draw with.
- Dependencies are passed through constructors. Code in `core` has no `object` or `companion object` holding
  behaviour or state; a composition root (`AssetLoading`) builds the loaders from an injected JSON processor, log,
  executor and shader source. The plugin builds one per project; tests build their own.
- Load problems are reported through an injected `AssetLog`; the plugin's implementation writes the IDE log as
  today.
- The plugin keeps rendering (`SceneRenderer`, the per-kind scene asset holders, `TerrainShader`, `SunDirection`),
  `GdxRuntime`, the canvas, the tree's VFS-based asset listing, Swing and every IntelliJ service.
- **BREAKING (internal API only):** the moved classes change package; `ProjectAssetFiles` is no longer an IntelliJ
  project service; `JsonProcessor.of(application)` and the `Shaders` object are removed. No user-visible change.

**Mundus files.** Read exactly as today: asset `meta.json` (`type`, `additional`, `uuid`) and the files it names, the
terrain `.terra` data, `.hdr` images. Nothing is written by this change, and the file format does not change.

**Out of scope.**

- Converting plugin code outside the loading path to injected instances (`ScenePicker`, `SceneMarkers`, the bundle,
  the tree builders). Only code in `core` must be free of singletons.
- Renaming `gdx-model`'s `net.nevinsky.abyssus.lib.gdx.*` packages.
- Moving rendering, picking, gizmos, cameras or the scene shaders that are not sky shaders into `core`.
- A DI framework (Koin, Dagger). Wiring is plain constructors.
- Any behaviour change in what is drawn, when assets load, or how failures look.

## Capabilities

### New Capabilities

- `asset-loading`: loading a Mundus project's assets (models, terrains, skies) into drawable objects without the IDE
  runtime, reporting problems through the caller's log, with no state shared between independent loaders.

### Modified Capabilities

None. The rendering capabilities (`scene-model-rendering`, `scene-terrain-rendering`, `scene-skybox-rendering`,
`scene-procedural-sky`) keep their requirements; only where the code lives changes.

## Impact

- **Build:** `settings.gradle.kts` includes `:core`; new `core/build.gradle.kts`; the plugin gains
  `implementation(project(":core"))`. `./gradlew check` runs `:core:test` too.
- **Code moved:** `sceneview/AssetCache.kt`, `SceneAssets.kt` (the generic part), `ProjectAssetFiles.kt` (file part),
  `MetaBase.kt`, `Asset.kt`, `JsonProcessor.kt`, `sceneview/model/ModelLoader.kt`, `sceneview/terrain/TerrainLoader.kt`,
  `TerrainData.kt`, `TerrainMesh.kt`, everything under `sceneview/skybox/` except `SunDirection.kt`, and
  `shader/scene/skybox.*`, `hdr_*`, `hdrsky.*`. Their tests move to `core/src/test`.
- **Plugin callers updated:** `SceneRenderer`, `SceneModels`, `SceneTerrains`, `SceneSkybox`, `TerrainShader`,
  `SceneViewPanel` / `SceneFileEditor` (wiring), the dto readers that use `JsonProcessor`, `SkyboxChoices`,
  `PanelState`, and a new light application service `AbyssusCore` that wires the graph (`ProjectAssetFiles` and
  `JsonProcessor` were light services registered by annotation, so `plugin.xml` does not change).
- **Tests:** plugin GL tests construct `AssetLoading` directly and load assets again.
- **Docs:** `AGENTS.md` (layout, commands, hard rules), `docs/ai/architecture.md`, `docs/ai/testing.md`,
  `sceneview/README.md`, a new `core/README.md`.
- **Other changes:** `add-hdr-skybox-ibl` resumes after this one; its remaining tasks name plugin paths that move here.
