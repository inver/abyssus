# Tasks

## 1. Runtime weather settings and scene edits

- [ ] 1.1 Add pure constructor-wired weather settings/validation in `lib-core`, reading `SceneDto.abyssus` through existing validated mapper paths; verify new `WeatherSettingsTest` cases for missing/default/afterRain, unknown modes, malformed containers and unsupported native markers with `./gradlew :lib-core:test`.
- [ ] 1.2 Add formatting-preserving weather edits in `lib-core-editor` using the existing scene document/text editing infrastructure; verify new `WeatherEditsTest` cases for unknown members, exact unrelated number text/key order, empty-container cleanup, no-op, malformed-container refusal and expected-mode conflicts with `./gradlew :lib-core-editor:test`.
- [ ] 1.3 Document `abyssus.weather.mode`, runtime read policy and default omission in `docs/ai/file-formats.md` and the two core module READMEs; verify examples match codec tests and run `scripts/check-docs.sh`.

## 2. Automatic ground receivers and stable masks

- [ ] 2.1 Add host-neutral CPU geometry/transform inputs and conservative receiver classification in `lib-render/weather`; verify new `WeatherReceiverTest` cases for sloping/transformed terrain, thin supported asphalt models, unsupported model-only scenes, elevated roofs, steep faces, animated models, overlapping receivers, lake/sea coverage and foliage exclusions with `./gradlew :lib-render:test`.
- [ ] 2.2 Add fixed-version scene-seeded mask planning, geometry/transform/foliage revision keys and shared shader mask source; verify new `PuddleMaskTest` cases for deterministic scene reload and cross-host inputs, changed geometry invalidation and camera-independent coverage with `./gradlew :lib-render:test`.
- [ ] 2.3 Add a regression fixture derived from a copy of ControlLine's actual pad and site terrain to verify automatic receiver admission and irregular patch coverage; verify the pad is included without name-based rules, nearby scenery is excluded and the committed project stays byte-identical in `WeatherReceiverTest`.
- [ ] 2.4 Document receiver eligibility, scene-unit assumptions, mask stability and cache ownership in `projects/lib-render/README.md`; verify documentation against classification tests and `scripts/check-docs.sh`.

## 3. Shared wet shading and bounded reflections

- [ ] 3.1 Add neutral receiver/wetness hooks to default and PBR model shaders in `lib-gdx`, with shared mask functions and preserved dry behavior; verify new opt-in `WetModelShaderGlTest` cases for wet texture/marking retention, grazing-angle reflection, non-receiver exclusion and unchanged dry captures using `./gradlew :lib-gdx:test -Dabyssus.glTests=true`.
- [ ] 3.2 Integrate the same wet response into both `TerrainPass` variants and add the host-neutral puddle pass/opaque input contracts in `lib-render`; verify new opt-in `PuddlePassGlTest` cases for wet terrain, depth-correct uppermost receiver composition and unchanged dry captures using `./gradlew :lib-render:test -Dabyssus.glTests=true`.
- [ ] 3.3 Implement bounded screen-space reflections with current sky/cloud/background fallback, the target limits and 32-step/4-refinement limits from the design; verify `PuddlePassGlTest` covers offscreen misses, many patches without extra views, no overlay contamination and no lake/sea double treatment.
- [ ] 3.4 Implement optional-target allocation recovery, dry bypass and owner-thread/context disposal; verify headless `WeatherResourcePlanTest` budget cases and opt-in `PuddlePassGlTest` allocation failure, disable/re-enable, resize/context replacement and borrowed-resource survival cases.
- [ ] 3.5 Document shader hooks, target budgets, screen-space limitations and resource ownership in the lib-gdx/lib-render READMEs; verify `./gradlew :lib-gdx:test :lib-render:test checkNoEditorCode checkPackageCycles` and `scripts/check-docs.sh`.

## 4. Editor selection and rendering integration

- [ ] 4.1 Add localized scene Weather control/validation to the scene Properties model and `SceneDetailsView`, committing through `editSceneJson`; verify new `SceneWeatherPropertiesTest` cases for supported/unsupported documents, unsaved reads, conflicting edits, no-op and byte-exact undo/redo using `./gradlew :plugin-abyssus:test --tests '*SceneWeatherPropertiesTest'`.
- [ ] 4.2 Adapt current editor terrain/model/foliage geometry and transform previews into shared weather inputs, preparing/caching CPU work off the render loop and rejecting stale revisions; verify headless `SceneWeatherInputsTest` cases for preview transforms, asset reload and cancellation/revision replacement using `./gradlew :plugin-abyssus:test --tests '*SceneWeatherInputsTest'`.
- [ ] 4.3 Integrate weather passes before editor overlays and with existing lake/sea depth coverage, under `GdxRuntime.withContext`/`glSafe`; verify opt-in `SceneWeatherGlTest` captures for pad/terrain, cloudy sky fallback, water coexistence, resize/hide/show and no overlay reflections using `./gradlew :plugin-abyssus:test --tests '*SceneWeatherGlTest' -Dabyssus.glTests=true`.
- [ ] 4.4 Add after-rain ray admission fallback with a localized reason, retaining requested ray mode and saved limits; reconcile the active `add-scene-raytracing`/`add-scene-raytracing-settings` deltas where they conflict. Verify new `SceneWeatherRayFallbackTest` wet/default transitions and unchanged settings, and run `openspec validate add-scene-raytracing --strict` and `openspec validate add-scene-raytracing-settings --strict` after any artifact amendment.
- [ ] 4.5 Document Weather, conservative receiver support, water interaction and wet-weather raster fallback in the scene-view README and user README/CHANGELOG; verify text against the integration tests and `scripts/check-docs.sh`.

## 5. Control Line session choice and shared rendering

- [ ] 5.1 Read scene weather in `FieldScene` and add a session override held outside per-flight scene reloads; verify new `GameWeatherTest` cases for initial scene precedence, explicit Default override, retry/menu/new-flight retention and restart reset using `./gradlew :app-game-control-line:test`.
- [ ] 5.2 Add the Weather selector to the authored main-menu asset and typed bindings/actions in `GameUiModel`/UI contract, preserving current Free flight/F2B/Scores/Quit actions; verify keyboard/mouse activation and current navigation in `GameWeatherTest` and the authored UI contract tests. Amend the `add-f2b-pattern-mode` menu delta to acknowledge the selector and verify `openspec validate add-f2b-pattern-mode --strict`.
- [ ] 5.3 Adapt `FieldRenderer` geometry, opaque inputs and displayed sky into the shared pass, including both terrain and the modelled pad; verify opt-in `FieldWeatherGlTest` captures and shared-mask sample agreement with editor inputs using `./gradlew :app-game-control-line:test -Dabyssus.glTests=true`.
- [ ] 5.4 Verify weather never enters physics or scoring by running identical deterministic flight/control traces under both choices in `GameWeatherTest`; assert matching state/results and byte-identical native project/assets, then run `./gradlew :app-game-control-line:test :app-game-control-line:checkShippedClasspath`.
- [ ] 5.5 Document the session override, restart semantics, preserved atmosphere and visual-only behavior in the Control Line README and release notes; verify examples against `GameWeatherTest` and `scripts/check-docs.sh`.

## 6. Cross-host acceptance and final checks

- [ ] 6.1 Run manual editor check E1 on copies of Untitled/ControlLine: choose wet/default, inspect top-down and grazing views, reopen, undo/redo, click/drop through puddles, try Play, then hide/resize/reopen and request ray mode; record that appearance, text preservation and fallback behavior meet `scene-after-rain` scenarios.
- [ ] 6.2 Run manual game check G1: choose weather with keyboard and mouse, start/retry Free flight and F2B, return to menus and restart the application; record choice precedence, visible wet asphalt/terrain, identifiable painted rings, unchanged atmosphere and unchanged flight controls.
- [ ] 6.3 Record dry/wet steady-state CPU/GPU frame times, machine, resolution, quality and target allocations for matching pilot/editor cameras, separate from first load/compilation; verify fixed target/reflection budgets and responsive controls, and attach representative paired captures to the change's verification record.
- [ ] 6.4 Run `openspec validate add-after-rain-weather --strict`, `./gradlew check` and `scripts/check-docs.sh`; record results and any environment-dependent GL/manual limitations in the change verification record before declaring implementation complete.
