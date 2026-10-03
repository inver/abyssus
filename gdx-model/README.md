# gdx-model

A libGDX 3D model runtime with **32-bit mesh indices** and an **Assimp importer**. It is a plain JVM library: it has no
IntelliJ dependency, so any libGDX project can use it.

## Why it exists

libGDX's own `g3d` meshes use 16-bit indices, so one mesh can address at most 65,535 vertices. This module's `Mesh`,
`IndexData` and `Model` keep `int` indices drawn with `GL_UNSIGNED_INT`. The rest of the classes (`ModelInstance`,
`Node`, `Renderable`, `ModelBatch`, `AnimationController`, the shaders) follow from that type change.
`LargeMeshGlTest` and `AssimpLoadingTest.keepsMoreVerticesThanA16BitIndexCanAddress` pin this down with a
90,000-vertex mesh.

## Contents

- `net.nevinsky.abyssus.core`: `Model` / `ModelData` / `ModelInstance`, nodes and animation, `ModelBatch` with keyed
  shaders (`ShaderProvider.DEFAULT_SHADER_KEY`), and the default and metallic-roughness (`PbrShader`) shaders. Their GLSL
  is under `resources/shader`.
- `net.nevinsky.abyssus.core.model.PBR*Attribute`: the PBR material attributes. They use the same aliases as gdx-gltf,
  without depending on it.
- `net.nevinsky.abyssus.core.shader.EnvironmentLightAttribute`: image based light for an `Environment` (an irradiance
  cube, a prefiltered specular cube and six axis colors), set in place of `ColorAttribute.AmbientLight`. `PbrShader`
  then compiles with `environmentLightFlag` and samples both cubes; `DefaultShader` uses the six colors as its ambient
  cubemap. An environment without it compiles and renders as before. The cubes are any `GLTexture`; building them
  (from an HDR image) is up to the caller.
- `net.nevinsky.abyssus.core.loader.AssimpModelLoader`:
  - `loadData` parses a file without a GL context.
  - `decodeTextures` decodes the textures off the GL thread.
  - `build` creates the GPU resources with the GL context current.
- `net.nevinsky.abyssus.lib.assets.assimp`: the Assimp to `ModelData` pipeline.

Procedural mesh building is not included: use libGDX's `ModelBuilder` / `MeshBuilder`.

## Origin

This is a trimmed fork of the `lib-core` and `lib-assets` modules of Mundus (commit
`128175e064a915e043f024a565f935d4c6883292`). `core/loader` is a trimmed copy of `lib-commons`' `AssimpModelLoader` /
`ParentBasedTextureProvider`, plus `Pixmaps` and `PreloadedTextureProvider`, which are new. The Java was converted to
Kotlin and no longer diffs against upstream.

Compared with upstream:
- the glTF exporter, Lombok, the shape and mesh builders, the asset providers, the vertex-array and sub-data buffer
  variants and the managed-mesh registry were removed
- gdx-gltf was replaced by the module's own attributes

Mundus and libGDX are Apache-2.0 licensed, and libGDX-derived files keep their original headers.

## Tests

`./gradlew :gdx-model:test` runs the CPU tests. Add `-Dabyssus.glTests=true` to also run the GL tests, which open a
small window.
