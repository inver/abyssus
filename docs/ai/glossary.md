# Glossary

| Term | Meaning |
|---|---|
| **Project** | A native Abyssus project: an `.abss` file with `scenes/` and `assets/` beside it. Shown as a top-level node of the Abyssus view. |
| **Scene** | A `.scene` file: environment settings (ambient light, fog, skybox) plus an `ecs` block of entities. |
| **Asset** | A folder under `assets/` described by its `meta.json` (`type`, `uuid`, `additional`). Scenes name assets by folder; assets name each other by `uuid`. |
| **Unused asset** | An asset no scene of its project reaches (see `docs/ai/file-formats.md`). Grayed and tagged `unused` in the tree. |
| **ECS** | Entity-component-system. In a scene file, `ecs.entities.<id>.components.<Name>Component`. In code, the Ashley-based `runtime.ecs` package used by games and the Play host; the editor decodes scene JSON directly. |
| **Entity id** | The key under `ecs.entities`. Picking, tree selection and transform writes all use it. |
| **Component class name** | The key of a component in a scene's `components`: the fully qualified class name or the short name of a built-in or registered component class. `EcsLoader` binds the value into that class with Jackson, `EcsWriter` writes it back. Any other key is carried raw. |
| **Placement** | What the scene view draws for an entity (`AssetPlacement`, `LightPlacement`, `CameraPlacement`, gathered in `SceneContent`; both in `editor-core`), taken directly from the `ecs` JSON. |
| **Abyssus view** | The Abyssus pane of the Project tool window: the project / scene / asset tree. |
| **Abyssus Properties** | The tool window for asset metadata and supported asset edits, entity/component fields, and a selected scene's Ray Tracing switch and saved settings. |
| **Scene view** | The second editor tab of a `.scene`: a libGDX render on an LWJGL3-AWT GL canvas. |
| **Eye toggle** | The eye icon on a row gated by an `<x>Enabled` boolean. A click flips the boolean in the file. |
| **Skybox chooser** | The "Choose" button on a project scene's `skybox` row. It opens a dialog listing the project's `SKYBOX`, `SKYBOX_PROCEDURAL` and `SKYBOX_HDR` assets. |
| **Gizmo** | The handles on the object selected in the scene view: X/Y/Z arrows (Move, key W) or rings (Rotate, key E). A drag previews live and writes on release. |
| **Look-through camera** | Rendering the scene view from a camera entity, chosen in the view's camera selector, instead of the free orbit camera. |
| **Marker** | The line drawing of a camera (body and frustum) or a light (octahedron and direction line), which makes them visible and clickable. |
| **Orbit camera** | The scene view's free camera (`OrbitCamera`): the left button orbits, the right button pans, the wheel zooms. It starts from the project's `mainCamera`. |
| **Stable-size gate** | `StableGate` in `GuardedGLCanvas`: GL is used only after the canvas has been on screen with a non-zero size for 250 ms, because macOS resizes the native surface late. |
| **`GdxRuntime` context** | The per-canvas `Gdx.*` shim installed only for the duration of `GdxRuntime.withContext`. |
| **`editSceneJson`** | The single path for writing a scene file: one undoable command that keeps formatting and number text. |
| **Ray Tracing mode** | Optional GPU renderer, enabled per open view from scene Properties. The enable switch is transient; quality limits and per-instance optical overrides are scene data. |
| **Play host** | A separate JVM process running physics and optional game systems, sending transient poses to the editor over a loopback socket. Jolt never loads in the IDE process. |
| **OpenSpec change / capability** | A planned change under `openspec/changes/`; a capability is one behavior area under `openspec/specs/`. |
