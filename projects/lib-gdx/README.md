# lib-gdx

Stock libGDX 3D on top of **32-bit mesh indices**, with an **Assimp importer**, a **binary glTF writer** and a PBR,
image-based-light, shadow-atlas and instanced uber shader. It is a plain JVM library: it has no IntelliJ dependency, so
any libGDX project can use it.

## Why it exists

libGDX's own `g3d` meshes use 16-bit indices, so one mesh can address at most 65,535 vertices. This module's `IntMesh`
keeps `int` indices drawn with `GL_UNSIGNED_INT`; it is a subclass of the stock `Mesh`, so it flows through the stock
`MeshPart`, `NodePart`, `ModelInstance`, `ModelBatch` and `BaseShader` unchanged. Everything else in the pipeline is stock
libGDX 1.14.2: game code and other plugins (which share Abyssus's libGDX classloader) can hand a stock `ModelInstance`
or `ModelBatch` to these models. `IntMeshGlTest`, `LargeMeshGlTest` and `AssimpLoadingTest.keepsMoreVerticesThanA16BitIndexCanAddress`
pin this down with a 90,000-vertex mesh.

## Contents

- `net.nevinsky.abyssus.lib.gdx.g3d`: what stock libGDX cannot do. Their GLSL is under `resources/shader`.
  - `IntIndexData` and `IntMesh`: a stock `IndexData` and `Mesh` with 32-bit indices (`GL_UNSIGNED_INT`), on a vertex array
    object. The views that take or return `short` indices throw `UnsupportedOperationException` instead of returning a
    truncated view; use the `IntArray` / `IntBuffer` members. `IntMesh.unbind` unbinds the instance attributes before the
    vertex array, because stock `Mesh.unbind` raises `GL_INVALID_OPERATION` on an instanced mesh in a core profile.
  - `IntModel`: a stock `Model` built from this module's `ModelData` (which keeps `IntArray` indices). It shares one
    texture per file, converts PBR materials, and reports `maxBones` and `hasPbrMaterials()`.
    `AssimpModelLoader.buildIntModel` returns one.
  - `ModelShader` and `PbrModelShader`: this module's GLSL on the stock `DefaultShader` (shadow atlas, directional
    visibility, world clip plane, image-based light, instancing), picked per renderable by `ModelShaderProvider`, a stock
    `BaseShaderProvider`. `ModelShaderProvider.fitBones` raises the bone count in steps of 8 and keeps all 8 weight slots.
    The shader config is the stock `DefaultShader.Config`.
  - `ModelDepthShader` and `ModelDepthShaderProvider`: the depth shader for shadow and water passes on stock
    `Renderable`s, driven by `ShadowDepthPass`.
  - `FilteredRenderables`: a `RenderableProvider` that hands on only the renderables a pass accepts, so a pass can leave
    some of a stock `ModelBatch`'s input out.
  - `InstanceAttributes`: instancing uses **one `mat4 a_instance`** attribute, declared to the mesh as four `vec4`
    `VertexAttribute`s with the same alias and the units 0 to 3, because stock `InstanceBufferObject` binds each attribute
    at `location + unit`. Pass `InstanceAttributes.of()` to `Mesh.enableInstancedRendering`; `InstanceAttributes.write`
    packs a `Matrix4`.
- `net.nevinsky.abyssus.lib.gdx.model.PBR*Attribute`: the PBR material attributes. They use the same aliases as gdx-gltf,
  without depending on it.
- `net.nevinsky.abyssus.lib.gdx.shader.EnvironmentLightAttribute`: image based light for an `Environment` (an irradiance
  cube, a prefiltered specular cube and six axis colors), set in place of `ColorAttribute.AmbientLight`. `PbrModelShader`
  then compiles with `environmentLightFlag` and samples both cubes; `ModelShader` uses the six colors as its ambient
  cubemap. An environment without it compiles and renders as before. The cubes are any `GLTexture`; building them
  (from an HDR image) is up to the caller.
- `net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasAttribute`: one packed-depth atlas plus light-identity keyed records.
  Each record owns copies of its projection matrices and atlas UV rectangles while retaining the light object identity
  used to match it to environment lights. `ModelDepthShaderProvider` supplies a reusable depth shader for this module's
  32-bit indexed meshes, posed bone weights and diffuse alpha-test cutouts. Alpha-blended materials do not cast in this
  first pass. Callers own atlas allocation, tile rendering, render-state restoration and attachment of the attribute to
  each environment; the legacy `Environment.shadowMap` path remains supported independently.
  The atlas depth format uses base-255 RGBA8 digits (least significant in R) to match channel quantization. Disable
  color dithering and sRGB output while writing it and use nearest texture filtering. The receivers interpolate PCF
  comparisons and correct sample depths using receiver-plane gradients; the atlas's UV rectangles may vary in size.
- Optional pass support for planar reflections and water depth (all of it off by default, so a scene that sets nothing
  renders and compiles exactly as before):
  - `ClipPlaneAttribute` is a world-space clip plane `a x + b y + c z + d >= 0` set on an `Environment` (or a material)
    while a pass draws. `ModelShader` and `PbrModelShader` then compile a `clipPlaneFlag` variant that discards the fragments
    below it from the world position (the vertex shader supplies it even without lighting or normals); the plane is
    bound per renderable. Skinned and instanced meshes are clipped after posing, and alpha-tested cutouts keep their holes.
  - `ShadowDepthPass.clipPlane` clips the depth pass the same way, and `ShadowDepthPass.linearFar` (above 0) makes it
    write the linear view depth divided by that distance, packed like the shadow depths, instead of the window depth.
    `ModelDepthShader` still skips alpha-blended materials.
  - `FilteredRenderables` drops the renderables of a provider that a pass should not draw (the opaque and the blended
    parts of one model can be drawn in separate passes).
- `net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader`:
  - `loadData` parses a file without a GL context.
  - `decodeTextures` decodes the textures off the GL thread.
  - `buildIntModel` creates the GPU resources, as an `IntModel`, with the GL context current.
- `net.nevinsky.abyssus.lib.gdx.assimp`: the Assimp to `ModelData` pipeline.
  - `AssimpModelDataLoader(normalize = true)` converts the up axis a file states to Y: FBX metadata, and a DAE file's
    `<asset>` unit and up axis, which Assimp's Collada importer applies itself. With `normalize = false` the root keeps
    the file's own transform and the Collada importer is told to ignore `<asset>`: an import that lets the user choose
    the unit and up axis applies them itself.
  - `loadScene` also returns the frame the file states (`StatedFrame`: metres per unit and up axis, `null` where the
    FBX metadata says nothing) and what the `ModelData` leaves out (`LeftOut`: cameras, lights, point and line meshes,
    morph targets). `ColladaAsset.read` reads a DAE file's stated unit and up axis; `X_UP` is reported as stated.
- The glTF writer (package `gltf`, beside `assimp`):
  - `GltfWriter.write(data, images, generator)` writes a `ModelData` as one binary glTF 2.0 (GLB) with libGDX's
    `JsonWriter`: the node hierarchy with translation, rotation and scale, mesh attributes as accessors, 16- or 32-bit
    indices, skins with inverse bind matrices, LINEAR animations and metallic-roughness materials. Images are external
    URIs from `images` (texture file name to URI). The bytes depend only on the input. Invalid data (indices or joints
    out of range, weights not summing to 1) throws `GltfWriteException`. Morph targets, cameras, lights and extensions
    are never written.
  - `PhongToPbr` maps a Phong-style material to metallic-roughness (diffuse to base colour, metallic 0, shininess to
    roughness, opacity below 1 to `BLEND`) and names what it drops, such as the specular colour.

Procedural mesh building is not included: use libGDX's `ModelBuilder` / `MeshBuilder`.

## Moving from the earlier forked classes

Earlier versions of this module carried their own copies of the whole `g3d` spine. They are gone; use the stock libGDX
class, or the replacement here.

| Was (`net.nevinsky.abyssus.lib.gdx`) | Now |
|---|---|
| `mesh.Mesh`, `IndexData`, `IndexBufferObject` | `g3d.IntMesh`, `g3d.IntIndexData` (a stock `Mesh` and `IndexData`) |
| `model.Model`, `AssimpModelLoader.build` | `g3d.IntModel`, `AssimpModelLoader.buildIntModel` (a stock `Model`) |
| `ModelInstance`, `node.Node`, `node.NodePart`, `node.Animation`, `node.NodeAnimation`, `mesh.MeshPart`, `AnimationController`, `Renderable`, `RenderableProvider`, `ModelBatch` | the classes of the same name in `com.badlogic.gdx.graphics.g3d` |
| `ModelBatch.render(provider, environment, shaderKey)` and `ShaderProvider.DEFAULT_SHADER_KEY` | `ModelBatch.render(provider, environment)`: which shader a renderable gets follows from its material and mesh |
| `ModelBatch.renderableFilter` | wrap the provider in `g3d.FilteredRenderables` |
| `shader.DefaultShader`, `shader.PbrShader`, `shader.DefaultShaderProvider` | `g3d.ModelShader`, `g3d.PbrModelShader`, `g3d.ModelShaderProvider` |
| `shader.ShaderConfig` | `DefaultShader.Config` |
| `shader.ModelDepthShader`, `shader.ModelDepthShaderProvider` | `g3d.ModelDepthShader`, `g3d.ModelDepthShaderProvider` |
| `mesh.InstanceAttributes` (four `vec4` attributes `a_instance0` to `a_instance3`) | `g3d.InstanceAttributes` (one `mat4 a_instance`); a custom vertex shader declares `attribute mat4 a_instance;` |
| `Model.materials` as a map, `Node.parts` as a list | the stock `Array`s: `model.getMaterial(id)`, `node.parts` |

## Origin

See [origin and license](../../docs/third-party/gdx-model-origin.md) for the inherited sources, original commit and changes.

## Tests

`./gradlew :lib-gdx:test` runs the CPU tests. Add `-Dabyssus.glTests=true` to also run the GL tests, which open a
small window.
