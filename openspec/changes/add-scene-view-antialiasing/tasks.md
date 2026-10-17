# Tasks

## 1. Setting and fallback logic

- [x] 1.1 Add plain antialiasing levels and the ordered sample fallback. Verify `AntialiasingLevelTest` and `SampleFallbackTest`.
- [x] 1.2 Replace application persistence with native `.abss` `sceneViewAntialiasing` parsing and undoable edits in
  `AbyssusProjectSettings`. Validate native markers, omit 4×, reject unsupported documents, report malformed values,
  preserve unrelated text and keep projects independent. Verify `ProjectAntialiasingSettingsTest`.

## 2. Project properties

- [x] 2.1 Remove the IDE configurable and application service. Add the localized combo to project Properties, default
  4×, disabled for unsupported projects. Verify document persistence, Undo/Redo, external text changes, unchanged
  scenes/assets and removal of the application configurable in `ProjectAntialiasingPropertiesTest`.

## 3. Scene view

- [x] 3.1 Bind editors to their owning native project's settings, including new/replaced views, changes, Undo/Redo,
  disposal and independent projects. Standalone scenes use 4×. Preference edits must not stop Play. Verify `SceneFileEditorTest`.
- [x] 3.2 Recreate canvases safely, defer hidden replacements and fall back on refused formats, keeping CPU editing,
  Play and Ray modes. Verify `SceneViewPanelAntialiasingTest`.
- [x] 3.3 Verify actual default-framebuffer multisampling and live/hidden canvas changes in `SceneViewAntialiasingGlTest`
  with `-Dabyssus.glTests=true`.

## 4. Docs

- [x] 4.1 Update README, CHANGELOG, file-format and architecture docs, package notes and verification record to describe
  project Properties and `.abss` persistence. Run `scripts/check-docs.sh` and strict OpenSpec validation.

## 5. Manual checks

- [ ] 5.1 On a fixture copy, verify default smooth edges, project Properties choices, selection/camera/Play/Ray retention,
  hidden tabs, persistence after reopening, independent projects and macOS minimise/restore. Leave open if unavailable.

## 6. Verification

- [ ] 6.1 Run `./gradlew check`; record results and any unresolved failures without claiming a green suite.

## Commands

Use `./gradlew :plugin-abyssus:test --tests '*<ClassName>'` for each plugin class. Manual IDE launch:

```sh
aa_project=$(mktemp -d /private/tmp/abyssus-aa-ide.XXXXXX)
cp -R projects/plugin-abyssus/src/test/testData/project/Untitled "$aa_project/Untitled"
./gradlew :plugin-abyssus:runIde -PideProject="$aa_project/Untitled"
```

Previous implementation and repair evidence is in [verification.md](verification.md); the project-persistence revision
requires fresh verification of the reopened tasks.
