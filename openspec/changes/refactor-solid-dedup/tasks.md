# Tasks

Every phase changes no behavior: existing tests pass unchanged apart from moves and renames named in a task. Each
phase ends green on `./gradlew check` before the next starts. Single plugin tests:
`./gradlew :test --tests '<class>'`; module tests: `./gradlew :core:test`, `:runtime:test`, `:physics:test`.
Only task 10.9 needs `runIde`; tasks marked (GL) run only with `-Dabyssus.glTests=true` on a machine with a display.

## 1. One meta.json binding (F1; spec `asset-loading`)

- [ ] 1.1 Pin current behavior first: add tests that load the Untitled fixture's model `meta.json` from saved and from
  unsaved text, a malformed `meta.json`, an unknown `type` and a bad `uuid`, and assert equal results.
  Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.AssetLoadingTest'` (new class if absent).
- [ ] 1.2 Add `AssetMetaBinder` to `core` with the `MetaType` → settings-class map and `bind(name, tree)`; use it in
  `AssetMetaLoader`; delete its private `additionalClass`. Verify with `./gradlew :core:test` and a new
  `AssetMetaBinderTest` (every `MetaType` binds; unknown binds to a map).
- [ ] 1.3 Make `UnsavedMetaLoader` in `AssetLoading.kt` use the binder; delete its `parse` and `additionalClass`.
  Verify with the task 1.1 tests and `AssetMetaReaderTest`.
- [ ] 1.4 Check that `AssetMetaReader` validation still runs before binding on every path
  (`rg -n 'AssetMetaBinder|AssetMetaReader' src/main core/src/main`). Verify with `AbyssusDocumentFormatTest` and
  `ProjectAssetsTest`.

## 2. Rule compliance (F2)

- [ ] 2.1 Reproduce: `rg -n '\brunCatching\s*\{' src/main/kotlin core/src/main/kotlin` lists four sites and
  `./gradlew checkNoRunCatching` fails. Record the output in the commit message.
- [ ] 2.2 Add `parseUuidOrNull` to `core` and use it in `AssetMetaBinder`, `AssetIndex` and `TerrainLoader`; replace the
  `AssetLoading` copy. Verify with `./gradlew :core:test --tests '*AssetIndex*'`, the terrain tests, and
  `./gradlew checkNoRunCatching` passing.
- [ ] 2.3 Replace `message ?: javaClass.simpleName` with `displayMessage()` in `RayIntegration`, `RayBackendService`,
  `PlayClient` and `PhysicsSimulationProvider` (`physics-plugin` already depends on `core` through Abyssus; check that
  `displayMessage` is reachable, else add it where both can see it). Verify with
  `rg -n 'message \?: .*simpleName' --glob '*.kt'` returning nothing, `./gradlew :test` and `:physics-plugin:test`.

## 3. Field types as strategies (F3; spec `component-schemas`)

- [ ] 3.1 Prototype `FieldTypeHandler` for `DECIMAL` and `VECTOR` in `runtime/schema/` and compare line counts with
  the switch arms they replace. Stop and report if it is not smaller.
- [ ] 3.2 Pin behavior first: `SchemaJsonTest` and `ComponentSchemaReaderTest` cases for every `FieldType`: decode,
  encode, default, limit failure and unusable value. Verify with `./gradlew :runtime:test`.
- [ ] 3.3 Finish all handlers, build the registry by constructor, and remove the `when` blocks in `SchemaJson`,
  `ComponentSchemaReader.schemaValue` and the type inference. Add a test that every `FieldType.entries` has a handler.
  Verify with `./gradlew :runtime:test` and `./gradlew :runtime:checkNoSingletons`.
- [ ] 3.4 Move the plugin's `ComponentEditor.fieldsOf` onto the handlers' `parts` and a plugin-side `FieldKind` mapping.
  Verify with `ComponentEditorTest`, `ComponentCodecsTest`, `EntityPropertiesPanelTest`.
- [ ] 3.5 Update `runtime/README.md` and `docs/ai/architecture.md` (how to add a field type).

## 4. Split by responsibility: component editor and properties (F4, F5 part)

- [ ] 4.1 Split `ComponentEditor.kt` into the four files from design D4; keep public names and packages. Verify with
  `./gradlew :test --tests 'net.nevinsky.abyssus.ecs.*'` and `./gradlew :test` unchanged.
- [ ] 4.2 Extract row builders and `Thumbnail` from `AssetPropertiesPanel` into their own files. Verify with
  `AssetPropertiesPanelTest` unchanged.

## 5. Protocol frames and Gradle checks (F6, F7)

- [ ] 5.1 Add a round-trip test over every play frame in `PlayProtocolTest`, with the wire ids written as literals so a
  renumbering fails. Verify with `./gradlew :physics:test`.
- [ ] 5.2 Add `FrameType` and use it in encode and decode. Verify with `PlayProtocolTest` and `PlayHostTest`.
- [ ] 5.3 Add `gradle/checks.gradle.kts` with `checkNoSingletons` and `checkNoRunCatching`; use it from `core`,
  `runtime`, `physics` and the root; widen `checkNoRunCatching` to `runtime`, `physics`, `raytracing` and
  `physics-plugin` (task 9 handles the `VulkanRayBackend` call). Verify that each task still fails when a violation is
  temporarily added, then passes, and that `./gradlew check` is green.

## 6. Narrow the core holder (F8)

- [ ] 6.1 Count `service<AbyssusCore>()` sites and the members each reads (`rg -n 'service<AbyssusCore>' src/main`);
  write the grouping into design D6.
- [ ] 6.2 Introduce the groups in `AbyssusCore`, switch entry points to ask for a group, and replace the inline fully
  qualified names with imports. Verify with `AbyssusViewTest`, `NewTerrainActionTest`, `SceneFileEditorTest` and
  `./gradlew :test`.

## 7. Scene view panel (F5)

- [ ] 7.1 Move camera choices and button enablement into Swing-free functions with tests. Verify with a new
  `SceneToolbarStateTest`.
- [ ] 7.2 Extract `SceneToolbar` and `SceneInputForwarder` from `SceneViewPanel`; collaborators by constructor, no
  shared mutable fields. Verify with `SceneViewPanelTest`, `SceneViewPanelRayTest`, `SceneInteractionTest`; (GL)
  `SceneRenderGlTest`.

## 8. Docs

- [ ] 8.1 Fix `docs/ai/conventions.md` (`AssetMetaEditor` is in the plugin, not `core`), then update
  `docs/ai/architecture.md`, `core/README.md`, `runtime/README.md`, `AGENTS.md` (if the check commands or layout
  change) and the `sceneview/` README. Verify with `scripts/check-docs.sh`.

## 9. Investigate, then decide (F9)

- [ ] 9.1 Diff the session and queue code of `MetalRayBackend` and `VulkanRayBackend`; record in design D8 which blocks
  are identical in behavior. Extract only those; if none, record that and close the task. Verify with
  `./gradlew :raytracing:test` (the conformance kit runs both backends where the hardware allows).
- [ ] 9.2 Replace the `runCatching { waitForFence() }` in `VulkanRayBackend` with the cancellation-keeping form or a
  documented `try`. Verify with `./gradlew :raytracing:test`.
- [ ] 9.3 List the seams in `PhysicsWorld` (bodies, shapes, stepping, queries) against `PhysicsWorldTest`; split only
  where a seam already has its own tests. Verify with `./gradlew :physics:test` and `:physics:checkNoSingletons`.

## 10. Platform best practices (P1–P9)

- [ ] 10.1 Baseline: add `pluginVerification { ides { recommended() } }` and run `./gradlew verifyPlugin` on a machine
  with network access to the IDE downloads; save the report summary in this design (D9). Verify that the task runs
  and lists the internal-API usages from P2.
- [ ] 10.2 Fix CI: call `verifyPlugin` instead of `runPluginVerifier` in `.github/workflows/build.yml`, and update the
  workflow actions to supported versions. Verify by running the workflow on the branch (a green run uploads the
  verifier report).
- [ ] 10.3 Isolate the internal API in `AbyssusProjectViewPane.kt` with a comment per use and an allowlist for known
  hits; replace any use that has a public equivalent. Verify with `verifyPlugin` (no new hits) and `AbyssusViewTest`.
- [ ] 10.4 Move action, tool window and notification text into `AbyssusBundle.properties` and
  `AbyssusPhysicsBundle.properties` (`action.<id>.text`, `toolwindow.stripe.<id>`, `notification.group.<id>`); remove
  the `text=` attributes. Verify with a test that loads `plugin.xml` and checks each action has a bundle key, and with
  `scripts/check-docs.sh`. Runtime titles in runIde: step 10.9.
- [ ] 10.5 Classify every `getActionUpdateThread()` (list in design D11); switch the data-only ones to BGT. Verify with
  `ComponentActionsTest`, `AddLightActionTest`, `NewTerrainActionTest` plus a test that `update()` of each switched
  action runs off the EDT without error.
- [ ] 10.6 Remove service lookups from constructors of `SceneReader`, `SceneDocumentCache`, `ProjectReader` and
  `AssetReadCache`; build `AbyssusCore` groups lazily (with task 6.2). Verify with
  `rg -n 'service<' src/main/kotlin` showing only entry points and `AbyssusViewTest`.
- [ ] 10.7 Make the skybox thumbnail load publish one immutable result to the EDT. Verify with `SkyboxChooserDialogTest`.
- [ ] 10.8 Add to `docs/ai/conventions.md`: service scopes for new coroutines, `update()` thread rule, no service lookup
  in constructors, internal-API policy. Verify with `scripts/check-docs.sh`.
- [ ] 10.9 Manual (runIde, cannot be done headless): (a) action and tool window titles unchanged; (b) install,
  update and uninstall the built plugin zip, and `Abyssus Physics`, without restart; (c) open a scene, close the
  project, and check `idea.log` for disposer or leaked-thread messages. List exact steps for the user if not run.

## 11. Finish

- [ ] 11.1 Run `./gradlew check` and `scripts/check-docs.sh`; fix only what this change caused and report anything
  failing for other reasons with its cause.
- [ ] 11.2 Archive the change, which merges the two spec deltas into `openspec/specs/`.
