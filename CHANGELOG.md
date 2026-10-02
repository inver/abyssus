<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# abyssus Changelog

## [Unreleased]
### Added
- Abyssus Properties tool window showing the `meta.json` of the asset selected in the Abyssus view (read only), with face previews for skyboxes
- Scene view draws the scene's models, terrain, skybox and light entities, plays model animations, and a click on an entity selects it in the Abyssus view
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

### Changed
- The model runtime and Assimp importer live in their own `gdx-model` library module (no IntelliJ or gdx-gltf dependency)

### Removed
- Template sample tool window, frame listener and project service

### Fixed
- Bounds and picking of meshes with more than 65,535 vertices
- Assets prepared after the view was closed or its project changed are released instead of leaking
