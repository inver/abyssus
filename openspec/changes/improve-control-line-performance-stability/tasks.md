# Tasks

## 1. Budgets and minimum spec

- [x] 1.1 Add a "Performance" section to `projects/app-game-control-line/README.md` with the reference machine, the proposed minimum spec and the proposed budgets of design D5, each marked `proposed`. Remove the citation of the missing `docs/perf/control-line-pbr/*-linux.json` (or commit those reports if they are found). Verify: `scripts/check-docs.sh` passes and the README names no file absent from the repo.
- [x] 1.2 Point the PBR "Frame time" scenario's budget at the README section (delta `control-line-pbr-rendering`). Verify: `openspec validate improve-control-line-performance-stability --strict` passes.
- [x] 1.3 Fix the main spec `openspec/specs/control-line-pbr-rendering/spec.md`: its requirements sit under a delta header `## ADDED Requirements` instead of `## Requirements`, so archive refuses any delta to it. Verify: `openspec validate improve-control-line-performance-stability --strict` prints no "Archive would refuse" notice.

## 2. Crash record and startup environment

- [x] 2.1 Add `CrashReporter` (D1): format, atomic write to `<home>/.abyssus-control-line/logs/crash-<timestamp>.txt`, prune to ten, stderr fallback. Verify: new `CrashReporterTest` covers the content fields, pruning at eleven and an unwritable folder.
- [x] 2.2 Install it as the default uncaught-exception handler in `Main`, guard the frame body of `ControlLineGame.render`, capture the GL strings in `create()`, and exit with status 1 after a reported crash. Verify: a `ControlLineGameCrashTest` (headless, with a throwing injected `FieldView`) writes a record and sets the exit status. Also a hand check: `-DcontrolLine.crashTest=true` (a test-only switch read by the injected view) creates a record from `./gradlew :app-game-control-line:run`.
- [x] 2.3 Log the environment line of the "environment is logged at start" requirement once in `create()`. Verify: a `ControlLineGameStartTest` assertion on the captured log (`warningsTo`-style capture).
- [x] 2.4 Extend `scripts/smoke-control-line-linux.sh` to point the game's home at a temporary folder, fail when `logs/crash-*.txt` exists, and print it. Verify: run it locally in the amd64 Linux container against a `.deb` built with a forced crash, and confirm it fails and prints the record.
- [ ] 2.5 Start without a reported monitor: give the window a fixed position when GLFW reports no primary monitor, and fall back from a saved fullscreen choice to windowed for that start with a log line (evidence: `evidence/2026-11-04-measurements.md`, M5). Verify: a headless `MainConfigTest` builds the configuration with no monitor and gets a positioned windowed mode; hand check: start `./gradlew :app-game-control-line:run` with the screen locked over SSH and confirm the window opens.
- [x] 2.6 Document the crash folder in the game README ("Run") and in the "Packages" hand check for macOS and Windows. Verify: `scripts/check-docs.sh`.

## 3. Lifecycle hygiene

- [x] 3.1 Add `BaseCtx.close()` (shut down the executor, dispose the asset storage). Remove `ControlLineGame`'s unused executor, stop `FieldRenderer.dispose` disposing the borrowed storage, and fix the raster dispose condition (D2). Check every `BaseCtx(` caller with `rg 'BaseCtx\('`. Verify: `:lib-core:test` gains a `BaseCtxCloseTest` (no `asset-prepare` thread alive after close). A headless `ControlLineGameDisposeTest` shows dispose with an uninitialized raster renderer builds none.
- [x] 3.2 Split the simulated and drawn poses in `FlightSession` (D3). The renderer, camera and line ends read the drawn transform; everything else reads `PositionComponent`. Verify: new `FrameRateIndependenceTest` flies the same Watch AI routine with 60 Hz and 144 Hz frame splits and asserts equal recordings and score. Existing `FlightTest`, `AiFlightLifecycleTest` and `GameFlowTest` pass.
- [ ] 3.3 Hand check: `./gradlew :app-game-control-line:run`, fly Free flight and an F2B Watch AI. The plane and camera move without jitter, and quitting exits with status 0 and no logged exception.

## 4. Model culling

- [x] 4.1 Land the working-tree `ModelCulling`. Drop cache entries when the scene changes or a model's storage build count changes (D6). Verify: `ModelCullingTest` gains a rebuilt-model case; `./gradlew :app-game-control-line:test --tests '*render.ModelCullingTest'`.
- [ ] 4.2 Check that culling leaves the image unchanged. Verify: `PixelEvidenceGlTest` with `-Dabyssus.glTests=true -Dabyssus.perf.pixels=compare` against a recording made before 4.1 passes on the reference machine.

## 5. Measurement harness

- [x] 5.1 Add the optional report fields of D4 to `lib-core` `testing.perf` (`spans`, per-pass `gpu`, `counters`, `hitches`), keeping old reports readable. Verify: `:lib-core:test --tests '*testing.perf*'` with a test reading the committed `docs/perf/baseline/game.json`, and `comparePerf` of old against new.
- [x] 5.2 Add the `FrameProbe` seam to `ControlLineGame` and `FieldRenderer` (no-op by default), plus the test seams for an injected clock and start screen. Verify: existing GL and headless tests pass. `checkShippedClasspath` and `checkNoEditorCode` pass.
- [ ] 5.3 Add the timer-query probe (ring of 3 `GL_TIME_ELAPSED` queries per pass, non-blocking read) and the `GLProfiler` counter run in the game's test sources. Verify: a GL test asserts the sky pass reports a positive GPU time on the reference machine, or the report names why it is unavailable.
- [x] 5.4 Rework `PerfWorkloadsGlTest` into the whole-frame workload: a Watch AI F2B flight on a copy of the bundled project; spans, GPU passes, counters (separate run), allocation per whole frame, and 5 flight-start and debrief-open hitches. Verify: a smoke run (`-Dabyssus.perf.warmupSeconds=2 -Dabyssus.perf.sampleSeconds=3 -Dabyssus.perf.samples=2`) writes a `MEASURED` report holding every new field.
- [ ] 5.5 Record a clean-tree baseline on the reference machine with JDK 21 (the CI JDK). Keep the old one as `docs/perf/baseline/game-2026-pre.json`. Update `docs/ai/testing.md` "Graphics performance evidence" with the new fields and commands. Verify: the report's `dirty` is false, and `scripts/check-docs.sh` passes.
- [ ] 5.6 With the baseline, ask the maintainer to approve or replace the budgets in the README, and mark them `approved`. Verify: the README table shows the approval date.

## 6. Allocation and CPU per frame (measured 0.65–0.98 MB per frame with culling, 2.4 MB without: allocation work is conditional on the approved budget; 6.4 is not conditional)

- [x] 6.1 Record a JFR allocation profile of the workload (`-XX:StartFlightRecording=settings=profile`) and list the top ten allocation sites in the change folder (`evidence/alloc-before.md`). Verify: the file exists, with sites and bytes.
- [ ] 6.2 Cache the per-scene values in `FieldScene` and reuse the per-frame lists, cameras and line or cable objects (D7), site by site in profile order. Verify: after each step the `PixelEvidenceGlTest` compare passes and the workload's allocation per frame drops. Finally the "allocation per frame stays within budget" check in `PerfWorkloadsGlTest` passes.
- [x] 6.6 Bind shadow uniforms without per-renderable allocation (D14; measured as 50% of allocation). Verify: a `lib-gdx` test binds the same atlas twice and asserts no upload the second time; the workload's model-span allocation falls by at least 700 KB per frame from `evidence/2026-11-04-measurements.md` M2; the pixel evidence compare passes in the game and the editor (`:plugin-abyssus:test --tests '*perf.PixelEvidenceGlTest'`).
- [x] 6.4 Draw shadow casters grouped by depth shader: sort the collected renderables by shader and begin and end each shader once per group in `lib-render`'s `DepthSubmission` (D12; measured 849 program switches for 423 draws). Verify: `lib-render` `DepthSubmissionTest` counts one begin per shader group; the workload's shadow-pass shader switches fall to at most the depth shader count plus one; `FieldShadowsGlTest` and the pixel evidence compare pass.
- [ ] 6.5 Optional, only if a minimum-spec report shows the flight span over its share: make the pattern judge's per-step scans incremental (`PathTokens`, `PatternJudge.burst` and the take-off scan grow linearly with the flight: measured 0.016 ms per step in the first minute and 0.156 ms in the last). Verify: `PatternJudgeTest`, `GraderTest` and `F2bRoutinePlannerTest` pass and the measured per-step time stays flat.
- [x] 6.3 Make the workload fail a run over the allocation budget. Verify: setting the budget to 1 byte in a local run makes it fail, naming the measured value.

## 7. Flight-start and debrief hitch (measured 1.4–1.7 s per start on the reference machine: not conditional)

- [x] 7.0 Keep one `PhysicsAssets` for the game run and cache built Jolt shapes by (asset, shape kind, scale) across flights; bound convex-hull input to the points of a first hull (built once per asset, or reduced to at most 1024 points) (D11). Verify: a `lib-physics` `ShapeCacheTest` builds a world twice and asserts the second build creates no hull; `FlightTest`, `AiFlightLifecycleTest` and the crash and landing scenarios of `control-line-flight` pass; the headless start measurement (`evidence/…`, M1) falls from about 1.38 s to under 100 ms after the first flight.
- [x] 7.1 Key `FieldRenderer`'s instances and foliage placements by entity id and storage key, so a reloaded scene reuses them (D8 option 1). Verify: a GL test flies twice and asserts no frame between the flights draws zero models, and the workload's flight-start maximum drops.
- [x] 7.2 Have the debrief reuse the last flight's scene when it is still held. Verify: `GameFlowTest` and `DebriefModelTest` pass, and the debrief-open hitch drops.
- [ ] 7.3 Only if the budget is still missed: parse the next scene ahead on the asset executor (D8 option 2), then, if needed, reuse the Jolt world (option 3) with `FrameRateIndependenceTest` and `FlightTest` as determinism guards. Verify: the workload's flight-start maximum is within budget.

## 8. Sky and shadow GPU cost

- [ ] 8.0 Make the cloud step-down act on the approved frame budget (D13; measured: the sky takes 17–21 ms of a 23–28 ms GPU frame on the reference machine with volumetric clouds, and the game stays above the current 30 fps trigger). Verify: `CloudStepDownTest` gains a case at the approved budget; a workload run with volumetric clouds steps down and logs it.

- [ ] 8.3 Cache the atmosphere in a cube map rebuilt only when the sun or the atmosphere settings change, for skies whose shader opts in, and opt the bundled `skybox_physical` in (D15). Verify: a `lib-core` GL test compares the cached and direct sky against a pixel tolerance; the workload's sky GPU time with layered clouds falls well below the 11 ms of M3; `FieldSkyGlTest` passes.

### 8.1 Static shadow cache (only if a minimum-spec report shows the shadow pass over its share; on the reference machine it is about 1 ms of GPU)

- [ ] 8.1 Classify casters as static or dynamic and add the static map and copy pass (D9). Verify: `FieldShadowsGlTest` gains a cached-versus-uncached pixel comparison with the plane in a building's shadow. The workload's shadow GPU time on minimum spec is within its share.

## 9. CI

- [x] 9.1 Add `abyssus.glTests.require` to `TestGl` (fail instead of skip without a context). Verify: a `lib-gdx` test sets it without a display and expects the failure.
- [ ] 9.2 Add the `gl-tests` job, in `.github/workflows/gl-tests.yml` (D10: `build.yml` runs only for plugin tags). Verify: the job is green on a pull request, and a deliberate throw in `FieldRenderer.draw` on a scratch branch makes it fail.
- [x] 9.3 Update AGENTS.md's command table and `docs/ai/testing.md` with the CI GL job. Verify: `scripts/check-docs.sh`.

## 10. Integration

- [x] 10.1 Run `./gradlew check` and `scripts/check-docs.sh`, and `openspec validate improve-control-line-performance-stability --strict`. Verify: all three pass.

## Workflow follow-up

- Archive the change after the budgets are approved and every conditional group is either done or recorded as not needed with its report.
