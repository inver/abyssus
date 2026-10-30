# lib-gdx origin and license

The reusable model runtime lives in `projects/lib-gdx/`, under the package `net.nevinsky.abyssus.lib.gdx`.
Its Gradle project is `:lib-gdx`; earlier project notes called it `gdx-model`.

The module began as a trimmed fork of the `lib-core` and `lib-assets` modules of Mundus (commit
`128175e064a915e043f024a565f935d4c6883292`), converted from Java to Kotlin, which copied libGDX's whole `g3d` model
spine (`Model`, `ModelInstance`, `Node`, `NodePart`, `Renderable`, `MeshPart`, `ModelBatch`, the animation classes,
`BaseShader`, the shader providers) because libGDX meshes use 16-bit indices.

It has since been reduced to what stock libGDX 1.14.2 cannot do, and the copied spine is deleted. What remains of the
fork:
- the `loader` package: a trimmed copy of `lib-commons`' `AssimpModelLoader` / `ParentBasedTextureProvider`, plus `Pixmaps`
  and `PreloadedTextureProvider`, which are new, and the `assimp` pipeline that turns a file into `ModelData`;
- `ModelData`, `ModelMesh` and `ModelMeshPart`, the libGDX data classes with `IntArray` indices (`ModelNode`,
  `ModelAnimation` and `ModelMaterial` are libGDX's own);
- the GLSL under `resources/shader` (the default, PBR and instanced uber shader, derived from Mundus' shader, which is
  derived from libGDX's `default.vertex.glsl` / `default.fragment.glsl`);
- `g3d.IntMesh` and `g3d.IntIndexData`, which are libGDX's `Mesh` and `IndexBufferObject` with `int` indices, and the
  shader classes of the `g3d` package, which subclass libGDX's `DefaultShader`. `IntModel` is libGDX's `Model` loading
  `ModelData`.

Compared with upstream:
- the glTF exporter, Lombok, the shape and mesh builders, the asset providers, the vertex-array and sub-data buffer
  variants and the managed-mesh registry were removed
- gdx-gltf was replaced by the module's own attributes
- instancing is one `mat4` attribute on libGDX's own instance buffer instead of a copy of it

Abyssus added its own binary glTF writer, the image-based light, shadow atlas, directional visibility, clip plane and
depth passes; see `projects/lib-gdx/README.md` for the current API and a list for moving from the deleted classes.

Mundus and libGDX are Apache-2.0 licensed (SPDX-License-Identifier: Apache-2.0), and libGDX-derived files keep their original headers.

