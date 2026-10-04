# gdx-model origin and license

This is a trimmed fork of the `lib-core` and `lib-assets` modules of Mundus (commit
`128175e064a915e043f024a565f935d4c6883292`). `core/loader` is a trimmed copy of `lib-commons`' `AssimpModelLoader` /
`ParentBasedTextureProvider`, plus `Pixmaps` and `PreloadedTextureProvider`, which are new. The Java was converted to
Kotlin and no longer diffs against upstream.

Compared with upstream:
- the glTF exporter, Lombok, the shape and mesh builders, the asset providers, the vertex-array and sub-data buffer
  variants and the managed-mesh registry were removed
- gdx-gltf was replaced by the module's own attributes

Mundus and libGDX are Apache-2.0 licensed (SPDX-License-Identifier: Apache-2.0), and libGDX-derived files keep their original headers.

