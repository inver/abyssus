# Tasks

## 0. Before moving anything

- [ ] 0.1 Confirm `design-review-refactor` is archived, or that the user has chosen to run this change first. In that
      case, amend `design-review-refactor`'s open tasks and design so each path this change moves names its `runtime`
      location. Verify: `openspec list --json` no longer lists `design-review-refactor`, or
      `openspec validate design-review-refactor --strict` passes after the amendment and the choice is recorded here
- [ ] 0.2 Confirm no other session is editing `src/main/kotlin/net/nevinsky/abyssus/{ecs,scene,dto}`. Verify:
      `git status --short src/main` is empty and the user's answer is recorded here

## 1. The module

- [ ] 1.1 Add `:runtime` as design decision 1: `settings.gradle.kts` includes it; `runtime/build.gradle.kts` with
      `api(project(":core"))`, `api` Ashley 1.7.4, JUnit 4, the `abyssus.testData` system property, and a
      `checkNoSingletons` task wired into `check` (the same regex as `core`'s). The plugin gets
      `implementation(project(":runtime"))` with the Kotlin / SLF4J excludes and drops its own Ashley line. Verify:
      `./gradlew :runtime:build :buildPlugin` succeeds and `unzip -l build/distributions/*.zip | grep -c ashley`
      prints `1`
- [ ] 1.2 Add `runtime`'s test helper `testProject(name)` (as in `core/src/test/.../TestData.kt`). Verify: a
      `TestDataTest` case finds `Untitled/Untitled.abss` with `./gradlew :runtime:test`

## 2. The ECS

- [ ] 2.1 `git mv` `ecs/component/`, `ecs/render/`, `ecs/system/`, `EcsConfigurator.kt`, `EcsUtils.kt` and, from
      `ecs/scene/`, `SceneEngine`, `SceneEcsLoader`, `SceneEcsWriter`, `ComponentCodecs`, `SceneEcsWarnings` (plus
      `ComponentDefaults` / `SceneEcsPaths` if `design-review-refactor` added them) to `runtime` under
      `net.nevinsky.abyssus.runtime.ecs`, applying design decision 3: remove `WorldUtils`, add
      `FolderAssetResolver`, turn the delegate's class name into a top-level `const val`, and give
      `SceneEcsWarnings` an `AssetLog`. `ComponentEditor` and `LightEntities` stay and import the new packages. Verify:
      `./gradlew :runtime:checkNoSingletons :compileKotlin` pass
- [ ] 2.2 Move `ColorDto`, `FogDto` and the light DTOs to `runtime.scene`. Verify: `./gradlew :runtime:compileKotlin
      :compileKotlin` pass and `grep -rn "net.nevinsky.abyssus.scene\.\(ColorDto\|FogDto\)" src/main` finds nothing
- [ ] 2.3 Move `ComponentCodecsTest`, `ComponentsTest`, `SceneEcsLoaderTest`, `SceneEcsWriterTest` and `SystemsTest`
      to `runtime/src/test` (plain JUnit, fixtures through `testProject`); loader tests pass a recording `AssetLog`.
      Add `SceneEcsLoaderTest.unmodeledComponentsAreLoggedOnceToTheCallersLog` (`PickableComponent` and
      `DependenciesComponent` of `Main Scene`, one message each). Verify:
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.runtime.ecs.*'` passes, and
      `./gradlew :test --tests 'net.nevinsky.abyssus.ecs.ComponentEditorTest' --tests
      'net.nevinsky.abyssus.ecs.LightEntitiesTest'` passes

## 3. Project and scene reading

- [ ] 3.1 Add `runtime.project.ProjectFolder` (the layout constants as top-level `const val`, `.abss` lookup, scene
      listing sorted by file name over `Path`) and `runtime.scene.SceneParser`; move `SceneDto` to `runtime.scene`
      without `file`. Point the plugin's `ProjectLayout` constants at `ProjectFolder`'s. Verify: `ProjectFolderTest`
      (`Untitled` lists `Main Scene.scene`; a folder without `.abss` has no project) and `SceneParserTest`
      (`Main Scene` is named `Ololo` with sky `skybox_physical`; a non-JSON text fails with a message) pass with
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.runtime.*'`
- [ ] 3.2 Add the composition root `SceneLoading(json, log)` with `project`, `projectName`, `parse` and both `load`
      forms (design decision 2). Cover the `scene-loading` spec in `SceneLoadingTest`:
      `untitledProjectReadsOutsideTheIde`, `mainScenesEntitiesLoadOutsideTheIde` (ids 0 to 8, entity 0 `Model 0`
      `OBJECT` rendering `model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`, entity 8 `Spot Light 8` `LIGHT_SPOT`),
      `unsavedTextLoadsWithoutReadingTheFile` (entity 0 renamed `Plane`, file bytes unchanged),
      `anUnreadableSceneIsLoggedOnceAndOthersLoad` (in a temp copy of `Untitled`), and
      `twoLoadsShareNothing` (`Untitled` and `Animated/scenes/Main.scene` on two threads with two logs). Verify:
      `./gradlew :runtime:test --tests 'net.nevinsky.abyssus.runtime.SceneLoadingTest'` passes

## 4. Plugin wiring

- [ ] 4.1 `AbyssusCore` builds one `SceneLoading` from its `JsonProcessor` and an `AssetLog` writing
      `Logger.getInstance("Abyssus.scenes")`. `SceneReader` delegates `parse` to it; `ProjectReader` reads the
      `.abss` name through `projectName` and keeps its VFS listing and stamping (design decision 5). Verify:
      `./gradlew :test --tests 'net.nevinsky.abyssus.dto.*'` passes
- [ ] 4.2 Add the plugin's `SceneEntry(file, scene)`; `ProjectReader` puts it into `ProjectDto.scenes`; `DtoTree`,
      `AbyssusNodes`, `EnabledToggle`, `AbyssusFooter`, `SkyboxChoices` read the scene and file from it (design
      decision 4). Verify: `./gradlew :test --tests 'net.nevinsky.abyssus.AbyssusViewTest' --tests
      'net.nevinsky.abyssus.AbyssusProjectViewPaneTest' --tests 'net.nevinsky.abyssus.projectView.*'` shows no
      failure `HEAD` does not already have
- [ ] 4.3 Update the remaining imports (`SceneContent`, `SceneRenderParams`, the properties panel, `ComponentActions`,
      `SceneComponentEdits`, `AddLightAction`). Verify: `./gradlew :compileKotlin :compileTestKotlin` pass and
      `grep -rn "net.nevinsky.abyssus.ecs\.\(component\|render\|system\)\|abyssus.scene.SceneDto" src/` finds nothing

## 5. Documentation

- [ ] 5.1 Update `AGENTS.md` (layout: the `runtime` module; commands: `./gradlew :runtime:test`; hard rules:
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
