# Tasks

Every phase changes no behavior: existing tests pass unchanged apart from moves and renames named in a task. Each
phase ends green on `./gradlew check` before the next starts, except the documented baseline continuation below. Single plugin tests:
`./gradlew :test --tests '<class>'`; module tests: `./gradlew :core:test`, `:runtime:test`, `:physics:test`.
Only task 10.9 needs `runIde`; tasks marked (GL) run only with `-Dabyssus.glTests=true` on a machine with a display.

## Baseline continuation (2026-10-06)

The user instructed continuation after the prerequisite's full check reported baseline failures. Proceed with scoped
refactor tasks and their focused verification; retain failing integration gates as unchecked and report baseline failures.
Do not fix unrelated Control Line work or mark a full-check task complete without a green check. Phase 2's UUID fixes
may run before phase 1 to remove the reproduced cancellation-rule violations.

The user subsequently authorized test-only repairs under `repair-native-test-regressions`, an explicit exception
to the unchanged-test rule above. Production defects and externally edited shared fixtures remain separate from
these repairs; integration checkboxes require their stated verification to pass.

## 1. One meta.json binding (F1; spec `asset-loading`)

- [x] 1.1 Pin current behavior first: add tests that load the Untitled fixture's model `meta.json` from saved and from
  unsaved text, a malformed `meta.json`, an unknown `type` and a bad `uuid`, and assert equal results.
  Verify with `./gradlew :test --tests 'net.nevinsky.abyssus.AssetLoadingTest'` (new class if absent).
- [x] 1.2 Add `AssetMetaBinder` to `core` with the `MetaType` → settings-class map and `bind(name, tree)`; use it in
  `AssetMetaLoader`; delete its private `additionalClass`. Verify with `./gradlew :core:test` and a new
  `AssetMetaBinderTest` (every `MetaType` binds; unknown binds to a map).
- [x] 1.3 Make `UnsavedMetaLoader` in `AssetLoading.kt` use the binder; delete its `parse` and `additionalClass`.
  Verify with the task 1.1 tests and `AssetMetaReaderTest`.
- [x] 1.4 Check that `AssetMetaReader` validation still runs before binding on every path
  (`rg -n 'AssetMetaBinder|AssetMetaReader' src/main core/src/main`). Verify with `AbyssusDocumentFormatTest` and
  `ProjectAssetsTest`.

## 2. Rule compliance (F2)

- [x] 2.1 Reproduce: `rg -n '\brunCatching\s*\{' src/main/kotlin core/src/main/kotlin` lists four sites and
  `./gradlew checkNoRunCatching` fails. Record the output in the commit message.
- [x] 2.2 Add `parseUuidOrNull` to `core` and use it in `AssetMetaBinder`, `AssetIndex` and `TerrainLoader`; replace the
  `AssetLoading` copy. Verify with `./gradlew :core:test --tests '*AssetIndex*'`, the terrain tests, and
  `./gradlew checkNoRunCatching` passing.
- [x] 2.3 Replace `message ?: javaClass.simpleName` with `displayMessage()` in `RayIntegration`, `RayBackendService`,
  `PlayClient` and `PhysicsSimulationProvider` (`physics-plugin` already depends on `core` through Abyssus; check that
  `displayMessage` is reachable, else add it where both can see it). Verify with
  `rg -n 'message \?: .*simpleName' --glob '*.kt' --glob '!Throwables.kt'` returning nothing (the canonical helper
  itself contains the one fallback), `./gradlew :test` and `:physics-plugin:test`.

## 3. Field types as strategies (F3; spec `component-schemas`)

- [x] 3.1 Prototype `FieldTypeHandler` for `DECIMAL` and `VECTOR` in `runtime/schema/` and compare line counts with
  the switch arms they replace. Stop and report if it is not smaller.
- [x] 3.2 Pin behavior first: `SchemaJsonTest` and `ComponentSchemaReaderTest` cases for every `FieldType`: decode,
  encode, default, limit failure and unusable value. Verify with `./gradlew :runtime:test`.
- [x] 3.5 Update `runtime/README.md` and `docs/ai/architecture.md` to document the retained switches and the places
  that must change when adding a field type. Verify with `scripts/check-docs.sh`.

Tasks 3.3 and 3.4 were withdrawn after the failed prototype and the user's instruction to keep the switches and continue.
Tasks 3.1/3.2 retain the prototype decision and behavior checks. No handler registry is introduced.

## 4. Split by responsibility: component editor and properties (F4, F5 part)

> Moved to `restructure-editor-modules` stage 3 (tasks 3.1 and 3.4), where the files also change module. Do these tasks
> here only if that change is not going to be applied. If you do them here, mark the matching tasks there as done.

- [ ] 4.1 Split `ComponentEditor.kt` into the four files from design D4; keep public names and packages. Verify with
  `./gradlew :test --tests 'net.nevinsky.abyssus.ecs.*'` and `./gradlew :test` unchanged.
- [ ] 4.2 Extract row builders and `Thumbnail` from `AssetPropertiesPanel` into their own files. Verify with
  `AssetPropertiesPanelTest` unchanged.

## 5. Protocol frames and Gradle checks (F6, F7)

- [x] 5.1 Add a round-trip test over every play frame in `PlayProtocolTest`, with the wire ids written as literals so a
  renumbering fails. Verify with `./gradlew :physics:test`.
- [x] 5.2 Add `FrameType` and use it in encode and decode. Verify with `PlayProtocolTest` and `PlayHostTest`.
- [x] 5.3 Add `gradle/checks.gradle.kts` with `checkNoSingletons` and `checkNoRunCatching`; use it from `core`,
  `runtime`, `physics` and the root; widen `checkNoRunCatching` to `runtime`, `physics`, `raytracing` and
  `physics-plugin` (task 9 handles the `VulkanRayBackend` call). Verify that each task still fails when a violation is
  temporarily added, then passes, and that `./gradlew check` is green.

## 6. Narrow the core holder (F8)

- [x] 6.1 Count `service<AbyssusCore>()` sites and the members each reads (`rg -n 'service<AbyssusCore>' src/main`);
  write the grouping into design D6.
- [x] 6.2 Introduce the groups in `AbyssusCore`, switch entry points to ask for a group, and replace the inline fully
  qualified names with imports. Verify with `AbyssusViewTest`, `NewTerrainActionTest`, `SceneFileEditorTest` and
  `./gradlew :test`.

## 7. Scene view panel (F5)

> Moved to `restructure-editor-modules` task 3.1 for the same reason as phase 4. Keep 7.1 (Swing-free toolbar state with
> tests) only if it is useful before the module move.

- [ ] 7.1 Move camera choices and button enablement into Swing-free functions with tests. Verify with a new
  `SceneToolbarStateTest`.
- [ ] 7.2 Extract `SceneToolbar` and `SceneInputForwarder` from `SceneViewPanel`; collaborators by constructor, no
  shared mutable fields. Verify with `SceneViewPanelTest`, `SceneViewPanelRayTest`, `SceneInteractionTest`; (GL)
  `SceneRenderGlTest`.

## 8. Docs

- [x] 8.1 Update
  `docs/ai/architecture.md`, `core/README.md`, `runtime/README.md`, `AGENTS.md` (if the check commands or layout
  change) and the `sceneview/` README. Verify with `scripts/check-docs.sh`.

## 9. Investigate, then decide (F9)

- [x] 9.1 Diff the session and queue code of `MetalRayBackend` and `VulkanRayBackend`; record in design D8 which blocks
  are identical in behavior. Extract only those; if none, record that and close the task. Verify with
  `./gradlew :raytracing:test` (the conformance kit runs both backends where the hardware allows).
- [x] 9.2 Replace the `runCatching { waitForFence() }` in `VulkanRayBackend` with the cancellation-keeping form or a
  documented `try`. Verify with `./gradlew :raytracing:test`.
- [x] 9.3 List the seams in `PhysicsWorld` (bodies, shapes, stepping, queries) against `PhysicsWorldTest`; split only
  where a seam already has its own tests. Verify with `./gradlew :physics:test` and `:physics:checkNoSingletons`.

## 10. Platform best practices (P1–P9)

- [x] 10.1 Baseline: add `pluginVerification { ides { recommended() } }` and run `./gradlew verifyPlugin` on a machine
  with network access to the IDE downloads; save the report summary in this design (D9). Verify that the task runs
  and lists the internal-API usages from P2.
- [ ] 10.2 Fix CI: call `verifyPlugin` instead of `runPluginVerifier` in `.github/workflows/build.yml`, and update the
  workflow actions to supported versions. Verify by running the workflow on the branch (a green run uploads the
  verifier report).
- [x] 10.3 Isolate the internal API in `AbyssusProjectViewPane.kt` with a comment per use and an allowlist for known
  hits; replace any use that has a public equivalent. Verify with `verifyPlugin` (no new hits) and `AbyssusViewTest`.
- [x] 10.4 Move action, tool window and notification text into `AbyssusBundle.properties` and
  `AbyssusPhysicsBundle.properties` (`action.<id>.text`, `toolwindow.stripe.<id>`, `notification.group.<id>`); remove
  the `text=` attributes. Verify with a test that loads `plugin.xml` and checks each action has a bundle key, and with
  `scripts/check-docs.sh`. Runtime titles in runIde: step 10.9.
- [x] 10.5 Classify every `getActionUpdateThread()` (list in design D11); switch the data-only ones to BGT. Verify with
  `ComponentActionsTest`, `AddLightActionTest`, `NewTerrainActionTest` plus a test that `update()` of each switched
  action runs off the EDT without error.
- [x] 10.6 Remove service lookups from constructors of `SceneReader`, `SceneDocumentCache`, `ProjectReader` and
  `AssetReadCache`; build `AbyssusCore` groups lazily (with task 6.2). Verify with
  `rg -n 'service<' src/main/kotlin` showing only entry points and `AbyssusViewTest`.
- [x] 10.7 Make the skybox thumbnail load publish one immutable result to the EDT. Verify with `SkyboxChooserDialogTest`.
- [x] 10.8 Add to `docs/ai/conventions.md`: service scopes for new coroutines, `update()` thread rule, no service lookup
  in constructors, internal-API policy. Verify with `scripts/check-docs.sh`.
- [ ] 10.9 Manual (runIde, cannot be done headless): (a) action and tool window titles unchanged; (b) install,
  update and uninstall the built plugin zip, and `Abyssus Physics`, without restart; (c) open a scene, close the
  project, and check `idea.log` for disposer or leaked-thread messages. List exact steps for the user if not run.

## 11. Finish

- [x] 11.1 Run `./gradlew check` and `scripts/check-docs.sh`; fix only what this change caused and report anything
  failing for other reasons with its cause.
- [x] 11.2 Archive the change, which merges the two spec deltas into `openspec/specs/`.
