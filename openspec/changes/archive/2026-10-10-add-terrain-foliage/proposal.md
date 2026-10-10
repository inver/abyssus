# Proposal

## Why

A scene can only hold hand-placed entities today: one entity, one model. Forests, grass, rocks and flowers need
thousands to hundreds of thousands of copies spread over a terrain. That is too many to place by hand, and too many to
keep as scene entities: the scene JSON, undo and the per-entity drawing in the scene view all break down. Terrains can
already be generated reproducibly. The next authoring gap is covering a terrain with vegetation, so that the field in
Control Line, or any other game, looks like a landscape.

## What Changes

- **New `FOLIAGE` asset type.** A foliage asset belongs to one terrain asset. It holds **layers**. Each layer scatters
  one or more model assets, with weights, at a density shaped by rules (height range, maximum slope) and by a painted
  density mask. A layer is one of two kinds:
  - **OBJECT** (trees, rocks): a few thousand copies; they cast shadows.
  - **DETAIL** (grass, flowers): up to hundreds of thousands of copies; they are drawn only within a draw distance and
    cast no shadows.
- **New Foliage...** on a recognised project's Assets node. It is offered only when the project has a terrain: the user
  picks the terrain, a unique folder name and a mask resolution, and gets an empty foliage asset (no layers) as one
  undoable operation.
- **The copies follow from the settings.** They are generated deterministically from the layer settings, the masks and
  the terrain heights. They are baked into `foliage.data` so a reader needs no generator. The bake records a
  fingerprint of its inputs.
  - The view detects a stale bake, for example after the terrain was regenerated. It shows freshly generated copies
    and the panel offers **Re-bake**.
  - Copies always stand on the terrain's current surface, because their height is taken from the terrain when they are
    drawn.
- **Foliage properties panel.** It adds, removes and reorders layers, and edits their models, weights, density, scale
  range, normal alignment, height range, maximum slope and (DETAIL) draw distance. Edits preview in the open scene views
  at once. **Apply** writes the settings and the new bake as one undoable operation; Cancel or selecting something else
  discards the preview. The panel shows the copy count of each layer, and refuses an Apply above the copy limit.
- **Show foliage in a scene: Add Foliage...** on a terrain entity, in the tree and in the Scene view toolbar. It lists
  the foliage assets of that entity's terrain and adds `FoliageComponent` to the entity, so the copies follow the
  entity's position, rotation and scale. This is one undoable scene edit through `editSceneJson`.
- **Paint Foliage mode in the Scene view**, available when a terrain entity with foliage is selected:
  - A brush strip chooses the layer, radius, strength and Paint or Erase; holding Shift erases.
  - Dragging with the left button paints that layer's density mask under a circle drawn on the terrain. The other
    buttons and the wheel keep navigating, and Esc cancels the current stroke.
  - The copies update while the user paints.
  - Each stroke is one undoable operation, reachable from the Scene view and from the foliage panel.
- **Scene view drawing.** Foliage is drawn with GPU instancing, culled by chunk and, for DETAIL layers, by distance.
  OBJECT copies cast and receive shadows; DETAIL copies receive them only. Foliage that cannot be loaded is skipped and
  logged, like a broken terrain. Copies cannot be picked: a click passes through to the terrain. Animated models are
  drawn in their rest pose.
- **Unused-asset marking.** A used foliage asset counts its terrain and its layers' models as used. A foliage asset is
  used when a scene's `FoliageComponent` names it.
- **Docs.** `AGENTS.md`, `docs/ai/*.md` and the module READMEs still name the old module paths (`editor-core/`,
  `core/`, `src/main/kotlin/...`). They are corrected to `projects/lib-*` / `projects/plugin-*` together with the
  foliage documentation.

Native fields:
- **Foliage `meta.json`** (a native document, validated with `AbyssusDocumentFormat` before reading or editing):
  - `format: "abyssus"`, integral `formatVersion: 1`, `version: 1`, `lastModified`, `uuid`, `type: "FOLIAGE"`.
  - `additional.terrain`: the terrain folder name.
  - `additional.dataFile: "foliage.data"`.
  - `additional.maskResolution`.
  - `additional.layers[]`, one object per layer:
    - `id`, `kind`: `OBJECT` or `DETAIL`;
    - `models[]` of `{ asset, weight }`;
    - `density` (copies per square unit at full mask), `scale` (`min`, `max`), `alignToNormal` (0 to 1);
    - optional `minHeight`, `maxHeight` and `maxSlope` (degrees);
    - `drawDistance` (DETAIL only), `seed`.

  Defaults are omitted, edits keep the text, key order and number spelling of untouched values, and unknown members
  are kept.
- **`layer-<id>.mask`**: one byte per cell (0 to 255), `maskResolution` squared, row after row (z-major) over the
  terrain's square, like `terrain.data`. A missing mask means full density.
- **`foliage.data`**: a binary cache with a magic and version header, the input fingerprint and the chunk size, then the
  copies of each layer by chunk. A copy is a model index, terrain-local x and z, yaw and scale. This file has its own
  version number. It is not a native JSON document, and the native `formatVersion` does not change.
- **The scene gains `ecs.<id>.components.FoliageComponent: { "assetName": "<foliage folder>" }`** on a terrain entity.
  It is written through `editSceneJson`. Until the runtime registers this component (follow-up change), it is extension
  data that round-trips unchanged.
- **No format version change for existing documents.** The `.abss` is never written.

Out of scope (follow-up changes):
- `add-foliage-runtime`: a `FoliageComponent` codec and drawing in `lib-runtime` and Control Line, and static physics
  colliders for OBJECT layers.
- Ray-traced foliage: the ray tracing preview leaves foliage out and says so.
- Selecting, moving, pinning or deleting single copies; an exclusion list.
- Wind and other vertex animation, impostors / LOD models, and a dithered distance fade (DETAIL copies end at a hard
  cutoff).
- Importing or exporting masks as images, using terrain splat channels as masks, and smoothing or noise brushes.
- Foliage on anything but a terrain, and foliage that spans several terrains.
- Renaming a foliage, terrain or model folder and following that rename in references.

## Capabilities

### New Capabilities
- `terrain-foliage-authoring`: foliage assets bound to a terrain. Covers creating one, its layer settings and rules, deterministic generation and baking, stale-bake detection and Re-bake, the properties panel with preview and Apply, the copy limit, adding foliage to a terrain entity, and undo.
- `terrain-foliage-painting`: the Scene view's Paint Foliage mode. Covers brush settings, painting and erasing a layer's density mask on the terrain, live updates while painting, cancelling a stroke, and one undoable operation per stroke.
- `scene-foliage-rendering`: showing a terrain entity's foliage in the scene view. Covers placement on the terrain surface, entity transform, draw distance, following edits, failure isolation, picking pass-through, and leaving foliage out of ray tracing.

### Modified Capabilities
- `abyssus-project-assets`: unused-asset marking also follows foliage. A scene uses a foliage asset through `FoliageComponent.assetName`, and a used foliage asset uses its terrain and its layers' models.
- `abyssus-project-view`: foliage assets get their own icon among the asset node kinds.
- `scene-shadows`: OBJECT-layer foliage copies cast and receive shadows; DETAIL-layer copies only receive them.

## Impact

- **`lib-core`**:
  - `MetaType.FOLIAGE` and the foliage meta binding.
  - Readers for `foliage.data` and the masks.
  - A foliage loader in the asset pipeline, which depends on the terrain and model assets.
  - The instanced foliage drawable, reused later by the runtime.
- **`lib-gdx-model`**: instanced variants of the default, PBR and depth shaders. `Mesh` already supports instanced
  rendering.
- **`lib-core-editor`**:
  - The scatter generator and the fingerprint.
  - The foliage settings model and validation.
  - Mask painting math.
  - Planning the files for New Foliage, Apply, Re-bake and a stroke.
  - The meta text edits through `DocumentTextEditor`.
  - Reading `FoliageComponent` in `SceneContent`.
  - Messages in `AbyssusEditorBundle`.
- **`plugin-abyssus`**:
  - The New Foliage and Add Foliage actions.
  - The foliage properties panel.
  - The Scene view's paint mode, brush strip and brush cursor.
  - `SceneFoliage` drawing, with shadow and fog integration.
  - Undo through `AssetFileCommand`.
  - The icon, and messages in `AbyssusBundle`.
- **Tests**: new tests in `lib-core-editor`, `lib-core` and `plugin-abyssus`. A foliage fixture is added to a copy-safe
  test project. The `Untitled` fixture is used read-only in scenarios.
- **Not touched**: `lib-runtime`, `lib-physics`, `plugin-abyssus-physics`, `lib-raytracing` and Control Line.
