# sceneview

The **Scene View** editor tab of a `.scene`: a libGDX render on an LWJGL3-AWT GL canvas inside a Swing panel, with
picking, camera markers, look-through, move/rotate gizmos and Drop. Required behavior: `openspec/specs/scene-*`.

The logic without IDE, Swing or GL lives in `lib-core-editor` (see `projects/lib-core-editor/README.md`): the scene read model in its
scene package, picking, gizmo math, interaction and transform write-back in its pick package, and the ray tracing
bridge in its ray package. Rows below marked *(lib-core-editor)* are there; this package keeps the canvas, the renderer,
the toolbar, Play and the view's asset storage.

## Pieces

| Class | Role |
|---|---|
| `SceneFileEditor` / `SceneFileEditorProvider` | The tab, the second editor of the file after the text editor: the IDE's own `Text` and `Scene View` tabs switch between the JSON and the render on one shared document (unsaved text and Undo shared), and `openSceneView` selects Scene View. There is no custom preview/source switch. Re-reads params (typing after a 200 ms pause via `ReloadPolicy`; VFS changes, plugin edits and Undo at once); writes transforms via `editSceneJson`; `DocumentReferenceProvider` for undo. The provider in the root package is where the tab's collaborators are looked up and passed in; `SceneViewHost` carries tree selection and Add actions |
| `AntialiasingLevel`, `SampleFallback`, `CanvasAntialiasing` | Plain setting values and canvas lifecycle decisions: default 4×, fallback 8 → 4 → 2 → Off; hidden changes coalesce until shown. `SceneViewPanel` supplies replacement, warning and terminal failure callbacks |
| `AbyssusProjectSettings` (filetype package) | The native project's `sceneViewAntialiasing` field, edited in project Properties through `editSceneJson`. `SceneFileEditor` reads its owning `.abss` for new views and subscribes to `ProjectSettingsListener` for live updates, including unsaved text and Undo/Redo. The preference is excluded from content comparisons so Play keeps running. Standalone scenes use 4×; there is no application setting |
| `SceneParamsSource` | Scene + project `mainCamera` → `SceneRenderParams`, from unsaved editor text when present |
| `SceneContent`, `PlacementMapper` *(lib-core-editor)* | `ecs` JSON → placements: models, terrains, lights, cameras, skybox. The components are decoded by the same codecs the Properties panel uses (`DecodedEntity`), so both show the same values and defaults; `PlacementMapper` (pure) maps them. A light's or camera's direction resolves its `lookAtId` to an entity's `localPosition` when that target exists and is not at the entity itself, else it uses the entity's `localRotation`. `handleIds` records the `HANDLE` entities a light may be aimed at |
| `LightSet`, `SpotCone` *(lib-core-editor)* | Deterministic light selection and CPU cone/range attenuation math |
| `shadows` | Per-context atlas, stable tile allocation, fitted light cameras and shared model/terrain/foliage depth pass |
| `SceneFoliage`, `FoliageAssets` | The foliage of the scene's terrain entities, beside `SceneTerrains`: it asks the view's assets for the `FoliageComponent`s' folders, prefers the project's `FoliageDrafts` draft, generates a stale or draft-touched bake off the GL thread and culls the drawn copies against the camera (see **Terrain foliage**) |
| `SceneView` | Interface of the view, so tests can pass a fake (`viewFactory`) |
| `SceneViewPanel` | Swing panel: GL canvas, Swing `Timer` frame loop, the toolbar (a wrapping row of the groups below, then the Drop / Add / paint / Clouds / Play controls), and the wiring of the collaborators below |
| `SceneToolsToolbar`, `SceneViewToolsModel` | The mockup's groups as platform action toolbars: Select / Move / Rotate, Local-World and Snap, shading with the Shadows / Fog / Grid toggles, the camera chooser and Frame selection. The actions are per view and unregistered; they read `ToolbarState` and write the model. The model (space, snap, `ViewDisplay`) is per view, never saved, and tested without Swing |
| `SceneViewIcon` | The toolbar and tab icons: the design artifact's stroke glyphs (24 px grid, 1.8 stroke, round caps) as 16 px SVGs `icons/scene_view_<name>_icon.svg` plus a `_dark` twin the platform picks in dark themes. `IconInventoryTest` fails on a missing or unlisted file |
| `CornerOverlay`, `CornerSprite`, `CornerPainter` (`OrientationWidget`, `StatsStrip`), `cornerRect` | The orientation widget (upper right) and the stats strip (lower left). A Swing component cannot sit over the heavyweight canvas, so Swing paints each into an image (`toImage`, at the display scale) and `CornerOverlay` draws it as a blended quad after the loading overlay, uploading the texture only when the painter's `version` changes. A press on the widget's disc is handled by the panel (`pressOnOrientationWidget`, look along the clicked axis from its side) and never reaches the scene. The geometry is `cornerRect`, tested headless |
| `FpsCounter`, `SceneViewSettings` | The optional frame rate. `SceneViewSettings` (project service over the IDE's project `PropertiesComponent`, EDT) holds `showFps`; `SceneFileEditor` applies it to every view it creates (`SceneView.setFpsEnabled`) and to open views when it changes, and the panel also reads the setting itself before every frame (`showFps`), so a missed change event cannot leave the counter off. `FpsCounter` is pure: the panel gives it nanosecond timestamps before a frame (`frameStarted`) and after its buffer swap (`frameCompleted`), it publishes a whole number once a window reached a second, and `reset` (disable, unsafe or hidden surface, `removeNotify`, failure, context abandonment, disposal) forgets everything so the next sample never includes inactive time. The value is drawn as the first part of the stats strip (`<n> fps`, `-- fps` before the first sample); sampling adds no timer or render |
| `ViewDisplay`, `Shading` | The Shadows / Fog / Grid toggles and Lit / Unlit / Wireframe of one view, set on `SceneRenderer.display` and read on the next frame. Per view and never saved; the scene's own `fogEnabled` is untouched. Unlit gives every material a white ambient and no lights or shadow pass; Wireframe draws every content drawable (models, terrains, foliage OBJECT layers, skybox cube, water) as edges only with `glPolygonMode(GL_LINE)` |
| `SceneInputForwarder` | Keys (Q / W / E / F / D / Esc / Enter / Backspace / Delete) and the canvas mouse: into `SceneInteraction`, into the simulation while playing, the arrangement point menu |
| `RayControlBinding` | The view's ray feed, the mode the Properties switch shows and its listeners |
| `CloudControls`, `PlayControlsStrip`, `FoliagePaintControls` | The Clouds choice and fallback, the Play / Pause / Step / Stop buttons, the foliage brush and its paint session |
| `PlacementMenu` | An "Add ..." menu: whether it can be offered, and its choices (`lightMenu`, `assetMenu`, `pathMenu` of the panel) |
| `SceneViewState` *(lib-core-editor)* | What the user chose: selection, gizmo mode and hovered handle, the camera looked through, the drag/drop preview. The panel changes it; the renderer and the queries read it |
| `SceneInteraction` *(lib-core-editor)* | Mouse and key logic without Swing or GL, over a `SceneViewState` and `SceneQueries`: click → pick/select, drag → gizmo or orbit/pan (one `Gesture`: Idle, Dragging or Cancelled), Drop → a Y-only move |
| `FrameSnapshot`, `SceneQueries`, `SnapshotSceneQueries` *(lib-core-editor)* | What the last frame drew (camera copy, model boxes, terrain targets, `drawnVersion`), and the CPU-only questions asked of it: pick, ray, ground below, lowest point, gizmo handles and hits, drag start. Tested with hand-built snapshots |
| `SceneContentPass`, `SceneSkyPass` | The two passes `SceneRenderer` runs over `SceneViewState`: models, terrains, foliage and the gizmo; the sky, fog and clouds (it packs the snapshot it reads). GL only |
| `SceneViewToolbar`, `SceneGlLifecycle`, `SceneFrameDriver` | What `SceneViewPanel` delegates to: the toolbar and its control sync (Swing); the canvas, libGDX context and capabilities of one GL context (GL, EDT); the frame timer and idle throttle (Swing) |
| `SceneRenderer` | One frame: environment, skybox, grid, terrains, models, markers, highlight, gizmo. GL only: it publishes a `FrameSnapshot` after each frame and exposes `queries`. `GridModel` and `SelectionBox` build the grid and the highlight |
| `PlacedAssets`, `SceneModels`, `SceneTerrains`, `SceneSkybox` | Per-kind loaded assets (an `AssetView` each over the view's `ViewAssets`, whose `ProjectAssets` from `AssetLoading` hold the one `lib-core` `AssetStorage`) and per-entity instances (`PlacedEntities`); `SceneModels` and `SceneTerrains` extend `PlacedAssets` and `SceneSkybox` has the same `abandon` |
| `skybox` | `SunDirection`: the sun a procedural sky is lit from, from the scene's lights (`sunLight` is the light clouds dim). `SkyClock`: the view's sky time, which clouds drift by. `CloudViewState` (the toolbar's Clouds choice and the automatic fallbacks) and `CloudFrameBudget` (2 s over 33 ms per frame while volumetric clouds draw) are pure and per view; nothing is written. The cloud techniques and `SunOcclusion` are in `lib-core`'s `assets.sky.clouds`. The sky loaders, the HDR environment and the sky shaders are in `lib-core` (`net.nevinsky.abyssus.lib.core.assets.sky`) |
| `SceneMarkers`, `CameraFrustum` *(lib-core-editor)* | Camera body and frustum, light markers, and their pick bounds |
| `ScenePicker` *(lib-core-editor)* | Ray from a pixel, nearest hit over boxes and terrain heights (used by `SnapshotSceneQueries`) |
| Drop: `OrientedBox`, `TerrainRestHeight`, `ScenePicker.restHeight` *(lib-core-editor)* | Highest surface under a rotated box footprint; CPU-only bilinear terrain-cell maxima |
| `ScenePreview` *(lib-core-editor)* | Applies a drag or drop preview over the placements, and simulated poses (`withPoses`) while playing |
| `SceneExtensions.kt`, `SceneOverlayHost` | The `sceneOverlay` and `sceneSimulation` extension points and the per-view overlay list that switches off a failing overlay |
| `PlayState` | Play in one view without Swing or GL: `IDLE -> STARTING -> PLAYING <-> PAUSED -> IDLE`, plus `FAILED` |
| `gizmo` | Drawing (`GizmoDraw`) over the handle geometry (`GizmoHandles`), hit tests (`GizmoHit`) and drag math (`GizmoDrag`) of `lib-core-editor` |
| `SceneTransformWriter` *(lib-core-editor)* | A finished transform → `PositionComponent` (and camera) fields in the scene JSON |
| `GdxRuntime`, `GuardedGLCanvas` | The `Gdx.*` shim, and the canvas that refuses unsafe GL |
| `GlCanvasHost`, `GlFrameTimer` | The create, paint, release and abandoned sequence of one GL context, shared by the scene view (through `SceneGlLifecycle`), the Game UI canvas and the Import Model preview; and the `glSafe()`-guarded frame timer the last two use (GL, EDT) |

## Things that are not obvious

- **`Gdx.*` is shared by the whole IDE.** `GdxRuntime.withContext` installs this canvas's `Gdx.app` / `graphics` /
  `gl*` / `files` and the GLSL 150 prefixes under a lock, and restores the old values afterwards. Every libGDX call
  must be inside it. Clicks arrive outside it, so `ScenePicker.pickRay` unprojects by hand instead of using
  `Camera.getPickRay`.
- **Multisampled default framebuffer.** `glData(samples)` requests a GL 3.3 core canvas with 24-bit depth, swap
  interval 0 (dropped for the last attempt when the GLX server has no swap control, as Xvfb), and sample buffers only when the count is positive. Default 4× smooths raster edges and overlays; shadow,
  water and ray targets retain their own formats and the ray image retains its own accumulation. Test harness formats
  stay unchanged. Applying a level replaces only the native canvas through guarded `disposeCanvas`, reattaches input
  and rebuilds renderer resources in `initGL`; camera, selection, tools, look-through camera, Play and Ray Tracing mode
  stay in the view. The ray feed resets accumulation on context loss. Hidden views defer replacement until `addNotify`
  or a showing hierarchy event. A failure before `initGL` retries lower sample counts down to Off, then Off without swap interval, warns once per view,
  and leaves the requested preference intact. A renderer failure after `initGL`, or a failed Off format, uses `onFailure`.
- **GL only on a safe surface.** `GuardedGLCanvas.glSafe` needs the canvas showing, non-empty, not minimized, and
  stable for 250 ms. On macOS a zero-sized surface aborts the JVM. A canvas disposed while hidden drops its context
  without making it current, so its GL objects can't be released. macOS also stops sizing that canvas's native
  surface with the component, so `SceneViewPanel` replaces such an "abandoned" canvas when the view is shown again.
- **IDE overlays take precedence over the native surface.** `CanvasOverlayHost` detects overlapping Swing siblings
  above the scene, captures the displayed framebuffer once while GL is safe, and hides the native canvas. Swing paints
  that snapshot beneath the notification until overlap ends, then live rendering resumes through the surface safety
  gate. Rendering pauses during overlap; no continuous readback is performed. Removing or disposing the view stops
  overlay monitoring and releases the snapshot.
- **Asset loading lives in `lib-core`** (`projects/lib-core/README.md`). `AssetLoader.prepare` runs on a pool thread (IO and decoding,
  no GL). `build`, and `upload` for big textures, run on the render thread one slice per frame, inside this package's
  `GdxRuntime.withContext`. A new project gets a new cache, so a pool thread never prepares from a stale project. A
  failed asset is remembered and logged once, through the SLF4J `Logger` `AbyssusCore` gives `AssetLoading` (`AbyssusCore.assets`).
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
- **Play shows poses, never writes them.** With an available `sceneSimulation` provider, the toolbar
  has Play, Pause, Step and Stop. `PlayState` starts the provider's simulation from the scene's document text, the
  project folder and the selection. Each frame the panel copies the simulation's latest poses into
  `SceneViewState.poses`. `ScenePreviewLayers.posed` applies them over the authored placements, keeping each
  placement's scale, so drawing, picking, markers and overlays all see them. A drag preview applies on top (`ScenePreviewLayers.apply`). While a
  simulation is active, gizmos are off (`SceneViewState.gizmosEnabled`), Move/Rotate/Drop are disabled, and every key
  but Escape goes to the simulation and is consumed. Mouse buttons and moves go to it too, and the camera still
  orbits. Escape stops. `SceneFileEditor` stops play before scene edits or changes to project render inputs. A change only
  to `physicsEnabled` uses provider availability and leaves unrelated available simulations running. Closing the tab stops it too. A simulation that ends on
  its own (`FAILED`) returns the view to the authored poses; its provider shows the notification. A provider that
  throws is switched off for the view with one logged error.
- **Overlays draw twice a frame.** Each `sceneOverlay` provider gets one `SceneOverlay` per view. `drawOverlays` calls
  them inside `GdxRuntime.withContext`, after the markers with depth testing (`OverlayView.onTop` false), and again
  after the selection and gizmo without it. They see the content as drawn (poses and previews applied) and the
  scene's raw `ecs` (`SceneRenderParams.ecs`). They draw only through `LineSink` and own no GL. An overlay that throws
  is disposed and switched off for that view with one error naming its plugin. Its `actions()` (such as Show Physics)
  join the toolbar only while the provider is available. Both provider interfaces have default-true
  `isAvailable(project, file)`, preserving old compiled extensions. Settings and structural VFS events refresh availability and Play
  selection on the EDT; draw uses cached availability. Built-in physics requires an admitted project with
  `physicsEnabled: true` and draws typed collider/constraint geometry without Jolt or schemas. Asset outlines use
  the scene's native project. Disabling physics stops its active session, rejects late callbacks and restores
  authored poses, while other available providers continue working.
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

## Disabled entities and components

The view's content comes from `sceneContentOf`, which leaves out a disabled entity entirely and a disabled component's own
placement (see "Enabled flag" in `lib-core-editor/README.md`). So a disabled entity is not drawn, does not cast a shadow, is
not picked (a click goes through it to the enabled entity behind), has no gizmo and is left out of the ray snapshot. Selecting
it in the tree still shows it in Properties. A toggle is a scene file edit, so the view refreshes through
`AbyssusSceneEdited` like any other edit and needs no reload.

## Add Light

The toolbar's Add Light menu offers Directional, Sun and Spot through `AddLightGroup`. `SceneFileEditor` supplies
a `PlacementMenu` (the scene file's availability check and its choices); the panel supplies its current orbit target when
a choice is made.
The new entity is written as one Add Light command and selected in the Abyssus tree. Spot adds 5 to placement Y.
Unreadable scene text disables creation (and the editor shows its existing parse-error state).

The toolbar's Add Asset button (`add-asset`) works the same way through `AddAssetGroup`: `SceneFileEditor` passes
an `assetMenu` (available for a readable scene and a project with models or terrains), and the panel supplies the
orbit target. A view built without an `assetMenu` has no such button.

## Water authoring

The toolbar's Add Water button (`add-water`) offers Lake and Sea through `AddWaterGroup`; `SceneFileEditor` supplies a
`waterMenu` (readable scene whose `abyssus` data is an object) and the panel the orbit target. Water selection is a
separate identity from ECS ids: `SceneViewState.selection` is a `SceneTarget` (`Entity` or `Water`), `selectedId` reads
only an entity and `selectedWater` only a surface, so a water id never reaches an ECS writer. A click picks through
`SceneQueries.pickTarget`: the rectangle of an enabled, valid surface at its mean level, seen from above only, wins only
when it is nearer than the terrain or model under the cursor (`WaterHit`). Water offers the Move gizmo only (no rotate
handles, and `canDrop` is false); a drag previews through `SceneViewState.waterPreview` and `SceneFileEditor.applyWaterMove`
writes only the coordinates that changed, as one Move Water command. Esc restores the position and writes nothing, and a
surface deleted elsewhere clears the selection. The selected extent is outlined by `WaterOutline`. Water is never in the
ground lists (`groundHit`, `groundBelow`).

## Water rendering

`SceneRenderer.render` updates the frame once (assets, animation, previews, lights, shadow atlas) and plans the water.
`SceneWater` (`sceneview/water/`, one per view, created on the first frame that has water) owns the pass order; the pure
planning is `WaterPassPlanner` in `lib-core` (`core.water`). A frame with no valid, enabled surface in view above the camera
(`WaterScreenArea` measures what is in view; a camera at or below the level skips that surface) does nothing extra:
`renderContent` draws everything, as before.

With water the frame is: shadows, then one **reflection pass per reflected level** into `WaterResources`' targets
(at most two levels at half the viewport and at most 1024 a side, chosen by on-screen area with a 1.25 hysteresis so two
similar levels do not swap), then the main sky, grid and **opaque** content (`ContentParts.OPAQUE`: alpha-tested parts
included, blended parts left out) on the canvas, `captureScene` (a copy of the canvas colour made with
`glCopyTexSubImage2D` from whatever framebuffer is bound, so it is the opaque scene without editor overlays), the **scene
and terrain depth targets** (packed linear view depth, `ModelDepthShader` with `linearFar`, at most 2048 a side; terrain
alone makes a shoreline), the **surfaces** far to near (`WaterDrawable`: a bounded wave grid, vertex waves, `water.frag`),
then the blended models (`ContentParts.BLENDED`), then the overlays. The reflection draws through `MirroredCamera`
(`view * mirror`, clockwise front faces for the pass) with a world clip plane just below the mean level (the bias grows with
wave height and distance) on the model shaders (`ClipPlaneAttribute`) and on `TerrainShader` (`u_clipPlane`); grid, markers,
gizmos, water and blended models are never in a reflection or a depth target. The water shader samples the
reflection, the scene copy and the depths at the fragment's own screen position: Fresnel mixes the reflection with the
refracted scene, which fades into the tint with the distance light travels in the water (`clarity` is the distance scale,
`tint` sets the per-channel absorption), sun glints add to it, and foam grows where terrain depth says the bottom comes
within `foamWidth` of the level. A fragment is discarded when something opaque is nearer than the surface, or when the
scene behind it is land above the mean level, so crests never show over a beach; waves die out toward the shore, and
fine waves fade with the pixel footprint so distant water does not alias. The water is drawn with a polygon offset because
the window depth buffer is coarse at a distance, and the shader's own exact depth test decides the shoreline. Scene fog is
applied once, to the final water colour. Limits: above-water viewing only (no underwater look), reflections and refraction
show opaque and alpha-tested scene geometry and the sky only (no blended models and no other water), the sky fallback is
the sky colour (or the sky's lighting cube when it has one), and water neither casts nor receives geometry shadows.

Budget and failure rules: a level beyond the reflection budget, or whose pass failed, shows the sky. For a procedural
sky with clouds the view holds an `EnvironmentLease` only while some level needs that fallback and samples the cloudy
lighting cube (an HDR sky's cube needs none); the lease is released when no level falls back and with the view, and just
forgotten when the context is replaced. `WaterResources` allocates once per size, releases surplus reflection targets,
restores the caller's framebuffer, viewport and state after every pass (`lib-render`'s `GlRenderTarget` and
`ShadowGlState`, including the winding), refuses an
unusable size without retrying until the size changes, and is abandoned without GL calls on context loss. A shader or mesh
that cannot be made skips the water in that view (logged once, retried after the context is replaced); targets that cannot
be made leave plain tinted water; a failing surface is skipped while the rest draw. The animation time advances with the
frame's delta (`SceneRenderer.waterTime` stands it still, for tests). See `docs/ai/testing.md` for the GL checks.

## Path arrangements

The toolbar's Add Path Arrangement button (`add-path-arrangement`) lists the project's models through
`AddPathArrangementGroup` (`SceneFileEditor` supplies `pathActions` and `canAddPath` through `SceneViewHost`). Choosing one
calls `SceneView.startDrawing`, which puts `SceneInteraction` in drawing mode (a hint shows in the toolbar). The renderer
draws the line with a handle on each point and a cross with a post at every copy's ground height (`SceneMarkers.drawPath`);
no model is drawn in the preview. A finished arrangement shows its line, handles and crosses only while its control points are edited; otherwise it shows only its copies, and the line cannot be clicked.

Finishing writes through `SceneFileEditor.createArrangement`, a point drag or Remove Point through `editArrangement`,
and the Regenerate button (shown while an arrangement is selected) through `regenerateArrangement`. The ground comes from
`SceneView.groundHeights()` (the last drawn frame's terrains, answers kept per terrain data) and, when the view has no
frame yet, from `ArrangementHeights`. Clicking the curve in the edit mode adds a control point there (`PathHit.Line`). Remove Point is on the right-click menu of a point handle and is disabled at two points; Backspace or Delete removes the selected point in the edit mode under the same limit, and Drop to terrain on that menu puts it on the ground. While the edit is on, changes stay in `SceneViewState.pathDraft`, and the renderer lays the copies out along it at the line's own height (`ArrangementPreview`, so the models follow the points, in the air too, before Apply); Apply (`SceneView.applyPathEdit`) writes them, Close drops them, and Drop path to terrain (`dropPathToTerrain`) drops every point. The three buttons replace Edit in the Properties panel and go through `PathEditSession`.

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

## GL resource lifecycle

`RenderResources` owns the GL objects `SceneRenderer` makes for one context (the model batch and shader provider, `TerrainShader`,
the skybox, loading and corner overlays, the line batch and the grid model). `create()` registers each in the current
generation; `dispose()` calls `releaseAll()` (valid context only: newest first, one failing release does not stop the rest) and a
hidden canvas that lost its context calls `abandonAll()` through `create()`, which runs only the GL-free abandon hooks
(`SceneSkybox.abandon`, `CornerOverlay.abandon`), deletes nothing and starts the next generation. A handle of an older generation is
never released through the new context. Release and abandon stay separate operations, and this is not a service container: the
assets, shadows, water, foliage and cloud shadow map keep their own `abandon()` / `dispose()`. `RenderResourcesTest` counts release
against abandon with fake resources.

## Scene shadows

The shader side of the atlas is one GLSL file, `lib-gdx`'s `shader/shared/shadow_visibility.glsl`: the atlas uniforms,
`shadowCompare`, `shadowBilinear`, `shadowTileVisibility`, `pointShadowVisibility` (under `shadowAtlasFlag`) and
`directionalVisibility` (under `directionalVisibilityFlag`). The default and PBR model fragments and `terrain_editor.frag` (`lib-render`) pull it
in with `#pragma include "shadow_visibility.glsl"`, so a change to the filter is made once. `terrain_editor.frag` defines both flags
itself and `ShaderStorage.fragment` expands the include; the model shaders read through `ShaderSources.readAssembled`.

After applying drag previews, each frame updates model poses and terrain transforms once. `SceneShadows` captures
their renderables plus the drawn foliage's OBJECT layers (`SceneFoliage` as a `FoliageCastSource`; DETAIL layers are
left out), renders the depth atlas, and restores the caller's framebuffer, viewport and depth/blend/scissor
state before the sky, grid and color passes. Color uses those same poses and transforms. Models and terrain both
cast and receive; markers, grid, sky, selection outlines and gizmos do not cast. Each light's visibility multiplies
only its direct contribution, leaving other lights, ambient, HDR environment lighting and emissive output intact.
`ShadowCasterBounds` caches per-bone mesh boxes and transforms them with the current pose and node transform for
fitting and culling, so movement outside a model's initial pose does not clip its shadows. Skin weights use the
model runtime's normalized-weight convention.

`lib-render` owns `DepthSubmission` for both the atlas and water depth passes, `sunShadowBias` (with a guarded depth
range), and `cameraUp` (each host retains its pole threshold). The receiver-plus-caster fit stays in `ShadowProjection`.
The atlas uses `lib-gdx`'s `ShadowLightKind` directly. `ShadowResources` and `WaterResources` borrow their targets through
`GlRenderTarget`: atlas tiles override its viewport/scissor step, while water draws the whole target. Its
`ShadowGlState` restores caller state after success or failure; `CloudShadowMap` uses the same snapshot around its
draw. These helpers require the current safe canvas GL context and own none of the host's targets or shaders.

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
See `projects/lib-gdx/README.md` for the reusable atlas attribute and provider API.

Resources belong to one canvas and are created, rendered and disposed inside `GdxRuntime.withContext` on the
safe AWT render thread. A lost/hidden context abandons references without GL calls; recreation builds a fresh atlas
and depth shaders. Missing framebuffer support, insufficient texture size or fewer than twelve fragment texture
units disables shadows while keeping lighting. A failed depth pass disables shadows until recreation. Terrain
reserves unit 6 for the atlas, between its six layer textures and irradiance on unit 7 (unit 8 holds the cube a sky light fades to, bound only while fading); model shaders use their
texture binder. Atlas allocation and tile passes restore GL state even on failure.

## Terrain foliage

A `FOLIAGE` asset stands on one terrain. A terrain entity with a `FoliageComponent` names it, and `PlacementMapper`
*(lib-core-editor)* turns that into a `FoliagePlacement` (entity id, foliage folder, terrain folder, transform) in
`SceneContent.foliages`, which is the only place the scene reads it. `AssetLoading` registers `FoliageLoader` for
`MetaType.FOLIAGE`, so foliage loads like any other asset: `prepare` on the asset pool thread reads the meta, the
`layer-<id>.mask` masks and the `foliage.data` bake and declares the terrain and every layer model as dependencies
(the storage loads them first), and the build runs on the render thread and makes `FoliageDrawable` — one instance
buffer set per (model folder, layer kind), copies in their model's bind pose, frustum and distance culling, and
`objectBounds`, the world box of the copies an OBJECT layer draws.

Each frame, inside `GdxRuntime.withContext`, `SceneRenderer` calls `foliage.update(content.foliages, projectDir,
camera)` for the frame's content, and `foliage.draw` hands the renderables to the content batch after the models.
`update` asks `FoliageAssets` for the folders the placements name, leaves out a placement bound to another terrain or
one whose terrain cannot be read (the reason is logged once, and the terrain and models around it still draw),
schedules the generation the drawable needs, and culls each drawn foliage against the camera.

Generation (`FoliageScatter`, *lib-core-editor*) never runs on the render thread: `SceneFoliage` posts it to the IDE's
background executor and hands the result to its drawable at the next `update`. One generation runs at a time per
foliage, so a change that lands during one is picked up by the next; a stale or missing bake is generated whole, a
draft only over the chunks it touched (`mergeFoliageChunks`), and a result whose asset was rebuilt meanwhile is
dropped. Refusals (copy or candidate limit), failures and unreadable inputs are written once per drawable revision
instead of every frame, and one unusable foliage never hides the rest of the scene.

`FoliageDrafts` (a project service in `plugin/foliage`) holds each project's uncommitted drafts, one per foliage
folder: `SceneFileEditorProvider` passes it to the `SceneRenderer`, which passes it to `SceneFoliage`, and a draft's
settings and masks are preferred over the stored asset until it is discarded. The draft's own state, dirty chunks and
stroke merge are pure and live in `lib-core-editor`'s `foliage/FoliageDraft.kt`.

Foliage is drawn, and it is deliberately left out of everything else the view answers:

- **Picking and Drop** never see a copy: `publishSnapshot` copies model boxes and terrain targets only, `SceneMarkers`
  adds cameras and lights, and a copy rides its terrain entity, so a click through the trees picks the terrain.
- **Shadows:** `SceneFoliage` implements `FoliageCastSource` (in `shadows/ShadowCasters.kt`), and `SceneShadows.render`
  collects its OBJECT layers' instanced parts through `getRenderablesOf(OBJECT)`, fitted by the union of
  `objectBounds`. A DETAIL layer's copies never reach the depth pass; they only receive.
- **Ray tracing** never converts a copy: `RaySceneSnapshots` builds its snapshot from the models and terrains the
  content places, and `RaySceneAssets` leases no foliage, so the ray-traced image shows the scene without it. The
  view's `RayControl.foliageNote` says so — `propertiesSceneRayFoliage`, "Foliage is not ray traced." — through
  `SceneRayControls` into the `ray-tracing-foliage` label of the Properties panel's Ray Tracing section, while the
  shown scene has any.

## Sculpt Terrain

Sculpt Terrain is a brush mode of a view beside Paint Foliage, and the two never hold at once. `SculptTerrainControls` is the
toggle and the strip (tool, radius, strength, the options of the chosen tool; Shift inverts); like `FoliagePaintControls` it
sets a mode on the view's state (`SceneViewState.sculpt`, a `SculptMode`), which hides the gizmos, gives the left button to the
brush and ends when another entity is selected. The toggle is disabled with a reason as its tooltip when the entity is not a
terrain, the terrain data cannot be read or its sculpt state is unsafe (`SculptEditing` reads the files off the EDT).
The pick layer calls a `TerrainSculpt` (`pressed` / `dragged` / `released` / `cancelled`), which `SculptStrokeCommand` implements.

A stroke lives in the project's `SculptDrafts` (one `SculptDraft` per terrain folder) only while the button is down. The
command writes nothing before the release: it then plans the files with `SculptPlanner.commitStroke` and stages them
through `SculptTransactions` as one undoable `AssetTransaction`. An empty stroke writes nothing and adds no undo step.

Every view follows a stroke through `SculptViewSync` (called each frame after `terrains.update`): when a draft has a new
revision, the draft's changed rectangle of heights is copied **in place** into that view's own `TerrainData.heights`
(each view loads its own copy of the terrain) and `TerrainMesh.updateRegion` sends those vertices again (one-vertex border
for the normals; the whole buffer past half the grid). When a draft a view followed is gone, because the stroke was
committed or discarded, the view loads the terrain again from the files. Picking, foliage and ray snapshots read the
view's `TerrainData`, so they follow the new heights; a ray snapshot is dropped by the existing asset refresh when
`terrain.data` changes. The Stamp tool decodes its image with a libGDX `Pixmap` off the AWT thread (`StampImages`), no GL.

## Ray tracing scene conversion

The ray classes named here (`RaySceneSnapshots`, `RayViewFeed`, `RayViewRuntime`, `RayBackendService`, `RayModeState`) are in
`lib-core-editor`'s ray package; `RayIntegration`, `RayFramePresenter` and `SceneRayControls` stay in the plugin.

`RaySceneSnapshots` converts the same preview-applied `SceneContent`, `LightSet`, camera and environment that raster
draws into an immutable `RaySceneSnapshot`, from CPU asset companions only (never GL handles). `RaySceneDiff` classifies
what changed between frames (structure, transform, pose, camera, light, material, environment), and anything the
backend cannot represent becomes an explicit `RaySceneConversion.Fallback`. `RayModelPoses` copies each animated or
skinned entity's displayed pose on the render thread, after animations advanced, and `RayModelSkinning` (core)
deforms the shared source mesh per instance on a worker.

What the native renderer then draws, and its bounds, are in `projects/lib-raytracing/README.md`: per-light shadow rays, cutouts,
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
CPU `RaySkySnapshot` (HDR and cube skies) or, for a procedural sky (arbitrary asset GLSL), rendered by `RaySkyBaker` into
six faces on the render thread and resampled. That bake is a display-value sample of the sky as the view shows it, clouds
included (`raySkyBakeKey`): it is taken again when the sky, project, sun or cloud asset change and, for clouds with wind,
every 5 s of sky time (full-resolution, three-sample volumetric clouds), so a reflection or a ray miss shows the same clouds as the background, never a cloudless sky. It
is not the linear light of the sky: that reaches the rays as the six axis colours of the current sky light (blended while
it fades), when the sky has `lightsScene`. The clouds' shade on the sun reaches them as a `RaySunVisibility`: a 256²
read-back of the cloud shadow map, with its world-to-map transform, the sun's entity id and the uniform factor for
beyond the map (or just that factor when no map was made). It is copied again when the sun, cloud asset, uniform factor,
500 m target cell or (with wind) 2 s time bucket change (`raySunVisibilityKey`), and it multiplies only the sun's direct
contribution, once, on top of the sun's own shadow ray. A sky that is still loading or cannot be transferred shows the
background colour; it never fails the view. Limits: a custom `sky.frag` that departs from the asset's atmosphere
parameters shows its own look in the background and the bake but the physical atmosphere in the sky light; rays see
no cloud shade on other lights; and baked clouds are not volumetric self-shadowed. The mode reads renderer state
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

The editor provider obtains loading and scene shaders from `AbyssusCore.assets`, JSON from `documents`, and native
ray collaborators from `ray`. These groups initialize independently; the ray group closes only resources it created.

## Procedural sky light and its cross-fade

A `SKYBOX_PROCEDURAL` asset with `additional.lightsScene: true` lights models and terrain like an HDR sky. Each frame
`SceneRenderer` calls `SceneSkybox.updateLighting`, which advances the sky's `ProceduralSkyLighting` (see
`docs/ai/architecture.md`); `SceneSkybox.environment` returns the completed light, so `SceneAmbient.of` replaces the ambient
color with it, and until the first build is done the ambient color is used. When the sun or clouds change, a new
environment is built in the background and fades in over about one second: `SceneSkybox.fadingTo` gives the next
environment and the weight (0 to 1). `renderContent` passes them on in two ways:
- models get an `EnvironmentLightAttribute` whose `fadeTo` / `weight` carry the second environment. `PbrModelShader` compiles a
  program with `environmentFadeFlag` (one more irradiance and specular fetch) only while an environment fades, and
  `ModelShader` blends the six axis colors on the CPU;
- `TerrainShader` (the scene view's adapter over `lib-render`'s `TerrainPass`: it turns the camera, `LightSet` and `FogParams` into `FrameInputs` and `TerrainLighting.Editor`) binds the next irradiance cube on unit 8 and `terrain_editor.frag` mixes the two by `u_skyFade`. Without a
  fade, or without a sky, the shader is bound exactly as before.

The light follows the asset's atmosphere parameters, not a custom `sky.frag` that departs from them.

## Cloud shadows

While the scene's procedural sky draws clouds some technique can still draw, the sun (the brightest usable directional
light) is shaded by a `CloudShadowMap`: a 512² `R16F` texture covering a 4000-unit square (metres) centered on the orbit
target, redrawn every frame at the sky's own time and cloud asset, so patches drift with each band's wind and follow a
cloud asset reload. `planSunShading` picks the map or the uniform `SunOcclusion` dimming, never both: with the map
the sun keeps its full color in `litLights` and `DirectionalVisibilityAttribute` carries the map, the sun's directional
index, the sun-ray world-to-map transform (so a tall model is looked up at its ground footprint), a 10% edge fade, and
the uniform factor for beyond the square. `ModelShader`, `PbrModelShader` and `terrain_editor.frag` (unit 9) multiply only that
light, on top of its atlas shadow. No usable sun, a sun outside the rendered light budget, no clouds, exhausted cloud
techniques or a map that could not be made (logged once to the `sceneview` logger) leave the uniform dimming as before.

