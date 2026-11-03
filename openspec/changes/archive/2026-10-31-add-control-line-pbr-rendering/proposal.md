# Proposal

## Why

Control Line's raster field is lit with a flat ambient colour and one sun, in display-referred colour: albedo textures are
used as stored (sRGB-encoded), lit as if they were linear, and written to the screen with no tone mapping or sRGB
encode. The PBR model shader, the sky-derived image-based light (`HdrEnvironment`) and an HDR tone map already exist in
`lib-gdx` and `lib-core`, but the game uses none of them, so metal planes reflect nothing, the terrain (most of the frame)
is lit by a different model than the planes, and bright sun or sky clips. The game is the shipped product and the
editor's view is not, so the game is where a physically based pipeline pays off first.

## What Changes

- Add an opt-in **output transform** (exposure, tone map, sRGB encode) as a shared GLSL include and a plain setting in `lib-gdx`,
  carried by `lib-render`'s frame inputs. Control Line turns it on; the editor does not, so the editor's scene view and its specs are unchanged.
- Light the game's **models with the sky**: build the field sky's `HdrEnvironment` and give the game's `Environment` an
  `EnvironmentLightAttribute`, replacing the flat ambient colour for models whose material is PBR.
- Put **terrain and foliage on the same lighting**: linearise albedo, irradiance-based ambient from the same environment,
  one sun with its shadow, fog matched to the sky.
- **Linearise colour inputs** (albedo and emissive textures and colours) in the shaders the game uses, so lighting is
  computed in linear space and encoded once at the end.
- Draw the **control lines as lit cable geometry** instead of 1 px `GL_LINES`, taking a polyline of points so a later
  change can bend it (physically based line dynamics are a separate change).
- Retune the field's exposure and sun/ambient values so the field still reads as a bright midday scene under the new
  pipeline (the scene's numbers are artistic multipliers, not measured units; this change records the convention).
- Out of scope: the editor's scene view and Play preview look, ray mode, line dynamics (sag, drag, stretch), new textures or
  models, a graphics-settings menu, post effects beyond the output transform (no bloom, SSAO, TAA), and any change to the
  native file formats.

Native files: none read or written differently. `.scene` / `.abss` / `meta.json` keep `format: "abyssus"` and
`formatVersion: 1`; the sky's existing `additional.lightsScene` flag is read as it is today and no new field is added. The
output transform and exposure are game settings in code, not document fields.

## Capabilities

### New Capabilities

- `control-line-pbr-rendering`: the game's physically based raster pipeline: linear-space lighting, sky image-based light on
  models, terrain and foliage on the same lighting, the output transform, and the lit cable for the control lines.
- `render-output-transform`: the shared opt-in output transform (exposure, tone map, sRGB encode): its contract, its
  off-by-default guarantee for other hosts, and its shared GLSL.

### Modified Capabilities

- `control-line-field-environment`: "Realistic daylight presentation" gains the pipeline requirements (sky light on models,
  tone-mapped output) and keeps the midday look, the soft shadows and the distinguishable surfaces.

The cable requirement is new, in `control-line-pbr-rendering`: no existing spec says how the lines are drawn.

## Impact

- `projects/lib-render`: carries the transform in `FrameInputs`; `terrain_game.{vert,frag}` (linear albedo, irradiance ambient, output
  transform); README.
- `projects/lib-gdx`: the `OutputTransform` setting, its attribute and the shared GLSL; `pbr.fragment.glsl` and `default.fragment.glsl` take the output transform and colour linearisation
  behind a define that is off for existing hosts; `ModelShaderProvider` selects it. No IntelliJ imports.
- `projects/lib-core`: foliage shader gets the same define; the sky's lighting lease is consumed by the game (API exists).
- `projects/app-game-control-line`: `FieldRenderer` (environment, lease, output settings, cable draw), `FieldScene`
  (exposure), scene values in `project/ControlLine`; tests; README.
- Tests: pure plans in `lib-render` (output transform maths), GL tests behind `-Dabyssus.glTests=true` (a lit sphere and the
  terrain against reference values, editor-off equivalence). Look and frame time are checked by hand and recorded.
- Rules that must keep passing: `checkNoEditorCode`, `checkShippedClasspath`, `lib-render:checkNoSingletons`,
  `RenderBoundaryTest`, `checkPackageCycles`; `Gdx.*` only inside the render thread; docs updated in the same change
  (`lib-render` README, the game's README, `docs/ai` if architecture notes change), then `scripts/check-docs.sh`.
- Risks: albedo linearisation changes the look of the whole field; 4x MSAA is on the default framebuffer, so the transform is
  applied per shader (not in a post pass) and the resolve averages encoded values; a blend-heavy foliage or cloud pass must
  encode exactly once.
