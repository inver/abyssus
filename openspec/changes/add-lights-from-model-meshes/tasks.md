# Tasks

## 1. Lamp geometry and light parameters (lib-core-editor, headless)

- [x] 1.1 Add `ModelMaterialInfo(id, emissive)` and report it from `ModelMeshLister`; verify with a new case in the lister's test (emissive listed, black or absent gives null) via `./gradlew :lib-core-editor:test --tests '*ModelMeshListerTest'`.
- [x] 1.2 Add the `modellights` package: gather triangles of the chosen material (honoring `meshId`), apply node and entity transforms, weld vertices, group into pieces and compute bounds; verify with `ModelLampPiecesTest` on synthetic `ModelData` (separate pieces, touching pieces, entity move and scale, unused material).
- [x] 1.3 Add light parameters (emissive-or-white color, default intensity, range from size with a minimum) and merge-to-cap; verify with `LampLightParamsTest` and `LampMergeTest` (larger lamp longer range, over-cap result has exactly the maximum and `merged` set, deterministic order).

## 2. Scene edit and headless API

- [x] 2.1 Extend `LightEntities` with a point-light builder and add `ModelLights` as one `SceneEdit` that adds all lights (ids after the highest, names `<entity> Lamp N`) or returns `Rejected` with a message in `AbyssusEditorBundle.properties`; verify with `ModelLightsTest` (ids and names, other entities unchanged, non-model entity rejected, no geometry rejected) and `SceneRoundTripTest` still passing.
- [x] 2.2 Expose the operation in `HeadlessEditing` returning the added ids or a rejection; verify with a case in the headless editing test that the returned ids match the scene text.

## 3. Plugin action

- [x] 3.1 Add the tree action Create Lights From Model with availability judged from the entity type and a cheap asset check, a background read of the model with progress, a material chooser dialog (emissive first, piece count shown) and the write through `editSceneDocument`; strings in `AbyssusBundle.properties`; verify with `./gradlew :plugin-abyssus:test --tests '*CreateLightsFromModelActionTest'` (offered on a model entity, absent on a light, disabled on a missing asset, cancel writes nothing, one undo removes all).
- [ ] 3.2 Hand check in `./gradlew :plugin-abyssus:runIde` on a copy of the Control Line project, `Hangar` scene: (1) right-click the warehouse entity, choose Emissive, confirm the lamps get point lights at the lamp positions; (2) the Scene view shows blue light near each lamp; (3) one Ctrl+Z removes all of them; (4) the merged notice appears when the cap is lowered for the check.

## 4. Docs and final checks

- [x] 4.1 Update `docs/ai/glossary.md`, `projects/lib-core-editor/README.md` and the projectView README for the action and the bake-not-link behavior; verify with `scripts/check-docs.sh`.
- [ ] 4.2 Run `./gradlew check` and `scripts/check-docs.sh`; verify both pass.
