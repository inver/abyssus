# Proposal

## Why

Creators and players need an after-rain appearance with wet ground and puddles in both Abyssus and Control Line. Existing weather presets snapshot clouds, and lake/sea rendering does not supply automatic ground puddles or game integration.

## What Changes

- Add a saved scene Weather choice: Default or After the rain. Default retains the existing appearance.
- Automatically produce repeatable irregular puddles and wet shading on suitable terrain and static ground models, including Control Line's asphalt flying circle. No painting or individual puddle authoring is required.
- Add a keyboard- and mouse-accessible weather choice to Control Line's authored main menu. It initially follows the scene, then overrides it for the current application session, including retries.
- Preserve authored sky, clouds, lights and fog. Weather is visual only: no changes to flight, grip, collisions, scoring or takeoff.
- Store only `abyssus.weather.mode` (`default` or `afterRain`) in native version 1 scenes. Validate documents with `AbyssusDocumentFormat`; preserve unknown extension data, unrelated text and omitted defaults. Opening a scene and using the game selector write nothing. No project/asset format or version changes.
- Render through shared runtime-safe contracts and mechanics with bounded reflections and resource ownership. Ray tracing uses an explained raster fallback while after-rain rendering is active; native wet-material tracing is outside this change.

Out of scope: active rainfall, weather-driven sky changes, drying over time, hydrology, puddle painting, new colliders, wet flight physics, splash effects, new weather assets, and game persistence across application restarts.

## Capabilities

### New Capabilities

- `scene-after-rain`: Scene weather persistence/editing, automatic puddle placement, wet appearance, editor/game consistency and bounded rendering.

### Modified Capabilities

- `scene-environment-lighting`: Allow wet ground and puddle sky reflections when after-rain weather is active while retaining the default lighting contract.
- `control-line-field-environment`: Keep the dry field as the default and support an optional wet ground presentation without changing the authored atmosphere or gameplay.
- `control-line-game-flow`: Add a session weather selector to the authored main menu while preserving existing game navigation and controls.

## Impact

`lib-core` owns runtime weather settings/read validation; `lib-core-editor` owns formatting-preserving edits. `lib-render` owns host-neutral receiver planning and puddle rendering mechanics, with any reusable model shader hooks in `lib-gdx`. The plugin integrates scene Properties, rendering lifecycle and ray-mode admission. Control Line integrates `FieldScene`, `FieldRenderer`, `GameUiModel`/contract and its authored screens asset. Tests cover native round trips, determinism, receiver exclusion, session precedence, rendering, failure recovery and shipped-module boundaries. Documentation includes file formats, affected module/scene-view READMEs, user documentation and release notes.

The working tree already contains the shared-render extraction and other unrelated work. Build on its current constructor-wired boundaries without reverting it. Coordinate the menu delta with `add-f2b-pattern-mode`, and the raster fallback with the active ray-tracing changes; implementation tasks explicitly reconcile those deltas.
