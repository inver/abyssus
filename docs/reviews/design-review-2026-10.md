# Design review: GoF patterns, KISS, SOLID, duplication

Date: 2026-10-03. Scope: the plugin (`src/main/kotlin`) and `core` (`core/src/main/kotlin`), about 11k lines.
`gdx-model` is a libGDX/Mundus fork and is only covered where it touches the rest.

## Summary

The architecture is in good shape. The module boundaries are clear and enforced (`core` has no singletons and is wired
by constructors, and `gdx-model` stays plain JVM). The asset pipeline (`AssetLoader` → `AssetCache` → `SceneAssets`) is
a clean Strategy + Template design, and math and decision logic is kept out of Swing/GL so it can be unit-tested.

The problems are concentrated in three places:

1. **The `.scene` ECS format is parsed and written in three independent places**, and they already disagree on
   defaults (a real bug, see H1).
2. **The scene view has a God class.** `SceneRenderer` (446 lines) renders and also holds the view's UI state,
   picking, ground queries and gizmo logic. `SceneInteraction` drives it by writing its public `var`s.
3. **Small helpers are duplicated** (cancellation, JSON mapper setup, scene folder listing, entity name, skybox face
   keys, meta-type strings, the sky fullscreen triangle). The project's own rules are also broken in four places
   (`runCatching`).

| Area | Rating | Main issue |
|---|---|---|
| Module boundaries / DIP across modules | Good | — |
| `core` asset pipeline | Good | `AssetCache` re-declares `AssetLoader` as five lambdas |
| Scene view (`sceneview/`) | Needs work | SRP in `SceneRenderer`, state shared through mutable fields |
| Scene JSON model (`SceneContent`, `ecs/`, `SceneTransformWriter`) | Needs work | Three parsers/writers of one format |
| Tree / actions (`projectView/`) | Fair | Service locator, repeated action boilerplate, parsing in `update()` |
| Properties (`properties/`) | Fair | A fourth `meta.json` reader; string types instead of `MetaType` |

---

## What is already done well (keep it)

| Pattern / principle | Where |
|---|---|
| **Strategy** | `AssetLoader<P, T>` with `ModelLoader`, `TerrainLoader`, `SkyboxLoader`, `ProceduralSkyLoader`, `HdrSkyLoader`; `ComponentCodec<C>` per component; `SceneParamsSource` |
| **Composite** | `SkyLoader` is an `AssetLoader` that delegates to three `AssetLoader`s |
| **State** (sealed) | `AssetCache.State` (`Loading` / `Failed` / `Ready`), `EditResult`, `PanelState`, `AssetMeta` |
| **Composition root / DI** | `AssetLoading` (in `core`) and `AbyssusCore` (in the plugin); `checkNoSingletons` enforces it |
| **Observer** | `AbyssusSelectionListener.TOPIC` on the message bus; VFS and document listeners |
| **Command** | Every write is a named `WriteCommandAction` through `editSceneJson`, so it can be undone |
| **Template Method** | `GuardedGLCanvas` (`initGL` / `paintGL` / `disposeGL` / `onContextAbandoned`) |
| **Testable core** | `ScenePicker`, `GizmoDrag`, `GizmoHit`, `SceneMarkers`, `PanelState`, `SkyboxPickerModel` have no Swing/GL |
| **OCP extension points** | A new asset kind is a new `AssetLoader`; a new component is a new `ComponentCodec` |

---

## Findings

Severity: **H** = a bug or a likely source of bugs; **M** = a design cost that slows changes; **L** = cleanup.

### H1. One file format, three parsers. They already disagree (DRY, SRP; a behavior bug)

The `ecs.entities.<id>.components` schema is implemented three times:

| Reader / writer | File | Used for |
|---|---|---|
| Ad-hoc `JsonNode` reader | `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneContent.kt` | Rendering, picking |
| `ComponentCodec`s (read + write) | `src/main/kotlin/net/nevinsky/abyssus/ecs/scene/ComponentCodecs.kt` | Properties panel, component edits |
| Ad-hoc writer | `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneTransformWriter.kt` | Gizmo drags, Drop |

They have already drifted apart:

- **Light intensity default:** `SceneContent` uses `0.3f` (`SceneContent.kt:171`), but `LightData` / `LightCodec` use
  `1f` (`ecs/component/LightComponent.kt`). A light without `intensity` is **rendered at 0.3 but shown and edited as
  1.0** in the properties panel.
- **Color channel default:** `readColor` fills a missing channel with `0f` (`ComponentCodecs.kt:73`), while
  `SceneContent` uses `1f`. A light with `"color": {"r": 1}` renders white but the panel shows red.
- **`lookAtId`:** the codec reads only integers (`asInt`); `SceneContent` also accepts text ids.
- **Number text:** codecs write whole numbers as integers (`number()`); `SceneTransformWriter` always writes
  `FloatNode` (`SceneTransformWriter.kt:66`), so dragging an entity can write `1.0` where an edit in the panel writes `1`.
- **Defaults are copied:** `100f` / `45f` / `0.2f` appear in `LightData`, `LightCodec.write`, `LightPlacement` and
  `SceneContent.lightOf`. `DEFAULT_LIGHT_RANGE` exists but the codec doesn't use it.

**Recommendation.** Make the codecs the single source of truth for the format:

1. Move the defaults into one place (for example a `LightDefaults` / `CameraDefaults` object next to the components),
   and have `LightData`, the codecs and `SceneContent` refer to it.
2. Build `SceneContent` from the codecs: decode each entity's components with `ComponentCodecs`, then map the
   components to `AssetPlacement` / `LightPlacement` / `CameraPlacement`. `SceneContent.of` becomes a small **Adapter**
   from ECS components to render placements, with no JSON knowledge.
3. Turn `SceneTransformWriter` into "decode `PositionComponent` → mutate → encode with `PositionCodec` → merge the
   changed keys", as `ComponentEditor.update` already does. This also fixes the number-text difference.
4. Add a test that loads the same fixture through both paths and checks they produce the same values.

### H2. The project's own rules are broken (`runCatching`)

`AGENTS.md` asks for `runCatchingKeepingCancellation` instead of `runCatching`. These four places still use `runCatching`:

- `src/main/kotlin/net/nevinsky/abyssus/projectView/EnabledToggle.kt:38` (inside `editSceneJson`, which every write goes through)
- `src/main/kotlin/net/nevinsky/abyssus/projectView/ComponentActions.kt:101`
- `src/main/kotlin/net/nevinsky/abyssus/sceneview/SceneRenderParams.kt:92`
- `src/main/kotlin/net/nevinsky/abyssus/filetype/SceneJson.kt:95`

**Recommendation.** Replace them, and add a small Gradle check (like `checkNoSingletons`) that fails on `runCatching {`
in `src/main` and `core/src/main`.

### H3. Parsing the whole scene in `AnAction.update()` and on every keystroke (KISS, performance)

- `canAddLight(file)` (`projectView/AddLightAction.kt:60`) reads the file and parses it **twice** (`SceneReader.parse`,
  then `SceneJson.parse`). It runs from `AddLightAction.update`, from each `AddLightGroup` child's `update` and from
  `SceneViewPanel.syncControls`. The IDE calls `update` many times a second on the EDT.
- `SceneComponentEdits.addLight` serializes the tree back to text (`root.toString()`) only to check that it binds.
- `SceneFileEditor` re-reads and rebuilds the scene on every `documentChanged` (`sceneview/SceneFileEditor.kt:97`),
  so typing in the text tab re-parses the whole scene on every key press.

**Recommendation.** Cache the parsed tree per `(file, modificationStamp)`. `AssetReadCache` already does this for the
tree, so extend it or add a `SceneDocumentCache`. Have `canAddLight` read from that cache. Debounce the editor reload
with `Alarm` / `MergingUpdateQueue` (about 100–200 ms).

---

### M1. `SceneRenderer` is a God class (SRP, ISP)

`sceneview/SceneRenderer.kt` handles:

- GL lifecycle and the render passes (grid, sky, terrain, models, shadows, overlay)
- Camera setup (`updateCamera`)
- Lighting and fog (`applyLights`, `applyEnvironment`)
- View UI state: `selectedId`, `gizmoMode`, `hoveredAxis`, `viewCamera` and `preview`, all `@Volatile var`
- Picking, ground and Drop queries (`pick`, `groundBelow`, `lowestPoint`, `targets`)
- Gizmo hit tests and drag start (`gizmoHandles`, `gizmoHit`, `beginDrag`)
- Grid mesh building and the selection-box drawing
- Test hooks (`drawnModels`, `drewGizmo`, `drawnCameraMarkers`, …)

`SceneInteraction` is meant to be "apart from Swing and GL". Yet it takes the concrete `SceneRenderer` and works by
writing the renderer's fields, so the renderer has become a mutable state store that two classes share.

**Recommendation.** Split it along the existing seams:

```
SceneViewState        selectedId, gizmoMode, hoveredAxis, viewCamera, preview  (plain class, EDT-owned)
SceneQueries          pick / groundBelow / lowestPoint / gizmoHit / beginDrag   (CPU only; reads last-frame snapshot)
SceneRenderer         GL passes only; reads SceneViewState; publishes a FrameSnapshot (camera, drawn boxes, terrains)
SceneInteraction      depends on SceneViewState + SceneQueries interfaces, not on SceneRenderer
```

This also lets `SceneInteraction` be tested with a fake `SceneQueries` instead of a real renderer (DIP). It removes
the `groundBelow` lambda parameter that only exists for testing.

### M2. Writing a file also refreshes the tree (SRP, DIP)

`editSceneJson` (in `projectView/EnabledToggle.kt`) parses, mutates, writes, saves, and then calls
`ProjectView…getProjectViewPaneById(AbyssusProjectViewPane.ID)?.updateFromRoot(true)` (line 45). So the low-level
document writer depends on a UI pane. The file name also hides it: every writer in the plugin goes through a function
in `EnabledToggle.kt`.

**Recommendation.** Move `editSceneJson` to its own file (for example `filetype/SceneDocumentWriter.kt`). Publish an
`AbyssusSceneChanged` message-bus topic (Observer), or let the pane react to the document/VFS change it already
receives, and remove the direct pane call. Keep the toggle, skybox and rename helpers in `EnabledToggle.kt` or move
them to `SceneEdits.kt`.

### M3. Service locator inside domain classes (DIP)

`service<AbyssusCore>()` / `service<SceneReader>()` are called inside `SceneReader.parse`, `ProjectReader.read`,
`PanelState.hdrCell`, `SkyboxChoices`, `SceneComponentEdits.addLight`, `canAddLight`, and in the default argument of
`SceneViewPanel`'s constructor. That makes these classes hard to test without the IDE, and it contradicts the
constructor-injection style that `core` follows.

**Recommendation.** Pass `JsonProcessor` / `SceneReader` / `AssetLoading` through constructors. Look services up only
at the IntelliJ entry points (`FileEditorProvider`, `ToolWindowFactory`, `AnAction`, `@Service` constructors).

### M4. Four separate `meta.json` readers, and type strings instead of `MetaType` (DRY)

| Reader | Result |
|---|---|
| `core/.../assets/files/AssetFiles.kt` | `JsonNode` and `MetaBase<T>` (parses the same file twice: `meta()` and `loadMeta()`) |
| `src/.../dto/ProjectAssetListing.kt` | `MetaBase<Any>` + references (also parses the text twice) |
| `src/.../properties/AssetMeta.kt` | `JsonNode` + `type: String?` |
| `src/.../projectView/SkyboxChooserDialog.kt` / `SkyboxChoices.kt` | `Map<String, JsonNode?>` |

The type constants are copied as strings: `SKYBOX_TYPE`, `PROCEDURAL_SKY_TYPE` and `HDR_SKY_TYPE`
(`projectView/SkyboxChoices.kt`), `"SKYBOX"` (`properties/PanelState.kt`) and `"MODEL"` / `"TERRAIN"`
(`projectView/SceneComponentEdits.kt`). `core` already has `MetaType` for all of them. The skybox face keys are listed
three times: `SKYBOX_FACES` (`PanelState.kt:58`), `FACE_KEYS` (`SkyboxChoices.kt:45`) and `SkyboxAdditional`'s fields.

**Recommendation.** Add one `AssetMetaReader` in `core` that returns `(type: MetaType, json: JsonNode, typed: MetaBase<T>?)`
from a single parse, plus a VFS adapter in the plugin. Use `MetaType` everywhere. Move `SKYBOX_FACES` to `core`, next
to `SkyboxAdditional`.

### M5. Copied `AnAction` code in the tree actions (DRY, Template Method)

`AddComponentAction`, `RemoveComponentAction` and `AddLightAction` all repeat:
`internal open fun selected(e) = selectedNode(e)`, `getActionUpdateThread() = EDT`, `private fun target(e)`,
`update { isEnabledAndVisible = target(e) != null }`. `AddLightAction.file()` (`AddLightAction.kt:72`) is a copy of
`viewableSceneFile()` (`AbyssusNodes.kt`).

**Recommendation.** Use an abstract `AbyssusTreeAction<T>` with `abstract fun target(node: Any?): T?` and
`abstract fun perform(project, target: T, e)`. The base class does the selection lookup, the update thread and
enable/visible. Replace `file()` with `viewableSceneFile(selected(e))`.

### M6. Two `AssetLoader` adapters in `core` (KISS)

- `AssetCache` takes `prepare`, `build`, `advance` and `discard` lambdas. Its only production caller,
  `SceneAssets.newCache`, builds them from an `AssetLoader`. The `name` argument of `build` is never used.
  **Recommendation:** let `AssetCache` take `(executor, prepare: (String) -> P?, loader: AssetLoader<P, T>, log)`.
- `SkyLoader` dispatches with a `when` in each of its four methods. **Recommendation:** have each `PreparedSky` hold its
  own loader (`class Tagged<P>(val loader: AssetLoader<P, out Sky>, val prepared: P)`). Then `upload`, `build` and
  `discard` become one-line delegations, and adding a fourth sky kind touches only `prepare` (OCP).

### M7. Copied texture upload code (DRY)

`PreparedModel.uploadNext` / `dispose` (`core/.../model/ModelLoader.kt`) and `PreparedTerrain.uploadNext` / `dispose`
(`core/.../terrain/TerrainLoader.kt`) contain the same "pop one pixmap, upload it, keep the texture, release the rest"
logic.

**Recommendation.** Add a `TextureUploadQueue(pixmaps, factory: (name, Pixmap) -> Texture)` class with
`uploadNext()`, `textures` and `dispose()`, and use it from both.

### M8. Copied sky draw code (DRY, Template Method)

`ProceduralSky`, `HdrSky`, `HdrEnvironmentBuild` and `LoadingOverlay` each build the same fullscreen triangle
`Mesh(true, 3, 0, Position(2))` with `[-1,-1, 3,-1, -1,3]`. `SkyboxCube`, `ProceduralSky` and `HdrSky` repeat the
"view without translation × projection" matrix setup.

**Recommendation.** Add `FullscreenTriangle.create()` and `Camera.rotationOnlyViewProj(out)` helpers in `core`, and
optionally an `abstract class FullscreenSky` whose `draw` binds the program, sets `u_invViewProj` and calls
`abstract fun setUniforms(program, sun)`. Also reformat the `SkyboxCube` vertex array, which is currently one number per
line, and make `ProceduralSky.CAMERA_HEIGHT` a top-level `private const val` (it is an instance property written in
constant case).

### M9. `SceneInteraction` uses loose fields for drag state (State pattern, KISS)

The trio `drag` / `dragEntity` / `dragResult` (plus `cancelled`) is reset in four places (`SceneInteraction.kt:163`,
`202`, `228`, and in `released`). The call
`size.fx(x), size.fy(y), size.framebufferWidth, size.framebufferHeight` is repeated five times.

**Recommendation.** Use `sealed interface Gesture { Idle; Dragging(drag, entityId, result); Cancelled }` so that one
assignment changes the state. Have `ViewSize` produce a `FramebufferPoint`, or pass `ViewSize` itself to
`rayAt` / `pick` / `gizmoHit`. Cache `renderer.content` within a call: `dropPosition()` reads it three times, and each
read can run `ScenePreview.apply`. `canDrop` also builds `targets()` twice (`lowestPoint` and `groundBelow`).

---

### Duplicate code

| # | Duplicate | Locations | Fix |
|---|---|---|---|
| D1 | `runCatchingKeepingCancellation` | `src/.../dto/Cancellation.kt`, `core/.../assets/Cancellation.kt` | Keep the `core` one. Delete the plugin copy once you confirm that `ProcessCanceledException` extends `CancellationException` on since-build 252, as the `core` KDoc says. |
| D2 | Jackson mapper and pretty printer setup | `filetype/SceneJson.kt`, `core/.../json/JsonProcessor.kt` | One shared `JsonFormat` factory in `core` for the builder and pretty printer |
| D3 | Project scene listing | `ProjectReader.sceneFiles` (`dto/ProjectReader.kt:47`) = `ProjectLayout.sceneFiles` | Delete the private copy |
| D4 | Entity display name (`NameComponent.name` or id) | `DtoTree.entityName`, `PanelState.readEntityState:119`, `SceneContent.cameraOf` | `fun entityName(entity: JsonNode, id: String)` in `ecs/scene` |
| D5 | `ecs` / `entities` / `components` path walk | `ComponentEditor.entities`, `SceneTransformWriter.apply`, `PanelState`, `LightEntities`, `AddLightAction`, `DtoTree`, `SceneContent` | One `SceneEcsPaths` helper (`entities(root)`, `components(root, id)`) |
| D6 | Skybox face keys | `PanelState.SKYBOX_FACES`, `SkyboxChoices.FACE_KEYS`, `SkyboxAdditional` | One constant in `core` |
| D7 | Meta type strings | `SkyboxChoices`, `PanelState`, `SceneComponentEdits` | Use `MetaType` |
| D8 | `SceneModels` / `SceneTerrains` | Same `update` / `abandon` / `dispose` / `isLoading` | `open class PlacedAssets<P, A, E>(assets, place)`; the model class adds the animation step |
| D9 | `AssetReadResult` from `runCatching…fold` | `SceneReader.read`, `ProjectReader.read` | `AssetReadResult.of(result: Result<T>)`. Better: make it `sealed` (`Ok(obj)` / `Failed(message)`) instead of `success` + nullable `obj` |
| D10 | Image file lookup | `PanelState.thumbnail`, `PanelState.hdrThumbnail` | A private `imageFile(folder, name)` helper |
| D11 | `"…message ?: javaClass.simpleName"` | 8 places | `Throwable.displayMessage()` extension |
| D12 | Fullscreen triangle and rotation-only view-projection | See M8 | See M8 |
| D13 | Texture upload queue | See M7 | See M7 |
| D14 | `WorldUtils.getFromWorld` and the `Engine.getFromWorld` extension | `ecs/EcsConfigurator.kt` | Keep only the extension |

---

### Low-priority cleanup

- **L1. Code only tests use** (YAGNI): Ashley systems (`ecs/system/`), `ecs/render/`, `EcsConfigurator`,
  `SceneEcsLoader` / `SceneEcsWriter`. Either use them to drive the view (see H1) or move them to `testFixtures`, so
  `src/main` ships only what runs.
- **L2. `SkyboxChoice` is a `data class` with `var` properties outside the constructor** (`folder`, `faceFiles`,
  `thumbs`). `equals` / `copy` ignore them, which is surprising. Make it a plain class, or move them into the
  constructor and copy them.
- **L3. `SceneRenderer.create()`** sets new `skybox`, `overlay`, `lineBatch` and `terrainShader` without releasing or
  abandoning the previous ones (`SceneRenderer.kt:223`). `models` / `terrains` are abandoned, but the skybox's
  `SceneAssets` and its pending pool work are dropped silently. Abandon them the same way.
- **L4. `SceneRenderer.boundsOf`** computes `heights.min()` / `max()` over the whole terrain every frame while a
  terrain is selected. Cache the bounds on `TerrainEntity`, as `ModelEntity.localBounds` already does.
- **L5. `textOf(file)`** lives in `sceneview/SceneParamsSource.kt` but is used by `projectView` and `properties`. Move
  it to `dto/` next to `VirtualFile.text()`.
- **L6. `ProjectLayout`** re-exports `core` constants one by one. Use the `core` constants directly, or have
  `ProjectLayout` own only the VFS helpers.
- **L7. `AssetLoading`** exposes `decoder`, `hdrFiles`, `toneCurve` and `hdrPreview` as public `val`s for the
  properties panel. Wrap them in an `HdrPreviewService` so callers depend on one small interface (ISP).
- **L8. `DtoEntry.equals`** mixes identity with presentation (it compares the row text) to force tree refreshes. This
  works, but it is fragile. A comment explains why; consider a version counter in the entry instead.
- **L9. `scripts/check-docs.sh` already fails** on the current branch (3 broken paths: `openspec/specs/` is named in
  `AGENTS.md` and `docs/ai/glossary.md` but doesn't exist yet). Create the folder, or change the wording.

---

## Suggested order of work

| Step | Work | Risk | Gain |
|---|---|---|---|
| 1 | H2 (`runCatching`), D1, D3, D11, D14, L5 | Very low | Rule compliance, less code |
| 2 | H1 shared defaults + parity test (fixes the intensity/color bug) | Low | Correctness |
| 3 | H3 parse cache + debounce | Low | EDT responsiveness |
| 4 | M4 + D6/D7 (`MetaType`, one meta reader) | Low | DRY |
| 5 | M7, M8, M6 in `core` | Low (covered by `core` tests) | DRY, OCP |
| 6 | M2, M5, M3 | Medium | SRP/DIP, simpler actions |
| 7 | M1 + M9 (split `SceneRenderer`, `SceneViewState`, `SceneQueries`) | Medium | The biggest maintainability gain in the scene view |
| 8 | H1 fully: `SceneContent` and `SceneTransformWriter` on top of the codecs; decide on L1 | Medium | One model of the scene format |

Steps 1–5 are mechanical and covered by the existing tests. Following `AGENTS.md`, steps 7–8 change behavior-adjacent
structure, so they should go through an OpenSpec change.

---

## Status

The `design-review-refactor` change closed every finding above except these:

- **L1** (Ashley code that only tests use): a non-goal of that change; the classes are untouched.
- **L8** (`DtoEntry.equals`): needs its own tree-refresh decision.
- **The number-text question** (transform writes use `1.0`, component writes use `1`): deferred until it is checked against
  what the Mundus editor writes.
- **L9** (`scripts/check-docs.sh` failing): already fixed before that change.
- **`TerrainPreviewRunner`** keeps its own `try`/`catch` instead of `runCatchingKeepingCancellation`: its "superseded"
  cancellation is the run's outcome, so it must be captured.
