# Tasks

## 1. Native UI model and project registration

- [x] 1.1 Add MetaType.UI and AssetMetaBinder support, UI metadata decoding, schema validation and immutable screen/widget/resource definitions in lib-core. Verify UiDocumentAdmissionTest cases for valid metadata, missing/foreign markers, fractional/future versions, duplicate IDs, unknown widgets, malformed geometry, depth/node/image limits and unknown-widget preservation with ./gradlew :lib-core:test --tests '*UiDocumentAdmissionTest'.
- [x] 1.2 Add optional uiAssetNames project decoding and registered UI lookup without inserting omitted defaults. Verify UiProjectRegistryTest cases for absent lists, duplicate/missing/wrong-type targets and native admission with ./gradlew :lib-core:test --tests '*UiProjectRegistryTest'.
- [x] 1.3 Extend project asset usage traversal for registered UI roots and cross-asset navigation; preserve existing scene usage. Verify ProjectAssetUsageTest cases for registered, unregistered, missing and cyclic UI references and existing scenarios with ./gradlew :plugin-abyssus:test --tests '*ProjectAssetUsageTest'.
- [x] 1.4 Document the UI schema, registry, versioning and minimal screen/binding examples in docs/ai/file-formats.md and projects/lib-core/README.md. Verify examples through UiDocumentAdmissionTest and run scripts/check-docs.sh.

## 2. Shared layout, binding state and navigation

- [x] 2.1 Implement pure reference-size fitting, anchors, row/column measurement, grow, clipping and resolved bounds. Verify UiLayoutTest cases for 16:9/4:3 fitting, nested containers, overflow, text measurement input and invalid dimensions with ./gradlew :lib-core:test --tests '*UiLayoutTest'.
- [x] 2.2 Implement typed snapshots, fallbacks, widget-property compatibility, constraints and validated write results. Verify UiBindingTest cases for live values, zero maximum, read-only targets, normalization, rejection, commit timing (slider release, checkbox toggle, text Enter/focus loss) and no publication feedback loops with ./gradlew :lib-core:test --tests '*UiBindingTest'.
- [x] 2.3 Implement screen history, overlay stack, action dispatch and focus/input routing. Verify UiNavigationTest cases for back-at-root, cross-asset targets, missing targets, Escape (overlay, back, root no-op), modal blocking, nonmodal pass-through, focus restoration and one action per activation with ./gradlew :lib-core:test --tests '*UiNavigationTest'.
- [x] 2.4 Document host snapshot/write/action contracts, callback ownership and layout semantics in projects/lib-core/README.md. Verify documented usage compiles in UiHostContractTest with ./gradlew :lib-core:test --tests '*UiHostContractTest'.

## 3. Runtime renderer and resources

- [x] 3.1 Add bundled styles and confined image/bitmap-font resource loading with explicit preparation/build/disposal ownership. Verify UiResourceTest cases for missing resources, path/symlink escape, network resources, oversized images, font pages, fallback and style priority with ./gradlew :lib-core:test --tests '*UiResourceTest'; record bundled resource licenses.
- [x] 3.2 Implement shared Scene2D actor rendering for every specified widget, consuming resolved layout and binding/navigation state. Verify UiRendererTest geometry, clipping, focus and styling cases with ./gradlew :lib-core:test --tests '*UiRendererTest' -Dabyssus.glTests=true on a display-capable host.
- [x] 3.3 Add a separate UI demo/test host in app-game-control-line with its own fixture project (authored menus, HUD, settings and changing data), not project/ControlLine, and without changing normal game launch or committed game scene files. Verify UiDemoHostTest accepted/rejected writes and action delivery with ./gradlew :app-game-control-line:test --tests '*UiDemoHostTest'; document and manually run its dedicated launch command.
- [x] 3.4 Document renderer lifecycle, image/font import formats and the standalone demo command in the affected module READMEs. Verify scripts/check-docs.sh and that the documented demo command launches the dedicated UI host.

## 4. Pure authoring and transactional persistence

- [x] 4.1 Add UiDocumentEdits for screens and widgets: create, rename, duplicate, reparent, reorder, delete, properties, styles, bindings and actions. Verify UiDocumentEditsTest exact-text undo inputs, unknown-field preservation, duplicate-ID repair on duplication, hierarchy refusal and stale revisions with ./gradlew :lib-core-editor:test --tests '*UiDocumentEditsTest'.
- [x] 4.2 Add pure selection, hit testing and gesture-to-layout transformations for panels and row/column children. Verify UiLayoutEditsTest scaled coordinates, clipping, drag reorder, reparent conversion and one final command per gesture with ./gradlew :lib-core-editor:test --tests '*UiLayoutEditsTest'.
- [x] 4.3 Add plugin adapters using editSceneJson for existing metadata/project edits, and coordinated staged asset/resource creation with project registration. Verify UiAssetCommandTest creation undo/redo, no-overwrite, read-only targets, name collisions, partial-failure rollback, unsaved text and concurrent edits with ./gradlew :plugin-abyssus:test --tests '*UiAssetCommandTest'.
- [x] 4.4 Document editing APIs and creation-versus-edit persistence boundaries in projects/lib-core-editor/README.md and docs/ai/architecture.md. Verify scripts/check-docs.sh and ./gradlew :lib-core-editor:checkNoSingletons checkPackageCycles.

## 5. Plugin visual authoring

- [x] 5.1 Register UI asset creation/opening and register/unregister actions, with a visual editor for UI meta.json alongside text. Verify UiEditorProviderTest supported/unsupported admission, other metadata editor behavior and project ownership with ./gradlew :plugin-abyssus:test --tests '*UiEditorProviderTest'.
- [x] 5.2 Wire widget tree, screen list, palette, canvas selection/gestures and properties to pure models. Verify UiEditorControllerTest selection synchronization, gesture commits, undo, text refresh and stale worker results with ./gradlew :plugin-abyssus:test --tests '*UiEditorControllerTest'.
- [x] 5.3 Add style/resource import, binding and action property editors with localized diagnostics. Verify UiPropertiesTest type-sensitive controls, read-only binding warnings, missing target feedback and resource import transactions with ./gradlew :plugin-abyssus:test --tests '*UiPropertiesTest'.
- [ ] 5.4 Perform runIde check 1 on a copy of Untitled: create menus, add every widget type, build an anchored HUD and centered column, reparent/duplicate/delete, style a button and import a bitmap font; undo/redo from the visual editor and confirm exact unrelated text preservation. Launch with ./gradlew :plugin-abyssus:runIde -PideProject=<copy>; record results separately from headless tests.
- [x] 5.5 Update README.md, CHANGELOG.md and plugin UI documentation for authoring, retaining plugin-description markers. Verify scripts/check-docs.sh and ./gradlew :plugin-abyssus:patchPluginXml.

## 6. Interactive preview and scene backdrop

- [x] 6.1 Implement isolated sample state, sample editors, reset, bounded action log and mode transitions. Verify UiPreviewStateTest interaction does not mutate documents, reset semantics, write-back validation and definition-change reset with ./gradlew :lib-core-editor:test --tests '*UiPreviewStateTest'.
- [x] 6.2 Integrate the shared renderer with guarded canvas input, target-resolution controls and selected scene backdrop, without simulation. Verify UiPreviewControllerTest unsaved admission, invalid-text suspension/recovery, stale results, resource reload and disposal with ./gradlew :plugin-abyssus:test --tests '*UiPreviewControllerTest'.
- [ ] 6.3 Perform runIde check 2: preview Main Scene behind a HUD at 1280x720 and 1024x768; navigate screens, tab through controls, adjust volume, reject an invalid value through sample constraints, change sample health and reset. Confirm startGame is logged, no game/physics process starts, documents and undo history are unchanged, and returning to editing restores selection.
- [ ] 6.4 Perform runIde check 3: open two UI editors, change unsaved JSON, break/fix a resource path, minimize/resize to zero, switch tabs and close focused preview editors. Confirm independent contexts, current-revision recovery, correct input release and resource disposal. Compare the same authored UI with the standalone demo at matching dimensions.
- [ ] 6.5 Document preview controls, sample isolation, fit/letterboxing and backdrop limitations in README.md and docs/ai/architecture.md. Verify scripts/check-docs.sh and confirm the documented sequence during runIde check 2.

## 7. Integration verification

- [x] 7.1 Run ./gradlew :plugin-abyssus:buildPlugin :plugin-abyssus:verifyPlugin and inspect packaging for required UI resources and absence of game/Jolt code in IDE-loaded libraries. Record unrelated verifier failures without silently expanding scope.
- [x] 7.2 Run ./gradlew check and scripts/check-docs.sh; record results and any unavailable GL/runIde checks explicitly, leaving their task boxes open until performed.

## Verification record (2026-10-11)

Automated, run and green:

- `./gradlew check` (all modules, includes `checkNoRunCatching`, `checkNoSingletons`, `checkPackageCycles`) and `scripts/check-docs.sh`.
- GL tests, run separately with `-Dabyssus.glTests=true` on a display: `lib-core` `UiResourceGlTest` and `UiRendererTest`;
  `plugin-abyssus` `UiCanvasViewGlTest` (6) and `UiSceneBackdropGlTest` (the real `SceneBackdrop` drawing a copy of Untitled's
  Main Scene, scene file unchanged); `app-game-control-line` `UiDemoGlTest`. The plain `check` skips these.
- `./gradlew :plugin-abyssus:buildPlugin`, `checkNoJolt`, `checkNoJoltInZip`; the zip holds the UI renderer and default theme in
  `lib-core.jar` and the editor in the plugin jar, and no game code or Jolt in the IDE-loaded libraries.
- `./gradlew :app-game-control-line:runUiDemo` launches the dedicated demo host and runs without error for 25 s (window not inspected).

Not run, so their boxes stay open: the three interactive `runIde` checks (5.4, 6.3, 6.4) and the `runIde` confirmation in 6.5.

Recorded, unrelated to this change: `verifyPlugin` reports the plugin compatible with all five target IDE builds, but its
`checkPluginInternalApis` step fails because it looks for `projects/plugin-abyssus/gradle/plugin-internal-api-allowlist.txt`
(the tracked file is `gradle/plugin-internal-api-allowlist.txt`) and the internal-API usages it lists are in
`AbyssusPropertiesToolWindowFactory`, `SceneFileEditorProvider`, `PhysicsSimulationProvider` and `EyeTree`. This change adds two
deprecated-API uses (`ReadAction.compute`, `FileChooserDescriptorFactory.createSingleFileDescriptor`).

