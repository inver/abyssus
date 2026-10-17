# Proposal

## Why

Abyssus repeats shadow algorithms across three shaders and maintains separate editor/game terrain rendering paths, making fixes expensive and prone to drift. Refactoring these paths needs reproducible graphics evidence, a consistent release toolchain, and current dependency plans; the tree also needs to reflect Undo and external changes without manual refresh.

## What Changes

- R1: Align build and release Java configuration at Java 21 and add a non-publishing packaging rehearsal.
- R2: Reconcile the existing `separate-shipped-code` plan with the current seven-module checkout before relying on its shipped-runtime boundary. This change owns reconciliation and integration checks, not a second implementation of that proposal.
- R3: Assemble default, PBR and terrain shaders from shared shadow/visibility source; retain host-specific quality policies.
- R4: Add repeatable graphics regression and performance workloads, recording hardware, software, quality, frame-time tails and allocation evidence. Extend the existing opt-in harnesses; do not equate skipped GPU tests with successful validation.
- R5: Consume `shrink-lib-gdx-to-stock-g3d` as a dependency for renderer extraction, with its own implementation and compatibility gates remaining in that change.
- R6: Introduce a plain JVM `lib-render` for shared terrain rendering, shader bindings and shadow primitives. Keep editor overlays, game control-line drawing, cameras and quality choices in their hosts. Preserve each host's current output; visual convergence is a separate feature decision.
- R7: Separate asset revision bookkeeping from resource execution and extract renderer resource ownership without changing loading, cancellation, context-loss or disposal behavior.
- R8: Refresh the Abyssus tree from document and filesystem changes, including Undo/Redo, using one coalesced refresh path and preserving surviving selection/expansion.

Native documents: no new fields, format versions, migration or write paths. Rendering continues to consume validated scene environment and `ecs` data, project scene listings, and asset metadata as today. Tree refresh rereads existing `.scene`/`.abss` fields (including `name`, `fogEnabled`, `skyboxName`, `ecs`) and asset metadata after validation; it writes nothing. Existing user edits still use `editSceneJson`, preserving unknown fields, key order and number text. Unsupported documents remain unchanged.

Out of scope: new graphics backends, a render graph, water, antialiasing, new lighting behavior, tree redesign, an FPS UI, physics changes, importer changes, mass UI-helper deduplication, and publishing a release. No new external library is required. The stock-model API migration remains the breaking change documented by `shrink-lib-gdx-to-stock-g3d`; this change does not redefine it.

## Capabilities

### New Capabilities

None. Rendering refactors and validation tooling preserve current behavior.

### Modified Capabilities

- `abyssus-project-view`: automatic synchronization after document changes, Undo/Redo and detected filesystem changes, without writes or lost surviving navigation state.

## Impact

- Build workflows, graphics test/report infrastructure, `lib-gdx` shader resources, `lib-core` asset storage, a new `lib-render` module, editor rendering and tree adapters, and Control Line rendering.
- Existing `asset-loading`, `scene-shadows`, model/foliage/sky rendering and headless editing contracts remain acceptance constraints, not rewritten requirements.
- Coordination: update stale paths and already-completed work in `separate-shipped-code`; keep `shrink-lib-gdx-to-stock-g3d` shader migration aware of shared source; preserve `redesign-abyssus-tree-view` layout and selection intent. `add-project-fps-counter` remains independent presentation work. Reconcile water's renderer targets when extraction lands.
- Documentation: module/package READMEs, AGENTS.md, architecture/testing notes, and removal of the documented tree-refresh limitation once verified.
- Delivery is phased. R1–R4 and R8 can progress before the external migration gates; R6 waits for R2/R5 and graphics evidence. Planning completion does not mean those dependencies have been implemented.
