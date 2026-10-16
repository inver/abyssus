# Tasks

Groups 1-3 are mechanical DRY work already covered by existing tests. Group 4 needs a display for its GL evidence.
Groups 5 and 8 touch rendering and change structure around behavior, so they keep the GL/parity gates. GPU/manual
items are never marked done from a skipped test.

## 1. Shared helpers in `lib-core-editor` (R3, R6 dead code)

- [ ] 1.1 Add `sha256Hex(ByteArray)` in a `lib-core-editor` util and replace the four digest loops (`NewTerrainFiles.kt`, `ModelSource.kt`, `TextureGather.kt`, `FlightGearArchive.kt`) and the plugin copy in `AssetTransaction.kt`; verify with a new `DigestTest` case asserting a known vector and that the asset-transaction hash is unchanged.
- [ ] 1.2 Add one number-text formatter and route `FoliageSection`, `EntityDetailsView`, `UiDeclarationForms`, `UiPropertiesModel`, `UiPropertiesPanel` and `ComponentEditor` through it; verify with unit tests for integers, trailing zeros and the `1.0` vs `1` text that `SceneTransformWriter` produces today.
- [ ] 1.3 Add one `wrapDegrees`/`azimuthOf` helper and delete the copies in `PathTokens`, `SphereTrack`, `IdealTracks`, `Matchers`, `PathLayout`, `HemisphereGeometry`, `DebriefModel`, `LineRig`, `IdealSampler`; verify with a unit test for wrap at 180/-180 and az=atan2(-z,x) on the four cardinal directions.
- [ ] 1.4 Add one native asset-meta builder and use it in `NewTerrainFiles`, `NewFoliageFiles`, `UiAssetTemplate`, `ModelImport`, `WeatherPresetDraft`, `FlightGearImport` and `SceneDocument`; verify with a test asserting the produced `meta.json` for a terrain and a skybox is byte-identical to today's output (format/formatVersion/kind and key order).
- [ ] 1.5 Add one atomic JSON store (read, `.bad` rename, temp-file save) and use it in `ScoreTable`, `PatternScoreTable` and `RecordingStore`; verify with a test for a truncated file (`.bad` kept, no partial main file) and for a successful save replacing the old content.
- [ ] 1.6 Delete `ComponentEditor.rejected` (kept `reject`) and the unused `Grader.SUPERIMPOSITION_BINS`; verify `./gradlew :lib-core-editor:test` still passes and no reference to either remains.

## 2. Loader Template Method and foliage Strategy (R2)

- [ ] 2.1 Give `AssetLoader.discard` a default no-op and delete the ten `override fun discard(model: Unit) = Unit` bodies; verify `./gradlew :lib-core:test` passes unchanged.
- [ ] 2.2 Add a package-internal loader base implementing `prepare(name) = metaLoader.loadBaseMeta(name)?.let(::loadPrepared)` and reduce the seven loaders to their `loadPrepared` work; verify `./gradlew :lib-core:test` and `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneAssetsTest'` (or the existing scene-assets coverage) pass.
- [ ] 2.3 Collapse `FoliageLoader` and `RuntimeFoliageLoader` into one loader parameterized by a read policy, with the shared dependency expression extracted; verify with a new `FoliageLoaderTest` case for editor-generate vs runtime-use-as-written and for declared dependencies.
- [ ] 2.4 Document the loader base and foliage policy in `projects/lib-core/README.md`; verify with `scripts/check-docs.sh`.

## 3. `lib-core` KISS and shared GL constants (R3, R6)

- [ ] 3.1 Add one white/blank texture factory that owns the `Pixmap` lifecycle and use it in `TerrainPass`, `UiGlKit`, `WaterDrawable` (removing the leaked inline `Pixmap`) and `WaterDrawable.blankPixmap`; verify with a unit test that the factory produces the expected format and that the water blank texture path no longer constructs an undisposed `Pixmap`.
- [ ] 3.2 Add one `checkFramebufferComplete(label)` helper and use it in `HdrEnvironmentBuild`, `VolumetricClouds` and `ProceduralLightingCube`; verify with the existing GL sky tests under `-Dabyssus.glTests=true`.
- [ ] 3.3 Name the duplicated GL constants (`GL_FRAMEBUFFER_SRGB` 0x8DB9, `GL_RED` 0x1903, `GL_HALF_FLOAT` 0x140B) once and import them in `ShadowResources`, `WaterResources`, `CloudShadowMap`, `CloudsLoader`, `HdrEnvironmentBuild` and `VolumetricClouds`; verify with `./gradlew :lib-core:test :lib-render:test`.
- [ ] 3.4 Make `createFullscreenTriangle` a top-level function, delete `GeometryUtils` and its `abyssusSingletonExcludes` entry, and delete the empty `SceneEnvironment` in favour of `Environment()`; verify `./gradlew :lib-core:checkNoSingletons :lib-core:test` and the Control Line field build.

## 4. Shadow and water reuse `lib-render` (R1)

- [ ] 4.1 Replace `SceneShadows`' inline bias with `sunShadowBias` and replace the `ShadowProjection`/`SceneRenderer` up-vector idioms with one shared helper (keep only the receiver+caster fit local). The plugin's ortho copy guards the divisor with `max(far - near, 0.01f)` while `sunShadowBias` does not: move the guard into `sunShadowBias` so both hosts share it, and pin it with a `sunShadowBias` unit test including `far == near`; verify the remaining helper with a new `ShadowProjectionTest` case fitting a known receiver and a `-Dabyssus.glTests=true` editor shadow run.
- [ ] 4.2 Delete the plugin `ShadowLightKind` and use `lib-gdx`'s, removing the `AtlasLightKind.valueOf(allocation.kind.name)` bridge; verify `./gradlew :plugin-abyssus:test` and the shadow GL cases compile and pass.
- [ ] 4.3 Route `SceneWater.depthTarget` through `lib-render`'s `DepthSubmission.submit` with the frustum-filtered caster list; verify water renders identically under `-Dabyssus.glTests=true`.
- [ ] 4.4 Extract one `GlRenderTarget.render(clear, block)` Template Method used by `ShadowResources` and `WaterResources`, and have `CloudShadowMap` use `ShadowGlState`; verify the shadow and water GL cases pass and state is restored after each (a test that a second render sees the default state).
- [ ] 4.5 Update the `sceneview` package README for the shared shadow/water helpers; verify with `scripts/check-docs.sh`.

## 5. One shader-budget contract (R4)

- [ ] 5.1 Define the light/texture-unit budgets once (light profile in `lib-render`, atlas attribute in `lib-gdx`) and derive `WaterDrawable.MAX_DIRECTIONAL`, `TerrainMesh.SPLAT_UNIT`, `TerrainPass.MAX`/`SHADOW_UNIT`, `RenderLights` and `SceneLighting.MAX_DIRECTIONAL` from it; verify with a unit test that the terrain splat unit and shadow unit do not overlap and match the profile's directional/point/spot maxima.
- [ ] 5.2 Apply D4: derive `TerrainPass.MAX` as the maximum of the profile's per-kind maxima (it is per-kind upload capacity, not a directional budget, so keep 5 alongside `maxDirectional = 2` and do not shrink it below the point/spot maxima), and extend the headless budget test to assert the capacity is at least the editor fragment shader's declared array size (read from the bundled resource) so a shader size change fails the test. Record the chosen contract in the design's D4.
- [ ] 5.3 Capture before/after render evidence for the editor multi-light and game single-sun terrain and shadow cases; verify with `-Dabyssus.glTests=true` before and after the constant unification, showing no unexplained pixel difference. (GPU/manual: leave unchecked until run on a display.)

## 6. Validated-tree read seam and plugin parse sites (R3, design D2)

- [ ] 6.1 Add the parse+validate-returning-`JsonNode` seam next to `DocumentParsing` (reusing `SceneJson`/`NativeJsonTree` and `AbyssusDocumentFormat`, with the `runCatchingKeepingCancellation { }.getOrNull()` form) and route `PanelState`, `AssetReferenceGuard`, `FoliageEditing`, `FoliageStrokeCommand`, `UiAssetCommands`, `SceneParamsSource`, `NewFoliageAction`, `NewWeatherPreset`, `NewTerrainAction`, `ImportRefusals` through it. Three of those sites (`NewFoliageAction`, `NewTerrainAction`, `ImportRefusals`) today validate a `JsonProcessor.readObject` tree, not the lossless one; they only validate and read names/flags, so routing them through the lossless seam must not change which documents are accepted. Verify with a test that a malformed and an unsupported-version document both return null and are not rewritten, and that a valid one preserves number text.
- [ ] 6.2 Add one `assetResultMessage(AssetCommandResult)` mapper and use it in `UiAssetCommands`, `NewFoliageAction` and the import actions; verify each result message with a unit test over the six cases.
- [ ] 6.3 Document the seam and the mapper in `projects/lib-core-editor/README.md`; verify with `scripts/check-docs.sh`.

## 7. Plugin flow deduplication (R3)

- [ ] 7.1 Add one `showActionGroup(e, group, title)` helper and use it in `AddLightAction`, `AddAssetAction`, `AddPathArrangementAction`, `AddFoliageAction`, `ComponentActions`, `WaterActions` and `EntityDetailsView`; verify with the existing action tests and a `runIde` check that each menu still opens (numbered check).
- [ ] 7.2 Add one `AddChildGroup<T>` Template-Method base (target + `canAdd` + `add`) and reduce the `Add*Group` family to it; verify with `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.projectView.*'` and an enable/visible check per action.
- [ ] 7.3 Replace the "change view -> get tree -> promiseSelect" copies in `EntitySelection` and `AssetSelection` with one helper, and the duplicated VFS native-path predicate with one `isNativeDocumentPath`; verify with `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.AbyssusProjectViewPaneTest'` including the unrelated-file-event case.
- [ ] 7.4 Consolidate the three independent `VFS_CHANGES` subscribers (`AbyssusTreeRefresh`, `AbyssusFooter`, `UiFileEditor`) behind one registration that filters native paths and dispatches; verify with the pane test and `runIde` external-edit checks.
- [ ] 7.5 Update `projects/plugin-abyssus/.../projectView/README.md`; verify with `scripts/check-docs.sh`.

## 8. Class splits (R5)

- [ ] 8.1 Split `AssetStorage` into the off-GL executor factory, the thread/call-depth guard, a dependency resolver and a hook registry, with `AssetStorage` keeping its public API and owning upload/build/discard; verify `./gradlew :lib-core:test --tests 'net.nevinsky.abyssus.lib.core.assets.loading.AssetStorageTest'` including late completion, replacement, cycles and abandonment.
- [ ] 8.2 Split `SceneRenderer` into a content/environment pass and a sky pass over `SceneViewState`, moving the snapshot packing with the pass that reads it; verify `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.sceneview.*'` and the scene-view GL cases under `-Dabyssus.glTests=true`.
- [ ] 8.3 Split `SceneViewPanel` into toolbar construction and a GL-lifecycle/frame-driver delegate; verify `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewPanelTest'`.
- [ ] 8.4 Replace `SceneInteraction`'s `drag`/`dragEntity`/`dragResult`/`cancelled` fields with a `sealed Gesture` and a per-branch ray helper; verify with `./gradlew :lib-core-editor:test` (gesture and hit-test cases) and a `runIde` drag/hover check.
- [ ] 8.5 Split `PhysicsWorld` into a body factory, a stepper and a pose writer (Jolt stays in the module); verify `./gradlew :lib-physics:test` and `./gradlew :app-game-control-line:test`.
- [ ] 8.6 Split `FlightGearImport` into collection/geometry/staging collaborators reusing the 1.4 meta builder; verify `./gradlew :lib-core-editor:test` including the FlightGear import cases.
- [ ] 8.7 Split `UiCanvasView`/`UiEditorController` into a document/selection model, a gesture controller and a thin view; verify `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.gameui.*'` and a `runIde` canvas edit check.
- [ ] 8.8 Document each extracted collaborating class and which ones are GL/Swing/platform-free in `docs/ai/architecture.md` and the affected READMEs; verify with `scripts/check-docs.sh`.

## 9. Boundaries: injection, ISP and `MetaType` (R6)

- [ ] 9.1 Replace `service<…>()` lookups in the domain classes (`FoliageStrokeCommand`, `FoliageEditing`, `NewFoliageAction`, `AddAssetAction`, `UiBackdrop`, `ProjectReader`, `ComponentActions`, `AssetReadCache`, `ArrangementHeights`) with constructor parameters resolved at the IntelliJ entry points; verify with the affected tests and `runIde` open of tree, properties and Game UI.
- [ ] 9.2 Replace `AssetLoading`'s public `decoder`/`hdrFiles`/`toneCurve`/`hdrPreview` with one `HdrPreviewService`; verify the properties HDR panel opens and its tests pass.
- [ ] 9.3 Replace the asset-kind strings with `MetaType` in `SkyboxChoices`, `PanelState` and `SceneComponentEdits`; verify with a test that each kind still resolves its editor and that `MetaType` values match the `meta.json` `type` text.
- [ ] 9.4 Update the affected package READMEs and the status lines in `docs/reviews/design-review-2026-10.md` for the findings this change closes; record there that the deferred number-text question is decided as pinning today's text (transform writes keep `1.0`, component edits keep `1`), since Mundus compatibility is gone and nothing remains to check it against. Verify with `scripts/check-docs.sh`.

## 10. Final verification

- [ ] 10.1 Run `./gradlew check -Pabyssus.requireShaders=true` and `scripts/check-docs.sh`; verify both pass.
- [ ] 10.2 On a display, run the group-4 and group-5 GL gates (`-Dabyssus.glTests=true`) and the `runIde` checks in 7.1, 7.4, 8.4 and 8.7; list any that could not be run instead of counting them as passed.
