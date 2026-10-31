# Tasks

## 1. Prerequisites

- [ ] 1.1 Finish `typed-scene-ecs` task 4.3 and archive it; verify `openspec list` no longer shows it and `./gradlew check` passes on the branch.
- [ ] 1.2 Amend remaining open-change deltas that say `ecs` lives in the scene (grep `openspec/changes/*/specs` for `ecs`); verify `openspec validate --all` passes.

## 2. Format and loading (lib-core)

- [ ] 2.1 Add the ECS path to `AbyssusProjectLayout`/`FileLoader` (`scenes/<Scene>.ecs.json`); verify a layout test covers the derived path and extension matching.
- [ ] 2.2 Reject `ecs` in a `.scene` and validate the array (root is an array, integral unique `id`, entity rules) in `AbyssusDocumentFormat`; verify tests for old inline `ecs`, object root, duplicate and missing id.
- [ ] 2.3 Make `SceneParser`/`SceneLoader` read scene text plus optional ECS text and bind once through `JsonProcessor`; verify tests for file load, unsaved buffers of each document, missing file and sparse ids.
- [ ] 2.4 Feed `RuntimeSceneLoader`, `BaseCtx` and `EcsLoader` from the pair; verify `./gradlew :lib-core:test` and `:lib-core:checkNoSingletons` pass.
- [ ] 2.5 Update `projects/lib-core/README.md`; verify `scripts/check-docs.sh`.

## 3. Editing (lib-core-editor)

- [ ] 3.1 Add the scene document pair and make `DocumentTextEditor`/`DocumentApi` edit either tree and report changed documents, keeping number text and key order; verify tests for entity-only, scene-only and both-document edits.
- [ ] 3.2 Move `SceneContent`, `SceneRenderParams`, `PanelBuilders`, `ComponentEditor`, `EntityRemoval`, `EcsWriter`, `Placements`, arrangement and ray readers/writers to the array; verify `./gradlew :lib-core-editor:test`.
- [ ] 3.3 Extend `HeadlessEditing` to take and return the pair (new ECS text when the first entity is added); verify a headless test and `NoPlatformClasspathTest`.
- [ ] 3.4 Update `projects/lib-core-editor/README.md`; verify `scripts/check-docs.sh`.

## 4. Plugin

- [ ] 4.1 Make `editSceneJson` write the pair as one undoable command and skip unchanged documents; verify a test that one Undo restores both files.
- [ ] 4.2 Load the scene view, `ProjectReader` and physics overlay from the pair with unsaved buffers of both files; verify `SceneViewPanelTest` and an edit in the ECS buffer refreshing the view.
- [ ] 4.3 Rename Scene renames both files; keep the ECS file out of the scene list, route opening it to its scene, and format it only beside a supported scene; verify project view tests.
- [ ] 4.4 Update plugin READMEs (`sceneview`, `projectView`); verify `scripts/check-docs.sh`.

## 5. Play and migration

- [ ] 5.1 Carry both files in the play launch file and `exportPlay` (`lib-game-link`); verify `./gradlew :lib-game-link:test`.
- [ ] 5.2 Convert the 10 `.scene` files with a one-time script (not committed); verify the diff shows only moved entities and `ecs` removed, and entity count and per-entity JSON equal the old content.
- [ ] 5.3 Point Control Line's loader, tests and packaging at the pair; verify `./gradlew :app-game-control-line:test` and `:app-game-control-line:checkShippedClasspath`.
- [ ] 5.4 Update `docs/ai/file-formats.md`, `docs/ai/architecture.md`, `docs/ai/testing.md` as needed and the Control Line README; verify `scripts/check-docs.sh`.

## 6. Integration

- [ ] 6.1 Run `./gradlew check` and verify it passes.
- [ ] 6.2 Run `./gradlew :plugin-abyssus:runIde` on a copy of `Untitled`: drag an object, toggle an eye, rename the scene, Undo each; verify each Undo is one step and file diffs touch only the changed values.
