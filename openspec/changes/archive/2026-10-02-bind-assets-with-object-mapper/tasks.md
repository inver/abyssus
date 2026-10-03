# Tasks

## 1. Bind files to concrete objects

- [x] 1.0 Move the mapper, `RawNumberNode` tree reader and pretty printer from the `Json` object into `SceneJson`; keep `opt`/`text`/`float`/`obj` and `runCatchingKeepingCancellation` top-level in package `dto`; repoint `EnabledToggle`, `SceneRenderParams` (`MainCamera`), `ProjectAssets`, `ProjectReader` and the tests off `Json.*`; verify `SceneJsonTest`, `SceneEditFormattingTest` (including the `1.0E-4`/`2.50`/`-0.0` byte-identity case) and `CancellationTest` pass unchanged
- [x] 1.1 Configure the `SceneJson` mapper (lenient unknowns/coercion, declaration order) and bind `SceneDto`, `FogDto`, `BaseLightDto`, `ColorDto` from a `.scene` tree via `treeToValue`; verify `SceneReader.parse("{}")` yields an all-null `SceneDto` and the existing color/fog/intensity assertions in `AbyssusViewTest` pass
- [x] 1.2 Delete the manual `color()`/`opt()` parsing in `SceneReader`; verify a scene with a mistyped field yields the per-scene `error` entry under a project while siblings still load (new test)
- [x] 1.3 Add `ProjectDto` (`name`, `scenes` with `SceneError` elements, `assets`) and bind the project's assets into `AssetInfo` with `@JsonIgnore` `name`/`references`/`unused`; verify `ProjectAssetsTest` still passes with type/unused assertions on `AssetInfo`

## 2. Render typed objects in the tree

- [x] 2.1 Make `DtoEntry`/`DtoEntryNode` hold the typed value and enumerate children via Jackson introspection (objects), index (lists) and `JsonNode` fields (`ecs`); verify row names and order equal today's for a scene, a project and an asset (only `type`, `uuid`) in a property-order test
- [x] 2.2 Port `foldToggles`, scene label, `sceneFileOf`/`sceneName`, the asset-type icon and the unused-asset graying to the typed model; verify `AbyssusViewTest`, `AbyssusProjectViewPaneTest`, `EntitySelectionTest` and `SceneEditFormattingTest` pass without `DtoValue`
- [x] 2.3 Keep `toggleEnabled`/`renameScene` writing through the `JsonNode` tree; verify the double-toggle byte-identity test still passes

## 3. Remove the old model

- [x] 3.1 Delete `DtoProperty.kt` (`DtoProperty`, `DtoValue`, `DtoSource`, `toDtoValue`), every `properties()`/`toValue()`, and the `Json` object; verify `grep -rE "DtoValue|DtoProperty|DtoSource|\bJson\." src` is empty and `./gradlew build` compiles
- [x] 3.2 Update `README.md` and any `AGENTS.md`/`docs/ai` page (if `add-ai-first-docs` landed) that describe the DTO tree or `Json`; verify the text matches the code (and `scripts/check-docs.sh` passes if present)
- [x] 3.3 Integration check: `./gradlew test` is green and the Abyssus view for the sample `.abss` shows the same tree as before
