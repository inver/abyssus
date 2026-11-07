# Proposal

## Why

Modelled lamps in a scene (the emissive lamp mesh of the `model_warehouse_fbx_model_free` warehouse placed in
`Hangar.scene`, the `lamp_lens` of `model_airfield_street_lamp`) glow but light nothing: the scene lights come only from
light entities, which an author must place and aim by hand, one by one, at each lamp. Letting Abyssus read the lamp
positions off the model removes that guesswork and keeps the light exactly where the lamp is.

## What Changes

- Add a **Create Lights From Model** action for a model entity (right-click menu of the entity row in the Abyssus tree).
  It asks which material of the model marks the lamps (materials with an emissive color are listed first) and bakes **one
  point light entity per lamp** into the scene.
- A "lamp" is one connected piece of geometry of the chosen material (triangles that share welded vertices). The light sits at the
  piece's bounds centre in world space, so it follows the entity's position, rotation and scale and the model's own node transforms.
- Each new `LIGHT_POINT` entity gets the material's emissive color (white when it has none), a default intensity and a range
  derived from the lamp's size. All values are then edited like any other light.
- The lights are ordinary scene data (method "bake"): no new component, no runtime system, no change to the game or the
  renderer. Moving the model afterwards does not move the lights; running the action again adds new lights.
- The whole action is one undoable `editSceneDocument` command and is also exposed through the headless editing API.
- The material lister gains the material's emissive color (read from the model data, no GL, off the UI thread).

Out of scope: a live link between a model and its lights (derived lights that follow the model), replacing or
updating lights from an earlier run, splitting one fused mesh by anything other than connectivity (the aircraft hangar
model has a single material and a single mesh and cannot be handled), baking emissive textures, per-lamp spot cones,
and Add Light changes (Directional, Sun and Spot stay as they are).

## Capabilities

### New Capabilities
- `scene-lights-from-model`: creating point light entities from the lamp geometry of a placed model.

### Modified Capabilities
<!-- none: existing light requirements are unchanged; the new lights are ordinary LIGHT_POINT entities -->

## Impact

- `lib-core-editor`: new `modellights` package (pure geometry: welded connectivity clusters, bounds, light parameters,
  merging down to a cap) and a `SceneEdit`/`LightEntities` entry adding the point lights; `ModelMeshLister` reports material emissive;
  `HeadlessEditing` gains the call; strings in `AbyssusEditorBundle.properties`.
- `plugin-abyssus`: tree action and a material chooser dialog; strings in `AbyssusBundle.properties`.
- Native files: reads the scene entity's `RenderComponent.assetName` (and `meshId` when present), position, rotation and scale; writes
  new entities of type `LIGHT_POINT` with `NameComponent`, `PositionComponent` and `LightComponent`. Documents are validated
  with `AbyssusDocumentFormat` before the read and written pretty-printed by the existing printer; no format change and no
  `formatVersion` bump.
- Docs: `docs/ai/file-formats.md` (nothing new in the format; note only if wording needs it), `docs/ai/glossary.md`,
  `projects/lib-core-editor/README.md`, the projectView README.
