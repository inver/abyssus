# Proposal

## Why

The module move (commits `fe37e21`, `99efb64`, `00aa167`: every Gradle module under `projects/`, packages under
`net.nevinsky.abyssus.{lib,plugin,app}`) landed after `refactor-solid-dedup` and `restructure-editor-modules` were
archived. A second read-only survey found three kinds of problems. It was re-checked against the sources on
2026-10-12 (`HEAD` `2297268c`): the docs were repaired, `restructure-gradle-modules`, `add-model-import` and
`add-sky-clouds` were archived, `lib-gdx-model`, `lib-runtime` and `plugin-abyssus-physics` were folded into
`lib-gdx`, `lib-core` and `plugin-abyssus`, and the code kept growing. What remains:

1. **The build and CI still point at the old root layout.** The plugin verifier's allowlist is resolved under the
   wrong folder, the CI workflow calls a root `:verifyPlugin` and reads root `build/` paths, a run configuration
   calls `runPluginVerifier`, and five `.DS_Store` files are tracked under a root `src/test`. (The doc paths, which
   used to be 85 broken ones, now pass `scripts/check-docs.sh` and are out of scope.)
2. **Duplication that the first round missed or that the move created.** A dead 61-line copy of the ray slice
   geometry, the asset loader table wired by hand in four places, and two Jackson configurations where the KDoc says
   there is one. The same field-row editor is also built in two panels.
3. **SOLID/KISS items carried over but not finished.** `SceneViewPanel` and `AssetPropertiesPanel` were meant to be
   split (`refactor-solid-dedup` 7.1, 7.2, 4.2, moved to `restructure-editor-modules` 3.1, which is ticked) but are
   now 882 and 668 lines. Six tree actions re-implement the `AbyssusTreeAction` template next to it.

Fixing (1) first makes every later step verifiable. (2) and (3) keep the next features (water, clouds, model import,
remote assets) from having to edit three copies.

## What Changes

Refactors, build fixes and doc fixes only. **No user-visible behavior change and no file format change.**

Findings (detail, evidence and line references in `design.md`). Priority: **M** must-fix, **S** should-fix, **O** optional.

| # | Finding | Principle | Pri |
|---|---|---|---|
| B2 | Build and CI drift: verifier allowlist resolved under the wrong folder; CI calls a root `:verifyPlugin`, uploads root `build/` paths and hashes a nonexistent root `app-game-control-line.gradle.kts`; a run configuration still calls `runPluginVerifier` | Config | M |
| B3 | 5 tracked `.DS_Store` files under an otherwise empty root `src/test/testData` | Process | M |
| D1 | `MetalSlice.kt` (61 lines, zero references) is a copy of `RaySlice.kt` | Duplication, KISS | M |
| D2 | The `MetaType` → loader table (nine kinds) is hand-wired in the plugin, `FieldRenderer`, `BaseCtx` and the Control Line game | Duplication, OCP | S |
| D4 | Jackson settings and pretty printer are copied in `JsonProcessor` and `JsonFormat`; `JsonFormat`'s KDoc claims `JsonProcessor` uses it | Duplication | S |
| D5 / G1 | Project-view selection lookup copied 7×; six actions (`RenameScene`, `NewTerrain`, `NewFoliage`, `ImportModel`, `ImportFlightGear`, `UiAssetActions`) bypass the existing `AbyssusTreeAction` Template Method | Duplication, GoF | S |
| D6 / S2 | Field-row editor (label, editor, error, commit on Enter or focus loss, revert) built separately in `AssetPropertiesPanel` and `EntityDetailsView`; `AssetPropertiesPanel` is 668 lines | Duplication, SRP | S |
| D7 | `testProject()` copied in three test source sets; the `abyssus.testData` path set in four build scripts | Duplication (tests, config) | S |
| S1 | `SceneViewPanel` (882 lines, 59 functions) owns ray control, canvas lifecycle, toolbar, play controls and input; 13-parameter constructor with paired lambdas | SRP, ISP | S |
| S3 | `EntityDetailsView` hard-codes `LightComponent` label and tooltip keys in the Swing view | OCP | S |
| K1 | `lib-core-editor` re-exports `lib-core`'s format types through five `typealias`es that 23 files import | KISS | S |
| D8 | `AssetMetaEditor` re-implements `parseUuidOrNull` | Duplication | O |
| G2 | The sky ray-snapshot loader dispatches with a `when` beside the `CompositeAssetLoader` map | GoF (Composite) | O |
| K3 | The ray feasibility spike (`abyssus.raytracing.experiment`) ships in production classes | KISS | O, blocked |

How each is fixed:

- **Baseline (B2, B3, D1).** Fix the verifier allowlist path and the CI and run-configuration task paths; delete
  `MetalSlice.kt` and the tracked `.DS_Store` files.
- **One registration per asset kind (D2, optional G2).** `lib-core` offers the standard `MetaType` → loader table as
  one function. The plugin, `BaseCtx` and the game take it instead of listing loaders themselves.
- **One JSON format (D4), one name per type (K1, D8).** Move the shared code to the lowest module that both callers
  already depend on (`lib-core`), and point callers at it.
- **Use the template that exists (D5/G1).** The six actions extend `AbyssusTreeAction`; `selectedNode` stays the one
  selection lookup.
- **One field row (D6, S2, S3).** A small Swing helper in `plugin/ui` builds the row and its commit/revert wiring;
  both panels use it. Light field labels and tooltips move into the field descriptions that `lib-core-editor`
  already produces.
- **Split `SceneViewPanel` (S1)** along its existing seams: toolbar, input forwarding, ray control binding. The
  paired `xActions`/`canAddX` lambdas (light, asset, and the other optional menu callbacks) become one small interface each.
- **Tests (D7).** One `testProject()` in `lib-core`'s `testFixtures`, one `abyssus.testData` setting in the root
  build.

### Out of scope

- Any change to `.scene`, `.abss` or `meta.json`: no field is read or written differently. Validation through
  `AbyssusDocumentFormat` stays before binding on every path; D4 and K1 move code and imports, not checks. Writes still go
  only through `editSceneJson`.
- Reopening decisions already made: field-type switches stay (`refactor-solid-dedup` 3.1 prototype failed its size
  gate), the `PhysicsWorld` and Vulkan/Metal backend seams stay as recorded there, and `AbyssusCore` keeps its groups.
- `lib-gdx`'s inherited classes (`Mesh`, `DefaultShader`, `Model`). Its package is already `net.nevinsky.abyssus.lib.gdx`.
- Cube-to-equirect sky resampling (`SkyboxRaySnapshotLoader` and `RaySkyBaker`). They look alike but differ: nearest
  sampling at 1024 wide versus bilinear sampling at 2048 wide with a cached mapping, so sharing code would change
  pixels. Recorded as a non-finding in `design.md`.
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

- **Build and CI:** `gradle/plugin-verification.gradle.kts`, `build.gradle.kts` (test data property), four module build
  scripts, `.github/workflows/build.yml`, `.run/Run Plugin Verification.run.xml`.
- **`lib-core`:** `io/JsonProcessor`, a new `io/JsonFormat` (moved), the standard loader table (also used by
  `BaseCtx`), `testFixtures`.
- **`lib-core-editor`:** `document/JsonFormat` (removed), `document/DocumentFormat.kt` (removed),
  `meta/AssetMetaEditor`, field label descriptions.
- **`lib-raytracing`:** `MetalSlice.kt` deleted.
- **`app-game-control-line`:** `render/FieldRenderer` uses the loader table. `lib-physics` is unchanged: `PhysicsAssets`
  takes its loaders by constructor.
- **`plugin-abyssus`:** `AssetLoading`, `projectView/{RenameScene,NewTerrain,NewFoliage,ImportModel,ImportFlightGear}Action`,
  `gameui/UiAssetActions`,
  `properties/{AssetPropertiesPanel,EntityDetailsView}`, `sceneview/SceneViewPanel`, new `ui/` helper.
- **Docs:** `docs/ai/architecture.md`, `docs/ai/conventions.md` and the `sceneview/` and `projectView/` READMEs, only
  where this change makes them wrong.
- **Open changes:** `add-scene-raytracing` (K3, ray files and `SceneViewPanel`), `add-realistic-water` and
  `add-remote-asset-library` (new asset kinds or the properties panel). Whichever lands second rebases (see design,
  Risks).
- **No new dependencies.** No public plugin extension point changes.
- **Survey limits:** read-only and static. Gradle did not run for the original survey (Maven Central answered HTTP 429)
  or for the 2026-10-12 re-check. Task 1.1 re-establishes the baseline before any code change.
