# Tasks

## 1. Vendored loader and spike

- [x] 1.1 Copy the Mundus Assimp loader and the model classes into the plugin (package per design Decision 1), keeping origin/license headers and recording the source commit in a package README. Add `lwjgl-assimp` (+ natives; version aligned to the plugin's LWJGL, 3.4.3 — 3.3.1 fails with `NoSuchMethodError` against LWJGL 3.4.3 core), `gdx-gltf` and, if needed, Lombok to `build.gradle.kts`. Verify: `./gradlew compileKotlin compileJava` passes and no Mundus path appears in any Gradle file.
- [x] 1.2 Resolve classpath conflicts (LWJGL 3.4.3 vs Assimp 3.3.1 natives, duplicate Gdx classes, Kotlin/Java 21 toolchain). Verify: `./gradlew buildPlugin` succeeds and the shadow jar contains the Assimp natives for macOS arm64/x64, Windows and Linux.
- [x] 1.3 Spike: in `GdxRuntimeTest`-style test (or a temporary `runIde` run) load `src/test/testData/project/Untitled/assets/model_29e9be61-.../model.gltf` with `AssimpModelLoader.loadModel` inside `GdxRuntime.withContext` and render it once with Mundus' `ModelBatch`. Record in `design.md` Decisions 1 and 2 which lib-core classes must be copied (prune the rest) and whether the `ModelData` (off-thread) / `Model` (GL thread) split works, whether Mundus shaders compile under the GL 3.2 core context, and which terrain/skybox/light classes must also be copied; adjust Decision 2 and the fallback accordingly. Verify: model draws non-empty (pixels differ from clear color) or the fallback is documented.

## 2. Reading scene content

- [x] 2.1 Add `SceneContent.of(scene)` returning `ModelPlacement(entityId, assetName, transform)` placements for entities with a `MODEL` render asset, defaulting missing transform fields (origin / identity / unit scale) and ignoring terrain and malformed entities. Add `SceneContentTest` over `Main Scene.scene` (placements from the four `model_...` assets; terrain entities are not model placements) plus defaults and malformed-entity cases. Verify: test passes.
- [x] 2.2 Add `ProjectAssetFiles` (`model`, `terrain`, `skybox`) returning the file from `<project>/assets/<assetName>/meta.json` (`additional.file`) beside the `.abss`; null when the folder, meta or file is missing. Test with the `Untitled` project and a missing asset. Verify: test passes.
- [x] 2.3 Extend `SceneRenderParams` (and `SceneRenderParamsTest`) with the parsed content (model/terrain/light placements, skybox choice) and resolved asset files; `SceneFileEditor.reload` fills them on a background thread, then pushes to the panel. Verify: existing params tests still pass and a new test shows placements flow through `from(...)`.
- [x] 2.4 Extend `SceneContent.of(scene)` to also return terrain placements, light placements (kind, color, intensity, position, direction, range, angle) and the skybox choice (`skyboxEnabled` + `skyboxName`), with `entityId` on every placement. Confirm the light serialization shape against a project that contains lights (add one to the test data if none exists). Verify: `SceneContentTest` over `Main Scene.scene` (4 models, 1 terrain, skybox none) plus a fixture with lights and a skybox.

## 3. Rendering

- [x] 3.1 Add `SceneModels` (owned by `SceneRenderer`): per-asset cache of loaded model or failure, instance-per-entity creation with position/rotation/scale, diff on param change, disposal of unused models, one `loadModel` per frame, failures logged once. Verify: unit test of the diff/cache logic with a fake loader (no GL): reuse across entities, removal disposes, failure not retried.
- [x] 3.2 Render the instances in `SceneRenderer.render` next to the grid with the scene `Environment`, and dispose models in `SceneRenderer.dispose` (inside the GL context, per the existing teardown lifecycle). Verify: `runIde` with `-PideProject=src/test/testData/project/Untitled`, open `Main Scene.scene` → models appear textured at their positions; closing the tab leaves no GL errors in `idea.log`.
- [x] 3.3 Failure isolation: missing asset, corrupt model file and unknown renderable types do not stop other models (add a test project fixture or temp-dir test for the missing/corrupt cases). Verify: tests pass and the view still renders in `runIde` with a deliberately broken asset.
- [x] 3.4 Live update: editing the `.scene` (add/remove/move an entity) updates the view without reopening it. Verify: manual `runIde` check plus a `SceneFileEditorTest` case that the new params reach the renderer.

## 4. Terrain and skybox

- [x] 4.1 Port the Mundus terrain reader (`terrain.data`, `size`, `uv`, splat ids from `meta.json`) and add `SceneTerrains` building one mesh per terrain asset on the GL thread, drawn with the entity's transform; share the cache/failure handling of `SceneModels`. Decide and record whether the splat shader is copied. Verify: reader test over the `Untitled` terrain (vertex count and height range), a corrupt-data test that skips cleanly, and `runIde` shows the terrain under the models.
- [x] 4.2 Add `SceneSkybox`: load the six faces from the skybox asset, draw first with depth writes off and camera translation removed; clear color when disabled/unnamed/failing. Verify: unit test of the choice logic (enabled+named / disabled / null / missing face) and `runIde` with a scene that names `skybox_default`.

## 5. Lights and animation

- [ ] 5.1 Map `LightPlacement`s onto the `Environment` (directional, point, spot) with per-kind caps; unreadable lights skipped. Verify: unit test of the mapping and caps with a fake environment, and `runIde` shows shading change when a light entity is added to the scene file.
- [x] 5.2 Start the first animation of each animated model entity (copied `AnimationController`, looping, per-entity time from `GdxFrame.deltaSeconds`), skip static models, stop on removal/dispose. Verify: unit test that two instances of one model advance independently and that a static model has no controller; `runIde` shows an animated sample moving.

## 6. Picking

- [x] 6.1 Add `ScenePicker`: ray from camera and cursor against transformed bounding boxes and the terrain heightfield, nearest hit wins, returns `entityId` or null. Verify: `ScenePickerTest` for hit, miss, nearest-of-two and terrain-behind-model cases (no GL).
- [x] 6.2 Click handling in `SceneViewPanel` (press/release within a drag threshold; drags only orbit/pan) reporting the picked `entityId` to the editor. Verify: a test that a drag does not pick and a click does, using synthetic mouse events and a fake picker.
- [x] 6.3 `AbyssusProjectViewPane.select(sceneFile, entityId)`: open the Abyssus pane and select `<scene node>/ecs/entities/<id>`, expanding as needed; no-op when not found. Verify: `AbyssusViewTest`-style test with the `Untitled` project selects the expected node; manual `runIde` click selects the node.

## 7. Wrap-up

- [x] 7.1 Update `README.md` / `CHANGELOG.md` (what the view shows now: models, terrain, skybox, lights, animation, click-to-select; origin and license of the copied Mundus code). Verify: README build instructions work from a clean clone with no Mundus checkout; `./gradlew check` passes.
