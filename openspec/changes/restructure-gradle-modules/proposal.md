# Proposal

## Why

The repository root is both the build and the Abyssus plugin, while libraries, a second plugin and a game sit beside
it under unrelated names (`core`, `physics-plugin`, `games/control-line`). Nothing in a module's name says whether it
is a plain library, an IntelliJ plugin or an application. The layout should make that obvious before
`merge-physics-into-abyssus` folds the physics plugin into Abyssus.

## What Changes

- Every Gradle module moves under `projects/`. The root keeps only the build settings (`settings.gradle.kts`,
  `gradle.properties`, the wrapper), the repository docs (`README.md`, `CHANGELOG.md`, `AGENTS.md`, `docs/`,
  `openspec/`), `scripts/` and the shared fixtures.
- The Abyssus plugin moves out of the root into `projects/plugin-abyssus` (`:plugin-abyssus`): its `src/`, its build
  script and the GLTF grammar generation.
- Modules are renamed by role. Each project path matches its folder name:

  | Today | New folder | New project path |
  |---|---|---|
  | root project | `projects/plugin-abyssus` | `:plugin-abyssus` |
  | `physics-plugin` | `projects/plugin-abyssus-physics` (until `merge-physics-into-abyssus`) | `:plugin-abyssus-physics` |
  | `gdx-model` | `projects/lib-gdx-model` | `:lib-gdx-model` |
  | `core` | `projects/lib-core` | `:lib-core` |
  | `runtime` | `projects/lib-runtime` | `:lib-runtime` |
  | `physics` | `projects/lib-physics` | `:lib-physics` |
  | `raytracing` | `projects/lib-raytracing` | `:lib-raytracing` |
  | `games/control-line` | `projects/app-control-line-game` | `:app-control-line-game` |

- The Control Line module folder becomes the native project itself. `ControlLine.abss`, `scenes/`, `assets/` and
  `abyssus/` move up from `project/ControlLine` and sit beside `build.gradle.kts` and `src/`.
- The shared test fixtures move from `src/test/testData` to `testData/` at the repository root. All modules read
  them from there.
- **BREAKING (contributors):** every Gradle command changes. For example, `./gradlew :core:test` becomes
  `./gradlew :lib-core:test`, `./gradlew runIde` becomes `./gradlew :plugin-abyssus:runIde`, and the plugin zip lands in
  `projects/plugin-abyssus/build/distributions/`. CI, run configurations, docs and open changes are updated to
  match.
- Out of scope:
  - Kotlin package names stay as they are (`net.nevinsky.abyssus.assets`, `...runtime`, `...physics`,
    `...games.controlline`).
  - Plugin IDs, plugin names, the plugin zip contents and published artifact coordinates stay the same.
  - Merging the physics plugin into Abyssus belongs to `merge-physics-into-abyssus`.

Native files: this change reads and writes no `.abss`, `.scene` or `meta.json` fields. The fixture and Control Line
documents move byte for byte. Their validation and format version stay the same.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. No requirement changes: this is a build-layout refactor, so `.openspec.yaml` sets `skip_specs: true`. The main
specs name no module paths. The fixture path in `openspec/config.yaml` and the paths in other open changes are
updated as tasks.

## Impact

- Build: `settings.gradle.kts`, every `build.gradle.kts`, and the root build script, which becomes plugin-version
  declarations only. Cross-module checks with hardcoded paths change too: `checkNoRunCatching` scans `lib-core`.
- Tests: the fixture paths in plugin tests (37 files use `src/test/testData` relative to the working folder) and in
  every module's `abyssus.testData` system property. Control Line's `controlLine.project` and the `Main.kt` fallback
  path also change.
- CI and tooling:
  - `.github/workflows/build.yml`, `release.yml` and `run-ui-tests.yml`: report, distribution and verifier paths, plus
    qualified Gradle tasks;
  - `.run/*.run.xml`;
  - `.gitignore` (`src/main/gen`, Control Line's `play.json`).
- Docs: `AGENTS.md`, `README.md`, `docs/ai/*.md`, the module READMEs (which move with their modules), the package
  READMEs and `openspec/config.yaml`.
- Open changes that name the old paths: `add-scene-raytracing`, `add-scene-raytracing-settings`, `add-sky-clouds`,
  `add-realistic-water`, `add-project-fps-counter`, `add-weather-preset-creation` and `add-remote-asset-library`.
