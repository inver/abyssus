# Tasks

## 1. Row presentation

- [x] 1.1 Add `rowPresentation(entry)` and bundle strings for `Scenes`, `Assets`, `N entities`, `N components`; verify with unit tests that `scenes`/`assets`/`ecs` rows get the labels and counts and other rows are unchanged (`./gradlew :test`)
- [x] 1.2 Use it in `DtoEntryNode.update` with gray secondary text, keeping the `unused` badge and dimming; update `AbyssusViewTest` expectations and verify the suite passes

## 2. ECS entity rows

- [x] 2.1 Fold `ecs/entities` in `childrenOf` so entities are direct children labelled by `NameComponent` name (id fallback) with a component count, and components are listed without the `Component` suffix; verify with tests on `Main Scene.scene` (entity `0` reads `Model 0`, `5 components`) and an unnamed entity
- [x] 2.2 Update `entityVisitAction` / `selectEntityInAbyssusView` for the folded structure; verify `EntitySelectionTest` (updated) and that a scene-view pick still selects the entity row

## 3. Icons

- [x] 3.1 Add the per-kind SVG icons (light and dark) from the board's Icon set and map node kinds and component names to them; verify with a test that every kind resolves to a non-null icon and unknown component names get the generic one
- [ ] 3.2 Apply the icons in `entryIcon`; verify the existing tree tests pass and, by `./gradlew runIde`, that scene, asset and component rows show distinct icons

## 4. Unused filter

- [x] 4.1 Add the "Show Only Unused Assets" `ToggleAction` through `addToolbarActions` with per-project persisted state, applied when the `assets` node lists its children; verify with tests that the list shrinks to unused assets, its count follows, and turning it off restores all

## 5. Footer

- [x] 5.1 Wrap the pane component with the `N scenes · N assets · N unused` footer, counting from `AssetReadCache` off the EDT and refreshing with the view; verify with tests on the Untitled fixture (counts) and with the filter on (counts unchanged), and that `AbyssusProjectViewPaneTest` still passes

## 6. Wrap-up

- [ ] 6.1 Update the README's Abyssus view section and the CHANGELOG, and compare the running tree with the "Panels" board via `./gradlew runIde`
