# Tasks

## 1. Dependencies

- [x] 1.0 Confirm the staged `render-project-models` work is committed (both edit
  `build.gradle.kts`); otherwise stop.
- [x] 1.1 Add `com.badlogicgames.ashley:ashley` (latest release compatible with gdx 1.13.5) to
  `build.gradle.kts`; verify `./gradlew compileKotlin` succeeds, `buildPlugin` bundles it and
  dependency resolution keeps gdx at 1.13.5.

## 2. Components and renderables

- [x] 2.1 Port `NameComponent`, `TypeComponent`, `ParentComponent`, `PositionComponent`,
  `CameraComponent`, `LightComponent` (+ `LightData`) and `Point2PointPositionComponent` to Kotlin as
  Ashley `Component`s in `net.nevinsky.abyssus.ecs.component`; add `IdComponent`. Unit test defaults
  (`-1` ids, unit scale, origin) and `getTransform`/`translate`/`getPosition`.
- [x] 2.2 Port `RenderableDelegate`, `RenderComponent`, `RenderableObjectDelegate` and
  `RenderableSceneObject` (+ `Dto`) with `RenderContext` (the `:gdx-model` `ModelBatch` and the
  environment) and `AssetResolver` in place of Mundus types; unit test that a delegate sets its
  transform and `asComponent` wraps it.

## 3. Loading and writing

- [x] 3.1 Add JSON mappers for camera, light and position/rotation/scale components (libGDX
  `Vector3`/`Quaternion`, reuse `ColorDto`); unit test mapper write/read round trip of camera
  values and of light values in both the direct and the nested-`light` shape.
- [x] 3.2 Add `SceneEcsLoader` with `SceneEntityIds` and `SceneEcsDocument`: entities get
  `IdComponent`, known components load, unknown components and editor-only renderables are kept raw
  (`RawComponentsComponent`) with a once-per-name log, `archetypes`/`componentIdentifiers`/`metadata`
  are kept raw, and dangling id references become `-1` with a log. Unit test inline JSON with
  `PickableComponent`, an editor-only delegate, a missing look-at id, an empty `PositionComponent`
  (`{}` gives origin, identity, `1,1,1`) and a `MODEL` asset with no folder (no renderable, logged,
  other entities load).
- [x] 3.3 Port `EcsConfigurator` (factory creating the `Engine`) and `WorldUtils` (`Engine`
  extension); test loads `src/test/testData/project/Untitled/scenes/Main Scene.scene` and asserts
  entity `0` is `Model 0` / `OBJECT` with a `MODEL` render reference, the Mundus delegate FQN maps,
  the engine has 7 entities, and the file's bytes are unchanged after loading.
- [x] 3.4 Add `SceneEcsWriter`; test load `Main Scene`, write, reload, assert the same entities,
  components and values; assert the written block equals the original ignoring key order (raw
  components, editor renderables, `archetypes`, `componentIdentifiers`, `metadata` included); and
  that no combined-matrix or light-instance fields are written.

## 4. Systems

- [x] 4.1 Port `LookAtSystem` to an Ashley `IteratingSystem`; test fixed positions against
  hand-computed Mundus yaw/pitch, the zero-horizontal-offset case (yaw `90`, no NaN) and the `-1`
  no-op.
- [x] 4.2 Port `SynchronizeRenderComponentSystem`, `SynchronizeRenderPoint2PointSystem` and
  `SynchronizeCameraComponentSystem`; with a recording fake renderable test placement, point to
  point (including a missing endpoint) and camera follow with and without a target.
- [x] 4.3 Port `RenderComponentSystem` and set system priorities in the factory; test two
  renderables are drawn once with render data set, nothing is drawn (no failure) without it, and a
  look-at entity is drawn with the rotation of the same update.

## 5. Integration

- [x] 5.1 Run `./gradlew test` and `./gradlew buildPlugin`; verify both pass, existing tests are
  untouched and scene view / project view behavior is unchanged.
- [x] 5.2 If `add-ai-first-docs` has landed, add the `ecs` package to `AGENTS.md`/`docs/ai`
  (architecture, glossary) and run `scripts/check-docs.sh`.
