<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# abyssus Changelog

## [Unreleased]

### Changed
- `lib-core-editor` packages now use `net.nevinsky.abyssus.lib.core.editor`; consumers and extension providers using editor types must update imports and rebuild.
- Physics ships in Abyssus and is enabled per native project with `physicsEnabled: true` in its `.abss`. Existing projects must enable Physics in project properties once.
- Abyssus is now an independent libGDX scene editor using native format version 1. Projects, scenes and asset metadata
  require `format: "abyssus"` and integral `formatVersion: 1`; renderables use stable native kinds and components use short names.
- Older unmarked files and files from other editors are unsupported. Plugin loading, editing and automatic formatting refuse them without
  rewriting them. No importer or automatic migration is included. External model, image, terrain binary and recipe encodings remain unchanged.
### Added
- **Foliage in games and Play.** A terrain's `FoliageComponent` is a built-in component that keeps its other members and number text on a round trip. Games read a foliage asset's committed bake as written (a stale bake keeps its saved copies; nothing is generated or written) and draw it instanced, one drawable per terrain entity, with OBJECT trees casting the sun's shadow even when off screen and grass only receiving it. Control Line now needs OpenGL 3.2 for this. Known limit: foliage is left out of ray traced views.
- **Foliage colliders.** An OBJECT layer may have a BOX, SPHERE or CAPSULE `collider` (None by default) in the foliage panel. A collider-only Apply edits just `meta.json` as one undoable step and leaves the bake and masks as they are, also when the bake is stale or missing. In the physics world a layer becomes static compound bodies per chunk, reported as contacts with the terrain entity; limits are 200,000 colliding copies and the bodies left after the ordinary ones, and a sheared, reflected or zero-scale copy gets no collider.
- **Control Line field foliage.** The field's 215 trees and bushes are now two foliage assets (`foliage_airfield_site`, `foliage_airfield_outer`) plus generated grass on the site terrain (never within 25 m of the pilot), made reproducibly by `tools/FieldFoliage.kt` from a recorded manifest.
- **Cloud shadows and sky lighting for procedural skies.** While a procedural sky draws clouds, they cast moving shadows from the sun onto terrain and models (only the sun, combined with its own scene shadow, replacing the uniform sun dimming within about 4 km of the view target); `additional.lightsScene: true` makes a procedural sky light the scene like an HDR sky, following the sun and clouds with a one-second fade; ray traced views show the clouds in reflections and the cloud shadows on the sun (Metal and the CPU reference verified, Vulkan pending a device). Known limits: lighting follows the sky's physical parameters, not a custom `sky.frag`; shadow size assumes scene units are metres.
- More realistic Ray Tracing clouds: a baked volumetric sky now marches its clouds at full resolution and averages three jittered samples per cube face. Before, the half-resolution single sample made the cloud shader think each pixel spanned a third of a degree, so it thinned and blurred the cloud detail away. Drifting skies are baked every 5 s instead of 2 s
- Ray Tracing clouds are sharp and smooth: the baked procedural sky is rendered from 512 px faces, resampled bilinearly into a 2048x1024 image (it was 256 px faces, nearest, 1024x512, which showed blocky, striped clouds) and carried as 8-bit data
- Ray Tracing no longer flickers in scenes with drifting clouds, terrain or animated models: terrain materials no longer count as changed every frame, a re-baked sky keeps the previous ray image on screen until the new one is ready, and an animated model with a still camera keeps an up to 500 ms old ray frame instead of flashing raster
- Ray Tracing no longer stops with "Scene textures exceed 16777216 texels": the largest textures are reduced (halved) until they fit the ray tracing texture budget
- **Maximum model parts and terrains** in the saved Ray Tracing settings of a scene (`rayTracing.maxInstances`, 1–4096, default 4096); the ray tracing instance capacity itself is raised from 1024 to 4096, so larger game scenes no longer fall back to raster
- Procedural scene light and cloud shadows: a `SKYBOX_PROCEDURAL` asset with `"lightsScene": true` lights models and terrain from its own atmosphere and clouds (fading in over about a second as the sun or clouds change; the ambient colour is used until it is built or if it fails), and a sky that draws clouds now casts moving shadows from the sun onto terrain and models (a 4 km map around the orbit target, replacing the uniform sun dimming inside it). Ray Tracing reflects the same clouds, uses the sky light as diffuse light and applies the cloud shade to the sun. Limitations: lighting and shadows follow the asset's atmosphere parameters, not a custom `sky.frag`; cloud shadows assume the scene's units are metres and shade only the sun; the Vulkan backend's cloud shade has been compiled and validated but not run on a GPU yet
- **New Weather Preset from Sky...** on procedural sky asset rows: snapshot the referenced clouds as a new native
  `CLOUDS` asset with resolved defaults and the stored technique, using unsaved metadata, guarded Undo and stable
  Redo. The source files remain unchanged and the snapshot starts unused.
- Clouds on procedural skies: a new `CLOUDS` asset (low, mid and high bands with cloud types, coverage, density and wind, and a technique) that a procedural sky names by `uuid` and loads like a terrain loads its textures; drawn layered, as shells or volumetric (a per-view **Clouds** toolbar choice overrides the asset's technique, and slow volumetric clouds fall back to shells), dimming the sun light when they cover it; the skybox chooser reads `procedural sky · clouds`
- **Import Model...** on a project's Assets node: an OBJ, FBX, 3DS, DAE, glTF or GLB file becomes a native model asset (`model.glb`, PNG textures and a `source.json`), checked first in a dialog with a live preview, unit, up axis and size settings and the animation to play; skeletons and animations are kept, the model stands on y = 0, centred, and **Add to scene** places it in the selected scene view in the same undoable step. Blender files are refused with a hint to export glTF
- `gdx-model`'s `GltfWriter` writes any `ModelData` as binary glTF (nodes, skins, animations, metallic-roughness materials); the FlightGear import now writes through it
- Logging in every module through SLF4J, bound to the IDE logger: `gdx-model`, `core` and `raytracing` now write progress, backend probe results, the chosen GPU, session limits, fallbacks and failures to `idea.log` under `Abyssus.assets`, `Abyssus.model` and `Abyssus.ray` (debug lines follow Debug Log Settings).
- Saved **Ray Tracing** settings for a selected scene in Abyssus Properties: target samples per pixel, maximum rays per frame, and maximum reflection and refraction bounces, stored in the scene's `rayTracing` block (defaults omitted) with Undo/Redo; the switch itself is still not saved
- Ray traced glass: **Transmission** and **IOR** for a model entity's PBR materials, saved as that entity's `RenderComponent.rayTracingMaterials` overrides, with Snell refraction, Fresnel and total internal reflection through closed solids; unsupported glass (open meshes, overlapping solids, masked or blended materials) falls back to raster with the reason
- **Ray Tracing** switch in Abyssus Properties for a selected scene (the Scene View itself has no ray tracing button): it flips the open Scene View, opening it when needed, and shows the status, the reason it is unavailable or failed, and Retry
- **Ray Tracing** (experimental, off by default): ray traced shadows and reflections, sky, fog and transparency on a Metal (macOS) or Vulkan (Windows, Linux) GPU, with an automatic return to the normal renderer on failure
- Add Light menus in the Scene view toolbar and on scene rows, with Directional, Sun and Spot presets, selection and single-step Undo; editable positive light range in the properties panel
- Abyssus view: `Scenes` / `Assets` rows with counts, entities listed by name with their components, per-kind icons, a "Show Only Unused Assets" option and a scenes / assets / unused counts footer
- Create, edit and remove an entity's components: **Add Component...** and **Remove Component** in the Abyssus view, and editable fields, **Add component** and **Remove** for a selected entity or component in the Abyssus Properties window (each change is one undoable edit of the scene file)
- Abyssus Properties tool window showing the `meta.json` of the asset selected in the Abyssus view (read only), with face previews for skyboxes
- Scene view draws the scene's models, terrain, skybox and light entities, plays model animations, and a click on an entity selects it in the Abyssus view
- Contributor and coding-agent docs: `AGENTS.md` (with `CLAUDE.md`), `docs/ai/` reference pages, package notes, and `scripts/check-docs.sh` to catch stale paths; README describes the plugin instead of the template
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

### Changed
- Scene view lights that leave out `intensity` are drawn at intensity 1 (they were drawn at 0.3, while the Properties panel always showed 1), and a color channel missing from a present `color` object counts as 0 in both; the scene files are not touched
- The model runtime and Assimp importer live in their own `gdx-model` library module (no IntelliJ or gdx-gltf dependency)

### Removed
- The separate Abyssus Physics plugin. Uninstall it when upgrading; Abyssus declares its former ID incompatible.
- Template sample tool window, frame listener and project service

### Fixed
- Ray Tracing stopped with "Minimum ray frame exceeds dimension, pixel, memory or ray bounds" in ordinary windows: the per-frame ray budget is now soft (the frame stays at half resolution with one sample) and only real allocation limits refuse a view.
- Ray Tracing reported "Packaged Vulkan shader slice is missing" when the plugin was built without a shader compiler on `PATH`; the build now also finds `glslangValidator`/`glslc` under `VULKAN_SDK` and the Android NDK.
- Ray Tracing refused real scenes ("RESOURCE_LIMIT: Scene exceeds instance, triangle or byte limits"): scenes can now have up to 1024 model parts on Metal and 32 blended surfaces, image textures stay 8-bit, and a scene that is still too big says exactly which limit it exceeded
- Bounds and picking of meshes with more than 65,535 vertices
- Assets prepared after the view was closed or its project changed are released instead of leaking
