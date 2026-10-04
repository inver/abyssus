# Tasks

## 1. Native format prerequisite and API contract

- [ ] 1.1 Confirm `decouple-from-mundus` has delivered the shared native validator and reconcile transaction integration with current refactor code; verify native asset/project acceptance and legacy refusal through its format tests, then `./gradlew :core:test`.
- [ ] 1.2 Promote this change's `openapi.yml` to repository-root `openapi.yml`, add a pinned OpenAPI validation task to `check`, and test required fields/examples/local references using `AssetLibraryContractTest`; verify `./gradlew :test --tests '*AssetLibraryContractTest'` and the contract validation task.
- [ ] 1.3 Document endpoint configuration, public access, cursor semantics, ZIP manifest and package limits in API/file-format guidance; verify links and `scripts/check-docs.sh`.

## 2. Catalog and transport

- [ ] 2.1 Add constructor-wired catalog models/state reducer in core with query reset, cursor paging, version pinning and stale-result rejection; verify `./gradlew :core:test --tests '*AssetCatalogStateTest'` covering out-of-order completion, empty results, invalid cursor and selection changes.
- [ ] 2.2 Add project-scoped endpoint settings and cancellable JDK HTTP adapter with URL/redirect policy, response limits, timeouts and error mapping; use a loopback fixture server to verify anonymous requests, 400/404/429/503, blocked redirects and disposal via `./gradlew :test --tests '*AssetCatalogClientTest'`.
- [ ] 2.3 Implement bounded background thumbnail decoding and placeholder failures; verify `./gradlew :test --tests '*AssetLibraryPreviewTest'` for malformed/oversized images, dimension limits and cancelled requests.
- [ ] 2.4 Document module boundaries, background/EDT responsibilities and disposal in architecture/testing guidance; verify `scripts/check-docs.sh` and the added core classes pass `./gradlew :core:checkNoSingletons`.

## 3. Download and package validation

- [ ] 3.1 Implement streaming download, SHA-256/size verification, inactivity timeout, cancellation, staging cleanup and download-only finalization without overwriting output files; verify `./gradlew :test --tests '*AssetPackageDownloadTest'` for success, truncation, checksum failure, excessive size, existing output and cancellation.
- [ ] 3.2 Implement pure package/manifest validation and bounded extraction; verify `./gradlew :core:test --tests '*AssetPackageValidatorTest'` for native models/materials/textures, cycles, missing closure/files, identity mismatch, absent licenses, unsupported versions, traversal, symlinks, duplicate/case paths, extra entries and actual extraction limits.
- [ ] 3.3 Add shared package dependency/resource validation covering supported metadata types and external model resources without changing local usage semantics; verify `./gradlew :core:test --tests '*AssetPackageReferencesTest'` for missing material/texture references and external files.
- [ ] 3.4 Document the exact limits, supported package types and retained LICENSE.txt behavior alongside fixture construction instructions; verify all documented fixtures exist and `scripts/check-docs.sh` passes.

## 4. Import planning and commands

- [ ] 4.1 Implement pure import planner using complete destination inventory, native root validation, case/name/UUID collision checks and created-directory plans; verify `./gradlew :core:test --tests '*AssetImportPlannerTest'` for no assets directory, multiple included assets, duplicate import and malformed local metadata.
- [ ] 4.2 Integrate planner with `AssetFileCommand`, final live inventory/unsaved-text checks, create-new writes and destination symlink refusal; verify `./gradlew :test --tests '*AssetLibraryImportTest'` for changed destination, new UUID collision, concurrent creation, unchanged `.abss`/`.scene`, write rollback and reported rollback leftovers.
- [ ] 4.3 Extend transaction forward/Undo guards for import sets, excluding internal package references but protecting external saved/unsaved references; verify `./gradlew :test --tests '*AssetLibraryImportUndoTest'` for full Undo/Redo, changed files, external scene/material references, new UUID collision before Redo and Redo without network. Rerun existing asset transaction tests to guard terrain behavior.
- [ ] 4.4 Document explicit import writes/rollback limitations and set-aware Undo behavior in architecture and conventions guidance; verify `scripts/check-docs.sh`.

## 5. Bottom tool window

- [ ] 5.1 Register bottom tool window and build search/filter/results/details UI with bundled strings, version/license/dependency display and project destination chooser; verify `./gradlew :test --tests '*AssetLibraryPanelTest'` for setup/loading/empty/error states, disabled import without project and multi-project selection, plus runIde check 1 below.
- [ ] 5.2 Connect Download/Import controls, progress/cancellation/finalization and local tree refresh; verify `./gradlew :test --tests '*AssetLibraryControllerTest'` for stale selection, cancellation, repeated-click prevention, disposal and one refresh per successful import, plus runIde checks 2-4.
- [ ] 5.3 Update README (preserve plugin-description markers), CHANGELOG and docs index with user-facing library setup/import behavior; verify `scripts/check-docs.sh` and review the rendered plugin description.

## 6. Integration verification

- [ ] 6.1 Run a local fixture server and `./gradlew runIde` against a native-format copy of Untitled outside repository fixtures. Verify numbered checks: (1) bottom default docking, move/hide, search/type/pagination/details and placeholders; (2) download-only ZIP, progress/cancel/retry and no project edits; (3) import model plus dependencies, tree refresh/unused marking, existing scene unchanged, Undo/Redo from library; (4) folder/UUID conflicts, offline/rate-limit/retry, multiple project choice and close-project cancellation. Record results; leave unchecked if unavailable.
- [ ] 6.2 Exercise one maximum-supported package and measure memory/time during staging/commit/Undo, then verify imported model can be selected for ordinary scene use in a disposable native project; run the relevant existing model GL tests with `-Dabyssus.glTests=true`. Record resource results and reduce documented client limits consistently if needed.
- [ ] 6.3 Run `./gradlew check` and `scripts/check-docs.sh`; record unrelated failures separately and verify the root OpenAPI contract validation is included in `check`.
