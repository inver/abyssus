# Tasks

GL tests run with `-Dabyssus.glTests=true` on a machine with a display. Single plugin tests run as `./gradlew :plugin-abyssus:test --tests '<class>'`.

## 1. Baseline and spec repair

- [x] 1.1 Repair `openspec/specs/render-output-transform/spec.md`: replace its `## ADDED Requirements` header with `## Requirements`, so the delta of this change can be archived. Verify `openspec validate improve-control-line-interior-rendering` prints no "Archive would refuse" note.
- [x] 1.2 Record the "before" evidence: the perf report for the pilot view and a new hangar workload, and a hangar pixel capture from its camera (`PerfWorkloadsGlTest`, `PixelEvidenceGlTest` with `-Dabyssus.perf.pixels=record`). Store them under `docs/perf/control-line-interior/` with the Sketchfab reference credited (CC-BY, Nicholas-3D). Verify the files exist and the hangar workload runs in `PerfWorkloadsGlTest`.

## 2. Scene lights in the game

- [x] 2.1 Move the pure light-entity conversion of `lightSetOf` (usable filter, colour × intensity, cone half-angle and softness, nearest-first order) from `lib-core-editor` `scene/SceneLighting.kt` to `lib-core`; `LightSet` calls it. Verify `./gradlew :lib-core-editor:test --tests '*SceneLightingTest'` passes unchanged and a new `lib-core` test covers the moved function.
- [x] 2.2 Add `LightProfile.GAME_SCENE_LIGHTS` and the pure `LocalLightSelection` (range sphere vs bounds, rank by contribution, cap 64 point + 16 spot) in `lib-render/light`. Verify with `./gradlew :lib-render:test --tests '*LocalLightSelectionTest'` (beyond range excluded, strongest kept, stable order).
- [x] 2.3 `FieldScene`: list active point and spot lights through the `lib-core` conversion; make the sun nullable (no directional light → no sun). Verify with `./gradlew :app-game-control-line:test --tests '*FieldSceneLightsTest'` on the bundled Hangar (64 points, 4 spots, no sun) and the Field (its sun kept).
- [x] 2.4 `FieldRenderer`: draw each instance with a per-instance `Environment` holding its selected lights; skip `FieldShadows` and use a zero terrain sun when there is no sun. Verify with a new `FieldLocalLightsGlTest` (table under a lamp brighter than without points; a range-2 m light 5 m above leaves the model unchanged; spot cone inside/outside; no shadow map rendered in the hangar) and `FieldRendererGlTest`, `FieldShadowsGlTest` still passing.
- [x] 2.5 Update `projects/app-game-control-line/README.md` (lights, no default sun, BREAKING note) and `projects/lib-render/README.md` (`GAME_SCENE_LIGHTS`, `LocalLightSelection`). Verify with `scripts/check-docs.sh`.

## 3. Emissive strength

- [x] 3.1 `lib-gdx`: add `PbrAttributes.EmissiveIntensity` and `u_emissiveIntensity` under `emissiveIntensityFlag` in `pbr.fragment.glsl` and `default.fragment.glsl`; read `$mat.emissiveIntensity` in `assimp/MaterialProcessor.kt`; write `KHR_materials_emissive_strength` (and `extensionsUsed`) in `GltfWriter` when it is not 1. Verify with `./gradlew :lib-gdx:test --tests '*GltfWriterTest'` (round trip keeps factor and strength 10) and a GL case in `OutputTransformGlTest` (colour 0.2 × 5 equals colour 1 × 1; a model without strength is unchanged within 1/255).
- [x] 3.2 `lib-core-editor` model import: stop listing `KHR_materials_emissive_strength` as left out. Verify with `./gradlew :lib-core-editor:test --tests '*ModelImportTest'` (a GLB with strength 10: not in the report or `skipped`, kept in `model.glb`) and `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.ModelImportFormTest'`.
- [x] 3.3 Re-import `model_warehouse_fbx_model_free` from its GLB into the bundled Control Line project, using a headless import or the IDE on a copy of the project with the result copied back, so `model.glb` carries the strength and `source.json` no longer lists it. Verify by reading the GLB's material JSON (the `Emissive` material has the extension) and that `./gradlew :app-game-control-line:test` passes.
- [x] 3.4 runIde check 1: in a copy of the Control Line project, open `Hangar.scene`; the warehouse tubes glow brighter than before and the other models look unchanged.

## 4. HDR target, linear-target shaders, composite

- [x] 4.1 `lib-gdx`: add `LinearTargetAttribute` / `linearTargetFlag` (decode as under `outputTransformFlag`, skip `outputTransform()`) for model, foliage and terrain programs, and to `TerrainPass` through `FrameInputs`. Verify with a GL equivalence test: editor programs (no flag) are pixel-equal within 1/255 to before, and the per-shader transform equals linear plus the CPU `OutputTransform.apply` within 2/255.
- [x] 4.2 `lib-render` `post`: `HdrFrameTarget` (4x MSAA RGBA16F + DEPTH24_STENCIL8, blit resolve to textures, resize) and `CompositePass` (coverage-alpha blend over the sky, exposure, `output_transform.glsl` tone map, vignette, grade, encode). GLSL lives in `src/main/resources/shader/render`. Verify with `./gradlew :lib-render:test` (`RenderBoundaryTest`, `checkNoSingletons`) and a `CompositePassGlTest` (an uncovered sky pixel unchanged within 1/255; a lit quad within 2/255 of the per-shader path; resize keeps working; GL state restored).
- [x] 4.3 `FieldRenderer`: sky on the default framebuffer, then the surfaces and lines into the target, then resolve and composite; the UI after it. Add `FrameSpan.POST`; extend `FieldRenderSettings` with `post: PostSettings?` and fall back to the per-shader path, logging once, when float targets or FBO completeness are missing. Verify with `FieldPipelineGlTest` (Field with all effects off within 2/255 of the plain path), `FrameProbesGlTest` (`post` span listed), `FieldPipelineGlTest.whatIsDrawnAfterTheFieldIsNotVignetted` (UI drawn after the field unaffected by the vignette) and `./gradlew :app-game-control-line:test --tests '*FieldRenderSettingsTest'` (fallback selection).

## 5. Bloom

- [x] 5.1 `lib-render` `post.BloomPass`: a 5-level 13-tap down / tent up chain with Karis averaging on the first level, mixed at `bloomStrength` before exposure; a pure mip-size plan. Verify with `./gradlew :lib-render:test --tests '*BloomPlanTest'` and a `BloomPassGlTest` (an emissive quad's surroundings brighter; far pixels within 2/255; an even frame blooms to its own light within 5%; a sharp spike is spread, not amplified).
- [x] 5.2 Wire bloom into `FieldRenderer`'s composite, including display-space glow over uncovered sky. Verify with `FieldLocalLightsGlTest.theEmissiveStripsBloom` (the wall below an emissive strip brighter with bloom on, the far floor unchanged).

## 6. Reflection probe

- [x] 6.1 Add the Kotlin reference for box-projected reflection and `envBoxFlag` / `ProbeBoxAttribute` in `pbr.fragment.glsl`. Verify with `./gradlew :lib-gdx:test --tests '*BoxProjectionTest'` (rays from inside meet the right wall or roof, axis-aligned rays do not divide by zero); the shader side is checked by the hangar case of 6.3.
- [x] 6.2 `lib-render` `ReflectionProbe`: capture six 128 px linear faces of static models from a position over three frames, prefilter through `HdrEnvironmentBuild(CubeSource)`, and dispose what it creates. Verify with a `ReflectionProbeGlTest` (cube faces not black in a lit box; no leaks after dispose; failure leaves no environment).
- [x] 6.3 `FieldRenderer`: capture once per scene without a lighting sky after `loaded`, set it with `setSkyLight` plus the box attribute, leave planes and the pilot out, and use the flat ambient until it is ready. Verify with `FieldLocalLightsGlTest.theHangarIsLitByAProbeOfItself` (lit by the probe, taken at the hangar camera, its box around the room down to the floor, the floor changed from the flat ambient; the reflection staying put is the box projection of 6.1) and `FieldPipelineGlTest` (the Field takes no probe).

## 7. Ambient occlusion, vignette, grade, settings, hangar retune

- [x] 7.1 `lib-render` `post.SsaoPass`: half-res, 12 samples, depth-reconstructed normals, depth-aware blur, faded above luminance 4. Verify with an `SsaoPassGlTest` (corner fixture darker near the wall; open floor within 2/255; emissive quad unchanged).
- [x] 7.2 Wire AO, vignette and grade into the composite with the `PostSettings` defaults; add `DisplaySettings.postQuality` (LOW turns bloom and AO off) to the display menu and the game's settings file. Verify with `./gradlew :app-game-control-line:test --tests '*DisplaySettingsTest'` (round trip, LOW maps to no bloom or AO) (round trip, a file from before the field keeps effects on), `ControlLineUiAssetTest` (the settings screen's "High quality effects" checkbox, added with `UiTextEditor`, binds a declared `highQualityPost`) and `FieldLocalLightsGlTest.lowQualityDrawsWithoutBloomAndOcclusion`.
- [x] 7.3 Retune `Hangar.scene` through `HeadlessEditing` or the IDE on a copy of the project, copied back: ambient about 0.05–0.1, lamp intensities, the four manual spots kept or removed; check whether `additional.materials` can lower `WetConcrete` roughness and apply it if so. Verify that `./gradlew :app-game-control-line:test` passes (`VitrineSceneTest` and the scene-loading tests) and that the document still validates with `AbyssusDocumentFormat`. Done with `SceneTextEditor` / `withSettingValue` and `HeadlessEditing.setComponentField`: ambient 0.05, the 64 lamps intensity 6 in near-white (1, 0.95, 0.88), the table spots 2, the key spot 3. `additional.materials` holds only material names, so `WetConcrete` keeps roughness 1 (puddles stay with `add-after-rain-weather`).
- [ ] 7.4 Manual check: `./gradlew :app-game-control-line:run`, open plane select, and compare a screenshot with the reference in `docs/perf/control-line-interior/`. Check lit pools under the lamps, glowing and blooming tubes, floor streaks and darker corners. Save the screenshot next to the "before" capture.

## 7b. Spot shadows (added after the plane-select check)

- [x] 7b.1 `FieldShadows.renderInterior`: a separate 4096 map with four spot tiles drawn every frame and the cube faces of the eight point lights nearest the scene camera drawn once from the static models, in a `ShadowAtlasAttribute` matched by light identity, drawn in `FieldRenderer` when the scene has no sun and `FieldRenderSettings.localShadows` is on. Verify with `FieldLocalLightsGlTest.theHangarsLightsCastShadows` (four spot tiles, eight lamp cubes baked once; the floor under the table and the wall behind it darker than with light shadows off) and `theLampsLightTheTableTopAndNoSunIsDrawn`, `FieldShadowsGlTest`, `FieldPipelineGlTest` still passing.
- [x] 7b.2 Retune the hangar through `SceneTextEditor` / `HeadlessEditing`: the table spots intensity 10, the key spot 12, the lamps 3 (fill), ambient 0.05. Verify that `./gradlew :app-game-control-line:test` passes and the hangar picture shows the table's shadow.
- [x] 7b.3 Update the game README (light shadows, `localShadows`) and design decision 6b. Verify with `scripts/check-docs.sh`.

## 8. Integration

- [x] 8.1 Update `docs/ai/architecture.md` (game frame order, `post` package, probe), `projects/lib-render/README.md` (post, probe, ownership), `projects/app-game-control-line/README.md` (settings, fallback, credits) and, if it lists skipped glTF extensions, the model-import user doc. Verify with `scripts/check-docs.sh`.
- [x] 8.2 Re-run `PerfWorkloadsGlTest` for the pilot view and the hangar, record the numbers in design.md decision 7 and `docs/perf/control-line-interior/`, and confirm the pilot view p50 ≤ 7.5 ms and p95 ≤ 11 ms and the hangar p50 ≤ 7.5 ms on the reference machine.
- [ ] 8.3 Run `./gradlew check` (includes `checkShippedClasspath`, `checkNoEditorCode`, `checkNoSingletons`, `checkJacksonTreeUse`, `checkPackageCycles`), the GL suites `./gradlew :lib-gdx:test :lib-render:test :app-game-control-line:test -Dabyssus.glTests=true`, and `scripts/check-docs.sh`. All pass.
