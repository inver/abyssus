# core

Asset reading and loading for Mundus projects, as a plain JVM library: the Abyssus plugin uses it, and so can any
libGDX tool or test that has a project folder and a GL context. Root package `net.nevinsky.abyssus.assets`.

## Rules

- **No IntelliJ or plugin imports.** Depends on `:gdx-model`, libGDX and Jackson only.
- **Wired by constructors.** No `object` or `companion object` in `src/main` (a `data object` case of a sealed type is a
  value and is fine); pure constants are top-level `const val`. `./gradlew :core:checkNoSingletons`, part of `check`,
  enforces it. Collaborators are passed in; nothing is looked up globally.
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
