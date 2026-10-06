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

- `net.nevinsky.abyssus.lib.core`: `Model` / `ModelData` / `ModelInstance`, nodes and animation, `ModelBatch` with keyed
  shaders (`ShaderProvider.DEFAULT_SHADER_KEY`), and the default and metallic-roughness (`PbrShader`) shaders. Their GLSL
  is under `resources/shader`.
- `net.nevinsky.abyssus.lib.core.model.PBR*Attribute`: the PBR material attributes. They use the same aliases as gdx-gltf,
  without depending on it.
- `net.nevinsky.abyssus.lib.core.shader.EnvironmentLightAttribute`: image based light for an `Environment` (an irradiance
  cube, a prefiltered specular cube and six axis colors), set in place of `ColorAttribute.AmbientLight`. `PbrShader`
  then compiles with `environmentLightFlag` and samples both cubes; `DefaultShader` uses the six colors as its ambient
  cubemap. An environment without it compiles and renders as before. The cubes are any `GLTexture`; building them
  (from an HDR image) is up to the caller.
- `net.nevinsky.abyssus.lib.core.shader.ShadowAtlasAttribute`: one packed-depth atlas plus light-identity keyed records.
  Each record owns copies of its projection matrices and atlas UV rectangles while retaining the light object identity
  used to match it to environment lights. `ModelDepthShaderProvider` supplies a reusable depth shader for this module's
  32-bit indexed meshes, posed bone weights and diffuse alpha-test cutouts. Alpha-blended materials do not cast in this
  first pass. Callers own atlas allocation, tile rendering, render-state restoration and attachment of the attribute to
  each environment; the legacy `Environment.shadowMap` path remains supported independently.
  The atlas depth format uses base-255 RGBA8 digits (least significant in R) to match channel quantization. Disable
  color dithering and sRGB output while writing it and use nearest texture filtering. The receivers interpolate PCF
  comparisons and correct sample depths using receiver-plane gradients; the atlas's UV rectangles may vary in size.
- `net.nevinsky.abyssus.lib.core.loader.AssimpModelLoader`:
  - `loadData` parses a file without a GL context.
  - `decodeTextures` decodes the textures off the GL thread.
  - `build` creates the GPU resources with the GL context current.
- `net.nevinsky.abyssus.lib.core.assimp`: the Assimp to `ModelData` pipeline.

Procedural mesh building is not included: use libGDX's `ModelBuilder` / `MeshBuilder`.

## Origin

See [origin and license](../docs/third-party/gdx-model-origin.md) for the inherited sources, original commit and changes.

## Tests

`./gradlew :gdx-model:test` runs the CPU tests. Add `-Dabyssus.glTests=true` to also run the GL tests, which open a
small window.
