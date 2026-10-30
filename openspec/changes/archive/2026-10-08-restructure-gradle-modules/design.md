# Design

## Context

Today `settings.gradle.kts` includes `:gdx-model`, `:core`, `:raytracing`, `:runtime`, `:physics`, `:physics-plugin` and
`:games:control-line`, and the root project is the Abyssus plugin. The root build script applies the IntelliJ
Platform, grammarkit, changelog, kover and shadow plugins with their versions. Subprojects apply them without
versions. Several builds reach into the root by path:

- `rootProject.file("src/test/testData")` in `core`, `runtime`, `physics` and `physics-plugin`.
- `localPlugin(project(":"))` in `physics-plugin`.
- `checkNoRunCatching`, which scans `core/src/main/kotlin`.
- `patchPluginXml`, which reads `README.md`, and the changelog plugin, which reads `CHANGELOG.md`.

Plugin tests also open fixtures by the relative path `src/test/testData/...`, which only works while the plugin is
the root project.

## Goals / Non-Goals

**Goals:**
- One `git mv` per module plus build edits, so history follows the files (`git log --follow`).
- `./gradlew check` runs the same tests and checks as before. Only their project paths change.

**Non-Goals:**
- No Gradle convention plugins (`buildSrc` / included build). Each module keeps its own build script. Sharing build
  logic is a separate refactor.
- No package or class renames.

## Decisions

### Flat project paths, nested folders

`settings.gradle.kts` includes each module by its short name and points it at its folder:

```kotlin
listOf("plugin-abyssus", "plugin-abyssus-physics", "lib-gdx-model", "lib-core", "lib-runtime",
    "lib-physics", "lib-raytracing", "app-control-line-game").forEach {
    include(":$it")
    project(":$it").projectDir = file("projects/$it")
}
```

**Why:** commands stay short (`./gradlew :lib-core:test`), and the prefix already says what kind of module it is.

**Alternative:** `include(":projects:lib-core")` gives three-part paths (`:projects:lib-core:test`) and an empty
`:projects` project, the same awkwardness `:games:control-line` has today.

### The root becomes a build-only project

The root `build.gradle.kts` keeps only the `plugins { ... apply false }` block that pins plugin versions, plus the
`wrapper` configuration. Everything else in it moves to `projects/plugin-abyssus/build.gradle.kts` unchanged, except
where it reads root files:

- `patchPluginXml` reads `rootProject.file("README.md")`, so the `<!-- Plugin description -->` markers stay in the
  root README.
- `changelog { path = rootProject.file("CHANGELOG.md") }`.
- `checkNoRunCatching` scans `projects/plugin-abyssus/src/main/kotlin` and `projects/lib-core/src/main/kotlin`, the same
  coverage as today.
- `src/main/gen` stays relative to the plugin module (`projects/plugin-abyssus/src/main/gen`).

`group` and `version` stay in `gradle.properties` and are read by `:plugin-abyssus`. CI reads them with
`./gradlew :plugin-abyssus:properties`.

**Alternative:** keeping the plugin as the root project with only the libraries moved. Rejected because you asked for
it to be its own module, and a root that is also a plugin is what makes `:test --tests` fragile today.

### Fixtures at `testData/`, found by system property everywhere

Every module's `test` task sets `abyssus.testData` to `rootProject.file("testData")`. Libraries already do this.
Plugin tests stop using the relative `src/test/testData` and resolve it through one test helper that reads the
property, failing with a clear message when the property is missing. `getTestDataPath()` overrides return the
property's value. `physics-plugin` already sets the property and only needs the new path.

**Alternative:** setting `workingDir = rootDir` for plugin tests so the paths become `testData/...`. Rejected because
the IntelliJ test framework writes its sandbox and logs relative to the working folder. Pointing that at the repo root
would scatter files there.

### Control Line: module folder is the native project

`gameProject = layout.projectDirectory`, so `run`, `test`, `exportComponentSchema`, `exportPlay`,
`generatePlaneModels` and `generateField` all point at the module folder. `Main.kt` falls back to the working folder
(`"."`). Abyssus finds a project from what sits beside the `.abss` (`scenes/`, `assets/`) and never recurses, so
`src/`, `tools/` and `build/` beside it are harmless. The git-ignored `play.json` moves to
`projects/app-control-line-game/abyssus/play.json`.

### The physics plugin moves but keeps its role

`physics-plugin` becomes `projects/plugin-abyssus-physics` with `localPlugin(project(":plugin-abyssus"))`. Its plugin
ID and name stay the same. `merge-physics-into-abyssus` then removes the module.

## Risks / Trade-offs

- **Contributors' muscle memory:** old commands such as `./gradlew :core:test` fail with "project not found". This is
  accepted: the failure is immediate and the AGENTS.md command table is updated in the same change.
- **Open changes keep old paths:** their tasks would point at missing files. The edits are mechanical path
  replacements, done in this change as config requires. Their checked boxes and meaning stay untouched.
- **IDE caches and `.idea/`:** a re-import is needed after pulling. `.idea/` is git-ignored, so there is nothing to
  migrate in the repo.
- **Configuration cache:** subprojects must not read `rootProject` state at execution time. Only file paths resolved
  at configuration time are used, as today.
- **Kover and the plugin verifier write reports under the plugin module:** CI upload paths change. If a path is
  missed, the step fails loudly, and the build workflow is checked in the final task.

## Migration Plan

This is one commit series on a branch: move the modules with `git mv`, edit the builds, run
`./gradlew check` and `scripts/check-docs.sh`. Rolling back means reverting the series. No file formats or user
projects are affected.
