## 1. lib-core: scene field and model key

- [ ] 1.1 Add `var meshId: String? = null` to `RenderComponent` (`projects/lib-core/.../ecs/component/RenderComponent.kt`) and read/write it as `renderable.asset.meshId` in the ECS loader/serializer; test that load and write keep it, omit it when null, and ignore it on a `TERRAIN`.
- [ ] 1.2 Add `ModelKey(name, meshId?)` with `encode()`/`decode()` in lib-core (no `object`/`companion`); test the round trip for ids containing `#`, `/` and spaces and that `meshId = null` encodes to the bare name.

## 2. lib-core: filtered model build

- [ ] 2.1 `AssetStorage` decodes a `ModelKey` in one private `resolveBase(name)` used by every name-to-folder/meta path (first list the `loadBaseMeta`/`prepare(` call sites and record them in design.md); test that `getAs<Model>` for a key returns a model built from the base asset's `meta.json`.
- [ ] 2.2 `ModelLoader` filters `PreparedModel.data` for a variant: keep the mesh, its parts' materials, prune node parts, keep the node tree, decode/upload only the kept materials' textures; headless test on a two-mesh fixture that the built model holds only the selected mesh and its materials.
- [ ] 2.3 Unknown `meshId` fails only that variant (logged once with the model's current mesh ids, no whole-model fallback); test that the other variants and the base model still load.
- [ ] 2.4a Log each variant load's duration at debug level.
- [ ] 2.4 `meshId` absent keeps the whole-model path byte-for-byte; existing model loader tests pass unchanged.
- [ ] 2.5 Animations and a skinned fixture: test that a filtered variant keeps node transforms and its animations play, and that bone nodes are not dropped (build the fixture with `GltfWriter` if none exists).
- [ ] 2.6 `invalidate(name)` also replaces every loaded variant of `name`; test with a variant loaded before the invalidation.

## 3. lib-core-editor: document, placement, listing

- [ ] 3.1 `RenderAsset` and `renderAssetOf` read `meshId` (nested and legacy flat shapes); round-trip test through scene JSON including formatting and number text.
- [ ] 3.2 `BuiltInComponentKinds`: add an optional `meshId` field to `RenderComponent` whose choices come from a provider (the current `ComponentField.choices` is static); clearing it removes the key.
- [ ] 3.3 `AssetPlacement.meshId`, `PlacementMapper` and `PlacedEntities`/`ModelEntity` carry it; unit tests for each mapping.
- [ ] 3.4 `HeadlessEditing`: place-asset accepts an optional `meshId`; setting or clearing `meshId` on an entity works without the IDE; test via the headless API.
- [ ] 3.5 `ModelMeshLister`: lists mesh ids and material names of a model asset with `ModelLoader(decodeTextures = false)`, cached per asset and dropped on invalidation; no platform imports (`NoPlatformClasspathTest`), no singletons.
- [ ] 3.6 Ray tracing: `RaySceneSnapshot` uses the variant model; test that a `meshId` entity's bounds match only that mesh and that `rayTracingMaterials` entries for dropped materials are preserved but ignored.

## 4. Plugin

- [ ] 4.1 `SceneModels`/`ModelEntity` request and cache per `ModelKey` through `ViewAssets`; test that two entities with different meshes of one asset get different models and an unknown id skips only that entity.
- [ ] 4.2 `SceneRenderer`, picking and shadows need no change; verify with `runIde` that an entity naming `meshId` draws, picks and casts a shadow for only that mesh.
- [ ] 4.3 Properties panel: mesh chooser (lists meshes from `ModelMeshLister` off the EDT, a "whole model" entry, shows a stored unknown id in a warning state); choosing writes through `editSceneJson` as one undoable edit and the view redraws on the next frame.
- [ ] 4.4 Add Asset (toolbar and tree): a model with more than one mesh gets a mesh submenu plus a whole-model entry, a single-mesh model only the whole-model entry; chosen `meshId` is recorded on the new entity; one undoable edit.
- [ ] 4.5 User-facing strings in `AbyssusBundle.properties`; text produced by lib-core-editor in `AbyssusEditorBundle.properties`.

## 5. Game runtime

- [ ] 5.1 `FieldRenderer` (`projects/app-game-control-line/.../render/FieldRenderer.kt`) builds a `ModelKey` for its preload check (`field.models`) and draw lookup; test that a `meshId`-placed model draws only that mesh. Confirm `checkNoEditorCode` and `checkShippedClasspath` still pass.

## 6. Docs and verification

- [ ] 6.1 Update `docs/ai/file-formats.md` (`renderable.asset.meshId`), `docs/ai/glossary.md` (model variant, mesh id) and the READMEs of `lib-core`, `lib-core-editor` and the sceneview package where they describe model loading; run `scripts/check-docs.sh`.
- [ ] 6.2 Run `./gradlew :lib-core:check :lib-core-editor:check :plugin-abyssus:test :app-game-control-line:check checkPackageCycles` and fix failures.
- [ ] 6.3 Run `openspec validate add-scene-model-mesh-selection --strict`.
