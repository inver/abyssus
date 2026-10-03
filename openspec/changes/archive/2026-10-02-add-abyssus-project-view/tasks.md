# Tasks

## 1. Asset file recognition

- [x] 1.1 Add `src/main/resources/icons/scene_file_icon.svg` and `src/main/resources/icons/abss_file_icon.svg` following the existing `gltf_file_icon.svg` style, plus `SceneIcons`/`AbyssusProjectIcons` holders using `IconLoader.getIcon`; verify both icons load in a platform test without throwing and are distinguishable
- [x] 1.2 Add `SceneFileType` and `AbyssusProjectFileType` as plain `FileType`s (`getDefaultExtension()` of `scene` / `abss`, icons from the holders) next to `GltfFileType`, with unit tests asserting each extension and icon; verify the existing `GltfFileType` still reports extension `gltf`
- [x] 1.3 Register both new `<fileType>` entries in `plugin.xml` and verify the plugin loads with no descriptor errors via `./gradlew verifyPluginProjectConfiguration` plus a platform test opening `forest.scene` and `game.abss` fixtures

## 2. DTO read models and readers

- [x] 2.1 Add a uniform property abstraction (`DtoProperty` with scalar (incl. null) / nested DTO / DTO-list / generic JSON object-or-array values) exposing an ordered name/value list, and apply it to `SceneDto`, `BaseLightDto`, and `FogDto`; verify with unit tests that each DTO reports its fields in declaration order
- [x] 2.2 Convert `SceneDto` from private hardcoded fields (`id = 0`, `name = null`, `ecs = null`) to a readable model with `id`, `name`, `ambientLightEnabled`, `ambientLight`, `fogEnabled`, `fog`, `skyboxEnabled`, `skyboxName`, and `ecs` accessible, keeping nullable for absent values; verify with unit tests over a populated and an empty model
- [x] 2.3 Give `ProjectDto` its ordered property list (`name`, then `scenes` as a DTO-list value) over its existing `val name` / `val scenes`; verify with unit tests that a project with three scenes reports `scenes` ordered by file name
- [x] 2.4 Define the `AssetReader` interface returning a read result (property tree or failure) and implement the tolerant default `SceneReader` (JSON) and `ProjectReader` (JSON, loading scenes from the sibling `scenes` folder via `SceneReader`), returning failures instead of throwing; verify with tests over valid, empty, and malformed content for both readers
- [x] 2.5 Use `src/test/testData/project/Untitled` (`Untitled.abss` + `scenes/Main Scene.scene`) as the primary fixture and add extra fixtures under `src/test/testData` (a project with three scenes and one with no `scenes` folder, populated scene, scene with nested `fog`/`ambientLight`, project with three scenes, plus empty and malformed files); verify the reader tests pass against them and that reading leaves fixture bytes unchanged (compare content before/after)

## 3. Project view provider and structure

- [x] 3.1 Implement `AbyssusProjectViewPane` (extends `AbstractProjectViewPane`, id `Abyssus`) and register `<projectViewPane>` in `plugin.xml`; verify `Abyssus` appears in the view selector of a fixture project in a platform test
- [x] 3.2 Build the pane's tree structure as a flat list of top-level asset nodes (projects first, scenes belonging to a project hidden, no directory nodes); verify `Untitled` yields only `Untitled.abss`
- [x] 3.3 Implement discovery over `ProjectRootManager.contentRoots` matching `virtualFile.extension in setOf("scene", "abss")` exactly (case-sensitive, suffix-based, includes excluded folders) and pick the reader/icon per extension; verify with a fixture project holding `a.scene`, `Forest.SCENE`, `forest.scene.bak`, `a.abss`, `A.ABSS`, `a.abss.bak`, `scenes/a.scene`, and an excluded-folder asset that only the four matching assets are listed with the right icons

## 4. Asset node rendering

- [x] 4.1 Implement `AbyssusAssetTreeElement` as a `ProjectViewNode` presenting the file name (`forest.scene`, `game.abss`) with the per-extension icon and a modification-stamp-keyed cache; verify the presentation test returns the file name and the matching icon for each kind
- [x] 4.2 Return `DtoProperty` child nodes from the asset node's `getChildren()`, rendered recursively so nested `fog`/`ambientLight` values expand further, `skyboxName: null` renders as `null`, and the `ecs` JSON tree of `Untitled/scenes/Main Scene.scene` expands through `entities` and `components`; verify with a tree-model test that a populated scene yields one child per property and nested children for nested objects
- [x] 4.3 Render DTO lists as one child per element in list order, labelled from the element's own name when present and otherwise by index (`scenes[0]`, ...); verify with a tree-model test over a project with three scenes, including one whose scenes have no name
- [x] 4.4 Fold `<x>Enabled` toggles into an eye icon on the gated property (`<x>` or `<x>Name`) instead of a separate node, drawn as a clickable eye at the right of the row that flips the option in the source file (`toggleEnabled`); label project scenes `name (id)`, hide their `id`/`name` rows and add a "Rename Scene..." popup action (`RenameSceneAction`, `renameScene`), and render the localizable parse-error placeholder as a leaf when reading fails, keeping the rest of the tree loadable; verify with tests for `fogEnabled=false` with a populated `fog` (hidden eye, no `fogEnabled` node), and that toggling twice restores the file's original bytes, and for a malformed asset file inside a valid fixture project
- [x] 4.5 Ensure expanding an asset does not insert extra entries into the parent directory node, and that each asset file yields exactly one node across switch-away/switch-back and add/delete refresh; verify with a tree-model test asserting unique child identities after re-querying the model

## 5. Integration and documentation

- [x] 5.1 Add the new bundle keys (`abyssusViewName`, `assetParseError`, `dtoListElementLabel`) to `messages/AbyssusBundle.properties` and use them through `AbyssusBundle.message`; verify no hardcoded user-visible strings remain in the new code
- [ ] 5.2 Document the view, both asset file formats, and the `AssetReader` extension point in `README.md`; verify the documented steps match the shipped behaviour by launching the plugin via `./gradlew runIde` and selecting the Abyssus view in a project containing scene and project fixtures
- [ ] 5.3 Run `./gradlew build` and `./gradlew test` and fix any failures so the full suite passes with the new view registered
