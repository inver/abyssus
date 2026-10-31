# Tasks

## 1. lib-core model and binding

- [x] 1.1 Confirm with the owner the `JsonProcessor` injectable for `ComponentRegistry` and the `formatVersion` choice (design decisions 1-2, risks) and the `flags` bit meaning. Verify: written answer in this file. Answer (accepted when the owner asked to apply): bit `1` of `flags` = disabled, other bits kept; `formatVersion` stays 1; component `enabled` unchanged; the registry reaches the binder through `JsonProcessor.binderWith(EntityDtoModule(registry))` (a Jackson module, no injectable). Test class is `EntityDtoModuleTest`.
- [x] 1.2 Add `RawComponent`, `ComponentMapDeserializer`, `EntityDto.flags`. Verify: `./gradlew :lib-core:test --tests '*ComponentMapDeserializerTest'` (known, unknown, unbindable, component `enabled`, missing `flags`).
- [x] 1.2a Delete `SourceBackedComponent` and its uses (`EcsLoader`, `JsonProcessor.bindComponent`/`componentTree`); give `FoliageComponent` an any-setter/any-getter extras map. Verify: `./gradlew :lib-core:test --tests '*FoliageComponentTest'` (unknown member and `1.0E-4` extra kept, `assetName` string/null/non-string) and `:lib-core-editor:test --tests '*FoliageEcsRoundTripTest'`.
- [x] 1.3 `AbyssusDocumentFormat`: reject the `entities` wrapper and entity `enabled`, require integral `flags`. Verify: `*AbyssusDocumentFormatTest` cases for wrapper, old `enabled`, fractional/string `flags`.
- [x] 1.4 `SceneLoader` binds `ecs` in one step; `EcsLoader.loadToEngine(Map<Long, EntityDto>, engine)`; `RuntimeSceneLoader` updated; entity state from `flags`. Verify: `./gradlew :lib-core:test`.

## 2. Fixtures

- [x] 2.1 Write the conversion script and convert every `.scene` under `projects/**` and `toolsTest/fixtures`. Verify: `git diff --stat` shows only `ecs` blocks; `./gradlew :lib-core:test`.
- [x] 2.2 Rewrite test strings that build `{"entities":...}` or the nested `renderable` (lib-core, lib-core-editor, plugin, physics, Control Line tests; `scripts/convert-render-flat.py --ecs`). Verify: each module's `test` compiles.

## 3. lib-core-editor

- [x] 3.1 `SceneDocument`, `ComponentEditor`, `LightEntities`, entity removal and enabled toggling work on `flags` and the unwrapped `ecs`. Verify: `./gradlew :lib-core-editor:test --tests '*ComponentEditorTest'` plus `*EntityRemovalTest`, `*SetEnabledActionTest`, `*LightEntitiesTest`.
- [x] 3.2 `EcsWriter.write` emits the unwrapped map and `flags`; new entities are written with `components` and no `flags` (the `writeComponent` helper stays; the class is not retired because every component writer uses it). Verify: `*AssetEntitiesTest`, `*EnabledFlagRoundTripTest`, and a test that an edit leaves number text and an unknown entry unchanged.
- [x] 3.3 `SceneContent`, `SceneRenderParams`, `RaySceneSnapshot`, `RayViewFeed`, `PanelBuilders` read `Map<Long, EntityDto>`. Verify: `./gradlew :lib-core-editor:test`.

## 4. Plugin and game

- [x] 4.1 `ProjectReader`, `PhysicsOverlayGeometry`, `SceneRenderer`, project view nodes use typed entities. Verify: `./gradlew :plugin-abyssus:test`.
- [x] 4.2 Control Line tools and tests (`FieldFoliage`, `TestField`, `trainer.patch.json` paths) on the new shape. Verify: `./gradlew :app-game-control-line:test`.
- [ ] 4.3 runIde check on a copy of `Untitled`: open the scene, drag an object, toggle an eye, Undo; the file diff touches only the changed values.

## 5. Docs and specs

- [x] 5.1 Update `docs/ai/file-formats.md` (`ecs`, enabled, game components, carried components, flat render component), `projects/lib-core-editor/README.md`, `projects/lib-core/README.md` (drop the `SourceBackedComponent` row) and `docs/ai/architecture.md` (foliage no longer carries a source object). Verify: `scripts/check-docs.sh`.
- [x] 5.2 Amend the other open changes' deltas that show the old `components` shape.

## 6. Final

- [x] 6.1 Run `./gradlew check` and `scripts/check-docs.sh`. Result: docs check passes; `check` is red only on 14 tests that also fail on a clean `HEAD` (3 `PathArrangementComponentTest`, 3 `ArrangementEditsTest`, 2 `ComponentEditorTest`, `PhysicsFixtureTest`, `EditorReadBaselineTest.testTreeRowsAndPanelState`, `AddPathArrangementActionTest`, `ComponentActionsTest.testRenderIsOfferedPerProjectAsset`, 2 `ArrangementEditingTest`).

## 7. Entity header (follow-up)

- [x] 7.1 Replace `IdComponent`, `EnabledState` and `TypeComponent` by `HeaderComponent`; the entry serializes `type`, `flags` and `disabledComponents`; `RawComponent` stores its JSON as text. Verify: `./gradlew check` (only the 14 failures that also fail on a clean `HEAD`).
