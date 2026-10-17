# Design

## Context

See proposal.md for motivation and scope. `SceneDto.abyssus` already carries raw extension data. Water settings, wave math and drawing are in `lib-core`, but `SceneWater` and its reflection/depth orchestration depend on editor scene types. The game's `FieldRenderer` draws terrain through `lib-render.TerrainPass` and models through `lib-gdx` shaders; it does not integrate scene water. The flying circle is `model_airfield_pad`, so changing only terrain shaders cannot satisfy this feature.

`SceneDetailsView` is the scene Properties entry point. `GameUiModel` publishes typed bindings into the authored screens asset. The working tree is midway through shared rendering extraction; preserve its host adapters and ownership policies. Existing cloud snapshots remain separate assets and are neither read as ground weather nor rewritten.

## Goals / Non-Goals

**Goals:** One runtime interpretation and deterministic receiver policy, host-neutral wet shading/reflections, no editor dependency in shipped modules, and recoverable optional rendering resources.

**Non-Goals:** General water orchestration extraction, wet materials in native ray backends, physical water accumulation, or automatic semantic recognition of every possible imported model. This first receiver classifier deliberately favors conservative ground candidates.

## Decisions

### 1. Minimal scene extension and separate editing adapter

Use `abyssus.weather: { "mode": "afterRain" }`. No generated puddle arrays, weather assets, seed UI or version bump. Missing mode means Default. Read validated documents through the existing mapper/DTO facilities (`JsonProcessor.bind`, `SceneReader.parse`, `SceneJson.bind`); use the raw extension tree only through the established scene document APIs. Do not introduce another parser or stock Jackson tree parse path.

Add constructor-wired `WeatherSettings` and a pure reader/validator in `lib-core`; `lib-core-editor` adds the text edit adapter using `DocumentTextEditor`. Reset removes `mode`, then empty known containers only; unknown members survive. Reject non-object weather/abyssus containers. Unknown mode reports Default without automatic repair; an explicit supported mode selection may replace the unknown scalar. Check the expected mode at commit so stale panels cannot overwrite newer edits. All plugin scene writes use `editSceneJson` as one command.

Alternative: store weather in a cloud asset. Rejected because users chose to preserve sky and lighting, and shared skies must not force shared wet ground.

### 2. Shared receiver classification and stable masks

Put pure receiver planning in `lib-render/weather`, with host-neutral geometry/transforms, stable scene/entity identities and foliage exclusions as inputs. Terrain height data and retained CPU model triangles are supplied by adapters, never by editor snapshot types. Cache classification by geometry revision, world transform and foliage revision; no mesh readback or scene-wide triangle scans each frame.

Initial conservative policy: accept terrain faces within 8 degrees of world horizontal; accept opaque static model ground sheets no more than 0.5 scene units in world vertical extent whose candidate upward faces lie within 0.25 units above supporting terrain. Treat scene units as metres in Control Line. Exclude skinned/animated models and foliage instances. Require actual supporting triangle/terrain coverage, not bounding rectangles. This admits the asphalt slab while rejecting buildings, furniture and planes. Model-only scenes without supporting terrain still render normally but do not automatically acquire model puddles. Exclude lake/sea-covered fragments and known foliage footprint coverage. Resolve overlapping terrain/model receivers to the uppermost admitted ground surface so the pad is not rendered twice.

Evaluate a shared world-space noise mask with a fixed algorithm version and a stable seed derived from scene id. Combine broad irregular patches with finer edge breakup, local flatness and small height variation; avoid camera/time-dependent randomness. Coverage favors local shallow low areas but is artistic, not a hydrology solver. Both hosts use the same planner and mask source. Regenerate cached inputs when geometry or transforms change; background results publish only if their revision is still current. No per-puddle scene entities or saved caches.

Alternative: lake rectangles or one reflected plane per puddle. Rejected for visible rectangular edges, poor fit to varying ground levels and unbounded scene redraw cost. Painted masks are outside the automatic workflow selected by the user.

### 3. Wet material response plus bounded reflection pass

Use shared wetness/mask shader functions and runtime bindings. `lib-render` owns terrain integration and a puddle pass. `lib-gdx` exposes neutral per-frame/per-draw material attributes for admitted model receivers; it does not import `lib-render` or weather scene types. Both default and PBR ground model paths receive the wet response. Retain original texture/marking detail, modestly darken the diffuse term and reduce effective roughness in wet regions. Puddle Fresnel and softened mask edges add a shallow water sheen without lake waves or shoreline foam. Non-receivers keep their current materials.

Provide bounded screen-space reflection tracing from opaque scene color/depth/normals, with current sky/background fallback for offscreen or invalid hits. Use at most one opaque color/depth/normal target set at viewport resolution, one reflection target at half resolution, at most 32 march steps and 4 refinement steps per reflection pixel. No scene rerender per patch. Sky sampling is injected by each host, preserving its existing sky implementation and time, and includes displayed clouds. Do not claim offscreen geometry reflections; those use the sky fallback. Composite weather before editor overlays and game lines/UI so those never enter reflections. Exclude lake/sea receivers and preserve the editor's existing water pass contract; use opaque inputs before either water treatment, with lake/sea depth coverage suppressing puddles.

Default bypasses weather targets and passes entirely. Allocation/reflection failure keeps wet shading and sky fallback where available; shader failure skips weather with a bounded diagnostic while the base scene remains usable. Targets are reused across frames and released when disabled/hidden/disposed or context ownership changes. Borrowed assets and sky textures are never disposed by this pass.

Alternative: extract all `SceneWater` orchestration into the shipped library now. Rejected as unnecessary scope; reuse neutral math/shader helpers only where useful. A new third-party rendering dependency is unnecessary.

### 4. Host adapters, UI and session precedence

Plugin scene Properties gains localized Weather choice and validation feedback; the same unsaved document feeds preview, undo and redo. `SceneRenderer` adapts its current geometry and render targets into the shared pass, including transform previews. Picking and Drop remain unchanged. Requested ray mode is retained, but admission falls back to raster with a localized wet-weather reason. This avoids silently presenting a dry ray image. Removing wet weather re-evaluates the existing request without changing stored ray limits.

Control Line's `FieldScene` reads scene weather from its loaded DTO. A session-level optional override, held outside reloaded flight scenes, initializes absent and persists across menu, flight, pause, results, retries and subsequent flights until application exit. Main menu shows the effective Default/After the rain value and publishes a typed action/binding through `GameUiModel` and the UI contract. Use the existing authored widgets and navigation to cycle the value by mouse or keyboard; do not add custom screen drawing. Returning to Default is an explicit override, not a return-to-scene command. Restart drops the override and follows the scene again. No preferences or scene writes from the game.

The F2B change replaces the older Start menu with Free flight/F2B. Preserve those current entries and amend that change's menu delta during apply to acknowledge the additive weather selector. Likewise reconcile the active ray-tracing delta with the new weather admission reason. These are planning coordination tasks, not authority to alter unrelated behavior.

### 5. Threading, boundaries and verification

Pure weather validation, text transforms, receiver eligibility, mask seed and session precedence run without GL/platform dependencies. Plugin DTO/geometry preparation uses existing background read/prepare paths with cancellation preserved; publish immutable results on the EDT. Plugin widgets and scene commands run on the EDT. Every plugin libGDX/GL call runs on the canvas AWT rendering thread inside `GdxRuntime.withContext`, only while `glSafe`. Control Line prepares CPU data through its existing loading path and runs drawing/resource disposal on its owning GL thread. No Jolt calls or physics integration are added.

Verify native text preservation/undo/conflicts, fixed-seed masks across hosts, transformed ground/steep/roof/foliage exclusions, session precedence and identical simulation input traces. Opt-in GL tests cover dry bypass, wet pad/terrain appearance, both model shader paths, reflection fallbacks, pass ordering, water coexistence and resource replacement. Manual acceptance uses copies of Untitled and ControlLine, including pilot/grazing/top-down cameras and editor Play. Record representative hardware, resolution, dry/wet CPU/GPU times and target allocations; fixed pass/ray limits are contractual, while final aesthetic tuning uses those measurements.

## Risks / Trade-offs

- Conservative ground classification misses thick platforms or model-only scenes -> document eligibility, test the actual asphalt pad and avoid unsafe guesses that wet roofs; broader receiver authoring is deferred.
- Noise can resemble a glossy uniform coating -> inspect irregular edges, texture visibility and restrained coverage from both pilot and overhead views.
- Screen-space reflection loses offscreen geometry -> blend to current sky without stretching or ghosting; exact planar reflection parity with lakes is not promised.
- Existing water/ray work and render extraction are active -> reconcile named deltas and use current shared frame inputs; do not revert or duplicate their implementation.
- Wet rendering can increase frame cost -> bound targets and march iterations, cache CPU receiver work, measure steady-state separately from first compilation and preserve dry bypass.

## Migration Plan

No migration or automatic rewrite. Existing scenes retain Default. Ship the game with its current dry field and a menu selector; test wet scenes using project copies. Rollback removes optional runtime/UI integration; stored weather remains valid unknown extension data for older readers. No asset regeneration or physics data changes are required.
