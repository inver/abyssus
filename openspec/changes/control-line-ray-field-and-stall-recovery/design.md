# Design

## Context

See `proposal.md` — Why. What shapes the approach:

- `lib-raytracing` is a plain JVM library: `RayBackend` exposes `openSession(...)` → `RaySession.submit`/`poll` (plus the
  scene-material `submit(RaySceneRequest)`), and `RayBackendProbe` already maps expected native failures to
  `RayCapability.Unavailable(...)` reason codes (`RUNTIME_NOT_FOUND`, `INITIALIZATION_FAILED`, ...); UI strings live
  in the plugin.
- Frames are scheduled by `RayRenderScheduler`/`RayQueuedSession`: one in-flight frame, one pending, `poll()` is
  non-blocking, and "not complete yet" is unbounded. `WAIT_TIMEOUT_NS` (10 s) already bounds the blocking fence waits
  in `VulkanRayBackend`; only the polling path lacks a bound. Device loss is a typed `RayDeviceLostException` that the
  session records and rethrows via `checkUsable()`; hosts treat it as "session unusable → dispose → raster with a
  reason", and re-activation re-probes.
- `VulkanDevice.loadVulkanLibrary()` holds `if (VK.getFunctionProvider() == null) VK.create()`. With LWJGL 3.4.3 the
  `VK` class auto-creates at class load unless `org.lwjgl.vulkan.explicitInit` is set; with explicit init the getter
  throws `IllegalStateException`, which the probe maps to `INITIALIZATION_FAILED` — so explicit-init hosts never get
  the backend, and the guard itself never fires.
- Audit evidence: the Vulkan suite's glass case stalled past the conformance kit's own 5 s completion bound once in
  three full lavapipe runs (15.38 s); single-case and later runs passed. `poll()` uses `vkGetFenceStatus` (no wait),
  so the dispatch genuinely stalled on llvmpipe. Glass shading logic in `scene.comp` and `slice.metal` is
  line-for-line equivalent, and payload updates already share `shadingUnchanged` — no parity gap to fix. The
  validation layer is not installed on this host.
- Control Line renders its field through the concrete `FieldRenderer` created by `ControlLineGame`; the `play` source
  set is the precedent for an opt-in, non-distributed source set with its own Gradle task. The editor's CPU scene
  path lives in `lib-core-editor` (`ModelRaySnapshotLoader`, `TerrainRaySnapshotLoader`, `RaySceneSnapshots`,
  `raySceneDiff`); `RayModelSnapshot`/`RayTerrainSnapshot` are plain data, and the model/terrain → `RayMesh`/
  `RayMaterial`/`RayTexture` conversion sits in a private builder inside `RaySceneSnapshots`, coupled to editor scene
  types (`ScenePreview`, `AssetPlacement`, `LightSet`, ...).

## Goals / Non-Goals

**Goals:**

- A stalled frame is bounded, reported, and recovered on every backend, sharing the existing device-loss fallback
  path, with deterministic tests (no GPU, no sleeping).
- Explicit-init hosts load Vulkan normally; missing loaders report the same availability in both init modes.
- An opt-in Control Line ray field view that reuses the editor's proven snapshot and update code without putting any
  of it on the shipped classpath or in any distribution.

**Non-Goals:**

- Diagnosing or fixing the llvmpipe stall itself (the bound makes it visible and survivable; a driver-level cause is
  out of reach here).
- Validation-layer evidence (layer not installed), Windows/macOS device testing, and any change to ray scheduling
  throughput.
- Ray mode as a shipped feature: no distribution, no in-game toggle, no `.scene`/`rayTracing` file reads, no foliage,
  no animation poses, no ray-traced HUD/menus.

## Decisions

### Part 1 — stall recovery

1. **The stall bound lives in the shared scheduler, not in a backend.** `RayRenderScheduler` already sees every
   `poll()` and owns the in-flight frame; a per-backend deadline would diverge between Vulkan and Metal and block the
   render thread if implemented as a waiting poll. The scheduler gets an injected clock and a frame-completion
   deadline (default 10 s, matching `WAIT_TIMEOUT_NS`), configurable by callers; tests inject both, so no test sleeps.
   *Alternatives:* backend-side `vkWaitForFences` timeouts — rejected (blocks the calling thread, Metal has no
   equivalent schedule, duplicates policy); a plugin-side timer — rejected (misses headless and game hosts, cannot
   observe `poll()`).
2. **A stall is a typed failure distinct from device loss.** `RayFrameStallException` joins `RayDeviceLostException`;
   `RayQueuedSession` records either as the session failure, `checkUsable()` rethrows it, and hosts localize the
   reason exactly as today (the library keeps no UI strings). The stalled frame is released, pending work is dropped,
   and no subsequent submission is made on that session.
3. **Recovery reuses the device-loss path.** The host disposes the unusable session and falls back to raster with the
   reason; a later activation re-probes through `RayBackendProvider` and opens a new session. The stalled backend is
   never retried in place. Teardown of a stalled backend uses the existing bounded waits (`WAIT_TIMEOUT_NS`) and is
   best-effort: a teardown failure is logged, the reference dropped, and the UI must not block beyond those waits.
4. **Vulkan loading becomes state-aware instead of a dead null check.** `loadVulkanLibrary()` first asks LWJGL for
   the function provider; when it throws because nothing is loaded (explicit init), the code calls `VK.create()`.
   `UnsatisfiedLinkError` still surfaces to the probe as `RUNTIME_NOT_FOUND`; an initialization `IllegalStateException`
   from `VK.create()` stays `INITIALIZATION_FAILED`. *Alternative:* reading `Configuration.VULKAN_EXPLICIT_INIT` —
   rejected (the property is read by the static initializer before we run; reflecting configuration defaults is
   brittle across LWJGL versions).
5. **Coverage:** scheduler/session unit tests with a fake backend that never completes one dispatch (deadline and
   clock injected), plus conformance-kit cases untouched because they require a completing real backend; a
   forked-JVM test runs the loader path with `org.lwjgl.vulkan.explicitInit=true`, mirroring the
   `verifyVulkanPackaging` one-JVM-per-class pattern of `VulkanRuntimeMissingTest`.

### Part 2 — Control Line ray field view

6. **Opt-in `ray` source set, mirroring `play`.** `rayImplementation` gets `lib-raytracing` and `lib-core-editor`;
   `runRay` is a `JavaExec` that passes `controlLine.project` plus `controlLine.fieldView=ray` (the flag follows the
   `controlLine.project` precedent) and the macOS `-XstartOnFirstThread` argument the `run` task already uses. Raster
   stays the default; `checkShippedClasspath` continues to visit only the main runtime classpath, and the
   `application` distribution and `exportPlay` never see the ray set.
   *Alternatives:* an in-game graphics-menu toggle — rejected (data-driven UI would need new UI assets and model
   changes, and the source-set split is what keeps `lib-raytracing` off the shipped classpath); a standalone sample
   app — rejected (no Control Line field, assets or flight loop to show).
7. **Main code depends on a small interface, the ray set on reflection.** The main source set gains a `FieldView`
   abstraction (draw/resize/dispose) implemented by `FieldRenderer`; `Main` resolves the launch option, reflectively
   loads `...controlline.ray.RayFieldEntry`, and asks it to create a view: `Available(view)` or
   `Unavailable(reason)`. Unavailable (classes absent, probe refused, runtime missing) starts raster and states the
   reason in the window title and log — never a crash, never silent. *Alternative:* `ServiceLoader` — rejected (more
   moving parts for a single implementation; the `play` export already proves explicit class names).
8. **The game reuses the editor's CPU snapshot code instead of re-implementing it.** The ray set parses models and
   terrain with `ModelRaySnapshotLoader`/`TerrainRaySnapshotLoader` and reuses `raySceneDiff`. The asset → ray
   conversion inside `RaySceneSnapshots`' private builder is extracted into a scene-independent public API in
   `lib-core-editor` (snapshot in, `RayMesh`/`RayMaterial`/`RayTexture` + mesh cache out) with no editor scene types,
   used by both the editor and the game. This keeps one tested conversion path; the editor's behavior must not
   change, and its tests plus new extraction tests guard the refactor.
   *Alternatives:* a game-local converter (~200 lines of node visiting, material/channel mapping, splat handling,
   byte limits) — rejected as duplicated logic that will drift; moving snapshots into `lib-raytracing` — rejected
   (bigger, cross-module blast radius than the extraction).
9. **Scene assembly per frame with cached meshes.** The game builds one `RaySceneSnapshot` per change: terrain, static
   models, moving planes and the pilot from their Ashley transforms (poses stay bind pose), sun from the scene light,
   sky/fog from `SceneRenderParams`-equivalent game values, camera from the field camera. Meshes/materials/textures
   are reused through the extracted cache; `raySceneDiff` decides what is resubmitted, so transform-only frames do
   not rebuild geometry (`geometryBuilds` stays flat).
10. **Presentation replicates the plugin technique in the game's own GL 3.2 core context.** The ray frame (RGBA
    floats + GL window depth) is uploaded to float textures and drawn as a fullscreen quad with a `#version 150`
    shader that writes `gl_FragDepth`, into the field viewport; the control lines and other raster overlays continue
    to draw on top, and the HUD keeps its own pass. The presenter is game-local (the plugin's presenter depends on
    its scene view).
11. **Modest ray settings, and runtime failures fall back like startup failures.** The ray set uses bounded defaults
    (target samples 64, ray budget 8388608) within `RayQualityPolicy` limits; a budget refusal, stall, or device loss
    after start disposes the ray view and continues raster with the reason in title/log, identical to the startup
    fallback. Backend choice reuses the same OS-based factory the plugin uses (Vulkan via LWJGL, Metal on macOS);
    macOS verification is manual when a machine is available.

## Risks / Trade-offs

- [A very slow but progressing frame can trip the stall bound] → default 10 s is far above interactive frames; the
  deadline is injectable, and the spec only promises recovery, not killing slow work.
- [Disposing a truly hung GPU op can block or fail] → teardown keeps the existing bounded waits, logs failures, drops
  the backend, never retries it, and the UI path uses the already-defined failure handling.
- [Extracting the conversion from `RaySceneSnapshots` can regress the editor's ray path] → extraction is additive
  (private builder delegates to the new API), lib-core-editor tests and the plugin's ray tests must stay green, and
  the game's snapshot builder gets its own headless test.
- [The game's ray set pulls `lib-core-editor` into a development classpath that some might confuse with shipping] →
  the `game-distribution` spec and `checkShippedClasspath` cover exactly this; tasks verify `installDist` and
  `exportPlay` outputs contain no ray classes.
- [Ray mode is unusably slow on software drivers] → it is a development aid with bounded samples/budget; documented
  expectations (a real GPU) and the reason-reporting fallback keep it honest.
- [Vulkan explicit-init fix cannot be validated with real frames on this host] → the forked-JVM test covers the
  loading state machine; device frames remain covered by the existing lavapipe runs.
- [macOS Metal path and the presenter are untested here] → reuse of the editor-proven backend factory; manual
  `runRay` check is a recorded task on macOS hardware.

## Migration Plan

Part 1 lands first and is invisible until a stall occurs (library-level, no format or API break). Part 2 adds files
only: no scene, asset, or distribution changes; rolling back means not passing `controlLine.fieldView=ray` and
deleting the source set. No data migration exists.

## Open Questions

- Whether ray mode should eventually draw foliage (would need a foliage ray snapshot path in `lib-core-editor`).
- Whether the frame-completion deadline should become user-visible (editor settings); the constructor default is
  sufficient for this change.
