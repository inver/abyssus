# Proposal

## Why

Abyssus currently has no way to browse its own assets in the IDE: `.scene` and `.abss` files
are invisible to the Project tool window (no view, no file types, no parsing), so users must
navigate raw directories and open files by hand. Both formats are hierarchical by nature -
`SceneDto` carries nested `BaseLightDto`/`FogDto`, skybox and ECS data, and `ProjectDto` carries
a list of scenes - so the default file tree hides the structure these files actually describe.

## What Changes

- Add an `Abyssus` view to the Project tool window (an `AbstractProjectViewPane` registered via the
  `com.intellij.projectViewPane` extension point with id `Abyssus`).
- The view searches the project content roots and displays every file with the `*.scene` or
  `*.abss` extension, with each `.abss` project as a top-level node (no directory nodes).
- Introduce plain file types for `*.scene` and `*.abss` so these files are recognized by the
  platform (icon, editor default type) and are filtered deterministically by the view.
- Parse each scene file into `net.nevinsky.abyssus.scene.SceneDto` (`id`, `name`,
  `ambientLightEnabled`/`ambientLight`, `fogEnabled`/`fog`, `skyboxEnabled`/`skyboxName`,
  `ecs`, rendered as a generic JSON tree) and each project file into `net.nevinsky.abyssus.project.ProjectDto` (`name`,
  `scenes` loaded from the sibling `scenes` folder), and expose their fields.
- Present each file as a node that expands into the structure of its DTO: nested child nodes
  for sub-objects (`ambientLight`, `fog`) and one child per element for DTO lists (`scenes`),
  with values shown next to their property names.
- A `*.abss` node expands inline into its `name` and its scenes. The scenes are the `*.scene`
  files in the `scenes` folder next to the `.abss` file, read into `SceneDto`s and shown as DTO
  content only; scene entries do not navigate to those files.
- Display only the name column by default so custom view columns remain available for
  later DTO-derived columns.

## Capabilities

### New Capabilities
- `abyssus-project-view`: The Abyssus view in the Project tool window - discovery of
  `*.scene` and `*.abss` files, reading them into the `SceneDto` / `ProjectDto` models, and
  rendering those models as an expandable tree.

### Modified Capabilities

None. This is the project's first capability; there are no existing specs to modify.

## Impact

- New code:
  - `net.nevinsky.abyssus.projectView` package - view provider, node model, DTO tree builder,
    and readers for `*.scene` and `*.abss` files.
  - `net.nevinsky.abyssus.scene` - `SceneDto` becomes a readable read model (currently a
    stub with `private` hardcoded values); `BaseLightDto`/`FogDto` gain readable accessors.
  - `net.nevinsky.abyssus.project` - `ProjectDto` gains a readable/displayable shape for its
    `name` and its `scenes` list.
  - `*.scene` and `*.abss` `FileType` implementations (alongside the existing `GltfFileType`).
- Modified: `src/main/resources/META-INF/plugin.xml` - register `projectViewPane` and
  both file types.
- Messages: new keys in `src/main/resources/messages/AbyssusBundle.properties` for the
  view name, the parse-error placeholder, and DTO list element labels.
- Tests: platform tests under `src/test/kotlin/net/nevinsky/abyssus` for node building and
  scene/project parsing.
- No breaking changes; no new Gradle dependencies (the platform test framework and JUnit 4
  already apply).
