## 1. Verify facts before writing

- [x] 1.0 Confirm the docs target HEAD: the working tree has no uncommitted code changes, and `show-project-assets` (still open) is described only where its code is committed
- [x] 1.1 Confirm commands in `AGENTS.md` by running them: `./gradlew check` (or `test`), `./gradlew :gdx-model:test`, one test via `--tests`, `./gradlew runIde`, `./gradlew buildPlugin`; note `-PideProject=`, `-Dabyssus.openView=true` and `-Dabyssus.glTests=true` from the two `build.gradle.kts` files
- [x] 1.2 Read `GdxRuntime.kt`, `GuardedGLCanvas.kt`, `SceneViewPanel.kt` and confirm the threading/global `Gdx.*` rules; read `JsonNodes.kt`, `SceneJson.kt`, `EnabledToggle.kt` (`editSceneJson`), `SceneTransformWriter.kt` and `SceneEditFormattingTest.kt` and confirm the single write path and its formatting-preservation and undoable-edit rules
- [x] 1.3 Read `ProjectLayout.kt`, `ProjectAssets.kt`, `AssetReader.kt` and `SceneDto.kt` to document project layout, asset reachability and the format fields accurately; read `gdx-model/README.md` and `gdx-model/build.gradle.kts` for the module boundary; read `ecs/scene/SceneEcsLoader.kt`, `SceneEcsWriter.kt` and `ComponentCodecs.kt` for the ECS round trip

## 2. Entry points

- [x] 2.1 Write `AGENTS.md` (<= ~100 lines): purpose, commands, layout map, hard rules with reasons, OpenSpec workflow, doc links
- [x] 2.2 Write `CLAUDE.md` containing only an `@AGENTS.md` import

## 3. Reference pages

- [x] 3.1 `docs/ai/architecture.md`: modules (plugin, `gdx-model`), components, data flow (file -> reader -> DTO tree -> project view -> properties panel; scene file -> SceneFileEditor -> SceneContent/SceneRenderer, picking -> entity selection; gizmo drag -> SceneTransformWriter -> editSceneJson -> re-read; look-through camera; ECS load and Mundus write-back), threading, extension points (`AssetReader.forExtension`, `plugin.xml`)
- [x] 3.2 `docs/ai/file-formats.md`: `.scene`, `.abss`, asset `meta.json`, reachability rules (link to README section, do not duplicate)
- [x] 3.3 `docs/ai/glossary.md`: scene, project, asset, unused asset, ECS, component codec, Mundus, Abyssus view, eye toggle, gizmo, look-through camera, stable-size gate
- [x] 3.4 `docs/ai/conventions.md`: Kotlin everywhere except the grammar sources, generated code boundary, `gdx-model` dependency rule, Jackson usage, cancellation-safe error handling (`runCatchingKeepingCancellation`), message bundle, naming
- [x] 3.5 `docs/ai/testing.md`: test layout in both modules, platform test fixtures, `testData/project/Untitled` and `Animated`, how to run one test, opt-in GL tests (`-Dabyssus.glTests=true`, `GlHarness`), GL-free test seams (`SceneView` interface)
- [x] 3.6 `docs/README.md`: index and source-of-truth table (`docs/ai`, `docs/superpowers`, `openspec/specs` for required behavior, `openspec/changes/archive` for change history), note that in-flight changes add doc-update tasks

## 4. Existing files

- [x] 4.1 Rewrite `README.md` template text into a real description; keep `<!-- Plugin description -->` markers; keep the Abyssus view section; fix `PLUGIN_ID` badge/ToDo leftovers only if the owner confirms (otherwise leave and list in the final report)
- [x] 4.2 Add `sceneview/README.md`, `projectView/README.md` and `ecs/README.md` under `src/main/kotlin/net/nevinsky/abyssus/`
- [x] 4.3 Add a `CHANGELOG.md` "Unreleased" entry for the documentation

## 5. Guard against rot and verify

- [x] 5.1 Add `scripts/check-docs.sh` failing on any backtick-quoted repo path (per design decision 6: contains `/` or a file extension, no spaces or globs) in `AGENTS.md`/`docs/ai/*.md` that does not exist; verify it skips `Gdx.*` and `./gradlew check`, fails on a deliberately wrong path, then run it and fix findings
- [x] 5.2 Run `./gradlew patchPluginXml` to confirm the README description extraction still works
- [ ] 5.3 Cold-read test: in a fresh session, ask an agent to find how to run one test and where `.abss` reachability is computed using only `AGENTS.md` and links; fix gaps
