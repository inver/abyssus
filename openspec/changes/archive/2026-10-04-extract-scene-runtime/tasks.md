# Tasks

## 0. Before moving anything

- [x] 0.1 Confirm `design-review-refactor` is archived, or that the user has chosen to run this change first. In that
      case, amend `design-review-refactor`'s open tasks and design so each path this change moves names its `runtime`
      location. Verify: `openspec list --json` no longer lists `design-review-refactor`, or
      `openspec validate design-review-refactor --strict` passes after the amendment and the choice is recorded here
- [x] 0.2 Confirm no other session is editing `src/main/kotlin/net/nevinsky/abyssus/{ecs,scene,dto}`. Verify:
      `git status --short src/main` is empty and the user's answer is recorded here

Verification: `design-review-refactor` is archived; `git status --short src/main` was empty before edits. The user confirmed no other session is editing these areas and requested this checkout. The user also authorized adding spotlight entity 8 to the shared fixture and continuing despite the recorded baseline failures.

## 1. The module

- [x] 1.1 Add `:runtime` as design decision 1: `settings.gradle.kts` includes it; `runtime/build.gradle.kts` with
      `api(project(":core"))`, `api` Ashley 1.7.4, JUnit 4, the `abyssus.testData` system property, and a
      `checkNoSingletons` task wired into `check` (the same regex as `core`'s). The plugin gets
      `implementation(project(":runtime"))` with the Kotlin / SLF4J excludes and drops its own Ashley line. Verify:
      `./gradlew :runtime:build :buildPlugin` succeeds and `unzip -l build/distributions/*.zip | grep -c ashley`
      prints `1`
- [x] 1.2 Add `runtime`'s test helper `testProject(name)` (as in `core/src/test/.../TestData.kt`). Verify: a
      `TestDataTest` case finds `Untitled/Untitled.abss` with `./gradlew :runtime:test`

## 2. The ECS

- [x] 2.1 `git mv` `ecs/component/`, `ecs/render/`, `ecs/system/`, `EcsConfigurator.kt`, `EcsUtils.kt` and, from
      `ecs/scene/`, `SceneEngine`, `SceneEcsLoader`, `SceneEcsWriter`, `ComponentCodecs`, `SceneEcsWarnings` (plus
      `ComponentDefaults` / `SceneEcsPaths` if `design-review-refactor` added them) to `runtime` under
      `net.nevinsky.abyssus.lib.runtime.ecs`, applying design decision 3: remove `WorldUtils`, add
      `FolderAssetResolver`, turn the delegate's class name into a top-level `const val`, and give
      `SceneEcsWarnings` an `AssetLog`. `ComponentEditor` and `LightEntities` stay and import the new packages. Verify:
      `./gradlew :runtime:checkNoSingletons :compileKotlin` pass
- [x] 2.2 Move `ColorDto`, `FogDto` and the light DTOs to `runtime.scene`. Verify: `./gradlew :runtime:compileKotlin
      :compileKotlin` pass and `grep -rn "net.nevinsky.abyssus.scene\.\(ColorDto\|FogDto\)" src/main` finds nothing
- [x] 2.3 Move `ComponentCodecsTest`, `ComponentsTest`, `SceneEcsLoaderTest`, `SceneEcsWriterTest` and `SystemsTest`
      to `runtime/src/test` (plain JUnit, fixtures through `testProject`); loader tests pass a recording `AssetLog`.
      Add `SceneEcsLoaderTest.unmodeledComponentsAreLoggedOnceToTheCallersLog` (`PickableComponent` and
      `DependenciesComponent` of `Main Scene`, one message each). Verify:
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.lib.runtime.ecs.*'` passes, and
      `./gradlew :test --tests 'net.nevinsky.abyssus.ecs.ComponentEditorTest' --tests
      'net.nevinsky.abyssus.ecs.LightEntitiesTest'` passes

## 3. Project and scene reading

- [x] 3.1 Add `runtime.project.ProjectFolder` (the layout constants as top-level `const val`, `.abss` lookup, scene
      listing sorted by file name over `Path`) and `runtime.scene.SceneParser`; move `SceneDto` to `runtime.scene`
      without `file`. Point the plugin's `ProjectLayout` constants at `ProjectFolder`'s. Verify: `ProjectFolderTest`
      (`Untitled` lists `Main Scene.scene`; a folder without `.abss` has no project) and `SceneParserTest`
      (`Main Scene` is named `Ololo` with sky `skybox_physical`; a non-JSON text fails with a message) pass with
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.lib.runtime.*'`
- [x] 3.2 Add the composition root `SceneLoading(json, log)` with `project`, `projectName`, `parse` and both `load`
      forms (design decision 2). Cover the `scene-loading` spec in `SceneLoadingTest`:
      `untitledProjectReadsOutsideTheIde`, `mainScenesEntitiesLoadOutsideTheIde` (ids 0 to 8, entity 0 `Model 0`
      `OBJECT` rendering `model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`, entity 8 `Spot Light 8` `LIGHT_SPOT`),
      `unsavedTextLoadsWithoutReadingTheFile` (entity 0 renamed `Plane`, file bytes unchanged),
      `anUnreadableSceneIsLoggedOnceAndOthersLoad` (in a temp copy of `Untitled`), and
      `twoLoadsShareNothing` (`Untitled` and `Animated/scenes/Main.scene` on two threads with two logs). Verify:
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.lib.runtime.SceneLoadingTest'` passes

## 4. Plugin wiring

- [x] 4.1 `AbyssusCore` builds one `SceneLoading` from its `JsonProcessor` and an `AssetLog` writing
      `Logger.getInstance("Abyssus.scenes")`. `SceneReader` delegates `parse` to it; `ProjectReader` reads the
      `.abss` name through `projectName` and keeps its VFS listing and stamping (design decision 5). Verify:
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.dto.*'` passes
- [x] 4.2 Add the plugin's `SceneEntry(file, scene)`; `ProjectReader` puts it into `ProjectDto.scenes`; `DtoTree`,
      `AbyssusNodes`, `EnabledToggle`, `AbyssusFooter`, `SkyboxChoices` read the scene and file from it (design
      decision 4). Verify: `./gradlew :test --tests 'net.nevinsky.abyssus.AbyssusViewTest' --tests
      'net.nevinsky.abyssus.AbyssusProjectViewPaneTest' --tests 'net.nevinsky.abyssus.projectView.*'` shows no
      failure `HEAD` does not already have
- [x] 4.3 Update the remaining imports (`SceneContent`, `SceneRenderParams`, the properties panel, `ComponentActions`,
      `SceneComponentEdits`, `AddLightAction`). Verify: `./gradlew :compileKotlin :compileTestKotlin` pass and
      `grep -rn "net.nevinsky.abyssus.ecs\.\(component\|render\|system\)\|abyssus.scene.SceneDto" src/` finds nothing

## 5. Documentation

- [x] 5.1 Update `AGENTS.md` (layout: the `runtime` module; commands: `./gradlew :runtime:test`; hard rules:
      `runtime` stays a plain JVM library with no singletons), `docs/ai/architecture.md` (modules, data flow,
      threads), `docs/ai/testing.md` (`runtime` tests and fixtures), `src/main/kotlin/net/nevinsky/abyssus/ecs/README.md`
      (only `ComponentEditor` and `LightEntities` remain; the rest points at `runtime`), and add `runtime/README.md`
      like `core/README.md`. Verify: `scripts/check-docs.sh` passes and `grep -rn "ecs/component\|ecs/system"
      docs AGENTS.md` names only `runtime` paths

## 6. Integration

- [ ] 6.1 In a copy of the `Untitled` project (never `src/test/testData/project/Untitled` itself), run
      `./gradlew runIde` and check: (1) the Abyssus view shows the project, `Main Scene`, its entities and the eye
      toggles as before; (2) the properties panel shows and edits a light's intensity, and Undo restores it;
      (3) Add Light adds a spot light; (4) the scene view draws `Main Scene` as before; (5) a scene file made invalid
      by hand logs one warning in `idea.log` naming it
- [ ] 6.2 Run `./gradlew check` (all modules, both `checkNoSingletons` tasks) and `scripts/check-docs.sh`. Verify:
      both pass

## Verification record

- Task 6.2 rerun: `./gradlew check` exited 1 (738 plugin tests: 12 failed, 39 skipped;
  173 raytracing tests: 1 failed, 92 skipped). Both singleton checks passed. `scripts/check-docs.sh`
  passed (139 paths in 6 files). The failures match those recorded below; task 6.2 remains unchecked.
  Full output: `/private/tmp/abyssus-task-6-2-check.log`.
- Runtime tests and singleton check pass; plugin DTO, component-editor/light and project-view adapter suites pass.
- The tree/pane suite has only the baseline `AbyssusViewTest.testNodeTree` failure (stale seven-entity expectation;
  the baseline already had eight, and the authorized spotlight addition makes nine).
- Plugin ZIP builds with exactly one `ashley-1.7.4.jar`. Documentation path check passes.
- Full `check --continue` still fails in twelve plugin tests present in the baseline: the tree assertion, two
  `SceneContentTest` fixture assertions, two `SceneMarkersTest` camera assertions and seven
  `TerrainGenerationPanelTest` cases. `SceneEcsLoaderTest.mainSceneLoads`, which also failed in the baseline,
  now passes in runtime with the nine-entity fixture expectation.
- `VulkanNativePackagingTest.jarHoldsValidSpirvAndNoShaderCompiler` also fails because the packaged SPIR-V resource
  is absent (its assertion asks for glslangValidator/glslc). No raytracing files were changed.
- Independent code review found no critical or important extraction issue. Its editor-read logging gap was covered
  by regression tests and fixed with source-aware text callbacks.
- Task 6.1 remains open: this session has no desktop interaction tool to perform the five manual IDE checks listed
  above. Task 6.2 remains open because the full check has the failures listed here.

Archive approval: the user confirmed continuing with spec sync and archive despite open tasks 6.1 and 6.2 and the recorded test failures. The incomplete checkboxes remain unchanged.
