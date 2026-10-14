# Design

## Context

See proposal.md for motivation and R1–R8 scope. Inspection on 2026-10-14 found:

- `settings.gradle.kts` includes seven modules. `separate-shipped-code` still names removed `lib-runtime`, `lib-gdx-model` and `plugin-abyssus-physics`; FlightGear import and EcsWriter are already in `lib-core-editor`. Ray snapshots remain in core and Play remains in physics.
- `StandardAssetLoaders` already centralizes loader registration. Do not recreate this abstraction.
- `shadowCompare`, `shadowBilinear`, `shadowTileVisibility` and `directionalVisibility` are identical in `lib-gdx`'s default/PBR fragment shaders and the plugin's terrain fragment shader. `ShaderStorage.fragment` already supports concatenation, but model shaders have their own loading path.
- `FieldRenderer` owns a separate terrain program and single-sun lighting; `SceneRenderer` owns multi-light, environment and cloud-shadow integration. `FieldShadows` already uses the shared model depth shader and atlas attribute with one tile. Its projection and terrain shadow filter differ from the editor's.
- `AssetStorage` combines worker publication, generations, dependencies, GPU stepping and disposal. Its tests already cover late completion, replacement, cycles and abandonment; preserve these rather than replacing the pipeline.
- `AbyssusProjectViewPane` subscribes to `AbyssusSceneEdited`; `ConfigFileReader` already reads unsaved text and includes document stamps. Refresh notification is the missing seam, not another parser. `AssetReadCache` handles deletion/move eviction and stamp-based reuse.
- `.github/workflows/build.yml` uses Java 21 and requires shader compilation; release.yml uses Java 11. GL/native rendering and timing tests are opt-in. Existing test presence establishes no measured baseline.

This design is cross-module and includes resource-lifecycle migration, so a design artifact is required. No source or test execution was performed while proposing it.

## Goals / Non-Goals

**Goals:** one owner per repeated algorithm; independently testable bookkeeping; reproducible migration evidence; documented dependency gates. Preserve existing native document semantics and per-host visuals.

**Non-Goals:** a universal scene renderer, identical game/editor quality defaults, moving editor picking or undo into runtime code, a new event bus, automatic performance tuning, and abstracting every repeated expression.

## Decisions

### D1. Phase ownership and dependencies

This change owns R1, R3, R4, R6, R7 and R8 plus reconciliation/integration for R2/R5. Implementation of shipped-code separation and stock g3d migration remains in their existing changes. Their completion must be verified before R6; file-existence status is insufficient. Do not silently apply those changes as part of this one.

Reconcile R2 against current source: retain FlightGear/EcsWriter where they already meet the shipped boundary; remove obsolete move tasks; adapt remaining ray and Play separation to core/physics/plugin modules actually present. Retain its game-distribution requirements. Revalidate that change after updating its artifacts. R5 remains responsible for stock model compatibility, 32-bit indices and instancing migration.

R3 may precede R5, but its shared fragment must be used by both temporary pipelines in R5 and survive deletion of the old resources. Amend that change's source-copy tasks accordingly. The tree delta adds synchronization without replacing layout requirements, so it composes with `redesign-abyssus-tree-view`. Water's target module references must be reconciled when R6 lands. No FPS UI is added.

Alternative: one large migration implementing all open proposals. Rejected because it hides ownership and makes rollback and test attribution difficult.

### D2. Common GLSL without a shader language framework

Place the shared atlas/visibility GLSL under `lib-gdx` resources, accessible to both its model shaders and the terrain path without introducing a dependency cycle. Add a GL-free source assembler in `lib-gdx` that keeps declarations/defines before functions and retains diagnostic source labels. Both model shader loading and `ShaderStorage` callers consume the same common source. Do not import core into gdx.

Extract the four verified identical functions and their common dependencies/declarations as a cohesive fragment. Preserve preprocessor guards, uniform names, texture units and define ordering. Source checks verify assembly and missing-resource diagnostics; GL checks verify plain/PBR/terrain, cutouts, skinning and instancing. Concatenation already used elsewhere is sufficient; a general include compiler is unnecessary.

### D3. Measure before removing old rendering paths

Use existing `TestGl`, plugin `GlHarness`, field rendering tests and native timing tests. Add an opt-in report runner and a documented GPU-capable execution lane; CPU CI validates report schema and explicit skipped/unavailable status. Do not assume hosted Linux runners provide representative GPUs.

Workloads use disposable copies of Untitled, Animated, Foliage and Control Line. Add a deterministic stress scene with 1,000 repeated model placements, a terrain and foliage, recording the resulting triangle/instance counts and seed. Fixed cameras, time, resolution (1280x720), cloud settings and warmed resources make comparisons repeatable. Run five warm samples of 30 seconds after a 10-second warmup; separately record cold-load and reload times. Capture CPU frame p50/p95/p99, allocations/GC, upload work and asset counts. GPU duration is reported only when measured independently; submission/readback duration must be labeled as such.

Record commit, dirty state, OS, JDK, GPU/driver, backend, scene hash and settings. Compare only matched environments. Establish baseline first; then use a provisional 10% p95/steady-state allocation regression threshold. If baseline variability exceeds that threshold, mark the gate inconclusive and improve repeatability instead of accepting a regression. No absolute FPS or speedup is promised. Pixel tolerances must be recorded from same-device repeated runs before comparing old/new paths; do not widen them to hide migration changes.

The publishing rehearsal builds/verifies/inspects the unsigned plugin package on Java 21 without publish credentials or a release event. Signed publishing remains an existing release operation, not a validation step in this change.

### D4. A small runtime rendering module

After R2/R5 gates, add `lib-render`, plain JVM and constructor-wired, depending on `lib-core` and `lib-gdx`. Plugin and game depend on it; core never depends back on it. It must contain no IntelliJ, Swing, AWT, editor, physics or ray-backend types. Apply existing singleton/package-cycle checks and a classpath/import boundary test.

Start with terrain shader binding/drawing and reusable shadow resource/depth submission primitives. Define host-neutral frame inputs: camera matrices, terrain drawables and transforms, light/environment values, atlas views and optional directional visibility. Pure light selection, projection and binding-plan calculations are GL-free. Adapt editor `LightSet` and game scene data at host boundaries rather than moving editor scene types into the new module.

Preserve different outputs as named policies: editor multi-light atlas/environment/cloud visibility; game single sun, existing fixed projection/filter, layered clouds, fallback terrain color and fog behavior. Share terrain sampling and uniform-binding mechanics, with explicit shader variants for the genuinely different lighting/filtering. Remove duplicated game/editor terrain assembly and binding logic, not the intentional policies. Matched-policy parity is tested separately from each host's before/after comparison.

Keep asset storage outside the renderer; shared passes borrow resources and dispose only what they create. Keep editor grid/gizmos/ray presentation and game lines/hidden-pilot handling in the hosts. A monolithic shared renderer would pull unrelated state into every game and is rejected.

### D5. Extract ownership, not a new asset framework

Retain the public `AssetStorage` API and render-thread ownership. Extract package-internal revision/dependency bookkeeping into GL-free collaborators receiving immutable completion messages and explicit generation IDs. `AssetStorage` remains the owner that performs upload/build/discard and dispatches notifications. Worker preparation publishes messages; it never mutates render-owned structures or invokes GL. Retain current cancellation propagation, dependency-failure behavior and once-per-revision logging.

After R6 removes common drawing, extract the remaining editor render-resource lifecycle into a small owner with create, dispose-on-valid-context and abandon-without-GL operations. Keep context generations explicit so old handles are never deleted through a new context. Pure bookkeeping tests use fake resources to count release/abandon; real GL tests exercise context replacement. Avoid a generic service container or merging dispose and abandon.

### D6. One tree refresh coordinator

Add a project/pane-lifetime coordinator consuming existing edit notifications, document changes and relevant VFS create/delete/move/rename/content events. Filter by project membership and native document/metadata paths; coalesce bursts into one pending update, keeping a dirty generation so events during a rebuild trigger a subsequent refresh. Reuse `ProjectReader`, `SceneReader` and stamp-based caches.

Keep a pure dirty-set/generation policy testable without Swing/GL/platform types in `lib-core-editor`; the plugin owns listener registration, VFS identity, read actions and scheduling. Capture identity-based selection/expansion before update and restore surviving entries; clear stale selection when the entry is gone. Use file identity plus entity ID/property path, never label text. Unrelated file events do not rebuild the tree. A full polling scan or notifications in every action would retain duplication and miss external edits.

Document events capture changed identities on the EDT; background tree construction uses platform read access. UI refresh and selection restoration occur on EDT, with callbacks canceled on disposal. Existing unsaved document precedence is retained. External-change scenarios begin when the IDE detects the change; this does not implement a filesystem watcher or resolve conflicting unsaved text.

### D7. Thread and execution boundaries

| Piece | Execution and ownership |
|---|---|
| Pure source assembly, revision/refresh policy, binding plans | No GL/platform state; deterministic headless tests |
| Asset prepare and CPU snapshots | Existing worker/executor contract; cancellation preserved |
| Plugin shared rendering and resource lifecycle | AWT render thread, inside `GdxRuntime.withContext`, only while canvas is safe |
| Game shared rendering | Existing libGDX render thread with current context |
| Tree listeners and UI restoration | Plugin lifecycle; capture document identities and update UI on EDT; model reads under read access |
| Native ray sessions | Existing serial native owner; no migration in this change |
| Benchmark/report collection | Samples on owning thread, aggregation/output outside hot rendering path |

## Risks / Trade-offs

- Broad program of work -> deliver separate phases with baseline reports and independently revertible commits.
- Intentional game/editor differences mistaken for bugs -> preserve explicit profiles and compare each host against its own baseline.
- Shader guards and resource packaging -> source assembly tests plus actual GL variants and jar inspection.
- Lifecycle regressions -> preserve late-completion, invalidation and abandon tests before extracting collaborators.
- GPU hardware unavailable -> CPU tasks can finish; GPU/manual acceptance tasks remain open, never marked passed through skips.
- Conflicting open changes -> reconcile named artifacts before touching shared areas, keeping each requirement's owner explicit.
- Refresh storms or lost navigation -> coalescing tests, generation tests, platform Undo tests and real IDE navigation checks.

## Migration Plan

1. R1/R2: fix release configuration and reconcile dependency artifacts. Verify packaging without publishing.
2. R4: capture pre-change graphics evidence. R3 then shares shader source under existing contracts.
3. R8 can ship independently once its headless/platform/UI checks pass.
4. Wait for separately applied R2/R5 implementations and verify their acceptance evidence.
5. R6: add shared passes, compare old/new in tests, switch editor then game adapters, delete superseded code after both pass. The temporary old path is test-only, not a permanent user setting.
6. R7: extract asset bookkeeping and remaining view lifecycle in separate commits; re-run lifecycle and graphics gates.
7. Refresh docs and run final project checks. Archive only with manual/GPU results honestly accounted for.

Rollback is per phase: revert a host switch to its previous renderer while shared code remains additive; remove old code only after both hosts pass. Revert tree subscriptions independently. No native document conversion is required, so rollback does not rewrite user projects.

## Open Questions

- Exact GPU runner identities and scheduling availability: choose and record them before R4 capture. Until then there is no platform-wide performance claim.
- Maintainer availability and release milestone affect scheduling, not the dependency graph. Estimates from review remain approximate: R1 0.5–2 days, R2 1–2 days, R3 2–4 days, R4 3–6 days, external R5 2–4 weeks, R6 3–6 weeks, R7 1–3 weeks, R8 2–5 days.
