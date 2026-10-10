# Glossary

| Term | Meaning |
|---|---|
| **Project** | A native Abyssus project: an `.abss` file with `scenes/` and `assets/` beside it. Shown as a top-level node of the Abyssus view. |
| **Scene** | A `.scene` file: environment settings (ambient light, fog, skybox) plus an `ecs` block of entities. |
| **Asset** | A folder under `assets/` described by its `meta.json` (`type`, `uuid`, `additional`). Scenes name assets by folder; asset references use `uuid` or, for foliage, terrain/model folder names. |
| **Unused asset** | An asset no scene of its project reaches (see `docs/ai/file-formats.md`). Grayed and tagged `unused` in the tree. |
| **ECS** | Entity-component-system. In a scene file, `ecs.entities.<id>.components.<Name>Component`. In code, the Ashley-based `core.ecs` package used by games and the Play host; the editor decodes scene JSON directly. |
| **Entity id** | The key under `ecs.entities`. Picking, tree selection and transform writes all use it. |
| **Component class name** | The key of a component in a scene's `components`: the fully qualified class name or the short name of a built-in or registered component class. `EcsLoader` binds the value into that class with Jackson, `EcsWriter` writes it back. Any other key is carried raw. |
| **Placement** | What the scene view draws for an entity (`AssetPlacement`, `FoliagePlacement`, `LightPlacement`, `CameraPlacement`, gathered in `SceneContent` in `lib-core-editor`), taken directly from the `ecs` JSON. |
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
| **Foliage asset** | A `FOLIAGE` asset bound to one terrain folder: ordered model-scatter layers in `meta.json`, optional density masks and a derived `foliage.data` bake. A terrain entity displays it through `FoliageComponent.assetName`. |
| **Foliage layer** | Scatter settings for one or more weighted model assets: density, seed, scale, normal alignment and optional height/slope limits. OBJECT layers cast and receive shadows; DETAIL layers receive shadows and stop at their draw distance. |
| **Foliage copy** | One generated model instance, positioned in terrain-local X/Z with yaw and scale. Its height follows the displayed terrain. It is drawn through GPU instancing and cannot be selected individually. |
| **Density mask** | One byte (0–255) per cell in a layer's `layer-<id>.mask`, scaling its density from empty to full; a missing mask means full density. Paint Foliage edits it with a circular brush. |
| **Foliage bake / fingerprint** | `foliage.data` stores deterministic copies grouped by chunk and a fingerprint of the scatter inputs. A mismatch, missing or corrupt bake is stale: the view regenerates copies and Re-bake saves the cache. |
| **Foliage draft** | Uncommitted settings and masks shared by the Properties panel, the brush and every open Scene view through `FoliageDrafts`. Apply or stroke release commits it; Cancel discards it. |
