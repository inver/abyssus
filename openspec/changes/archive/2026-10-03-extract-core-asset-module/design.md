# Design

## Context

See proposal.md - Why. The loading code today:

- `ProjectAssetFiles` (a light `@Service(Service.Level.PROJECT)`) finds an asset's files and parses `meta.json` through
  `service<JsonProcessor>()`. It also holds `loadShortAssets(VirtualFile)`, the VFS listing the Abyssus tree uses.
- `JsonProcessor` is a light application service, reached through `service<JsonProcessor>()` from `dto/ProjectReader`,
  `dto/SceneReader` and `ProjectAssetFiles`, and through `JsonProcessor.of(application)`.
- `AssetCache` (prepare on an executor, upload and build on the GL thread) logs through IntelliJ's `Logger`;
  `SceneAssets` gives each scene-view holder (`SceneModels`, `SceneTerrains`, `SceneSkybox`) a cache per project folder.
- `SceneRenderer` takes `SceneLoaders` (model, terrain and sky loaders) as a default argument; `SceneViewPanel` builds
  `SceneRenderer()` with defaults.
- Singletons in the code that moves: `object` `Equirect`, `HdrPreview`, `HdrSkyFiles`, `HdrToneMap`, `RadianceDecoder`,
  `Shaders`; `companion object` in `JsonProcessor` (`of`), `RadianceHeader` (`of`, `read`, `MAX_HEIGHT`),
  `HdrEnvironmentBuild` (sizes, GL constants), `TerrainData`, `TerrainMesh` (`SPLAT_UNIT`), `ProceduralSkyMeta`.
- `gdx-model` is the precedent for a plain JVM module (`java-library`, Kotlin with its own stdlib, libGDX as `api`).
  It already owns the package root `net.nevinsky.abyssus.lib.core`.

## Goals / Non-Goals

**Goals:**

- `core` compiles and tests with no IntelliJ artifact on its classpath; a GL test builds the whole loading graph with
  `new`-style constructor calls.
- Every collaborator of a `core` class arrives through its constructor; nothing in `core` looks anything up globally.
- The plugin behaves exactly as before; only wiring and imports change there.

**Non-Goals:**

- Converting plugin code outside the loading path (see proposal, Out of scope).
- Changing what the loaders read, decode, upload or draw, or their step-per-frame slicing.
- A DI container.

## Decisions

### 1. Module shape

`core/build.gradle.kts` mirrors `gdx-model`: `java-library` + Kotlin JVM 21, `implementation(kotlin("stdlib"))`
(the root disables the default stdlib for the IDE plugin), `api(project(":gdx-model"))`, `api` Jackson
(`jackson-databind`, `jackson-module-kotlin`, same versions the plugin uses), JUnit 4 for tests, and the same opt-in GL
test dependencies as `gdx-model` (`lwjgl3-awt`, `lwjgl-opengl`, `gdx-backend-lwjgl3`, natives). Resources: the sky
shaders under `core/src/main/resources/shader/sky/`.

Root package `net.nevinsky.abyssus.assets`:

| Package | Holds |
|---|---|
| `assets` | `AssetLoading` (composition root), `AssetLog`, `ShaderSource`, `AssetLayout` constants |
| `assets.files` | `AssetFiles` (was `ProjectAssetFiles`' file part), `MetaBase`, `MetaType`, `Asset`, `TerrainFiles` |
| `assets.json` | `JsonProcessor` |
| `assets.loading` | `AssetLoader`, `AssetCache`, `SceneAssets` |
| `assets.model` | `ModelLoader`, `PreparedModel` |
| `assets.terrain` | `TerrainLoader`, `TerrainData`, `TerrainDataReader`, `TerrainMesh` |
| `assets.sky` | `SkyLoader`, `PreparedSky`; `cube/`, `procedural/`, `hdr/` with each kind's loader, meta and drawable |

`PlacedEntities` and `PlacementTransform.toMatrix` (also in `SceneAssets.kt`) are scene placement, not loading; they
stay in the plugin, split into their own file.

**Alternative rejected:** package `net.nevinsky.abyssus.lib.core` to match the module name. It would split one package
root across two jars with `gdx-model`.

### 2. Constructor injection and one composition root

```
AssetLoading(json: JsonProcessor, log: AssetLog, executor: Executor, shaders: ShaderSource)
  ├─ files(projectDir): AssetFiles                 = AssetFiles(projectDir, json)
  ├─ models:   AssetLoader<PreparedModel, Model>   = ModelLoader()
  ├─ terrains: AssetLoader<PreparedTerrain, TerrainMesh> = TerrainLoader(TerrainDataReader())
  ├─ skies:    AssetLoader<PreparedSky, Sky>       = SkyLoader(cube, procedural, hdr)
  │              hdr = HdrSkyLoader(RadianceDecoder(), HdrSkyFiles(), shaders, log)
  └─ assets(loader): SceneAssets<P, T>             = SceneAssets(executor, loader, ::files, log)
```

`AssetLoading` holds no state of its own beyond these instances; each `SceneAssets` it creates owns its caches, so
two scene views (or two tests) never share a cache. `SceneAssets` receives the `files` factory instead of calling a
constructor that reaches a service, and `AssetCache` receives `AssetLog` instead of IntelliJ's `Logger`.

The plugin's composition root is one light application service, `AbyssusCore` (`@Service(APP)`, plugin code):
it creates a `JsonProcessor`, an `AssetLog` that writes `Logger.getInstance("Abyssus.assets")`, the
`AppExecutorUtil` pool and `ShaderSource("/shader/sky")`, and from them one `AssetLoading`. `SceneViewPanel` passes
`service<AbyssusCore>().loading` to `SceneRenderer`, which no longer has a loaders default argument; the dto readers
take `service<AbyssusCore>().json`. Tests construct `AssetLoading` directly with a recording `AssetLog`.

**Alternative rejected:** a project-level service. Loaders hold no per-project state (the per-project cache lives in
each `SceneAssets`), so one application-level graph is enough and is what `JsonProcessor` already was.

### 3. Singletons become instances

| Today | In `core` |
|---|---|
| `object RadianceDecoder`, `RadianceHeader.Companion.read/of` | `class RadianceDecoder` with `read`, `header`, `headerOf` |
| `object HdrSkyFiles`, `object Equirect` | `class HdrSkyFiles`, `class EquirectProjection` |
| `object HdrToneMap` | `class ToneCurve(exposure = 1f)`; its constants are the curve's parameters |
| `object HdrPreview` | `class HdrPreview(decoder, curve)` |
| `object Shaders` | `class ShaderSource(root: String)` with `program(vertex, vararg fragment)`; the plugin makes its own for `/shader/scene` |
| `TerrainData.Companion` factory | `class TerrainDataReader` |
| `JsonProcessor.Companion.of` | removed; the instance is injected |
| companions that only hold `const val` (`HdrEnvironmentBuild`, `TerrainMesh.SPLAT_UNIT`, `MAX_HEIGHT`) | top-level `const val` in the same file |
| `ProceduralSkyMeta` / `AtmosphereParams` defaults companion | default constructor arguments |

A `data object` case of a sealed type (`AssetCache.State.Failed`) is a value, not a singleton, and stays.

A `checkNoSingletons` Gradle task in `core`, wired into `check`, fails when a `core/src/main` Kotlin file declares
`object <Name>` or `companion object` other than `data object`. Object *expressions* (`object : Setter`) are allowed.

**Alternative rejected:** top-level functions for the pure maths (`Equirect`, the tone curve). They are static in all
but syntax, and the curve already has a parameter (exposure) an instance can carry.

### 4. What stays in the plugin

`SceneRenderer`, `SceneModels`, `SceneTerrains`, `SceneSkybox`, `PlacedEntities`, `TerrainShader` and `terrain.*`,
`SunDirection` (it reads scene light placements), `SceneAmbient`, `GdxRuntime`, `GuardedGLCanvas`, the plugin's
scene shaders (`lines`, `overlay`, `terrain`), the dto readers, and the VFS listing formerly
`ProjectAssetFiles.loadShortAssets`, which moves to `dto/ProjectAssetListing.kt` and takes the `JsonProcessor`.

`core` drawables take libGDX types (`Camera`, `Vector3`); the plugin converts its `Vec3` sun direction at the
`SceneSkybox` call. `TerrainMesh.draw(program, blank)` keeps taking the plugin's terrain program.

### 5. Cancellation

`core` catches with its own `runCatchingKeepingCancellation`, which rethrows `kotlin.coroutines.cancellation.
CancellationException`. IntelliJ's `ProcessCanceledException` extends it, so cancellation still reaches the platform
without `core` naming IntelliJ. The plugin keeps its own copy, which also names `ProcessCanceledException` explicitly.

### Threads

| Piece | Thread | GL / context |
|---|---|---|
| `AssetFiles`, `JsonProcessor`, `RadianceDecoder`, `TerrainDataReader`, loaders' `prepare` | pool (`AssetCache` executor) | none |
| `AssetCache.pump`, loaders' `upload` / `build`, drawables' `draw` | AWT render thread, inside the plugin's `GdxRuntime.withContext` | yes |
| `HdrPreview` (chooser, properties panel) | pooled background thread | none |
| `AbyssusCore` construction | first `service<AbyssusCore>()` caller | none |

`core` does not know `GdxRuntime`; its GL entry points document that a context must be current, as `gdx-model` does.

### Testable without GL or the platform

`AssetFiles`, `JsonProcessor`, `RadianceDecoder`, `HdrSkyFiles`, `EquirectProjection`, `ToneCurve`, `HdrPreview`,
`TerrainDataReader`, `AssetCache` (with a fake loader and a direct executor), every loader's `prepare`. GL tests in
`core` (opt-in with `-Dabyssus.glTests=true`) use a small AWT GL context like `gdx-model`'s `TestGl`.

## Risks / Trade-offs

- **Large mechanical move.** Many imports change at once. → One task group per package, each ending with both
  modules compiling and their tests passing; `git mv` keeps history.
- **Open changes name moved paths.** `add-hdr-skybox-ibl` tasks 3.2, 4.3, 6.x and `add-scene-object-drop` /
  `add-light-entities` may reference `sceneview/...` loader paths. → A task amends their artifacts to the new paths.
- **Plugin tests that build loaders.** `SceneRenderGlTest`, `SkyboxMetaTest`, `TestAssets` construct today's classes.
  → They switch to `AssetLoading`; the GL tests then load assets again (the point of the change).
- **Jackson version drift.** `core` must use the plugin's Jackson so `MetaBase` binding behaves the same. → Versions
  come from one Gradle property shared by both modules.

## Migration Plan

Internal only; no user data or file format changes. Land on a branch in task order; each group leaves the build green.
Rollback is reverting the change's commits.
