# runtime

Plain JVM scene runtime with no IntelliJ or plugin dependencies. Constructor-wired, with no `object` or
`companion object`; `checkNoSingletons` runs as part of `check`. Depends on `core` and Ashley 1.7.4.

`RuntimeSceneLoader(json, fileLoader, log, registry)` is the composition root for one project folder. `load(sceneName)`
reads a scene file of the project's `scenes` folder (by its name, extension included) and `loadFromText(text)` a scene from
text, including unsaved edits; neither reads or writes anything else. Both return a `SceneContext` (the new Ashley engine,
the scene settings and the ECS document), or null: a scene that cannot be read is logged once with its cause, and a
caller can carry on with the others. Each load logs under the scene's name. The game components of `registry` are
checked when the loader is built, so a registration that fails (`ComponentRegistrationException`) loads no scene.
Reading a project's name, its project file or its scene list is `core`'s (`ProjectLoader`, `Project`).

The `core` scene package holds the platform-free scene DTOs and `SceneLoader`. `ecs/` holds components, systems, codecs, loader and writer. The plugin pairs scenes with
`VirtualFile` in its own `SceneEntry`; `AbyssusCore` supplies the IDE log to this module.

Each load owns its engine, entities, resolver and warning collection. Unknown components are logged once per load
and carried unchanged; missing references become `NO_ENTITY`. Render references resolve folder names without GL.
The writer preserves carried unknown component JSON, orders entities by numeric id and known components by registration,
omits default transform fields, and never writes derived state. It does not preserve arbitrary input key order like the
editor's document writer.
Codecs retain unchanged source fields; callers requiring exact number text supply number-preserving JSON nodes.
Only the render system needs a current GL context. Parsing, loading, other systems and tests need no GL.

Tests use plain JUnit and `testProject(name)` with Gradle's shared `abyssus.testData` property. Required behavior is in
`openspec/specs/scene-ecs-components` and `openspec/specs/scene-ecs-systems`; scene loading is specified in
`openspec/specs/scene-loading`.

Native version 1 documents have root `format: "abyssus"` and integral `formatVersion: 1`; asset metadata's `version`
is independent. `core.format.AbyssusDocumentFormat` guards JVM project, scene and metadata readers as well as editor reads and edits.
Raw `EcsLoader` validates reserved fields before changing the engine; `EcsWriter` checks its completed output.
Direct render serialization and deserialization reject renderable `class` fields. `componentIdentifiers` is also forbidden.
Asset renderables use `kind: "asset"`. Unknown native kinds keep their raw payload and do not render.
No importer is provided.

## Loading components: `EcsLoader`

`EcsLoader(mapper, resolver, log, game)` loads a scene's `ecs` block with Jackson, without per-component codecs. Each
entry of an entity's `components` is keyed by the class name of its component and bound with `ObjectMapper.readerFor(type).readValue`:

```json
"components": {
  "net.nevinsky.abyssus.runtime.ecs.component.NameComponent": { "name": "Model 0" },
  "PositionComponent": { "localPosition": { "x": 1.5 } }
}
```

A key is a fully qualified class name or the short class name the scene files have always used (the short name of a
registered game component works the same way). **Only the built-in components and the classes registered in `game` can
be named**: a scene is project data, so a class name in it never loads an arbitrary class. Any other key (an unknown
class, a class that is not a registered component) and a value Jackson cannot bind (an unknown enum name, say) are
carried raw with one warning, so the scene writes back unchanged. References to entity ids that are not in the file
become `NO_ENTITY`.

Components configure their own binding with Jackson annotations. `PositionComponent` merges `localPosition`,
`localRotation` and `localScale` into its own vectors (`@JsonMerge`, so an axis the file leaves out keeps its default)
and reads `lookAtId` as an integer or text; `LightComponent`, `CameraComponent` and `RenderComponent` have small
deserializers for their two shapes, their nested objects and their asset (the `AssetResolver` and the warnings are
injected into the reader). The loader binds libGDX's `Vector3`, `Quaternion` and `Color` by their public fields through
mixins on a copy of the mapper it is given, so the caller's mapper is untouched.

A registered game component is bound the same way, not through its schema: its properties bind by name, an object merges
into the value the property already holds (a vector the file names only in part keeps the default of the other axes),
and a value Jackson cannot bind (the wrong type, an unknown enum name) keeps the whole component raw with one warning
naming entity and component. The schema's limits and choices are not enforced on load; they remain what the editor
checks. Properties are bound by name, so a property that is not a `@Field` binds too when the file names it.
There are no component codecs: `EcsWriter` writes what `EcsLoader` reads.

## Writing components: `EcsWriter`

`EcsWriter(mapper, game)` is the counterpart of `EcsLoader`: `write(engine, document)` returns the `ecs` block, and
`writeComponent(component)` one component's JSON. Every component goes through `ObjectMapper.valueToTree`:

- A component is written without the properties that equal a new instance's (`@JsonInclude(NON_DEFAULT)` on every
  `Component`), and a decimal as the scene files spell it (`22`, not `22.0`).
- `PositionComponent`, `CameraComponent`, `LightComponent` and `RenderComponent` have small serializers for their
  shapes: a position writes only the axes that differ from the defaults, and a light, a render component and a look-at
  reference write the file's own node while unchanged, so unknown members and number spelling survive.
- Entities are written in ascending order of their `IdComponent`'s id, whatever order they were added in; an entity
  without one follows, numbered after the largest id. Components are written in a fixed order (built-in ones, then the
  game's) under their short names.
- What the loader could not bind (an unknown class, an unregistered game component, a value Jackson cannot bind) is kept
  in `SceneEcsDocument.carried` by entity id and key, not on the entity, and written back unchanged after that
  entity's components under the key the file gave it.
- A block is the entity map itself (`{"0": {...}}`). An older block that wrapped it in an `entities` member beside
  others (`metadata`) is read too, and written back in that shape with the extras after the entities. The Mundus-era
  `archetype` of an entity and the `archetypes` table are not read or carried: Ashley has no archetypes, so a scene
  written back no longer has them.
- Derived state (combined transform, light instance, point-to-point positions) is never written.
- A game component binds and writes Jackson-visible properties by name. `@Field` controls the exported editor schema;
  it does not restrict runtime Jackson binding to those fields.

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
`RuntimeSceneLoader(json, fileLoader, log, MyComponents())`. Construction fails with `ComponentRegistrationException` (naming
the component and the reason) for a built-in or repeated short name, an unsupported field type or a missing
no-argument constructor. Built-in codecs keep their names. A component is written under its short name
(`ecs.entities.<id>.components.PlaneComponent`) with no identifier table, and `EcsLoader` reads it by that name or by
its fully qualified class name. A program that does not register a component keeps it raw and writes it back unchanged.

`SchemaJson` encodes and decodes editor values using exported schemas. Runtime `EcsWriter` binds game classes
through Jackson; `SchemaJsonRoundTripTest` holds the two paths to the same text.

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

## Adding a schema field type

Field types retain their explicit switches: the DECIMAL/VECTOR handler prototype increased code size and was not
adopted. Add the enum entry and value contract in `schema/ComponentSchema.kt`, Java inference and default conversion
in `ComponentSchemaReader`, encode/decode handling in `SchemaJson`, and the editor field mapping in
`src/main/kotlin/net/nevinsky/abyssus/ecs/scene/ComponentEditor.kt`. Update schema-file validation and UI handling
where the new type needs them. Tests must cover inferred defaults, exact wire text, default omission, unusable input
and applicable limits for every type.
