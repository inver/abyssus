# Design

## Context

See `proposal.md` for the motivation and `docs/reviews/design-review-2026-10.md` for the findings (H1–H3, M1–M9,
D1–D14, L1–L9), which this design refers to by id.

Constraints that shape the approach:
- **Writes:** every scene write goes through `editSceneJson` (one undoable command, file style kept). This change adds
  no write path.
- **Threading:** libGDX runs only inside `GdxRuntime.withContext` on the AWT thread that renders the canvas.
  Picking, the Drop query and gizmo hit tests are CPU-only and run on the EDT.
- **`core`:** no `object` / `companion object`, and no IntelliJ imports. Collaborators are passed through constructors.
- **Open change `show-project-assets`:** `ProjectAssetListing` must keep listing an unreadable or unknown `meta.json` as
  `MetaType.UNKNOWN` with no references, and must keep the unused rule.
- **Other open changes:** `add-realistic-water`, `add-scene-raytracing` and `add-asset-editing-and-terrain-generation`
  plan code in `SceneRenderer`, `SceneContent`, `SceneFileEditor`, `editSceneJson`, `AssetCache` and `AssetFiles`. None
  of their delta specs touch the capabilities this change modifies, but their code will conflict with phases 5–8.
  Whichever change lands second rebases onto the new structure.
- **Existing tests** pin today's behavior: `SceneContentTest`, `ComponentCodecsTest`, `SceneFileEditorTest`,
  `SceneComponentEditsTest`, `SceneInteraction*` tests and `core` tests. They are the safety net for the refactors.

## Goals / Non-Goals

**Goals:**
- One set of component defaults, and one decoder of entity components, shared by the view, the panel and edits.
- Classes with a single responsibility in the scene view, with the state they share made explicit.
- Collaborators passed in rather than looked up, so the plugin classes touched here can be tested without the IDE.
- Remove the duplicates listed as D1–D14 without changing behavior.

**Non-Goals:**
- Unifying the number text of transform writes with that of component writes (see Open Questions).
- Driving the view through Ashley systems (L1 stays open: those classes are untouched).
- Changing what any write puts in the file.

## Decisions

### D-1. Component defaults live with the components (H1)
Add `ecs/component/ComponentDefaults.kt` (top-level `const val`s: `LIGHT_INTENSITY = 1f`, `LIGHT_RANGE = 100f`,
`LIGHT_CONE_ANGLE = 45f`, `LIGHT_EDGE_SOFTNESS = 0.2f`, plus the camera defaults that now live in `SceneContent.kt`).
The following all read from it:
- `LightData` and `CameraComponent`
- `LightCodec.write`, which today hard-codes `100f` / `45f` / `0.2f`
- the render-side placements
`DEFAULT_LIGHT_RANGE` and `DEFAULT_CAMERA_*` become aliases, then are removed once their callers move.

The panel's defaults win, as the user decided: a missing `intensity` reads as 1, a missing `color` object as white,
and a missing channel inside `color` as 0 (`readColor` today).
*Alternative:* the view's defaults (0.3 / 1). Rejected by the user. It would also have contradicted
`ComponentCodecsTest`.

### D-2. `SceneContent` is built from the codecs (H1, D4, D5)
`SceneContent.of(scene)` now works in three steps:
1. Walk `ecs.entities` through a new `SceneEcsPaths` helper (`entities(root)`, `components(root, id)`), which
   replaces the copies in `ComponentEditor`, `SceneTransformWriter`, `PanelState`, `LightEntities`, `AddLightAction`
   and `DtoTree`.
2. Decode the components each entity has through `ComponentCodecs` (with no resolver, as `ComponentEditor` does).
3. Map them to `AssetPlacement` / `LightPlacement` / `CameraPlacement` in a new pure class, `PlacementMapper`. It has
   no Swing, GL or platform code and is unit-tested.

Light kind detection (`TypeComponent` `LIGHT_*`, or a lone `LightComponent` → directional) and the camera fallbacks
(`camera.position` when the entity has no `PositionComponent`; `lookAtId` accepting text and integer ids) stay in
`PlacementMapper`, so `SceneContentTest` keeps passing.

- **`lookAtId`:** `PositionCodec` currently reads only integers. It gains the same text/integer reading, so the panel
  and the view agree. It writes back the form the file used, so the file format does not change.
- **Entity names:** `entityName(components, id)` in `SceneEcsPaths` replaces the three copies of the name lookup.

*Alternative:* keep the ad-hoc reader and only share the defaults. Rejected because the two readers would drift apart
again. A parity test (task 2.4) guards the shared one.

### D-3. `SceneTransformWriter` keeps its output, changes its plumbing (H1)
It uses `SceneEcsPaths` to find the components, and keeps its `setFields` diff and `FloatNode` output, so the file
output stays byte-identical (the `scene-object-transform` requirement that a write changes nothing else). Routing it through
`PositionCodec` + `number()` would change number text, so it is deferred.

### D-4. Split `SceneRenderer` (M1, M9)
New pieces, all on the EDT/AWT thread:
- **`SceneViewState`** — plain class: `selectedId`, `gizmoMode`, `hoveredAxis`, `viewCamera` and `preview`. Owned by
  `SceneViewPanel`, passed to the renderer and to the interaction. The `@Volatile` fields go: everything already runs
  on the AWT thread.
- **`FrameSnapshot`** — immutable data the renderer publishes after each frame: a camera copy, the drawn boxes
  (id, local bounds, world matrix), the terrain targets, and `drawnVersion`. It is built from data that already exists
  after `models.update` / `terrains.update`, so no extra GL work is needed.
- **`SceneQueries`** (interface) and **`SnapshotSceneQueries`** (implementation) — `pick`, `rayAt`, `groundBelow`,
  `lowestPoint`, `gizmoHandles`, `gizmoHit` and `beginDrag`, computed from `FrameSnapshot` + `SceneViewState` +
  `SceneContent`. CPU-only, no GL, unit-tested with hand-built snapshots. `targets()` is computed once per snapshot
  (fixing the double build in `canDrop`).
- **`SceneRenderer`** — GL only: it reads `SceneViewState` and publishes `FrameSnapshot`. The grid builder and the
  selection box move to `GridModel` / `SelectionBox` helpers in `sceneview/`.
- **`SceneInteraction`** — depends on `SceneViewState` and `SceneQueries`, not on the renderer. Drag state becomes
  `sealed interface Gesture { Idle; Dragging(drag, entityId, result); Cancelled }`. Pixel conversion moves into
  `ViewSize.toFramebuffer(x, y)`. `content` is read once per call.

`SceneMarkers` still supplies the marker boxes for the snapshot, and the test hooks (`drawnModels`, `drewGizmo`, …)
stay `internal` on the renderer.
*Alternative:* only extract `SceneViewState`. Rejected: picking would still need a live renderer, which is the main
obstacle to testing.

### D-5. Model and terrain holders share a base (D8, L3, L4)
An `open class PlacedAssets<P, A, E>` holds the `SceneAssets` and the `PlacedEntities` and provides `update`,
`abandon`, `dispose` and `isLoading`. `SceneModels` adds the animation step, and `TerrainEntity` caches its local
bounds (L4). `SceneSkybox` gains `abandon()`. `SceneRenderer.create()` abandons the old skybox, overlay, line batch and
terrain shader before replacing them (L3), mirroring models and terrains.

### D-6. Writing, refreshing and the scene document cache (M2, H3)
- `editSceneJson` moves to `filetype/SceneDocumentWriter.kt`, unchanged except for two things:
  - It uses `runCatchingKeepingCancellation`.
  - It publishes `AbyssusSceneEdited.TOPIC` (file) after the command, instead of calling the pane.
- `AbyssusProjectViewPane` subscribes to the topic and calls `updateFromRoot(true)`, which is today's behavior.
  `SceneFileEditor` subscribes too and reloads at once (spec: `scene-model-rendering` *Plugin edits are shown at once*).
- **`SceneDocumentCache`** (project service, EDT and read actions) maps `(file, document modificationStamp)` to the
  parsed `ObjectNode` and the bind result. Callers get a deep copy when they intend to change it. `canAddLight`,
  `AddComponentAction.choices` and `SceneComponentEdits.addLight`'s validation read from it (spec: `scene-light-creation`
  *Unchanged scene is not re-read*). Entries go when the file is deleted or moved, like `AssetReadCache`.
- **Undo:** an undo emits no `AbyssusSceneEdited`. It is a document change, which gets the same immediate reload
  because `SceneFileEditor` reloads at once when the change happens inside an undo/redo
  (`UndoManager.isUndoOrRedoInProgress`). Other document changes go through the debounce below.

*Alternative:* keep the direct pane call. Rejected because it ties the writer to one UI pane (DIP).

### D-7. Debounced reload in `SceneFileEditor` (H3)
A `MergingUpdateQueue` (named `abyssus-scene-reload`, 200 ms, EDT, disposed with the editor) collects document events
from the text tabs. VFS content changes and `AbyssusSceneEdited` cancel any queued update and reload immediately.
The pure part — "given these events, reload now / later / not at all" — goes into a `ReloadPolicy` class with unit
tests. Tests flush the queue (`flush()` / `waitForAllExecuted`) where they previously relied on synchronous reloads,
for example `testRendersAndUpdatesInPlaceOnUnsavedEdits`.

### D-8. Collaborators through constructors (M3)
- `SceneReader(json: JsonProcessor)` and `ProjectReader(project, json, sceneReader)`. The `@Service` constructors look
  up `AbyssusCore` once.
- `PanelState` functions take an `HdrPreviewSource` (L7), a small interface over `AssetLoading`'s HDR pieces.
- `SceneViewPanel` takes `SceneRenderer` from its caller (`SceneFileEditor`'s view factory, which runs in the editor
  provider), with no default `service<>()` call.
- `SkyboxChoices` and `SceneComponentEdits` take what they need as parameters.
- Services stay as lookups only in `AnAction`s, providers, tool window factories and `@Service` constructors.

### D-9. One `meta.json` reading (M4, D6, D7, D10)
- **`core`:** `AssetMetaReader(json)` parses once to `MetaDocument(type: MetaType, json: JsonNode)`, with
  `typed(clazz)` for `MetaBase<T>`. `AssetFiles` uses it, and caches per folder within one `AssetFiles` instance.
  `SKYBOX_FACES` moves to `core`, next to `SkyboxAdditional`.
- **Plugin:** `ProjectAssetListing`, `AssetMeta` and the skybox chooser read through a VFS adapter (`text` from
  `textOf`) into the same reader.
  - `AssetMeta.Loaded.type` becomes `MetaType`. Rows still come from the raw JSON, so the panel's table is unchanged.
  - `SKYBOX_TYPE` / `PROCEDURAL_SKY_TYPE` / `HDR_SKY_TYPE` and the `"MODEL"` / `"TERRAIN"` strings are replaced by
    `MetaType`.
- The `show-project-assets` scenarios (unknown/unreadable meta, references, unused rule) are kept, checked by
  `ProjectAssetsTest`.

### D-10. `core` loading simplifications (M6, M7, M8)
- **`AssetCache`:** takes `(executor, prepare: (String) -> P?, loader: AssetLoader<P, T>, log)`. The `build` name
  argument goes, and `SceneAssets` passes its loader straight through.
- **`SkyLoader`:** `PreparedSky` becomes `class PreparedSky<P>(val loader: AssetLoader<P, out Sky>, val prepared: P)`
  behind a star-projected helper, so `upload` / `build` / `discard` delegate in one line. Only `prepare` chooses by
  `MetaType`, using `AssetMetaReader` instead of binding `ProceduralSkyMeta` to read the type.
- **`TextureUploadQueue`:** `TextureUploadQueue(pixmaps, makeTexture: (String, Pixmap) -> Texture)` is shared by
  `PreparedModel` and `PreparedTerrain`. The terrain passes its splat/layer filter choice as `makeTexture`. The upload
  still runs on the GL thread inside `pump`, and the release still runs on any thread that `discard` already uses.
- **Sky drawing:** `FullscreenTriangle.create()` and `rotationOnlyViewProj(camera, out)` are top-level functions in
  `core/.../sky/SkyGeometry.kt`, used by `SkyboxCube`, `ProceduralSky`, `HdrSky`, `HdrEnvironmentBuild` and the
  plugin's `LoadingOverlay`.
  - No `FullscreenSky` base class: the two skies set very different uniforms, so a base class would save little.
  - `ProceduralSky.CAMERA_HEIGHT` becomes a file-level `private const val`.

### D-11. Small shared helpers (H2, D1–D3, D9, D11, D14, L2, L5, L6)
- **Cancellation:** delete `dto/Cancellation.kt`. The plugin imports `net.nevinsky.abyssus.assets.runCatchingKeepingCancellation`
  (verified in task 1.1 that `ProcessCanceledException` is a `CancellationException` on 252). Replace the four
  `runCatching` uses.
- **Build check:** a `checkNoRunCatching` Gradle task (a regex over `src/main` and `core/src/main`, as
  `checkNoSingletons` does) wired into `check`.
- **JSON setup:** a `JsonFormat` in `core` (a class, no object) builds the shared `JsonMapper.Builder` settings and the
  pretty printer. `JsonProcessor` and `SceneJson` both use it, and `SceneJson` keeps its own `RawNumberNode` reading.
- **Removed copies and small fixes:**
  - `ProjectReader.sceneFiles` goes; it calls `ProjectLayout.sceneFiles`.
  - `AssetReadResult` becomes `sealed` (`Ok` / `Failed`) with `of(Result)`.
  - Add `Throwable.displayMessage()`.
  - `WorldUtils` goes; the extension stays.
  - `SkyboxChoice` becomes a plain class (L2).
  - `textOf` moves to `dto/` (L5).
  - `ProjectLayout` keeps only the VFS helpers and uses `core` constants directly (L6).

### D-12. Tree actions share a base (M5)
`abstract class AbyssusTreeAction<T : Any> : AnAction(), DumbAware`:
- It has `internal open fun selected(e)`, `getActionUpdateThread() = EDT`,
  `abstract fun targetOf(node: Any?): T?` and `abstract fun perform(project, target: T, e)`.
- `update` sets enabled/visible from `targetOf` and the optional `isEnabled(target)`.
- `AddComponentAction`, `RemoveComponentAction` and `AddLightAction` extend it, and `AddLightAction` uses
  `viewableSceneFile`.

## Risks / Trade-offs

- **Lights that omit `intensity` look brighter** (0.3 → 1) in existing users' scenes. → This is called out in
  `docs/ai/file-formats.md` and the release notes. The file is unchanged, and the panel always showed 1.
- **The `SceneRenderer` split touches GL code that has no headless coverage.** → Do it last. Keep the GL calls in
  their current order, run the GL tests with `-Dabyssus.glTests=true`, and run the runIde checklist (task 9).
- **The debounce can break tests that expect synchronous reloads.** → Flush the queue in the tests. `ReloadPolicy` is
  unit-tested, and plugin edits and undo bypass the delay.
- **A `SceneDocumentCache` copy could be mutated by mistake.** → Read callers get the cached node read-only by
  convention, and `editSceneJson` always parses fresh from the document text (it does not use the cache), so the write
  path cannot be affected.
- **Merging the `meta.json` readers could change the `show-project-assets` behavior.** → Its tests run unchanged; the
  type fallback stays `UNKNOWN`.
- **Other open changes edit the same classes.** → Phases 1–4 are small and mechanical, so land them first. Agree on a
  merge order with the owners of `add-realistic-water`, `add-scene-raytracing` and
  `add-asset-editing-and-terrain-generation` before phases 5–8.
- **The change is large.** → Phases 1–5 have no behavior change and can each be merged separately. Phases 6–8 depend
  on 1–2.

## Migration Plan

There is no data migration, and no file is rewritten. Each phase in `tasks.md` leaves `./gradlew check` green and can
be shipped on its own. Rollback is a revert of the phase's commits. Phase 2's only user-visible effect (the light
defaults) is reverted by restoring the view-side defaults in `ComponentDefaults` usage.

## Open Questions

- What number text does the Mundus editor write for whole floats in `PositionComponent` (`1` or `1.0`)? The answer
  decides a later change unifying transform and component number text. It does not affect this change, which keeps
  both as they are.
