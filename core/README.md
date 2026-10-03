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
| `assets` | `AssetLoading` (the composition root), `AssetLog` (where problems go), `ShaderSource` (GLSL from a resource folder), `AssetLayout` constants, `runCatchingKeepingCancellation` |
| `assets.files` | `AssetFiles` (an asset folder's files and `meta.json`), `MetaBase`, `MetaType`, `Asset`, `TerrainFiles` |
| `assets.json` | `JsonProcessor` (binds Mundus JSON) and `JsonNode` helpers |
| `assets.loading` | `AssetLoader` (prepare / upload / build / discard), `AssetCache` (load once, fail once, slice GPU work per frame), `SceneAssets` (a cache per project folder) |
| `assets.model` | `ModelLoader` (glTF and other formats through `gdx-model`'s Assimp loader) |
| `assets.terrain` | `TerrainLoader`, `TerrainDataReader`, `TerrainData`, `TerrainMesh` |
| `assets.terrain.generation` | `TerrainGenerator` and `TerrainGenerationSettings` (seeded heights; defaults: seed 12345, feature size 200, heights 0..120, 5 octaves, persistence 0.5, lacunarity 2), `TerrainHeightEncoder`, `TerrainAssetWriter` (a new terrain's files), `TerrainRecipeCodec` and `TerrainRecipe` (the separate `abyssus-terrain.recipe.json`), `TerrainGenerationDraft` (preview state, no threads) |
| `assets.terrain.noise` | `NoiseSampler` and its factory (injected), `FastNoiseSampler` over the vendored FastNoiseLite |
| `assets.edit` | `AssetFieldDescriptions` and `AssetMetaEditor`: the editable `meta.json` fields, their validation and one-key edits on a JSON tree |
| `assets.sky` | `Sky` (a drawable background), `SkyLoader` (picks a loader by `meta.json` type); `cube/` six-face skyboxes, `procedural/` skies drawn by the asset's own GLSL, `hdr/` Radiance `.hdr` skies and their lighting environment |

Sky shaders are in `src/main/resources/shader/sky/`.

## Use

```kotlin
val loading = AssetLoading(JsonProcessor(), AssetLog { message, error -> println(message) }, executor,
    ShaderSource("/shader/sky", AssetLoading::class.java))
val models = loading.assets(loading.models)    // one per scene view: owns its caches
models.update(projectDir, setOf("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"))  // once per frame, GL current
val model = models.get("model_29e9be61-6594-4f82-a6cf-44ccf09f71fb")           // null until built
```

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
