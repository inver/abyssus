# Proposal

## Why

Abyssus can display terrain and skies but cannot add bodies of water, leaving seas and lakes absent from scene composition. Users need realistic water viewed from above, with reflections, visible shallows and foam following the terrain shoreline.

## What Changes

- Add Water offers Lake and Sea presets from the Scene view toolbar and a scene's tree menu. Both create finite horizontal surfaces; Sea starts larger and with stronger waves. Multiple surfaces can have independent levels and settings.
- Add selectable water rows, move handles, visibility, rename and removal, and properties for position/water level, width, length, tint, clarity, wave amplitude/wavelength/speed and foam amount/width. Rotation and Drop do not apply to water.
- Render animated water with reflected scene geometry and sky, wave-distorted refraction, depth-dependent absorption revealing shallow terrain, and animated shoreline foam. Terrain protruding above the level defines land and islands without a boundary editor.
- Save an explicitly Abyssus-only extension at `abyssus.waterSurfaces`, keyed by stable surface ids. This is a scoped exception to the usual Mundus-only field constraint, authorized by the user's Abyssus-only choice. No Mundus loading or round-trip persistence is promised.
- Existing Mundus fields are read, not changed: `ecs.entities.*.components.PositionComponent`, `RenderComponent.renderable.asset`, scene lights, fog, ambient settings and `skyboxEnabled`/`skyboxName`; terrain `meta.json.additional` and height data feed existing loading. Water operations write only `abyssus.waterSurfaces.<id>` fields through `editSceneJson`. `.abss`, asset `meta.json`, ECS entities/archetypes/component identifiers and existing keys remain unchanged.
- Out of scope: underwater rendering, gameplay/runtime export, custom shoreline drawing, infinite oceans, rivers, fluid simulation, buoyancy, breaking waves, water-to-water reflections, transparent-model refraction, and water casting or receiving geometry shadows in this first version. Shadowed opaque scene geometry still appears in reflection/refraction when scene shadows are available.

## Capabilities

### New Capabilities

- `scene-water`: persistent water surfaces, authoring, selection/movement and realistic above-water rendering, including bounded rendering cost and isolated failures.

### Modified Capabilities

- `abyssus-project-view`: dedicated water rows and visibility controls for the scene extension.
- `object-properties-panel`: editable water properties when a water row is selected.

## Impact

- Plugin: scene DTO/content reading, project tree, panel state and properties, toolbar/menu registration, selection identity, move previews/write-back, and SceneRenderer pass orchestration.
- `core`: constructor-wired water settings, validation/math and drawable/shader resources, still a plain JVM library; no singleton state. Water is procedural scene content, not a new asset type.
- `gdx-model`: reusable model clipping/depth-pass support for its 32-bit meshes, including skinning and alpha-tested cutouts; no IntelliJ imports.
- Rendering: per-view offscreen scene color/depth and bounded planar reflection targets, explicit resource lifecycle and fallback. No new third-party dependency is expected.
- Coordinate render-pass extraction with `add-scene-shadows`; reuse the loaded frame snapshot rather than duplicating animation updates. Preserve `add-scene-object-drop`'s existing ground rules: water is neither a drop target nor droppable.
- Update file-format/architecture docs, sceneview/core package notes, README and CHANGELOG; add headless editing/math checks, GL rendering checks and numbered sandbox-IDE acceptance checks.
