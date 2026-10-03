# Design

## Context

Plugin code is Kotlin (JVM 21), libGDX is already bundled (`gdx` 1.13.5), Jackson reads scene
files, and there is no ECS. `:gdx-model` holds the Kotlin fork of Mundus' `ModelBatch`/`Model`
runtime. `sceneview/SceneContent` already reads model/terrain/light placements straight from the
`ecs` JSON for drawing and picking. The Mundus source (`lib-commons/.../core/ecs`, 21 files, ~680 lines)
is Java + Lombok on Artemis-odb and depends on Mundus types the plugin does not have
(`AssetManager`, `TerrainService`, `ModelService`, `SceneEnvironment`, `ModelObject`,
`TerrainObject`, `BaseLight`/`DirectionalLight`, `Vector3Dto`; its `ModelBatch` exists here as the
`:gdx-model` fork). The plugin
uses libGDX **Ashley** instead of Artemis. They differ where it matters here: Artemis entities are
integer ids with `@All` aspects, `EntityLinkManager` and a JSON serializer (the scene format is
that serializer's output); Ashley entities are objects with auto-assigned ids, `Family` +
`ComponentMapper`, and no serializer.
`Main Scene` shows what real files contain: an `ecs` object with `entities` (7, ids `0`-`6`),
`archetypes` (id → component short names), `componentIdentifiers` (Mundus FQN → short name) and
`metadata` (`{"version": 1}`); components the port does not define (`PickableComponent`,
`DependenciesComponent`); and `class` values naming editor-only delegates
(`com.mbrlabs.mundus.editor...`) next to `...commons.core.ecs.delegate.RenderableObjectDelegate`.
No test scene has a `LightComponent`; `SceneContent` accepts its `color`/`intensity` either directly
or in a nested `light` object, with the kind taken from `TypeComponent` `LIGHT_*`.
See proposal.md for motivation.

## Goals / Non-Goals

**Goals:** port of components, systems and loading to Ashley with Mundus' behavior; loads and
writes real Mundus scene files; no Mundus or Artemis dependency; testable without a GL context.

**Non-Goals:** rendering, GL resources, picking, UI; replacing `SceneDto.ecs: JsonNode`; moving
`SceneContent`/`ScenePicker` onto the engine (follow-up change); loading terrain/model geometry
(owned by `render-project-models`); editing or saving scenes from the UI.

## Decisions

1. **Kotlin on Ashley, package `net.nevinsky.abyssus.ecs`** (`component`, `system`, `render`,
   `scene`), no Lombok. Components implement `com.badlogic.ashley.core.Component` with plain
   fields. Alternative: keep Artemis (what Mundus uses) — rejected by the user's choice of Ashley,
   which is already part of the libGDX family the plugin uses.
2. **Own Jackson loader and writer** (`SceneEcsLoader`/`SceneEcsWriter`) own the file format
   (`entities` → id → `archetype`/`components`), reading and writing through the plugin's shared
   scene mapper (`dto.Json` now, `SceneJson` after `bind-assets-with-object-mapper`), so float text
   is preserved the same way. Each known component has a small mapper to and from `JsonNode`.
   Alternative: port Artemis' JSON serializer — rejected, it is bound to Artemis' `World` and
   entity-id model.
3. **File ids are kept explicitly.** An `IdComponent(id: Int)` carries the id from the file and a
   `SceneEntityIds` map (id ↔ `Entity`) resolves it. Components that refer to other entities
   (`PositionComponent.lookAtId`, `ParentComponent.parentEntityId`,
   `Point2PointPositionComponent.entity1Id/entity2Id`) keep the file id as `Int`, `-1` meaning
   none, and systems resolve them through the map. A reference to an id not in the file is
   normalised to `-1` and logged once. Alternative: hold `Entity` references in components —
   rejected, awkward for write-back and the `-1` convention.
4. **Unknown things are carried, not interpreted.** The loader maps only the components it knows;
   every other component of an entity (`PickableComponent`, `DependenciesComponent`) is kept as
   its raw `JsonNode` in a `RawComponentsComponent` (name → node, file order). A render component
   whose `renderable.class` is not a known delegate loads without a renderable and keeps its raw
   `renderable` node. The entity's `archetype` id and the block's `archetypes`,
   `componentIdentifiers` and `metadata` are kept as raw nodes on a `SceneEcsDocument` returned
   with the engine. The writer emits known components from their mappers and raw ones unchanged,
   in the file's component order, so a written scene still opens in Mundus. Each distinct carried
   name is logged once; the file on disk is never modified. Alternative: drop unknowns and write
   only modeled components — rejected, it silently strips editor data (pick ids, camera handles).
5. **Class names.** The loader maps the Mundus FQN
   `com.mbrlabs...delegate.RenderableObjectDelegate` to the plugin delegate and the writer emits
   that FQN again, so files stay Mundus-compatible.
6. **Decouple through plugin interfaces.** `RenderableDelegate` keeps `setPosition`,
   `set2PointPosition`, `render`; `render` takes an opaque `RenderContext` (batch and environment
   supplied by the scene view). The loader takes an `AssetResolver` (`resolve(type, name)`) in
   place of `AssetManager` + `TerrainService` + `ModelService`; the default resolver returns a
   reference (`type`, `assetName`) with no geometry when the asset folder exists
   (`ProjectLayout.assetFolders`) and `null` when it does not; `render-project-models` can supply a
   real one. `RenderContext` carries the `:gdx-model` `ModelBatch` and the environment.
   `LightComponent` holds a plugin `LightData` (color, intensity), read from the component or its
   nested `light` object as `SceneContent` does, and written back in the shape it was read in; the
   light kind stays on `TypeComponent`. Components hold libGDX `Vector3`/`Quaternion`; the JSON
   mappers read and write `x`/`y`/`z`(/`w`) directly, so no `Vector3Dto` is needed; the existing
   `ColorDto` is reused for colors.
7. **Systems on Ashley.** Each is an `IteratingSystem` over a `Family.all(...)` with
   `ComponentMapper`s replacing `@All`. Mundus' registration order is kept through system
   priorities: look-at, render placement, camera sync, point-to-point, render pass. The LookAt math
   and the null no-op of `setRenderData` are ported 1:1; scratch vectors are per-system
   (Ashley's `Engine.update` is single-threaded). Equivalence is checked with numbers computed
   from the Mundus implementation.
8. **`EcsConfigurator`** becomes a factory that creates an `Engine` with the systems and loads a
   scene into it; `WorldUtils.getFromWorld` becomes an `Engine` extension with the same
   `(id, component) → R` shape.
9. **Packaging:** only `ashley` is added (`implementation`, bundled like libGDX); no Artemis or
   jsonbeans.

## Risks / Trade-offs

- [Own loader drifts from the format Mundus writes] → a `Main Scene` round-trip test (load, write,
  reload) and a key-order-insensitive comparison of the whole `ecs` block against the original,
  including raw components, `archetypes`, `componentIdentifiers` and `metadata`.
- [Light shape guessed, no real fixture] → inline fixtures for both shapes `SceneContent` accepts;
  replace with a real Mundus light scene when one is available.
- [Ashley ordering and entity-removal semantics differ from Artemis] → explicit system priorities
  with an ordering test; entities are not removed during an update.
- [Ashley's own `Entity.id` differs from the file id] → never used for references; only
  `IdComponent`.
- [`RenderContext` has no real user yet] → kept minimal; becomes concrete with
  `render-project-models`.
- [Ashley bundle/classloader surprises in the plugin] → exercised by tests and `buildPlugin` /
  plugin verifier.

## Open Questions

- Ashley version: default to the latest release (1.7.4) unless it conflicts with gdx 1.13.5; decided
  in task 1.1.
