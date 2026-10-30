# Proposal

## Why

Existing real-IDE tests cover selected tree, properties, rename, skybox and toolbar behavior, but do not prove complete editing lifecycles across the plugin. Creators need confidence that adding, editing, undoing, reopening and removing content works together without corrupting their project.

## What Changes

- Expand the existing Remote Robot suite into a feature-by-feature full-cycle regression suite for currently implemented plugin workflows. Cover valid edits, rejection/cancel paths, undo/redo, save/reopen, removal where offered, and agreement between UI and persisted state.
- Add a single-entity **Remove Entity** action to the Abyssus tree, as an explicit deliverable of this change, with reference guards, selection cleanup, native document preservation and one undoable command. Do not cascade-delete other entities or assets.
- Maintain an explicit coverage matrix mapping implemented entry points to executable scenarios, fixtures, assertions and execution environments. Exercise all implemented feature families; pure rendering quality remains covered by appropriate GL checks rather than pixel-perfect viewport baselines.
- Define the executable acceptance-coverage contract in `plugin-ui-regression-coverage`, with code-grounded WHEN/THEN scenarios for component/entity editing, field commit/rejection behavior, typed asset properties, per-entity material optics, tree/skybox actions, scene gestures/display preferences, all five saved ray limits, water, terrain, foliage/painting, imports/weather, Game UI, physics/Play, native admission and editor lifecycle. Each scenario states the visible result and persisted changes or required absence of writes.
- Fix confirmed product defects exposed by these scenarios, retaining the failing regression and adding lower-level tests where useful. Do not weaken assertions or silently rebaseline a broken UI.
- Reuse the canonical Linux/Xvfb runner, fixture isolation, screenshot checks and CI diagnostics. Keep real-IDE tests separate from headless `check`.

Native files remain version 1: validate `format: "abyssus"` and integral `formatVersion: 1` before edits. Entity removal changes only the selected entry under `ecs` (or admitted wrapped `ecs.entities`); it reads built-in and registered entity references before deletion. Other scenarios exercise existing fields including scene `name`, environment flags/settings, `skyboxName`, component payloads, `abyssus.waterSurfaces`, project `physicsEnabled` and `uiAssetNames`, and asset `additional` data including UI declarations. Preserve unknown extension data, unrelated number spelling/key order and omitted defaults. No format migration or new format version is proposed.

Out of scope: implementing unbuilt features from other open proposals, editor redesigns, exhaustive combinations of every renderer setting, new general asset/scene deletion features, runtime game feature testing, live remote-service dependencies and unrelated refactoring. Existing asset creation lifecycles end through their supported undo/unregister paths when permanent deletion is unavailable.

## Capabilities

### New Capabilities

- `plugin-ui-regression-coverage`: the regression suite's executable acceptance coverage for implemented plugin workflows, including full editing cycles, rejected/cancelled operations, document preservation and lifecycle recovery. This specifies coverage of existing product contracts; it does not introduce additional product editors or replace their feature specs.

### Modified Capabilities

- `scene-component-editing`: remove a whole entity through the tree, reject incoming references, preserve unrelated data and restore the operation with undo/redo. Correct its stale `Untitled` example (next entity id `11`, not `9`).
- `scene-loading`: correct the stale `Untitled` entity-id example (`0`–`10`, not `0`–`8`).
- `scene-asset-placement`: correct the stale next-entity example (`11`, not `9`).
- `scene-light-creation`: correct the stale next-entity example (`11`, not `9`).

## Impact

- Tests: `projects/plugin-abyssus/src/uiTest`, relevant plugin platform tests and `lib-core-editor` tests; deterministic local fixture inputs for UI assets, imports, physics and generated content.
- Product code: narrowly scoped entity-removal logic in `lib-core-editor`, its plugin tree action/registration/messages, and demonstrated defect fixes in affected modules.
- Execution/docs: existing `scripts/run-ui-tests*.sh`, plugin test configuration and `.github/workflows/run-ui-tests.yml` only as needed for suite capacity and diagnostics; update `docs/ai/testing.md`, project-view notes and user-facing release notes when implemented.
- No new production dependency, SDK upgrade or shipped runtime/editor dependency is required.
