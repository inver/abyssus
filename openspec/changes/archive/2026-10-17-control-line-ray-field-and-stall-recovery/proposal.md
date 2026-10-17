# Proposal

## Why

Today's audit of the Vulkan ray backend against the Metal backend, the conformance kit and the READMEs found two
robustness gaps in `lib-raytracing` and one product gap in Control Line.

- A ray frame can stall silently and wedge the view forever. `RayRenderScheduler.pump()` treats "not complete yet"
  (`poll()` returns null) as "try again next frame" without any bound, so one frame that never completes stops every
  later submission with no diagnostic and no recovery. The lavapipe run of the conformance suite hit exactly this on
  the closed-glass case: one of three full runs failed with `Backend frame completion timed out`
  (`RayBackendConformanceKit.kt:983`) after 15.38 s against the case's 5 s deadline, and the reruns passed, so the
  stall is real but intermittent and currently invisible to users until the view freezes.
- `VulkanDevice.loadVulkanLibrary()`'s guard `if (VK.getFunctionProvider() == null) VK.create()` is dead code with
  LWJGL 3.4.3: the `VK` class auto-creates Vulkan at class load unless `org.lwjgl.vulkan.explicitInit` is set, and
  with explicit init the getter throws `IllegalStateException` instead of returning null. Hosts that run with
  explicit init (a supported LWJGL configuration) can never get the Vulkan backend, and the failure is not reported
  as the existing "runtime not found" probe result.
- Control Line proves the editor-for-games chain, but it renders only through the raster `FieldRenderer`. There is no
  way to run the game on the same ray tracer the editor uses, so the backend is never exercised by a shipped-game
  host and the ray view's real limits (foliage, animation poses, device support) are not demonstrated end to end.

## What Changes

Part 1 — bound frame completion and repair Vulkan loading (`lib-raytracing`):

- A shared frame-completion deadline: an in-flight frame that does not complete within the deadline (default 10 s,
  matching the existing fence wait) is reported once as a stall with a reason code, the in-flight frame is abandoned,
  and the session follows the same fallback path as a lost device: raster resumes, editing state is preserved, and a
  later retry re-probes the backend. The bound applies to every session (Vulkan, Metal, fake) through the shared
  scheduler; no backend-specific timer.
- The fake-backend test suite gains a stall-injection case (a backend that never completes one dispatch) covering the
  recovery transitions; the conformance kit's cases stay as they are, since they require a backend that completes
  frames.
- `loadVulkanLibrary()` works whether or not LWJGL automatic initialization is enabled, and reports
  `RuntimeNotFound` when the loader is absent in both modes.
- A forked-JVM test runs the loader path with `org.lwjgl.vulkan.explicitInit=true`, following the one-JVM-per-class
  pattern of `VulkanRuntimeMissingTest`.
- `lib-raytracing/README.md` is corrected: Vulkan runtime cases now run on this development host through lavapipe;
  the 3-run evidence (one glass stall, two clean) is recorded together with the missing validation-layer
  limitation.

Part 2 — an opt-in ray-traced field view for Control Line (`app-game-control-line`):

- A new optional `ray` source set (mirroring the existing `play` source set) with a `runRay` Gradle task and
  `rayImplementation(project(":lib-raytracing"))`, activated by `-DcontrolLine.fieldView=ray`. Raster remains the
  default and the only shipped path; `checkShippedClasspath` continues to pass unchanged.
- Ray mode displays the field through the game's own presenter (GL 3.2 core composite of the ray frame) and a
  game-local CPU scene snapshot built with the editor's tested snapshot loaders and an extracted, scene-independent
  asset conversion: terrain, the field scene's models (moving planes follow their simulation transforms), sunlight,
  sky and fog, with the game's own camera and input. HUD, menus and screens stay raster.
- Startup fallback is explicit: when no backend is usable (missing loader/crate, unsupported device, probe refusal)
  the game runs raster and states the reason in the window title and the log; it never crashes and never switches
  silently.
- Documented limitations of ray mode: foliage is omitted, animation poses render in bind pose, and the mode is a
  development aid — not a distribution feature.
- Out of scope: shipping ray tracing or `lib-raytracing` artifacts in any distribution, an in-game graphics-menu
  toggle, ray-traced HUD/menus, foliage and animation support in ray mode, and any change to `.scene` files.

No native document fields are read or written differently by this change: it edits no `.scene`, `.abss` or
`meta.json` content and introduces no format or version change.

## Capabilities

### New Capabilities

- `scene-raytracing`: complements the open `add-scene-raytracing` change instead of redeclaring its platform
  requirements; this change adds bounded frame completion with stall recovery, and Vulkan runtime loading that does
  not depend on LWJGL automatic initialization. (The open change introduces the capability itself; the deltas here
  merge additively when either change archives first.)

### Modified Capabilities

- `control-line-field-environment`: adds the opt-in ray-traced field view — activation, what it renders, the
  explicit raster fallback with a visible reason, and the documented omissions — without changing the raster field
  default.
- `game-distribution`: extends the shipped-vs-development boundary so the opt-in ray source set and its
  `lib-raytracing` dependency stay out of every distribution while `checkShippedClasspath` keeps guarding the main
  classpath.

## Impact

- `lib-raytracing`: `RayRenderScheduler` / session lifecycle (stall bound, reason, recovery), `VulkanDevice`
  loading, `RayBackendConformanceKit` and fake-backend tests, a new forked-JVM loader test, `README.md`.
- `app-game-control-line`: new `src/ray` source set (`RayFieldPresenter`, `RayFieldSceneBuilder`, ray `main`),
  `runRay` task, README; no changes to the shipped classpath, the project fixture or game logic.
- Plugin (`plugin-abyssus`): none; the stall recovery surfaces through the scene view's existing rendering-failure
  UX (raster resume with a reason), which the open `add-scene-raytracing` change defines.
- No file-format or dependency changes for shipped code; docs updated in the same change per `AGENTS.md`.
