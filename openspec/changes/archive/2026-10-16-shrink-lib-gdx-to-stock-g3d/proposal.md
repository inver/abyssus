# Proposal

## Why

`lib-gdx` is about 10k lines of Kotlin that copy libGDX's whole `g3d` model spine (`Model`, `ModelInstance`, `Node`,
`NodePart`, `Renderable`, `MeshPart`, `ModelBatch`, animation, `BaseShader`, `Shader`, providers). Only the `Mesh` end of
that chain needs to differ: libGDX meshes use 16-bit indices, so one mesh holds at most 65,535 vertices. The rest was
forked because it holds a reference to the forked `Mesh` type, not because it behaves differently.

The fork has real costs. Game code and third-party plugins (which share Abyssus's libGDX classloader) cannot hand a
stock `ModelInstance` or `ModelBatch` to Abyssus models. The fork's duplicated instancing API is a stale copy of what stock
libGDX 1.14.2 now ships (`InstanceBufferObject`, `enableInstancedRendering`). Every libGDX upgrade needs a manual diff.

Two throwaway spikes (kept in the session scratchpad, not in the repo) showed a minimal fork is enough:

- A stock `Mesh` subclass with an int index buffer, wrapped by a stock `Model`, drew 90,000 vertices through a **stock**
  `ModelInstance`, `ModelBatch` and `DefaultShader`. Forcing `GL_UNSIGNED_SHORT` as a negative control made exactly the rows
  above index 65,535 disappear.
- The fork's own GLSL (PBR, image-based light, instanced variant) compiled and drew on a **subclass of the stock
  `DefaultShader`**, for both plain and instanced meshes.

## What Changes

- Add `IntMesh` (a `com.badlogic.gdx.graphics.Mesh` subclass with 32-bit indices) and `IntIndexData`. It overrides
  `render`, `extendBoundingBox`, `calculateRadiusSquared` and `unbind`, and throws from the 16-bit views (`getIndicesBuffer`,
  `setIndices(short[])`, `copy`, ...).
- Add `IntModel`, a subclass of stock `Model` that loads the fork's `ModelData` (which keeps `IntArray` indices) and keeps
  today's PBR material conversion, shared textures, `maxBones` and `hasPbrMaterials`.
- Replace the forked `ModelInstance`, `Node`, `NodePart`, `Animation`, `NodeAnimation`, `AnimationController`,
  `BaseAnimationController`, `Renderable`, `RenderableProvider`, `RenderableSorter`, `MeshPart`, `ModelBatch`, `BaseShader`,
  `Shader` and the shader providers with the stock classes. The shader-key argument of `ModelBatch.render` is dropped:
  every caller passes the one key, `DEFAULT_SHADER_KEY`.
- Rebuild `DefaultShader`/`PbrShader` as subclasses of the stock `DefaultShader` that load the fork's GLSL: stock `createPrefix`
  plus extra defines, `register(Uniform, Setter)` for PBR and image-based-light uniforms, a `canRender` override for the
  instanced variant, and a `bindLights` override for the shadow atlas and directional visibility. `ModelDepthShader` stays
  its own shader but implements the stock `Shader` interface.
- **BREAKING (internal and for external users of `lib-gdx`):** instancing changes from four `vec4` attributes
  `a_instance0..3` to one `mat4 a_instance` attribute declared as four `VertexAttribute`s with units 0 to 3, because stock
  `InstanceBufferObject.bind` binds `location + attribute.unit`. This touches `default.vertex.glsl`, `ModelDepthShader`,
  `InstanceAttributes` and `FoliageDrawable`.
- Fix a core-profile `GL_INVALID_OPERATION` that stock `Mesh.unbind` raises on instanced meshes (it unbinds the VAO before the
  instance attributes) by unbinding the instances first.
- Switch every `net.nevinsky.abyssus.lib.gdx.*` model-runtime import in `lib-core`, `lib-core-editor`, `lib-physics`,
  `plugin-abyssus` and `app-game-control-line` to the stock `com.badlogic.gdx.graphics.g3d.*` types.
- Delete the dead fork classes. Update `lib-gdx`'s README, `docs/third-party/gdx-model-origin.md`,
  `docs/ai/architecture.md`, `docs/ai/conventions.md` and `docs/ai/testing.md`, and run `scripts/check-docs.sh`.

Out of scope:

- No behavior change for users: models, animation, PBR, shadows, image-based light, foliage and fog render as today.
- No change to the Assimp importer, `ModelData`/`ModelMesh`/`ModelMeshPart`, the binary glTF writer, the PBR attribute
  classes, `EnvironmentLightAttribute`, `ShadowAtlasAttribute` or `DirectionalVisibilityAttribute`.
- No new shader features, and no change to the ray tracing path (it reads `ModelData`, not `Mesh`).
- No native file fields are read or written, so there is no document validation or format-version change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a refactor that keeps observable behavior, so the change sets `skip_specs: true`. The existing capabilities
that this must keep working are `scene-model-rendering`, `scene-model-animation`, `scene-foliage-rendering`,
`scene-shadows`, `scene-hdr-skybox` and `model-import`.

## Impact

- **Code:** `projects/lib-gdx` (most classes deleted, a few added), and the imports in the other five modules that use it
  (about 90 files). `FoliageDrawable` (`lib-core`), `ModelImportTest` and `FlightGearImport` (`lib-core-editor`) and the
  plugin's `AssetLoading`, `SceneRenderer`, `ShadowResources`, `SceneShadows`, `TerrainShader` are the most affected.
- **Public API:** `lib-gdx` stops exporting its own `ModelInstance`, `ModelBatch` and friends. Anyone using them (game
  code, third-party plugins) moves to the stock types, which is the intended outcome.
- **Dependencies:** none added. libGDX stays at 1.14.2.
- **Tests:** existing `lib-gdx`, `lib-core`, `lib-core-editor` and plugin tests are the parity check for the Kotlin behavior
  that the fork carried; new GL tests port the two spikes. `TestGl` needs no `Gdx.files` backend because the shaders always
  receive their GLSL from `lib-gdx`'s own resources, never from stock's classpath defaults.
- **Open changes:** `add-realistic-water` task 3.2 adds model depth passes in `lib-gdx`; it needs to target the new
  `ModelDepthShader` and the mat4 instancing convention.
