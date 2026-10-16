# Tasks

Phases follow design.md's migration plan. Groups 1–4 and 5 (R8) need none of the external gates. Group 6 starts only after
`separate-shipped-code` and `shrink-lib-gdx-to-stock-g3d` are implemented and their acceptance evidence is verified.
GPU/manual items are never marked done from a skipped test.

## 1. Release toolchain and dependency reconciliation (R1, R2)

- [x] 1.1 Change `.github/workflows/release.yml` from Java 11 to Java 21 to match `build.yml` and `build.gradle.kts`; verify with `grep -n "java-version" .github/workflows/*.yml` showing one version.
- [x] 1.2 Add a non-publishing packaging rehearsal (a workflow job or documented script) that runs `./gradlew :plugin-abyssus:buildPlugin` plus `checkNoJoltInZip` on Java 21 with no publish credentials or release trigger; verify by running it locally and confirming the unsigned zip in `projects/plugin-abyssus/build/distributions/`.
- [x] 1.3 Update `openspec/changes/separate-shipped-code` artifacts to the current seven-module checkout: drop `lib-runtime` / `lib-gdx-model` / `plugin-abyssus-physics` references and already-done FlightGear/EcsWriter moves, keep game-distribution requirements; verify with `openspec validate separate-shipped-code`.
- [x] 1.4 Amend `shrink-lib-gdx-to-stock-g3d` shader-copy tasks so both temporary pipelines consume the shared shadow fragment from group 3; verify with `openspec validate shrink-lib-gdx-to-stock-g3d`.
- [ ] 1.5 Note in `add-realistic-water` that its renderer module targets are reconciled when group 6 lands; verify with `openspec validate add-realistic-water`.
- [x] 1.6 Document the Java 21 baseline and rehearsal command in `docs/ai/testing.md` (or the build section of `AGENTS.md`); verify with `scripts/check-docs.sh`.

## 2. Graphics regression and performance evidence (R4)

- [x] 2.1 Choose and record the GPU-capable runner identities and the execution lane in `docs/ai/testing.md`, resolving the design's open question; verify the doc names each runner and states that CPU CI only validates schema/skipped status.
- [x] 2.2 Build the deterministic stress scene (1,000 repeated model placements, terrain, foliage, fixed seed) as a disposable-copy fixture generator; verify with a headless test asserting the stable scene hash and triangle/instance counts.
- [x] 2.3 Add a report data model and writer in the existing test fixtures: commit, dirty state, OS, JDK, GPU/driver, backend, scene hash, settings, p50/p95/p99 CPU frame time, allocations/GC, upload work, asset counts, cold-load/reload times, with explicit `skipped`/`unavailable` status; verify with a headless schema test including the skipped case.
- [x] 2.4 Add the comparison policy (matched environments only; provisional 10% p95/allocation threshold; "inconclusive" when baseline variance exceeds it); verify with unit tests for pass, regress, inconclusive and mismatched-environment inputs.
- [x] 2.5 Add the opt-in workload runner extending `TestGl`, `GlHarness` and `FieldRendererGlTest` harnesses (1280x720, fixed cameras/time/clouds, 10 s warmup, five 30 s samples) over copies of Untitled, Animated, Foliage, Control Line and the stress scene; verify with `./gradlew :plugin-abyssus:test -Dabyssus.glTests=true` on a GPU machine producing a report.
- [x] 2.6 Record same-device repeated-run pixel tolerances for plain/PBR/terrain, cutout, skinned and instanced cases and capture the pre-change baseline report; verify the baseline report is committed or stored per 2.1 and referenced from `docs/ai/testing.md`. (GPU/manual: leave unchecked until measured.)

## 3. Shared shadow/visibility shader source (R3)

- [ ] 3.1 Add a GL-free shader source assembler in `lib-gdx` that keeps declarations/defines before functions and retains source labels; verify with unit tests for ordering, labels and missing-resource diagnostics.
- [ ] 3.2 Extract `shadowCompare`, `shadowBilinear`, `shadowTileVisibility`, `directionalVisibility` and their shared declarations into one GLSL resource under `lib-gdx`, preserving guards, uniform names, texture units and define order; verify with a test that the assembled default/PBR/terrain fragments contain each function exactly once.
- [ ] 3.3 Switch the default and PBR model fragment shaders to the shared source; verify with `./gradlew :lib-gdx:test` and, with `-Dabyssus.glTests=true`, GL compile of plain/PBR variants incl. cutouts, skinning and instancing.
- [ ] 3.4 Switch the plugin terrain fragment shader through `ShaderStorage.fragment` concatenation to the same source; verify with `./gradlew :lib-core:test --tests 'net.nevinsky.abyssus.lib.core.assets.ShaderStorageTest'` and the plugin scene-view GL tests, then with `-Pabyssus.requireShaders=true`.
- [ ] 3.5 Compare group 2 workloads against the baseline within the recorded pixel tolerances; verify the report shows no unexplained difference. (GPU/manual.)
- [ ] 3.6 Document the shared shader source in `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md` and `docs/ai/architecture.md`; verify with `scripts/check-docs.sh`.

## 4. Asset storage bookkeeping (R7, part 1)

- [ ] 4.1 Confirm the existing `AssetStorage` tests for late completion, replacement, cycles and abandonment pass unchanged before refactoring; verify with `./gradlew :lib-core:test`.
- [ ] 4.2 Extract package-internal, GL-free revision/dependency bookkeeping consuming immutable completion messages and explicit generation IDs; `AssetStorage` stays the owner of upload/build/discard and notifications; verify with new fake-resource unit tests for generation ordering, dependency failure and once-per-revision logging.
- [ ] 4.3 Verify cancellation propagation and the public `AssetStorage` API are unchanged: existing tests pass and `./gradlew :lib-core:checkNoSingletons checkPackageCycles` passes.
- [ ] 4.4 Update `projects/lib-core/README.md` and `docs/ai/architecture.md` for the new collaborators; verify with `scripts/check-docs.sh`.

## 5. Tree refresh coordinator (R8)

- [ ] 5.1 Add a pure dirty-set/generation coalescing policy in `lib-core-editor` (no Swing/platform types) that schedules one pending update and a follow-up when events arrive during a rebuild; verify with unit tests for burst coalescing, event-during-rebuild, and disposal canceling pending work.
- [ ] 5.2 Add a pure selection/expansion capture-and-restore model keyed by file identity plus entity ID/property path, never label text; verify with unit tests for surviving entries, removed selection cleared, and no duplicate nodes.
- [ ] 5.3 Add the plugin coordinator (pane lifetime) consuming `AbyssusSceneEdited`, document changes and project-scoped VFS create/delete/move/rename/content events filtered to native document and metadata paths; capture identities on the EDT, build under read access, update UI and restore selection on the EDT; verify with `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.AbyssusProjectViewPaneTest'` covering unrelated-file events not rebuilding.
- [ ] 5.4 Platform tests on a copy of Untitled for the spec scenarios: rename Undo/Redo follows labels with no extra undo entry; unsaved `fogEnabled` edit and its undo; external replacement; file/folder add/remove/rename and metadata changes (counts, unused markings); malformed or unsupported-version replacement keeps the unreadable presentation and is not rewritten; verify each with a named test in `AbyssusProjectViewPaneTest`.
- [ ] 5.5 Test navigation preservation: selected+expanded entity `0` survives an unrelated edit; a removed selected entity disappears with no stale details; closing the project with a pending refresh does not reopen the view; verify with named tests in `AbyssusProjectViewPaneTest`.
- [ ] 5.6 Verify in `runIde` on a copy of Untitled (not the committed fixture): (a) rename, Undo, Redo, (b) unsaved fog edit and undo, (c) external edit of a `.scene`, (d) add/delete an asset folder; the tree updates each time without reopening and keeps selection/expansion. (Manual: leave unchecked until observed.)
- [ ] 5.7 Remove the documented tree-refresh limitation and describe the coordinator in `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/projectView/README.md` and `docs/ai/architecture.md`; verify with `scripts/check-docs.sh`.

## 6. `lib-render` and host adapters (R6) — gated on R2/R5 and group 2 baseline

- [ ] 6.1 Gate check: confirm `separate-shipped-code` and `shrink-lib-gdx-to-stock-g3d` acceptance evidence (not file existence) and that a group 2 baseline exists; verify by recording the evidence links in this change's design notes before any group 6 code lands.
- [ ] 6.2 Add the plain JVM `lib-render` module (depends on `lib-core`, `lib-gdx`; wired in `settings.gradle.kts`) with the existing singleton and package-cycle checks and a classpath/import boundary test forbidding IntelliJ, Swing, AWT, editor, physics and ray-backend types; verify with `./gradlew :lib-render:check checkPackageCycles`.
- [ ] 6.3 Implement GL-free host-neutral frame inputs, light selection, projection and binding-plan calculations; verify with headless unit tests covering the editor multi-light and game single-sun policies as named profiles.
- [ ] 6.4 Implement shared terrain shader binding/drawing and shadow resource/depth submission primitives with explicit shader variants for differing lighting/filtering; passes borrow resources and dispose only what they create; verify with `./gradlew :lib-render:test -Dabyssus.glTests=true` incl. matched-policy parity, and a test that borrowed resources are not disposed.
- [ ] 6.5 Switch the editor (`SceneRenderer`, adapting `LightSet` at the plugin boundary) to `lib-render`, keeping the old path test-only for comparison; verify old/new comparison within group 2 tolerances and `./gradlew :plugin-abyssus:test -Dabyssus.glTests=true`. (GPU.)
- [ ] 6.6 Switch Control Line (`FieldRenderer`, `FieldShadows`) to `lib-render`, preserving fixed projection/filter, layered clouds, fallback terrain color and fog; verify with `./gradlew :app-game-control-line:test -Dabyssus.glTests=true` and old/new comparison. (GPU.)
- [ ] 6.7 Delete the superseded terrain assembly/binding code and the test-only old paths after both hosts pass; keep editor grid/gizmos/ray presentation and game lines/hidden-pilot handling in hosts; verify with `./gradlew check -Pabyssus.requireShaders=true`.
- [ ] 6.8 Compare group 2 workloads before/after per host with the 10% p95/allocation threshold; verify the report states pass, regression or inconclusive. (GPU/manual.)
- [ ] 6.9 Update `AGENTS.md` layout/hard rules for `lib-render`, add `projects/lib-render/README.md`, and refresh `docs/ai/architecture.md` and `docs/ai/testing.md`; reconcile water's renderer targets; verify with `scripts/check-docs.sh`.

## 7. Renderer resource lifecycle (R7, part 2)

- [ ] 7.1 Extract the remaining editor render-resource lifecycle into a small owner with create, dispose-on-valid-context and abandon-without-GL operations and explicit context generations (no generic container; dispose and abandon stay separate); verify with fake-resource unit tests counting release vs abandon and stale-generation handles never deleted.
- [ ] 7.2 Verify real context replacement and context loss with GL tests (`-Dabyssus.glTests=true`) and re-run group 4 and group 2 gates; leave unchecked until run on a GPU machine.
- [ ] 7.3 Document the lifecycle owner in `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/sceneview/README.md`; verify with `scripts/check-docs.sh`.

## 8. Final verification

- [ ] 8.1 Run `./gradlew check -Pabyssus.requireShaders=true` and `scripts/check-docs.sh`; verify both pass, and list any GPU/manual items still open instead of counting them as passed.
