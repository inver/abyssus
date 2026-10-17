## Context

See proposal.md for motivation. The current state: `RenderComponent` (lib-core) holds `type`, `assetName`, `shaderKey`.
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

**1. `meshId` lives on the render component, next to `assetName` (JSON: `renderable.asset.meshId`).**
It is an optional non-empty string naming a `ModelMesh.id` of the model asset (unique per model; `ModelData.addMesh`
rejects duplicates). `RenderComponent` (lib-core) gains `var meshId: String? = null`. The native contract places asset
fields under `renderable.asset` (`type`, `assetName`), so `meshId` goes there; `renderAssetOf` already reads the flat
legacy shape too and reads `meshId` from the same node as `assetName`. Absent means the whole model, unchanged.
`meshId` applies only to `MODEL` renders; on a `TERRAIN` it is ignored and preserved.
- Alternative: a separate component. Rejected: mesh selection is part of how a renderable is shown.

**2. A selection is a model variant, identified by a typed key, filtered at build.**
`AssetStorage` is keyed by a `String` and `AssetMetaLoader` resolves that string to an asset folder, so a bare
`name#meshId` string would fail to find the `meta.json`. Variant handling is therefore confined to one place:
a small `ModelKey` value class in lib-core (`name`, `meshId?`) with `encode()` / `decode()`, used by the loader and by
`AssetStorage`'s name-to-folder step. Callers (`SceneModels`, `FieldRenderer`, ray snapshot) build the key through
`ModelKey`, never by string concatenation. Decoding happens in one private `resolveBase(name)` inside `AssetStorage`; every path that turns a name into a folder or
`meta.json` (`request`, dependency lookup, the loader's `prepare(name)`) goes through it, while `getAs` and `builtMap`
keep using the encoded key. The implementation lists the call sites of `loadBaseMeta` / `prepare(` first and records
them here before archive.
`ModelLoader.build` filters `PreparedModel.data` before `buildIntModel`:
- keep only the `ModelMesh` with the requested id; drop the others;
- keep only the `ModelMaterial`s referenced by that mesh's parts (a mesh may have several parts with different
  materials; all of them are kept);
- prune `ModelNodePart`s that reference dropped mesh parts or materials; keep the node tree itself, so node transforms
  (which place the mesh inside the model) and animations that target nodes still work;
- decode and upload only the textures the kept materials use (this also bounds the cost of variants).
Everything downstream (drawing, picking, bounds, shadows, animation, ray snapshot) works unchanged because the built
`Model` already holds only the selected mesh.
- Alternative: filter at draw time per entity. Rejected: the shared `Model` would still carry all meshes and picking,
  bounds and ray tracing would see the wrong geometry.

**3. An unknown `meshId` fails that variant only.**
When the model has no mesh with the id, the variant load fails the same way a corrupt model does: logged once, entity
skipped, other entities unaffected, no dialog. The view never falls back to the whole model, so a typo is visible
rather than silently drawing everything. The Properties chooser keeps showing the stored id so it can be corrected.

**4. Invalidation covers variants inside `AssetStorage`.**
`invalidate(names)` expands each model name to the base asset plus every cached variant of it (`ModelKey` match), so a
changed model file replaces all of its variants. This is part of the design, not a follow-up: a stale variant after a
model re-import would otherwise show geometry the asset no longer has. Eviction of variants no entity uses any more
follows whatever the existing unused-asset policy is; if there is none, variants live until the storage is disposed
(bounded by the number of meshes of the placed models).

**5. The editor reads and writes `meshId` through the existing document layer.**
`renderAssetOf` / `RenderAsset` gain `meshId`; `PlacementMapper` and `AssetPlacement` carry it; `PlacedEntities` and
`ModelEntity` expose it. Writes go through `editSceneJson` (one undoable command, formatting and number text kept) and
through the headless API (`HeadlessEditing`), so a place-asset call can pass a `meshId` and a script can set or clear
it. Clearing removes the key (default omission). `RenderComponent.rayTracingMaterials` is keyed by material id: after
selecting a mesh, entries for materials the mesh does not use stay in the file but are unresolved (existing
"unresolved IDs are preserved" rule), and ray conversion ignores them for that entity.

**6. The chooser lists meshes read from the model data, off the EDT and GL-free.**
A `ModelMeshLister` in lib-core-editor returns `ModelMesh` ids (and each mesh's material names) for a model asset by
reading the geometry with `ModelLoader(decodeTextures = false)`, the existing reader-only mode, so no libGDX natives or
GL are needed. The result is cached per asset and dropped when the asset is invalidated. The Properties `meshId` field
is a dynamic choice (the current `ComponentField.choices` is static, so the field needs a choices provider or a
dedicated editor); the Add Asset menu shows a mesh submenu for a model that has more than one mesh, plus a whole-model
entry; a model with a single mesh offers only the whole-model entry.

**7. Any non-empty mesh id is valid.**
Because the key is a typed `ModelKey` rather than a parsed string, any non-empty mesh id is valid. If `ModelKey`
encodes to a string for `AssetStorage`, it escapes its separator, and a round-trip test covers ids with `#`, `/` and
spaces.

## Risks / Trade-offs

[Each variant re-reads and re-parses the model file] N variants of one asset parse the file N times and build N GPU
models. Mitigation: textures are limited to the kept materials; mesh selection is expected to be sparse. Each variant load logs its time at debug level. A shared,
reference-counted `ModelData` cache per asset is added only if a realistic scene (about 5 meshes of one model) takes
more than a second to open.

[Mesh ids are Assimp/glTF-derived] Ids come from the importer and can change if the model file is re-exported or the
importer changes naming. A changed id makes the entity skip (decision 3); the chooser shows the stored id with a warning state, and the log line
lists the model's current mesh ids. There is no automatic re-targeting: guessing a mesh is worse than skipping. Document
that `meshId` follows the model file.

[Skinned meshes] The whole node tree is kept; only node parts are pruned, and bone nodes or part-less nodes are never
removed. A skinned fixture (built with `GltfWriter` in lib-gdx if the repo has none) confirms it. Only if that test
fails, also keep every node referenced by a kept part's bone map.

[Invalidation scope] Decision 4 puts variant expansion in `AssetStorage`; it must be covered by a test with a variant
loaded before the invalidation.

[Editor/game agreement] Both hosts decode `meshId` from the same scene JSON through the same lib-core `RenderComponent`
and `ModelKey`, so they select the same mesh.

[Module rules] `ModelKey`, `ModelLoader` and `AssetStorage` stay constructor-wired with no `object` /
`companion object` in lib-core (`checkNoSingletons`); `ModelMeshLister` in lib-core-editor has no platform imports; the
game classpath gains no editor module (`checkNoEditorCode`, `checkShippedClasspath`).

## Migration Plan

No data migration: `meshId` is optional and absent means the existing whole-model behavior. Existing scenes and assets
are unchanged. No `formatVersion` bump (the field is optional; older readers that do not know it keep the key and draw
the whole model). `docs/ai/file-formats.md` documents the field.

## Open Questions

None blocking. The `AssetStorage` call sites that reach `resolveBase` are recorded in this design before archive.
