## Why

A glTF/GLB model asset can hold several meshes, each with its own material, but Abyssus treats a model as an atomic
unit. When an entity references a model asset, the whole model is drawn with all of its meshes and materials, and
there is no way to select one concrete mesh with its material. This limits what a single model asset can represent
and forces authors to split content into many asset folders to show different parts independently.

## What Changes

- Add an optional `meshId` field to a model entity's `RenderComponent` in the scene JSON (beside `assetName`: `renderable.asset.meshId` in the nested shape, `meshId` in the flat shape), naming
  the concrete mesh (with its materials) of the model asset to use; when absent the whole model is drawn as before.
- Filter a model's `ModelData` at build time (mesh, its materials, node parts, textures) so the built `Model` contains
  only the selected mesh; identify and cache each variant by a typed `ModelKey(asset, meshId)`.
- Thread `meshId` through the editor scene view (`SceneModels`/`ModelEntity`/`SceneRenderer`) and the game runtime
  (`FieldRenderer`) so both draw the selected mesh.
- Add a mesh chooser to the model entity's Properties panel and a mesh choice to Add Asset, fed by a GL-free mesh lister.
- Read/write `meshId` through `editSceneJson` and the headless editing API (one undoable command, preserving formatting and number text).
- Invalidate (in `AssetStorage`, `ViewAssets` and the ray scene assets) and retain a model's variants together with the model; document the field in `docs/ai/file-formats.md`.

## Capabilities

### Modified Capabilities
(delta specs: `scene-model-rendering` and `scene-asset-placement` modify requirements; `scene-ecs-components` and
`scene-component-editing` add requirements)
- `scene-model-rendering`: a placed model SHALL be able to draw a single concrete mesh with its material when the
  entity's render component names a `meshId`; otherwise the whole model is drawn as before.
- `scene-asset-placement`: Add Asset SHALL offer to place a model either whole or by a chosen mesh, recording the
  chosen `meshId` on the new entity's render component.
- `scene-ecs-components`: the render component SHALL accept an optional `meshId` and an unknown one skips the entity.
- `scene-component-editing`: the Properties panel SHALL offer a mesh chooser for a model render component.

## Impact

- lib-core: `RenderComponent` gains `meshId`; new `ModelKey`; `ModelLoader` filters `ModelData` at build; `AssetStorage` decodes the key and invalidates variants.
- lib-core-editor: `BuiltInComponentKinds` (RenderComponent editor field), `SceneDocument`/`RenderAsset`, `AssetPlacement`,
  `PlacementMapper`, `PlacedEntities`, `RaySceneSnapshot`, `HeadlessEditing`, new `ModelMeshLister`.
- plugin: `SceneModels`, `ModelEntity`, `SceneRenderer`, Properties mesh chooser, Add Asset mesh submenu.
- game (`app-game-control-line`): `FieldRenderer` requests models per `ModelKey` (its preload check and draw lookup).
- Out of scope: importing/splitting a model into per-mesh assets at import time; writing a trimmed glTF subset;
  morph targets, cameras, lights.
