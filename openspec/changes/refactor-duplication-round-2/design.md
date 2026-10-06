# Design

## Context

### Current state (survey of 2026-10-06, `HEAD` `00aa167`)

Nine Gradle modules under `projects/`, named by `settings.gradle.kts` (`lib-*`, `plugin-*`, `app-*`). Kotlin main
sources, excluding generated code:

| Module | Files | Lines | Root package |
|---|---|---|---|
| `plugin-abyssus` | 124 | 11,422 | `net.nevinsky.abyssus.plugin` |
| `lib-gdx-model` | 52 | 8,974 | `net.nevinsky.abyssus.lib.core` (see K2) |
| `lib-core-editor` | 76 | 6,899 | `net.nevinsky.abyssus.lib.core.editor` |
| `lib-core` | 65 | 6,797 | `net.nevinsky.abyssus.lib.core` |
| `lib-raytracing` | 17 | 2,669 | `net.nevinsky.abyssus.lib.raytracing` |
| `app-game-control-line` | 25 | 2,585 | `net.nevinsky.abyssus.app.game.controlline` |
| `lib-runtime` | 33 | 1,842 | `net.nevinsky.abyssus.lib.runtime` |
| `lib-physics` | 15 | 1,724 | `net.nevinsky.abyssus.lib.physics` |
| `plugin-abyssus-physics` | 7 | 708 | `net.nevinsky.abyssus.plugin.physics` |

Dependencies: `lib-core` → `lib-gdx-model`; `lib-runtime` → `lib-core`; `lib-core-editor` → `lib-core`, `lib-runtime`,
`lib-raytracing`, `lib-gdx-model`; the plugin → all. The root `build.gradle.kts` applies Kotlin, Kover,
`gradle/checks.gradle.kts` (`checkNoSingletons`, `checkNoRunCatching`) and `gradle/package-cycles.gradle.kts` to every
module.

What the first round (`refactor-solid-dedup`, archived) already fixed and this change keeps: one `meta.json` binder,
`parseUuidOrNull`, `displayMessage`, named play frames, shared Gradle checks, grouped core services, bundle-backed
action text, verifier wiring. Its remaining open items (4.1/4.2/7.1/7.2) went to `restructure-editor-modules` 3.1. That
task is ticked, but `SceneViewPanel` and `AssetPropertiesPanel` were not split (S1, S2).

### Survey method and coverage

- All modules except `lib-gdx-model` (inherited code, out of scope) were read at file-list and function-list level.
  The 40 largest files and every hit below were read in full.
- Literal duplication: a normalized 8-line sliding-window scan over every `src/**/*.kt` (imports, braces and comments
  dropped). It found 8 clone groups; each is classified below or listed under non-findings.
- Semantic duplication: targeted searches (`CompositeAssetLoader(`, `MetaType.X to`, `currentProjectViewPane`,
  `UUID.fromString`, vector helpers, `JsonMapper.builder`, `abyssus.testData`, `typealias`).
- Baseline: `scripts/check-docs.sh` ran (85 broken paths). Gradle did not run here: plugin resolution from Maven
  Central failed with HTTP 429 on two attempts. Build and CI findings B2 are static reads, so task 1.1 confirms them
  first.

## Goals / Non-Goals

**Goals**

- A green, trustworthy baseline: `./gradlew check`, `verifyPlugin` and `scripts/check-docs.sh` pass, and the docs
  describe the tree as it is.
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

**B1. Docs describe the pre-move tree.** `scripts/check-docs.sh` exits non-zero with 85 broken paths in
`docs/ai/architecture.md`, `conventions.md`, `file-formats.md` and `testing.md` (for example
`core/src/main/kotlin/net/nevinsky/abyssus/core/format/AbyssusDocumentFormat.kt`, now
`lib-core/P/lib/core/format/AbyssusDocumentFormat.kt`). `AGENTS.md` (144 lines) has 28 lines naming `editor-core`,
`gdx-model/`, `physics-plugin` or `games/control-line`, and its command table names tasks that no longer exist (`:test`,
`:core:test`, `:gdx-model:test`). `openspec/config.yaml` tells agents to run `./gradlew :test --tests` and names the
fixture `src/test/testData/project/Untitled`, which is now `plugin-abyssus/src/test/testData/project/Untitled`.
*Impact:* every agent-driven change starts from wrong commands, and the docs check, which is part of every change's
final task, cannot pass. *Fix:* rewrite paths and commands; no prose redesign.

**B2. Build and CI drift.**
- `gradle/plugin-verification.gradle.kts:2` resolves `gradle/plugin-internal-api-allowlist.txt` with
  `layout.projectDirectory`, which is `projects/plugin-abyssus` or `projects/plugin-abyssus-physics` when applied
  there (`plugin-abyssus.gradle.kts:250`, `plugin-abyssus-physics.gradle.kts:148`). Neither has a `gradle/` folder.
  `gradle/package-cycles.gradle.kts:5` uses `rootProject.layout`, which is right. *Impact:* `checkPluginInternalApis`
  (the finalizer of `verifyPlugin`) declares a missing input and cannot pass.
- `.github/workflows/build.yml:108` runs `./gradlew :verifyPlugin`; the root project no longer applies the IntelliJ
  plugin. Lines 91, 97, 116, 127 and 138 read `build/reports/...` and `build/distributions` at the root; the plugin
  writes under `projects/plugin-abyssus/build/`.
- `.run/Run Plugin Verification.run.xml` calls `runPluginVerifier` (the old plugin's task name).
*Fix:* `rootProject.layout` in the verification script; module-qualified task names and paths in CI and `.run`.

**B3. Process drift.** The open change `restructure-gradle-modules` (13 open tasks) plans `lib-editor-core`,
`app-control-line-game` and a root `testData/`; the code has `lib-core-editor`, `app-game-control-line` and
`projects/plugin-abyssus/src/test/testData`. Five `.DS_Store` files are tracked under `src/test/testData/`, which
otherwise holds only empty folders. *Fix:* amend that change's proposal and tasks to the as-built names, tick what
landed, keep the remaining items (root fixture move, Control Line folder flattening) as open tasks or drop them with a
reason, and remove the `.DS_Store` files with a `.gitignore` entry.

### Duplication

**D1. Dead copy of the ray slice (must-fix, zero risk).** `lib-raytracing/P/lib/raytracing/MetalSlice.kt:11-61`
(`MetalSliceMesh`, `MetalSliceInstance`, `MetalSliceCamera`) is a line-for-line copy of `RaySlice.kt:11-64`. The scan
flagged 16 shared windows. `RaySlice` already carries the Metal-only `metalUniforms()`. A search of every source set
finds no reference to any `MetalSlice*` type, while `RaySlice*` has 89. *Fix:* delete the file.

**D2. The asset loader table is wired by hand three times (should-fix; OCP).**
- `plugin-abyssus/P/plugin/AssetLoading.kt:138-150`: seven `MetaType.X to Loader` entries.
- `app-game-control-line/P/app/game/controlline/render/FieldRenderer.kt:57-69`: the same seven, in the same order.
  Its KDoc says "wired by constructors like the plugin does".
- `lib-physics/P/lib/physics/PhysicsAssets.kt:44-51`: a subset (model without textures, terrain).

Adding an asset kind (water and clouds are open changes) means editing all three; a missed host fails at run time with
"no loader". *Fix (decision 1):* one table in `lib-core`.

**D3. Cube-to-equirect resampling twice (should-fix).** `lib-core/P/lib/core/assets/sky/cube/SkyboxRaySnapshotLoader.kt:21-69`
and `lib-core-editor/P/lib/core/editor/ray/RaySkyBaker.kt:72-105` share the output size (`RAY_SKY_MAX_WIDTH`), the
pixel-to-direction loop and the major-axis face choice. They differ in how a face is sampled: GL `sc/tc` per Pixmap
versus a projection on baked faces. *Risk:* the two can drift, and the raster sky and the ray sky would then disagree.
*Fix (decision 3):* share the direction loop and face choice, and pass the sampling in as a function.

**D4. Two Jackson configurations (should-fix).** `lib-core/P/lib/core/io/JsonProcessor.kt:21-37` and
`lib-core-editor/P/lib/core/editor/document/JsonFormat.kt:22-37` build the same `DefaultPrettyPrinter` and the same
two `MapperFeature`/`DeserializationFeature` settings. `JsonFormat`'s KDoc (lines 17-21) says "`JsonProcessor` and the
plugin's `SceneJson` both build on it, so the two cannot drift apart", but `lib-core` cannot depend on
`lib-core-editor`, and only `SceneJson.kt:46` uses it. *Fix:* move `JsonFormat` to `lib-core` `io/`; `JsonProcessor`
and `SceneJson` both use it. Output must stay byte-identical (number text and key order are format rules).

**D5. Project-view selection lookup copied four times.** `ProjectView.getInstance(project).currentProjectViewPane
?.takeIf { it.id == AbyssusProjectViewPane.ID }` appears in `RenameSceneAction.kt:21-26`,
`NewTerrainAction.kt:43-47`, `ImportFlightGearAction.kt:65-69` and the shared `selectedNode` in
`ComponentActions.kt:84-89`. Fixed together with G1.

**D6. One field-row editor, built twice (should-fix).** `plugin-abyssus/P/plugin/properties/AssetPropertiesPanel.kt:407-507`
(`fieldRow`, `fieldEditor`, `commitField`, `twoColumns`) and `EntityDetailsView.kt:258-337` (`fieldRow`, `commit`,
`editorFor`) each build a label | editor | red error label row. Each makes a combo box or text field that commits on Enter
and on focus loss, skips equal values, and reverts with a reason on rejection. Only the edit call and the result
type differ (`AssetMetaEdits.update` → `AssetEditResult`, `SceneComponentEdits.update` → `EditResult`). *Fix (decision
4).*

**D7. Test plumbing copied.** `testProject(name)` is defined in `lib-core/src/test/.../assets/TestData.kt`,
`lib-physics/src/test/.../TestData.kt` and `lib-runtime/src/test/.../TestData.kt`, and again in
`lib-core-editor/src/testFixtures/.../TestScenes.kt`. The same
`systemProperty("abyssus.testData", rootProject.file("projects/plugin-abyssus/src/test/testData")...)` line is in five
build scripts (`lib-core`:36, `lib-core-editor`:33, `lib-physics`:51, `lib-runtime`:21, `plugin-abyssus-physics`:129).
The scan also flags two `CancellationTest`s; they test different exception types (`CancellationException` and IntelliJ's
`ProcessCanceledException`), and the plugin copy says so, so they stay. *Fix:* `testProject` in `lib-core`'s
existing `testFixtures` (it already holds `HdrFixtures`); the property set once in the root `subprojects {}` block.

**D8. UUID check re-implemented (optional).** `lib-core-editor/P/lib/core/editor/meta/AssetMetaEditor.kt:229` uses
`runCatchingKeepingCancellation { UUID.fromString(...) }.isSuccess` beside `lib-core`'s `parseUuidOrNull`
(`assets/UuidParsing.kt`). *Fix:* `parseUuidOrNull(value.value) == null`.

### SOLID

**S1. `SceneViewPanel` does five jobs (should-fix; SRP, ISP).** `plugin-abyssus/P/plugin/sceneview/SceneViewPanel.kt`,
567 lines and 43 functions in one class:

| Responsibility | Lines |
|---|---|
| Ray control binding (`panelRayControl`, `installRay`, `refreshRay`, experiment toggle) | 96-157 |
| GL canvas lifecycle (`newCanvas`, timer, `replaceAbandonedCanvas`, `addNotify`/`removeNotify`/`dispose`) | 159-233, 478-550 |
| Toolbar and camera choices (`buildToolbar`, `overlayToolbar`, `syncControls`, `refreshCameraChoices`) | 234-327, 356-377, 398-412 |
| Play controls (`addPlayControls`, `syncPlayControls`) | 292-312, 378-397 |
| Keyboard and mouse input (`bindKeys`, `forwardKeys`, `attachInput`, `forwardMouse`) | 328-355, 413-477 |

Its constructor (lines 55-70) takes 10 parameters, including two lambda pairs (`lightActions` + `canAddLight`,
`assetActions` + `canAddAsset`) that must be kept consistent by every caller. *Fix (decision 5).*

**S2. `AssetPropertiesPanel` (585 lines).** Selection tracking (126-190), terrain hookup (193-221), state rendering
(222-300), header and undo buttons (323-373), field rows (D6, 388-507), previews (509-555) and the `Thumbnail`
component (568). *Fix:* D6 removes about 100 lines. Previews and `Thumbnail` move to `AssetPreviews.kt`. No further split.

**S3. Light labels hard-coded in the view (should-fix; OCP).** `EntityDetailsView.kt:262-273` checks
`section.kind == "LightComponent"` and a field name to pick `lightRangeLabel`, `lightConeAngleLabel`,
`lightEdgeSoftnessLabel` and `lightRangeTooltip`. The field model already carries `label`. A new component with a
custom label means editing the Swing view. *Fix:* the field description in `lib-core-editor` (`components/`) carries
the label and an optional tooltip, read through `EditorMessages`; the view shows what it is given. The bundle keys move
to `AbyssusEditorBundle.properties` with the same English text.

### KISS

**K1. Typealias re-exports (should-fix).** `lib-core-editor/P/lib/core/editor/document/DocumentFormat.kt:3-8`
re-exports `DocumentKind`, `FormatProblem`, `FormatRejection`, `UnsupportedDocumentFormat` and
`AbyssusDocumentFormat` from `lib-core` "for source compatibility". 15 files import the alias, so one type has two
names. The module is internal; there is no external source to stay compatible with. *Fix:* import from
`lib.core.format`, then delete the file.

**K2. `lib-gdx-model` package name (optional).** Its packages are `net.nevinsky.abyssus.lib.core`,
`.lib.core.shader`, `.lib.core.model` and so on (see `lib-gdx-model/README.md:16-35`), while `lib-core` uses
`.lib.core.assets`, `.lib.core.io`. No package is split today (checked), but a reader cannot tell the module from an
import, and a future `lib.core.model` in `lib-core` would split a package. *Proposal:* rename to
`net.nevinsky.abyssus.lib.gdx.model`. It touches every import of `Model`/`ModelInstance` in all modules and conflicts
with most open changes, so it is optional and gated on open question 2.

**K3. Ray feasibility spike in production (optional, blocked).** `SceneViewPanel.kt:120-124` reads the system property
`abyssus.raytracing.experiment`; `RayFeasibilityPreview.kt` (108 lines, plugin) and `RayFeasibilityLoop.kt` (99
lines, `lib-core-editor`) exist only for it. No spec in `openspec/specs/` mentions it, but `add-scene-raytracing`
task 1.8 (open) uses it for its manual check. *Decision:* leave it alone now. Add a removal task to
`add-scene-raytracing` after 1.8 (task 9.2).

### GoF patterns

**G1. Template Method exists but is bypassed (should-fix).** `projectView/AbyssusTreeAction.kt` is a Template Method
(`targetOf` / `isEnabled` / `perform`, with final `update` and `actionPerformed`), used by the component and light
actions. `RenameSceneAction` (15-35), `NewTerrainAction` (40-56) and `ImportFlightGearAction` (62-80) re-implement it:
the selection lookup (D5), `update` setting `isEnabledAndVisible`, and the null checks in `actionPerformed`. *Fix
(decision 2):* extend `AbyssusTreeAction`. Their `update` behavior is the same: with the default `isEnabled = true`,
visible and enabled are both `target != null`.

**G2. Two dispatch styles for one job (optional).** `AssetLoading.kt:183-194` (`SkyRaySnapshotLoader`) switches on
`meta.type` across three sky loaders, while `CompositeAssetLoader` (`lib-core`) dispatches by a `MetaType` map. Worth
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
- Module rules hold: no `runCatching {` in any main source set, no IntelliJ/Swing/AWT import in `lib-core-editor`,
  `lib-runtime` or `lib-physics`. `lib-core`'s AWT use (`HdrPreview`, `SgiImage`) is allowed by its rule.

## Decisions

### 1. Standard asset loader table in `lib-core` (D2)

A plain function next to `CompositeAssetLoader`, in `lib-core` `assets/loading/` (Swing-, GL- and platform-free; the
loaders it builds only touch GL in their `build` step, on the GL thread, as today):

```kotlin
fun standardAssetLoaders(files: FileLoader, metas: AssetMetaLoader, skyShaders: ShaderSource,
                         assimp: AssimpModelLoader = AssimpModelLoader(), toneCurve: ToneCurve = ToneCurve(),
                         rayModels: RaySnapshotStore<...>? = null): Map<MetaType, AssetLoader<PreparedAsset, Disposable>>
```

The plugin keeps `ProjectAssets` (unsaved metadata, ray stores) and passes in what it shares (`terrainLoader`,
`skyboxLoader`) through optional parameters, or reads them from the returned map. The game uses the function as it is.
Physics keeps its explicit two-entry map: it needs `decodeTextures = false` and no sky shaders. A KDoc points it at the
function, so the subset is a visible choice. No class hierarchy, no `object`, which keeps `checkNoSingletons` green.

Rejected: moving `ProjectAssets` into `lib-core`. It owns the unsaved-metadata overlay, which is editor-only.

### 2. Tree actions use `AbyssusTreeAction` (D5, G1)

`RenameSceneAction` → `AbyssusTreeAction<DtoEntry>`. `NewTerrainAction` and `ImportFlightGearAction` →
`AbyssusTreeAction<VirtualFile>` with `targetOf = ::assetsNodeProjectFile`. `selectedNode` stays the only lookup. Runs
on the EDT (`update` reads the Swing tree selection, as recorded in `refactor-solid-dedup` D11). Before the
change, tests for the two untested actions (`RenameSceneAction`, `ImportFlightGearAction`) pin visibility on a scene
row, the Assets row and an unrelated row.

### 3. One sky resampler (D3)

`lib-core` `assets/sky/` gets a function that walks the `RAY_SKY_MAX_WIDTH × /2` equirect grid, picks the cube face by
major axis, and calls `sample(face: Int, direction: FloatArray): FloatArray` for the color.
`SkyboxRaySnapshotLoader` passes its Pixmap `sc/tc` sampler; `RaySkyBaker` passes its baked-face projection.
CPU only; `RaySkyBaker`'s GL capture stays on the GL thread inside `GdxRuntime.withContext`, and only the resampling
it calls afterwards changes. *Gate:* before moving code, a test captures both outputs for the Untitled `skybox_default`
(and the baker's for a synthetic six-color cube). If the face choice differs at seams (`>=` versus `>` ties), keep
each one's tie rule as a parameter instead of changing pixels.

### 4. One field row helper (D6, S2, S3)

`plugin-abyssus/P/plugin/ui/FieldRow.kt` (Swing, EDT only):

```kotlin
sealed interface CommitOutcome { object Saved; object Unchanged; data class Refused(val reason: String) ... }
fun fieldRow(label: JComponent, editor: JComponent, error: JBLabel, names: RowNames): JComponent
fun textEditor(initial: String, commit: (String) -> CommitOutcome): JComponent   // Enter + focus loss, revert on Refused
fun choiceEditor(choices: List<T>, selected: T?, render: (T) -> String, commit: (T) -> CommitOutcome): JComponent
```

Each panel maps its own result type (`AssetEditResult`, `EditResult`) to `CommitOutcome` in one `when`. Asset-only
behavior stays in `AssetPropertiesPanel`: conflict refresh, local parse before commit, monospace font. Component
names (`asset-field-<key>`, `field-<kind>-<field>`, `error-...`, `label-...`) stay identical, because UI tests look
them up. Label and tooltip resolution moves to the field description in `lib-core-editor` (S3), which keeps that
logic testable without Swing.

### 5. Split `SceneViewPanel` along its seams (S1)

- `SceneToolbar` (Swing): buttons, camera combo, overlay toolbar, `sync(state)`. Button enablement and camera
  choices are computed by a Swing-free `SceneToolbarState` (plain data from `SceneRenderParams`, play state and
  ray mode), which a headless test covers.
- `SceneInputForwarder`: `bindKeys`, `forwardKeys`, `attachInput`, `forwardMouse`. It takes `SceneInteraction`,
  `OrbitCamera` and the play forwarder by constructor.
- `RayControlBinding`: `panelRayControl`, `installRay`, `refreshRay`, listener list.
- `SceneViewPanel` keeps the canvas lifecycle and wires the three. The canvas lifecycle carries the GL-safety rules
  (`GuardedGLCanvas.glSafe`, the macOS zero-size abort), so it stays where it is.
- The two lambda pairs become `fun interface PlacementMenu { fun choices(target: () -> Vec3): DefaultActionGroup? }`
  (null when unavailable), one parameter each.

Threads are unchanged: everything runs on the EDT (AWT thread), and libGDX calls stay inside `GdxRuntime.withContext`
in `paintGL`.

### 6. Ordering

Baseline first (phases 1-2), because each later task's verification needs `./gradlew check` and the docs check to
mean something. Then library-level deduplication (3-5), which is small and covered by headless tests. Then plugin UI
(6-8), which is larger and partly runIde-only. Every phase is one PR, independently revertible, and leaves `check`
green.

## Risks / Trade-offs

- **Byte drift in JSON output (D4, K1).** A changed printer setting would rewrite number text or key order in user
  files. *Mitigation:* task 3.1 adds a round-trip test over every fixture `.scene`, `.abss` and `meta.json` (format →
  identical bytes) before the move. `SceneJsonTest` and `HeadlessEditingTest` must pass unchanged.
- **Sky pixels change (D3).** *Mitigation:* the pixel-equality gate in decision 3. If it cannot hold, stop and report;
  the duplication then stays, documented.
- **UI regressions that headless tests miss (S1, D6).** *Mitigation:* `SceneViewPanelTest`, `SceneViewPanelRayTest`,
  `SceneInteractionTest`, `AssetPropertiesPanelTest` and `EntityPropertiesPanelTest` unchanged; numbered runIde checks
  in tasks 7.4 and 8.5.
- **Merge conflicts with open changes.** `add-realistic-water`, `add-sky-clouds`, `add-model-import` and
  `add-remote-asset-library` touch `AssetLoading` and the sky loaders (phases 4-5); `add-scene-raytracing*` touch
  `SceneViewPanel` (phase 8). *Mitigation:* phases are separate PRs; phase 4 lands before new asset kinds, so those
  changes add one table entry instead of three. Task 9.1 amends their task lists to name the new function.
- **Over-abstraction.** Every extraction above replaces at least two existing copies. Anything that would leave only
  one user (for example a loader registry class, or splitting the canvas lifecycle out) is excluded.
- **Static survey only.** B2 is inferred from file reads. *Mitigation:* task 1.1 reproduces each item before any fix.

## Migration Plan (rollout and rollback)

- **Rollout:** nine PR-sized phases in `tasks.md` order. Each merges only with `./gradlew check` green (CI adds
  `-Pabyssus.requireShaders=true`) and `scripts/check-docs.sh` clean from phase 2 on. No feature flag is needed: there
  is no behavior change, and nothing changes in the plugin's settings or files.
- **Rollback:** revert the phase's PR. Phases 3-8 do not depend on each other's code (4 and 5 both touch `lib-core`
  `assets/`, but different files). Reverting phase 1 or 2 would restore broken docs and CI, so those are fixed forward
  instead.
- **Users:** none affected. The plugin zip contents, IDs and extension points stay the same. `buildPlugin` output
  moves only in the CI artifact path (B2), which matches where Gradle already writes it.

## Open Questions

1. `restructure-gradle-modules` still plans a root `testData/` and a flattened Control Line folder. Should those land
   (amend and keep the change open), or be dropped (amend and archive)? Task 2.3 assumes **drop**, because the as-built
   layout is what the code and the docs use, unless the user says otherwise.
2. K2 (`lib.core` → `lib.gdx.model`): worth the churn now, after the open changes land, or never?
3. G2: should the sky ray-snapshot dispatch join the loader table in phase 4, or stay a `when`?
4. Is the hard-coded property `abyssus.raytracing.experiment` meant to survive `add-scene-raytracing` (as a hidden
   diagnostic) or go with it (K3)?

## Acceptance Criteria

- `./gradlew check` green, with the same test counts as the phase 1.1 baseline plus the tests this change adds, and no
  test deleted or weakened except the `MetalSlice` file, which has none.
- `./gradlew :plugin-abyssus:verifyPlugin` runs and `checkPluginInternalApis` passes; the CI workflow's verify and
  artifact steps reference existing tasks and paths.
- `scripts/check-docs.sh` exits 0. `AGENTS.md`'s command table runs as written.
- Searches return nothing: `MetalSlice`; `typealias .* = net.nevinsky.abyssus.lib.core.format`; more than one
  `MetaType.SKYBOX_HDR to` in main sources; `currentProjectViewPane` outside `selectedNode`; `fun testProject` outside
  `testFixtures`; `"LightComponent"` in `plugin-abyssus/P/plugin/properties/`.
- `SceneViewPanel.kt` under 300 lines and `AssetPropertiesPanel.kt` under 450, with no new class over 250.
- Every fixture document formats to identical bytes before and after phase 3.
- The two new spec requirements have passing scenarios (tasks 4.3, 7.3).
- runIde checks 7.4 and 8.5 done or listed for the user with exact steps.
