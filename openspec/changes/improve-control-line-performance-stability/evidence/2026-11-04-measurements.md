# Measurements, 2026-11-04

These numbers come from a scratch worktree of `568a8bd2` with the working-tree `ModelCulling` applied. The instrumentation is
`measure-probe.patch`: apply it to that tree. It edits `FieldRenderer.kt` against the culling version, so `ModelCulling.kt`
must already be present. Raw outputs are in `raw/`. None of this is a committed baseline. Use the harness of tasks 5.x for
that.

## Environment

- **Machine.** The reference machine of `docs/ai/testing.md`: Apple M1 Pro, macOS 27.0.1 arm64, `GL_RENDERER` Apple M1 Pro,
  `GL_VERSION` 4.1 Metal - 91.7. On battery or power: not recorded.
- **JVM.** Eclipse Adoptium 25.0.4, with the default max heap of 8192 MB on this machine. CI uses JDK 21.
- **Window.** The game's own window configuration: 1280×720 logical, a **2560×1440 backbuffer** on Retina, 4× MSAA, a 3.3
  core context. For measurement only, vsync was off and the 60 fps cap was kept.
- **Screen.** **The screen was locked for the whole session** (`CGSSessionScreenIsLocked = Yes`, user idle more than
  45 min). Presentation is throttled, so **CPU frame times below are not lane-valid**. They include driver waits.
  - Valid: GPU pass times (from `GL_TIME_ELAPSED`), counters, allocation, and hitches made of CPU work.
- **Kover agent.** It's attached to every `:app-game-control-line:test` JVM. It skews the JVM-side headless timings (M1),
  but not native Jolt time. The game runs (M2–M4) used a separate `JavaExec` task without it.

## M1: headless flight costs (`raw/measure-headless.txt`, `raw/measure-shapes.txt`)

**Scene load and session build:**

| What | Cold | Warm (median of 7) |
|---|---|---|
| Scene load (`RuntimeSceneLoader.load` and `FieldScene`) | 1193 ms, 509 MB allocated | 17–37 ms, 5 MB |
| `FlightSession` build (Jolt world, all bodies) | 2072 ms (includes Jolt load) | **1370–1418 ms** |
| The same, one `PhysicsAssets` kept | — | 1280 ms |
| `PhysicsWorld` alone | — | 1367 ms |
| Session close | 2.8 ms | 0.1 ms |

**Collider shapes, per build:**

| Shape | Input points | Build time |
|---|---|---|
| Stunter convex hull | **84,826** | **1050–1064 ms** |
| MiG-3 convex hull | 21,612 | 170 ms |
| Trainer convex hull | 1,361 | 8 ms |
| Racer convex hull | 288 | 2–5 ms |
| Height field (`terrain_airfield_site`, 255²) | — | 49–64 ms |

**Watch AI F2B flight** (Stunter, 369 s, landed, 22,156 frames at 1/60 s):

| What | p50 | p95 | p99 | max |
|---|---|---|---|---|
| `advance` per frame | 0.147 ms | 0.529 ms | 0.640 ms | 10.7 ms |
| Judge per step, first minute | 0.016 ms | — | — | — |
| Judge per step, last minute | 0.156 ms | — | — | — |
| Bot per step | ~0 ms | — | — | — |
| Allocation per frame | 5.5 KB | — | 7.8 KB | — |

- The judge's per-step cost grows linearly with the flight. Its maximum is 10.5 ms.
- 4 GCs over the whole flight.
- `reportOf` at the end of the flight: 13 ms. `judge.ended`: 0.35 ms.

## M2: the real game, culling on, off, and with counters (`raw/game-cull.txt`, `raw/game-nocull.txt`, `raw/game-counters.txt`)

The script: 10 s in the menu, a 60 s Watch AI F2B flight (Mid), back to the menu, then 5 more starts and returns.

**GPU time per pass, p50:**

| Pass | Menu | Flying |
|---|---|---|
| Whole frame | 27.9 ms | 22.9 ms (p95 27.5) |
| Sky (atmosphere and volumetric clouds) | **21.4 ms** | **17.5 ms** (p95 26.3) |
| Models (with foliage and cable) | 4.3 ms | 2.9 ms |
| Shadow map | 0.95 ms | 0.83 ms |
| Terrain | 0.98 ms | 0.79 ms |
| UI | 0.50 ms | 0.44 ms |

The sky-light span reads 0 and has a negative mean, which is a query artifact. Treat it as unmeasured.

**Culling:**

| Model-pass CPU, menu | Shadow-pass CPU, menu | Allocation per frame, menu | Allocation per frame, flying |
|---|---|---|---|
| Off: 9.8 ms | Off: 4.1 ms | Off: 2.39 MB | Off: 2.46 MB |
| On: 5.8 ms | On: 3.1 ms | On: 0.65 MB | On: 0.98 MB |

Culling doesn't measurably change GPU time.

**Counters per frame** (`GLProfiler`, a separate run):

| Pass | Draw calls | Shader switches | Texture binds | Vertices |
|---|---|---|---|---|
| Shadow | 423 | **849** | 86 | 2.05 M |
| Models | 234–312 | 124–168 | 112 | 1.42–1.52 M |
| Terrain | 2 | 1 | 15 | 627 k |
| UI | 15–22 | 1 | 15–22 | <1 k |

- The light frustum culls no caster: 423 every frame.
- With the profiler's overhead, the game fell under 30 fps and the cloud step-down went volumetric → shells → cumulus.

**Hitches** (CPU, one frame, valid despite the lock):

| Event | Frame time | Allocated | Notes |
|---|---|---|---|
| First flight start | **1683–1743 ms** | 141 MB | 1593–1651 ms in `FlightRunner.start` |
| Flight starts 2–6 | **1448–1469 ms** | 125 MB each | 1389–1404 ms in `FlightRunner.start` |
| Flight scene's first frame | — | — | +33–37 ms in the shadow span: model instances and foliage placements rebuilt |
| Return to menu | 61–89 ms | 64 MB | 31–45 ms of it the renderer's scene switch |

**GC.** 32–36 collections and 30–37 ms in total over the 60 s flight (with an 8 GB max heap). That's about 1 ms per pause,
not a tail factor on this machine.

## M3: cheaper clouds (`raw/game-CUMULUS.txt`, `raw/game-LAYERED.txt`)

| Clouds | GPU frame p50, menu | Sky p50, menu | GPU frame p50, flying |
|---|---|---|---|
| Volumetric | 27.9 ms | 21.4 ms | 22.9 ms |
| Cumulus | 23.1 ms | 16.6 ms | 23.0 ms |
| Layered | 16.7 ms | 11.0 ms | 16.1 ms |

Even with layered clouds, the sky takes about 11 ms. The atmosphere shader (`skybox_physical/sky.frag`) integrates 16
primary × 8 light steps per pixel, for 3.7 M pixels per frame, under a sun that doesn't move.

## M4: allocation and CPU sites (JFR `settings=profile`, culling on)

- **Allocation pressure:**
  - `ShadowAtlasBindings.bind`: **50.3%**. It runs per model renderable and allocates new matrix and tile arrays, a map,
    and string-built uniform names each time.
  - `FoliageDrawable.instancedPart`: 8.5% (scene switches).
  - Map and string building: about 10%.
- **CPU hot methods, all threads:**
  - `Plane.testPoint`: 13.3%, from frustum tests (foliage chunk culling, `visibleFoliageChunks` 3.5%).
  - `Matrix4.mul`: 8.6%.
  - `ModelInstance.getRenderables`: 7.8%.
  - `HashMap.getNode`: 4.5%.
  - `DefaultRenderableSorter`: 1.9%.

## M5: start without a reported monitor

With the screen locked, `Lwjgl3Application` threw a `NullPointerException` in `glfwGetMonitorPos`. libGDX centres a window
that has no position on the primary monitor, and GLFW reported none. The game's `Main` configuration sets no window
position, so it takes the same path. A fullscreen start calls `getDisplayMode()`, which also needs a monitor.

## Not measured

- A minimum-spec GPU.
- Windows and Linux.
- Debrief-open time. The debrief needs a finished flight, and it takes the same scene-load and renderer scene-switch path
  as a return to the menu, so expect about 20 ms plus 60–90 ms.
- Lane-valid CPU frame times (they need an unlocked, visible screen).
