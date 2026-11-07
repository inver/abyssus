# Proposal

## Why

Control Line's hangar (`Hangar.scene`, plane select) places `model_warehouse_fbx_model_free`, the same model shown on Sketchfab
(https://sketchfab.com/3d-models/warehouse-fbx-model-free-daa7fd3ff88945298d00045ca40a4c03, CC-BY, Nicholas-3D). There it reads
as a dark arched shed lit by rows of glowing tube lamps over wet, reflective concrete. In the game it reads as a flatly lit grey
box. The game lights every scene with one sun and a flat ambient: the hangar's 64 lamp point lights (baked by
`add-lights-from-model-meshes`) and its 4 spot lights light nothing, and a scene without a directional light gets a default sun.
The lamps' emissive strength is lost on import, so they glow dim blue. Nothing blooms, the metallic floor has nothing to
reflect without a sky, and corners lack contact shadow.

## What Changes

- The game lights models with the scene's **point and spot light entities**, with the same light values the editor uses, in
  addition to the sun and ambient or sky light. A scene **without a directional light has no sun** (no light, no sun shadow)
  instead of a default sun 45° up. **BREAKING** only for scenes that relied on the default sun; the bundled Field has its own sun.
- In a scene without a sun, its **lights cast shadows**: its spot lights (up to four, the brightest first), so the table and the
  planes on it shadow the floor, and its point lights nearest the scene camera (up to eight), so the warehouse's pillars and beams
  shadow its walls and floor. The point lights' shadows see only the static models and are drawn once per scene.
- glTF **emissive strength** (`KHR_materials_emissive_strength`) is kept on model import and honoured when the model is
  drawn, in the editor and the game, so emissive surfaces can be brighter than their colour's 0–1 range.
- The game draws each frame into an **HDR target** and finishes it in **one post pass**: bloom, then exposure, tone map,
  vignette and grade, then sRGB encode. The output transform moves from each shader into that pass for game scenes. A display-referred sky
  still shows as it is. On a context that cannot run it, the game draws as it does today and logs why.
- A scene without a lighting sky gets a **reflection probe** captured from the scene itself after it loads. Metallic and smooth
  surfaces (the hangar's wet concrete) reflect the lit interior, parallax-corrected to the scene's bounds.
- **Ambient occlusion** darkens creases and contact areas in the game.
- Game-side settings switch bloom, AO and vignette, and the display settings menu gets a quality toggle for them.
- `Hangar.scene` is retuned through the editor or the headless editing API (lower ambient, lamp intensities, the manual spots kept or
  removed) so plane select reads like the reference. The warehouse asset is re-imported to pick up its emissive strength.

Native files: the change reads the existing scene fields `ambientLight`, `ambientLightEnabled`, `skyboxEnabled`, light entities'
`LightComponent.light` (`color`, `intensity`, `range`, `coneAngle`, `edgeSoftness`) and `PositionComponent`, validated with
`AbyssusDocumentFormat` through the existing scene loading. It adds **no** scene, project or meta field and no format version
change. Model import writes `KHR_materials_emissive_strength` into the asset's `model.glb` (glTF data, not a native document) and stops
listing it under `skipped` in `source.json`.

Out of scope, though the reference shows it or it is close by:
- Screen-space reflections, real-time planar reflections and puddle masks. Puddles belong to the open change
  `add-after-rain-weather`; this change only gives such surfaces something to reflect.
- Shadows from more than eight point lights or four spot lights, from moving models in point lights' shadows, local light shadows in a
  scene that has a sun, and volumetric light shafts.
- Lighting terrain and foliage with point and spot lights. The game terrain stays lit by the sun only.
- Authored post-processing or probe settings in the scene file, a probe entity, and editor-view bloom or AO.
- Changes to ray mode, gameplay, physics or the editor scene view's lighting (apart from honouring emissive strength).

## Capabilities

### New Capabilities
- `control-line-post-processing`: the game's HDR frame, bloom, ambient occlusion, vignette and grade, the single output
  transform pass, its fallback and its settings.

### Modified Capabilities
- `control-line-pbr-rendering`: models are lit by the scene's point and spot lights; a scene without a directional light has no
  sun; a scene without a lighting sky reflects itself through a captured probe; the output transform is applied by the post pass.
- `render-output-transform`: the transform may be applied once by a host's final pass over a linear HDR frame instead of at the
  end of each shader. "Once" and "display-referred sky drawn as it is" still hold.
- `model-import`: glTF emissive strength is kept rather than reported as left out.
- `scene-model-rendering`: drawn models honour their material's emissive strength.

## Impact

- `app-game-control-line`: `FieldScene` (local lights, no default sun), `FieldRenderer` (light culling per instance, HDR target,
  probe, post), `FieldRenderSettings`, `FrameProbe` (`post` span), display settings, `Hangar.scene` content, README.
- `lib-render`: new `post` package (HDR target, bloom, SSAO, composite) and reflection probe capture with their GLSL;
  `LightProfile` gains a game profile with local lights; README.
- `lib-gdx`: a linear-target flag for model, foliage and terrain programs (lit and decoded, not transformed); a box-projected
  environment option in `pbr.fragment.glsl`; emissive strength in the material attributes, in the Assimp material reading and in
  `GltfWriter`.
- `lib-core`: the pure light-entity conversion moves here from `lib-core-editor`'s `SceneLighting` so editor and game agree.
- `lib-core-editor`: model import keeps emissive strength.
- Boundaries: no new dependency. `checkShippedClasspath`, `checkNoEditorCode`, `checkNoSingletons` and `RenderBoundaryTest` hold.
- Docs: `projects/lib-render/README.md`, `projects/app-game-control-line/README.md`, `docs/ai/architecture.md`; the model-import part
  of the user docs if it lists skipped extensions. Credit the warehouse model's author where the game lists asset credits.
