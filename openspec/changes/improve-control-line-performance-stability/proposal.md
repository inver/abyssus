# Proposal

## Why

The architecture review of Control Line (revision `568a8bd2`) found a sound structure but two gaps that block a release
plan. First, performance is judged by one number: the CPU submission time of `FieldRenderer.draw`. GPU time, physics, the UI
and the hitch when a flight starts or the debrief opens are never measured, and the README cites Linux reports
(`docs/perf/control-line-pbr/`) the repository does not hold. Second, a packaged game that throws on the render thread dies
leaving only stderr, which a player who launched it from Finder or the Start menu never sees. With no evidence and no crash
record, neither speed nor stability can be judged, so this change adds both and then acts on what they show.

## What Changes

- **Budgets and a minimum spec.** The game states the hardware it promises to run on and its approved budgets: p99 frame
  time, the flight-start and debrief-open hitch, and allocation per frame. Until the maintainer approves them they are
  recorded as proposed.
- **Whole-frame evidence.** The game's performance workload measures the whole frame (input, physics, renderer, UI) as well
  as the renderer alone. It adds GPU time per pass from timer queries (sky, shadow, terrain, models, lines) where the context
  supports them, `GLProfiler` counters (draw calls, shader switches, texture bindings), allocation per frame, and the time to
  start a flight and to open the debrief. Reports keep the existing schema and stay comparable only on the recorded machine.
- **A crash record.** A failure on the render thread, or an uncaught exception in any game thread, is written to a crash
  file in the game's user folder with the game version, the OS, the JVM and the GL renderer and version. Then the game exits
  with a non-zero status. At start the game logs the same environment once.
- **Lifecycle hygiene.** One asset-prepare executor that its owner shuts down. The asset storage is disposed by its owner,
  not by the renderer. Disposing never creates a renderer it never drew with. A flight keeps its simulated pose apart from
  the pose it draws.
- **Model culling lands** (the working-tree `ModelCulling`): models and shadow casters outside the camera's or the sun's
  volume are not submitted. Its cache follows asset rebuilds.
- **Allocation and hitch work, driven by the evidence.** Per-scene values the renderer recomputes every frame are cached,
  and per-frame temporaries are reused, until allocation per frame meets its budget. If the flight-start or debrief hitch
  exceeds its budget, a flight reuses what the field already built (model instances, foliage placements) instead of
  rebuilding it.
- **Measured fixes** (`evidence/2026-11-04-measurements.md`, reference machine):
  - Collider shapes are cached across flights, and convex hulls are built from bounded input. A flight start costs
    1.4–1.7 s today, about 1.05 s of it the Stunter's 84,826-point hull.
  - Shadow uniforms are bound without allocating. That's 50% of all allocation today.
  - Shadow casters are drawn in shader order. Today 423 draws make 849 program switches.
  - The atmosphere is cached and redrawn only when the sun changes. The sky takes 11–21 ms of a 17–28 ms GPU frame.
  - The cloud step-down acts on the approved frame budget, not only below 30 fps.
  - The game starts when no monitor is reported.
- **Optional static shadow cache.** Only if GPU timing shows the shadow pass matters on the minimum-spec GPU: static
  casters are drawn into the sun's map only when the light camera changes, and moving casters are drawn each frame.
- **CI coverage.** The game's core GL tests run in CI under Xvfb on Mesa software GL, beside the existing Linux package
  smoke test. macOS and Windows startup stay a documented hand check, because their hosted runners have no OpenGL 3.3.

## Capabilities

### New Capabilities

- `control-line-runtime-stability`: what the running game does when it fails (crash record, exit status, startup
  environment log) and how it owns and releases its threads, asset storage and per-flight state.
- `control-line-performance`: the game's minimum spec and budgets, the evidence the performance workload produces
  (whole-frame, per-pass GPU, counters, allocation, flight-start and debrief-open latency), and the frame-cost behaviours that
  keep it within budget (culling, per-scene caching, reuse across scene loads, the conditional static shadow cache).

### Modified Capabilities

- `control-line-pbr-rendering`: the "Frame time" scenario of "Raster remains the shipped path and boundaries hold" points at
  a budget "recorded in the design" of an archived change. It now points at the budget of `control-line-performance`.
- `game-distribution`: adds that the Linux package smoke test fails when the game writes a crash record, and that the game's
  GL tests run in CI under software GL.

## Impact

- **Code.** `app-game-control-line`: `Main.kt`, `ControlLineGame`, `FlightRunner`, `FlightSession`, `render/FieldRenderer`,
  `render/FieldShadows`, `render/ModelCulling`, `screens/GameUiModel`, and the test-side `render/PerfWorkloadsGlTest`.
  `lib-core`: `BaseCtx` (executor ownership), and `testing.perf` (new report fields, schema stays backward compatible).
  The GPU timer helper lives in the game's test fixtures, or in `lib-render` if both hosts need it. It must hold no editor
  types (`RenderBoundaryTest`, `checkNoEditorCode`).
- **Files.** The crash record is a new plain-text file under `<user-home>/.abyssus-control-line/logs/`. It isn't a native
  document and isn't read back by the game. No `.scene`, `.abss` or asset `meta.json` field is read or written differently,
  and no format version changes.
- **CI.** A new `.github/workflows/gl-tests.yml` runs a GL test job on Ubuntu with Xvfb and Mesa for main and pull requests. `scripts/smoke-control-line-linux.sh`
  also checks for a crash record.
- **Docs.** The game's README ("Rendering" frame-time section, "Packages"), `docs/ai/testing.md` ("Graphics performance
  evidence") and the committed `docs/perf/` baseline for the game.
- **Out of scope:**
  - JVM flag tuning (heap, GC): only after the new evidence shows GC affects the frame-time tail, and then as its own change.
  - Ray mode performance.
  - Shader rewrites and precision changes.
  - Multi-threaded physics: it would end determinism, which the flight specs rely on.
  - Crash upload or telemetry: the record stays on the player's machine.
  - Animation, audio and networking.
