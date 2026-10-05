# Proposal

## Why

A first-time read of the whole repository shows a healthy lower stack (`gdx-model` → `core` → `runtime` → `physics`,
with no IDE import in any of them, enforced by the Gradle dependencies) and an overloaded top. The root Gradle module is
both the IntelliJ plugin shell and the entire editing engine: about 16.7k lines in 166 files and 114 test files in one
module. The refactors in `refactor-solid-dedup` fix local duplication; they cannot fix this, because it is a
structure problem. Findings, each measured on this checkout:

| # | Finding | Evidence |
|---|---|---|
| S1 | **Most of the "plugin" is not IDE code.** 61 of the 76 `sceneview` files (5.5k lines) import no `com.intellij`, Swing, AWT or LWJGL. So do the terrain generator (9 files, 875 lines), the component editor and reader, `SceneJson`, `AssetMetaEditor`, `AssetLoading`, `DocumentParsing`, `AssetMetaReader` and the format check. They are plain Kotlin on libGDX, but the only guard is convention, and they can only be tested through the plugin's Gradle module. | import scan of `src/main/kotlin` |
| S2 | **The ray tracing bridge sits in the plugin.** About 15 plain files in `sceneview/` (`RaySceneSnapshot` 467 lines, `RayViewFeed`, `RayViewRuntime`, `RayFeasibilityLoop`, `RayModeState`, `RayModelPoses`, `RaySkyBaker`, `RayBackendSelector` and others) convert a scene into `raytracing` snapshots and schedule frames. None of it needs the IDE. | file list and imports |
| S3 | **Package cycles inside the plugin.** `sceneview` ↔ `projectView` (5 + 3 imports), `projectView` ↔ `properties` (4 + 7), `terrain` ↔ `properties`, `dto` ↔ `filetype`, and `ecs` → `sceneview`. Causes: `Vec3` and `Quat` live in `sceneview/SceneContent.kt` but are used by `ecs` and `projectView`; `SceneFileEditor` imports five `projectView` symbols (the Add Light group, the selection topic and helpers); `properties` imports 20 `sceneview` symbols. | import edge count |
| S4 | **The scene JSON layout is known in 14 files.** `SceneEcsPaths` (or `"ecs"`/`"components"` literals) is used in `SceneContent`, `RaySceneSnapshot`, `RayViewFeed`, `SceneTransformWriter`, `SceneRayEdits`, `ComponentEditor`, `LightEntities`, `PanelState`, `DtoTree`, `EntitySelection`, `AddLightAction` and the format check. A change to how entities are addressed touches all of them. | grep |
| S5 | **A split package across two modules.** `net.nevinsky.abyssus.core` holds 16 files in `gdx-model` (`ModelInstance`, `ModelBatch`, `AnimationController`, …) and 4 in `core` (`AbyssusProjectLayout`, `FileLoader`, `GeometryUtils`, `JsonProcessor`), and `core`'s other packages share the root with `gdx-model`'s `core.mesh`/`core.model`/`core.shader`. Readers cannot tell the `core` module from the `core.*` packages of `gdx-model`. 98 files import the four `core` helpers; 51 import `gdx-model`'s classes. | package scan |
| S6 | **Three in-memory scene shapes.** The edited JSON tree (`SceneJson`), the editor read model (`SceneContent` placements built from component codecs) and the Ashley `SceneEngine` that games and Play use, plus `RaySceneSnapshot` reading the JSON a fourth way. The first and third differ on purpose (the editor keeps unknown keys and number text; a game wants typed components), but there is no single typed read layer over the JSON for the editor itself. | architecture doc, `SceneContent`, `RaySceneSnapshot:123` |

## What Changes

Behavior-preserving, in four stages. Each stage ends with `./gradlew check` green and is its own set of commits, so
the work can stop after any stage.

- **Stage 1: break the cycles (S3, S5 small).** Move `Vec3`, `Quat` and the placement value types to a neutral package;
  invert `SceneFileEditor`'s calls into `projectView` through a small interface registered by the pane (or a topic);
  split what `properties` takes from `sceneview` into a read-only `SceneFacts` interface; move the four bare
  `core` helpers out of the shared root package. Add a Gradle `checkPackageCycles` task so cycles cannot return.
- **Stage 2: one typed scene document layer (S4, S6).** A `SceneDocument` read layer over the parsed JSON: entities,
  typed component access through the existing codecs, `lookAt` resolution, and the only code that knows the
  `ecs.entities.<id>.components` layout. `SceneContent`, `RaySceneSnapshot`, `PanelState`, the tree and the writers
  use it. The JSON tree, key order and number text stay the document's own; no new write path.
- **Stage 3: extract `editor-core`, a plain JVM module (S1, S2).** Move, in dependency order, the format and JSON
  layer, components and schemas editing, terrain generation, asset meta editing, scene content, picking, gizmo math,
  transform edits and the ray tracing bridge. The module depends on `core`, `runtime`, `raytracing` and `gdx-model`
  and may not import IntelliJ, Swing or AWT (the Gradle classpath enforces it). The plugin keeps only IDE glue:
  `editSceneJson`, tree, tool windows, dialogs, the canvas host, actions, file types and the VFS wiring.
- **Stage 4 (decision gate, may be dropped): `editor-render`.** The libGDX rendering pass (`SceneRenderer`, shadows,
  fog, sky, gizmo drawing) is IDE-independent too. Extract it only if one of these holds: a standalone viewer or a
  headless render test is wanted, or GL tests need to run without the IDE test framework. Otherwise it stays.

### Package names

New code goes to `net.nevinsky.abyssus.editor.*` (document, components, content, picking, terrain, meta, ray). Keeping
the old package names in a new module would reproduce S5, so this stage renames them; the moves are mechanical and each
slice carries its tests.

### Out of scope

- Behavior and file formats (`format: "abyssus"`, `formatVersion: 1`) are unchanged; every scene, `.abss` and
  `meta.json` write still goes through `editSceneJson`.
- Replacing the editor's JSON-based read model with the Ashley engine, and the reverse (see design D3).
- Renaming `gdx-model`'s own packages (`core.mesh`, `core.model`, …). Only the shared root package is cleaned up; a
  wider rename is recorded as an open decision.
- New features, and the module structure of `physics`, `physics-plugin` and the Control Line game.

## Capabilities

### New Capabilities

- `headless-scene-editing`: validating a native scene, project or `meta.json`, and applying the component, transform
  and asset-property edits, work on a project folder without the IDE running, with the same results the plugin
  produces.

### Modified Capabilities

None. Everything else is a regression check: `scene-component-editing`, `scene-object-transform`,
`object-properties-panel`, `scene-picking`, `terrain-authoring`, `scene-model-rendering`,
`abyssus-document-format`.

## Impact

- **New Gradle module** `editor-core` (and optionally `editor-render`); `settings.gradle.kts`, root `build.gradle.kts`,
  `physics-plugin` (compile-only on it, like `runtime`), CI and the Sonar/Kover wiring.
- **Plugin** loses about 60% of its main code to the new module; about 100 of the 114 plugin test files move with it
  (the Swing, tree and IDE-fixture tests stay).
- **Imports** change in most plugin files (package rename); no logic change.
- **Interaction with other work:**
  - `refactor-solid-dedup` runs first for phases 1–3 and 5 (they are small and make the moves cleaner); its phases 4 and
    7 (split `SceneViewPanel`, `ComponentEditor.kt`) are folded into stage 3 here and should not be done twice. Its
    task list says so.
  - Open changes (`add-scene-raytracing*`, `add-remote-asset-library`, `add-realistic-water`, `add-sky-clouds`,
    `add-weather-preset-creation`) touch `sceneview/` and the ray files. Stage 3 moves those files, so land or rebase
    them before stage 3, or each of them rebases onto the new paths.
- **Docs:** `AGENTS.md` layout and hard rules, `docs/ai/architecture.md`, `conventions.md`, `testing.md`, package
  READMEs; a new `editor-core/README.md`.
- **No new third-party dependencies.** A Gradle import-scan task replaces any need for an architecture-test library.
