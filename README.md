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
  from seeded noise, **New Terrain** on the Assets node creates a terrain asset, **Import Model** turns an OBJ, FBX,
  3DS, DAE, glTF or GLB file into a model asset with a live preview, and **Import FlightGear Aircraft** turns an
  aircraft from a FlightGear `.zip` into a model asset.
- **Scene view**: a 3D view of a `.scene` with its models, animations, terrain, skybox, lights and cameras, with an optional GPU Ray Tracing mode. Select
  objects, move and rotate them with gizmos (saved to the scene file, undoable), and look through a scene camera.
- **Terrain foliage**: **New Foliage...** creates layered scatter settings for trees, rocks and grass;
  **Add Foliage...** attaches them to a terrain entity, and **Paint Foliage** paints or erases their density, with Undo.
- **Built-in Physics**: select a project’s `.abss` file and enable **Physics** in Properties for editable physics
  components, collider/constraint overlays and Play in a separate process.
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
- A project scene's `skybox` row has a **Choose** button. It opens **Choose a skybox**: the project's cube, procedural and HDR sky assets
  with their format and preview information, how many scenes use each (or `unused`), a name filter and a **None** entry.
  **Assign** writes the chosen folder name to the scene's `skyboxName` (undoable); **Cancel** writes nothing.
- A scene under a project is labelled `Name (id)`; right-click it and choose **Rename Scene...** to change its name.
- A project also lists its `assets`: one entry per sub-folder of the `assets` folder next to the `.abss`
  file, with the `type` and `uuid` from the folder's `meta.json`. An asset no scene reaches is grayed and
  marked `unused`. A scene reaches an asset by folder name through `assetName` and `shaderKey` values in its
  `ecs` and through `skyboxName`; a reached asset in turn reaches the assets its `meta.json` references by
  `uuid` (terrain `splatMap`/`splatBase`/`splatR`/`splatG`/`splatB`/`splatA`, model `materials`), and a reached
  procedural sky reaches the cloud asset (`CLOUDS`) its `clouds` names. A reached foliage asset reaches its terrain and its layers' models by folder name. Cloud and foliage assets have their own icons.
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
  model or terrain of the project), or right-click a component and choose **Remove Component**. **Add Component...** on
  a scene's **ecs** row starts a new entity instead: the chosen component goes into a new entity named `Entity <id>`,
  which is then selected. Components the plugin
  does not model (`Pickable`, `Dependencies`, ...) cannot be removed. The eye, **Rename Scene...**, **Choose**, and
  these component actions, **Add Light** and **Add Asset** on a scene row write the file as undoable edits.

## Abyssus Properties panel

The **Abyssus Properties** tool window (right side) shows the Meta of the asset selected in the Abyssus view: a header
with the asset's type icon, name and `<type> asset`, then a Name / Value table of its `meta.json` (`format`, `formatVersion`, `version`,
`lastModified` as a date-time, `uuid`, `type`, then the fields of `additional` under an `additional` heading). Every
field in the file is listed, whatever the asset type; a list shows its item count. A skybox also shows its six faces
under **Face previews**; an HDR sky shows a tone-mapped OpenEXR preview and image information. Selecting a scene
shows its Rendering section with the Ray Tracing switch and saved quality settings. Selecting a project or an
unhandled property shows a "Nothing to show" message. The panel refreshes when the asset's `meta.json` or a face image changes, and
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

### Keep weather from a sky

Right-click a procedural sky asset that references clouds and choose **New Weather Preset from Sky...**.
Choose a new folder name (suggested: `weather_<sky>`). Abyssus creates a reusable `CLOUDS` asset with a fresh UUID,
its stored drawing technique and its cloud bands with all defaults resolved. Unsaved metadata edits are copied;
the view's Clouds override is not. The new asset is selected and starts unused. Assign its UUID to another sky's
`additional.clouds` to use it.

Creation leaves the source clouds, sky, scenes and project unchanged. Undo removes an unchanged, unreferenced
new folder; Redo restores the same snapshot. Missing or unreadable clouds and invalid folder names are refused.

### New Terrain

Right-click an **Assets** node and choose **New Terrain...**: pick a folder name (it must be new and stay inside
`assets`), the world size, a resolution from 2 to 255 and the noise settings, generate a preview and **Create**. The
terrain is written as a native Abyssus terrain asset (`meta.json` with a fresh `uuid`, `terrain.data`, and the recipe), the view
refreshes and the new asset is selected. Nothing is placed in a scene and no scene or project file is changed, so the asset
is marked unused until you add it to a scene. Undo removes the asset again, and Redo brings back the same files and
`uuid`; Undo refuses while a scene or another asset uses it, or something was added to its folder.

### New Foliage

Right-click a project's **Assets** node and choose **New Foliage...**. The project needs a readable terrain asset.
Choose the terrain, a new folder name (suggested: `foliage_<terrain folder>`) and a mask resolution from 16 to 2048
(default 512). **Create** makes an empty foliage asset and selects it; add layers in its Properties panel.

Each layer scatters one or more model assets, chosen with positive weights. Use **OBJECT** for trees and rocks that
cast shadows, or **DETAIL** for grass and flowers that receive shadows and disappear beyond their draw distance.
Set density (copies per square unit), scale range, alignment to the terrain normal, seed, and optional height and
slope limits. Add, remove or reorder layers; the panel shows each layer's copy count and the total.

Valid settings preview in every open Scene view using the foliage. **Apply** saves the settings and matching bake
as one undoable operation; **Cancel**, selecting another asset or closing the panel discards the preview. The total
limit is 1,000,000 copies: Apply, Re-bake and painting refuse results above it. After terrain regeneration or another
change makes the bake out of date, the view regenerates the copies and the panel offers **Re-bake** to save them.
Copies stand on the current terrain surface. Creation adds no scene entity; Undo removes the new asset unless it
is used or its files changed, and Redo restores the same files.

### Import Model

Right-click an **Assets** node and choose **Import Model...**, then pick an `.obj`, `.fbx`, `.3ds`, `.dae`, `.gltf` or
`.glb` file. Blender files are not read: export the model from Blender as glTF (File > Export > glTF 2.0) and import the
`.glb`. The dialog shows a live preview of the model as it will be written, over a ground grid with a 1 m post (drag to
orbit, wheel to zoom), and plays the animation chosen in its list. Choose:

- the folder name (`model_` plus the file name by default);
- the source unit (m, cm, mm, in, ft) and up axis (Y or Z), pre-filled from the file where it says (an FBX header, a
  DAE `<asset>`; glTF is metres and Y up, 3DS is Z up) and marked as read from the file;
- the size: the unit only, or scaled so the largest extent or the height is a number of metres;
- **Add to scene**, on when a scene view is selected: Create also places the model at the point that view orbits
  around and selects it. It is off, with the reason, when no scene view is open, the scene is playing, or its file
  cannot be edited.

The model always stands on y = 0 and is centred on X and Z. Create writes one model asset: `model.glb` with the node
hierarchy, materials and, for animated files, the skeleton and every animation; textures as PNG files in `textures/`;
and a `source.json` with the source path, checksum, frame and settings. Phong materials are approximated as
metallic-roughness (the specular colour is lost), and cameras, lights, points and lines, missing textures, morph targets
and unsupported glTF extensions are left out; the dialog lists each before Create. Nothing is written next to the
source. Undo removes the folder, and the placed entity with it; Redo brings both back with the same files and `uuid`.
Undo refuses once a scene uses the asset through another edit.

### Import FlightGear Aircraft

Right-click an **Assets** node and choose **Import FlightGear Aircraft...**, then pick a FlightGear aircraft `.zip`. The
dialog shows the aircraft (choose one when the archive has several), its authors and licence (with a warning when the
archive states none), a folder name, the size (original metres or a wingspan in metres) and its named parts. Parts the
aircraft hides at rest, such as a spinning propeller disc, start unticked. It also lists what is not imported:
instrument panels, line surfaces, missing or unsupported textures. **Create** writes one model asset: `model.glb` with
the parts as named nodes, nose toward +Z, up +Y, left wing toward +X, standing on y = 0; SGI textures converted to PNG
in `textures/`; any licence files from the archive; and a `source.json` with the archive, its checksum, the licence
and the settings. No scene or project file changes, so the asset is unused until you place it. Undo removes the folder
and Redo restores the same files. Animations, flight models, sounds, panels and effects are not imported.

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
- **foliage** attached to a terrain entity: scattered model copies that follow its position, rotation and scale;
  animated models stay in their rest pose, and a click passes through a copy to the terrain behind it;
- the **skybox** named by `skyboxName` when `skyboxEnabled`; a procedural sky's **clouds** (the `CLOUDS` asset its `meta.json`
  `additional.clouds` names by `uuid`: low, mid and high bands and a technique, shared by every sky naming it) drift with their wind and dim the sun light when they cover it. The toolbar's
  **Clouds** choice (*Asset*, *Layered*, *Shells*, *Volumetric*) overrides the sky's technique in that view only and
  writes nothing; volumetric clouds that keep the view slower than 30 frames per second switch to shells with a note;
- the **light entities** (directional, point and spot, with cone and edge softness) on top of the scene's
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
`-Dabyssus.raytracing.backend=off` disables it for the IDE session. See [ray tracing](projects/lib-raytracing/README.md) for
backend requirements, rendering limits and native toolchains.

Select a terrain entity and choose **Add Foliage...** in its tree context menu or the Scene view toolbar. Choose
one of the foliage assets made for that terrain; this adds or replaces its foliage as one undoable scene edit.
If none is available, create one from the project's **Assets** node first.

With a terrain entity that has readable foliage and at least one layer selected, turn on **Paint Foliage** in the
Scene view toolbar. Choose the layer, radius in world units, strength from 0 to 1, and **Paint** or **Erase** in
the brush strip. Drag the left button inside the circle on that terrain to change its density mask; hold Shift to
erase while Paint is chosen. Layers without a saved mask start at full density, so erase to clear space first.
Copies update during the stroke in all views. Right-drag and the wheel still navigate; gizmos return when you leave
paint mode or select another entity. Esc before release cancels the stroke. Release saves the mask and matching bake
as one undoable operation; Ctrl+Z in the Scene view or Undo in the foliage panel restores it. A stroke above the copy
limit is discarded with a reason. Foliage is omitted from the ray-traced preview, and Properties shows a note.

**Add Light** in the toolbar creates a Directional light, Sun or Spot at the current orbit target and selects it.
The same menu on a scene row places it at the origin; Spot sits 5 units above that point. A Sun starts warm and
brighter, with a low direction. Its light component's **Range** field sets a positive reach (default 100).

**Add Asset** in the toolbar, and on a scene row, lists the project's models and terrains (by folder name, under
Models and Terrains) and adds the chosen one as a new entity named `Model <id>` or `Terrain <id>`. It has a type,
a position and a render component that names the asset. From the toolbar it is placed at the orbit target, from the
tree at the origin; a terrain is centred on that point using its size. The new entity is selected, and Undo removes it.
Each creation is one undoable scene edit. These new light entities use the plugin's own component structure.

For a spotlight, Properties also offers **Cone angle (degrees)** for its full beam width and **Edge softness (%)**
for the inward edge fade. Defaults are 45 degrees and 20 percent. Each accepted edit is saved in the scene and can
be undone; resetting a default omits its saved field. These beam fields are part of the native scene contract.

The toolbar's camera selector (**Free camera** and the scene's cameras by name) renders the view through a camera
entity; orbit, pan and zoom pause until **Free camera** is chosen again.

### Physics

Physics is built into Abyssus. Select the project's `.abss` file in the Abyssus tree and tick **Physics** in
**Abyssus Properties**. This saves the optional `physicsEnabled` boolean as one undoable edit; missing means off.
The checkbox and scene controls follow unsaved text edits and Undo/Redo. Unsupported project documents stay read-only.
The separate Abyssus Physics plugin must be uninstalled; the IDE marks it incompatible with this build.

- Enabling Physics makes rigid bodies, colliders and constraints editable and offers them in **Add Component**.
  **Show Physics** draws collider wireframes and constraint anchors, including selected and simulated objects.
  Disabling Physics restores read-only raw physics data and removes built-in physics overlay/Play actions.
- **Play**, **Pause**, **Step** and **Stop** run the scene's physics in a separate process. Play uses the scene as
  the editor holds it, unsaved text included. Simulated poses are shown but never written: Stop, Esc, editing the
  scene or closing the tab returns the view to the scene as the document holds it. While playing, gizmos are off,
  and keys and mouse buttons go to the game. If the process dies, one notification shows its last output and the IDE
  keeps running.
- A game makes Play run its own code by exporting `<project>/abyssus/play.json` (see `projects/lib-physics/README.md`). Without that file,
  Play runs physics alone.

Disabling Physics during Play stops that session and restores authored poses without changing scene files or Undo history.
Jolt, the physics engine, is loaded only by the play process, never by the IDE.

The model runtime (Assimp import, the model/mesh/shader classes with 32-bit indices) is the `lib-gdx` module, a
plain JVM library reusable in other libGDX projects; see [source provenance](docs/third-party/gdx-model-origin.md)
for its origin and license. The GL
render tests are opt-in: `./gradlew check -Dabyssus.glTests=true` (opens a window).

To add a project or scene file format, implement `ConfigFileReader` and wire it into `AssetReadCache` and
`ProjectLayout.ASSET_EXTENSIONS`. Asset loading uses `core`'s `AssetLoader` implementations; see
[architecture and extension points](docs/ai/architecture.md).

## Development

See [AGENTS.md](AGENTS.md) for build, test and sandbox commands, and [the documentation map](docs/README.md)
for architecture, native formats and module guides. The [Control Line game](projects/app-game-control-line/README.md) demonstrates
registered game components and a Play module for the separate physics host. Use a copy of its native project in the IDE.

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "abyssus"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/inver/abyssus/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


## License

Licensed under [Apache-2.0](LICENSE) (SPDX-License-Identifier: Apache-2.0).

Files under `projects/lib-gdx` derived from [libGDX](https://github.com/libgdx/libgdx) retain their original
Apache 2.0 headers.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
