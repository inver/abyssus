# Tasks

Tests named below are proposed new classes unless already present. Plugin single-class runs use `./gradlew :test --tests '<fully-qualified-class>'`; core and gdx-model use their own module test tasks. Manual checks use a temporary copy of Untitled, never the original fixture. Each manual checkbox stays open until performed.

## 1. Persistent settings and pure editing

- [ ] 1.1 Add constructor-wired core WaterSettings, preset defaults and WaterValidation; verify WaterSettingsTest covers Lake/Sea differences, finite/range checks, missing defaults and invalid presets with `./gradlew :core:test`.
- [ ] 1.2 Add a scene extension codec and SceneDto/SceneContent reading that retains unknown data without rewrites; verify WaterCodecTest and SceneContentTest cover absent/empty/malformed extensions, invalid surfaces, independent ids/levels and unchanged original Untitled text with `./gradlew :test`.
- [ ] 1.3 Implement pure WaterEdits add/update/rename/toggle/remove and id allocation; verify WaterEditsTest covers incompatible containers, collisions, no-op/rejection, preservation of unknown members/key order/number text, last removal and unchanged ECS data and native document markers.
- [ ] 1.4 Wire water edits through editSceneJson with bundle commands; verify WaterSceneEditsTest covers each edit's exact-text undo/redo and unchanged .abss/meta.json, and that a rejected edit creates no undo step.
- [ ] 1.5 Document the schema, defaults, Abyssus-only limitation and no-migration behavior in docs/ai/file-formats.md; verify documented field names against WaterCodecTest and run `scripts/check-docs.sh`.

## 2. Authoring UI and selection

- [ ] 2.1 Add toolbar/tree Add Water presets, water group/rows and eye/rename/remove actions using stable scene+surface targets; verify WaterTreeTest and WaterActionTest cover placement source, naming, visibility, refresh and existing ECS rows. Run `./gradlew :test`.
- [ ] 2.2 Add WaterProperties panel state/editors and localized labels/errors; verify WaterPropertiesTest covers every setting, validation, external refresh, missing target and Abyssus-only indication, without regressing EntityDetailsView editors.
- [ ] 2.3 Introduce distinct ECS/water selection identities and pure WaterHit bounds/distance queries; verify WaterHitTest and ScenePickerTest cover nearest hits, dry terrain occlusion, rectangle edges, disabled/invalid surfaces, parallel/grazing rays and above-only picking.
- [ ] 2.4 Implement water outlines, move previews/commit/cancel and selection clearing; verify WaterInteractionTest covers X/Y/Z moves, exact position-only writes, Esc, no-op, removed selection, disabled rotation and disabled Drop. Verify SceneInteractionTest's existing drop cases still exclude water as ground.
- [ ] 2.5 Update projectView and sceneview package notes for water targets and authoring; verify bundle keys resolve in WaterActionTest and run `scripts/check-docs.sh`.
- [ ] 2.6 Perform runIde check 1: add Lake in the viewport and Sea from the tree, select exposed water and dry terrain, edit every property, toggle/rename/remove, reopen, Undo/Redo and Esc a drag; confirm placement, selection and file isolation. Use `./gradlew runIde -PideProject=<temporary-copy>`.

## 3. Multipass scene groundwork

- [ ] 3.1 Integrate a once-per-frame geometry/animation snapshot into SceneRenderer, reusing add-scene-shadows' entry points if present; verify SceneFrameSnapshotTest and SceneRenderGlTest cover one animation update despite multiple draws, identical previews and unchanged no-water rendering. Resolve any conflicting open delta discovered during integration without removing its behavior; verify affected changes with `openspec validate <change> --strict`.
- [ ] 3.2 Add optional world-plane clipping and packed depth passes for model meshes in gdx-model, reusing existing depth shaders where available; verify ModelWaterPassGlTest covers 32-bit indices, skinning, alpha-cutout holes and defaults-off regression with `./gradlew :gdx-model:test -Dabyssus.glTests=true`.
- [ ] 3.3 Add matching clipping/depth support to TerrainShader and separate opaque/alpha-tested from blended model passes; verify SceneWaterPassGlTest checks transformed terrain, clipped geometry and absence of grid/markers/water/blended models from offscreen targets with `./gradlew :test -Dabyssus.glTests=true`.
- [ ] 3.4 Implement pure WaterReflectionCamera and WaterDepthMath; verify WaterReflectionCameraTest covers position/direction/up reflection and winding conventions, and WaterDepthMathTest covers packed depth round trips, inverse projection, empty depth, near/far extremes and grazing rays. Run `./gradlew :core:test`.
- [ ] 3.5 Document frame snapshot/pass responsibilities and optional reusable gdx-model pass APIs in docs/ai/architecture.md and module notes; verify `scripts/check-docs.sh` and existing module tests.

## 4. Water rendering and graphics lifecycle

- [ ] 4.1 Implement pure WaterPassPlan for visible level grouping, fixed budgets, stable selection/hysteresis, target dimensions and disabled/below-view filtering; verify WaterPassPlanTest covers one/two/many levels, coplanar grouping, resize caps and stable ties with `./gradlew :core:test`.
- [ ] 4.2 Implement per-context framebuffer pooling, scene color/depth, terrain depth and planar reflection views; verify SceneWaterResourcesGlTest covers caller framebuffer/state restoration, resize, zero-size skip, context abandon/rebuild and disposal. Run `./gradlew :test -Dabyssus.glTests=true`.
- [ ] 4.3 Add bounded wave mesh generation and analytic wave/normal math plus packaged shaders; verify WaterWavesTest covers zero amplitude, finite normals, world-space phase and mesh caps with `./gradlew :core:test`; verify WaterShaderGlTest compiles and animates both presets with GL tests enabled.
- [ ] 4.4 Implement Fresnel reflection, directional/environment lighting, reflected-camera clipping and single fog application; verify SceneWaterReflectionGlTest covers sky modes, a model above/below level, moving/animated geometry, grazing-angle strength and editor-overlay exclusion.
- [ ] 4.5 Implement depth-dependent absorption/refraction with bounds and above-water rejection; verify SceneWaterShallowsGlTest checks shallow/deep terrain, clarity changes, distorted samples near dry land, empty depth and active-camera near/far values.
- [ ] 4.6 Implement terrain-only animated shoreline foam and contact wave attenuation; verify SceneWaterFoamGlTest covers level changes, zero amount, width/amount changes, dry land rejection and absence of foam on deep water, model contacts and extent edges.
- [ ] 4.7 Implement sky/tint budget fallback, resource failure degradation, deterministic overlapping surface composition and below-water skip; verify WaterFailureTest plus SceneWaterFallbackGlTest cover log-once/retry rules, invalid surface isolation, allocation/shader failure and two-level/coplanar scenes.
- [ ] 4.8 Update core/sceneview README notes and docs/ai/testing.md with water pass ownership, capabilities, limits and GL test commands; verify `scripts/check-docs.sh` and `./gradlew :core:checkNoSingletons`.
- [ ] 4.9 Perform runIde check 2: use a copied Untitled scene with its Terrain entity 1 and a test basin, place Lake/Sea across slopes, change level/clarity/waves/foam and orbit from overhead to grazing angles. Confirm realistic reflections, visible shallows, terrain-defined islands and moving foam; capture screenshots for review.
- [ ] 4.10 Perform runIde check 3: reflect a moved and animated model, test available cube/procedural/HDR skies and lights/fog, and verify reflections match the same visible frame and exclude grid/gizmos. If scene shadows are present, confirm shadowed geometry remains shaded in water passes.
- [ ] 4.11 Perform runIde check 4: profile one, two and more than two water levels at a recorded viewport size, compare no-water frame time, verify bounded target/pass counts and usable input, then resize/hide/show/reopen the canvas and move below water. Record timings and confirm fallback, lifecycle and navigation behavior.

## 5. Integration and readiness

- [ ] 5.1 Update README user features and CHANGELOG with above-water authoring, Abyssus-only storage and visual limitations; preserve README plugin-description markers. Verify documented behavior against runIde checks 1-4 and run `scripts/check-docs.sh`.
- [ ] 5.2 Run plugin/core/gdx-model water GL suites on a display with `./gradlew check -Dabyssus.glTests=true`; record results and keep unperformed GL/manual checks open.
- [ ] 5.3 Run `./gradlew check` and `scripts/check-docs.sh`, then `openspec validate add-realistic-water --strict`; record unrelated failures separately and confirm all completed task boxes have their stated evidence.
