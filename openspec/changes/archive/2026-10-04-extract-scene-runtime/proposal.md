# Proposal

## Why

Abyssus is meant to be the editor for libGDX games built on Mundus projects, and a game has to load the scenes it
edits. Today the code that does that lives in the plugin. The Ashley ECS (`ecs/`: components, codecs, loader, writer,
systems) is almost free of the IDE: only `SceneEcsWarnings` logs through IntelliJ's `Logger`. Reading a project and
its scenes (`dto/ProjectReader`, `SceneReader`, `ProjectLayout`, `scene/SceneDto`) works on `VirtualFile` and
IntelliJ services. A game, a test, or the out-of-process
play host planned in `add-jolt-physics` cannot use either. Moving both into a plain JVM module, wired by constructors
like `core`, gives the editor and the games one scene runtime.

## What Changes

- A new Gradle module `runtime`, a plain JVM library like `core`: Kotlin, libGDX, Ashley, Jackson, `:core` and
  `:gdx-model`, with no IntelliJ dependency and no `object` / `companion object` in `src/main` (the same
  `checkNoSingletons` check as `core`). The plugin depends on it.
- Moved into `runtime`, behavior unchanged:
  - the ECS: `ecs/component/`, `ecs/render/`, `ecs/system/`, `EcsConfigurator`, and from `ecs/scene/` the
    `SceneEngine`, `SceneEcsLoader`, `SceneEcsWriter`, `ComponentCodecs` and the warnings;
  - the scene DTOs (`SceneDto` without its `VirtualFile`, `ColorDto`, `FogDto`, the light DTOs);
  - project and scene reading over `java.nio.file.Path`: the `.abss` project file, its scene list and each `.scene`
    file, from a project folder.
- Problems met while loading are reported to a log the caller passes in. The plugin's implementation writes the IDE
  log as today.
- The plugin keeps everything that edits or shows scenes: `ComponentEditor` (it uses `SceneJson` and
  `AbyssusBundle`), `LightEntities` (it builds new light entities for an edit), `editSceneJson`, the tree, the
  properties panel and the scene view. Thin `VirtualFile` adapters over the `runtime` readers replace the readers'
  file access, and `ProjectReader` / `SceneReader` stay IntelliJ services only as those adapters.
- **BREAKING (internal API only):** the moved classes change package and module. No user-visible change.

**Mundus files.** Read exactly as today: the `.abss` project file (`name`, `mainCamera`), the `.scene` files in the
project's `scenes` folder, and each scene's top-level fields and `ecs` block (`entities`, `archetypes`,
`componentIdentifiers`, `metadata`) with every modeled component. Nothing new is written, and the file format does
not change.

**Out of scope.**

- Moving the scene view onto the Ashley engine. It keeps reading placements from the `ecs` JSON.
- Moving `ComponentEditor`, `SceneJson` or any write path out of the plugin.
- Custom (game-defined) components and schemas. That is `add-custom-components`.
- Physics, play mode and the control-line game: `add-jolt-physics` and `add-control-line-game`.
- Any change to what loads, what is drawn or how problems look.

## Capabilities

### New Capabilities

- `scene-loading`: reading a Mundus project and its scenes from a folder into an Ashley engine, without the IDE
  runtime, reporting problems through the caller's log, with no state shared between independent loads.

### Modified Capabilities

None. `scene-ecs-components` and `scene-ecs-systems` keep their requirements; only where the code lives changes.

## Impact

- **Build:** `settings.gradle.kts` includes `:runtime`; new `runtime/build.gradle.kts`; Ashley moves from the
  plugin's dependencies to `runtime`; the plugin gains `implementation(project(":runtime"))`. `./gradlew check` runs
  `:runtime:test` and `:runtime:checkNoSingletons`.
- **Code moved:** the files listed above. Their tests (`ComponentCodecsTest`, `ComponentsTest`,
  `SceneEcsLoaderTest`, `SceneEcsWriterTest`, `SystemsTest`) move to `runtime/src/test` and read the shared
  `src/test/testData/project` fixtures the way `core`'s tests do. `ComponentEditorTest` and
  `LightEntitiesTest` stay in the plugin.
- **Plugin callers updated:** `ComponentEditor`, the properties panel (`EntityDetailsView`, `PanelState`),
  `projectView` actions that use the ECS (`ComponentActions`, `SceneComponentEdits`, `AddLightAction`), and the `dto`
  readers.
- **Docs:** `AGENTS.md` (layout, commands, a hard rule for `runtime`), `docs/ai/architecture.md`,
  `docs/ai/testing.md`, `src/main/kotlin/net/nevinsky/abyssus/ecs/README.md` (what stays), and a new
  `runtime/README.md`.
- **Other changes:** this change runs after `design-review-refactor`, which makes the scene view read entities
  through `ComponentCodecs`, moves `editSceneJson` and switches to constructor wiring in the same packages. If the
  order is reversed, that change's tasks must be amended to the `runtime` paths. `add-custom-components`,
  `add-jolt-physics` and `add-control-line-game` build on this module.
