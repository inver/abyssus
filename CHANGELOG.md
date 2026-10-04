<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# abyssus Changelog

## [Unreleased]

### Changed
- Abyssus is now an independent libGDX scene editor using native format version 1. Projects, scenes and asset metadata
  require `format: "abyssus"` and integral `formatVersion: 1`; renderables use stable native kinds and components use short names.
- Older unmarked files and files from other editors are unsupported. Plugin loading, editing and automatic formatting refuse them without
  rewriting them. No importer or automatic migration is included. External model, image, terrain binary and recipe encodings remain unchanged.
### Added
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
- Template sample tool window, frame listener and project service

### Fixed
- Ray Tracing refused real scenes ("RESOURCE_LIMIT: Scene exceeds instance, triangle or byte limits"): scenes can now have up to 1024 model parts on Metal and 32 blended surfaces, image textures stay 8-bit, and a scene that is still too big says exactly which limit it exceeded
- Bounds and picking of meshes with more than 65,535 vertices
- Assets prepared after the view was closed or its project changed are released instead of leaking
