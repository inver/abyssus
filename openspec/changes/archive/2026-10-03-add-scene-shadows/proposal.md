# Proposal

## Why

Scene lights currently illuminate through solid objects, and spotlights behave as point lights. Shadows and editable spotlight beams will let users judge object placement and lighting in the scene view.

## What Changes

- Models and terrain cast and receive dynamic shadows from directional, point and spot lights within a bounded rendering budget.
- Spotlights illuminate a directional cone with a soft transition at its edge.
- Properties exposes per-spotlight full cone angle in degrees and edge softness in percent; edits are undoable, saved in the scene and restored on reopen.
- Shadowing applies independently to each light; ambient and HDR environment lighting remain unaffected.
- Preserve rendering and editor responsiveness when shadow resources fail or the light count exceeds the budget.
- Persist spotlight settings using verified Mundus equivalents where available; otherwise use the user-authorized Abyssus-specific light fields `coneAngle` and `edgeSoftness`. Document the extension and do not claim Mundus renders or preserves it.
- Out of scope: ray tracing, baked lighting, volumetric beams, contact shadows, adjustable shadow penumbra, per-object cast/receive switches and new light-creation actions.

## Capabilities

### New Capabilities

- `scene-shadows`: Dynamic shadows, per-light isolation, bounded allocation and graceful fallback.

### Modified Capabilities

- `scene-entity-lights`: True spot cones with adjustable soft edges and correct supported-light selection.
- `scene-component-editing`: Validated, undoable spotlight angle and softness edits persisted in the scene.
- `object-properties-panel`: Spotlight-specific angle and softness controls.

## Impact

SceneRenderer, SceneLighting, SceneContent, model shader attributes/providers and GLSL in gdx-model, TerrainShader and terrain GLSL, LightData/LightCodec, ComponentEditor, entity properties and localized labels. No IntelliJ dependencies enter core or gdx-model.

Reads existing TypeComponent.type, PositionComponent.localPosition/localRotation, LightComponent.light.color/intensity/range (and supported flat light representation). Writes spotlight angle/softness through editSceneJson, preserving unrelated numbers, keys and formatting. Proposed fallback keys are LightComponent.light.coneAngle (full angle in degrees) and LightComponent.light.edgeSoftness (fraction 0..1), or the equivalent flat component keys. No .abss or asset meta.json edits. This is an additive scene-format extension if no native equivalents exist, explicitly authorized by the user. Existing fields retain their meaning; Mundus support for added fields is unverified.

Reconcile add-light-entities, which excludes cone controls and proposes range editing; do not duplicate its entity-creation work. Current HDR lighting and extracted core assets must remain supported. Update sceneview docs, file-format docs and user feature descriptions during implementation.
