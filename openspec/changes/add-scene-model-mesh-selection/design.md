## Context

See proposal.md for motivation. The current state: `RenderComponent` (lib-core) holds `assetType`, `assetName`, `shaderKey`.
A model asset's `ModelData` (lib-gdx) holds many `ModelMesh`es; each `ModelMeshPart` carries a `materialId`. Assets are
built and cached by `AssetStorage` keyed by asset folder name only (`builtMap[name]`, `getAs<Model>(name)`). The editor
draws models through `SceneModels`/`ModelEntity`/`SceneRenderer`; the game draws through `FieldRenderer`, both calling
`assetStorage.getAs<Model>(name)`. Ray tracing snapshots the same built `Model` via `RaySceneSnapshot`.

Module constraints: lib-core and lib-gdx are plain JVM libraries with no object/companion with behavior and no
IntelliJ/Swing/AWT imports; lib-core-editor likewise has no platform imports. Scene document edits go through
`editSceneJson` (one undoable command preserving formatting and number text).

## Goals / Non-Goals

**Goals:**
- Let a model entity's render component name a `meshId` so the editor and game draw only that mesh (with its material)
  of a multi-mesh model asset, while `meshId` absent keeps drawing the whole model.
- Cache the built, filtered `Model` per `(asset, meshId)` so entities sharing a selection reuse GPU resources.
- Keep ray tracing, shadows, picking and bounds consistent with the drawn mesh.

**Non-Goals:**
- Splitting a model into per-mesh asset folders at import time.
- Writing a trimmed glTF subset at import time.
- Morph targets, cameras, lights, animations per mesh (a mesh keeps its existing animations).

## Decisions

**1. `meshId` lives on `RenderComponent` (lib-core `RenderComponent.kt`).**
It is an optional `String?`. When absent, the whole model is drawn (unchanged behavior). When present, only the
mesh whose id matches is drawn, using that mesh's material.
- Alternative: a separate component. Rejected — mesh selection is a property of how a renderable is shown, so it
  belongs on `RenderComponent`.

**2. Filtering happens at model build, in `ModelLoader` (lib-core), on the prepare thread.**
`ModelLoader.read`/`build` filters `PreparedModel.data` meshes to the selected `meshId` and keeps only that mesh's
material before `IntModel.buildIntModel` builds GPU resources. Everything downstream (drawing, picking, bounds,
animation, ray snapshot) works unchanged because the built `Model` already contains only the selected mesh.
- Alternative: filter at draw time per entity. Rejected — the shared `Model`/`ModelInstance` would still carry all
  meshes and ray tracing/picking would see the wrong bounds.

**3. Cache key is the composite `asset#meshId`, threaded through callers.**
`AssetStorage` is keyed by `String` name only, so callers (`SceneModels`, `FieldRenderer`) request and read models
under `name#meshId`. `ModelLoader` strips the `#meshId` suffix to locate the real asset, then filters `ModelData` by
meshId+material at build. `meshId` absent → key is just `name` (current behavior).
- Risk: `invalidate(names)` by bare asset name won't drop mesh-specific variants. Mitigation: invalidate the composite
  keys too when a model asset changes (task to enumerate and drop `name#*` variants), or accept a stale mesh variant
  until the next explicit reload.

**4. `meshId` is read/written through `SceneDocument`/`RenderAsset` and `PlacementMapper`.**
`RenderAsset` gains `meshId`. `PlacementMapper` maps `RenderComponent.meshId` → `AssetPlacement.meshId`. Scene JSON
edits use `editSceneJson` so formatting and number text are preserved.

**5. UI chooser lists the model's meshes.**
The properties panel shows, for a model entity, a mesh chooser populated from the model asset's mesh ids (read off
the EDT via the model's `ModelData`). Runs off the EDT; GL-free.

## Risks / Trade-offs

[Composite key parsing is implicit (`name#meshId`)] → A mesh id containing `#` would collide; require `meshId` to not
contain `#`, or escape it. Mesh ids from glTF node/mesh names rarely contain `#`.

[Invalidation by bare name misses mesh variants] → When a model asset is edited, only `name` is invalidated, so a
`name#meshId` variant may stay stale. Mitigate in a follow-up task by enumerating and dropping `name#*` variants, or
document that the user must reload the scene.

[Memory: one filtered Model per (asset, meshId)] → An asset with N meshes used by entities picking different meshes
builds N GPU models. Acceptable; mesh selection is intended to be sparse.

[Editor/game must agree on meshId] → Both `SceneModels` and `FieldRenderer` read `meshId` from the same scene JSON
`RenderComponent`, so they use identical selections.

[`lib-core` stays singleton-free] → `ModelLoader` and `AssetStorage` remain constructor-wired; no `object` is added.

## Migration Plan

No data migration: `meshId` is optional and absent means the existing whole-model behavior. Existing scenes and assets
are unchanged. No `formatVersion` bump (the field is optional and unknown keys are preserved).

## Open Questions

None. The composite-key invalidation gap is a known risk, not a spec-blocking unknown; it is tracked as a risk above
and will be resolved in an implementation task.
