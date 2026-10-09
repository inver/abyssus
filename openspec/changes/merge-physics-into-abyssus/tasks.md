# Tasks

Reconciled against the working tree on 2026-10-09. Module restructuring is already present. Only task 1.1 is
implemented; the existing settings service is partial. This refresh records source evidence, not a fresh test run.
Single plugin classes: `./gradlew :plugin-abyssus:test --tests '<fully-qualified-class>'`. Manual IDE checks use copies
of `projects/plugin-abyssus/src/test/testData/project/{Untitled,Physics}` and
`projects/app-game-control-line/project/ControlLine`, never the committed projects.

## 1. Complete the project physics switch

- [x] 1.1 Add the platform-free `dto/ProjectSettingsReader` with native admission and boolean decoding. Source evidence:
  `ProjectSettingsReaderTest` covers missing key, true, non-boolean, future version and missing marker.
- [ ] 1.2 Complete the existing `filetype/AbyssusProjectSettings` service: delegate admission/decoding to the reader,
  read unsaved documents, handle document/VFS changes and Undo/Redo, publish on EDT, deduplicate localized problems,
  and retain undoable `editSceneJson` writes. Extend `dto/AbyssusProjectSettingsTest` to verify unsaved edits notify
  before Save, Undo/Redo updates the cache without Save, unsupported files with `physicsEnabled: true` stay off and
  unwritable, malformed values report once without rewriting, equal-value toggles create no edit, and independent
  native projects do not share settings. Keep the service in `filetype/` to respect current package boundaries.
- [ ] 1.3 Add `"physicsEnabled": true` only to
  `projects/plugin-abyssus/src/test/testData/project/Physics/Physics.abss` and
  `projects/app-game-control-line/project/ControlLine/ControlLine.abss`. Add actual-fixture reader coverage; retain
  Untitled's missing-key test. Update `docs/ai/file-formats.md` for the optional switch and `.abss` writer. Verify
  reader tests, `./gradlew :lib-core:test :app-game-control-line:test` and `scripts/check-docs.sh`.

## 2. Merge the physics plugin and packaging

- [ ] 2.1 Move physics sources/resources/tests into `projects/plugin-abyssus`, preserving package
  `net.nevinsky.abyssus.plugin.physics` and `AbyssusPhysicsBundle`. Move overlay, simulation and notification
  registrations, remove the dangling physics schema contribution and unused `schemaExport` configuration, and add
  `<incompatible-with>net.nevinsky.abyssus.physics</incompatible-with>`. Remove the old module and settings/build/CI
  references. Verify `./gradlew :plugin-abyssus:test --tests 'net.nevinsky.abyssus.plugin.physics.*'` and confirm no
  active build/config reference names the removed module.
- [ ] 2.2 Add non-transitive `:lib-physics` to the main plugin. Transfer `playHostLibs`, sandbox copy and test host
  property from `plugin-abyssus-physics.gradle.kts`; there are no schema export tasks to transfer. Move `checkNoJolt`
  with current package names and add `checkNoJoltInZip` to `check`, scanning only IDE-loaded `lib/`. Verify both
  checks, `BundledPlayHostTest`, `PlayLaunchTest` and zip contents: physics jar in `lib/`, no Jolt there, full host
  dependencies/natives in `play-host/`.
- [ ] 2.3 Update `AGENTS.md`, `docs/ai/architecture.md`, `projects/lib-physics/README.md` and `CHANGELOG.md` for one
  plugin, its packaging boundary and migration. Keep general third-party classloader guidance where still relevant.
  Verify `scripts/check-docs.sh` and all documented Gradle module/task names against current build scripts.

## 3. Implement editable physics with typed component kinds

- [ ] 3.1 Allow constructor-injected `ComponentKind` contributions to `lib-core-editor`'s `ComponentEditor`, with
  unchanged built-in defaults. Implement headless physics kinds/codecs in the plugin for rigid body, collider and
  constraint fields, matching defaults, positivity and reference validation. Cover short and fully qualified keys,
  omitted defaults, unknown-field/number preservation and rejected edits in `PhysicsComponentKindsTest`; verify
  `ComponentEditorTest`, `:lib-core-editor:checkNoSingletons`, no platform classpath and package-cycle checks.
- [ ] 3.2 Have `ComponentSchemas.editorFor(sceneFile)` choose typed physics contributions only when that scene's
  native project enables physics; subscribe to `filetype/ProjectSettingsListener` and refresh the panel. Verify
  `PhysicsComponentsGateTest`: off excludes all three add choices and preserves raw existing data; enabling makes
  them editable without reopening; disabling or Undo restores read-only sections; stale panel edits cannot bypass
  the gate. Do not use nonexistent schema snapshots/resources or restore general schema loading.
- [ ] 3.3 Update `projects/lib-core-editor/README.md`, `docs/ai/architecture.md` and the properties package notes to
  describe injected physics kinds and continued read-only game components. Verify `scripts/check-docs.sh` and the
  documented composition against `ComponentSchemas` and the new kind adapter.

## 4. Restore overlay drawing and gate provider availability

- [ ] 4.1 Restore collider and constraint segments with typed/default-aware JSON decoding and no schema/Jolt access.
  Replace `PhysicsOverlayGeometryTest`'s current empty-output assertions with checks for box/sphere/capsule,
  height-field outline, hull marker, scaled offset, motion colors, selected passes, constraint anchors and
  preview/simulated transforms. Resolve assets from the scene's native project and remove the overlay's
  `BaseCtx(project.projectFilePath!!, ...)` wiring. Verify headless geometry tests, unchanged input JSON and
  `:plugin-abyssus:checkNoJolt`.
- [ ] 4.2 Add default-true `isAvailable(project, file)` to both providers in `sceneview/SceneExtensions.kt`, implement
  physics gating and update overlay hosting and Play selection. React to settings changes without reopening;
  unavailable providers contribute no actions/draws and available third-party providers remain usable. Verify
  `SceneExtensionsAvailabilityTest` for off/on transitions, two native projects, outside-project scenes, source
  providers using defaults and already compiled providers implementing only the old interface methods.
- [ ] 4.3 Stop active physics Play through the existing stop path on disable, including STARTING and PAUSED; reject
  late callbacks/poses and restore authored content. Verify `PlayStateTest` with off/on transitions and exactly one
  process stop. Verify that disabling built-in physics does not stop an unrelated available third-party simulation.
- [ ] 4.4 Update the extension/Play/overlay sections of `docs/ai/architecture.md` and sceneview README for availability
  and restored geometry. Verify `scripts/check-docs.sh`; inspect draw wiring for safe AWT `GdxRuntime.withContext`
  use and ensure the settings read path does not parse or perform file IO per frame.

## 5. Project properties and manual behavior

- [ ] 5.1 Add `PanelState.Project` and `ProjectDetailsView` alongside the existing `UISceneState`/scene ray properties.
  Bind the checkbox and problems to settings updates, make unsupported documents read-only, and localize plugin
  text. Verify `PanelStateTest` and panel tests: Untitled off, Physics on, unsupported project cannot be toggled,
  unsaved edits and Undo refresh the checkbox, and selecting a scene still shows existing scene properties.
- [ ] 5.2 Update the README plugin description and projectView/properties package notes for built-in per-project
  physics. Verify `./gradlew :plugin-abyssus:patchPluginXml` and `scripts/check-docs.sh`.
- [ ] 5.3 Manual check with `:plugin-abyssus:runIde -PideProject=<copy of Untitled>`: select `.abss`, tick Physics,
  observe one-key edit and new overlay/Play actions, Undo and Redo, then change the unsaved project text. Confirm
  checkbox/actions follow immediately, physics components change between editable and raw JSON, and scene ray
  properties remain available. Turning off writes explicit false and touches no unrelated data.
- [ ] 5.4 Manual check with copies of Physics and Control Line: show green box on Model 0, play and observe it fall,
  pause/step/stop, disable Physics while playing and confirm Model 0 returns to authored Y `3.086434`. Confirm
  constraints/selection rendering, exported game Play and no file/Undo changes from playing. Install the old
  physics plugin alongside the build and confirm the IDE reports the incompatibility.

## 6. Integration

- [ ] 6.1 Run `./gradlew check`, `./gradlew :plugin-abyssus:buildPlugin`, `scripts/check-docs.sh` and
  `openspec validate merge-physics-into-abyssus --strict`; all pass. Review the final zip's IDE/host dependency
  separation and confirm no stale old-module commands remain in active docs or CI configuration.
