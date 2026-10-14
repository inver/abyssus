# Tasks

GL tests need `-Dabyssus.glTests=true` and a display. Run single classes with the module-qualified task, for example
`./gradlew :lib-gdx:test --tests 'net.nevinsky.abyssus.lib.gdx.g3d.IntMeshGlTest' -Dabyssus.glTests=true`.
New code goes in `projects/lib-gdx/src/main/kotlin/net/nevinsky/abyssus/lib/gdx/g3d/`. Groups 1 to 4 are additive: no caller
changes and the old pipeline keeps compiling, so every task leaves the build green. Group 5 is the one atomic flip.

## 1. 32-bit mesh and model on stock types

- [ ] 1.1 Add `IntIndexData` (stock `IndexData` over an int element buffer; the short-typed members throw) and verify
  `IntIndexDataGlTest` shows set, update and read-back of indices above 65,535 and `GL_ELEMENT_ARRAY_BUFFER` upload.
- [ ] 1.2 Add `IntMesh : Mesh` on `VertexBufferObjectWithVAO` with `render` (`GL_UNSIGNED_INT`, `offset * 4`, instanced branch kept),
  `extendBoundingBox`, `calculateRadiusSquared` and the reordered `unbind` (instances, vertices, indices); the short views and
  `copy` throw. Verify `IntMeshGlTest` covers a 90,000-vertex mesh drawn through a stock `ModelInstance` and `ModelBatch`
  (rows above index 65,535 are non-black, `glGetError` is 0), bounds of 299 through the stock `MeshPart.update`, and each
  blocked method throwing `UnsupportedOperationException`.
- [ ] 1.3 Add a negative-control case to `IntMeshGlTest` that draws the same mesh with `GL_UNSIGNED_SHORT` through a test-only
  subclass and verify the rows above index 65,535 are black, so the test can fail for the right reason.
- [ ] 1.4 Add `IntModel : Model` with `load(forkData, textureProvider)`, shared-texture `loadMaterials`, PBR `convertMaterial`,
  `maxBones` and `hasPbrMaterials`, and make `AssimpModelLoader` offer a build that returns it (the old build stays).
  Verify `IntModelGlTest` loads the existing `AssimpLoadingTest` fixtures and the 90,000-vertex `GridModel`, with the same
  materials, node tree, animations and bone counts as the old `Model`.
- [ ] 1.5 Verify with `./gradlew :lib-gdx:test` and, with `-Dabyssus.glTests=true`, `:lib-gdx:test` that all existing tests
  still pass unchanged.

## 2. Shaders and instancing on stock types

- [ ] 2.1 Add `InstanceAttributes` for the mat4 convention (one alias, four `VertexAttribute`s with units 0 to 3, `FLOATS`
  and `write` as today). Verify `InstanceAttributesTest` for the attribute keys, units, offsets and `write` bounds check.
- [ ] 2.2 Add the new GLSL under `shader/g3d/` (copies of `default.vertex.glsl`, `pbr.fragment.glsl` and `default.fragment.glsl`
  with `attribute mat4 a_instance;` and `#define u_worldTrans a_instance`); the old files stay. Verify `ShaderSourcesTest`
  reads each resource and that the only difference from the old vertex shader is the instancing declaration.
- [ ] 2.3 Add `ModelShader : DefaultShader` (prefix from `createPrefix` plus `instancedFlag` and the PBR and environment
  defines, `canRender` for the instanced variant, `bindLights` calling `ShadowAtlasBindings` and the directional-visibility
  binding) and `PbrModelShader` (metallic, roughness, occlusion, metallic-roughness texture, `u_env*` uniforms, the fade
  variant), re-registering `u_ambientCubemap` with the fork's sky-seeded setter. Verify `ShaderDefinesTest` (no GL) for the
  define builders and `ModelShaderGlTest` for a lit PBR mesh with an `EnvironmentLightAttribute`, with and without
  `environmentFadeFlag`.
- [ ] 2.4 Add `ModelShaderProvider : BaseShaderProvider` (PBR vs default choice, instanced flag from the mesh, bone count
  stepped by 8 into the stock `Config`) and the stock-typed `ModelDepthShader` (mat4 instancing in its generated source,
  stock `Shader` interface) and `ModelDepthShaderProvider`. Verify `InstancedModelShaderGlTest` draws a 2-instance, 90,000-vertex
  mesh with exactly two shader variants created, and `ModelDepthGlTest` writes depth for plain, instanced, skinned and
  alpha-cutout meshes.
- [ ] 2.5 Verify the shadow-atlas and directional-visibility paths on the new shader by porting `ModelShadowGlTest` and
  `DirectionalVisibilityGlTest` to the stock pipeline.
- [ ] 2.6 Document the new classes and the mat4 convention in `projects/lib-gdx/README.md` and verify `scripts/check-docs.sh`
  passes.

## 3. Parity and audit gates

- [ ] 3.1 Add `StockPipelineParityGlTest`: render one scene (a static PBR model, a skinned model, a shadowed directional light,
  an environment light, fog and a 2-instance foliage mesh) through the old pipeline and the new one and compare the pixels
  with a stated tolerance; verify it passes and record the tolerance and any accepted differences in `design.md`.
- [ ] 3.2 Audit every call of the 16-bit `Mesh` API on model meshes: run
  `grep -rn "getIndicesBuffer\|\.getIndices(\|\.setIndices(\|\.copy(" projects --include='*.kt'` and list the hits that reach an
  `IntMesh`. Verify the list is empty or each hit is fixed in this group, with the result recorded in `design.md`.
- [ ] 3.3 Run the `lib-gdx` and `lib-core` GL suites with `-Dabyssus.glTests=true` on a Windows machine and a Linux machine
  with a display (CI does not run GL tests), and record the result. Verify the instanced mat4 draw and group 2 tests pass on
  both; if the mat4 attribute fails on a driver, record it and stop before group 5.

## 4. Open-change coordination

- [ ] 4.1 Amend `add-realistic-water` task 3.2 (and its design if it names the depth shader) so model depth and clip passes build
  on the new `ModelDepthShader` and the mat4 convention. Verify with `openspec validate add-realistic-water`.

## 5. Flip the callers (one atomic commit)

- [ ] 5.1 Switch `lib-core` to the stock g3d types and the new classes: loaders, `FoliageDrawable` (mat4 `InstanceAttributes`),
  `ModelRaySnapshotLoader` and tests. Verify `./gradlew :lib-core:test -Dabyssus.glTests=true`, including
  `FoliageDrawableGlTest`, passes, and `:lib-core:checkNoSingletons` stays green.
- [ ] 5.2 Switch `lib-core-editor` (`ModelImportTest`, `FlightGearImport`, `RayTestModels`, model import code) and `lib-physics`.
  Verify `./gradlew :lib-core-editor:test :lib-physics:test` passes and `NoPlatformClasspathTest` still passes.
- [ ] 5.3 Switch `plugin-abyssus` (`AssetLoading`, `SceneRenderer`, `SceneShadows`, `ShadowResources`, `TerrainShader`,
  `ModelPreviewCanvas`, `SceneFoliage`): drop the shader-key argument, replace `ShaderConfig` with stock `DefaultShader.Config`
  and the `ContentBatch` alias with stock `ModelBatch`. Verify `./gradlew :plugin-abyssus:test -Dabyssus.glTests=true`,
  `:plugin-abyssus:checkNoJolt` and `checkNoRunCatching` pass.
- [ ] 5.4 Switch `app-game-control-line` (`FieldRenderer`, `FieldShadows`, `tools/FieldFoliage`). Verify
  `./gradlew :app-game-control-line:test` passes.
- [ ] 5.5 Verify the whole flip with `./gradlew check` and `./gradlew checkPackageCycles` (the allowlist stays empty).

## 6. Delete the dead fork and move into place

- [ ] 6.1 Delete the unused fork classes (`ModelInstance`, `Node`, `NodePart`, `Animation`, `NodeAnimation`, `AnimationController`,
  `BaseAnimationController`, `Renderable`, `RenderableProvider`, `RenderableSorter`, `DefaultRenderableSorter`, `MeshPart`,
  `Mesh`, `IndexData`, `IndexBufferObject`, `ModelBatch`, `BaseShader`, `Shader`, `ShaderProvider`, the old shader classes,
  `ShaderHolder`, `ShaderConfig`) and the old GLSL; move the `g3d` GLSL into `shader/`. Verify `./gradlew :lib-gdx:test` and
  that `grep -rn "lib.gdx.ModelInstance\|lib.gdx.ModelBatch" projects` finds nothing.
- [ ] 6.2 Update `projects/lib-gdx/README.md` (contents, why it exists, a migration list for external users),
  `docs/third-party/gdx-model-origin.md` (what remains of the fork and its Mundus origin), `docs/ai/architecture.md` (the
  module table and the shader notes that name fork classes), `docs/ai/conventions.md` and `docs/ai/testing.md`. Verify
  `scripts/check-docs.sh` passes and no doc names a deleted class.

## 7. Integration checks

- [ ] 7.1 Run `./gradlew check` (and `./gradlew :lib-gdx:test :lib-core:test :plugin-abyssus:test -Dabyssus.glTests=true` on a
  machine with a display) and `scripts/check-docs.sh`; record the results.
- [ ] 7.2 Check by hand in `./gradlew :plugin-abyssus:runIde` on a copy of the `Untitled` project (never the committed fixture):
  (1) open `Main Scene`: models, textures and animation look as before; (2) toggle shadows and move the sun: sun and point
  shadows match; (3) foliage draws, including after a regeneration; (4) a skinned model animates in the scene view and in the
  project-view preview; (5) an HDR sky lights PBR and non-PBR models and cross-fades; (6) fog and the loading overlay still
  draw; (7) a model with more than 65,535 vertices imports, previews and renders. Then run `./gradlew :app-game-control-line:run`
  and check the plane, field foliage and shadows. Keep any unperformed check open.
