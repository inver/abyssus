# Tasks

## 1. Settle the file shape

- [x] 1.1 Define the plugin light entity structure in design Decision 3: Name, Type, Position and Light, nested color/intensity/optional range, matching archetype and missing component identifiers. Record the observed Mundus fixture as provenance only; Mundus compatibility is outside scope by explicit user decision. Verify the design and creation spec agree.

## 2. Light data and range

- [x] 2.1 Add `range` to `LightData`, write it in `LightCodec` only when it is not 100, and add the `range` field (positive-number check) to the `LightComponent` kind in `ComponentEditor`; verify with new cases in the existing codec and `ComponentEditor` tests (`./gradlew :test --tests '*ComponentEditorTest'`): set `30`, set back to `100` removes the key, `0` and `abc` are rejected, a file without `range` round trips byte for byte.
- [x] 2.2 Show the field in the properties panel and add its label strings to `AbyssusBundle.properties`; verify with a case in `EntityPropertiesPanelTest` that a light entity has a `field-LightComponent-range` editor that writes `30` (`./gradlew :test --tests '*EntityPropertiesPanelTest'`).
- [x] 2.3 Document `range` in `docs/ai/file-formats.md` (the `LightComponent` row) and verify with `scripts/check-docs.sh`.

## 3. Creating a light entity (headless)

- [x] 3.1 Add `LightPreset` (the three rows of design Decision 1) and `LightEntities.add` in `ecs/scene/` with the id, name, archetype and `componentIdentifiers` rules of design Decisions 2 and 3; verify with a new `LightEntitiesTest` over the `Untitled` scene text: Directional into `Main Scene` makes entity `7` named `Directional Light 7`, entities `0` to `6`, `archetypes` 1-3 and `metadata` are unchanged, an empty scene gives id `0`, a second light reuses the archetype made by the first (`./gradlew :test --tests '*LightEntitiesTest'`).
- [x] 3.2 Pin the presets against the reader: in `LightEntitiesTest`, load each result through `SceneContent` and assert Directional and Sun resolve to `LightKind.DIRECTIONAL` with direction `(0, -0.707, -0.707)` and a lower direction for Sun, Spot to `LightKind.SPOT` at the placement point plus `+5` Y with direction `(0, -1, 0)`; verify with the same test class.
- [x] 3.3 Verify the written scene loads: in `LightEntitiesTest`, run the result through `SceneEcsLoader` and assert no warnings and the entity's components; verify with the same class.
- [x] 3.4 Document the entity-creation function and the presets in `src/main/kotlin/net/nevinsky/abyssus/ecs/README.md` and the new entity shape in `docs/ai/file-formats.md`; verify with `scripts/check-docs.sh`.

## 4. The edit and the actions

- [x] 4.1 Add `SceneComponentEdits.addLight` using `editSceneJson`, with a `commandAddLight` name in `AbyssusBundle.properties`; verify with a case in the existing `SceneComponentEdits` test that one add is one undo step restoring the exact file text, and that a scene that is not JSON is rejected with no write (`./gradlew :test --tests '*SceneComponentEditsTest'`).
- [x] 4.2 Add the `AddLightGroup` (choices Directional, Sun, Spot) and the tree right-click action registered in `plugin.xml`, shown only for a scene row and placing at the origin; verify with a case in `ComponentActionsTest` (or the nearest existing action test) that the choices are those three, the action is hidden on an entity row, and choosing Spot writes position `(0, 5, 0)` (`./gradlew :test --tests '*ComponentActionsTest'`).
- [x] 4.3 Add the Add Light drop-down to the `SceneViewPanel` toolbar, passing the orbit target, disabled while the scene text is unreadable, and select the new entity afterwards; verify with a headless panel test that the target `(10, 0, -4)` yields that position and that the unreadable-scene state disables it (`./gradlew :test --tests '*SceneViewPanelTest'`).
- [x] 4.4 Document the Add Light action in `src/main/kotlin/net/nevinsky/abyssus/sceneview/README.md` and the `projectView` README; verify with `scripts/check-docs.sh`.

## 5. Integration

- [x] 5.1 Run `./gradlew check` and `scripts/check-docs.sh`; verify both pass, or list the failing tests with their cause if they fail for reasons outside this change.
- [ ] 5.2 runIde on a copy of the `Untitled` project (never the fixture): (1) Add Light > Sun from the toolbar shows a marker at the orbit target and shades the models by it; (2) with the procedural sky selected, the sky's sun moves to follow a Directional light; (3) Add Light > Spot from the tree menu on the scene row creates a light at `(0, 5, 0)` and selects it; (4) set its range to `30` in the properties panel and the lit area shrinks; (5) Undo removes the light and the marker; (6) reopen the scene text and read the entity. Leave open and list the exact steps for the user if the IDE cannot be driven here.
