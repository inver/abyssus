# sceneview

The **Scene View** editor tab of a `.scene`: a libGDX render on an LWJGL3-AWT GL canvas inside a Swing panel, with
picking, camera markers, look-through and move/rotate gizmos. Required behavior: `openspec/specs/scene-*`.

## Pieces

| Class | Role |
|---|---|
| `SceneFileEditor` / `SceneFileEditorProvider` | The tab. Re-reads params on document/VFS changes; writes drags via `editSceneJson`; `DocumentReferenceProvider` for undo |
| `SceneParamsSource` | Scene + project `mainCamera` → `SceneRenderParams`, from unsaved editor text when present |
| `SceneContent` | `ecs` JSON → placements: models, terrains, lights, cameras, skybox |
| `SceneView` | Interface of the view, so tests can pass a fake (`viewFactory`) |
| `SceneViewPanel` | Swing panel: GL canvas, Swing `Timer` frame loop, toolbar, keys (W / E / Esc) |
| `SceneInteraction` | Mouse and key logic without Swing or GL: click → pick/select, drag → gizmo or orbit/pan |
| `SceneRenderer` | One frame: environment, skybox, grid, terrains, models, markers, highlight, gizmo; `pick` |
| `SceneModels`, `SceneTerrains`, `SceneSkybox` | Per-kind asset loading and per-entity instances on top of `SceneAssets` / `AssetCache` |
| `SceneMarkers`, `CameraFrustum` | Camera body and frustum, light markers, and their pick bounds |
| `ScenePicker` | Ray from a pixel, nearest hit over boxes and terrain heights |
| `ScenePreview` | Applies an in-progress drag over the placements |
| `gizmo/` | Handle geometry (`GizmoHandles`), hit tests (`GizmoHit`), drag math (`GizmoDrag`), drawing (`GizmoDraw`) |
| `SceneTransformWriter` | A finished drag → `PositionComponent` (and camera) fields in the scene JSON |
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
- **Asset loading has two steps.** `AssetCache.prepare` runs on a pool thread (IO and decoding, no GL). `build`, and
  `advance` for big textures, run on the render thread one slice per frame. A new project gets a new cache, so a pool
  thread never prepares from a stale project. A failed asset is remembered and logged once.
- **The view reads JSON, not the ECS engine.** Placements come straight from the `ecs` JSON. `ParentComponent` is
  ignored, and `local*` values are drawn as world values; drags write them the same way.
- **Drags preview, then write once.** During a drag `ScenePreview` overrides the dragged entity's placement. On release
  one `editSceneJson` command ("Move Entity" / "Rotate Entity") writes it. The document change re-reads params, and
  the override stays until they arrive so the object doesn't jump back.
- **Objects without rotation.** A camera whose `lookAtId` resolves, and a point light, get Move handles only.
- **HiDPI:** mouse positions are Swing pixels; the framebuffer can be larger. `ViewSize` converts between them for
  picking and gizmo hits.
