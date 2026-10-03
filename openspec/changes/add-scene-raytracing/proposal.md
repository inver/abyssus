# Proposal

## Why

The Scene view cannot show geometry reflected in other surfaces, and its raster lighting cannot provide ray-traced shadows. Users need to judge both effects while moving the camera, objects and lights, on macOS, Windows and Linux.

## What Changes

- Add an opt-in Ray Tracing toolbar toggle for live shadows and reflections while editing, including transform previews and animation.
- Detect a usable GPU/backend, explain unavailability inline, and retain the existing renderer on unsupported machines or recoverable backend failure.
- Render opaque and alpha-tested models and terrain with their existing materials, lights and environment; preserve picking, gizmos, camera look-through, fog and overlays.
- Add bounded asynchronous rendering and adaptive resolution/sample counts so interaction remains responsive. Improve quality when movement stops without requiring a still camera to display effects.
- Establish Metal on macOS (JNI bridge) and Vulkan on Windows/Linux (LWJGL bindings) as the initial backends, subject to a narrowly defined integration/performance gate on each platform.
- Put both behind an internal backend interface with a fixed selection rule (probe in order, the first usable backend wins, a JVM property forces a backend or turns the mode off) and a conformance test kit every backend must pass. The toggle names the active backend and GPU.
- Treat a missing Vulkan runtime, a GPU without the required features and a lost device (driver reset or timeout) as ordinary unavailability or recoverable failure, never as an IDE crash or freeze.
- Keep the toggle per open view and off by default. Switching it does not edit scene, project or asset files.

Out of scope: software ray tracing on unsupported GPUs, full path tracing/global illumination, baked lighting, image export, transparent refraction or transparent reflection geometry, new material editors, water rendering, replacing the default raster renderer, a public extension point for third-party backends, GLFW or any native window or swapchain, and compiling shaders at runtime.

## Capabilities

### New Capabilities

- `scene-raytracing`: Optional live ray-traced shadows and reflections, availability, quality bounds, editor integration and fallback.

### Modified Capabilities

- `scene-environment-lighting`: In Ray Tracing mode, PBR specular reflections include scene geometry and sample the existing HDR environment on misses; existing raster and diffuse environment behavior remains.

## Impact

- SceneViewPanel toolbar, SceneFileEditor wiring and SceneRenderer composition; SceneModels, SceneTerrains, ScenePreview and asset preparation supply geometry/material snapshots.
- A constructor-wired plain JVM rendering module with an internal backend interface, a Vulkan backend and a packaged native Metal bridge. Dependencies:
  - `lwjgl-vulkan` and `lwjgl-vma`, pinned to the project's `lwjglVersion`.
  - Prebuilt SPIR-V and a Metal library.
  - The native jars are the `lwjgl-vma` natives and the MoltenVK natives for macOS; Vulkan itself has no Windows/Linux natives.
  - Windows and Linux rely on the Vulkan loader and driver installed on the machine.
  Native build and loading must be verified for the supported macOS architectures and the Windows/Linux targets. No IntelliJ dependencies enter `core` or `gdx-model`.
- Reads existing `ecs.entities.*.components` RenderComponent asset references, PositionComponent transforms, LightComponent color/intensity/range/coneAngle/edgeSoftness, CameraComponent lens/direction and scene ambient/fog/skybox fields. Reads existing model material/texture and terrain/splat asset data, and project `mainCamera`. Writes none of these; no `.scene`, `.abss` or `meta.json` format changes.
- Coordinate with the `scene-shadows` main spec (from the archived `add-scene-shadows` change): its shadow maps remain the raster fallback. Its ray-tracing exclusion was that change's scope boundary, not a prohibition on this capability.
- Coordinate with the open change `design-review-refactor`, which splits `SceneRenderer` into view state, CPU-only queries and GL drawing with a per-frame snapshot. Ray composition hooks into that structure, whichever change lands first.
- Update architecture, Scene view documentation, user feature documentation and changelog with GPU support and fallback behavior.
