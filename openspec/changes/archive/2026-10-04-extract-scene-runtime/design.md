# Design

## Context

See proposal.md - Why. The code today:

- `ecs/` (Ashley 1.7.4, a plugin dependency): components, `ecs/render/Render.kt` (`RenderComponent`,
  `AssetResolver`, `RenderableObjectDelegate`), `ecs/system/Systems.kt`, `EcsConfigurator` (with an
  `object WorldUtils`), and `ecs/scene/` (`SceneEngine`, `SceneEcsLoader`, `SceneEcsWriter`, `ComponentCodecs`,
  `SceneEcsWarnings`, `ComponentEditor`, `LightEntities`). Only `SceneEcsWarnings` imports IntelliJ (its `Logger`).
  `ComponentEditor` imports `SceneJson` and `AbyssusBundle`; `LightEntities` imports `sceneview.Vec3`.
- Singletons in the code that moves: `object WorldUtils`, the `SceneEcsWarnings` logger companion, the
  `AssetResolver` companion (`ofFolders`) and the `RenderableObjectDelegate` companion (the Mundus class name).
- `scene/SceneDto` (`id`, `name`, ambient light, fog, sky, `ecs` as a `JsonNode`) carries an ignored `file:
  VirtualFile`, used by four plugin call sites (`AbyssusNodes` twice, `EnabledToggle`, the tree's icon).
- `dto/SceneReader` (an app service) parses a scene through `AbyssusCore.json`; `dto/ProjectReader` (a project
  service) reads the `.abss` `name`, the `scenes` folder and the asset listing for the tree; `ProjectLayout` (an
  `object`) holds the layout constants and `VirtualFile` helpers. `SceneRenderParams` reads `mainCamera` from the
  `.abss` JSON itself.
- `core` is the precedent: a plain JVM module wired by constructors, `AssetLog` for problems, `checkNoSingletons`,
  tests reading `src/test/testData/project` through `testProject(name)`.
- `design-review-refactor` (open, not started) adds `ecs/component/ComponentDefaults.kt` and `SceneEcsPaths`, makes the
  scene view decode components through `ComponentCodecs`, and passes `JsonProcessor` / `SceneReader` through
  constructors. This change runs after it (proposal, Impact).

## Goals / Non-Goals

**Goals:**

- `runtime` compiles and tests with no IntelliJ artifact on its classpath. A test reads a project folder and loads a
  scene into an engine with plain constructor calls.
- The plugin shows, edits and writes exactly what it does today; only imports and wiring change there.
- `add-custom-components` can add a codec registry, and `add-jolt-physics` a play host, on top of `runtime` without
  touching the plugin's file access.

**Non-Goals:**

- Moving the scene view onto the Ashley engine, or `ComponentEditor`, `LightEntities`, `SceneJson` and `editSceneJson`
  out of the plugin.
- Changing what the loader reads or warns about.

## Decisions

### 1. Module shape and package root

`runtime/build.gradle.kts` mirrors `core`: `java-library` + Kotlin JVM 21 with its own stdlib,
`api(project(":core"))` (which brings `:gdx-model`, libGDX and Jackson), `api("com.badlogicgames.ashley:ashley:1.7.4")`,
JUnit 4, the shared `abyssus.testData` system property, and a `checkNoSingletons` task wired into `check`. The plugin
gets `implementation(project(":runtime"))` with the same Kotlin / SLF4J excludes as `:core`, and drops its own Ashley
line.

Root package `net.nevinsky.abyssus.lib.runtime`:

| Package | Holds |
|---|---|
| `runtime` | `SceneLoading` (composition root) |
| `runtime.project` | `ProjectFolder` (layout constants, `.abss` and scene listing over `Path`), `ProjectInfo` |
| `runtime.scene` | `SceneDto`, `ColorDto`, `FogDto`, the light DTOs, `SceneParser` |
| `runtime.ecs` | `EcsConfigurator`, `LoadedScene`, the world helpers |
| `runtime.ecs.component`, `.render`, `.system`, `.scene` | the moved ECS packages, `scene` without `ComponentEditor` / `LightEntities` |

**Alternative rejected:** keeping `net.nevinsky.abyssus.ecs`. `ComponentEditor` and `LightEntities` stay in the
plugin under `ecs.scene`, so the package would be split across two jars, the problem `core` avoided by taking
`net.nevinsky.abyssus.assets`.

### 2. One composition root, one log

```
SceneLoading(json: JsonProcessor, log: AssetLog)
  |- project(dir: Path): ProjectInfo             name from the .abss, scene paths sorted by file name
  |- projectName(text: String): String?          the .abss name, for callers that hold the text
  |- parse(text: String): SceneDto               top-level fields, ecs kept as a JsonNode
  |- load(scene: Path): LoadedScene              reads the file, then as below
  '- load(text: String, projectDir: Path): LoadedScene
        = SceneDto + SceneEngine + SceneEcsDocument, render assets resolved against <projectDir>/assets
```

`AssetLog` from `core` is reused rather than a second log type: the plugin already builds one and tests already record
one. `SceneEcsWarnings` takes the log and keeps its once-per-message rule per load. Each `load` creates its own
warnings, engine and resolver, so two loads share nothing (`scene-loading`, "Independent scene loads share nothing").

The plugin builds one `SceneLoading` in `AbyssusCore` from its `JsonProcessor` and an `AssetLog` that writes
`Logger.getInstance("Abyssus.scenes")`.

### 3. Singletons become instances or top-level declarations

| Today | In `runtime` |
|---|---|
| `object WorldUtils` | the existing top-level `Engine.getFromWorld` extension; the object is removed |
| `SceneEcsWarnings` logger companion | the injected `AssetLog` |
| `AssetResolver.ofFolders` (companion) | `class FolderAssetResolver(folders: Collection<String>) : AssetResolver` |
| `RenderableObjectDelegate` companion (Mundus class name) | a top-level `const val` |
| `ProjectLayout` (`object`) | `ProjectFolder` constants as top-level `const val`; the plugin's `ProjectLayout` keeps only its `VirtualFile` helpers and points at them |

### 4. `SceneDto` loses its file; the plugin pairs it

`SceneDto` moves without `file`. `ProjectReader` puts `SceneEntry(file, scene)` into `ProjectDto.scenes` where it
put a `SceneDto`. The tree renders a `SceneEntry` exactly as it rendered the scene (same label, icon and rows), and the
four call sites read `entry.file`. `SceneError` already pairs a file with a message and stays.

**Alternative rejected:** an untyped `source: Any?` field on the moved `SceneDto`. It keeps the plugin diff small but
puts an editor concern into the runtime's data model.

### 5. The plugin's readers become adapters

`SceneReader.parse(text)` calls `SceneLoading.parse`; `read(file)` passes the file's text. `ProjectReader` keeps its
`VirtualFile` stamping, scene-folder listing and asset listing (tree concerns, which must see unsaved VFS state) and
reads the `.abss` name through `SceneLoading.projectName(text)`, the same parser `project(dir)` uses. `SceneRenderParams`
keeps reading `mainCamera` itself (a view concern). `ComponentEditor` keeps using `ComponentCodecs` from `runtime`.

### Threads

| Piece | Thread | GL |
|---|---|---|
| `SceneLoading.project` / `parse` / `load`, `SceneEcsWriter`, codecs | the caller's; in the plugin, where `SceneReader` / `ProjectReader` run today (pooled reads and the read action of the tree) | none |
| `RenderComponentSystem` render pass (unchanged, unused by the plugin) | whoever draws, inside a current GL context; in the plugin that would be `GdxRuntime.withContext` on the AWT render thread | yes |

### Testable without GL or the platform

All of `runtime` except the render pass: `ProjectFolder`, `SceneParser`, `SceneLoading`, the loader, writer, codecs,
systems (with no render data set). The moved tests already run without GL.

## Risks / Trade-offs

- **Collision with `design-review-refactor`.** It edits the same files. → This change starts only once that change is
  archived (task 0.1). If it must go first, a task amends that change's open tasks to the `runtime` paths, as
  `openspec/config.yaml` asks.
- **Tree regressions from `SceneEntry`.** The tree's rows come from the DTO it is given. → `AbyssusViewTest` and
  `AbyssusProjectViewPaneTest` must show identical trees; a runIde check covers icons and toggles.
- **Ashley on two classpaths.** The plugin must not bundle Ashley twice. → The plugin's own Ashley dependency is
  removed; `buildPlugin`'s zip is checked for a single `ashley-*.jar`.
- **Fixture text in the spec.** The current `scene-ecs-components` scenario says `Main Scene` has 7 entities; the
  fixture now holds ids 0 to 8. The new spec states the fixture as it is; the old scenario is left for its own owner
  to correct.

## Migration Plan

Internal only; no user data or file format change. Land in task order with the build green after each group;
`git mv` keeps history. Rollback is reverting the change's commits.
