# Tasks

## 1. Entity insertion

- [x] 1.1 `SceneEntities.insert` and `matchArchetype`; `AssetEntities` delegates to them. Verify: `./gradlew :test
  --tests 'net.nevinsky.abyssus.ecs.scene.SceneEntitiesTest' --tests
  'net.nevinsky.abyssus.ecs.scene.AssetEntitiesTest'`:
  - an id one above the highest, and `0` in an empty scene;
  - a wrapped layout reuses or adds archetypes;
  - `matchArchetype` moves an entity to the archetype of its components;
  - the asset tests are unchanged.

## 2. Add Component on the ecs row

- [x] 2.1 `SceneComponentEdits.addAsNewEntity`, the ecs row target in `AddComponentAction`, the shared
  `addComponentGroup`, selection, and the bundle string `newEntityName`. Verify: `./gradlew :test --tests
  'net.nevinsky.abyssus.projectView.AddComponentOnEcsTest'`, on a copy of Untitled:
  - offered on `ecs` and not on the scene row;
  - Camera writes `Entity 9` with a camera and Undo restores the file;
  - Render → `tree` writes the render component;
  - no Name choice;
  - invalid JSON hides it;
  - a wrapped scene gets a matching archetype.
- [x] 2.2 Docs: `projectView/README.md` and the `README.md` user section. Verify: `scripts/check-docs.sh`.

## 3. Verification

- [ ] 3.1 runIde check 1: in a copy of Untitled, right-click `Main Scene` → `ecs` → Add Component... → Camera. Expected:
  a new `Entity 9` row with a camera is selected, and Undo removes it.
- [x] 3.2 Run `./gradlew check` and `scripts/check-docs.sh`. Report the failures that came before this change
  separately, with their causes.
