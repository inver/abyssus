# Design

## Context

See proposal.md for motivation. Current state:

- `SceneRenderer` owns a libGDX `ModelBatch`, an `Environment`, a camera and a grid `ModelInstance`; it runs only inside `GdxRuntime.withContext` on the AWT thread, with a `Gdx` shim (no libGDX backend, `Gdx.files` is `Lwjgl3Files`).
- `SceneFileEditor` reads the scene via `SceneReader`, builds `SceneRenderParams` and pushes them to `SceneViewPanel.setParams`; it reloads on `VFileContentChangeEvent`.
- Mundus' `AssimpModelLoader` (lib-commons) returns **Mundus' own** `net.nevinsky.abyssus.core.model.Model`, not libGDX's `com.badlogic.gdx.graphics.g3d.Model`. That `Model` is built from Assimp `ModelData` (no GL needed for parsing) and creates meshes/textures in its constructor, i.e. **it needs a current GL context**. Rendering goes through Mundus' own `ModelInstance` / `ModelBatch` (lib-core). The textures are loaded by `ParentBasedTextureProvider` through `Gdx.files.internal(path)`.
- Scene entities carry `RenderComponent.renderable.asset = {type, assetName}` and `PositionComponent` (`localPosition`, `localRotation`, `localScale`); `assetName` is the folder name under `<project>/assets`. A model folder's `meta.json` holds `additional.file` (e.g. `model.gltf`).
- Mundus is a sibling checkout (`../Mundus`) used only as a reference; its code is copied into the plugin, not consumed. Its Gradle build uses a jitpack dependency (`gdx-gltf` `master-SNAPSHOT`) and Lombok.

## Goals / Non-Goals

**Goals:**
- Draw placed `MODEL` and `TERRAIN` entities, textured, with correct transform, plus the skybox, light entities and model animations.
- Click an entity to select its node in the Abyssus project view.
- Keep the AWT/GL thread free of file IO and Assimp parsing; keep all GL calls on the GL thread.
- A bad asset never breaks the rest of the view.

**Non-Goals:**
- Entity hierarchy/parenting (`ParentComponent`), `CameraComponent` entities, selection highlight in the view, editing, gizmos, shadows.
- Mundus' PBR/custom shaders and splat-map terrain shader beyond what the spike (task 1.3) shows is needed; the first pass uses the default shader of the chosen batch with the scene `Environment`.
- Animation controls (play/pause, clip selection): the first clip loops, always.
- Editing: the view stays read-only.

## Decisions

**1. Vendor a minimal subset of Mundus into the plugin** (e.g. package `net.nevinsky.abyssus.sceneview.model`), rather than depending on Mundus as an included build or a `mavenLocal` artifact.
Why: the Mundus build is heavy and fragile for a plugin (jitpack snapshot, Lombok, its own toolchain), and the plugin only needs the model loading path. Copy: the Assimp pipeline of `lib-assets` (~1,400 lines: importer, material/mesh/node/animation/texture processors, scene normalizer, `AssimpModelDataLoader`, `AssimpFlags`), `AssimpModelLoader` and `ParentBasedTextureProvider`, the `Model`, `ModelData`, `ModelMesh`, `ModelMeshPart` and `PbrModelMaterial` classes with the node/mesh/animation classes they use, and `ModelInstance`/`ModelBatch`/shader classes only if the spike (task 1.3) shows a plain libGDX render path does not work. Keep the copy as small as the spike allows; drop the exporter and anything unused. The copy was converted to Kotlin (IntelliJ J2K plus manual fixes), so it lives in `src/main/kotlin`; only the generated GLTF parser stays Java. Each file keeps its origin/license header and the source commit is recorded in a README note under the package. Third-party deps (`lwjgl-assimp`, `gdx-gltf`) are declared directly in `build.gradle.kts`.
Alternatives: Gradle included build of `../Mundus` (rejected by the user: couples the plugin build to another repo); `publishToMavenLocal` (manual step, stale artifacts).

**2. Two-phase load: parse off-thread, build on GL thread.** `AssimpModelLoader.loadModel` constructs `Model` (GL objects) in one call, so it cannot be split. It is therefore run **on the GL thread** from the render loop, one model per frame, under `GdxRuntime.withContext`, with the file reading cheap-first: placements and `meta.json` are resolved off-thread (background read action / pooled thread) into a `ModelRequest(assetName, file)` list; only the `loadModel` call runs in the render loop. Alternative: load `ModelData` with `AssimpModelDataLoader` off-thread and construct `Model(data, provider)` on the GL thread — this is the better split and is preferred if the spike (task 1.3) shows `Model(ModelData, TextureProvider)` is the only GL-bound step. Spike result (task 1.3, rendered through a real GL 3.2 core canvas): the `Model(ModelData, TextureProvider)` constructor is the only GL-bound step, so `loadData` runs on a pooled thread and `build` on the GL thread, one model per frame; Mundus' `DefaultShaderProvider` shaders compile in the plugin's context and textured models draw correctly with the libGDX `Environment`. The whole of lib-core is copied unpruned: its shape builders, meshes and shaders are mutually entangled (removing `builder/` breaks `MeshBuilder`), so pruning costs more than it saves.

**3. Cache per asset folder in a `SceneModels` holder owned by `SceneRenderer`.** `Map<assetName, LoadedModel | Failed>`; instances (one per entity) share the loaded `Model`. A scene reload diffs placements: new assets are queued, assets no entity uses any more are disposed on the GL thread. Failed assets are remembered (and logged once) so they are not retried every frame.

**4. Placements are read into a small immutable model** (`ModelPlacement(assetName, position, rotation, scale)`), parsed in the same pass as `SceneRenderParams` by a `ScenePlacements.parse(json)` function, so it is unit-testable without GL. `SceneRenderParams` gains `placements` and `projectAssetsDir`; the renderer receives them through the existing `params` volatile.

**5. Textures resolve through `Gdx.files.internal`.** `ParentBasedTextureProvider` passes absolute paths to `Gdx.files.internal`; with `Lwjgl3Files` this works for absolute paths (falls back to the file system). A `loadModel` call also extracts embedded textures to `<modelDir>/embedded`, writing into the user's project; accepted for now (same as the Mundus editor) and noted as a risk.

**6. Terrain.** Port Mundus' terrain reader (`terrain.data` = big-endian floats, `size`, `uv`; at most 255x255 heights because of 16-bit indices) and build one libGDX `Mesh` per terrain asset on the GL thread. The Mundus terrain shader lives in the editor app and was not copied: `TerrainShader` is a small program of the plugin that blends the base and R/G/B/A layers by the splat map (neutral gray when there are none) and applies the scene's ambient, directional and point lights and fog. Terrain instances are cached per asset like models and share the failure handling of Decision 3.

**7. Skybox.** Build a cubemap-style box from the six faces of the skybox asset's `meta.json`, drawn first with depth writes off and the camera translation removed, so everything else always draws over it. When the skybox is not shown the clear color applies.

**8. Lights.** Light entities are read into `LightPlacement(kind, color, intensity, position, direction, range, angle)` by `SceneContent.parse` and mapped onto the `Environment` as directional / point / spot light attributes next to the ambient light; caps follow the shader's supported counts. The on-disk shape of Mundus' `LightComponent` is not visible in the test project (no light entities, and the `light` field is `transient`); the reader accepts `TypeComponent.type = LIGHT_<KIND>` with color and intensity either in `LightComponent` or its nested `light`, and a directional light shines along its rotated -Z. This is an assumption, covered by fixtures in `SceneContentTest`. The copied Mundus shaders implement directional and point lights only (`numSpotLights = 0`), so spot lights are drawn as point lights.

**9. Animation.** Each model entity gets its own animation controller (from the copied `AnimationController`) started on the first clip, looping; `GdxFrame.deltaSeconds` is the time step. Static models skip the update.

**10. Picking.** On a click (press+release within a small drag threshold, in `SceneViewPanel`) cast a ray from the camera through the cursor and test it against each instance's transformed bounding box (models) and the terrain heightfield; the nearest hit gives the entity id. No color-id render pass. The panel reports `entityId` to the editor, which asks the Abyssus project view pane to select the node whose path is `<scene node>/ecs/entities/<id>` (`AbyssusProjectViewPane` gains a `select(sceneFile, entityId)` entry that opens the Abyssus pane and uses the platform's tree selection). Bounding boxes (the model's local bounds, computed once per entity and moved by its transform) can select a model by its box rather than its exact mesh and ignore animation poses; accepted for this version.

**11. Auto-framing is not changed.** The camera still comes from the project's `mainCamera`; models are placed by world coordinates.

## Risks / Trade-offs

- [Copied code drifts from upstream Mundus] → record the source commit in the package README and in file headers; accept the snapshot.
- [License/attribution of copied files] → keep original headers and add attribution to the README.
- [LWJGL version clash: plugin uses 3.4.3, Assimp natives are 3.3.1] → resolved: Assimp 3.3.1 fails at runtime (`NoSuchMethodError AIScene.wrap`) against core 3.4.3, so `lwjgl-assimp` is aligned to the plugin's LWJGL version (3.4.3), verified by `AssimpLoadingTest`; versions are now declared directly in the plugin's `build.gradle.kts`.
- [Mundus `ModelBatch`/shaders may not compile under the GL 3.2 core context / `GdxRuntime` prefixes] → spike renders one model first; fallback is converting the copied `Model` meshes to libGDX `Model` or using the plain libGDX `ModelBatch` with a mesh adapter.
- [`loadModel` stalls a frame on large models] → one model per frame, results cached; acceptable for a read-only view.
- [Embedded-texture extraction writes into the project] → keep Mundus behaviour; revisit with `importModel`'s temp dir approach if users object.
- [Terrain data format / splat shader not covered by the default shader] → port the reader as is; if splat blending needs Mundus' terrain shader, copy it, otherwise ship the plain material and leave splat blending for later (the splat requirement scenario then needs the shader copied; task 3.x decides).
- [Light serialization shape unknown] → spike against a project with lights; if the shape cannot be determined, lights are skipped and logged and the spec's light scenarios are not met, so this is resolved before the lights task is closed.
- [Bounding-box picking is coarse] → acceptable for "find it in the tree"; refine with mesh triangles only if users ask.
- [Selecting a tree node from a Swing mouse event must run on the EDT and may need the node to be loaded lazily] → select by path through the project view's tree structure with expansion, falling back to opening the scene node.
- [Plugin jar size grows with Assimp natives for 4 platforms] → shadow jar `minimize`/exclude unused natives only if size becomes a problem.

## Open Questions

- Should rendering switch to a model-bounds based camera when `mainCamera` is far from the models? Deferrable; no spec impact.
