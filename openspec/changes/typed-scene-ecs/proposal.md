# Proposal

## Why

`SceneDto.ecs` is a raw `JsonNode`, so every consumer (`EcsLoader`, `SceneContent`, ray snapshots, `PanelBuilders`,
`ProjectReader`, the physics overlay) re-walks JSON by key. The scene's own DTO should describe its entities:
`ecs: Map<Long, EntityDto>` with `EntityDto.components: List<Component>`. Components then come out of the one
`JsonProcessor.bind` call, and the hand-written tree walking in the loaders goes away.

## What Changes

- **BREAKING (file format):** `ecs` is the id-keyed entity map itself and each entity is
  `{ "components": { "<Name>": {...} }, "flags": <int> }`. `components` stays an object keyed by component name.
  `flags` is a new integer bit set per entity; it replaces the entity-level `enabled: false`. The `entities` wrapper,
  `archetypes` and `metadata` members are no longer read or written.
- `SceneDto.ecs` becomes `Map<Long, EntityDto>`; `EntityDto` carries `flags: Int` and `components: List<Component>` (bound from the name-keyed object).
  Unknown, reserved or unbindable components are bound as a `RawComponent` that keeps their payload, so they still
  round-trip and are still reported as warnings.
- `SceneLoader` binds the whole scene in one `JsonProcessor.bind`. `EcsLoader` takes the typed entities (not a `JsonNode`).
  `RuntimeSceneLoader` is fed from `SceneDto`.
- `lib-core-editor` (`SceneContent`, `SceneRenderParams`, ray snapshots, `PanelBuilders`, `EcsWriter`) and the plugin
  (`ProjectReader`, `PhysicsOverlayGeometry`, scene view) read entities from the typed map instead of `JsonNode`.
- Text edits still go through `editSceneJson` and `DocumentTextEditor` on the `SceneJson` tree (the document, not a DTO)
  so number text and key order survive; they are adapted to the new entity shape.
- Remove `SourceBackedComponent` (interface, its `EcsLoader`/`JsonProcessor.bindComponent`/`componentTree` hooks). Its only
  implementer, `FoliageComponent`, keeps unmodeled members through Jackson `@JsonAnySetter`/`@JsonAnyGetter`.
- All `.scene` fixtures and bundled projects are converted once by a script. `docs/ai/file-formats.md` and the specs are updated.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `scene-ecs-components`: the `ecs` block shape (entity `flags`, no `entities` wrapper), typed loading, raw carrying of unknown components.
- `abyssus-document-format`: the `flags` entity member replacing entity `enabled`, rejection of the old `entities` wrapper and `enabled` entity key.
- `scene-loading`: a scene is bound to a typed entity map in one step.

## Impact

- Code: `lib-core` (`dto`, `scene`, `ecs`, `io`), `lib-core-editor` (`document`, `ecs`, `scene`, `ray`, `panel`,
  `components`), `plugin-abyssus` (`dto`, `physics`, `sceneview`, `projectView`), `app-game-control-line` tools.
- Data: every `.scene` in `projects/**` (about 9 fixtures plus Control Line). Old-shape files become unsupported (no
  importer, per the project rules); `formatVersion` stays `1` unless the owner decides to bump it (see design, open question).
- Docs: `docs/ai/file-formats.md`, `projects/lib-core-editor/README.md`, `projects/lib-core/README.md`.
- Out of scope: changing `abyssus` (water) extension block to DTOs (the `todo` in `SceneDto`), the `.abss` and asset
  `meta.json` shapes, any new editing feature.
