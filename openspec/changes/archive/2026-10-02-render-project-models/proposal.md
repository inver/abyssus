# Proposal

## Why

The scene view draws only the scene's environment (clear color, ambient light, fog) and a ground
grid. A scene's actual content — its models, terrain, skybox and lights — is invisible, so the
view cannot answer "what does this scene look like", and nothing in it can be clicked to find the
entity in the project. Mundus (the editor that produces these projects) already has an
Assimp based model loader that turns an asset's model file into a renderable model; the view can
reuse it instead of growing its own importer.

## What Changes

- The scene view loads every `MODEL` asset a scene places, from the `assets` folder beside the
  project's `.abss` file, with Mundus' `AssimpModelLoader.loadModel(FileHandle)`, and draws it in
  the view at the entity's position, rotation and scale.
- A scene's model entities are found through its ECS data: an entity with a `RenderComponent`
  whose `renderable.asset.type` is `MODEL` and whose `assetName` names an asset folder, placed by
  its `PositionComponent` (`localPosition`, `localRotation`, `localScale`).
- Loading happens off the AWT/GL thread (Assimp needs no GL context); GPU upload (meshes,
  textures) happens on the GL thread inside `GdxRuntime.withContext`. Loaded models are cached per
  asset folder and shared by all entities that use it; they are disposed with the renderer.
- A model that cannot be found or loaded is skipped (logged once) and the rest of the scene still
  renders.
- The view also draws the scene's **terrain** entities (height data from the terrain asset's
  `terrain.data`, textured from its splat maps when it has them), its **skybox** (the six-face
  skybox asset named by `skyboxName` when `skyboxEnabled`), and the **lights** defined by light
  entities (directional, point, spot) on top of the scene's ambient light.
- Models with animations play automatically (looping), driven by the frame time.
- **Picking**: clicking an entity in the view selects its node (`ecs/entities/<id>`) in the Abyssus
  project view, expanding the tree as needed. Picking is a plain click (no drag) and does not
  modify anything.
- The plugin vendors the Mundus loading code instead of depending on Mundus: `AssimpModelLoader`,
  the `lib-assets` Assimp pipeline, and the Mundus `Model`/`ModelData`/`ModelInstance` classes it
  needs are copied into the plugin under its own package. There is no build-time or runtime
  dependency on the Mundus repository. Third-party libraries are declared directly
  (`lwjgl-assimp` with natives, `gdx-gltf` if the copied `Model` needs it).

## Capabilities

### New Capabilities
- `scene-model-rendering`: drawing the models a scene places, loaded from the project's assets,
  inside the scene view.
- `scene-terrain-rendering`: drawing a scene's terrain entities from the project's terrain assets.
- `scene-skybox-rendering`: drawing the scene's skybox as the view's background.
- `scene-entity-lights`: lighting the view with the light entities of the scene.
- `scene-model-animation`: playing the animations of displayed models.
- `scene-picking`: selecting an entity in the Abyssus project view by clicking it in the scene view.

### Modified Capabilities

None. There is no main spec for the scene view yet (it is not captured under `openspec/specs/`).

## Impact

- Code: `sceneview/SceneRenderer.kt` (draw skybox, models, terrains; apply lights; advance
  animations), `sceneview/SceneViewPanel.kt` / `SceneFileEditor.kt` (hand the scene's placements and
  project root to the renderer, refresh on scene change, click handling),
  new `sceneview/SceneContent.kt` (read model/terrain/light placements from scene JSON),
  `sceneview/SceneModels.kt` / `SceneTerrains.kt` / `SceneSkybox.kt` (async load + GL upload caches),
  `sceneview/ScenePicker.kt` (ray vs. bounds), `projectView/AbyssusProjectViewPane.kt` (select an
  entity node by path), `scene/SceneDto.kt` (only if placements are read through the DTO).
- Dependencies: `build.gradle.kts` adds `lwjgl-assimp` 3.3.1 with all platform natives, `gdx-gltf`
  and, only if the copied code needs it, Lombok as an annotation processor. LWJGL alignment with the
  existing `lwjgl 3.4.3` and shadow-jar bundling must be checked. No Mundus path in any Gradle file.
- Vendored code: also the terrain reader/mesh code and skybox loading from Mundus `lib-commons`,
  and the light classes; copied as a snapshot of the sibling Mundus checkout (it no longer follows upstream);
  each copied file keeps its origin and license/attribution header, and the source commit is recorded.
- Packaging: plugin jar size grows with the Assimp natives.
- Tests: content parsing over `src/test/testData/project/Untitled` (`Main Scene` places models
  from 4 model assets and one terrain); load/skip behaviour with a missing or corrupt asset; picking
  ray tests; entity-node selection in the project view.
