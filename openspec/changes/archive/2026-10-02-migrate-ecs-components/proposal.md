# Proposal

## Why

A scene's `ecs` block is today only opaque JSON (`SceneDto.ecs` is a Jackson `JsonNode`). Mundus
(the editor producing these projects) defines what that block means: an entity-component model (Artemis in the editor) with
`Name`, `Type`, `Position`, `Parent`, `Render`, `Camera`, `Light` and `Point2PointPosition`
components, and systems that keep rendering, camera and look-at state in sync.
`render-project-models` draws and picks scene content by reading that JSON directly
(`sceneview/SceneContent`), which covers static placement only: it ignores parents, look-at and
point-to-point links, and every new feature re-parses the same raw fields. A live, typed ECS engine
built from a scene file, porting Mundus' `core/ecs` behavior once, gives later features
(inspection, editing, moving `SceneContent` onto it) one model with Mundus semantics.

## What Changes

- Add libGDX Ashley as the plugin's ECS and port the behavior of the Mundus `commons/core/ecs`
  package (Artemis-odb) to Kotlin on Ashley under
  `net.nevinsky.abyssus.ecs`: components (`NameComponent`, `TypeComponent`, `PositionComponent`,
  `ParentComponent`, `CameraComponent`, `LightComponent`, `Point2PointPositionComponent`,
  `RenderComponent`), the renderable abstractions (`RenderableDelegate`,
  `RenderableObjectDelegate`, `RenderableSceneObject`), the systems (`LookAtSystem`,
  `SynchronizeRenderComponentSystem`, `SynchronizeCameraComponentSystem`,
  `SynchronizeRenderPoint2PointSystem`, `RenderComponentSystem`), the `WorldUtils` helper and
  the `EcsConfigurator` factory. Components and systems are rewritten against Ashley's API, not
  copied.
- Add a Jackson-based scene ECS loader and writer (Ashley has no serializer): a scene's `ecs`
  block loads into an Ashley `Engine` and the engine writes back to the same format Mundus
  produces. Entities keep the integer ids of the file; references between entities (look-at,
  parent, point-to-point) resolve through them. What the port does not model (editor components
  such as `PickableComponent`, editor-only renderables, `archetypes`, `componentIdentifiers`,
  `metadata`) is kept as raw JSON and written back unchanged.
- Cut every dependency on Mundus-only types (`AssetManager`, `TerrainService`, `ModelService`,
  `SceneEnvironment`, `Vector3Dto`/`ColorDto` from `commons`): the ported code talks to small
  plugin-owned interfaces instead. The `ModelBatch` it draws with is the fork already in
  `:gdx-model`. Mundus is not a build or runtime dependency (same rule as `render-project-models`).
- Drop Lombok; use Kotlin properties. No Apache `commons-lang3` `Pair`.
- Out of scope: drawing anything, GL, picking, UI. The scene view and project view are unchanged;
  `SceneDto.ecs` stays a `JsonNode` for the project view, and `SceneContent` keeps reading JSON.
  Moving the scene view (`SceneContent`, `ScenePicker`) onto the engine is a separate follow-up
  change, owned by neither this change nor `render-project-models`.

## Capabilities

### New Capabilities
- `scene-ecs-components`: a scene's `ecs` block is read into typed entity components and written
  back in the Mundus format.
- `scene-ecs-systems`: the behaviors that derive state from components (look-at rotation,
  render and camera synchronisation, point-to-point placement, render pass).

### Modified Capabilities

None.

## Impact

- `build.gradle.kts`: add `com.badlogicgames.ashley:ashley` (libGDX is already a dependency);
  bundled into the plugin jar like libGDX. No Artemis or jsonbeans.
- New package `src/main/kotlin/net/nevinsky/abyssus/ecs/**` and tests under `src/test/kotlin/.../ecs`.
- No change to plugin.xml, existing file types, project view or scene view behavior.
- Overlap to note: apply after the staged `render-project-models` work is committed (both edit
  `build.gradle.kts`). The loader uses the plugin's shared scene mapper: `dto.Json` today,
  `SceneJson` once `bind-assets-with-object-mapper` lands; whichever lands second follows the other.
  This change does not touch other changes' artifacts.
