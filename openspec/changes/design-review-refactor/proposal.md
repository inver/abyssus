# Proposal

## Why

The design review in `docs/reviews/design-review-2026-10.md` found one visible bug and several structural problems
that make the next feature harder to build.

The bug: a scene's entity components are interpreted in three separate places, which already disagree on defaults. A
light that omits `intensity` is drawn at 0.3 but shown and edited as 1 in the properties panel. A light color with a
missing channel is drawn with that channel at 1 but shown as 0.

The structural problems:
- `SceneRenderer` does far more than draw.
- The writer every edit goes through also refreshes the tree pane.
- Services are looked up inside classes instead of being passed in.
- `runCatching` is used where the project requires `runCatchingKeepingCancellation`.
- Small helpers are copied in about fourteen places.
- The scene is parsed on every keystroke and on every action-availability check.

Fixing these now, before more scene-editing features land, keeps each later change small.

## What Changes

- **Light defaults (behavior fix).** The scene view, the properties panel and edits use one set of defaults for
  missing values: intensity 1, a missing `color` object white, a missing channel inside `color` 0, `range` 100,
  `coneAngle` 45, `edgeSoftness` 0.2. **Visible change:** lights that omit `intensity`, or a color channel, render
  differently than today. Files are not touched.
- **One reading of the entity format.** The scene view reads entity components through the same component codecs the
  panel uses, so the view and the panel always show the same values.
- **Gizmo and Drop writes:** same keys, same number text and same undo behavior as today. Only the code path moves
  onto the shared entity helpers.
- **Live update from the text editor.** The scene view follows unsaved edits in the `.scene` / `.abss` text tabs after
  a short pause instead of on every keystroke. Edits made through the view, panel or tree still update the view at once.
- **Add Light availability.** Add Light (tree and toolbar) checks a cached parse of the scene instead of parsing the
  file twice each time the IDE asks whether the action is enabled. What is enabled when does not change.
- **No behavior change (refactor):**
  - Split `SceneRenderer` into view state, CPU-only scene queries and GL drawing.
  - Move `editSceneJson` out of `EnabledToggle.kt` and stop it from refreshing the pane directly.
  - Pass collaborators through constructors instead of looking up services inside classes.
  - Replace `runCatching` with `runCatchingKeepingCancellation`, plus a build check that keeps it out.
  - One `meta.json` reader, using `MetaType` instead of type strings.
  - Shared helpers for duplicated code:
    - texture upload queue, fullscreen triangle and sky matrix (in `core`)
    - entity paths, entity display name and skybox face keys
    - tree action base class and model/terrain entity holders
    - one `runCatchingKeepingCancellation`
    - one Jackson mapper and pretty-printer setup
  - `AssetCache` takes an `AssetLoader` directly.
  - `SkyLoader` dispatches without repeated `when` blocks.
- **Docs:** update `docs/ai/architecture.md`, `docs/ai/conventions.md`, `docs/ai/file-formats.md` (light defaults) and
  `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md`. Fix the three broken paths `scripts/check-docs.sh` reports.

**Mundus fields read or written:**
- **Read:** `ecs.entities.<id>.components` — `PositionComponent` (`localPosition`, `localRotation`, `localScale`,
  `lookAtId`), `CameraComponent.camera`, `LightComponent` (flat or under `light`), `TypeComponent.type`,
  `NameComponent.name`, `RenderComponent.renderable.asset`; `skyboxName` / `skyboxEnabled`; asset `meta.json` `type`,
  `uuid` and `additional`.
- **Written:** the same keys as today, through the same edits.

**The file format does not change:** no new keys, no renamed or reordered keys, and defaults stay omitted.

### Out of scope

- Changing the number text that gizmo drags and Drop write (`FloatNode`, for example `1.0`), even though component
  edits write whole numbers as `1`. Unifying them would change bytes in users' files; it needs a separate decision
  checked against what the Mundus editor writes.
- Using the Ashley systems (`ecs/system/`, `ecs/render/`) to drive the scene view, or deleting them. Only the reading
  side of the entity format moves onto the codecs.
- Rendering changes beyond the light defaults (shadows, HDR, fog, picking accuracy).
- Any change to `gdx-model`.

## Capabilities

### New Capabilities
- `abyssus-scene-entity-values`: how a scene's entity component values, including omitted defaults, are interpreted
  the same way by the scene view, the properties panel and edits. Also covers what an edit leaves unchanged in the file.
- `abyssus-scene-view-updates`: when the scene view picks up changes to the scene and project files (unsaved text
  edits, edits made through the plugin, changes on disk), and how responsive scene actions' availability stays.

### Modified Capabilities

None. `openspec/specs/` has no main specs yet. The open change `show-project-assets` (`abyssus-project-assets`) must
keep its behavior when the `meta.json` readers are merged. Its scenarios are used as regression checks, and its delta
spec does not change.

## Impact

- **Plugin, `sceneview/`:**
  - `SceneRenderer`, `SceneInteraction` and `SceneViewPanel`
  - `SceneContent` and `SceneTransformWriter`
  - `SceneModels` and `SceneTerrains`
  - `SceneFileEditor` and `SceneRenderParams`
- **Plugin, `projectView/`:**
  - `EnabledToggle` (`editSceneJson` moves out)
  - `ComponentActions`, `AddLightAction`, `SceneComponentEdits`, `SkyboxChoices` and `DtoTree`
- **Plugin, other packages:**
  - `dto/`: `ProjectReader`, `SceneReader`, `ConfigFileReader`, `ProjectAssetListing`, `ProjectLayout` and
    `Cancellation.kt` (removed)
  - `properties/`: `PanelState` and `AssetMeta`
  - `ecs/`: `ComponentCodecs`, `LightComponent`, `ComponentEditor` and `EcsConfigurator`
  - `filetype/SceneJson.kt`
- **`core`:**
  - `AssetCache`, `SceneAssets` and `SkyLoader`
  - `ModelLoader` and `TerrainLoader`
  - `SkyboxCube`, `ProceduralSky`, `HdrSky` and `HdrEnvironmentBuild`
  - `AssetFiles` and `JsonProcessor`
  - new shared helpers
- **Build:** a `checkNoRunCatching` task wired into `check`, like `checkNoSingletons`.
- **Tests:**
  - Existing tests keep passing, except for any that assert the old light defaults on the view side.
  - New tests: view/panel parity on the Untitled fixture, the edit-cache behavior and the debounce.
- **No new dependencies.**
