# Tasks

## 1. Entity building

- [x] 1.1 `AssetEntities` in `ecs/scene`:
  - `canAdd`, with the same rules as `LightEntities`;
  - `add(root, asset, position, terrainSize)` for native and wrapped layouts, with the name, type, position and render
    component as specified, and terrain centring.

  Verify: `./gradlew :test --tests 'net.nevinsky.abyssus.ecs.scene.AssetEntitiesTest'`:
  - `tree` into `Main Scene` gives entity `9` `Model 9` and leaves entities `0`-`8` unchanged;
  - the terrain gives `Terrain 9` at `(-800, 0, -800)`;
  - an empty scene gives id `0`;
  - a wrapped layout gets an archetype;
  - a non-native scene is rejected.

## 2. Actions

- [x] 2.1 `SceneComponentEdits.addAsset`, `AddAssetGroup` (Models / Terrains), `AddAssetAction` on scene rows, the
  generalised `selectCreatedEntity`, the bundle strings and `plugin.xml`. Verify: `./gradlew :test --tests
  'net.nevinsky.abyssus.projectView.AddAssetActionTest'`, a platform test on a copy of Untitled:
  - the group lists the five models and the terrain, and no skybox;
  - choosing `tree` writes entity `9` and Undo restores the file;
  - the action is disabled for invalid JSON and for a scene outside a project.
- [x] 2.2 Scene view: the "Add Asset" button in `SceneViewPanel`, wired in `SceneFileEditor` with the orbit target.
  Verify: `./gradlew :test --tests 'net.nevinsky.abyssus.plugin.sceneview.SceneViewPanelTest'` gains a case: the button exists,
  follows the editing state, and its choices place `tree` at the orbit target.
- [x] 2.3 Docs: `README.md` user section, `projectView/README.md`, `sceneview/README.md`. Verify: `scripts/check-docs.sh`.

## 3. Verification

- [ ] 3.1 runIde check 1:
  - in a copy of Untitled, right-click `Main Scene` → Add Asset → `tree`: a model appears at the origin and its row is
    selected;
  - in the Scene view, Add Asset → the terrain: it is centred on the orbit point;
  - Undo removes each.
- [x] 3.2 Run `./gradlew check` and `scripts/check-docs.sh`. Report the failures that came before this change
  separately, with their causes.
