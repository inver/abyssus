# Proposal

## Why

The module move (commits `fe37e21`, `99efb64`, `00aa167`: every Gradle module under `projects/`, packages under
`net.nevinsky.abyssus.{lib,plugin,app}`) landed after `refactor-solid-dedup` and `restructure-editor-modules` were
archived. A second read-only survey of the code as it is now finds three kinds of problems:

1. **The baseline cannot be trusted.** `scripts/check-docs.sh` reports 85 broken paths. `AGENTS.md`, `docs/ai/*.md`
   and `openspec/config.yaml` name commands (`./gradlew :test`, `:core:test`) and folders (`editor-core/`,
   `src/main/kotlin/...`) that no longer exist. The plugin verifier's allowlist and the CI workflow still point at the
   old root layout. The open change `restructure-gradle-modules` describes a different layout from the one that landed
   (`lib-editor-core` vs the real `lib-core-editor`, `app-control-line-game` vs `app-game-control-line`, fixtures at
   `testData/` vs the real `projects/plugin-abyssus/src/test/testData`).
2. **Duplication that the first round missed or that the move created.** A dead 61-line copy of the ray slice
   geometry, the asset loader table wired by hand in three hosts, two Jackson configurations where the KDoc says
   there is one, cube-to-equirect resampling written twice, and the same field-row editor in two panels.
3. **SOLID/KISS items carried over but not finished.** `SceneViewPanel` and `AssetPropertiesPanel` were meant to be
   split (`refactor-solid-dedup` 7.1, 7.2, 4.2, moved to `restructure-editor-modules` 3.1, which is ticked) but are
   still 567 and 585 lines. Three tree actions re-implement the `AbyssusTreeAction` template next to it.

Fixing (1) first makes every later step verifiable. (2) and (3) keep the next features (water, clouds, model import,
remote assets) from having to edit three copies.

## What Changes

Refactors, build fixes and doc fixes only. **No user-visible behavior change and no file format change.**

Findings (detail, evidence and line references in `design.md`). Priority: **M** must-fix, **S** should-fix, **O** optional.

| # | Finding | Principle | Pri |
|---|---|---|---|
| B1 | 85 broken doc paths; `AGENTS.md` commands and layout, `docs/ai/*.md` and `openspec/config.yaml` describe the pre-move tree | Docs / DRY | M |
| B2 | Build and CI drift: verifier allowlist resolved under the wrong folder; CI calls a root `:verifyPlugin` and uploads root `build/` paths; a run configuration still calls `runPluginVerifier` | Config | M |
| B3 | Open change `restructure-gradle-modules` contradicts the landed layout; 5 tracked `.DS_Store` files under an otherwise empty root `src/test/testData` | Process | M |
| D1 | `MetalSlice.kt` (61 lines, zero references) is a copy of `RaySlice.kt` | Duplication, KISS | M |
| D2 | The `MetaType` → loader table is hand-wired in the plugin, the Control Line game and physics | Duplication, OCP | S |
| D3 | Cube-to-equirect sky resampling is written twice (`lib-core` and `lib-core-editor`) | Duplication | S |
| D4 | Jackson settings and pretty printer are copied in `JsonProcessor` and `JsonFormat`; `JsonFormat`'s KDoc claims `JsonProcessor` uses it | Duplication | S |
| D5 / G1 | Project-view selection lookup copied 4×; `RenameSceneAction`, `NewTerrainAction`, `ImportFlightGearAction` bypass the existing `AbyssusTreeAction` Template Method | Duplication, GoF | S |
| D6 / S2 | Field-row editor (label, editor, error, commit on Enter or focus loss, revert) built separately in `AssetPropertiesPanel` and `EntityDetailsView`; `AssetPropertiesPanel` is 585 lines | Duplication, SRP | S |
| D7 | `testProject()` copied in three test source sets; the `abyssus.testData` path set in five build scripts | Duplication (tests, config) | S |
| S1 | `SceneViewPanel` (567 lines, 43 functions) owns ray control, canvas lifecycle, toolbar, play controls and input; 10-parameter constructor with paired lambdas | SRP, ISP | S |
| S3 | `EntityDetailsView` hard-codes `LightComponent` label and tooltip keys in the Swing view | OCP | S |
| K1 | `lib-core-editor` re-exports `lib-core`'s format types through five `typealias`es that 15 files import | KISS | S |
| D8 | `AssetMetaEditor` re-implements `parseUuidOrNull` | Duplication | O |
| G2 | The sky ray-snapshot loader dispatches with a `when` beside the `CompositeAssetLoader` map | GoF (Composite) | O |
| K2 | `lib-gdx-model` uses package `net.nevinsky.abyssus.lib.core.*`, which reads as `lib-core` | KISS | O |
| K3 | The ray feasibility spike (`abyssus.raytracing.experiment`) ships in production classes | KISS | O, blocked |

How each is fixed:

- **Baseline (B1–B3, D1).** Fix the verifier allowlist path and the CI and run-configuration task paths; delete
  `MetalSlice.kt` and the tracked `.DS_Store` files; rewrite the stale paths in `AGENTS.md`, `docs/ai/*.md` and
  `openspec/config.yaml`; amend `restructure-gradle-modules` to the as-built layout and close it.
- **One registration per asset kind (D2, optional G2).** `lib-core` offers the standard `MetaType` → loader table as
  one function. The plugin, the game and physics take it, or a subset of it, instead of listing loaders themselves.
- **One resampler (D3), one JSON format (D4), one name per type (K1, D8).** Move the shared code to the lowest module
  that both callers already depend on (`lib-core`), and point callers at it.
- **Use the template that exists (D5/G1).** The three actions extend `AbyssusTreeAction`; `selectedNode` stays the one
  selection lookup.
- **One field row (D6, S2, S3).** A small Swing helper in `plugin/ui` builds the row and its commit/revert wiring;
  both panels use it. Light field labels and tooltips move into the field descriptions that `lib-core-editor`
  already produces.
- **Split `SceneViewPanel` (S1)** along its existing seams: toolbar, input forwarding, ray control binding. The
  paired `xActions`/`canAddX` lambdas become one small interface each.
- **Tests (D7).** One `testProject()` in `lib-core`'s `testFixtures`, one `abyssus.testData` setting in the root
  build.

### Out of scope

- Any change to `.scene`, `.abss` or `meta.json`: no field is read or written differently. Validation through
  `AbyssusDocumentFormat` stays before binding on every path; D4 and K1 move code and imports, not checks. Writes still go
  only through `editSceneJson`.
- Reopening decisions already made: field-type switches stay (`refactor-solid-dedup` 3.1 prototype failed its size
  gate), the `PhysicsWorld` and Vulkan/Metal backend seams stay as recorded there, and `AbyssusCore` keeps its groups.
- `lib-gdx-model`'s inherited classes (`Mesh`, `DefaultShader`, `Model`). K2 only proposes a package rename and is
  optional.
- The ray feasibility spike itself (K3) while `add-scene-raytracing` task 1.8 still needs it.
- `GdxGlBridge`'s `CoreProfileGL20` / `CoreProfileGL30` pair: the repetition comes from libGDX's class hierarchy, and
  delegation would add code. It stays.
- New GoF patterns for their own sake: no Observer bus, no Builder for `SceneViewPanel`, no Strategy registry.
- Performance work: the frame loop and picking are not touched.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `asset-loading`: new requirement that every host loads the same asset kinds from one registration (guards D2).
- `object-properties-panel`: new requirement that asset fields and component fields confirm, refuse and revert the
  same way (guards D6).

Unchanged and used as regression checks: `abyssus-project-view` (tree actions), `scene-camera-display`,
`scene-play-mode`, `scene-hdr-skybox`, `scene-skybox-rendering`, `headless-scene-editing`, `abyssus-document-format`.

## Impact

- **Build and CI:** `gradle/plugin-verification.gradle.kts`, `build.gradle.kts` (test data property), five module build
  scripts, `.github/workflows/build.yml`, `.run/Run Plugin Verification.run.xml`.
- **`lib-core`:** `io/JsonProcessor`, a new `io/JsonFormat` (moved), the standard loader table, the shared sky
  resampler, `testFixtures`.
- **`lib-core-editor`:** `document/JsonFormat` (removed), `document/DocumentFormat.kt` (removed), `ray/RaySkyBaker`,
  `meta/AssetMetaEditor`, field label descriptions.
- **`lib-raytracing`:** `MetalSlice.kt` deleted.
- **`lib-physics`, `app-game-control-line`:** `PhysicsAssets`, `render/FieldRenderer` use the loader table.
- **`plugin-abyssus`:** `AssetLoading`, `projectView/{RenameScene,NewTerrain,ImportFlightGear}Action`,
  `properties/{AssetPropertiesPanel,EntityDetailsView}`, `sceneview/SceneViewPanel`, new `ui/` helper.
- **Docs:** `AGENTS.md`, `docs/ai/*.md`, `openspec/config.yaml`, module and package READMEs, and the open change
  `restructure-gradle-modules`.
- **Open changes:** `add-scene-raytracing` (K3, ray files), `add-realistic-water`, `add-sky-clouds`,
  `add-remote-asset-library` and `add-model-import` touch `AssetLoading`, the sky loaders or the properties panel. Whichever
  lands second rebases (see design, Risks).
- **No new dependencies.** No public plugin extension point changes.
- **Survey limits:** read-only and static. Gradle could not run in the survey container: Maven Central answered HTTP 429
  to every plugin resolution. Task 1.1 re-establishes the baseline before any code change.
