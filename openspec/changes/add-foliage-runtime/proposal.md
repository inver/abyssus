# Proposal

## Why

`add-terrain-foliage` lets users author foliage in the editor, but a game cannot use it yet:
- the runtime keeps `FoliageComponent` as unknown raw data;
- nothing outside the scene view draws a bake;
- copies have no collision, so a plane flies through a forest.

Control Line shows the limits of placing scenery by hand. Its Field scene places about 215 trees and bushes as
separate entities, one per copy. That is the exact case foliage was built for. Moving them to foliage, and adding grass
that was impossible before, proves the editor-to-game chain end to end.

## What Changes

- **`FoliageComponent` becomes a built-in runtime component** (`assetName`).
  - Scenes load it into the engine and write it back unchanged, together with any unknown members.
  - A name that is not a project asset folder loads with one warning, and the component is kept.
- **Games can draw foliage.** The runtime lists the foliage of a loaded scene: the entity, its foliage asset and its
  terrain asset.
  - A game draws it with the instanced `FoliageDrawable` from `lib-core`, through `AssetStorage` and `FoliageLoader`.
    These are the same pieces the editor uses, so copies stand on the terrain the same way.
  - The game uses the bake as written. A bake that is out of date is still drawn, with one warning.
  - A missing or unreadable bake draws nothing for that foliage, with one warning. The runtime never regenerates
    copies.
- **Layer colliders.**
  - An OBJECT layer may declare a `collider` with shape `BOX`, `SPHERE` or `CAPSULE`, its sizes and an offset, with
    the same fields and limits as `ColliderComponent`. Sizes are in model units, scaled by each copy's scale and the
    terrain entity's scale.
  - DETAIL layers never collide.
  - The foliage properties panel edits the collider. It is not part of the bake fingerprint, so changing it does not
    make the bake stale.
- **Physics builds static bodies for foliage colliders.** `PhysicsWorld` creates static bodies for every OBJECT layer
  with a collider:
  - Each copy's collider is placed exactly where the copy is drawn.
  - Bodies are grouped by chunk, and `close()` releases them.
  - Bad sizes are refused before they reach Jolt, with one warning naming the foliage and the layer.
  - A world with more than 200,000 foliage colliders leaves the excess layers out, with one warning each.
  - Play in Abyssus Physics, which simulates through `PhysicsWorld`, collides with foliage too.
- **Control Line moves its vegetation to foliage and adds grass.**
  - The 215 tree and bush entities of `Field.scene` are replaced by two foliage assets:
    - `foliage_airfield_site`, on `terrain_airfield_site`;
    - `foliage_airfield_outer`, on `terrain_airfield_outer`. Its masks are empty under the site terrain.
  - Each has one OBJECT layer per species (broadleaf, young and acacia trees; low and tall bushes). Their masks are
    painted from today's positions by a seeded tool. Each species keeps about its current count, its northeast and
    east density, and the clear flying area.
  - A DETAIL grass layer covers the site terrain. It uses a new generated, redistributable grass tuft model and stays
    off the asphalt pad.
  - Scenery colliders stay off, so flight behaviour does not change. The game's field renderer and its sun shadows
    draw foliage: trees cast shadows, grass only receives them.
  - The migration is a committed, rerunnable tool in `tools/` (`FieldFoliage`). The environment documentation records
    it as an estimate of the previous layout.

Native fields:
- **Scene.** `ecs.<id>.components.FoliageComponent: { "assetName": "<folder>" }`, as defined by
  `add-terrain-foliage`, is now read and written by the runtime. Unknown members are carried, and number text is kept
  by the codec rules that already exist.
- **Foliage `meta.json`.** Layers gain an optional `collider`:
  `{ "shape": "BOX" | "SPHERE" | "CAPSULE", "halfExtents": {x,y,z}, "radius", "halfHeight", "offset": {x,y,z} }`.
  Its defaults match `ColliderComponent`, defaults are omitted, and it is ignored on DETAIL layers. Without a
  `collider`, the layer has no collision.
- **The metas are validated** with `AbyssusDocumentFormat` before the runtime or physics reads them.
- **No format version changes.** Neither `foliage.data` nor masks change format.
- **Control Line's `Field.scene` loses 215 entities and gains `FoliageComponent` on terrain entities `0` and `300`.**
  It is rewritten by the tool through `HeadlessEditing`, keeping the formatting and number text of the other entities.

Out of scope:
- An Ashley render system for foliage. Control Line draws its field itself, and a generic `RenderComponentSystem`
  integration can follow when a game needs it.
- Regenerating stale bakes at runtime.
- Dynamic or kinematic foliage, and destructible or moving copies.
- Colliders on DETAIL layers, and convex-hull or mesh colliders for copies.
- Colliders for Control Line scenery. The field-environment requirement that scenery adds no colliders is kept.
- Wind, LOD and impostors.

Depends on `add-terrain-foliage`, which must be applied and archived first. This change adds a requirement to that
change's `terrain-foliage-authoring` capability.

## Capabilities

### New Capabilities
- `foliage-runtime`: games load and draw foliage without the IDE. Covers `FoliageComponent` loading and writing, listing a scene's foliage, drawing bakes as authored, stale and missing bakes, and failure isolation.

### Modified Capabilities
- `terrain-foliage-authoring` (from `add-terrain-foliage`): adds a requirement for optional per-layer colliders, how they are edited and validated in the foliage panel, and that they do not affect the bake.
- `physics-simulation`: foliage colliders become static bodies placed at their copies, with the same refusal rules and resource release as other bodies, and a cap on their count.
- `scene-ecs-components`: `FoliageComponent` is a built-in component that loads and writes back.
- `control-line-field-environment`: vegetation comes from foliage layers instead of individual entities, a grass layer is added, and the clear-flying-area and no-new-colliders rules are checked against foliage copies.

## Impact

- **`lib-runtime`**:
  - `FoliageComponent` and its codec.
  - Registration in `BuiltInComponents`.
  - `SceneFoliage`, the list of (entity, foliage, terrain) for a loaded scene.
  - README updates.
- **`lib-core`**: `FoliageMeta` gains `collider`. The copy transform math, already shared by `FoliageDrawable`, is
  exposed for physics as `FoliageCopyTransforms`.
- **`lib-core-editor`**: settings validation for colliders, and `FoliageMetaEdits` for the `collider` member.
- **`plugin-abyssus`**: collider fields in the foliage panel.
- **`lib-physics`**: `PhysicsAssets` reads foliage meta, bake and terrain. `ShapeFactory` builds a static compound shape
  per chunk. `PhysicsWorld` adds and releases foliage bodies. README updates.
- **`plugin-abyssus-physics`**: no code change. Its play host picks up foliage through `PhysicsWorld`.
- **`app-game-control-line`**:
  - `FieldScene` lists foliage, and `FieldRenderer` and `FieldShadows` draw it.
  - The `tools/FieldFoliage` migration tool, and a generated grass model and texture.
  - The rewritten `Field.scene` and the two new foliage assets.
  - `AirfieldEnvironmentTest` is updated to check foliage copies.
  - `project/environment/README.md` and the game README are updated.
- **Tests**: `lib-runtime`, `lib-physics` (a `Physics` test project copy with a foliage collider layer),
  `lib-core-editor`, `plugin-abyssus` and Control Line.
