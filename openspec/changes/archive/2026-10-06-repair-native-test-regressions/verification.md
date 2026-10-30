# Test-only repair verification — 2026-10-06

The user authorized repairing tests without changing current production logic. Repairs update ECS access through
`SceneEcsPaths`, native DTO expectations, deterministic identifiers, test-owned formatting, metadata replacement
anchors and disk-backed EXR preview inputs. Exact preservation, undo, invalid-input and preview assertions remain.
No production source was changed to address the failures described here.

## Historical verification before the authorized continuation

- Clean full check: `./gradlew :clean check --continue --console=plain`; editor 916 tests, 44 failures,
  40 skipped; runtime 99 tests, 6 failures. Log: `/private/tmp/abyssus-test-repair-clean-check.log`.
- Final focused run: 85 tests, 6 failures. ComponentActionsTest (8), AddLightActionTest (1), NewTerrainActionTest
  (17), SceneTransformEditTest (4), and SceneComponentEditsTest (8) all pass. Remaining failures are HDR choices,
  HDR thumbnails, HDR panel previews/errors and panel undo/ray edits. Log:
  `/private/tmp/abyssus-test-repair-focused-final.log`.
- Passing module suites in the clean check: core 214 (13 skipped), physics 39, raytracing 229 (114 skipped),
  physics-plugin 11, gdx-model 27 (9 skipped), Control Line 97. Source-rule checks pass.
- Docs path check: 198 paths in 6 files. Strict OpenSpec validation and whitespace checking pass.
- Final full check after the remaining setup repairs: editor 916 tests, 41 failures, 40 skipped; runtime 99 tests,
  6 failures. All other module checks pass. Log: `/private/tmp/abyssus-test-repair-check-final.log`.
  Exact failures are recorded in `remaining-failures.md`. Integration has not passed.

## Historical causes

- The shared Untitled fixture was edited concurrently: two additional entities and `model_extra500` now change
  fixed entity/asset counts, marker counts and ray snapshot limits. These external changes were preserved.
  All six runtime failures in the clean check are the nine-entity expectations against eleven current entities.
- `SceneRayEdits.kt` has its mutation call commented out: valid edits return `Rejected(OBJECT)`. Several panel,
  material and exact-undo assertions consequently cannot pass through test setup changes.
- `RayViewFeed.kt` has its settings-signature assignment commented out: repeated invalidation prevents the
  expected asynchronous frame publication.
- Typed scene binding converts explicit null ray settings to defaults, contrary to the rejection assertion.
- `SkyboxChoices.kt` currently returns HDR dimensions as zero. Preview decoding also lacks a working TinyEXR
  native library in this test environment; valid disk-backed EXR input still cannot produce a thumbnail.
- The ordinary asset-panel undo test reports an enabled Undo control before its first edit; its assertion remains.

Fresh-context review confirmed the verifier consistency fix and the production/fixture distinction; a final review
of the disk-backed EXR and ECS path repairs found no weakened assertions. Task 2.1 records the completed review
and verification run, including failures; it does not establish a passing integration gate. Required suite gates
remain open.

## Authorized continuation

TinyEXR natives now use `testRuntimeOnly` for the existing supported classifiers. A separate user-authorized
production prerequisite, `repair-ray-editor-regressions`, restores the four defects above; those production edits
are outside this test-only change. Stable `Tree` metadata and scene inputs preserve the original nine-entity and
nine-asset assertions while leaving the interactive Untitled fixture untouched. Ray geometry still loads real
Untitled binary assets, with the controlled scene deciding which assets to load.

The ordinary panel undo test clears only its fixture document's setup history before opening the panel, retaining
the initially disabled Undo assertion and exact undo/redo text checks. The face chooser compares terminal
whitespace consistently on both sides, and asset rows recognize both observed JVM getter placements while
pinning complete rows and repeat ordering. Focused suites passed: grouped actions/readers 68 tests, runtime 99,
and HDR/chooser/asset-panel 48. Fresh-context review covers both the test repairs and production prerequisite.

Final full check passes: 1,644 tests, zero failures/errors, 177 skipped. Editor 917 (40 skipped), core 216 (13),
runtime 99, physics 39, physics-plugin 11, raytracing 229 (114), gdx-model 27 (9), Control Line 106 (1).
This includes every sceneview, properties and terrain case covered by tasks 1.2/1.3; both gates are now complete.
Source checks and coverage verification also pass. Log: `/private/tmp/abyssus-solid-continuation-check.log`.
Docs checking passes for 199 paths, strict OpenSpec validation passes, and review found no material weakening
in the final getter-order and symmetric-whitespace expectation repairs. All five tasks are complete.
