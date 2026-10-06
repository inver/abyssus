# Proposal

## Why

Scene ray tracing exposes only an on/off switch: users cannot choose an image-quality target, a ray budget, or reflection/refraction depth. These choices need to be editable in Abyssus Properties, saved with each scene, and reflected immediately in its preview, including actual glass refraction.

## What Changes

- Add four integer controls under the selected scene's Rendering section: Target samples per pixel, Maximum rays per frame, Maximum reflection bounces, and Maximum refraction bounces.
- Persist them per scene and restore them after reopening. Defaults are 256 accumulated samples, 2,097,152 rays per submitted frame, one reflection bounce, and zero refraction bounces. The existing Ray Tracing enable switch remains a runtime choice, off on opening.
- Keep adaptive interaction quality, with the sample target accumulated over bounded submissions and the ray budget covering primary, secondary, shadow and intersection-retry work. Setting changes invalidate older rendering work and history.
- Replace fixed reflection depth with bounded configurable transport, and implement refraction for explicitly transmissive PBR model materials, including air/glass entry and exit and total internal reflection.
- Add narrowly scoped per-instance material overrides for Transmission and IOR to the selected model entity/Render component's Properties section. Existing alpha transparency is not inferred to be glass.
- Preserve unrelated scene text, omitted defaults, and key order; accepted edits are undoable and propagate to every open view of the scene.

**Fields read/written:** add root `rayTracing.targetSamplesPerPixel`, `rayTracing.maxRaysPerFrame`, `rayTracing.maxReflectionBounces`, and `rayTracing.maxRefractionBounces`; add `ecs.entities.<id>.components.RenderComponent.rayTracingMaterials.<material-id>.transmission` and `.ior`. Continue reading existing renderable asset references, transforms, model material identifiers, lighting, sky and fog fields. No `.abss`, model source or asset `meta.json` writes are introduced.

**Format impact:** these are additive Abyssus scene fields, with defaults omitted. They are native Abyssus scene fields inside the version 1 contract. Implementation depends on `decouple-from-mundus` establishing the native document contract; this planning change does not modify workflow configuration or convert legacy files.

**Out of scope:** persisting the enable switch, a general material editor, imported transmission extensions/textures, raster refraction, water, caustics, diffuse global illumination, dispersion, absorbing volumes, overlapping/nested refractive solids, and completing unverified platform/device integration gates.

## Capabilities

### New Capabilities

- `scene-raytracing-settings`: Persisted quality and transport limits, their budget semantics, and immediate scene-wide application.
- `scene-raytracing-materials`: Explicit transmissive material overrides and bounded reflection/refraction behavior. Complements the open `scene-raytracing` capability rather than redeclaring its foundational platform requirements.

### Modified Capabilities

- `object-properties-panel`: Scene rendering controls and per-instance transmissive material editors, validation, refresh and undo behavior.

## Impact

- Properties `SceneDetailsView`, `PanelState`, `EntityDetailsView`, localized bundle text and document refresh/undo routing; scene edits continue through `editSceneJson` and `SceneJson`.
- Scene parameter decoding, `RaySceneSnapshots`, `RayViewFeed`, `RayViewRuntime`, `RayBackendService`, `RayRenderScheduler`, `RayQualityPolicy`, request/material/native payloads and the Metal scene shader; extend the CPU reference renderer and conformance kit.
- Reuse model snapshot material identifiers without changing shared asset metadata. Keep native/GL work off panel actions and plain JVM boundaries intact. No new dependency is planned.
- Depends on the implemented scene path in `add-scene-raytracing` and native format from `decouple-from-mundus`. During implementation, amend the former's refraction exclusion and fixed one-bounce planning language; coordinate parameter ownership with `extract-scene-runtime` if its classes have moved. Unsupported backend scene features must produce explicit raster fallback rather than ignored settings.
- Update file-format, architecture, scene-view and raytracing documentation, user documentation and changelog. Device verification covers both implemented Metal and Vulkan scene paths; unperformed platform/device checks remain explicitly reported.
