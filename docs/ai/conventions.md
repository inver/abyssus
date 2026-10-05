# Conventions

## Languages and generated code

- **Kotlin everywhere.** The only Java sources are the GLTF grammar inputs in
  `src/main/java/net/nevinsky/abyssus/language/psi/` (`Gltf.bnf`, `Gltf.flex`).
- **Generated code:** the lexer and parser are generated into `src/main/gen` by the `generateGltfParser` / `generateGltfLexer` Gradle tasks (run before compiling), and the directory is git-ignored. Never edit it;
  change the grammar instead.
- **Package-private libGDX code:** `com.badlogic.gdx.backends.lwjgl3.GdxGlBridge` lives in libGDX's package on
  purpose, to reach backend code that is package-private.
- **License header:** start new source files with the Apache 2.0 header (copy it from
  `src/main/kotlin/net/nevinsky/abyssus/AbyssusBundle.kt`). Most files have it; a few newer ones, mostly in `ecs/`,
  don't yet. libGDX-derived files in `gdx-model` keep their original headers.

## Module boundary

`gdx-model` depends only on libGDX, LWJGL Assimp and the slf4j API (`gdx-model/build.gradle.kts`). Nothing in it may import
`com.intellij.*` or `net.nevinsky.abyssus` plugin packages. The plugin depends on it with
`implementation(project(":gdx-model"))`.

## Logging

- **SLF4J is the one logging interface.** `gdx-model`, `core`, `raytracing`, `runtime` and `physics` (plain JVM) log through
  `org.slf4j.Logger` and never import `com.intellij.*`. `core`, `raytracing`, `runtime` and `physics` take a `Logger` through
  constructors (`SceneLoading`, `PhysicsWorld`, `PlayHost`, `AssetLoading`,
  `MetalRayBackendFactory`, `VulkanRayBackendFactory`, `RayRenderScheduler`); `gdx-model`'s static loaders read
  `ModelLogging.logger`. Debug messages are lazy: `log.atDebug().log { "..." }`.
- **The binding to the IDE logger is `IntellijLogger`** (`src/main/kotlin/net/nevinsky/abyssus/log/IntellijLogger.kt`), an
  SLF4J `Logger` over `com.intellij.openapi.diagnostic.Logger`, created by `IntellijLoggerFactory` and held by
  `AbyssusCore.loggers`. The composition root passes `getLogger("assets")`, `("scenes")`, `("ray")` and `("model")` (the last installed
  into `ModelLogging`), so everything lands in `idea.log` under `Abyssus.<category>` and obeys Debug Log Settings. It is
  passed explicitly because the platform already binds SLF4J to `java.util.logging` for the whole IDE (the IDE's `util-8` library),
  which cannot be changed from a plugin; the plugin zip excludes `org.slf4j` and uses the platform's API classes.
  SLF4J `error` is logged as an IDE *warn*, because `Logger.error` raises the IDE-error dialog.
- **Outside the IDE** (the play host, the Control Line game) `physics`'s runtime dependency `slf4j-simple` binds SLF4J to
  stderr; Abyssus Physics bundles `physics` without its dependencies, so it never reaches the IDE.
- **Tests** use `RecordingLogger`, `warningsTo(list)` or `failOnWarnings()` (`core` test fixtures) or `NOPLogger.NOP_LOGGER`; `raytracing` has its own small recorder.

## JSON

- **Always use `SceneJson`** (`src/main/kotlin/net/nevinsky/abyssus/filetype/SceneJson.kt`), never a fresh
  `ObjectMapper`. It keeps key order and `null` members, and keeps float text exactly (`RawNumberNode`). Reading and
  writing a file therefore never changes numbers you didn't touch.
- **Binding:** bind files to DTOs with `SceneJson.bind` / `SceneReader.parse`. Unknown fields are ignored, and
  property declaration order is the order the tree shows.
- **Non-row fields:** mark them `@get:JsonIgnore` (for example `SceneEntry.file`, `AssetInfo.unused`) so the tree
  doesn't list them.
- **Optional values:** read them from a `JsonNode` with the helpers in `../../src/main/kotlin/net/nevinsky/abyssus/sceneview/JsonNodes.kt`
  (`opt`, `text`, `float`, `obj`), which treat absent and JSON `null` alike.
- **Wiring:** pass collaborators in through constructors. A `service<...>()` lookup belongs only in an action, a
  provider, a tool window factory, the Abyssus pane or a `@Service` constructor.
- **Writing a file:** use `editSceneJson` (`src/main/kotlin/net/nevinsky/abyssus/filetype/SceneDocumentWriter.kt`), re-serialized with `SceneJson.inStyleOf`, so a pretty file stays pretty
  and a compact one stays compact.

## Writing files

Readers never write (`ConfigFileReader` implementations never write and never throw). Only these edit files, all through
`editSceneJson` as named undoable commands:
- the eye toggle,
- Rename Scene,
- the skybox chooser,
- scene view gizmo drags and Drop (the same Move Entity command),
- asset property edits in the properties panel (`AssetMetaEdits` in `properties/AssetMetaEdits.kt`): one `additional` key
  of an asset's `meta.json` per command, named Edit Asset Property. The rules (which keys, validation, defaults, stale
  values) are `AssetMetaEditor`'s in `core`, which works on any JSON tree and never touches `version`, `uuid`, `type`,
  `lastModified` or unknown keys.

**The one other write path: terrain files.** A scene or project file edit never takes it. Regenerating a terrain
replaces a binary height file and an Abyssus recipe, and creating one makes a folder with new files; none of that is a
JSON document edit, so `AssetFileCommand` (`assetfiles/AssetFileCommand.kt`) runs it as one named write command over
immutable `FileSnapshot`s staged beforehand. It checks the starting state inside the command (a changed file is a
conflict, an existing folder a collision, and nothing is written), writes with `java.io` and refreshes the VFS after the
command (a VFS write inside a command would also be recorded by the platform's own file undo, which would fight ours),
puts back what it wrote when a write fails (application-level rollback only: a process that dies mid-write can leave
files behind), and registers one `UndoableAction` only after success. Undo and Redo check the files again and refuse,
with the reason, rather than overwrite a later change; Undo of a creation also refuses while a scene (saved or unsaved)
or another asset uses the new asset (`AssetReferenceGuard`), and removes a folder only when it holds exactly what was
written. Cancellation is honoured only before the first write.

`SceneFormatListener` is the one other writer: it pretty-prints a `.scene` / `.abss` document when it opens in the
text editor. A new writer goes through `editSceneJson` (it publishes `AbyssusSceneEdited.TOPIC` when done) and gets a
command name in the message bundle.

## Errors and cancellation

- **Catching:** use `runCatchingKeepingCancellation`
  (`core/src/main/kotlin/net/nevinsky/abyssus/assets/Cancellation.kt`), not `runCatching`. It rethrows
  `CancellationException`, which includes `ProcessCanceledException`, which the platform requires.
  `./gradlew checkNoRunCatching` (part of `check`) fails on a `runCatching {` in the plugin or `core`.
- **Failure text:** show `Throwable.displayMessage()` (the message, or the class name when it has none).
- **Unreadable files:** an unreadable file or asset becomes a visible failure (an error row, a status message, a
  skipped asset logged once), never an exception out of a reader, renderer or tree node.

## UI text

User-visible strings go in `src/main/resources/messages/AbyssusBundle.properties` and are read with
`AbyssusBundle.message(key, args)`. Escape non-ASCII characters as `\uXXXX` in that file.

## Code style

- **KDoc:** explains *why* and the contract (thread, null meaning, what is never written), not what the next line
  does. Match the comment density of the file you edit.
- **Keep logic testable:** keep math and decisions in plain classes without Swing or GL (`ScenePicker`, `GizmoDrag`,
  `SceneMarkers`, `SkyboxPickerModel`, `PanelState`) so they get unit tests. The Swing or GL class only forwards to
  them.
- **Names:** `*Dto` for bound file models, `*Reader` for file readers, `*Codec` for ECS component mappers, `*Test`
  for test classes.

## Specs and docs

Behavior changes go through OpenSpec (`openspec/changes/`). After archive, `openspec/specs/<capability>/spec.md`
states the required behavior. When code changes make a page under `docs/ai/` or a package `README.md` wrong, fix it in
the same change.
