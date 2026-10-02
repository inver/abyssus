# ecs

An Ashley-based model of a Mundus scene's `ecs` block: components, the systems Mundus runs, and a loader and writer
that round-trip the scene file. Required behavior: `openspec/specs/scene-ecs-components` and
`openspec/specs/scene-ecs-systems`.

**Only tests use the engine, loader, writer and systems so far; the plugin uses `scene/ComponentEditor.kt`.** The scene view and the tree read the `ecs` JSON directly (`SceneContent`,
`DtoTree.kt`), and gizmo drags write it with `SceneTransformWriter`. Moving those onto this engine is future work.

## Pieces

| Where | What |
|---|---|
| `component/` | `IdComponent` (file id), `NameComponent`, `TypeComponent`, `ParentComponent`, `PositionComponent` (+ `lookAtId`), `CameraComponent`, `LightComponent`, `Point2PointPositionComponent`, `RawComponentsComponent` |
| `render/Render.kt` | `RenderComponent`, `RenderableDelegate`, `AssetResolver` (asset name → renderable), `RenderContext` |
| `system/Systems.kt` | `LookAtSystem`, `SynchronizeRenderComponentSystem`, `SynchronizeCameraComponentSystem`, `SynchronizeRenderPoint2PointSystem`, `RenderComponentSystem` |
| `scene/SceneEcsLoader.kt` | `ecs` JSON → `SceneEngine` + `SceneEcsDocument` |
| `scene/SceneEcsWriter.kt` | `SceneEngine` + `SceneEcsDocument` → `ecs` JSON in Mundus' format |
| `scene/ComponentEditor.kt` | Adds, updates and removes a modeled component in the scene JSON (`ecs.entities.<id>.components`): field tables per kind, defaults from the codecs, reference and cycle checks, and only the differing keys rewritten. Used by the plugin, unlike the rest of this package |
| `scene/ComponentCodecs.kt` | One `ComponentCodec` per modeled component, registered in `ComponentCodecs` |
| `EcsConfigurator.kt` | Builds an engine with the systems at Mundus' priorities (look-at 0, render sync 1, camera sync 2, point-to-point 3, render 4) and loads a scene into it |

## Things that are not obvious

- **Unknown data survives a round trip.** A component without a codec is kept as raw JSON in
  `RawComponentsComponent`, along with the file's component order and the entity's `archetype`. The writer emits
  components in the original order: modeled ones through their codec, carried ones unchanged. Top-level members other
  than `entities` (`archetypes`, `componentIdentifiers`, `metadata`) are kept in `SceneEcsDocument.extras` and written
  after the entities.
- **Defaults are omitted, as Mundus does.** `PositionCodec` writes only the position, rotation and scale fields that
  differ from their defaults. Floats are written whole when they are whole, else as the shortest float text.
- **References are checked.** An id (parent, look-at, point-to-point endpoint) that names no entity in the file becomes
  `NO_ENTITY` (-1) and is reported in `SceneEcsDocument.warnings`.
- **Render components with unsupported renderables.** A `RenderableObjectDelegate` resolves its asset through
  `AssetResolver`. Any other renderable class (Mundus editor-only delegates such as the camera body), or an asset the
  project lacks, loads without a renderable and keeps the file's `renderable` object for write-back.
- **Derived state is not written.** The combined transform and the light instance are rebuilt, not saved.
- **New component:** add a component class and a `ComponentCodec`, register the codec in `ComponentCodecs`, and add
  round-trip cases to `ComponentCodecsTest` / `SceneEcsWriterTest`.
