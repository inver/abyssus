# sceneview

The **Scene View** editor tab of a `.scene`: a libGDX render on an LWJGL3-AWT GL canvas inside a Swing panel, with
picking, camera markers, look-through, move/rotate gizmos and Drop. Required behavior: `openspec/specs/scene-*`.

## Pieces

| Class | Role |
|---|---|
| `SceneFileEditor` / `SceneFileEditorProvider` | The tab. Re-reads params on document/VFS changes; writes transforms via `editSceneJson`; `DocumentReferenceProvider` for undo |
| `SceneParamsSource` | Scene + project `mainCamera` → `SceneRenderParams`, from unsaved editor text when present |
| `SceneContent` | `ecs` JSON → placements: models, terrains, lights, cameras, skybox |
| `LightSet`, `SpotCone` | Deterministic light selection and CPU cone/range attenuation math |
| `shadows/` | Per-context atlas, stable tile allocation, fitted light cameras and shared model/terrain depth pass |
| `SceneView` | Interface of the view, so tests can pass a fake (`viewFactory`) |
| `SceneViewPanel` | Swing panel: GL canvas, Swing `Timer` frame loop, toolbar, keys (W / E / D / Esc) |
| `SceneInteraction` | Mouse and key logic without Swing or GL: click → pick/select, drag → gizmo or orbit/pan, Drop → a Y-only move |
| `SceneRenderer` | One frame: environment, skybox, grid, terrains, models, markers, highlight, gizmo; `pick` |
| `SceneModels`, `SceneTerrains`, `SceneSkybox` | Per-kind loaded assets (a `core` `SceneAssets` each, from `AssetLoading`) and per-entity instances (`PlacedEntities`) |
| `skybox/` | `SunDirection`: the sun a procedural sky is lit from, from the scene's lights. The sky loaders, the HDR environment and the sky shaders are in `core` (`net.nevinsky.abyssus.assets.sky`) |
| `SceneMarkers`, `CameraFrustum` | Camera body and frustum, light markers, and their pick bounds |
| `ScenePicker` | Ray from a pixel, nearest hit over boxes and terrain heights |
| Drop: `OrientedBox`, `TerrainRestHeight`, `ScenePicker.restHeight` | Highest surface under a rotated box footprint; CPU-only bilinear terrain-cell maxima |
| `ScenePreview` | Applies a drag or drop preview over the placements |
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
- **Asset loading lives in `core`** (`core/README.md`). `AssetCache.prepare` runs on a pool thread (IO and decoding,
  no GL). `build`, and `advance` for big textures, run on the render thread one slice per frame, inside this package's
  `GdxRuntime.withContext`. A new project gets a new cache, so a pool thread never prepares from a stale project. A
  failed asset is remembered and logged once, through the `AssetLog` `AbyssusCore` gives `AssetLoading`.
- **The view reads JSON, not the ECS engine.** Placements come straight from the `ecs` JSON. `ParentComponent` is
  ignored, and `local*` values are drawn as world values; drags write them the same way.
- **Drags preview, then write once.** During a drag `ScenePreview` overrides the dragged entity's placement. On release
  one `editSceneJson` command ("Move Entity" / "Rotate Entity") writes it. The document change re-reads params, and
  the override stays until they arrive so the object doesn't jump back.
- **Objects without rotation.** A camera whose `lookAtId` resolves, and a point light, get Move handles only.
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
