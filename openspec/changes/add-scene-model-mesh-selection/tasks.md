## 1. Scene format: RenderComponent gains meshId

- [ ] 1.1 Add optional `meshId: String?` field to `RenderComponent` in `projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/ecs/component/RenderComponent.kt`; verify with a unit test that serializing/deserializing a RenderComponent preserves meshId.

## 2. Model loading: filter ModelData by meshId + material

- [ ] 2.1 In `ModelLoader` (`projects/lib-core/src/main/kotlin/net/nevinsky/abyssus/lib/core/assets/model/ModelLoader.kt`), parse the composite `name#meshId` request key, strip `#meshId` to find the real asset, and filter `PreparedModel.data` to the selected mesh and its material before `buildIntModel`; verify with a headless test that loading `asset#mesh` yields a model containing only that mesh.
- [ ] 2.2 Handle `meshId` absent (key is just `name`) so existing whole-model behavior is unchanged; verify existing model load tests still pass.

## 3. Editor document layer: read/write meshId

- [ ] 3.1 Add `meshId` to `RenderAsset` and `renderAssetOf` in `SceneDocument` (`projects/lib-core-editor/.../document/SceneDocument.kt`); verify a unit test round-trips meshId through scene JSON.
- [ ] 3.2 Add `meshId` ComponentField to RenderComponent in `BuiltInComponentKinds` (`projects/lib-core-editor/.../components/BuiltInComponentKinds.kt`); verify the properties field round-trips meshId.
- [ ] 3.3 Add `meshId` field to `AssetPlacement` (`projects/lib-core-editor/.../content/Placements.kt`) and map `RenderComponent.meshId` → placement in `PlacementMapper`; verify a unit test maps a meshId render component to a placement.
- [ ] 3.4 Thread meshId into `ModelEntity` in `PlacedEntities`/`SceneModels`; verify a placed entity carries its meshId.

## 4. Editor scene view: draw filtered model per (asset, meshId)

- [ ] 4.1 `SceneModels`/`ModelEntity` request and cache the built model per composite `asset#meshId` via `AssetView`/`ViewAssets`; verify two entities using different meshes of one asset get different filtered models.
- [ ] 4.2 `SceneRenderer` draws the filtered model instance (works automatically once pre-filtered); verify via a headless/runIde check that an entity naming meshId draws only that mesh.
- [ ] 4.3 Properties panel mesh chooser lists a model asset's mesh ids for a model entity; verify via runIde that choosing a mesh writes meshId and the view redraws.

## 5. Ray tracing: snapshot uses the filtered model

- [ ] 5.1 `RaySceneSnapshot` uses the meshId-filtered model for the entity's placement; verify a ray test that a meshId entity's snapshot bounds match only that mesh.

## 6. Game runtime: draw filtered model per (asset, meshId)

- [ ] 6.1 `FieldRenderer` (`projects/app-game-control-line/.../render/FieldRenderer.kt`) requests models per `asset#meshId` and draws the filtered instance; verify the game loads a meshId-placed model and draws only that mesh.

## 7. Asset invalidation for mesh variants

- [ ] 7.1 When a model asset is invalidated, drop its `name#meshId` variants too (or document the reload requirement); verify invalidation clears mesh variants.

## 8. Specs, docs, and final verification

- [ ] 8.1 Confirm `openspec/specs/scene-model-rendering/spec.md` and `scene-asset-placement/spec.md` are unchanged (change owns delta specs only).
- [ ] 8.2 Run `./gradlew check` for affected modules (`:lib-core`, `:lib-core-editor`, `:plugin-abyssus`, `:app-game-control-line`) and fix failures.
- [ ] 8.3 Run `scripts/check-docs.sh` and update any doc the change makes wrong.
