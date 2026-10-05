# Proposal

## Why

A static survey of the code (about 31k lines of Kotlin outside `gdx-model`, plus `docs/reviews/design-review-2026-10.md`
and the archived `design-review-refactor`) shows that the first review's big items are done, but new duplication and
a few SOLID problems have appeared since, mostly in code added by the ray tracing, physics, schema and terrain changes.
Fixing them now keeps later features small.

Concrete findings (each has a task with its own verification):

| # | Finding | Principle | Where |
|---|---|---|---|
| F1 | `meta.json` is bound in three places: `AssetMetaReader` (plugin), `AssetMetaLoader.parse` (`core`) and `UnsavedMetaLoader.parse` (plugin `AssetLoading`). The last two are line-for-line copies, including the `additionalClass` table. A new `MetaType` must be added in two `when` blocks. | DRY, OCP | `AssetLoading.kt`, `AssetMetaLoader.kt`, `AssetMetaReader.kt` |
| F2 | `checkNoRunCatching` is in `check`, yet by grep four direct `runCatching {` calls remain in its scope (`UUID.fromString` in `AssetLoading`, `AssetMetaLoader`, `AssetIndex`, and a lookup in `TerrainLoader`), so it should be failing; a fifth, in `VulkanRayBackend`, is outside the scope. The `core` `displayMessage()` helper exists but four `message ?: javaClass.simpleName` sites still copy it. | Project rules, DRY | `core/.../assets`, `RayIntegration`, `RayBackendService`, `physics-plugin` |
| F3 | A component field's type is switched on in five places (`SchemaJson.encodeValue`, `SchemaJson.decodeValue`, `schemaValue`, `ComponentSchemaReader` type inference, `ComponentEditor.fieldsOf`). A new `FieldType` touches all five. | OCP, Strategy | `runtime/schema/`, `ecs/scene/ComponentEditor.kt` |
| F4 | `ComponentEditor.kt` (488 lines, 7 types) holds the codec interface, the built-in component field tables, the schema codec and the editor. | SRP | `ecs/scene/ComponentEditor.kt` |
| F5 | `SceneViewPanel` (545 lines, 44 functions) builds the toolbar, play controls, ray bridge, key and mouse forwarding, canvas lifecycle and camera choices. `AssetPropertiesPanel` (593 lines) mixes layout, thumbnail and edit wiring. | SRP | `sceneview/`, `properties/` |
| F6 | `PlayProtocol.decode` switches on bare frame numbers (1..25), and encode repeats the same numbers. Adding a frame touches both. | OCP, KISS (magic numbers) | `physics/.../play/PlayProtocol.kt` |
| F7 | Three Gradle build files carry near-identical `checkNoSingletons` tasks (`core`, `runtime`, `physics`), plus `checkNoRunCatching` and `checkNoJolt`. | DRY | `*/build.gradle.kts` |
| F8 | `AbyssusCore` is a flat holder of about 25 collaborators with fully qualified names inline, and callers take the whole holder through `service<AbyssusCore>()` (about 9 sites) to reach one or two members. | ISP, DIP | `AbyssusCore.kt` and its callers |
| F9 | `PhysicsWorld` (545 lines, 45 functions) and `VulkanRayBackend` (795 lines) are large single classes. Whether the Metal and Vulkan backends repeat session/queue logic is **not yet verified**. | SRP | `physics/jolt/`, `raytracing/` |
| F10 | Docs drift: `docs/ai/conventions.md` says `AssetMetaEditor` is in `core`; it is in the plugin root package (`core/README.md` already says so). | Docs | `docs/ai/conventions.md` |

### IntelliJ Platform best practices audit

Checked against the JetBrains guidance (sources at the end of this file): threading model, services, disposers, listeners,
coroutines, dynamic plugins, Plugin Verifier and UX. Already in line with it (keep): `Disposable` parents are never
`Project` or `Application`; message bus connections use `connect(parent)`; `ReadAction.nonBlocking` for the footer; the
three extension points are `dynamic="true"`; the startup hook is a `ProjectActivity`; the file-type listener is declared
in `plugin.xml`; every action group and action has an id; user text goes through a resource bundle.

| # | Finding | Rule it breaks | Where |
|---|---|---|---|
| P1 | The CI step runs `./gradlew runPluginVerifier`, the task name of the old Gradle plugin, and `build.gradle.kts` has no `pluginVerification { ides { ... } }` block, though the plugin is on `org.jetbrains.intellij.platform` 2.19.0 where the task is `verifyPlugin`. By grep, the verifier is likely not running at all. **Unverified until task 11.1 runs it.** | Verify binary compatibility in CI | `build.gradle.kts`, `.github/workflows/build.yml` |
| P2 | Internal and impl platform API is used: `ProjectViewPane`, `ProjectAbstractTreeStructureBase`, `ProjectViewSelectInTarget`, `TextEditorProvider.impl`. The verifier will flag these; none is documented or isolated. | Avoid `impl`/`@Internal` API, or isolate and justify it | `AbyssusProjectViewPane.kt`, `AssetPropertiesPanel.kt` |
| P3 | Service lookups run inside constructors: `SceneReader()`, `SceneDocumentCache`, `ProjectReader` and `AssetReadCache` secondary constructors call `service<AbyssusCore>()` and other services. `AbyssusCore` builds about 25 collaborators when first touched, only the ray parts being lazy. | "Don't retrieve services in constructors; keep constructors light"; startup cost; init-cycle risk | `dto/`, `projectView/AssetReadCache.kt`, `AbyssusCore.kt` |
| P4 | Plugin-owned threads: `Executors.newSingleThread...` in `AbyssusCore` and `RayBackendService` and a raw `executeOnPooledThread` in `SkyboxChooserDialog` (which writes `choice.thumbs` from the pooled thread and reads it on the EDT without any publication guarantee). | Use platform executors or a service-scoped coroutine; keep cross-thread state safe | `AbyssusCore.kt`, `RayBackendService.kt`, `SkyboxChooserDialog.kt` |
| P5 | Every action (`RenameScene`, `NewTerrain`, tree actions, light items, filter) answers `ActionUpdateThread.EDT`. `AddLight` parses scene text in `update()`; the cached parse mitigates this, but the platform default and recommendation is a background thread for `update()`. | "Keep `update()` fast; prefer BGT" | `projectView/*Action.kt`, `UnusedFilter.kt` |
| P6 | Action text is hard-coded in `plugin.xml` (`text="Rename Scene..."`, `"Add Light"` and so on); the bundle has no `action.<id>.text` keys. The `<action>`s, tool-window and notification-group titles cannot be localized. | Localize through the resource bundle (`action.<id>.text`, `toolwindow.stripe.<id>`) | `plugin.xml`, `physics-plugin` `plugin.xml`, `AbyssusBundle.properties` |
| P7 | Only one coroutine entry point (`OpenAbyssusViewActivity`) exists; every other async path uses callbacks and pooled threads. This is not a defect (it works, and the build targets 252), so no rewrite is planned. For *new* async work the platform recommends a service-injected `CoroutineScope`. | Guidance for new code | whole plugin |
| P8 | Dynamic-plugin unloading is untested: Abyssus holds native libGDX/Assimp/LWJGL state and a global `Gdx.*`, and `physics-plugin` borrows its classloader. The three extension points are dynamic, but nothing shows an install, update or unload works without a restart. | Dynamic plugin requirements | both plugins |
| P9 | The CI workflow uses `actions/checkout@v3`, `cache@v3`, `upload-artifact@v3` and `codecov-action@v3`, which are deprecated. Also the `SceneFormatListener` rewrites a document on open; it is documented in `conventions.md` and kept, only noted here. | Build hygiene | `.github/workflows/*.yml` |

## What Changes

Refactors only; **no user-visible behavior and no file format change**.

- **One meta.json binding (F1).** `core` gets the single `MetaType` → `additional` class table and one parser. The
  unsaved-meta loader and `AssetMetaLoader` both use it. The plugin's `AssetMetaReader` keeps document-format
  validation and hands the validated tree to it.
- **Rule compliance (F2).** A small `parseUuidOrNull` replaces the `runCatching { UUID... }` calls; the remaining
  `message ?: ...` sites use `displayMessage()`. The `checkNoRunCatching` scope grows to `raytracing`, `runtime`,
  `physics` and `physics-plugin`.
- **Field type as a strategy (F3).** One `FieldTypeHandler` per `FieldType` (encode, decode, default, display
  parts); a registry replaces the five `when` blocks.
- **Split `ComponentEditor.kt` (F4)** into the codec contract, the built-in kinds, `SchemaCodec` and the editor.
- **Split the big UI classes (F5)** by responsibility: toolbar, input forwarding and canvas host out of
  `SceneViewPanel`; row building and thumbnail out of `AssetPropertiesPanel`.
- **Named protocol frames (F6):** a `FrameType` enum with encode and decode registered once.
- **Shared Gradle checks (F7):** one convention script (`gradle/checks.gradle.kts` or `buildSrc`) used by the modules.
- **Narrow dependencies (F8):** `AbyssusCore` is grouped (documents, assets, terrain, ray) and entry points ask for
  the group they use.
- **Investigate, then decide (F9):** extract only what a diff of the Metal and Vulkan session code proves repeated;
  split `PhysicsWorld` only along seams that already have separate tests.
- **Platform best practices (P1–P9):**
  - Make the plugin verifier actually run: add a `pluginVerification` block (recommended IDEs plus the `since-build`
    IDE) and call `verifyPlugin` from CI; triage what it reports, isolating or documenting each internal API use.
  - Remove service lookups from constructors and make `AbyssusCore` lazy by group (shared with F8).
  - Replace plugin-owned executors with platform executors where no native-thread affinity is needed; keep the
    ray worker (a single native-owning thread) and shut it down in `dispose`; publish the thumbnail results safely.
  - Move `update()` to a background thread for actions that never touch Swing state.
  - Move action, tool window and notification text into the bundle.
  - Add a service-scope coroutine rule to the conventions for new async code (no rewrite of working code).
  - Check dynamic install/unload by hand and fix what it shows.
  - Update workflow actions.
- **Docs (F10):** fix `conventions.md`, update `architecture.md`, `runtime/README.md`, `core/README.md` and the package READMEs.

### Out of scope

- Behavior, number text, key order and every file format (`format: "abyssus"`, `formatVersion: 1`). No scene, `.abss`
  or `meta.json` key is read or written differently; validation through `AbyssusDocumentFormat` stays where it is.
- `gdx-model` (an inherited libGDX/Mundus fork with its own origin notes), including its large `Mesh`,
  `DefaultShader` and `Model` classes.
- The Control Line game, except where it calls a moved API.
- New GoF patterns for their own sake. A pattern is used only where it removes a duplicated `when` or a copied block
  listed above (Strategy for F3 and F6, Facade for F8, Composite/Template are already in place).
- Rewriting working callback code to coroutines, and moving to a different logging API (`IntellijLogger` over SLF4J is a
  documented choice that the platform's own logging reaches).
- Hand-checking performance. No hot path (frame loop, picking) is restructured.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `asset-loading`: new requirement that saved and unsaved `meta.json` text yield the same asset and the same type
  binding (guards F1).
- `component-schemas`: new requirement that every field type decodes, encodes, defaults and displays the same through
  the schema reader, the scene codecs and the component editor (guards F3).

Unchanged and used as regression checks: `scene-component-editing`, `custom-scene-components`,
`object-properties-panel`, `scene-play-mode`, `physics-simulation`, `scene-model-rendering`,
`abyssus-project-assets` and `abyssus-document-format`.

No spec delta covers the platform items: they change no requirement (action titles, tool window and notification names
stay the same text); the plugin verifier becomes a build check, not a specified behavior.

## Impact

- **Plugin:** `AssetLoading`, `AbyssusCore`, `ecs/scene/ComponentEditor`, `sceneview/SceneViewPanel`,
  `properties/AssetPropertiesPanel`, `dto/AssetMetaReader`, and the entry points that call `service<AbyssusCore>()`.
- **`core`:** `AssetMetaLoader`, `AssetIndex`, `TerrainLoader`, a new meta-binding class.
- **`runtime`:** `schema/SchemaJson`, `schema/ComponentSchemaReader`.
- **`physics`:** `play/PlayProtocol`; `PhysicsWorld` only if task 9.2 finds a clean seam.
- **`raytracing`, `physics-plugin`:** `runCatching` and `displayMessage` sites; the backend diff in task 9.1.
- **Build and CI:** root, `core`, `runtime`, `physics`, `physics-plugin` Gradle files; `.github/workflows/*.yml`.
- **Resources:** both `plugin.xml` files, `AbyssusBundle.properties`, `AbyssusPhysicsBundle.properties`.
- **Docs:** `docs/ai/architecture.md`, `docs/ai/conventions.md`, `core/README.md`, `runtime/README.md`, package READMEs.
- **No new dependencies.** Existing tests must pass unchanged except for moves and renames named in a task.
- **Integration:** the open changes `add-scene-raytracing*`, `add-remote-asset-library` and `add-realistic-water`
  touch `AssetLoading`, `SceneViewPanel` and the ray files. Whichever lands second rebases; phases 4 and 5 are the
  likely conflict points and are ordered last-but-one for that reason.
- **Caveat on the survey:** it is a read-only static pass. No Gradle build or test was run while writing it, so each
  task starts by reproducing the claim (grep or a failing check) before changing code.

## Sources

- [Plugin UX and performance](https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html)
- [Threading model](https://plugins.jetbrains.com/docs/intellij/threading-model.html)
- [Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)
- [Disposer and Disposable](https://plugins.jetbrains.com/docs/intellij/disposers.html)
- [Coroutine scopes](https://plugins.jetbrains.com/docs/intellij/coroutine-scopes.html)
- [Listeners](https://plugins.jetbrains.com/docs/intellij/plugin-listeners.html)
- [Dynamic plugins](https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html)
- [Plugin Verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)
