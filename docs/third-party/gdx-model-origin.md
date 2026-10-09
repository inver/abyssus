# lib-gdx origin and license

The reusable model runtime lives in `projects/lib-gdx/`, under the package `net.nevinsky.abyssus.lib.gdx`.
Its Gradle project is `:lib-gdx`; earlier project notes called it `gdx-model`.

This is a trimmed fork of the `lib-core` and `lib-assets` modules of Mundus (commit
`128175e064a915e043f024a565f935d4c6883292`). The `loader` package is a trimmed copy of `lib-commons`' `AssimpModelLoader` /
`ParentBasedTextureProvider`, plus `Pixmaps` and `PreloadedTextureProvider`, which are new. The Java was converted to
Kotlin and no longer diffs against upstream.

Compared with upstream:
- the glTF exporter, Lombok, the shape and mesh builders, the asset providers, the vertex-array and sub-data buffer
  variants and the managed-mesh registry were removed
- gdx-gltf was replaced by the module's own attributes

Abyssus subsequently added its own binary glTF writer; see `projects/lib-gdx/README.md` for the current API.

Mundus and libGDX are Apache-2.0 licensed (SPDX-License-Identifier: Apache-2.0), and libGDX-derived files keep their original headers.

