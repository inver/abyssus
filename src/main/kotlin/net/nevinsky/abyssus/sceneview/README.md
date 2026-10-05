# sceneview

The **Scene View** editor tab of a `.scene`: a libGDX render on an LWJGL3-AWT GL canvas inside a Swing panel, with
picking, camera markers, look-through, move/rotate gizmos and Drop. Required behavior: `openspec/specs/scene-*`.

## Pieces

| Class | Role |
|---|---|
| `SceneFileEditor` / `SceneFileEditorProvider` | The tab. Re-reads params (typing after a 200 ms pause via `ReloadPolicy`; VFS changes, plugin edits and Undo at once); writes transforms via `editSceneJson`; `DocumentReferenceProvider` for undo. The provider is where the tab's collaborators are looked up and passed in |
| `SceneParamsSource` | Scene + project `mainCamera` → `SceneRenderParams`, from unsaved editor text when present |
| `SceneContent`, `PlacementMapper` | `ecs` JSON → placements: models, terrains, lights, cameras, skybox. The components are decoded by the same codecs the Properties panel uses (`DecodedEntity`), so both show the same values and defaults; `PlacementMapper` (pure) maps them. A light's or camera's direction resolves its `lookAtId` to an entity's `localPosition` when that target exists and is not at the entity itself, else it uses the entity's `localRotation`. `handleIds` records the `HANDLE` entities a light may be aimed at |
| `LightSet`, `SpotCone` | Deterministic light selection and CPU cone/range attenuation math |
| `shadows/` | Per-context atlas, stable tile allocation, fitted light cameras and shared model/terrain depth pass |
| `SceneView` | Interface of the view, so tests can pass a fake (`viewFactory`) |
| `SceneViewPanel` | Swing panel: GL canvas, Swing `Timer` frame loop, toolbar, keys (W / E / D / Esc) |
| `SceneViewState` | What the user chose: selection, gizmo mode and hovered handle, the camera looked through, the drag/drop preview. The panel changes it; the renderer and the queries read it |
| `SceneInteraction` | Mouse and key logic without Swing or GL, over a `SceneViewState` and `SceneQueries`: click → pick/select, drag → gizmo or orbit/pan (one `Gesture`: Idle, Dragging or Cancelled), Drop → a Y-only move |
| `FrameSnapshot`, `SceneQueries`, `SnapshotSceneQueries` | What the last frame drew (camera copy, model boxes, terrain targets, `drawnVersion`), and the CPU-only questions asked of it: pick, ray, ground below, lowest point, gizmo handles and hits, drag start. Tested with hand-built snapshots |
| `SceneRenderer` | One frame: environment, skybox, grid, terrains, models, markers, highlight, gizmo. GL only: it publishes a `FrameSnapshot` after each frame and exposes `queries`. `GridModel` and `SelectionBox` build the grid and the highlight |
| `PlacedAssets`, `SceneModels`, `SceneTerrains`, `SceneSkybox` | Per-kind loaded assets (an `AssetView` each over the view's `ViewAssets`, whose `ProjectAssets` from `AssetLoading` hold the one `core` `AssetStorage`) and per-entity instances (`PlacedEntities`); `SceneModels` and `SceneTerrains` extend `PlacedAssets` and `SceneSkybox` has the same `abandon` |
| `skybox/` | `SunDirection`: the sun a procedural sky is lit from, from the scene's lights. The sky loaders, the HDR environment and the sky shaders are in `core` (`net.nevinsky.abyssus.core.assets.sky`) |
| `SceneMarkers`, `CameraFrustum` | Camera body and frustum, light markers, and their pick bounds |
| `ScenePicker` | Ray from a pixel, nearest hit over boxes and terrain heights (used by `SnapshotSceneQueries`) |
| Drop: `OrientedBox`, `TerrainRestHeight`, `ScenePicker.restHeight` | Highest surface under a rotated box footprint; CPU-only bilinear terrain-cell maxima |
| `ScenePreview` | Applies a drag or drop preview over the placements, and simulated poses (`withPoses`) while playing |
| `SceneExtensions.kt`, `SceneOverlayHost` | The `sceneOverlay` and `sceneSimulation` extension points and the per-view overlay list that switches off a failing overlay |
| `PlayState` | Play in one view without Swing or GL: `IDLE -> STARTING -> PLAYING <-> PAUSED -> IDLE`, plus `FAILED` |
| `gizmo/` | Handle geometry (`GizmoHandles`), hit tests (`GizmoHit`), drag math (`GizmoDrag`), drawing (`GizmoDraw`) |
| `SceneTransformWriter` | A finished transform → `PositionComponent` (and camera) fields in the scene JSON |
| `GdxRuntime`, `GuardedGLCanvas` | The `Gdx.*` shim, and the canvas that refuses unsafe GL |

## Things that are not obvious

- **`Gdx.*` is shared by the whole IDE.** `GdxRuntime.withContext` installs this canvas's `Gdx.app` / `graphics` /
  `gl*` / `files` and the GLSL 150 prefixes under a lock, and restores the old values afterwards. Every libGDX call
  must be inside it. Clicks arrive outside it, so `ScenePicker.pickRay` unprojects by hand instead of using
  `Camera.getPickRay`.
- **GL only on a safe surface.** `GuardedGLCanvas.glSafe` needs the canvas showing, non-empty, not minimized, and
  stable for 250 ms. On macOS a zero-sized surface aborts the JVM. A canvas disposed while hidden drops its context
  without making it current, so its GL objects can't be released. macOS also stops sizing that canvas's native
  surface with the component, so `SceneViewPanel` replaces such an "abandoned" canvas when the view is shown again.
- **Asset loading lives in `core`** (`core/README.md`). `AssetStorage.prepare` runs on a pool thread (IO and decoding,
  no GL). `build`, and `upload` for big textures, run on the render thread one slice per frame, inside this package's
  `GdxRuntime.withContext`. A new project gets a new cache, so a pool thread never prepares from a stale project. A
  failed asset is remembered and logged once, through the SLF4J `Logger` `AbyssusCore` gives `AssetLoading` (`Abyssus.assets`).
- **Changed assets reload without reopening the view.** `AssetRefresh` (UI thread, reads on the pool) compares
  snapshots of the project's effective asset revisions: each `meta.json` as the editors hold it (unsaved text is captured
  on the UI thread by `unsavedAssetMeta` and handed in as immutable text, so pool threads never touch documents) plus the
  stamps of the files it names. Only a real difference produces an `AssetRevisionBatch` (names plus the unsaved
  `meta.json` text, which `FileLoader` serves to later loads), so saving shown text, or Undo back to loaded text, costs nothing. A texture change also names the terrains
  that use it. `SceneFileEditor` feeds it VFS and document events and passes the batch to `SceneView.refreshAssets`;
  `SceneRenderer.queueAssetRevision` keeps batches (merged, in `PendingAssetRevision`) until `render` takes them, and
  `render` only runs while the canvas is safely on screen, so a hidden view reloads when it is shown. On the render
  thread the batch gives `ViewAssets` the unsaved text and invalidates the names (`AssetStorage.invalidate`): the old
  asset keeps drawing until its replacement is built, then is disposed once; a superseded load is discarded. A terrain's
  mesh and CPU height data come from one `TerrainMesh`, so drawing, picking, Drop and shadows all see the same
  replacement (`drawnVersion` changes when an asset is replaced so Drop re-measures). Nothing moves entities.
- **The view reads JSON, not the ECS engine.** Placements come straight from the `ecs` JSON. `ParentComponent` is
  ignored, and `local*` values are drawn as world values; drags write them the same way.
- **Picking needs no renderer.** `SceneRenderer.publishSnapshot` copies the camera, the drawn boxes and terrain targets after each
  frame; `SnapshotSceneQueries` answers from that copy, the `SceneViewState` and the scene content, and puts the drawn
  boxes together once per snapshot and preview. Marker boxes (cameras, lights) come from the content at question time.
- **Drags preview, then write once.** During a drag `ScenePreview` overrides the dragged entity's placement. On release
  one `editSceneJson` command ("Move Entity" / "Rotate Entity") writes it. The document change re-reads params, and
  the override stays until they arrive so the object doesn't jump back.
- **Play shows poses, never writes them.** With a `sceneSimulation` provider installed (Abyssus Physics), the toolbar
  has Play, Pause, Step and Stop. `PlayState` starts the provider's simulation from the scene's document text, the
  project folder and the selection. Each frame the panel copies the simulation's latest poses into
  `SceneViewState.poses`. The renderer's `posedContent` applies them over the authored placements, keeping each
  placement's scale, so drawing, picking, markers and overlays all see them. A drag preview applies on top. While a
  simulation is active, gizmos are off (`SceneViewState.gizmosEnabled`), Move/Rotate/Drop are disabled, and every key
  but Escape goes to the simulation and is consumed. Mouse buttons and moves go to it too, and the camera still
  orbits. Escape stops. `SceneFileEditor` stops play in `beforeDocumentChange` of the scene or its project file, so
  any edit (text, panel, tree) applies to the authored scene. Closing the tab stops it too. A simulation that ends on
  its own (`FAILED`) returns the view to the authored poses; its provider shows the notification. A provider that
  throws is switched off for the view with one logged error.
- **Overlays draw twice a frame.** Each `sceneOverlay` provider gets one `SceneOverlay` per view. `drawOverlays` calls
  them inside `GdxRuntime.withContext`, after the markers with depth testing (`OverlayView.onTop` false), and again
  after the selection and gizmo without it. They see the content as drawn (poses and previews applied) and the
  scene's raw `ecs` (`SceneRenderParams.ecs`). They draw only through `LineSink` and own no GL. An overlay that throws
  is disposed and switched off for that view with one error naming its plugin. Its `actions()` (such as Show Physics)
  join the toolbar.
- **Objects without rotation.** A camera whose `lookAtId` resolves, a point light, and a light aimed at anything
  other than a direction handle get Move handles only. A directional or spot light aimed at a `HANDLE` entity keeps
  its rings, but a rotate drag on it turns the direction and moves the handle (`ScenePreview.aimedTarget`), writing
  the handle's `PositionComponent`; a move drag re-aims the light at its unmoved handle.
- **HiDPI:** mouse positions are Swing pixels; the framebuffer can be larger. `ViewSize` converts between them for
  picking and gizmo hits.

- **Drop is an area query.** The button and D key ask for the highest surface under an oriented-box footprint,
  using other oriented boxes and transformed bilinear terrain cells. This cannot use `pickRay`: picking returns
  one entity at one point, `intersectRayBounds` reports the ray origin when it starts inside a box, and the terrain
  march is bounded by `camera.far` and assumes uniform scale. Drop has no fallback ground. A sunk object rises;
  terrains and the looked-through camera cannot drop. Heights within 0.0001 world units count as equal, so repeated
  drops are idempotent. The edit uses the existing Move Entity command and changes only Y.
- **Drop availability follows loading.** `SceneRenderer.drawnVersion` changes when models or terrains enter or leave
  the drawn lists. After rendering, outside `GdxRuntime.withContext`, `SceneInteraction.frameRendered` queries once
  for a changed version and notifies controls only when availability flips. Scene params, selection, camera and drag
  changes also refresh availability. Params invalidate the next frame's query as well, because a transform update
  leaves drawn entity ids unchanged.

## Add Light

The toolbar's Add Light menu offers Directional, Sun and Spot through `AddLightGroup`. `SceneFileEditor` supplies
the scene file and availability check; the panel supplies its current orbit target when a choice is made.
The new entity is written as one Add Light command and selected in the Abyssus tree. Spot adds 5 to placement Y.
Unreadable scene text disables creation (and the editor shows its existing parse-error state).

## Spotlight illumination

`LightSet` retains each light's entity id and separates directional, point and spot sources. It selects at most two
directional lights and five local lights total (point and spot share that limit), nearest the orbit target with
entity-id tie-breaking. Invalid light values are skipped. The model shader configuration supports five spot slots;
the shared selection ceiling keeps the total selected local lights bounded.

Default models, PBR models and terrain use the spotlight's rotated -Z axis, saved full cone angle and edge softness.
`SpotCone` computes outer and inner half-angle cosines; the inner angle is the outer angle times one minus softness.
Zero softness gives a sharp edge; positive softness gives a smooth inward fade without changing the outer boundary.
All local lights fade from 75 percent of their range to zero at the range limit. Missing beam values use 45 degrees
and 0.2 softness without writing the scene. Properties displays softness as percent; storage and compatibility
limits are documented in `docs/ai/file-formats.md`.

## Scene shadows

After applying drag previews, each frame updates model poses and terrain transforms once. `SceneShadows` captures
their renderables, renders the depth atlas, and restores the caller's framebuffer, viewport and depth/blend/scissor
state before the sky, grid and color passes. Color uses those same poses and transforms. Models and terrain both
cast and receive; markers, grid, sky, selection outlines and gizmos do not cast. Each light's visibility multiplies
only its direct contribution, leaving other lights, ambient, HDR environment lighting and emissive output intact.
`ShadowCasterBounds` caches per-bone mesh boxes and transforms them with the current pose and node transform for
fitting and culling, so movement outside a model's initial pose does not clip its shadows. Skin weights use the
model runtime's normalized-weight convention.

The atlas is RGBA8888 with a depth buffer, 4096 square, with at most sixteen views. A single shadow uses a
4096-square tile; two to four views use 2048-square tiles, five to nine use 1365-square tiles, and larger sets use
1024-square tiles. The grid grows when needed and keeps its size until all shadow lights are removed, preserving
surviving tile positions after deletion. Among selected
lights, stable entity-id order assigns shadows to one directional light, two point lights (six faces each), and
three spots. Additional selected lights still illuminate. Point faces store radial distance; spot projection uses
the saved full cone and range. Directional fitting clips receivers to an 80-unit camera region, includes upstream
casters overlapping that region in light space, and snaps to texels. Four bilinear PCF samples (sixteen depth comparisons) smooth edges while clamping to actual tile texel centers.
Receiver-plane depth gradients correct each tap for surface slope, with a small numerical bias rather than a large
detaching offset. Packed depth uses base-255 digits matched to RGBA8 quantization, with dithering and sRGB output
disabled during depth rendering. Coverage outside a projection remains lit.

The depth shader supports the custom 32-bit mesh indices, posed bones and diffuse alpha-test cutouts. Materials
with active alpha blending do not cast. Terrain uses its color mesh and transform as an opaque depth renderable.
See `gdx-model/README.md` for the reusable atlas attribute and provider API.

Resources belong to one canvas and are created, rendered and disposed inside `GdxRuntime.withContext` on the
safe AWT render thread. A lost/hidden context abandons references without GL calls; recreation builds a fresh atlas
and depth shaders. Missing framebuffer support, insufficient texture size or fewer than twelve fragment texture
units disables shadows while keeping lighting. A failed depth pass disables shadows until recreation. Terrain
reserves unit 6 for the atlas, between its six layer textures and irradiance on unit 7; model shaders use their
texture binder. Atlas allocation and tile passes restore GL state even on failure.

## Ray tracing scene conversion

`RaySceneSnapshots` converts the same preview-applied `SceneContent`, `LightSet`, camera and environment that raster
draws into an immutable `RaySceneSnapshot`, from CPU asset companions only (never GL handles). `RaySceneDiff` classifies
what changed between frames (structure, transform, pose, camera, light, material, environment), and anything the
backend cannot represent becomes an explicit `RaySceneConversion.Fallback`. `RayModelPoses` copies each animated or
skinned entity's displayed pose on the render thread, after animations advanced, and `RayModelSkinning` (core)
deforms the shared source mesh per instance on a worker.

What the native renderer then draws, and its bounds, are in `raytracing/README.md`: per-light shadow rays, cutouts,
reflections up to the scene's saved depth, glass refraction for materials given a transmission override, sky and fog like
raster, and front-to-back alpha blending that neither casts shadows nor appears in reflections. Whole-view raster
fallback applies when the scene exceeds those bounds, when its saved `rayTracing` settings or optical overrides are
malformed or name materials the model lacks, when glass is not a closed opaque-PBR solid, and when the backend lacks
`sceneOptics` (`RayBackendService` checks each request).

Each frame's conversion also carries the scene's saved settings (`SceneRaySettings`, from `SceneRenderParams`) and
the entity's optical overrides (`RayMaterialOverrides`). An override copies only that entity's material, with
transmission and IOR, into its own material index; meshes and textures stay shared with other instances.

## Ray Tracing mode

Ray Tracing is per view and off by default, and the Scene View's toolbar has no control for it: the switch lives in Abyssus
Properties (below). `RayModeState` holds the phase (Off, Checking, Preparing, Active, Unavailable, Failed) and a revision that
rejects late results after off, retry, hide or close. It holds no camera, selection or drag state, so switching renderers
cannot change them. The Properties switch shows the phase, an unusable GPU's reasons (`RayModeText`) and Retry after a
failure, and the tooltip of an active view names the backend and GPU. `RayBackendSelector` chooses a backend once per IDE
session (`-Dabyssus.raytracing.backend=auto|metal|vulkan|off`); nothing native loads until ray tracing is switched on.
Probe results, the chosen backend and GPU, session limits, scene fallbacks and every failure go to `idea.log` through an SLF4J
`Logger` (category `Abyssus.ray`; add `#Abyssus.ray` in Help | Diagnostic Tools | Debug Log Settings for the debug lines).
See Logging in `docs/ai/conventions.md`.

Each frame `SceneRenderer` calls its `rayFrameProvider` (the panel's `RayViewFeed`) with a `RayFrameContext`: the
preview-applied content, camera, lights, animated models and the built HDR sky's ambient colours. The feed copies that
state on the EDT, converts it off the EDT, and returns the newest completed frame that still matches the view's size,
camera, project and drawn entities; otherwise the renderer draws raster as usual. A presented ray frame goes through
`RayFramePresenter`, then the grid and overlays are drawn with the frame's own camera against its depth. Hiding the view
stops submissions and releases the session; showing it resumes without stale images. The scene's sky is transferred as a
CPU `RaySkySnapshot` (HDR and cube skies) or, for a procedural sky (arbitrary asset GLSL), rendered once per sky and sun
direction by `RaySkyBaker` into six faces on the render thread and resampled. A sky that is still loading or cannot be
transferred shows the background colour; it never fails the view. The mode reads renderer state
and never writes a scene file, so every edit, move, rotate, drop and undo reaches it through the view's existing state.

### Switching it from Abyssus Properties

A selected scene row in the Abyssus Properties panel (`properties/SceneDetailsView.kt`) has a **Ray Tracing** switch with the
same status, reason and Retry. It reaches the live view through `SceneRayControls`, a project service: each
`SceneFileEditor` registers its view's `RayControl` (implemented by `SceneViewPanel`, which flips the same
`RayViewRuntime`) by scene file, and `request` applies a change to every open view of that scene. With no
Scene View open, switching it on opens one (`openSceneView`) and applies the request when that view registers. The
switch follows the view's `RayModeState`, so it never disagrees with it, and the switch persists nothing: the mode ends with
the view and turning it on or off never writes the scene file.

### Saved settings and glass in Abyssus Properties

Below the switch, the same Rendering section edits the scene's saved `rayTracing` limits: **Target samples per pixel**
(accumulated while the view is still), **Maximum rays per frame** (all queries of one submitted frame), and maximum
reflection and refraction bounces. They are scene data, unlike the switch: each accepted edit is one `editSceneJson`
command through `SceneRayEdits`, checked against the value the panel was built from (a newer value wins and the field
says so), and Undo/Redo work from the panel through its hidden text editor on the scene. They stay editable with no view
open or no ray tracing hardware, selecting a scene writes nothing, and a malformed saved value is shown beside its
field, never rewritten. Every open view of the scene reads the new values on its next frame and discards older results.

A selected model entity (or its Render component) lists its model's materials under **Ray Tracing materials** with
**Transmission (%)** and **IOR** for each uniquely named PBR material, stored as overrides of that entity only
(`RenderComponent.rayTracingMaterials`). Repeated or missing material identifiers and non-PBR materials get an
explanation instead of editors; stored overrides for materials the model no longer has are listed, kept and never
retargeted. The material table comes from `AssetLoading.rayModelMaterials` (no images decoded), cached per model file
by the tool window. Raster rendering ignores both values.

Native scene input uses `format: "abyssus"` and integral `formatVersion: 1`. Asset renderables dispatch on `kind: "asset"`
and their folder reference, without class loading. Unknown native kinds draw nothing and remain raw; light and camera
look-at resolution still uses the preserved entity ids and transforms. Unsupported enclosing documents are rejected
before view construction, and unsupported asset metadata never reaches GPU build.
