# Design

## Context

See proposal.md for motivation and the delta specs for the behavior contract. `SceneDetailsView` currently edits only runtime mode through `SceneRayControls`; its `PanelState.SceneDetails` does not read scene contents. The existing test `SceneRaySwitchTest` asserts that toggling never writes the scene, which remains true for the switch but no longer describes the entire Rendering section.

`RayQualityLimits` defaults to eight samples per submission and 256 accumulated samples. `RaySceneRequest` accepts only 1..8 samples per submission. `RayViewRuntime` receives immutable limits when constructed; `RayBackendService` constructs the scheduler/policy when activating it. `RayViewFeed` currently estimates ray cost as one primary, one shadow per light and one reflection for any PBR material. That estimate omits secondary lighting and cutout/blend retries and cannot enforce the new budget contract without replacement.

`RayModelSnapshotReader` retains material IDs, and `RaySceneSnapshots` converts CPU model data to the scene ray material representation. Materials currently have alpha mode and opacity but no transmission/IOR. The Metal shader has a fixed single-bounce scene path; Vulkan remains a feasibility slice with no complete scene renderer. Native and fake conformance tests pin the current single-bounce and blended-surface rules.

## Goals / Non-Goals

**Goals:** Make persisted document state authoritative; keep panel commits independent of GPU availability; bound transport without recursive branch explosion; maintain responsiveness through revisioned immutable requests.

**Non-Goals:** No shared material mutation or general material-authoring pipeline. No recursive GPU call stack, new native window, new dependencies, or implied Vulkan scene parity. Optical overrides are deliberately scene-instance data until a separate material-authoring capability exists.

## Decisions

### 1. Native scene fields with omitted defaults

Add an immutable `SceneRaySettings` value and pure codec/editor in the scene parameter owner (plugin today; `runtime` if `extract-scene-runtime` has moved that ownership). The codec distinguishes omitted fields from malformed present values and carries errors without repairing files. Accept samples 1..4096, rays 1..67,108,864, and each depth 0..16. These bounded ranges are proposed product defaults; they do not promise the backend can render every combination at every resolution.

Store four fields under root `rayTracing`; remove default-valued edited fields and prune only empty known containers. Preserve unknown members. Opening the panel never inserts defaults. Use `SceneJson` and `editSceneJson` for both settings and optical overrides. Check the populated field's expected value against the current document before applying an edit; equal-value edits do nothing. Document commands and document references support Undo/Redo from Properties. Subscribe to the existing scene edit topic and document/VFS refresh to update controls while preserving focus where possible.

Alternative: IDE workspace persistence would survive reopening locally but would not travel with scene files. A sidecar would add lifecycle and rename concerns. The selected `.scene` storage satisfies the requested scene ownership. It requires the native document change first: the fields are native extension data under the version 1 contract, written only to supported native scenes. No workflow configuration is edited by this change's proposal.

### 2. Scene-owned per-instance optical overrides

Store an optional map at `RenderComponent.rayTracingMaterials`, keyed by the CPU snapshot's unique material ID, with `transmission` (0..1, default 0) and `ior` (1..3, default 1.5). Expose only these two values per PBR material in the model entity/Render Properties section. Require nonempty unique IDs; ambiguous IDs are not editable. Show unresolved stored IDs after asset replacement and preserve them without retargeting. Validation allows saved drafts on unavailable hardware but activation rejects positive transmission for masked/blended materials.

The pure override codec/editor performs validation, default removal and material-ID resolution. The converter copies only the affected instance's materials, includes overrides in material/content identity, and keeps shared meshes/textures reusable. Overrides are applied after material snapshot conversion; `core`'s snapshots need no optical policy or IntelliJ imports. Raster ignores optical overrides and retains the base material.

Alternative: editing asset `meta.json` or model files would require a new shared material-loading/editing contract and would affect every instance. Deriving transmission from opacity would alter existing blended scenes. Both exceed the narrow controls needed here.

### 3. Dynamic settings revisions and accumulation

Carry frozen settings and a settings revision through scene parameters, conversion jobs, requests and completed-frame identity. Updating settings replaces pending work, clears publication/history, and changes the scheduler's policy on its owner worker. If a submission is in flight, let it finish safely but reject its old revision; do not display it after the settings change. Distinguish this reset from unchanged still content, which stops once the target is reached.

Separate accumulated target from the native per-submission sample cap (retain 8). The policy chooses dimensions/sample count using timings and worst-case ray cost; the scheduler clamps the last submission to the remaining target. Changing internal resolution starts a fresh accumulation epoch. Settings apply to every registered view and are loaded for views opened later. Persisted edits must not open a view, enable mode or trigger native initialization.

Alternative: recreating the view/session on every spinner edit would simplify limits ownership but needlessly rebuild native resources and disrupt preview. Runtime-only limits would fail restoration and multi-view consistency.

### 4. Bound every intersection query

Introduce a pure `RayWorkBudget` estimator using overflow-safe Long arithmetic, plus a shader/reference per-path query guard. A conservative bound per camera sample includes primary and all bounded alpha layers/cutout retries, each possible secondary event, and each direct-light shadow query at every shaded vertex with its cutout retries. Derive constants from the actual traversal loops rather than assuming one query per ray. Use the same declared limits in native code and the reference model, with tests that count actual intersection invocations.

Choose submission pixels and samples so their worst-case product fits `maxRaysPerFrame`, after backend dimension, memory and dispatch caps. With one sampled continuation per vertex, at most R+T secondary events occur (R,T each <=16); there is no binary reflection/refraction tree. The query guard treats unexpected overrun as an explicit failed frame, never silently returning an incomplete image. Any full-frame error flag travels through readback/poll and produces raster fallback with a reason. Backend dispatch splitting cannot multiply the total frame budget. A saved budget too small for the existing half-resolution minimum produces explained fallback; the file retains its value.

Alternative: counting only primary rays makes the control misleading. An unbounded branch tree would make modest bounce increases consume exponential work. Lowering resolution below the existing floor silently would contradict the current quality policy.

### 5. Iterative reflection and dielectric transmission

Extend `RayMaterial`, immutable request payloads and native material layouts with transmission and IOR, and requests with both depth limits. Implement the same iterative transport semantics in the CPU reference and Metal scene kernel. Nontransmissive default depth 1 preserves existing reflection behavior within established image tolerances; depth 0 uses environment specular. At exhausted continuation limits, use terminal environment shading without another scene query. Preserve existing direct lights, emission, sky sampling, fog conventions and material roughness behavior.

For positive transmission on opaque PBR surfaces, compute dielectric Fresnel using air IOR 1 and the selected material IOR, refract with Snell's law, and choose one reflected/transmitted continuation using deterministic pixel/sample/event seeds and probability-compensated throughput. Combine transmissive and existing opaque PBR contributions without doubling specular energy. Count reflection and transmitted crossings separately; total internal reflection consumes reflection depth only. An exhausted branch receives its terminal environment contribution instead of spending a disallowed ray. Keep throughput and direction finite, offset origins on the correct side, and use geometric normals for side/medium changes and shading normals for appearance.

Track air versus one current solid (instance ID and IOR). An entry/exit pair consumes two refraction events. Starting inside a solid is inferred from the first backface exit. Encountering a different transmissive solid before exiting, an invalid boundary, or an unmatched open boundary sets an unsupported-optics error and falls back after poll. CPU topology checks reject open/nonmanifold transmissive meshes where detectable; request-side eligibility and shader medium checks cover the supported contract. Nested/overlapping media are not approximated silently.

Preserve alpha blending on materials without transmission and keep masked/blended transmission unsupported. Transmissive solids participate in secondary intersections, with primary depth using the first opaque boundary so existing overlay occlusion and CPU picking remain usable. Shadow rays keep straight opaque occlusion for those solids; colored transmission shadows and caustics are excluded. Editor decorations never enter native geometry.

Alternative: alpha blending has no refracted direction; a single fixed air-to-glass bend cannot correctly leave a closed object. A full medium stack and absorption model are larger capabilities than this first explicit glass mode.

### 6. Threading and backend capability

Panel actions, document commands and listener publication run on the EDT. Settings/override codecs, budget math and optical math have no Swing, GL or IntelliJ dependency and are headless-testable. Document reads follow existing background-read patterns. Conversion, material resolution and skin deformation run on the converter thread using frozen jobs. Policy replacement, native preparation/submission/polling/disposal run on the service's serial worker. Only safe presentation/GL upload uses `GdxRuntime.withContext` on the rendering AWT thread with `GuardedGLCanvas.glSafe`.

Add a scene-optics capability check rather than assume every backend understands the extended payload. Implement and verify fake/reference and Metal; Vulkan's missing scene path remains explicit unavailability/fallback. Shader/native layout changes ship together and packaging tests load the compiled library. No GUI action awaits conversion or a device fence.

## Risks / Trade-offs

- Conservative cost can lower quality sooner than the old estimate --> test the corrected default budget on representative copies of Untitled and report real fallback/timing, never weaken the bound to preserve a screenshot.
- Glass sampling can be noisy --> accumulate deterministic independent samples, verify sample offsets, Fresnel energy and target stopping; interaction may remain noisy by design.
- Mesh orientation and invalid solids can make medium tracking unreliable --> geometric boundary checks, explicit unsupported reason and closed-slab reference fixtures; document supported geometry.
- Native-format and runtime extraction work may land in another order --> task 1 reconciles ownership/contracts before implementation; this change does not silently implement those other changes.
- Stored high budgets may exceed hardware limits --> policy intersects user settings with device/resource caps and shows explicit fallback without rewriting saved preferences.
- Raster fallback cannot show the new glass effect --> keep scene editing and base appearance intact and state why Ray Tracing was unavailable.

## Migration Plan

1. Land the native document contract from `decouple-from-mundus` and use the existing complete Metal scene path. Reconcile the base raytracing delta's recursion/refraction exclusions as explicit historical baseline, superseded by these optical requirements.
2. Add optional codecs and tests; native scenes lacking fields keep defaults and are not rewritten on load. Extend scheduler/material/request layouts and package matching Metal shaders.
3. Add Properties editors and verification on temporary native project copies. Record manual and device results separately; skipped device tests do not count as support verification.
4. To roll back the feature, turn runtime Ray Tracing off. Older native readers must preserve unknown optional fields; there is no automatic file conversion or deletion of saved settings.
