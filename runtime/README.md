# runtime

Plain JVM scene runtime with no IntelliJ or plugin dependencies. Constructor-wired, with no `object` or
`companion object`; `checkNoSingletons` runs as part of `check`. Depends on `core` and Ashley 1.7.4.

`SceneLoading(json, log)` is the composition root. It reads a project name and sorted scene paths through
`project(dir: Path)`, parses supplied `.abss` text through `projectName`, and parses scenes through `parse`.
`load(scene: Path)` and `load(text, projectDir: Path)` return the scene DTO, a new Ashley engine and the ECS document.
The text overload never reads or writes a scene file. Missing projects return null; failed filesystem loads log the
source and cause once and return null so callers can continue with other scenes. Parsing throws for error-row adapters. Source-aware callback overloads also log failures obtaining editor text
once before rethrowing, so VFS errors and malformed files use the same reporting boundary.

`project/ProjectFolder` owns case-sensitive layout constants and filesystem listing. `scene/` holds platform-free
DTOs and `SceneParser`. `ecs/` holds components, systems, codecs, loader and writer. The plugin pairs scenes with
`VirtualFile` in its own `SceneEntry`; `AbyssusCore` supplies the IDE log to this module.

Each load owns its engine, entities, resolver and warning collection. Unknown components are logged once per load
and carried unchanged; missing references become `NO_ENTITY`. Render references resolve folder names without GL.
The writer preserves unknown JSON and component order, omits default transform fields, and never writes derived state.
Codecs retain unchanged source fields; callers requiring exact number text supply number-preserving JSON nodes.
Only the render system needs a current GL context. Parsing, loading, other systems and tests need no GL.

Tests use plain JUnit and `testProject(name)` with Gradle's shared `abyssus.testData` property. Required behavior is in
`openspec/specs/scene-ecs-components` and `openspec/specs/scene-ecs-systems`; scene loading is specified in
`openspec/specs/scene-loading`.

Native version 1 documents have root `format: "abyssus"` and integral `formatVersion: 1`; asset metadata's `version`
is independent. `core` owns `AbyssusDocumentFormat`, shared with the editor. Enclosing project/scene parsing validates
headers; raw ECS helpers validate only reserved payload paths. `componentIdentifiers` and renderable `class` fields
are rejected. Short component names are stable identifiers, and asset renderables use `kind: "asset"`. Unknown native
kinds keep their raw payload and do not render. No importer or Java-class aliases are provided.
