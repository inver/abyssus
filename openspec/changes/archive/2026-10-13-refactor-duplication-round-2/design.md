# Design

## Context

### Current state (survey of 2026-10-06, re-checked 2026-10-12 at `HEAD` `2297268c`)

Seven Gradle modules under `projects/`, named by `settings.gradle.kts` (`lib-*`, `plugin-*`, `app-*`). The folders
`lib-gdx-model`, `lib-runtime` and `plugin-abyssus-physics` still exist on disk but hold only `build/` leftovers: their
code moved into `lib-gdx` (package `net.nevinsky.abyssus.lib.gdx`), `lib-core` and `plugin-abyssus`. Sizes of the
two classes this change splits are given under S1 and S2.

| Module | Root package |
|---|---|
| `plugin-abyssus` | `net.nevinsky.abyssus.plugin` |
| `lib-gdx` | `net.nevinsky.abyssus.lib.gdx` |
| `lib-core-editor` | `net.nevinsky.abyssus.lib.core.editor` |
| `lib-core` | `net.nevinsky.abyssus.lib.core` |
| `lib-raytracing` | `net.nevinsky.abyssus.lib.raytracing` |
| `app-game-control-line` | `net.nevinsky.abyssus.app.game.controlline` |
| `lib-physics` | `net.nevinsky.abyssus.lib.physics` |

Dependencies: `lib-core` → `lib-gdx`; `lib-core-editor` → `lib-core`, `lib-raytracing`, `lib-gdx`; the plugin → all
(`lib-physics` without transitive dependencies). The root `build.gradle.kts` applies Kotlin, Kover,
`gradle/checks.gradle.kts` (`checkNoSingletons`, `checkNoRunCatching`) and `gradle/package-cycles.gradle.kts` to every
module.

What the first round (`refactor-solid-dedup`, archived) already fixed and this change keeps: one `meta.json` binder,
`parseUuidOrNull`, `displayMessage`, named play frames, shared Gradle checks, grouped core services, bundle-backed
action text, verifier wiring. Its remaining open items (4.1/4.2/7.1/7.2) went to `restructure-editor-modules` 3.1. That
task is ticked, but `SceneViewPanel` (now 882 lines) and `AssetPropertiesPanel` (668) were not split (S1, S2).

### Survey method and coverage

- All modules except `lib-gdx` (inherited code, out of scope) were read at file-list and function-list level.
  The 40 largest files and every hit below were read in full.
- Literal duplication: a normalized 8-line sliding-window scan over every `src/**/*.kt` (imports, braces and comments
  dropped). It found 8 clone groups; each is classified below or listed under non-findings.
- Semantic duplication: targeted searches (`CompositeAssetLoader(`, `MetaType.X to`, `currentProjectViewPane`,
  `UUID.fromString`, vector helpers, `JsonMapper.builder`, `abyssus.testData`, `typealias`).
- Baseline: `scripts/check-docs.sh` reported 85 broken paths in the first survey; on 2026-10-12 it passes (248 paths).
  Gradle did not run in either survey (first: plugin resolution from Maven Central failed with HTTP 429). Build and CI findings B2 are static reads, so task 1.1 confirms them
  first.

## Goals / Non-Goals

**Goals**

- A green, trustworthy baseline: `./gradlew check`, `verifyPlugin` and `scripts/check-docs.sh` pass, and CI and the run
  configurations name tasks and paths that exist.
- Each duplicated rule listed below exists once, in the lowest module that all its users already depend on.
- Two large UI classes split along seams they already have, with no behavior change.
- Patterns only where one already exists (Template Method `AbyssusTreeAction`, Composite `CompositeAssetLoader`) or
  where a plain factory function removes a copied table.

**Non-Goals**

- Behavior, file format, plugin IDs, extension points and published artifacts all stay as they are.
- New module boundaries (the move is done), coroutine rewrites, logging changes.
- Revisiting `refactor-solid-dedup` decisions D3 (field-type switches), D8 (backend seams) and D6 (core groups).

## Findings

Evidence paths are relative to `projects/` unless they start with a root folder. `P` = `src/main/kotlin/net/nevinsky/abyssus`.

### Baseline integrity (must-fix)

**B1. Docs describe the pre-move tree (resolved).** The first survey found 85 broken paths. Since then the docs were
rewritten for the as-built tree and `scripts/check-docs.sh` passes (248 paths in 6 files). Nothing to do here except keep
it green; later phases update only the docs they make wrong.

**B2. Build and CI drift.**
- `gradle/plugin-verification.gradle.kts:2` resolves `gradle/plugin-internal-api-allowlist.txt` with
  `layout.projectDirectory`, which is `projects/plugin-abyssus` when applied there
  (`projects/plugin-abyssus/plugin-abyssus.gradle.kts`). It has no `gradle/` folder.
  `gradle/package-cycles.gradle.kts:5` uses `rootProject.layout`, which is right. *Impact:* `checkPluginInternalApis`
  (the finalizer of `verifyPlugin`) declares a missing input and cannot pass.
- `.github/workflows/build.yml:108` runs `./gradlew :verifyPlugin`; the root project no longer applies the IntelliJ
  plugin. Lines 91, 97, 116, 127 and 138 read `build/reports/...` and `build/distributions` at the root; the plugin
  writes under `projects/plugin-abyssus/build/`. The verifier cache key (line 104) hashes `app-game-control-line.gradle.kts`
  at the root, which does not exist there; it should hash `gradle/libs.versions.toml` and `gradle.properties`.
- `.run/Run Plugin Verification.run.xml` calls `runPluginVerifier` (the old plugin's task name).
*Fix:* `rootProject.layout` in the verification script; module-qualified task names and paths in CI and `.run`.

**B3. Tracked junk.** Five `.DS_Store` files are tracked under the root `src/test/testData/` (`git ls-files | grep DS_Store`),
which otherwise holds only empty folders. *Fix:* remove them and the empty tree, and add a `.gitignore` entry. The earlier
process item (the open change `restructure-gradle-modules`) is resolved: it was archived on 2026-10-08.

### Duplication

**D1. Dead copy of the ray slice (must-fix, zero risk).** `lib-raytracing/P/lib/raytracing/MetalSlice.kt:11-61`
(`MetalSliceMesh`, `MetalSliceInstance`, `MetalSliceCamera`) is a line-for-line copy of `RaySlice.kt:11-64`. The scan
flagged 16 shared windows. `RaySlice` already carries the Metal-only `metalUniforms()`. A search of every source set
finds no reference to any `MetalSlice*` type, while `RaySlice*` has 89. *Fix:* delete the file.

**D2. The asset loader table is wired by hand four times (should-fix; OCP).**
- `plugin-abyssus/P/plugin/AssetLoading.kt:144-152`: nine `MetaType.X to Loader` entries (model, terrain, foliage, texture,
  pixmap texture, skybox, procedural sky, clouds, HDR sky), sharing some loader instances.
- `app-game-control-line/P/app/game/controlline/render/FieldRenderer.kt:67-75`: the same nine. Its KDoc says "wired by
  constructors like the plugin does".
- `lib-core/P/lib/core/BaseCtx.kt:61-71`: the same nine again, for standalone hosts.
- `lib-physics/P/lib/physics/PhysicsAssets.kt` takes a `ModelLoader` and a `TerrainLoader` by constructor and keeps no
  table, so it is not a copy any more (it needed `decodeTextures = false`, which its caller supplies).

Adding an asset kind (water is an open change; clouds and foliage have just been added to all of these) means editing
all of them; a missed host fails at run time with "no loader". *Fix (decision 1):* one table in `lib-core`.

**D3. Cube-to-equirect resampling (checked, kept; was a finding).** `SkyboxRaySnapshotLoader.kt` and `RaySkyBaker.kt`
share the pixel-to-direction loop and the major-axis face choice, but they differ in everything a shared function would
need to hide: output width (`RAY_SKY_MAX_WIDTH` = 1024 versus a private 2048), nearest versus bilinear sampling, float
versus 8-bit output, per-pixel math versus a cached, parallel mapping, and the face basis. Sharing the loop would change
pixels or force a parameter for each difference. They stay as they are. Revisit only if a third resampler appears.

**D4. Two Jackson configurations (should-fix).** `lib-core/P/lib/core/io/JsonProcessor.kt:59-73` and
`lib-core-editor/P/lib/core/editor/document/JsonFormat.kt:22-37` build the same `DefaultPrettyPrinter` and the same
two `MapperFeature`/`DeserializationFeature` settings. `JsonFormat`'s KDoc (lines 17-21) says "`JsonProcessor` and the
plugin's `SceneJson` both build on it, so the two cannot drift apart", but `lib-core` cannot depend on
`lib-core-editor`, and only `SceneJson.kt:46` uses it. *Fix:* move `JsonFormat` to `lib-core` `io/`; `JsonProcessor`
and `SceneJson` both use it. Output must stay byte-identical (number text and key order are format rules).

**D5. Project-view selection lookup copied seven times.** `ProjectView.getInstance(project).currentProjectViewPane
?.takeIf { it.id == AbyssusProjectViewPane.ID }` appears in `RenameSceneAction.kt:23`, `NewTerrainAction.kt:45`,
`NewFoliageAction.kt:110`, `ImportModelAction.kt:64`, `ImportFlightGearAction.kt:65`, `gameui/UiAssetActions.kt:43` and
the shared `selectedNode` in `ComponentActions.kt:86`. Fixed together with G1.

**D6. One field-row editor, built twice (should-fix).** `plugin-abyssus/P/plugin/properties/AssetPropertiesPanel.kt:490-590`
(`fieldRow`, `fieldEditor`, `commitField`, `twoColumns`) and `EntityDetailsView.kt:258-337` (`fieldRow`, `commit`,
`editorFor`) each build a label | editor | red error label row. Each makes a combo box or text field that commits on Enter
and on focus loss, skips equal values, and reverts with a reason on rejection. Only the edit call and the result
type differ (`AssetMetaEdits.update` → `AssetEditResult`, `SceneComponentEdits.update` → `EditResult`). Other panels
(`FoliageSection.fieldRow`, `gameui/UiPropertiesPanel`, `SceneDetailsView`) have their own commit helpers; they have
different rules (drafts, Apply buttons) and are not in scope. *Fix (decision 4).*

**D7. Test plumbing copied.** `testProject(name)` is defined in `lib-core/src/test/.../assets/TestData.kt`,
`lib-physics/src/test/.../TestData.kt`, and again in `lib-core-editor/src/testFixtures/.../TestScenes.kt` (`lib-runtime`,
which had a fourth copy, is gone). The same
`systemProperty("abyssus.testData", rootProject.file("projects/plugin-abyssus/src/test/testData")...)` line is in four
build scripts (`lib-core`:42, `lib-core-editor`:32, `lib-physics`:51, `plugin-abyssus`:267).
The scan also flags two `CancellationTest`s; they test different exception types (`CancellationException` and IntelliJ's
`ProcessCanceledException`), and the plugin copy says so, so they stay. *Fix:* `testProject` in `lib-core`'s
existing `testFixtures` (it already holds `HdrFixtures`); the property set once in the root `subprojects {}` block.

**D8. UUID check re-implemented (optional).** `lib-core-editor/P/lib/core/editor/meta/AssetMetaEditor.kt:229` (re-checked) uses
`runCatchingKeepingCancellation { UUID.fromString(...) }.isSuccess` beside `lib-core`'s `parseUuidOrNull`
(`assets/UuidParsing.kt`). *Fix:* `parseUuidOrNull(value.value) == null`.

### SOLID

**S1. `SceneViewPanel` does five jobs (should-fix; SRP, ISP).** `plugin-abyssus/P/plugin/sceneview/SceneViewPanel.kt`,
882 lines and 59 functions in one class (567 and 43 in the first survey; foliage painting, path arrangements and
overlays were added since):

| Responsibility | Functions |
|---|---|
| Ray control binding | `panelRayControl`, `installRay`, `refreshRay`, the experiment toggle |
| GL canvas lifecycle | `newCanvas`, the frame timer, `replaceAbandonedCanvas`, `addNotify`/`removeNotify`/`dispose` |
| Toolbar and camera choices | `buildToolbar`, `overlayToolbar`, `syncControls`, `refreshCameraChoices` |
| Play controls | `addPlayControls`, `syncPlayControls` |
| Keyboard and mouse input | `bindKeys`, `forwardKeys`, `attachInput`, `forwardMouse` |

Task 8.1 records the exact line ranges before extracting. Its constructor (line 45) takes 13 parameters, including
optional callback pairs that must be kept consistent by every caller (`lightActions` + `canAddLight`, `assetActions` +
`canAddAsset`) and further single callbacks (`foliageActions`, `paintSession`, `pathActions`). *Fix (decision 5).*

**S2. `AssetPropertiesPanel` (668 lines).** Selection tracking, terrain hookup, state rendering, header and undo
buttons, field rows (D6, from line 490), previews and the `Thumbnail` component. *Fix:* D6 removes about 100 lines.
Previews and `Thumbnail` move to `AssetPreviews.kt` (`ui/Thumbnails.kt` exists and is the place to check first for
overlap). Expect about 500 lines afterwards; no further split here.

**S3. Light labels hard-coded in the view (should-fix; OCP).** `EntityDetailsView.kt:262-273` (re-checked) checks
`section.kind == "LightComponent"` and a field name to pick `lightRangeLabel`, `lightConeAngleLabel`,
`lightEdgeSoftnessLabel` and `lightRangeTooltip`. The field model already carries `label`. A new component with a
custom label means editing the Swing view. *Fix:* the field description in `lib-core-editor` (`components/`) carries
the label and an optional tooltip, read through `EditorMessages`; the view shows what it is given. The bundle keys move
to `AbyssusEditorBundle.properties` with the same English text.

### KISS

**K1. Typealias re-exports (should-fix).** `lib-core-editor/P/lib/core/editor/document/DocumentFormat.kt`
re-exports `DocumentKind`, `FormatProblem`, `FormatRejection`, `UnsupportedDocumentFormat` and
`AbyssusDocumentFormat` from `lib-core` "for source compatibility". 23 files import the alias, so one type has two
names. The module is internal; there is no external source to stay compatible with. *Fix:* import from
`lib.core.format`, then delete the file.

**K2. `lib-gdx` package name (resolved).** `lib-gdx-model` was renamed `lib-gdx` with package
`net.nevinsky.abyssus.lib.gdx`; nothing is left to do.

**K3. Ray feasibility spike in production (optional, blocked).** `SceneViewPanel.kt` reads the system property
`abyssus.raytracing.experiment`; `RayFeasibilityPreview.kt` (108 lines, plugin) and `RayFeasibilityLoop.kt` (99
lines, `lib-core-editor`) exist only for it. No spec in `openspec/specs/` mentions it, but `add-scene-raytracing`
task 1.8 (open) uses it for its manual check. *Decision:* leave it alone now. Add a removal task to
`add-scene-raytracing` after 1.8 (task 9.2).

### GoF patterns

**G1. Template Method exists but is bypassed (should-fix).** `projectView/AbyssusTreeAction.kt` is a Template Method
(`targetOf` / `isEnabled` / `perform`, with final `update` and `actionPerformed`), used by the component and light
actions. `RenameSceneAction`, `NewTerrainAction`, `NewFoliageAction`, `ImportModelAction`, `ImportFlightGearAction` and
`gameui/UiAssetActions` re-implement it: the selection lookup (D5), `update` setting `isEnabledAndVisible`, and the null
checks in `actionPerformed`. *Fix (decision 2):* extend `AbyssusTreeAction` where the shape fits. With the default
`isEnabled = true`, visible and enabled are both `target != null`; an action whose `update` does more (for example
`ImportModelAction`, which reads the scene and play state) overrides `isEnabled` and keeps that logic.

**G2. Two dispatch styles for one job (optional).** `AssetLoading.kt` (`SkyRaySnapshotLoader`) switches on
`meta.type` across three sky loaders (cube, procedural, HDR), while `CompositeAssetLoader` (`lib-core`) dispatches by a `MetaType` map. Worth
doing only together with D2, so that a new sky kind is one registration for both drawing and ray snapshots. Otherwise
three `when` arms are simpler than a registry.

**Patterns considered and rejected:**
- *Strategy for field types:* already prototyped and rejected (`refactor-solid-dedup` 3.1).
- *Observer/event bus for `SceneViewPanel`:* the parts talk through a handful of calls, and a bus would hide that.
  Plain collaborators passed by constructor are enough.
- *Builder for `SceneViewPanel`:* decision 5 removes the lambda pairs, so the constructor no longer needs one.
- *Abstract Factory for loaders:* one function returning a map is enough (decision 1).
- *Delegation for `GdxGlBridge`:* see non-findings.

### Non-findings (checked, kept)

- `plugin-abyssus/src/main/kotlin/com/badlogic/gdx/backends/lwjgl3/GdxGlBridge.kt:24-76`: `CoreProfileGL20` and
  `CoreProfileGL30` repeat about 25 one-line overrides because `Lwjgl3GL30` extends `Lwjgl3GL20`. A delegate would be
  longer and would sit on a JVM-abort path.
- `ImportFlightGearAction` / `NewTerrainAction` clone: covered by G1.
- `EntityPropertiesPanelTest` / `AssetPropertiesPanelTest` setup clone (about 20 lines): test-local fixtures. This could
  be revisited after D6, but nothing is planned.
- Module rules hold: no `runCatching {` in any main source set, no IntelliJ/Swing/AWT import in `lib-core-editor`
  or `lib-physics`. `lib-core`'s AWT use (`HdrPreview`, `SgiImage`) is allowed by its rule.

## Decisions

### 1. Standard asset loader table in `lib-core` (D2)

A small constructor-wired class `StandardAssetLoaders` in `lib-core` `assets/standard/` (not `assets/loading/`: the loaders
import `loading`, so a class there would make a package cycle). It is Swing- and platform-free; the loaders it builds
only touch GL in their `build` step, on the GL thread, as today. A class rather than a bare function, because the plugin
also needs four of the loaders themselves (for its ray snapshot stores) and would otherwise cast them out of a map:

```kotlin
class StandardAssetLoaders(files: FileLoader, metas: AssetMetaLoader, skyShaders: ShaderStorage,
                           assimp: AssimpModelLoader = ..., toneCurve: ToneCurve = ..., rayModels: RaySnapshotStore<...>? = null,
                           log: Logger = NOP, foliage: (TerrainLoader) -> AssetLoader<...> = runtime foliage loader) {
    val model; val terrain; val foliage; val texture; val skybox; val proceduralSky; val clouds; val hdrSky
    val byType: Map<MetaType, AssetLoader<*, *, *>>
}
```

The table has nine kinds today: model, terrain, foliage, texture, pixmap texture, skybox, procedural sky, clouds and HDR
sky. Foliage takes the terrain loader, so the function builds that one first and shares it. The plugin keeps
`ProjectAssets` (unsaved metadata, ray stores) and passes in what it shares (`terrainLoader`, `skyboxLoader`) through
optional parameters, or reads them from the returned map. `FieldRenderer` and `BaseCtx` use the function as it is.
`PhysicsAssets` already takes its loaders by constructor and keeps no table, so it is unchanged. No class hierarchy, no
`object`, which keeps `checkNoSingletons` green.

Rejected: moving `ProjectAssets` into `lib-core`. It owns the unsaved-metadata overlay, which is editor-only.

### 2. Tree actions use `AbyssusTreeAction` (D5, G1)

`RenameSceneAction` → `AbyssusTreeAction<DtoEntry>`. `NewTerrainAction`, `NewFoliageAction`, `ImportModelAction`,
`ImportFlightGearAction` and `UiAssetActions` → `AbyssusTreeAction<VirtualFile>` with `targetOf = ::assetsNodeProjectFile`
(each checked first: an action whose enablement depends on more than the target overrides `isEnabled`). `selectedNode`
stays the only lookup. Runs on the EDT (`update` reads the Swing tree selection, as recorded in `refactor-solid-dedup`
D11). Before the change, tests pin visibility on a scene row, the Assets row and an unrelated row for each action that
lacks them (`RenameSceneAction`, `ImportFlightGearAction`, `ImportModelAction`, `UiAssetActions`; `NewTerrainActionTest` and
`NewFoliageActionTest` exist).

### 3. (Dropped) One sky resampler

Dropped on 2026-10-12: the two resamplers differ in width, sampling, output type and caching, so sharing them would
change pixels or add a parameter per difference (see D3). The number stays so that other references hold.

### 4. One field editor helper (D6, S2, S3)

`plugin-abyssus/P/plugin/ui/FieldEditors.kt` (Swing, EDT only). It holds what the two panels really share, the editors and
their commit/revert wiring; the row layouts stay in each panel because they differ on purpose (column widths, where the
reason goes):

```kotlin
sealed interface CommitOutcome { Saved; Unchanged; data class Refused(val reason: String) }
fun settle(outcome, error: JBLabel, revert: () -> Unit)          // clears or shows the reason; reverts unless Saved
fun textEditor(current: String, error, commit: (String) -> CommitOutcome): JBTextField   // Enter + focus loss
fun <T> choiceEditor(choices, selected, error, renderer?, commit: (T) -> CommitOutcome): ComboBox<T>
fun checkEditor(selected: Boolean, error, commit: (Boolean) -> CommitOutcome): JBCheckBox
```

Each panel maps its own result type (`AssetEditResult`, `EditResult`) to `CommitOutcome` in one `when`. Asset-only
behavior stays in `AssetPropertiesPanel`: conflict refresh, local parse before commit, monospace font. Component names
(`asset-field-<key>`, `field-<kind>-<field>`, `error-...`, `label-...`) stay identical, because UI tests look them up. The
asset panel's previews and `Thumbnail` moved to `properties/AssetPreviews.kt`, and its row rendering to
`properties/AssetRows.kt`. Label and tooltip resolution moved to the field description in `lib-core-editor` (S3)
(`ComponentField.labelKey`/`tooltipKey`, `FieldValue.tooltip`), which keeps that logic testable without Swing.

### 5. Split `SceneViewPanel` along its seams (S1)

Done as collaborators passed by constructor, each owning one responsibility and its widgets (`sceneview/`):

- `SceneInputForwarder`: `bindKeys`, `forwardKeys`, the canvas mouse, the arrangement point menu. It takes the panel,
  `SceneRenderer`, `SceneInteraction`, `PlayState` and three small lambdas (`experimenting`, `experimentOrbit`,
  `updatePaintMode`).
- `RayControlBinding`: the ray feed, the mode shown to the Properties switch, its listeners.
- `CloudControls`: the Clouds combo and note, `CloudViewState`, the frame budget and the fallback to shells.
- `PlayControlsStrip`: Play/Pause/Step/Stop with their status text.
- `FoliagePaintControls`: the Paint toggle and strip, the paint session of the selected foliage entity.
- `SceneViewPanel` keeps the canvas lifecycle (it carries the GL-safety rules, `GuardedGLCanvas.glSafe` and the macOS
  zero-size abort), the gizmo / Add / camera toolbar, and the wiring of the collaborators. A separate `SceneToolbar`
  class was dropped: the buttons that remain share too much state with the panel (`updatingControls`, the interaction
  mode) to move without a back-reference.
- The Swing-free toolbar state already existed (`toolbarState`, `playControls`, `cameraChoices`).
- The callback pairs become `class PlacementMenu<T>(available: () -> Boolean, choices: (T) -> DefaultActionGroup)`: a
  `lightMenu`, an `assetMenu` and a `pathMenu` parameter replace six. The single callbacks `foliageActions` and
  `paintSession` stay.

Threads are unchanged: everything runs on the EDT (AWT thread), and libGDX calls stay inside `GdxRuntime.withContext`
in `paintGL`.

### 6. Ordering

Baseline first (phase 1; phase 2, the docs rewrite, is no longer needed), because each later task's verification needs
`./gradlew check` and the CI paths to mean something. Then library-level deduplication (3-4; phase 5 is dropped), which
is small and covered by headless tests. Then plugin UI (6-8), which is larger and partly runIde-only. Every phase is one PR, independently revertible, and leaves `check`
green.

## Risks / Trade-offs

- **Byte drift in JSON output (D4, K1).** A changed printer setting would rewrite number text or key order in user
  files. *Mitigation:* task 3.1 adds a round-trip test over every fixture `.scene`, `.abss` and `meta.json` (format →
  identical bytes) before the move. `SceneJsonTest` and `HeadlessEditingTest` must pass unchanged.
- **UI regressions that headless tests miss (S1, D6).** *Mitigation:* `SceneViewPanelTest`, `SceneViewPanelRayTest`,
  `SceneInteractionTest`, `AssetPropertiesPanelTest` and `EntityPropertiesPanelTest` unchanged; numbered runIde checks
  in tasks 7.4 and 8.5.
- **Merge conflicts with open changes.** `add-realistic-water` and `add-remote-asset-library` may touch `AssetLoading`
  (phase 4) or the properties panel (phase 7); `add-scene-raytracing*`, `add-scene-view-antialiasing`,
  `add-project-fps-counter` and the foliage and arrangement work touch `SceneViewPanel` (phase 8). *Mitigation:* phases
  are separate PRs; phase 4 lands before new asset kinds, so those changes add one table entry instead of four. Task
  9.1 amends the task lists that add a loader to name the new function.
- **Over-abstraction.** Every extraction above replaces at least two existing copies. Anything that would leave only
  one user (for example a loader registry class, or splitting the canvas lifecycle out) is excluded.
- **Static survey only.** B2 is inferred from file reads. *Mitigation:* task 1.1 reproduces each item before any fix.

## Migration Plan (rollout and rollback)

- **Rollout:** seven PR-sized phases (1, 3, 4, 6, 7, 8, 9) in `tasks.md` order. Each merges only with `./gradlew check`
  green (CI adds `-Pabyssus.requireShaders=true`) and `scripts/check-docs.sh` clean. No feature flag is needed: there
  is no behavior change, and nothing changes in the plugin's settings or files.
- **Rollback:** revert the phase's PR. Phases 3-8 do not depend on each other's code. Reverting phase 1 would restore
  broken CI, so it is fixed forward instead.
- **Users:** none affected. The plugin zip contents, IDs and extension points stay the same. `buildPlugin` output
  moves only in the CI artifact path (B2), which matches where Gradle already writes it.

## Open Questions

1. G2: should the sky ray-snapshot dispatch (cube, procedural, HDR) join the loader table in phase 4, or stay a `when`?
2. (Decided 2026-10-12) The hard-coded property `abyssus.raytracing.experiment` goes with the feasibility spike: task
   1.10 of `add-scene-raytracing` removes it after 1.8 records the result. This change leaves the code alone.

Closed since the first draft: `restructure-gradle-modules` was archived (2026-10-08); `lib-gdx` already uses package
`lib.gdx` (K2); D3 is dropped (see D3).

## Acceptance Criteria

- `./gradlew check` green, with the same test counts as the phase 1.1 baseline plus the tests this change adds, and no
  test deleted or weakened except the `MetalSlice` file, which has none.
- `./gradlew :plugin-abyssus:verifyPlugin` runs and `checkPluginInternalApis` passes; the CI workflow's verify and
  artifact steps reference existing tasks and paths.
- `scripts/check-docs.sh` exits 0 (it does today) and still does after the docs edits of task 9.4.
- Searches return nothing: `MetalSlice`; `typealias .* = net.nevinsky.abyssus.lib.core.format`; more than one
  `MetaType.SKYBOX_HDR to` in main sources (outside `AssetMetaBinder`'s class map, which is not a loader table); `currentProjectViewPane` outside `selectedNode`; `fun testProject` outside
  `testFixtures`; `"LightComponent"` in `plugin-abyssus/P/plugin/properties/`.
- `SceneViewPanel.kt` under 550 lines (from 882) and `AssetPropertiesPanel.kt` under 520 (from 668), with no new class over 250.
- Every fixture document formats to identical bytes before and after phase 3.
- The two new spec requirements have passing scenarios (tasks 4.3, 7.3).
- runIde checks 7.4 and 8.5 done or listed for the user with exact steps.
