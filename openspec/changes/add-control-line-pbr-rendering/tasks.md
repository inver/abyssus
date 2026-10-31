# Tasks

## 1. Baseline and measurement

- [x] 1.1 (baseline superseded by 7.2: the harness drew outside its target) Record the pre-change steady frame time (the harness measures CPU submission time; no GPU timer exists) of the pilot view (hardware, resolution, loading excluded) and capture reference screenshots of the pilot, plane-select and flying views with the game's existing screenshot tooling; verify the numbers and image paths are written in the game's README "Rendering" notes.
- [x] 1.2 Check which type each of the field's sky assets (`skybox_physical`, `weather_skybox_physical`) is, whether `lightsScene` is set, and what colour space its draw writes; verify the design's sky table (decision 4) names each type and amend the design if a type is missing. Result: `skybox_physical` is a procedural sky with a custom tone-mapped, gamma-encoded `sky.frag` and no `lightsScene`; `weather_skybox_physical` is clouds. Design decision 4, the output-transform spec and tasks 4.1 and 5.2 amended.

## 2. Output transform in `lib-gdx`

- [x] 2.1 Add `OutputTransform` (exposure, tone map) with a pure Kotlin reference of the tone map and sRGB encode/decode; verify `./gradlew :lib-gdx:test --tests '*OutputTransformTest'` covers 0 maps to 0, monotonic, 0.18 near 0.18, sRGB round trip, and exposure scaling.
- [x] 2.2 Choose the tone map curve by looking at sun-lit asphalt and sky; verify the choice and a one-line reason are written in the design's open question and the test pins the constants.
- [x] 2.3 Add `shader/shared/output_transform.glsl` and `OutputTransformAttribute`, and gate `outputTransformFlag` in `ModelShader` / `PbrModelShader` (decode of diffuse, emissive, vertex colour, fog colour; the transform as the last write); verify `ShaderDefinesTest` shows the flag only with the attribute and `./gradlew :lib-gdx:test` passes.
- [x] 2.4 GL test (`-Dabyssus.glTests=true`): a lit sphere, flag off equals the stored reference image within 1/255, flag on equals the Kotlin reference within 2/255 including the 0.5 albedo decode (0.2140) and data textures untouched; verify with `./gradlew :lib-gdx:test -Dabyssus.glTests=true --tests '*OutputTransform*'`.
- [x] 2.5 Update `projects/lib-gdx` notes and the shader headers that say "no gamma / tone mapping"; verify `scripts/check-docs.sh`.

## 3. Terrain in `lib-render`

- [x] 3.1 Carry the transform and an optional irradiance cube in `FrameInputs` / `TerrainLighting.Sun`; verify `./gradlew :lib-render:test` (plans, `RenderBoundaryTest`, `checkNoSingletons`) passes with the new fields defaulting to off.
- [x] 3.2 Update `terrain_game.frag`: decode layer colours and mix in linear, irradiance ambient when a cube is given, fog colour decode, include and transform behind the flag; verify with `./gradlew :lib-render:test -Dabyssus.glTests=true --tests '*TerrainPassGlTest*'` that flag off still agrees with the editor program to 2/255.
- [x] 3.3 GL parity test: a flat terrain and a grey diffuse model of equal albedo, same sun, shadow and ambient, flag on, within 5% linear brightness; verify the test passes and a grass-in-shadow case is darker than lit grass.
- [x] 3.4 Update the `lib-render` README (variants, ownership of the cube, the flag); verify `scripts/check-docs.sh`.

## 4. Sky and foliage in `lib-core`

- [x] 4.1 Leave the sky pass untransformed (the field's `skybox_physical` writes display colour itself) and record in the `lib-core` README that skies write display colour and the game's transform does not apply to them; verify `./gradlew :lib-core:test` passes. The pixel check that a sky-only pixel is unchanged by the transform needs the game's renderer and is part of 5.1.
- [x] 4.2 Check that the instanced foliage programs compile with and without the flag and render the same as non-instanced ones; verify with the GL test of one foliage chunk (`-Dabyssus.glTests=true`) and `./gradlew :lib-gdx:test --tests '*InstancedModelShaderGlTest*'`.
- [x] 4.3 Run `./gradlew checkNoEditorCode :lib-core:checkNoSingletons`; verify both pass.

## 5. Wire the game

- [x] 5.1 Add `FieldRenderSettings` (exposure, output transform on/off) and use it in `FieldRenderer`: set `OutputTransformAttribute` on the environment, pass the transform to `TerrainPass` and the sky; verify a renderer test in `app-game-control-line` shows the environment carries the attribute with the setting on and not with it off, and (GL test) that a sky-only pixel of a drawn frame is identical with the transform on and off.
- [x] 5.2 Set `"lightsScene": true` in `skybox_physical/meta.json` (a one-key edit that keeps the file's layout), then lease the sky's `HdrEnvironment` per scene, call `updateLighting` each frame, set `EnvironmentLightAttribute` on models and the cube on terrain, close the lease with the scene; verify a GL test that a metallic sphere's colour differs under sky light from the flat ambient, and a not-lit-sky scene uses the flat ambient without error.
- [x] 5.3 Log once and draw as before when the context cannot run the pipeline; verify with a test on the capability check and `./gradlew :app-game-control-line:checkShippedClasspath`.
- [ ] 5.4 Confirm ray mode (`runRay`) is untouched: verify `./gradlew :app-game-control-line:test` includes its existing ray tests passing and the raster pipeline does not run when the ray view is active.

## 6. Cable

- [x] 6.1 Implement `CableGeometry` (polyline, world radius, minimum pixel radius from distance, fov and viewport height, 6-sided tube, smooth joints) as GL-free code; verify `./gradlew :app-game-control-line:test --tests '*CableGeometryTest*'` for vertex count, radius rule, endpoints on the polyline, and normals unit length.
- [x] 6.2 Add `CableMesh` (fixed-capacity dynamic mesh, no per-frame allocation) and draw it through the model batch with a PBR steel material; extend `FieldView.draw` with `cables` and have `ControlLineGame` pass the two lines as cables; verify a GL test that the cable's endpoints are where the leadouts are and it is hidden behind terrain.
- [x] 6.3 Keep the debrief and guide tracks and ray mode on `GL_LINES`; verify the existing debrief tests pass and a flight recorded before and after gives the same track and score (`FlightTest`, `GameFlowTest`).

## 7. Look pass and documentation

- [x] 7.1 Pick the default exposure and retune the sun, sky and ambient values in `project/ControlLine` through Abyssus (a copy of the project while editing, never the committed folder) so the spec's no-clipping and distinguishable-surfaces scenarios hold; verify the screenshot measurements for those scenarios are recorded and the scene tests (`./gradlew :app-game-control-line:test`) still pass.
- [x] 7.2 Re-base the perf and pixel harnesses (`PerfWorkloadsGlTest`, `PixelEvidenceGlTest`) on the default framebuffer in a `TestGl` window of their size (the 1.1 baseline measured a frame that was drawn outside its target), take the `legacy` baseline and the `pbr` numbers with the same harness, and record both against the 20% budget; verify the README states hardware, resolution, both numbers and whether the budget holds.
- [x] 7.3 Update the game's README (Rendering section: pipeline, exposure, tone map, MSAA note, ray mode difference), `docs/ai/architecture.md` if its render notes change, and the `AGENTS.md` module line for `lib-gdx` / `lib-render` only if wrong; verify `scripts/check-docs.sh`.
- [ ] 7.4 Manual check in `./gradlew :app-game-control-line:run`, with each result noted: (1) metal planes show the sky, (2) ground, planes and trees look lit by one sun, (3) no white-clipped asphalt, (4) cable visible at 20 m and thin close up, (5) horizon fog matches the sky, (6) menus and HUD colours unchanged, (7) the editor scene view in `runIde` looks as before.

## 8. Final

- [ ] 8.1 Run `./gradlew check -Pabyssus.requireShaders=true` and `scripts/check-docs.sh`, plus `./gradlew :lib-gdx:test :lib-render:test :app-game-control-line:test -Dabyssus.glTests=true` on a machine with a display; verify all pass and attach the output summary.
