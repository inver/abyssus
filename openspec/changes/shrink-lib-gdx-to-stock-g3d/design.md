# Design

## Context

See proposal.md for the motivation. The facts that shape the approach (all checked against libGDX 1.14.2 sources and two
spikes):

- Only `Mesh` and `IndexData` carry the 16-bit limit. Stock `Mesh` is not `final`, has a `protected Mesh(VertexData,
  IndexData, boolean)` constructor and a public, overridable `render(shader, type, offset, count, autoBind)`. `MeshPart.mesh`
  and `Renderable.meshPart` are typed `Mesh`, so a subclass flows through stock `NodePart`, `Node`, `ModelInstance`,
  `ModelBatch` and `BaseShader` unchanged.
- The forked `ModelInstance`, `Node`, `NodePart`, `Animation*`, controllers, `Renderable` and `Shader` define no members that
  stock lacks (apart from `Renderable.cleanup`). They exist only because they name the forked `MeshPart`.
- Stock `Model.load` is five steps, and `convertMesh`, `loadMaterials` and `convertMaterial` are `protected`. The fork's
  `ModelData` / `ModelMesh` / `ModelMeshPart` hold `IntArray` indices; `ModelNode`, `ModelAnimation` and `ModelMaterial` are
  already the stock types.
- Stock `DefaultShader` accepts a prefix and both GLSL sources in its constructor, exposes `register(Uniform, Setter)` and
  `canRender`/`bindLights` as overridable, and `BaseShader.register` **replaces** a registration of the same alias.
- Stock `Mesh` already has instancing. Its `InstanceBufferObject.bind` binds each attribute at `location + attribute.unit`,
  so one instanced `mat4` is four `VertexAttribute`s with the same alias and units 0 to 3.
- The plugin already draws its grid and overlay with a stock `ModelBatch` and a `DefaultShader` subclass (`FogShader`).
  Content is drawn with a second, forked batch (`ContentBatch`). Every content call passes `DEFAULT_SHADER_KEY`, the only key.
- `lib-core`'s ray snapshots read `ModelData`, not `Mesh`, so they are unaffected.

## Goals / Non-Goals

**Goals:**
- `lib-gdx` keeps only what stock g3d cannot do: 32-bit meshes, the Assimp import, the glTF writer, PBR, image-based light,
  shadow-atlas and instanced shading.
- Every other class the callers touch is the stock libGDX class, so game code can pass stock objects to Abyssus models.
- Observable rendering is unchanged, checked pixel-for-pixel against the old pipeline while both exist.

**Non-Goals:**
- Merging the plugin's two batches into one. It becomes possible, but is a separate cleanup.
- Changing shader features or GLSL beyond the one instancing declaration.
- Replacing `ModelData` or the Assimp pipeline with stock loaders.

## Decisions

### 1. The fork boundary is the index type, plus the shaders

New code lives in `net.nevinsky.abyssus.lib.gdx.g3d`. It holds:

```
 IntIndexData            stock IndexData over an int element buffer (short members throw)
 IntMesh : Mesh          overrides render, extendBoundingBox, calculateRadiusSquared, unbind;
                         throws from getIndicesBuffer / setIndices(short[]) / getIndices(short[]) / copy
 IntModel : Model        loads fork ModelData; PBR materials, shared textures, maxBones, hasPbrMaterials
 ModelShader : DefaultShader        fork GLSL on the stock shader
 PbrModelShader : ModelShader       metallic/roughness/occlusion + image-based light uniforms
 ModelShaderProvider : BaseShaderProvider
 ModelDepthShader : Shader          own program, stock Shader interface
 InstanceAttributes                 mat4 a_instance as four VertexAttributes (units 0..3)
```

`IntMesh` uses `VertexBufferObjectWithVAO`: the spike's plain `VertexBufferObject` raised `GL_INVALID_OPERATION` in a GL 3.2
core context, which is what macOS gives. Short-typed Mesh methods **throw** rather than return a truncated view, so a stock
utility that reads indices fails loudly instead of corrupting.

*Alternative:* keep the whole fork and only trim. Rejected: it keeps the interop and upgrade cost, which is the point.
*Alternative:* split large imports into several 16-bit meshes. Rejected: it changes the node/mesh structure users see,
breaks the glTF round trip, and costs draw calls; the spike shows a subclass is about 210 lines.

### 2. `IntModel` is a stock `Model` subclass with its own `load`

Stock `load` takes the stock `ModelData`, so `IntModel.load(forkData, textureProvider)` repeats the five steps. It overrides
`loadMaterials` (shared textures per file) and `convertMaterial` (PBR attributes), and fills the `meshes` / `meshParts`
fields itself so that stock `loadNodes` finds each part by id. `AssimpModelLoader.build` returns an `IntModel`.
`hasPbrMaterials()` and `maxBones` become members of `IntModel`; `ModelData` is untouched.

### 3. The shader is a subclass of stock `DefaultShader`

| Fork feature | Stock hook |
|---|---|
| `instancedFlag`, PBR and environment defines | prefix = `createPrefix(renderable, config)` + extra defines |
| PBR and environment-light uniforms | `register(Uniform, LocalSetter)` after `super()`, before `init()` |
| instanced vs plain variant | `canRender` override: `instanced == mesh.isInstanced` and `super.canRender` |
| shadow atlas, directional visibility | `bindLights` override after `super.bindLights`; existing `ShadowAtlasBindings` code unchanged |
| ambient cube seeded from the sky's six colors | re-register `u_ambientCubemap` with the fork's setter (`BaseShader.register` replaces the stock one) |
| bone count in steps of 8 | the provider adjusts `Config.numBones` before construction (stock `canRender` already rejects too many bones) |

`ShaderConfig` is replaced by stock `DefaultShader.Config`; it has the same fields. Callers that set `numSpotLights` and
similar keep working with a different type.

*Alternative:* extend stock `BaseShader` directly. Rejected: it would duplicate the 900 lines of material and light binding
that stock `DefaultShader` already gives us.

### 4. The shader key is dropped

Every caller passes `ShaderProvider.DEFAULT_SHADER_KEY`. Stock `ModelBatch.render(provider, environment)` is used, and
`ModelShaderProvider` extends stock `BaseShaderProvider`. Which shader a renderable gets (plain, PBR, instanced) is decided
by the material and the mesh in `createShader`, as `DefaultShaderProvider.createShader` does today.
`ModelDepthShaderProvider` is not a `ShaderProvider` today and stays a small standalone class.

### 5. Instancing becomes one `mat4 a_instance`

`default.vertex.glsl` declares `attribute mat4 a_instance;` with `#define u_worldTrans a_instance` in place of four `vec4`s;
the generated source in `ModelDepthShader` changes the same way. `InstanceAttributes.of()` returns four
`VertexAttribute(Generic, 4, "a_instance", unit)`. The float layout and `write` are unchanged, so `FoliageDrawable`'s buffer
code stays; only the `enableInstancedRendering` call site follows `InstanceAttributes.of()`.

The spike measured the alternatives: the fork's four same-unit attributes collide in stock `BaseShader`'s key map, and
four distinct units are shifted to the wrong locations by `location + unit`. Overriding `InstanceBufferObject` instead
would reintroduce a fork of exactly the part stock now provides.

### 6. Unbind instances before the VAO

Stock `Mesh.unbind` unbinds the VAO first and then disables the instance attributes with VAO 0 bound, which is
`GL_INVALID_OPERATION` in a core profile. `IntMesh.unbind` unbinds instances, then vertices, then indices. The error was
reproduced with probes and cleared by the reorder.

### 7. Staging: additive, then an atomic flip, then deletion

A caller cannot be half-switched: stock `ModelBatch` cannot draw with a fork shader or `Renderable`, and `lib-core`
re-exports `lib-gdx`'s types to every module above it. So the stages are ordered by that constraint rather than by module:

1. **Additive.** Add the `g3d` package, its GLSL variants (a `shader/g3d/` copy so the old shaders keep working), and tests.
   Callers are untouched, so everything stays green. Both pipelines exist, so a GL test renders the same scene through the
   old and the new pipeline and compares pixels. That test is the parity gate for the Kotlin behavior the fork carried.
2. **Flip.** One commit that changes the callers in dependency order (`lib-core`, `lib-core-editor`, `lib-physics`, the
   plugin, `app-game-control-line`) to the `g3d` and stock types, drops the shader-key argument, and moves
   `ModelDepthShader` and `FoliageDrawable` to the mat4 convention.
3. **Delete.** Remove the dead fork classes and old GLSL, move the new GLSL into place, and update the docs.

The final order differs from the first sketch (mesh and model, then imports, then shaders) because that order has no green
intermediate state: imports cannot move before the shaders can render stock `Renderable`s.

## Risks / Trade-offs

- **Behavior parity of the Kotlin fork** → the old-vs-new pixel test in stage 1, plus the existing `lib-gdx`, `lib-core`,
  `lib-core-editor` and plugin suites with `-Dabyssus.glTests=true` before the flip is committed.
- **Stock code reading `Mesh` indices as `short`** (`Intersector`, `MeshPartBuilder`, `Mesh.copy`) → `IntMesh` throws
  instead of corrupting; a grep over all modules in stage 1 lists the call sites, and any that touch model meshes are fixed
  there. Terrain and procedural meshes already use stock `Mesh`.
- **`attribute mat4` on other drivers** → the spike ran on macOS (GL 3.2 core) only, and CI does not run GL tests. Before
  stage 2 lands, run the `lib-gdx` and `lib-core` GL suites with `-Dabyssus.glTests=true` on a Windows and a Linux machine
  with a display, recorded as manual checks. A failure there falls back to the same four-column layout bound by a custom
  `InstanceData`.
- **`BaseShader` caches attribute locations at `init`** → the instanced variant is created from an instanced renderable, as in
  the spike; a test enables instancing before the first draw and one disables it afterwards.
- **IntelliJ plugin classloading** → third-party plugins share Abyssus's libGDX classes, so moving to stock types is
  compatible with them. `checkNoJolt` and `checkNoJoltInZip` are unaffected.
- **Open change `add-realistic-water`** (task 3.2) adds model depth passes in `lib-gdx` → a task amends it to build on the
  new `ModelDepthShader` and the mat4 convention.
- **Visual correctness** → the spike checked that geometry is drawn, not shading quality; the pixel comparison against the
  old pipeline covers shading.

Threading and testability: all libGDX calls stay inside `GdxRuntime.withContext` on the canvas's AWT thread, and `prepare`
still decodes textures off-thread without GL. Everything new that constructs a buffer or a program needs a GL context, so
those checks are opt-in GL tests. Pure, GL-free classes are the prefix/define builders (`pbrDefines` and the instanced define),
`InstanceAttributes` and the `IntModel` index and material mapping helpers; they get ordinary unit tests.

## Migration Plan

Stage 1 and stage 3 are safe to ship alone. Stage 2 is one revertable commit. Rollback is to revert that commit; stage 1 keeps
the old pipeline compiling until stage 3 deletes it, so do not start stage 3 until the flip has passed the plugin suite and a
manual `runIde` check (scene, shadows, foliage, a skinned model, the model preview).

External users of `lib-gdx`'s own `ModelInstance` / `ModelBatch` / `Renderable` move to the stock classes; the README gets a
short migration list.

## Open Questions

- Whether to merge the plugin's `batch` and `contentBatch` once both are stock. Deferred; it is a behavior-neutral cleanup.
