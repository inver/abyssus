# Tasks

## 1. Jackson migration

- [x] 1.1 Add `jackson-databind` (or the platform-provided Jackson) to `build.gradle.kts`, add one shared `ObjectMapper` in the `dto` package, and verify `./gradlew compileKotlin` succeeds and `verifyPlugin` reports no Jackson conflicts
- [x] 1.2 Port `dto/AssetReader.kt`, `dto/DtoProperty.kt` (`toDtoValue` over `JsonNode`) and `scene/SceneDto.kt` (`ecs: JsonNode?`) from Gson to Jackson, keeping tolerant reads that return failures instead of throwing; verify the existing reader/DTO tests pass unchanged for valid, empty and malformed files
- [x] 1.3 Port `sceneview/SceneRenderParams.kt` and `projectView/EnabledToggle.kt` to Jackson (write path via the shared mapper, nulls kept, no HTML escaping); verify the toggle test restores the original fixture bytes after toggling twice and `SceneRenderParamsTest` passes
- [x] 1.4 Remove every `com.google.gson` import; verify `grep -r "com.google.gson" src build.gradle.kts` returns nothing

## 2. Project assets

- [x] 2.1 Read `assets/*/meta.json` in `ProjectReader` into `assets` entries (folder name, `uuid`, `type`, the `splat*` and `materials` reference fields, unknown type on missing/malformed meta, empty list without an `assets` folder) placed after `scenes`; verify with reader tests on `Untitled` (seven entries in name order), a project without `assets`, and a folder with broken `meta.json`
- [x] 2.2 Seed the used set from all readable scenes (`assetName` and `shaderKey` values in `ecs`, plus `skyboxName`) and set `unused` on assets outside it; verify with tests that `tree` is unused, a model referenced by `Main Scene` is used, a skybox is used through `skyboxName`, a reference from only the second scene counts, and a malformed scene does not drop other scenes' references; add a fixture with a project `SHADER` folder and verify that one matching a `shaderKey` is used, an unreferenced one is unused, and a bundled key like `pbr` is ignored without error
- [x] 2.3 Close the used set transitively over `splatMap`/`splatBase`/`splatR`/`splatG`/`splatB`/`splatA` and `materials` `uuid` references with a worklist; verify with tests for a splat texture used via a used terrain, a material used via a used model, a material used only via an unused model (both unused), a reference cycle (terminates), and an unknown `uuid` (ignored, no failure)
- [x] 2.4 Extend `ProjectReader.stamp` to cover the asset folder listing and `meta.json` stamps; verify with a test that adding an asset folder changes the stamp
- [x] 2.5 Render the `assets` property and unused entries in `AbyssusNodes` (grayed text plus localized "unused" suffix) with new keys in `AbyssusBundle.properties`; verify with a tree-model test that unused entries expose the marker and used ones do not, and that reading leaves every fixture file unchanged

## 3. Integration

- [ ] 3.1 Document the assets listing, the unused rule and its non-goals in `README.md`; verify against `./gradlew runIde` on `Untitled`
- [x] 3.2 Run `./gradlew build` and `./gradlew test`; verify the full suite passes
