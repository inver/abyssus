# Proposal

## Why

The Scene view cannot show geometry reflected in other surfaces, and its raster lighting cannot provide ray-traced shadows. Users need to judge both effects while moving the camera, objects and lights, on macOS, Windows and Linux.

## What Changes

- Add an opt-in Ray Tracing toolbar toggle for live shadows and reflections while editing, including transform previews and animation.
- Detect a usable GPU/backend, explain unavailability inline, and retain the existing renderer on unsupported machines or recoverable backend failure.
- Render opaque and alpha-tested models and terrain with their existing materials, lights and environment; preserve picking, gizmos, camera look-through, fog and overlays.
- Add bounded asynchronous rendering and adaptive resolution/sample counts so interaction remains responsive. Improve quality when movement stops without requiring a still camera to display effects.
- Establish Metal on macOS and Vulkan on Windows/Linux as the initial backend design, subject to a narrowly defined integration/performance gate on each platform.
- Keep the toggle per open view and off by default. Switching it does not edit scene, project or asset files.

Out of scope: software ray tracing on unsupported GPUs, full path tracing/global illumination, baked lighting, image export, transparent refraction or transparent reflection geometry, new material editors, water rendering, and replacing the default raster renderer.

## Capabilities

### New Capabilities

- `scene-raytracing`: Optional live ray-traced shadows and reflections, availability, quality bounds, editor integration and fallback.

### Modified Capabilities

- `scene-environment-lighting`: In Ray Tracing mode, PBR specular reflections include scene geometry and sample the existing HDR environment on misses; existing raster and diffuse environment behavior remains.

## Impact

- SceneViewPanel toolbar, SceneFileEditor wiring and SceneRenderer composition; SceneModels, SceneTerrains, ScenePreview and asset preparation supply geometry/material snapshots.
- A constructor-wired plain JVM rendering module plus packaged native Metal bridge and Vulkan dependencies. Native build/package verification is needed for supported macOS architectures and Windows/Linux targets; no IntelliJ dependencies enter core or gdx-model.
- Reads existing `ecs.entities.*.components` RenderComponent asset references, PositionComponent transforms, LightComponent color/intensity/range/coneAngle/edgeSoftness, CameraComponent lens/direction and scene ambient/fog/skybox fields. Reads existing model material/texture and terrain/splat asset data, and project `mainCamera`. Writes none of these; no `.scene`, `.abss` or `meta.json` format changes.
- Coordinate with `add-scene-shadows`: its shadow maps remain the raster fallback. Its explicit ray-tracing exclusion is its scope boundary, not a prohibition on this separate capability; review overlapping deltas during implementation.
- Update architecture, Scene view documentation, user feature documentation and changelog with GPU support and fallback behavior.
