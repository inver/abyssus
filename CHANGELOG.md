<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# abyssus Changelog

## [Unreleased]
### Added
- Abyssus view: `Scenes` / `Assets` rows with counts, entities listed by name with their components, per-kind icons, a "Show Only Unused Assets" option and a scenes / assets / unused counts footer
- Create, edit and remove an entity's components: **Add Component...** and **Remove Component** in the Abyssus view, and editable fields, **Add component** and **Remove** for a selected entity or component in the Abyssus Properties window (each change is one undoable edit of the scene file)
- Abyssus Properties tool window showing the `meta.json` of the asset selected in the Abyssus view (read only), with face previews for skyboxes
- Scene view draws the scene's models, terrain, skybox and light entities, plays model animations, and a click on an entity selects it in the Abyssus view
- Contributor and coding-agent docs: `AGENTS.md` (with `CLAUDE.md`), `docs/ai/` reference pages, package notes, and `scripts/check-docs.sh` to catch stale paths; README describes the plugin instead of the template
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

### Changed
- The model runtime and Assimp importer live in their own `gdx-model` library module (no IntelliJ or gdx-gltf dependency)

### Removed
- Template sample tool window, frame listener and project service

### Fixed
- Bounds and picking of meshes with more than 65,535 vertices
- Assets prepared after the view was closed or its project changed are released instead of leaking
