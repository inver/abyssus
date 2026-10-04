# abyssus

![Build](https://github.com/inver/abyssus/workflows/Build/badge.svg)

An independent libGDX scene editor in the IntelliJ Platform:
browse a project's scenes and assets, inspect asset metadata, and view and lay out a scene in a 3D view.
Contributors and coding agents: start at [AGENTS.md](AGENTS.md).

<!-- Plugin description -->
Edit native Abyssus projects and libGDX scenes in the IDE.

Abyssus format version 1 requires `"format":"abyssus"` and `"formatVersion":1` in every `.abss`, `.scene` and
asset `meta.json`. This breaks compatibility with earlier unmarked files and projects from other editors. Unsupported files
remain readable in the text editor; plugin editing and loading are refused. No importer or automatic migration is included.

- **Abyssus view**: a project tree of `.abss` projects, their scenes, entities and assets, with unused assets marked.
  Toggle scene options, rename scenes and choose a scene's skybox from the tree.
- **Abyssus Properties**: the `meta.json` of the selected asset, with skybox face previews; terrain size, texture
  repetition and textures, cube skybox faces and procedural sky parameters can be edited, terrain heights can be generated
  from seeded noise, and **New Terrain** on the Assets node creates a terrain asset.
- **Scene view**: a 3D view of a `.scene` with its models, animations, terrain, skybox, lights and cameras, with an optional GPU Ray Tracing mode. Select
  objects, move and rotate them with gizmos (saved to the scene file, undoable), and look through a scene camera.
- **Ray Tracing settings**: a selected scene's Properties switch turns Ray Tracing on for its open views (not saved), and
  its target samples, ray budget and reflection and refraction depths are saved in the scene and undoable. A model
  entity's PBR materials can be given Transmission and IOR to render as glass; these overrides apply to that entity only.
  Glass must be a closed solid, and anything Ray Tracing cannot draw falls back to the normal renderer with the reason.
<!-- Plugin description end -->

## Abyssus view

The Project tool window has an **Abyssus** view listing every `*.scene` and `*.abss` file under the
content roots (including excluded folders) as a flat list. Matching is exact and case
sensitive (`.SCENE` and `.scene.bak` are ignored).

- `*.scene` - JSON scene (`id`, `name`, `ambientLight`, `fog`, `skyboxName`, `ecs`, ...). The node
  expands into those properties; nested objects and the `ecs` tree expand recursively.
- `*.abss` - JSON project (the top-level node of the view; no folder nodes are shown) (`name`). Its scenes are the `*.scene` files in the `scenes` folder next
  to it, listed by file name and expandable inline (display only, no navigation).
- A property gated by an `xxxEnabled` option (e.g. `fog`, `skybox`) shows an eye at the right of
  its row; click it to flip the option in the file (undoable). The scene's `skyboxName` is shown as `skybox`.
- A project scene's `skybox` row has a **Choose** button. It opens **Choose a skybox**: the project's `SKYBOX` assets
  with their face count and format, how many scenes use each (or `unused`), a name filter and a **None** entry.
  **Assign** writes the chosen folder name to the scene's `skyboxName` (undoable); **Cancel** writes nothing.
- A scene under a project is labelled `Name (id)`; right-click it and choose **Rename Scene...** to change its name.
- A project also lists its `assets`: one entry per sub-folder of the `assets` folder next to the `.abss`
  file, with the `type` and `uuid` from the folder's `meta.json`. An asset no scene reaches is grayed and
  marked `unused`. A scene reaches an asset by folder name through `assetName` and `shaderKey` values in its
  `ecs` and through `skyboxName`; a reached asset in turn reaches the assets its `meta.json` references by
  `uuid` (terrain `splatMap`/`splatBase`/`splatR`/`splatG`/`splatB`/`splatA`, model `materials`).
  A `shaderKey` with no matching folder is a bundled editor shader and is ignored. Files named inside a
  `meta.json` (textures of a material, shader sources) are not followed.
- A project's `scenes` and `assets` rows are labelled `Scenes` and `Assets` with their count. In a scene, `ecs` shows its
  entity count and lists its entities directly, each named by its `NameComponent` (the id when it has none) with its
  component count; an entity expands into its components (`Position`, `Render`, ...), each with its own icon.
- The view's toolbar options have **Show Only Unused Assets**, which limits the `Assets` list to unused assets
  (remembered per project). Under the tree a footer reads `N scenes · N assets · N unused`, summed over the projects
  shown and unaffected by that filter.
- Unreadable files stay in the tree with a placeholder. Right-click an entity and choose **Add Component...** to add a
  component it lacks (Name, Type, Parent, Position, Camera, Light, Point2Point or Render; a Render component asks for a
  model or terrain of the project), or right-click a component and choose **Remove Component**. Components the plugin
  does not model (`Pickable`, `Dependencies`, ...) cannot be removed. The eye, **Rename Scene...**, **Choose**, and
  these component actions and **Add Light** on a scene row write the file as undoable edits.

## Abyssus Properties panel

The **Abyssus Properties** tool window (right side) shows the Meta of the asset selected in the Abyssus view: a header
with the asset's type icon, name and `<type> asset`, then a Name / Value table of its `meta.json` (`format`, `formatVersion`, `version`,
`lastModified` as a date-time, `uuid`, `type`, then the fields of `additional` under an `additional` heading). Every
field in the file is listed, whatever the asset type; a list shows its item count. A skybox also shows its six faces
under **Face previews**. Selecting anything that is neither an asset, an entity nor a component (a scene, the project
file) shows a "Nothing to show" message. The panel refreshes when the asset's `meta.json` or a face image changes, and
selecting an asset never writes a file.

Some properties are editable, and the header then says that an edit changes every instance that uses the asset:

- **Terrain:** `size` (a whole number above zero), `uv` (a number above zero) and the six texture references
  (`splatMap`, `splatBase`, `splatR`, `splatG`, `splatB`, `splatA`), chosen from the project's textures or **None**.
- **Cube skybox:** the six faces `top`, `bottom`, `left`, `right`, `front`, `back`, chosen from the images inside the
  asset's own folder.
- **Procedural sky:** `planetRadius`, `atmosphereRadius`, `betaRayleigh` (three numbers), `betaMie`, `heightRayleigh`,
  `heightMie`, `mieG` and `sunIntensity`. A parameter the file omits shows its default and stays omitted until you
  change it. The atmosphere radius must exceed the planet radius, `mieG` lies between -1 and 1.

Type a value and press Enter (or leave the field), or pick from the list. A value that does not fit goes back to the old
value with the reason beside the field, and the file is not touched. Identity fields (`format`, `formatVersion`, `uuid`, `type`, `version`,
`lastModified`), file names and every other asset type (models, HDR skies, and so on) stay read only. Each change is one
undoable edit of that asset's `meta.json` that keeps its formatting and every other value; the **Undo** and **Redo**
buttons in the header (or Ctrl+Z / Ctrl+Shift+Z with the panel focused) apply it.

### Generating terrain

A terrain's properties end with **Terrain generation**: its resolution (read only: regenerating never changes it), the
state of its recipe, and the settings of the noise: **Seed**, **Feature size** (world units of the biggest hills),
**Min height** and **Max height**, **Octaves** (1 to 8), **Persistence** (0 to 1) and **Lacunarity** (1 to 4).
**Regenerate preview** draws the heights as a grayscale picture (black is the minimum height, white the maximum) without
writing anything; **Randomize seed** only changes the seed. Changing any setting discards the preview, and **Apply**
is enabled only for a finished preview of exactly the current settings. **Apply** replaces the terrain's height data and
writes the recipe beside it, as one undoable edit (Undo and Redo in the properties header); the terrain's `meta.json`,
resolution, size, textures and the scenes that use it are left alone, and no object moves, so objects placed at the old
height may need Drop. **Cancel**, selecting something else or closing the panel discards the draft without writing.
The preview refuses to be applied if the terrain's files or unsaved metadata changed meanwhile: generate a new one.

The same seed and settings always give the same heights, at any resolution. The settings are saved as a recipe,
`abyssus-terrain.recipe.json`, in the terrain's folder: a terrain loads
without it. The panel restores the settings from a recipe that still matches the heights, and shows why when it does not
(missing, malformed, an unknown version, or heights, size or resolution that changed) and offers the defaults instead.
Heights that are not a square grid of 2 to 255 per side cannot be regenerated.

### New Terrain

Right-click an **Assets** node and choose **New Terrain...**: pick a folder name (it must be new and stay inside
`assets`), the world size, a resolution from 2 to 255 and the noise settings, generate a preview and **Create**. The
terrain is written as a native Abyssus terrain asset (`meta.json` with a fresh `uuid`, `terrain.data`, and the recipe), the view
refreshes and the new asset is selected. Nothing is placed in a scene and no scene or project file is changed, so the asset
is marked unused until you add it to a scene. Undo removes the asset again, and Redo brings back the same files and
`uuid`; Undo refuses while a scene or another asset uses it, or something was added to its folder.

Select an **entity** to see all its components, or one **component** to see only that one. Each component the plugin
models lists its fields with an editor: type a value and press Enter (or leave the field) to save it, or pick from the
list. A value that does not fit (text for a number, an unknown entity, a parent that would make a loop) goes back to the
old value with the reason beside the field and the file is not touched. **Add component** lists the kinds the entity
lacks and each section has a **Remove** button. Unmodeled components are shown as read-only JSON. Every change is one
undoable edit of the scene file.

## Scene view

Open a `*.scene` and switch to its **Scene View** tab (or click *View* in the Abyssus view). The left mouse button
orbits, the right button pans, the wheel zooms; the view starts from the project's `mainCamera`. It draws, from the
project's `assets` folder beside the `.abss`:

- the scene's **models** (`RenderComponent` entities of type `MODEL`), textured, at each entity's position,
  rotation and scale; a model that has animations plays its first one on a loop;
- the scene's **terrain** (height data and splat textures of a `TERRAIN` asset);
- the **skybox** named by `skyboxName` when `skyboxEnabled`;
- the **light entities** (directional and point; a spot light is drawn as a point light) on top of the scene's
  ambient light and fog, each with a small marker (and a direction line for directional and spot lights);
- the **camera entities**, each as a small body with its view frustum (near, far, field of view), pointing at its
  `lookAtId` entity when it has one.

A click (without dragging) on a model, the terrain, a camera or a light selects it in the view and selects that
entity's row (`ecs/entities/<id>`) in the Abyssus view; a click on empty space clears the view's selection. An asset
that is missing or cannot be read is skipped and logged; the rest of the scene still draws.

The selected object shows a gizmo. **Move** (W) shows X/Y/Z arrows, **Rotate** (E) X/Y/Z rings; both are also in the
view's toolbar. Dragging a handle moves or rotates the object along that world axis, live; releasing writes its
`PositionComponent` `localPosition` / `localRotation` (and a camera's `position` / `viewPointPosition`) to the scene
file as one undoable edit, keeping the file's formatting. Esc cancels a drag. A camera aimed at a `lookAtId` entity
and a point light have no rotate rings. Dragging anywhere else orbits or pans as usual.

**Ray Tracing** (experimental, off by default) renders the scene on the GPU with ray traced shadows from every
shadow-casting light, reflections on PBR materials (including geometry outside the picture), the scene's sky, fog and
alpha-tested leaves and blended surfaces. It has no button in the Scene View: select a scene in the Abyssus view and tick
**Ray Tracing** under *Rendering* in **Abyssus Properties**. That flips the open Scene View (opening it first if needed) and
shows the status, the reason it is unavailable or failed, and **Retry**. It needs a GPU with hardware ray tracing: Apple
silicon with Metal on macOS, or a Vulkan 1.2 device with ray queries on Windows and Linux. Where it is unavailable the switch
is disabled and says why; if it fails, the view returns to the normal renderer. Selecting, moving and rotating objects, the
camera and the gizmos work as usual, and the scene file is never written by switching it on or off.
`-Dabyssus.raytracing.backend=off` disables it for the IDE session.

**Add Light** in the toolbar creates a Directional light, Sun or Spot at the current orbit target and selects it.
The same menu on a scene row places it at the origin; Spot sits 5 units above that point. A Sun starts warm and
brighter, with a low direction. Its light component's **Range** field sets a positive reach (default 100).
Each creation is one undoable scene edit. These new light entities use the plugin's own component structure.

For a spotlight, Properties also offers **Cone angle (degrees)** for its full beam width and **Edge softness (%)**
for the inward edge fade. Defaults are 45 degrees and 20 percent. Each accepted edit is saved in the scene and can
be undone; resetting a default omits its saved field. These beam fields are part of the native scene contract.

The toolbar's camera selector (**Free camera** and the scene's cameras by name) renders the view through a camera
entity; orbit, pan and zoom pause until **Free camera** is chosen again.

### Physics (Abyssus Physics plugin)

**Abyssus Physics** is a separate plugin, built from `physics-plugin/`, that depends on Abyssus. With it installed:

- **Add Component** offers **Rigid body**, **Collider** and **Constraint**. They are edited like other components,
  and sizes and masses must be greater than 0. Without the plugin, these components show as read-only JSON and are
  kept unchanged.
- **Show Physics** in the Scene View toolbar (off when the view opens) draws each collider as a wireframe: green for
  dynamic, blue for kinematic, grey for static. A height field is drawn as its terrain's outline. Each constraint is a
  line between its anchors; a rope that is longer than its maximum is drawn dashed. The selected entity's physics is
  brighter and drawn through its model.
- **Play**, **Pause**, **Step** and **Stop** run the scene's physics in a separate process. Play uses the scene as
  the editor holds it, unsaved text included. Simulated poses are shown but never written: Stop, Esc, editing the
  scene or closing the tab returns the view to the scene as the document holds it. While playing, gizmos are off,
  and keys and mouse buttons go to the game. If the process dies, one notification shows its last output and the IDE
  keeps running.
- A game makes Play run its own code by exporting `abyssus/play.json` (see `physics/README.md`). Without that file,
  Play runs physics alone.

Jolt, the physics engine, is loaded only by the play process, never by the IDE.

The model runtime (Assimp import, the model/mesh/shader classes with 32-bit indices) is the `gdx-model` module, a
plain JVM library reusable in other libGDX projects; see [source provenance](docs/third-party/gdx-model-origin.md)
for its origin and license. The GL
render tests are opt-in: `./gradlew test -Dabyssus.glTests=true` (opens a window).

To support another format, implement `net.nevinsky.abyssus.dto.AssetReader` and return it from
`AssetReader.forExtension`.

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "abyssus"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/inver/abyssus/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


## License

Licensed under [Apache-2.0](LICENSE) (SPDX-License-Identifier: Apache-2.0).

Files under `gdx-model` derived from [libGDX](https://github.com/libgdx/libgdx) retain their original
Apache 2.0 headers.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
