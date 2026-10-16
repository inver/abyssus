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
| `light` | `RenderLights` (directional, point, spot), `LightProfile` (named policies `EDITOR_MULTI_LIGHT` and `GAME_SINGLE_SUN` with their budgets) and `packLights`, which lays lights out for uniform arrays. No GL |
| `frame` | `FrameInputs` (camera matrices and position, ambient, `RenderFog`, the lights cut to the profile) and `RenderFog`. No GL |
| `shadow` | `sunShadowCamera` / `sunShadowBias` (a single sun's texel-snapped orthographic camera; no GL) and `DepthSubmission`, which draws casters through the host's depth shaders (`DepthShaders`) into whatever target the host bound |
| `terrain` | `TerrainPass` with `TerrainVariant`: the terrain program's binding and drawing. `TerrainLighting.Editor` (several lights, HDR sky, shadow atlas, cloud shadow map, clip plane) and `TerrainLighting.Sun` (one sun, one shadow map) are the inputs only their variant understands; the GLSL is `terrain_editor.{vert,frag}` and `terrain_game.{vert,frag}` in `src/main/resources/shader/render` |

## Ownership

Passes borrow what they are handed and dispose only what they create. `TerrainPass` makes and disposes its program and, unless
the host lends a `blank` texture, the 1x1 white stand-in for a missing splat layer; every texture, map and atlas in its inputs
stays the host's (`TerrainPassGlTest` checks that a lent texture is not disposed). `DepthSubmission` borrows the depth shaders and
the target. Storage of assets stays outside: assets come from the host's `AssetStorage`.

## Variants

The two terrain programs differ on purpose: the editor lights with several lights, the sky's irradiance and the shadow atlas and
fogs with a quadratic ramp; a game lights with one sun, one shadow map and `1 - exp(-(d * density)^gradient)` fog. They share the
mechanics (program, camera, ambient, per-terrain transform and splat layers) and, through `lib-gdx`'s `shadow_visibility.glsl`,
the editor program's shadow functions with the model shaders. With one sun, no shadow and no fog the two agree to 2/255
(`TerrainPassGlTest`, opt-in).

## Tests

`./gradlew :lib-render:test` (pure plans, the boundary test); add `-Dabyssus.glTests=true` for `TerrainPassGlTest`.
