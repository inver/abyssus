# Tasks

Proposed test classes and Gradle tasks below are to be added during implementation. Manual checks use copies of Untitled and Animated plus synthetic reflective/cutout scenes, never the original fixtures. Re-read current fixture contents before pinning names, ids or coordinates. Record device/platform measurements and manual outcomes in this change's verification.md during apply.

## 1. Backend feasibility and build foundation

- [x] 1.1 Reconcile this plan with the current add-scene-shadows and add-realistic-water deltas and implemented renderer; amend contradictory deltas if necessary without broadening their scope. Verify with `openspec validate add-scene-raytracing --strict` and validation of any amended change; record the reconciliation in verification.md.
- [ ] 1.2 Add a constructor-wired raytracing JVM module, backend interfaces, immutable frame/capability types and Gradle wiring without IntelliJ dependencies. Verify `./gradlew :raytracing:compileKotlin` and RayBackendContractTest cases for unsupported features and instance ownership. (Reopened: `verification.md` records this as done, but the `add-scene-object-drop` branch has no `:raytracing` module in `settings.gradle.kts` and no `raytracing/` directory. Re-land it or merge the branch that has it before 1.3.)
- [ ] 1.3 Add `RayBackendConformanceKit` (design decision 7) as an abstract test suite, and a fake in-memory backend that passes it headlessly with `./gradlew :raytracing:test`. Make MetalRayBackendTest (1.6) and VulkanRayBackendTest (1.7) extend the kit, and run them as opt-in device tests on their platforms. The kit covers:
  - probe without a device;
  - the reference scenes, compared with tolerances;
  - depth convention and instance motion;
  - request replacement and stale-frame rejection;
  - two sessions on one device;
  - dispose with work in flight;
  - injected device loss.
- [ ] 1.4 Add the Vulkan dependencies and build-time shaders:
  - `lwjgl-vulkan` and `lwjgl-vma` pinned to `lwjglVersion`, with the `lwjgl-vma` natives for every target and the MoltenVK natives for macOS only.
  - A Gradle task compiling the GLSL compute shaders to SPIR-V resources; no `lwjgl-shaderc` is packaged.
  - Optional loading: nothing Vulkan-related loads until a probe runs.
  Verify with the proposed `./gradlew :raytracing:verifyNativePackaging`. It checks that the plugin zip holds the SPIR-V and the expected natives, has no shaderc, and that a probe on a machine without a Vulkan loader returns "runtime not found" instead of throwing. Run it on Windows/Linux x86_64 and macOS arm64/x86_64.
- [ ] 1.5 Add a reproducible Metal JNI bridge build (Objective-C++, the Metal library compiled at build time) for macOS arm64 and x86_64, with optional loading. Verify that `verifyNativePackaging` loads the bridge and runs a probe on both Mac architectures, checking load behavior and not only filenames.
- [ ] 1.6 Build the minimal Metal offscreen camera-ray, shadow and reflection slice, including color/depth readback and a movable acceleration-structure instance. Verify opt-in MetalRayBackendTest cases for visibility, reflection hit/miss and instance motion on a compatible Mac.
- [ ] 1.7 Build the same minimal Vulkan slice, headless (no GLFW, surface or swapchain).
  - It requires the features in design decision 8, using ray queries in compute shaders.
  - It writes an `R16G16B16A16_SFLOAT` color storage image and an `R32_SFLOAT` depth storage image.
  - VMA allocates memory, buffer device addresses back the acceleration structures, and completion is polled from the backend worker.
  Verify with opt-in VulkanRayBackendTest cases for visibility, reflection hit/miss and instance motion on compatible Windows and Linux devices, run with `-Dabyssus.raytracing.validation=true` and no validation errors.
- [ ] 1.8 Connect the slices to experimental GL presentation and complete manual runIde check 1 on each OS: 30-second moving-instance/camera test at 1280x720, internal resolution no lower than 640x360, at least 30 presented fps and p95 preview latency below 100 ms; record EDT upload/submission timing against the 8 ms target, depth correctness and reference hardware. Stop and revise the design if the gate fails; leave this task open for untested platforms.
- [ ] 1.9 Document module ownership, native toolchains, optional loading and opt-in backend-test commands in the new module README and docs/ai/architecture.md. Verify `scripts/check-docs.sh` and the documented build commands on the corresponding targets.

## 2. Asset snapshots and live scene updates

- [ ] 2.1 Retain optional immutable CPU model geometry/material/texture data before preparation images are disposed, including acquisition when enabling after an asset is already cached. Verify `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.model.RayModelSnapshotTest'` cases for 32-bit indices, repeated instances, texture color spaces/samplers and release after cancellation; update core/README.md ownership notes.
- [ ] 2.2 Retain equivalent terrain height triangles, normals, UVs and splat textures. Verify `./gradlew :core:test --tests 'net.nevinsky.abyssus.assets.terrain.RayTerrainSnapshotTest'` cases for splat blending, transformed terrain and failed/missing layer images; update terrain loading notes in core/README.md.
- [ ] 2.3 Add RaySceneSnapshot/RaySceneDiff conversion using SceneContent, ScenePreview, LightSet and current camera/environment. Verify `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.RaySceneSnapshotTest'` cases for drag previews, selected-light identity, camera look-through, asset deletion, project replacement and limits causing explicit fallback.
- [ ] 2.4 Capture animation pose and implement worker-side deformation plus dirty geometry updates. Verify RayAnimationSnapshotTest cases for Animated's first animation, two independent instances, joint deformation and removal; opt-in backend tests must check the updated geometry hit position on both backends.
- [ ] 2.5 Implement static geometry reuse, top-level transform updates and owned resource budgets in both backends. Verify RaySceneDiffTest rebuild-versus-transform cases and backend lifecycle tests for repeated instances, memory-budget rejection and removed instances; document resource bounds in the module README.

## 3. Shading parity, shadows and reflections

- [ ] 3.1 Implement default/PBR direct shading and terrain splat evaluation with shared material reference vectors, UV/sampler conventions and existing light cone/range attenuation. Verify RayMaterialTest and RayLightTest reference cases and opt-in backend images for default, PBR and terrain materials.
- [ ] 3.2 Implement per-light visibility rays for directional/point/spot lights and cutout rejection, excluding editor decorations. Verify RayShadowBackendTest on both backends for model-to-terrain, terrain ridge, point coverage, spot cone, cutout holes and independent ambient/emission/second-light contribution.
- [ ] 3.3 Implement a bounded PBR reflection bounce including offscreen models/terrain, roughness sampling, metallic weighting and directly lit secondary hits. Verify RayReflectionBackendTest on both backends for offscreen hits, rough/smooth surfaces, recursion bounds and exclusion of blended geometry.
- [ ] 3.4 Transfer and shade the current sky/environment, preserving HDR diffuse-versus-ambient behavior, sky misses, orientation/exposure and fog. Verify RayEnvironmentBackendTest images for HDR hit/miss, non-HDR skies, disabled sky and fog distances; verify existing raster SceneRenderGlTest images with Ray Tracing off.
- [ ] 3.5 Implement bounded primary-ray alpha compositing with blended receivers and no blended shadow casters/reflection hits. Verify RayTransparencyBackendTest cases for overlapping transparent layers, shadow reception and explicit fallback on unsupported layer/resource limits.
- [ ] 3.6 Document one-bounce shading, material/transparency limits and sky behavior in the module README and Scene view README. Verify `scripts/check-docs.sh` and trace each documented limit to a passing case from tasks 3.1–3.5.

## 4. Scheduling, composition and editor controls

- [ ] 4.1 Implement one in-flight/one replaceable pending request, versioned completion, bounded quality policy and accumulation reset. Verify RayRenderSchedulerTest cases for continuous camera/animation motion without starvation, stale project/resize/deletion results, slow completion, cancellation and queue bounds; verify RayQualityPolicyTest timing and memory caps.
- [ ] 4.2 Integrate color/depth upload and grid/overlay composition into SceneRenderer, using safe GL context ownership and frame-matched display metadata. Verify opt-in RayFrameCompositionGlTest cases for depth conventions, fog/sky, grid occlusion, HiDPI sizing and GL state restoration; document render/worker threading in docs/ai/architecture.md.
- [ ] 4.3 Add the localized per-view Ray Tracing toggle and availability/preparation/failure states. Verify `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.RayModeStateTest'` and SceneViewPanelTest cases for default-off, two independent views, unsupported hardware and retry; update Scene view README, README.md feature description and CHANGELOG.md.
- [ ] 4.4 Wire editor updates, selection, move/rotate/drop previews, camera changes and undo/redo to snapshot publication without a new file writer. Verify SceneFileEditorTest and RaySceneSnapshotTest cases for edits, selection retention, camera retention and byte-identical files across toggles.
- [ ] 4.5 Add backend selection and device-loss fallback.
  - `RayBackendSelector`: the `abyssus.raytracing.backend` property (`auto`/`vulkan`/`metal`/`off`), probe order per OS, one probe per IDE session, re-probe on retry after a loss.
  - The toggle tooltip names the backend and GPU, or the reason none is usable.
  - Bounded dispatch sizes.
  Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.sceneview.RayBackendSelectorTest'` (forced, off, auto order, a forced backend that is unusable) and RayDeviceLossTest, which injects a loss through the kit hook and checks: raster resumes within one second, camera/selection/drag are kept, retry re-probes, and the EDT is never blocked. Update the Scene view README.
- [ ] 4.6 Add fallback, hide/resume and asynchronous disposal for native and GL resources. Verify RayRenderLifecycleTest cases for delayed completion after close, hidden-view submission stop, context replacement, failure during preparation/render and multiple views; verify ordinary raster startup without native library loading.

## 5. Platform integration and completion

- [ ] 5.1 Complete manual runIde check 2 on each supported platform: enable/disable, move/rotate/drop an object, drag a light continuously, orbit/pan and look through Camera 4, then undo/redo. Verify both effects update during the interaction and camera/selection/controls remain usable; record outcomes separately per OS.
- [ ] 5.2 Complete manual runIde check 3 on each supported platform: reflective object with offscreen geometry and terrain, roughness variants, cutout/blended surfaces, HDR/non-HDR/no-sky and fog, then Animated's independent instances. Verify reflection/shadow images against specs and capture comparable images/tolerances.
- [ ] 5.3 Complete manual runIde check 4 on each supported platform: resize/HiDPI, hide/show, minimize/restore, two views, asset loading/deletion, closing during preparation, unsupported-device and injected recoverable-failure fallback. Verify no stale effects, editing remains available and resources/queued work return to bounds.
- [ ] 5.4 Build the plugin with `./gradlew buildPlugin` and load the packaged zip on macOS, Windows and Linux. Verify native classifier selection, shader/native inclusion, the compatibility explanation and raster startup on unsupported devices. Also check: a Windows or Linux machine with no Vulkan loader starts the IDE cleanly with the "Vulkan runtime not found" reason; `-Dabyssus.raytracing.backend=off` loads no ray-tracing natives; and the tooltip names the backend/GPU on each supported device. Record the tested OS/device/driver matrix and distribution contents in verification.md.
- [ ] 5.5 Run `./gradlew check`, `scripts/check-docs.sh` and `openspec validate add-scene-raytracing --strict`; report any unrelated fixture/test failures with their cause, and leave unperformed platform/manual tasks unchecked.
