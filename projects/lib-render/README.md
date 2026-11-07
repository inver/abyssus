# lib-render

Rendering mechanics the editor and games share, as a plain JVM library on `lib-core` and `lib-gdx`: root package
`net.nevinsky.abyssus.lib.render`. Constructor-wired (no `object` or `companion object`: `checkNoSingletons`), and a shipped
library (`checkNoEditorCode`). It holds no IntelliJ, Swing, AWT, editor, physics or ray-backend type
(`RenderBoundaryTest` checks its classpath and imports). Required behavior of the hosts stays in their own specs.

It is not a scene renderer. Each host keeps its scene, its passes and its resources (the editor's grid, gizmos and ray
presentation; a game's lines and hidden pilot) and adapts its own types at the boundary: the plugin turns its `LightSet` and
`FogParams` into the inputs below (`TerrainShader`), Control Line builds them from its `FieldScene` (`FieldRenderer`).

| Package | What |
|---|---|
| `light` | `RenderLights` (directional, point, spot), `LightProfile` (named policies `EDITOR_MULTI_LIGHT`, `GAME_SINGLE_SUN` and `GAME_SCENE_LIGHTS` with their budgets), `packLights`, which lays lights out for uniform arrays, and `selectLocalLights`, which picks the point and spot lights that reach one model's bounds, strongest first (`localLightWeight` repeats the model shaders' falloff). No GL |
| `frame` | `FrameInputs` (camera matrices and position, ambient, `RenderFog`, the lights cut to the profile, and an optional `OutputTransform` from `lib-gdx`) and `RenderFog`. No GL |
| `shadow` | `sunShadowCamera` / `sunShadowBias` and `cameraUp` (camera math; no GL), `DepthSubmission` (draws casters through the host's `DepthShaders`), and `GlRenderTarget` / `ShadowGlState` (packed-depth target rendering and caller-state restoration in the current GL context) |
| `hostframe` | `HostFramePresenter`: uploads a host-produced frame (RGBA floats and GL window depth, e.g. a ray-traced image) into two float textures and draws it as a fullscreen triangle writing `gl_FragDepth`; GLSL is `ray-frame.{vert,frag}` in `src/main/resources/shader/render`. It takes sizes and fill callbacks, so it knows no ray type. GL thread only |
| `post` | A host frame in linear HDR and its final pass: `HdrFrameTarget` (multisampled RGBA16F colour and depth, resolved by blit to textures), `BloomPass` (thresholdless 13-tap / tent mip chain, `bloomPlan`), `SsaoPass` (half-size hemisphere ambient occlusion from depth with a depth-aware blur, `ssaoKernel`), `CompositePass` (bloom, occlusion, the `lib-gdx` output transform, vignette and `ColorGrade`, blended over the host's display-referred background by coverage) and `ReflectionProbe` (a scene seen from one point into a cube over a few frames, prefiltered by `lib-core`'s `HdrEnvironmentBuild`). LWJGL GL 3.2 calls; GL thread only |
| `terrain` | `TerrainPass` with `TerrainVariant`: the terrain program's binding and drawing. `TerrainLighting.Editor` (several lights, HDR sky, shadow atlas, cloud shadow map, clip plane) and `TerrainLighting.Sun` (one sun, one shadow map, an optional sky irradiance cube) are the inputs only their variant understands; the GLSL is `terrain_editor.{vert,frag}` and `terrain_game.{vert,frag}` in `src/main/resources/shader/render` |

## Ownership

Passes borrow what they are handed and dispose only what they create. `TerrainPass` makes and disposes its program and, unless
the host lends a `blank` texture, the 1x1 white stand-in for a missing splat layer; every texture, map and atlas in its inputs
stays the host's (`TerrainPassGlTest` checks that a lent texture is not disposed). `DepthSubmission` borrows the depth shaders and
the target. Storage of assets stays outside: assets come from the host's `AssetStorage`.

`GlRenderTarget` borrows a framebuffer and restores the caller's GL state, including winding, after success or failure.
Hosts may override its viewport/scissor step for atlas tiles. `ShadowGlState` can also surround a host's own draw,
as the editor's cloud shadow pass does. Neither helper allocates or disposes targets or shaders.

The `post` passes own their targets and programs and borrow the textures they are given; each restores the framebuffer,
viewport, program, texture bindings and switches it changes (`CompositePassGlTest`, `BloomPassGlTest`, `SsaoPassGlTest`,
`ReflectionProbeGlTest`). The environment a `ReflectionProbe` returns is the caller's to dispose. With `FrameInputs.linearTarget`
the game terrain program writes linear light for such a final pass instead of ending in the output transform.

## Variants

The two terrain programs differ on purpose: the editor lights with several lights, the sky's irradiance and the shadow atlas and
fogs with a quadratic ramp; a game lights with one sun, one shadow map and `1 - exp(-(d * density)^gradient)` fog. They share the
mechanics (program, camera, ambient, per-terrain transform and splat layers) and, through `lib-gdx`'s `shadow_visibility.glsl`,
the editor program's shadow functions with the model shaders. With one sun, no shadow and no fog the two agree to 2/255
(`TerrainPassGlTest`, opt-in).

### Output transform

A host that lights in linear space sets `FrameInputs.output` (an `OutputTransform`: exposure, tone map, sRGB encode, from
`lib-gdx`). The game terrain program then decodes its layer textures from sRGB, mixes them in linear light, takes its
ambient from `TerrainLighting.Sun.irradiance` (a sky's irradiance cube, which the host keeps and disposes) instead of the flat
ambient when one is given, fogs with the decoded fog colour, and ends in the transform. The program is one program for both
cases: `u_exposure` of 0 (no `output`) runs the code it always ran, so a host that never sets it is unchanged
(`TerrainPassGlTest`). The editor program ignores `output`. `TerrainOutputTransformGlTest` checks a terrain against a model
of equal albedo (within 5%), the shadow, the irradiance and the maths against the Kotlin reference.

## Tests

`./gradlew :lib-render:test` (pure plans, the boundary test); add `-Dabyssus.glTests=true` for `TerrainPassGlTest` and
`GlRenderTargetGlTest` (caller-state restoration and a second pass after failure).
