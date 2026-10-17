## Why

A glTF/GLB model asset can hold several meshes, each with its own material, but Abyssus treats a model as an atomic
unit. When an entity references a model asset, the whole model is drawn with all of its meshes and materials, and
there is no way to select one concrete mesh with its material. This limits what a single model asset can represent
and forces authors to split content into many asset folders to show different parts independently.

## What Changes

- Add an optional `meshId` field to a model entity's `RenderComponent` in the scene JSON, naming the concrete mesh
  (with its material) of the model asset to use; when absent the whole model is drawn as before.
- Filter a model's `ModelData` by `meshId` + material at build time so the built `Model` contains only the selected
  mesh and its material; cache the built model per `(asset, meshId)`.
- Thread `meshId` through the editor scene view (`SceneModels`/`ModelEntity`/`SceneRenderer`) and the game runtime
  (`FieldRenderer`) so both draw the selected mesh.
- Add a mesh/material chooser to the model entity's properties panel.
- Read/write `meshId` through `editSceneJson` (one undoable command, preserving formatting and number text).

## Capabilities

### Modified Capabilities
- `scene-model-rendering`: a placed model SHALL be able to draw a single concrete mesh with its material when the
  entity's render component names a `meshId`; otherwise the whole model is drawn as before.
- `scene-asset-placement`: Add Asset SHALL offer to place a model either whole or by a chosen mesh, recording the
  chosen `meshId` on the new entity's render component.

## Impact

- lib-core: `RenderComponent` gains `meshId`; `ModelLoader` filters `ModelData` by meshId+material at build.
- lib-core-editor: `BuiltInComponentKinds` (RenderComponent editor field), `SceneDocument`/`RenderAsset`, `AssetPlacement`,
  `PlacementMapper`, `PlacedEntities`, `RaySceneSnapshot`.
- plugin: `SceneModels`, `ModelEntity`, `SceneRenderer`, properties panel mesh chooser.
- game (`app-game-control-line`): `FieldRenderer` requests models per (asset, meshId).
- Out of scope: importing/splitting a model into per-mesh assets at import time; writing a trimmed glTF subset;
  morph targets, cameras, lights.
