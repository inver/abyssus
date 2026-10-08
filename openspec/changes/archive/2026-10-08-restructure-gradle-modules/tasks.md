# Tasks

Moves use `git mv` so history follows the files. After this change, single plugin tests run with
`./gradlew :plugin-abyssus:test --tests '<class>'`. Until group 2 is done, use the old project paths.

## 1. Move the libraries and the game

- [x] 1.1 `git mv` `gdx-model` to `projects/lib-gdx-model`, `core` to `projects/lib-core`, `runtime` to
  `projects/lib-runtime`, `physics` to `projects/lib-physics` and `raytracing` to `projects/lib-raytracing`. In
  `settings.gradle.kts`, include each one by its new short path with `projectDir` set. Update `project(":...")`
  references in every build script, including `testFixtures(project(":lib-core"))`. Verify with
  `./gradlew :lib-gdx-model:test :lib-core:test :lib-runtime:test :lib-physics:test :lib-raytracing:test`, and run
  `./gradlew :lib-core:checkNoSingletons :lib-runtime:checkNoSingletons :lib-physics:checkNoSingletons`.
- [x] 1.2 `git mv` `games/control-line` to `projects/app-control-line-game`. Move `project/ControlLine/*`
  (`ControlLine.abss`, `scenes/`, `assets/`, `abyssus/components.schema.json`) up into the module folder, and remove
  the empty `games/` and `project/` folders. Set `gameProject = layout.projectDirectory`, change `Main.kt`'s fallback
  to `"."` and its KDoc, and update the task names in `tools/*.kt` comments. Verify with
  `./gradlew :app-control-line-game:test`. Also run `./gradlew :app-control-line-game:exportAbyssus` and verify that
  `projects/app-control-line-game/abyssus/play.json` is written and `components.schema.json` is unchanged
  (`git diff --exit-code`).

## 2. Extract the Abyssus plugin into `projects/plugin-abyssus`

- [x] 2.1 `git mv src projects/plugin-abyssus/src`. Move everything in the root `build.gradle.kts` except the
  version-pinning `plugins { ... apply false }` block and `wrapper` into `projects/plugin-abyssus/build.gradle.kts`.
  Make these changes there:
  - `patchPluginXml` reads `rootProject.file("README.md")`;
  - `changelog` reads `rootProject.file("CHANGELOG.md")`;
  - `checkNoRunCatching` scans the new `lib-core` path.

  Include `:plugin-abyssus` in settings, and change `.gitignore`'s `src/main/gen` to
  `projects/plugin-abyssus/src/main/gen`. Verify with `./gradlew :plugin-abyssus:buildPlugin`, then check the zip
  under `projects/plugin-abyssus/build/distributions/`: it has the same `lib/` jar set as a build from the base
  branch (compare `unzip -l` listings), and its `plugin.xml` has the README description.
- [] 2.2 Move the shared fixtures with `git mv projects/plugin-abyssus/src/test/testData testData`, and point every
  library's `abyssus.testData` at `rootProject.file("testData")`. Add a plugin test helper that resolves the fixture
  folder from `abyssus.testData`, set that property in `:plugin-abyssus`'s `test` task, and replace the 37 relative
  `src/test/testData` uses and the `getTestDataPath()` overrides with it. Verify with `./gradlew :plugin-abyssus:test`
  and the library and game tests from 1.1 and 1.2. The plugin test count matches the base branch (compare the counts
  from `build/test-results`).
- [x] 2.3 `git mv physics-plugin projects/plugin-abyssus-physics`, include it as `:plugin-abyssus-physics`, and change
  `localPlugin(project(":"))` to `localPlugin(project(":plugin-abyssus"))`, its `:physics`/`:runtime` references and its `abyssus.testData` path.
  Verify with `./gradlew :plugin-abyssus-physics:test` (including `BundledPlayHostTest`) and
  `./gradlew :plugin-abyssus-physics:checkNoJolt`.
- [x] 2.4 Manual check 1: `./gradlew :plugin-abyssus-physics:runIde -PideProject=<copy of testData/project/Physics>`.
  Open `Main Scene`. The Scene view renders, Show Physics draws colliders, and Play starts and stops.

## 3. CI, run configurations and tooling

- [ ] 3.1 Update `.github/workflows/build.yml`:
  - read properties through `./gradlew :plugin-abyssus:properties`;
  - use the `projects/plugin-abyssus/build/...` paths for test reports, the Kover XML, `listProductsReleases.txt`,
    the verifier reports and `distributions`.

  Update `release.yml`'s upload path and `run-ui-tests.yml`'s task (`:plugin-abyssus:runIdeForUiTests`). Verify by
  checking each path against a local `./gradlew :plugin-abyssus:check :plugin-abyssus:buildPlugin
  :plugin-abyssus:listProductsReleases` (every referenced file exists), then on the branch's CI run.
- [x] 3.2 Update `.run/*.run.xml`: task names qualified with `:plugin-abyssus:`, and `-PideProject` pointing at
  `$PROJECT_DIR$/testData/project/...`. Verify that each configuration's tasks resolve:
  `./gradlew :plugin-abyssus:runIde --dry-run`, `:plugin-abyssus:runIdeForUiTests --dry-run`,
  `:plugin-abyssus:runPluginVerifier --dry-run`.
- [] 3.3 Change `.gitignore`'s Control Line `play.json` entry to `projects/app-control-line-game/abyssus/play.json`.
  Verify that after 1.2's `exportAbyssus`, `git status --porcelain` shows no `play.json`.

## 4. Docs and open changes

- [x] 4.1 Update `AGENTS.md`: the command table, the layout section, and the hard rules that name `src/main/gen`,
  `src/test/testData/project/Untitled` and the module names. Update `docs/ai/architecture.md`, `docs/ai/testing.md`,
  `docs/ai/conventions.md` and `docs/ai/file-formats.md`, the module READMEs (moved with their modules) and the
  package READMEs under `projects/plugin-abyssus/src/main/kotlin/...`. Update `README.md`'s build instructions and keep
  its plugin description markers. Verify with `scripts/check-docs.sh` and `./gradlew :plugin-abyssus:patchPluginXml`.
- [x] 4.2 Update `openspec/config.yaml`'s fixture path (`testData/project/Untitled`) and command examples. Verify
  with `grep -n "src/test/testData\|:test --tests" openspec/config.yaml`, which should return nothing stale.
- [x] 4.3 Replace the old module and fixture paths and Gradle task paths in the open changes `add-scene-raytracing`,
  `add-scene-raytracing-settings`, `add-sky-clouds`, `add-realistic-water`, `add-project-fps-counter`,
  `add-weather-preset-creation` and `add-remote-asset-library`, without touching checkbox state. Verify that
  `grep -rnE "src/test/testData|games/control-line|physics-plugin|:core:|:runtime:|:physics:|(^|[^-/])(core|runtime|raytracing|gdx-model)/src" openspec/changes --include=*.md | grep -v archive | grep -v restructure-gradle-modules | grep -v merge-physics-into-abyssus`
  returns nothing, and that `openspec validate --all` passes.

## 5. Integration

- [ ] 5.1 Run `./gradlew check` and `scripts/check-docs.sh` from a clean clone of the branch. Both pass, and the test
  count matches the base branch.
