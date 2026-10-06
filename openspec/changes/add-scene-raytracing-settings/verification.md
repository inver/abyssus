# Verification: add-scene-raytracing-settings

## Prerequisites and reconciliation (2026-10-04)

- `decouple-from-mundus` is archived at `openspec/changes/archive/2026-10-04-decouple-from-mundus`; status on its former active name returns change-not-found. It retains tasks 5.2/5.3 open. Native validation is implemented in core and wired to runtime readers and plugin writes.
- `extract-scene-runtime` is archived at `openspec/changes/archive/2026-10-04-extract-scene-runtime`, with manual/full-suite checks open. `runtime.scene.SceneDto` / `SceneParser` own raw parsing; plugin `sceneview/SceneRenderParams.kt` / `SceneParamsSource.kt` own view parameter conversion. Pure settings codec/editor stays in plugin sceneview alongside SceneJson-backed edits; raw optional JSON is carried by SceneDto.
- Active `openspec/config.yaml` requires native version 1, short component identifiers, asset kinds and no migration. `editSceneJson` validates before and after edits.
- User scope decision: extend settings and optics on both Metal and Vulkan. VulkanRaySession.submit(RaySceneRequest), src/main/glsl/scene.comp and raytracing/README.md confirm the full Vulkan scene path exists. Corrected stale assumptions in this change and reconciled the original fixed-depth/refraction exclusions in add-scene-raytracing proposal/design/tasks/spec. Existing completion and platform evidence retained.
- Strict validation passes for both changes.

Prerequisite test command: `./gradlew :core:test --tests '*AbyssusDocumentFormatTest' :runtime:test --tests '*SceneLoadingTest' :test --tests '*NativeDocumentReadTest' --tests '*NativeDocumentWriteGuardTest'` — BUILD SUCCESSFUL. Log: `/private/tmp/abyssus-ray-settings-prerequisites.log`.
- AbyssusDocumentFormatTest: 6 tests, zero failures/errors/skips.
- SceneLoadingTest: 12 tests, zero failures/errors/skips.
- NativeDocumentReadTest: 2 tests, zero failures/errors/skips.
- NativeDocumentWriteGuardTest: 3 tests, zero failures/errors/skips.

## Pure scene codecs

- Tasks 2.1/2.2: SceneRaySettingsTest (6) and RayMaterialOverrideTest (6) pass with zero skips. Each first failed compilation on the missing codec API, then passed after implementation. Commands: `./gradlew :test --tests '*SceneRaySettingsTest'` and `./gradlew :test --tests '*RayMaterialOverrideTest' --tests '*SceneRaySettingsTest'`. Logs: `/private/tmp/abyssus-ray-settings-codec-red.log`, `/private/tmp/abyssus-ray-settings-codec-green.log`, `/private/tmp/abyssus-ray-material-codec-red.log`, `/private/tmp/abyssus-ray-codecs-green.log`. Coverage: bounds/types, omitted defaults, unknown fields/raw number text, expected-node conflicts, independent instances, unresolved/ambiguous IDs and PBR eligibility.

- Task 2.4: RaySceneSnapshotTest passes, including independent override copies with shared mesh identity, unresolved-ID fallback and malformed settings carried through runtime parsing. Command: `./gradlew :test --tests '*RaySceneSnapshotTest' --tests '*SceneRaySettingsEditTest'` — BUILD SUCCESSFUL; log `/private/tmp/abyssus-ray-snapshot-green.log`. SceneRaySettingsEditTest (3) passes after using the formatted starting text (SceneFormatListener formats native scenes on text-editor open); Properties undo routing remains pending for task 2.3.

## Budget and scheduler

- Task 3.1: RayWorkBudgetTest (3) passes with instrumented CPU intersection counts for opaque/masked/blended scenes and 0/1/12 lights, default/non-PBR costs, maximum limits and overflowing frame multiplication. Native loops inspected: both use 16 blend + 8 cutout primary queries and 8 secondary cutout retries. Cost accounts for direct shadows at every possible primary/secondary shaded vertex and one sampled continuation per event. Command `./gradlew :raytracing:test --tests '*RayWorkBudgetTest'`; red/green logs `/private/tmp/abyssus-ray-budget-{red,green}.log`.
- Task 3.2: RayQualityPolicyTest and RayRenderSchedulerTest pass. Saved budget is now a hard bound with explicit minimum-resolution fallback; targets 1/257/4096 stop exactly, clamp the last batch and retain the eight-sample cap. Settings are frozen per input and policy selection remains on the worker. Command `./gradlew :raytracing:test --tests '*RayQualityPolicyTest' --tests '*RayRenderSchedulerTest'`; logs `/private/tmp/abyssus-ray-policy-{red,green}.log`. Native independent samples/optical transport remain task 4 work.

- Tasks 3.3/3.4: `./gradlew :test --tests '*RaySettingsRevisionTest' --tests '*RayViewFeedTest' --tests '*RayRenderLifecycleTest'` passes (log `/private/tmp/abyssus-ray-revision-green.log`; new revision tests first failed on missing publication invalidation/settings propagation). Coverage includes immediate invalidation before stalled conversion, coalesced successive edits, unchanged session count, two views, off/future views, hide/show/close plus existing delayed completion/lifecycle cases. Worker policy stays dynamic and the new conservative cost reaches scheduler inputs. Budget/revision semantics documented in raytracing/README.md and docs/ai/architecture.md.

## Optics, backends and packaging (2026-10-04)

Host: macOS (Apple GPU, Metal). No Vulkan loader or device is installed (`/opt/homebrew/lib/libvulkan*` absent), so every Vulkan render case was skipped and none is claimed as passing.

- Task 4.1: RayOpticsTest (6) passes: IOR 1, air/glass entry and exit, starting inside glass, critical angle, exhausted counters, finite values, repeatable independent sample seeds. `./gradlew :raytracing:test --tests '*RayOpticsTest'`.
- Task 4.2: RayBackendContractTest (7), RayMaterialTest (7), RayOpticsContractTest (4, native layout and 28-float camera), RayOpticalEligibilityTest (4) and `:test` RaySceneSnapshotTest (12) pass. `RayBackendService` now calls `RaySceneRequest.requireOptics` before submission; RayRenderLifecycleTest `aBackendWithoutSceneOpticsFallsBackInsteadOfIgnoringSavedDepths` shows a Failed view with a "scene optics" reason and no submission, and a capable backend rendering the same request.
- Task 4.3 (Metal and fake only, left open for Vulkan): new conformance cases `secondMirrorRevealsTheHiddenObjectOnlyFromReflectionDepthTwo` (limits 0/1/2/16), `closedSlabRefractionAtLimitsZeroOneAndTwo`, `fresnelSplitConservesEnergyInAUniformEnvironment`, `totalInternalReflectionReflectsInsteadOfTransmitting`, `mixedReflectionAndTransmissionPathsMatchTheReferenceAtEveryLimitPair` and `framesOverTheSavedRayBudgetAreRejectedAndTheSessionStaysUsable`, plus the earlier reflection-limit and closed-glass cases. `./gradlew :raytracing:test -Dabyssus.metalTests=true`: MetalRayBackendTest 54/54 and FakeRayBackendTest 54/54 executed with zero failures; VulkanRayBackendTest 55 skipped. Log: scratchpad `raytracing-metal.log`.
  - Bug found by the Metal run: `RayQueuedSession.poll` kept `inFlight` after a driver rejected a completed frame (query budget or unsupported medium), so later submissions waited forever. Fixed; `RayQueuedSessionTest` fails without the fix and passes with it. The fake backend missed this because its reference renderer fails at submit time.
- Task 4.4 (Metal and fake only, left open for Vulkan): `unsupportedTransmissionIsAnExplicitFailure` (open surface, masked and blended transmission, a solid entered from inside another, then a valid frame on the same session) and `transmissiveSolidsCastStraightOpaqueShadows` pass on Metal and the reference; existing blended, cutout and shadow conformance cases unchanged and passing. Unresolved overrides fall back in RaySceneSnapshotTest. raytracing/README.md documents supported geometry, the medium errors and straight opaque shadows.
- Task 4.5 (Metal only, left open for Vulkan): `./gradlew :raytracing:verifyNativePackaging -Dabyssus.metalTests=true` passes; MetalNativePackagingTest now also renders a glass slab through the packaged scene shader (`assertPackagedSceneOptics`: red wall only with two crossings, opaque at limit 0). VulkanNativePackagingTest `packagedBackendRendersAFrame` (which now includes the same optics render) was skipped: no device. README packaging notes updated. To close: run `./gradlew :raytracing:test -Dabyssus.vulkanTests=true` and `./gradlew :raytracing:verifyNativePackaging -Dabyssus.vulkanTests=true` on a Vulkan ray-query device (or lavapipe).

## Properties (2026-10-04)

- Task 2.3: `useUndoEditor` hands the panel's hidden editor the scene file for scene and entity details. AssetPropertiesPanelTest `testSceneRaySettingsUndoAndRedoFromThePanelFollowTheSavedScene` covers selection writing nothing, panel edit, Undo/Redo through the panel's editor with exact text, refresh after each, an external valid edit and an external invalid edit (reported, not rewritten). SceneRaySettingsEditTest (3) passes.
- Task 5.1: labels now match the spec (Target samples per pixel, Maximum rays per frame, Maximum reflection/refraction bounces), with help separating accumulated quality from per-frame work and showing ranges/defaults; a non-object `rayTracing` block is explained; a conflict message survives the panel's rebuild. SceneRaySettingsPanelTest (6) and SceneRaySwitchTest (11, incl. editing a limit while hardware is unavailable) pass.
- Task 5.2: `AssetLoading.rayModelMaterials` (core, no images; RayModelSnapshotTest covers the loader's IDs) feeds `PanelServices.rayMaterials`; the tool window caches tables per model file and modification time. RayMaterialPropertiesTest (7) passes: percent conversion with default IOR omitted, two instances, invalid edits, repeated/non-PBR explanations, unresolved overrides kept, unreadable model, external change, stale-editor conflict and Undo/Redo.
- Task 5.3: scene view README, README plugin description, CHANGELOG, core/README.md and docs/ai/architecture.md updated; `scripts/check-docs.sh` passes (175 paths).
- Properties and scene view suites: `./gradlew :test --tests 'net.nevinsky.abyssus.properties.*' --tests 'net.nevinsky.abyssus.plugin.sceneview.*'`: 535 tests, 0 failures, 40 skipped (GL tests without `-Dabyssus.glTests=true`).

## Manual runIde checks (not performed)

Tasks 6.1–6.3 need an interactive `./gradlew runIde` session on a temporary copy of Untitled and were not performed in this session. They remain open.

## Completion checks (task 6.4, 2026-10-04)

- First `./gradlew check` failed 3 AbyssusViewTest cases, caused by this change: the new `SceneDto.rayTracing` property appeared in the Abyssus view tree as a `rayTracing: null` row. Fixed by making it read-only for Jackson (`@param:`/`@get:JsonProperty(access = WRITE_ONLY)`), so it is still parsed (RaySceneSnapshotTest malformed-settings case) but not listed.
- Second `./gradlew check`: BUILD SUCCESSFUL. Tests (run/failed/skipped): plugin 812/0/40, gdx-model 27/0/9, core 215/0/12, runtime 80/0/0, physics 38/0/0, raytracing 229/0/114 (device tests are opt-in), physics-plugin 11/0/0, control-line 88/0/0. The skips are opt-in GL, Metal and Vulkan device tests; Metal was run separately above.
- `scripts/check-docs.sh`: 175 paths OK. `openspec validate add-scene-raytracing-settings --strict` and `openspec validate add-scene-raytracing --strict`: valid.
- Checked boxes: 1.1–1.2, 2.1–2.4, 3.1–3.4, 4.1–4.2, 5.1–5.3, 6.4, each with the evidence above. Open: 4.3–4.5 (Vulkan device runs not performed; Metal passes) and 6.1–6.3 (manual runIde checks not performed).
