## 1. lib-raytracing stall bound and recovery

- [x] 1.1 Add RayFrameStallException distinct from RayDeviceLostException, with a reason field; verify with a unit test asserting the exception type and reason. (lib-raytracing)

- [ ] 1.2 Inject a clock and frame-completion deadline (default 10 s) into RayRenderScheduler; on poll past the deadline without completion, treat the in-flight frame as stalled, release it, and stop submissions on that session; verify with a fake-backend unit test that never completes one dispatch and injects the deadline/clock. (lib-raytracing)

- [ ] 1.3 Make RayQueuedSession record either a stall or device loss as the session failure and rethrow via checkUsable(); verify with unit tests that checkUsable rethrows both failure types. (lib-raytracing)

- [ ] 1.4 Reuse the device-loss recovery path on stall: dispose the unusable session, fall back to raster with the reason, and re-probe on re-activation; verify with a headless test simulating a stall then a successful re-probe. (lib-raytracing)

- [ ] 1.5 Make VulkanDevice.loadVulkanLibrary() state-aware: query LWJGL's function provider, call VK.create() on explicit-init IllegalStateException, surface UnsatisfiedLinkError as RUNTIME_NOT_FOUND and creation IllegalStateException as INITIALIZATION_FAILED; verify with unit tests in both init modes. (lib-raytracing)

- [ ] 1.6 Add a forked-JVM test running the loader path with org.lwjgl.vulkan.explicitInit=true, mirroring VulkanRuntimeMissingTest's one-JVM-per-class pattern; verify the test passes. (lib-raytracing)

- [ ] 1.7 Add a fake-backend stall-injection case to the conformance kit covering recovery transitions (stall → raster fallback → re-probe → resume); verify the case passes. (lib-raytracing)

- [ ] 1.8 Update lib-raytracing/README.md: record Vulkan runtime cases running through lavapipe on this host, the 3-run evidence (one glass stall, two clean), and the missing validation-layer limitation; verify the file is updated. (lib-raytracing)

## 2. lib-core-editor snapshot conversion extraction

- [ ] 2.1 Extract the asset→ray conversion (mesh/material/texture building) from RaySceneSnapshots' private builder into a scene-independent public API taking a RaySceneSnapshot and returning RayMesh/RayMaterial/RayTexture plus a mesh cache, with no editor scene types; verify the editor's existing ray tests still pass unchanged. (lib-core-editor)

- [ ] 2.2 Add headless tests for the extracted conversion covering terrain, models, sunlight, sky and fog; verify the tests pass. (lib-core-editor)

## 3. app-game-control-line ray source set and entry point

- [ ] 3.1 Add a `ray` source set mirroring `play`, with `rayImplementation(project(":lib-raytracing"))` and `rayImplementation(project(":lib-core-editor"))`, plus a `runRay` JavaExec task passing controlLine.project, controlLine.fieldView=ray and macOS -XstartOnFirstThread; verify `./gradlew :app-game-control-line:runRay` compiles the ray set. (app-game-control-line)

- [ ] 3.2 Add a `FieldView` interface (draw/resize/dispose) to the main source set, implemented by FieldRenderer; verify the interface compiles and FieldRenderer implements it. (app-game-control-line)

- [ ] 3.3 Make Main resolve the launch option, reflectively load ...controlline.ray.RayFieldEntry, and create a view via Available(view) or Unavailable(reason); on Unavailable start raster and state the reason in window title and log; verify with a unit test covering both branches. (app-game-control-line)

- [ ] 3.4 Add RayFieldEntry, RayFieldPresenter and RayFieldSceneBuilder in the ray source set; the presenter draws the ray frame as a fullscreen quad in the game's GL 3.2 core context with float textures and gl_FragDepth, reusing the extracted snapshot API and raySceneDiff for per-frame assembly; verify the ray set compiles. (app-game-control-line)

- [ ] 3.5 Build one RaySceneSnapshot per change (terrain, static models, moving planes/pilot from Ashley transforms in bind pose, sun from scene light, sky/fog from game render params, camera from field camera) with cached meshes/materials/textures; verify transform-only frames do not rebuild geometry via a headless test. (app-game-control-line)

- [ ] 3.6 On ray rendering failure after start (quality limit, stall, lost device), dispose the ray view, fall back to raster, and state the reason in window title and log; verify with a headless test simulating each failure type. (app-game-control-line)

## 4. Distribution guards and verification

- [ ] 4.1 Verify checkShippedClasspath passes with the ray source set present (shipped classpath excludes ray classes and ray-tracing modules); run the task and confirm it passes. (app-game-control-line)

- [ ] 4.2 Verify installDist and exportPlay outputs contain no ray-tracing module and no ray source-set class; run the tasks and confirm the outputs. (app-game-control-line)

- [ ] 4.3 Update app-game-control-line README with documented ray mode limitations (foliage omitted, bind pose only, development aid not a distribution feature); verify the file is updated. (app-game-control-line)

## 5. Final verification

- [ ] 5.1 Run ./gradlew check and scripts/check-docs.sh and confirm both pass. (all modules)
