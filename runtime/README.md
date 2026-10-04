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

## Game components

A game declares its own components as annotated Ashley components (package `schema/`). The class needs a no-argument
constructor; each `@Field`'s value in a new instance is its default, and only fields that differ from it are written.

```kotlin
@SceneComponent("PlaneComponent", label = "Plane")
class PlaneComponent : Component {
    @Field(label = "Line length", group = "Lines", min = 5.0, max = 30.0) var lineLength = 18f
    @Field var kind = Kind.TRAINER                       // an enum is a choice
    @Field var leadout = Vector3(0f, 0f, -0.3f)          // Vector3 and Color are written whole
    @Field @EntityRef var pilot = -1                     // another entity of the scene
    @Field @AssetRef("MODEL") var model = ""             // an asset folder of that type
}
```

Field types: `Float`, `Int`, `Boolean`, `String`, an enum, libGDX `Vector3` and `Color`; `@EntityRef` on an `Int`,
`@AssetRef(type)` on a `String`. Anything else fails registration.

**Registering is explicit.** Implement `ComponentRegistry` and pass it to the loader:
`SceneLoading(json, log, registry = MyComponents())`. Construction fails with `ComponentRegistrationException` (naming
the component and the reason) for a built-in or repeated short name, an unsupported field type or a missing
no-argument constructor. Built-in codecs keep their names. A component is stored under its short name only
(`ecs.entities.<id>.components.PlaneComponent`); no class name or identifier table is written. A value of the wrong
type, not among the choices or outside the limits loads as the default, with one warning naming entity, component
and field. A program that does not register a component keeps it raw and writes it back unchanged.

`SchemaJson` is the one encoding of these values: `ReflectiveCodec` (the game, with classes) and the editor (without)
both go through it, and `SchemaJsonRoundTripTest` holds them to the same text.

**Exporting the schema** lets Abyssus edit the components without loading game classes. `SchemaExportMain` writes
`<project>/abyssus/components.schema.json` (`SchemaFile`: stable bytes, version 1). Arguments: the
`ComponentRegistry` implementation's class name (it needs a no-argument constructor) and the project folder. A game
wires it as a Gradle task:

```kotlin
tasks.register<JavaExec>("exportComponentSchema") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("net.nevinsky.abyssus.runtime.schema.SchemaExportMain")
    args("com.example.game.MyComponents", rootProject.file("assets-project").absolutePath)
}
```

Required behavior: `openspec/specs/custom-scene-components` and `openspec/specs/component-schemas`.
