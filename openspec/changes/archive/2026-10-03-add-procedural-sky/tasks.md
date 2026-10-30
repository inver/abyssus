# Tasks

## 1. The sky as data

- [x] 1.1 Add `SKYBOX_PROCEDURAL` to `MetaType` and `ProceduralSkyMeta` / `ProceduralSkyAdditional` (`vertex`,
      `fragment`, optional physical parameters); add `AtmosphereParams` with the Earth-like defaults and parsing
      with omitted-field fallback, free of Swing, GL and the platform. Verify: new `AtmosphereParamsTest` cases
      `omittedParametersTakeEarthDefaults`, `parsesTheFixtureMeta`, `rejectsAMetaWithoutShaderNames` pass with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.skybox.AtmosphereParamsTest'`
- [x] 1.2 Add `SunDirection.of(lights)`: negated, normalized direction of the brightest directional light; default
      sun at 45 degrees elevation when none is usable. Verify: `SunDirectionTest` cases `followsTheBrightestLight`,
      `defaultsWithoutADirectionalLight`, `ignoresPointLights`, `resultIsUnitLength` pass with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.skybox.SunDirectionTest'`
- [x] 1.3 Add the fixture `src/test/testData/project/Untitled/assets/skybox_physical/` with `meta.json`
      (`"type": "SKYBOX_PROCEDURAL"`, `vertex: sky.vert`, `fragment: sky.frag`, parameters), `sky.vert` and `sky.frag`
      (fullscreen triangle; single-scattering Rayleigh+Mie ray-march, Henyey-Greenstein phase, sun disc, ground tint,
      exposure curve). Verify: a test reads the folder through `ProjectAssets` and finds a `SKYBOX_PROCEDURAL`
      asset with both files present (`ProceduralSkyFixtureTest.fixtureFolderIsComplete`) via
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.skybox.ProceduralSkyFixtureTest'`; then re-run the
      full `Untitled`-dependent tests and fix any that now see a second skybox
- [x] 1.4 Add the CPU reference `AtmosphereModel.radiance(viewDir, sunDir, params)` mirroring the shader's math.
      Verify: `AtmosphereModelTest` cases `zenithIsBlueAtNoon`, `horizonIsRedderAtSunset`, `outputIsFiniteAndNonNegative`
      (over a grid of directions), `belowHorizonUsesTheGroundTint` pass with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.skybox.AtmosphereModelTest'`

## 2. Drawing it

- [x] 2.1 Add `ProceduralSkyLoader` (`prepare` off-thread: parse meta, read the two GLSL files as text, no GL;
      `upload` on the render thread: compile the program, build the 3-vertex mesh; failure caught with
      `runCatchingKeepingCancellation`, logged once, cached as failed). Verify: `ProceduralSkyLoaderTest` cases
      `prepareReadsMetaAndBothShaders`, `missingFragmentFileFailsPrepare`, `wrongTypeIsRejected` pass with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.skybox.ProceduralSkyLoaderTest'`
- [ ] 2.2 Generalize `SceneSkybox` to draw either a cube or a procedural sky (shared state setup; the procedural
      path binds `u_invViewProj`, `u_sunDir`, `u_cameraHeight` and the parameter uniforms) and have `SceneRenderer`
      pass the sun from `LightSet`. Existing `SKYBOX` drawing stays unchanged. Verify: the existing skybox cases in
      `SceneRenderGlTest` still pass and a new case `proceduralSkyCompilesAndDraws` passes with
      `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneRenderGlTest' -Dabyssus.glTests=true` on a machine
      with a display; if none is available, record that and leave it to runIde check 5.2
- [x] 2.3 Document the type and its shader contract (uniform names, parameters, plugin-only status) in
      `docs/ai/file-formats.md` and `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md`. Verify:
      `scripts/check-docs.sh` passes

## 3. Choosing it

- [x] 3.1 Accept `SKYBOX_PROCEDURAL` in `SkyboxChoices` and add the `procedural sky` detail line through
      `AbyssusBundle`. Verify: new cases in the existing skybox chooser tests - `listsBothSkyboxKinds`
      (`Untitled` reads "2 found", lists `skybox_default` and `skybox_physical`), `proceduralDetailLine`,
      `filterStillAppliesToProceduralSkies` - pass with the chooser test class's `./gradlew :test --tests '<class>'`
- [x] 3.2 Update the existing chooser tests that pin "1 found" / the `Untitled` skybox list to the new count.
      Verify: `./gradlew :test` passes with no failure from chooser or tree tests

## 4. Integration

- [x] 4.1 Run `./gradlew check` and `scripts/check-docs.sh`. Verify: both exit 0; report any failure outside this
      change with its cause instead of fixing it silently

## 5. By hand and by reconciling

- [ ] 5.1 Reconcile with `add-hdr-skybox-ibl`: if it is archived first, amend this change's MODIFIED requirements to
      start from its merged text; if this one is archived first, amend that delta the same way. Verify:
      `openspec validate add-procedural-sky` passes and the two deltas no longer contradict on the chooser's type list
- [ ] 5.2 runIde check, using a copy of `Untitled` (never the fixture itself): (1) open the copy's `Main Scene`,
      assign `skybox_physical` through Choose, see a blue sky with a sun disc; (2) orbit to the zenith and nadir and
      see no seams and a dark ground below the horizon; (3) rotate the scene's directional light toward the horizon
      and see sunset colors after refresh; (4) break `sky.frag`, reopen the scene, see no sky, a logged compile
      error and models still drawn. Leave open until the user confirms
