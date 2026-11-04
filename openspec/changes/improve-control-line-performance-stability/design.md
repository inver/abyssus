# Design

## Context

See proposal.md for the motivation. The current state is from the review at `568a8bd2`:

- **Frame order.** `ControlLineGame.render` runs everything on the LWJGL main thread, in this order: controllers →
  `FlightRunner.follow` → `FlightSession.advance` (fixed `PHYSICS_STEP`, at most 0.1 s of catch-up, interpolated pose) →
  camera → `FieldRenderer.draw` → `GameUiHost.update`/`draw`. Only `AssetStorage` prepares run off that thread, on
  `BaseCtx.executor`.
- **Executors.** `ControlLineGame` makes a second, unused 2-thread executor and shuts that one down; `BaseCtx.executor` is
  never shut down.
- **Asset storage ownership.** `FieldRenderer.dispose` disposes `ctx.assetStorage`, which `BaseCtx` owns.
- **Scene loads.** Each flight (`FlightRunner.start`) and each debrief (`debriefSceneOf`) loads the field scene again and
  builds a new Jolt world. When the scene changes, `FieldRenderer.draw` drops every `ModelInstance` and disposes the foliage
  placements.
- **Per-frame allocation.** `FieldRenderer.draw` rebuilds per-scene sets and lists every frame: wanted names, storage keys,
  drawn pairs, terrains, foliage map, casters, line partitions, `FrameInputs`, a new shadow camera. The baseline is
  2.12 MB per frame (`docs/perf/baseline/game.json`, M1 Pro, dirty tree, JDK 25).
- **Shadows.** The 2048² sun map (RGBA8888 packed depth plus a depth attachment) is cleared and redrawn every frame.
- **Harness.** `PerfWorkloadsGlTest` times only `FieldRenderer.draw` on the CPU. The report schema has a `gpuMs` field that
  is always null.
- **CI.** CI runs `check` without GL tests. `scripts/smoke-control-line-linux.sh` already runs the game under Xvfb with Mesa
  `LIBGL_ALWAYS_SOFTWARE`.

## Goals / Non-Goals

**Goals:**
- Evidence first. Every optimization task (allocation, hitch, shadow cache) is gated on a measured report showing the
  budget is missed, and closes with a report showing it is met.
- Keep the existing report schema backward compatible, so `:lib-core:comparePerf` still compares old baselines.

**Non-Goals:**
- A frame graph or a render-pass abstraction.
- Changing `lib-render`'s public passes beyond an optional timing hook.
- Any change to physics determinism or to the fixed step.

## Decisions

### D1. Crash handling lives in the game, as one small guard class without GL

A `CrashReporter` in the game's `app` package formats the record from plain values (version, OS, JVM, GL strings,
throwable), writes it atomically (temp file, then move), and prunes to ten. It's unit-tested without GL.
- `Main` installs it as the default uncaught-exception handler before `Lwjgl3Application` starts.
- `ControlLineGame.render` catches `Throwable` around the frame body, hands it to the reporter, and calls `Gdx.app.exit()`.
  `Main` then exits with status 1. The status is held in a flag the reporter sets, checked after `Lwjgl3Application`
  returns.
- GL strings are captured once in `create()` and kept as values, so the reporter never touches GL. Catching in `render`
  works because LWJGL3's loop rethrows. Wrapping `Lwjgl3Application` alone would lose the GL strings and would not run on
  the asset threads.
- Use `runCatchingKeepingCancellation` only where cancellation applies. It doesn't here (plain JVM threads), but
  `checkNoRunCatching` must stay green, so the guard uses `try`/`catch`.
- Alternative considered: a logging framework file appender. Rejected: the game binds slf4j-simple, and a crash must still
  be recorded if logging setup failed.

### D2. Ownership

- `BaseCtx` keeps its executor but gains `close()`, which shuts it down and disposes the asset storage. `lib-core` stays free
  of singletons; this is an instance method.
- `ControlLineGame` drops its own executor and calls `ctx.close()` last in `dispose()`.
- `FieldRenderer.dispose` stops disposing the storage it borrows (lib-render's rule: "dispose only what they create" is
  applied to the game too).
- The dispose condition becomes "dispose the raster renderer only if it was initialized".
- Plugin callers of `BaseCtx` are checked: the plugin wires its own `AssetLoading`, so it is not affected. The task greps to
  confirm.

### D3. Simulated versus drawn pose

`FlightSession` keeps the interpolated pose in its own `drawnPosition`/`drawnRotation`, and stops writing it into the plane's
`PositionComponent`.
- The renderer, camera and line ends ask the session for the plane's drawn transform. `FieldScene.position(entity)` stays
  the simulated pose for everyone else.
- Today the game restores the pose at the start of each `advance`. That works only because of call order, and any new
  reader between frames would break it.
- The test is the spec's "same flight at two frame rates" scenario, run headless with the existing `AiFlight` test helper:
  the same steps, with 60 Hz and 144 Hz frame splits.

### D4. Measurement: a game-level frame timer, timer queries, profiler counters

- **Whole frame.** The workload drives the real `ControlLineGame` frame body in `TestGl`, with a Watch AI F2B flight on
  the bundled field (a copy of the project). That needs `ControlLineGame` to accept an injected clock and an injected start
  screen (a test seam). It does not build a parallel loop in the test.
- **Spans.** `frame`, `flight`, `field` and `ui` are taken with `System.nanoTime` through a `FrameProbe` interface that the
  game calls. The game's default probe does nothing, and the test supplies a recording one, so the shipped build carries no
  timing work.
- **GPU timing.** `GL_TIME_ELAPSED` queries via LWJGL's `GL33`. The context is 3.3 core, where these queries are core.
  - A ring of 3 query objects per pass is read back with `GL_QUERY_RESULT_AVAILABLE`, never blocking.
  - Passes: sky, shadow, terrain, models (`ModelBatch`, foliage and cable), lines.
  - The probe wraps the pass calls in `FieldRenderer` through the same `FrameProbe` (`begin(pass)`/`end(pass)`).
  - The query helper lives in the game's test fixtures, because only the workload uses it. It moves to `lib-render` only if
    the editor needs it later.
  - Apple's Metal-backed GL 4.1 supports `GL_TIME_ELAPSED`. If `glGenQueries` fails or results never become available within
    10 frames, the GPU fields are marked unavailable with that reason.
- **Counters.** libGDX `GLProfiler`, enabled only in the workload. It's reset around the shadow pass and the main pass, so
  each gets its own counters. The profiler wraps GL calls and adds CPU overhead, so the CPU percentiles come from a
  separate run with the profiler off. The report says which run each number came from.
- **Hitch.** Flight start is measured from `flow` entering `Screen.Flying` to the end of the first `render` that draws the
  flight's scene. Debrief open is measured the same way. 5 repetitions after one warm-up flight; median and maximum.
- **Allocation.** `ThreadMXBean.getThreadAllocatedBytes` for the main thread, as today, now around the whole frame and
  per span.
- **Measured constraints** (see `evidence/2026-11-04-measurements.md`):
  - On the reference machine's Metal-backed GL 4.1, `glQueryCounter(GL_TIMESTAMP)` returns 0, so per-pass GPU time uses
    one `GL_TIME_ELAPSED` query per span, issued back to back (never nested).
  - The game's `test` task attaches the Kover agent, which skews JVM timings. The workload runs through its own task
    without the agent, or records the agent and is never compared.
  - A locked screen or a hidden window throttles presentation, so CPU spans then include driver waits. The workload reads
    the session's lock state (`CGSSessionScreenIsLocked` on macOS) and marks CPU percentiles not comparable.
- **Schema.** New fields are optional. `PerfStats` gains a `spans` map, `gpu` gains a per-pass map, and `counters` and
  `hitches` are added. Old reports still parse, and `comparePerf` compares only the fields both sides hold.

### D5. Budgets

- **Reference machine.** The maintainer's M1 Pro (docs/ai/testing.md "Runner and lane").
- **Proposed minimum spec, to be approved.** A GTX 750 Ti class GPU with OpenGL 3.3, 1280×720, on Windows 10 / Ubuntu 22.04.
  That's the same floor the cloud-render skill uses. Until a minimum-spec machine reports, minimum-spec budgets stay
  proposed.
- **Proposed budgets, to be approved or replaced from the first full report:**
  - p99 whole frame ≤ 16.7 ms CPU and ≤ 14 ms GPU on the reference machine
  - flight start and debrief open ≤ 250 ms maximum
  - steady allocation ≤ 256 KB per frame
  - shadow pass ≤ 25% of GPU frame time on minimum spec
- They are written in the README with status "proposed". Task 1 asks the maintainer to approve them before any
  optimization task starts.

### D6. Culling (already in the working tree)

- Keep the sphere-per-`Model` approach. Rejected alternative: per-node boxes. Sphere tests are cheap, and the field's models
  are compact.
- The cache is keyed by the `Model` instance, so a rebuilt asset (a new `Model`) misses the cache, which is correct. Entries
  for dead models are dropped when `FieldRenderer` sees a scene change, or when the storage's built count for a name
  changes.
- Clip-playing models bypass culling, because their animated bounds aren't known.

### D7. Allocation reduction (gated on D4's allocation profile)

- `FieldScene` is immutable per load. Per scene, compute once:
  - the wanted asset names
  - the model storage keys per entity
  - the terrain and foliage entries
  - the line-free environment
- Keep reusable `ArrayList`s for drawn models, casters, and the plain/wire partitions. Iterate instead of using
  `partition`/`groupBy`.
- Reuse one `OrthographicCamera` in `FieldShadows.aim`, updated in place by a `sunShadowCamera` overload that takes the
  target camera. That overload goes in `lib-render`, and the existing function stays.
- Reuse `FrameInputs` parts where `lib-render` allows. If its types are immutable data, accept those few small objects.
- `ControlLineGame`'s lines and cables: `FlightSession.wires` already reuses its points, so build `Cable`/`LineSegment` lists
  once per session and update them in place.
- UI: `GameUiModel.snapshot` reuses its value map, but only if the presenter does not keep the snapshot. The task checks
  `publish`'s contract first.
- Order of the work: JFR allocation profile → the top sites, until the budget is met. Each step is checked against the
  pixel evidence.

### D8. Flight-start hitch (gated on D4's hitch numbers)

Options, cheapest first. Stop at the first that meets the budget:
1. **Keep model instances across scenes by entity id.** `FieldRenderer` keys instances by `Entity`, and a new load makes new
   `Entity` objects. Key by (entity id, storage key) instead, so a reload reuses instances and foliage placements.
2. **Build the next flight's scene ahead.** Parse and bind the scene document on the asset executor (it's CPU-only, with no
   GL and no Ashley engine mutation that another thread sees). Then build the ECS and the Jolt world on the main thread
   when the flight starts. Prefetch when plane select opens.
3. **Reuse one Jolt world** by resetting bodies, which avoids the native world build and the 16 MB temp allocator per
   flight. Highest risk for determinism; only if 1 and 2 are not enough.

Debrief: the debrief needs no physics. It reuses the last flight's scene (its poses are overwritten by the recording) instead
of loading a third copy, unless the flight scene was already released.

### D11. Collider shapes: one asset reader, cached shapes, bounded hulls (measured, not conditional)

- **Measured.** A flight start spends about 1.38 s building its Jolt world, of which 1.05 s is the Stunter's convex hull
  (84,826 points) and 0.17 s the MiG-3's (21,612). Every flight builds all four planes' hulls again. Reusing one
  `PhysicsAssets` saves only about 90 ms.
- **Fix:**
  - The game keeps one `PhysicsAssets` for its run.
  - `lib-physics` gains a constructor-wired `ShapeCache`. It's owned by the game and passed to each `PhysicsWorld`, so
    there's no singleton.
  - The cache holds `ShapeRefC` by (asset, shape kind, scale, offset) and is closed by its owner on exit. Jolt shapes are
    immutable and reference-counted, so worlds can share them.
- **Hull input.** Before the first build, the input points are reduced to the vertices of a quick hull computed once per
  asset. That gives the same hull and drops the interior points. If that is still slow, sample to ≤1024 points (Jolt keeps
  at most 256 hull vertices anyway).
- **Rejected alternative:** baking hull points into the asset `meta.json`. It changes a native document format.
- **Thread.** The main thread, as today. The first flight still pays the build; prefetching it while plane select is shown
  is D8 option 2.

### D12. Shadow casters in shader order (measured, not conditional)

`DepthSubmission` begins and ends a shader for every caster, unsorted: 423 draws make 849 program switches. It sorts casters
by shader (stable, so draw order within a shader is kept) and begins each shader once per run of casters. The depth map
doesn't depend on draw order, because the depth test makes it order-independent. The change is in `lib-render`, and the
editor gains the same.

### D13. Cloud step-down on the approved budget

- **Measured.** Volumetric clouds take 17–21 ms of GPU per frame on the reference machine (the sky pass, at the window's
  backbuffer size). The game holds about 35–40 fps there, above the 30 fps trigger, so the step-down never acts.
- **Fix.** `CloudFrameBudget` takes the approved frame budget (from the README budgets, passed in by `ControlLineGame`)
  instead of the fixed 30 fps.
- **Decision for the maintainer (task 5.6):** whether the default sky of the bundled field should start at a cheaper
  technique on machines below the reference, or keep volumetric and rely on the step-down.

### D14. Shadow uniforms bound without allocating (measured, not conditional)

- **Measured.** `lib-gdx`'s `ShadowAtlasBindings.bind` runs for every model renderable. Each call builds the 16-view matrix,
  tile, position, far and bias arrays, a map and concatenated uniform names again. It is 50% of the game's allocation
  (about 830 KB per frame in the model span).
- **Fix:**
  - The arrays are filled once per atlas revision (the `ShadowAtlasAttribute` instance changes when the light camera
    does: `FieldShadows.aim`).
  - Uniform locations are cached per program.
  - A program that already holds this atlas revision skips the upload.
- **State lives on the shader, not globally**, so `lib-gdx` keeps no singletons. The editor shares the path and gets the
  same benefit.

### D15. Atmosphere cached, sky drawn at a bounded resolution (measured; the approach is decided here, the switch-over in task 8.3)

- **Measured.** With the cheapest clouds, the sky still takes about 11 ms of GPU. The bundled `sky.frag` integrates 16×8
  scattering steps per pixel over the 2560×1440 Retina backbuffer every frame, while the field's sun is fixed.
- **Fix.** `ProceduralSky` renders the atmosphere into a small cube map (for example 6×256², RGBA16F), only when the sun or
  the atmosphere settings change. Each frame it draws the cube map with a cheap lookup. Clouds keep their own targets.
- **Constraint.** The sky shader is project content (`assets/skybox_physical/sky.frag`). The cached path is used only for
  a sky whose shader declares that it's cacheable (a define the loader checks). Any other sky draws as today, so there's
  no file-format change.
- **Rejected alternative:** a global render scale for the whole field. It blurs models and lines. If the frame still
  misses its budget after D13 and D15, a render-scale setting is a separate decision for the maintainer.

### D9. Static shadow cache (conditional, gated on D4's GPU numbers on minimum spec)

- Two maps: a static map, redrawn only when the shadow camera's combined matrix changes; and the shown map, which is the
  static map copied (`glBlitFramebuffer` of the colour attachment is not depth-tested, so it's a full-screen copy pass) plus
  dynamic casters drawn into it each frame with the depth test on.
- Dynamic casters are the flight's plane, the pilot, clip-playing models and foliage that moves.
- The extra map costs 16 MB of VRAM at 2048² RGBA8 plus depth.
- Rejected alternative: a lower map resolution. It changes the picture.

### D10. CI GL job

- A new `gl-tests` job in `build.yml` on `ubuntu-22.04`, which installs `xvfb libgl1-mesa-dri`. It runs `xvfb-run -a`
  `./gradlew :app-game-control-line:test --tests '*render.FieldRendererGlTest' --tests '*render.FieldPipelineGlTest'`
  `--tests '*render.FieldShadowsGlTest' --tests '*render.ModelCullingTest' -Dabyssus.glTests=true -Dabyssus.glTests.require=true`.
- `abyssus.glTests.require` is a new property that makes `TestGl` fail instead of skip when it gets no context.
- Pixel evidence isn't compared in CI: Mesa output differs from the reference machine.

### Threads

All new runtime pieces run on the LWJGL main thread: `CrashReporter` writes on whichever thread failed, `FrameProbe`, the
pose split, culling and caches. The only exception is D8 option 2's scene parse, which runs on the asset executor and hands
DTOs to the main thread through `AssetStorage`-style polling in `update`. Nothing here runs inside the IDE; `GdxRuntime`
does not apply.

### Testable pieces without GL

- `CrashReporter`: formatting, pruning, the fallback to stderr.
- The pose split: `FlightSession` with the existing headless AI flight helper.
- `ModelCulling`: plain math, already tested.
- Hitch and span statistics: `lib-core` `testing.perf`.
- The scene-key caching in `FieldScene`.

## Risks / Trade-offs

- [`GLProfiler` and timer queries distort CPU timing] → CPU percentiles come from a separate profiler-off run, and query
  results are read back without blocking.
- [macOS GL timer queries can report zero or coarse values on Apple silicon] → validate in task 3 against a known-cost pass.
  If they aren't plausible, mark GPU unavailable on that machine and note it.
- [The pose split misses a reader that wanted the drawn pose, making the camera jitter] → the camera and line ends are
  listed explicitly, and the jitter-free flight is checked by eye in a run.
- [D8 option 2 introduces cross-thread scene data] → only immutable DTOs cross. ECS building stays on the main thread.
- [Mesa GL tests are flaky in CI] → run them in their own job, never retried silently. A flake is fixed or the test is
  marked by name, with a reason.
- [Budgets approved before measurements exist] → task 1 only proposes them. Approval happens after the first full report.

## Migration Plan

- Every step ships behind no flag and changes no file format.
- Rollback is a revert per task. The perf report schema change is additive, so old baselines stay readable.
- After measuring, the committed `docs/perf/baseline/game.json` is replaced by a clean-tree report on the reference
  machine. The old one is kept as `game-2026-pre.json` for comparison.

## Open Questions

- Whether a GTX 750 Ti class machine is available to the maintainer for minimum-spec reports. Without one, the minimum-spec
  budgets stay proposed and D9 stays unscheduled.
