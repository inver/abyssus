# Conventions

## Languages and generated code

- **Kotlin everywhere.** The only Java sources are the GLTF grammar inputs in
  `src/main/java/net/nevinsky/abyssus/language/psi/` (`Gltf.bnf`, `Gltf.flex`).
- **Generated code:** the lexer and parser are generated into `src/main/gen`, which is git-ignored. Never edit it;
  change the grammar instead.
- **Package-private libGDX code:** `com.badlogic.gdx.backends.lwjgl3.GdxGlBridge` lives in libGDX's package on
  purpose, to reach backend code that is package-private.
- **License header:** start new source files with the Apache 2.0 header (copy it from
  `src/main/kotlin/net/nevinsky/abyssus/AbyssusBundle.kt`). Most files have it; a few newer ones, mostly in `ecs/`,
  don't yet. libGDX-derived files in `gdx-model` keep their original headers.

## Module boundary

`gdx-model` depends only on libGDX, LWJGL Assimp and slf4j (`gdx-model/build.gradle.kts`). Nothing in it may import
`com.intellij.*` or `net.nevinsky.abyssus` plugin packages. The plugin depends on it with
`implementation(project(":gdx-model"))`.

## JSON

- **Always use `SceneJson`** (`src/main/kotlin/net/nevinsky/abyssus/filetype/SceneJson.kt`), never a fresh
  `ObjectMapper`. It keeps key order and `null` members, and keeps float text exactly (`RawNumberNode`). Reading and
  writing a file therefore never changes numbers you didn't touch.
- **Binding:** bind files to DTOs with `SceneJson.bind` / `SceneReader.parse`. Unknown fields are ignored, and
  property declaration order is the order the tree shows.
- **Non-row fields:** mark them `@get:JsonIgnore` (for example `SceneDto.file`, `AssetInfo.unused`) so the tree
  doesn't list them.
- **Optional values:** read them from a `JsonNode` with the helpers in `core/src/main/kotlin/net/nevinsky/abyssus/assets/json/JsonNodes.kt`
  (`opt`, `text`, `float`, `obj`), which treat absent and JSON `null` alike.
- **Writing a file:** use `editSceneJson`, re-serialized with `SceneJson.inStyleOf`, so a pretty file stays pretty
  and a compact one stays compact.

## Writing files

Readers never write (`ConfigFileReader` implementations never write and never throw). Only these edit files, all through
`editSceneJson` as named undoable commands:
- the eye toggle,
- Rename Scene,
- the skybox chooser,
- scene view gizmo drags and Drop (the same Move Entity command).

`SceneFormatListener` is the one other writer: it pretty-prints a `.scene` / `.abss` document when it opens in the
text editor. A new writer goes through `editSceneJson` and gets a command name in the message bundle.

## Errors and cancellation

- **Catching:** use `runCatchingKeepingCancellation`
  (`src/main/kotlin/net/nevinsky/abyssus/dto/Cancellation.kt`), not `runCatching`. It rethrows
  `ProcessCanceledException`, which the platform requires.
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
