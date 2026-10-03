# Design

## Context

See proposal.md - Why. After `extract-scene-runtime`:

- `runtime` holds the Ashley components, `ComponentCodec<C>` (`name`, `type`, `read(JsonNode)`, `write(C)`), the
  fixed `ComponentCodecs` list, `SceneEcsLoader` (an unknown name is kept in `RawComponentsComponent` with a warning),
  `SceneEcsWriter` (writes components in the file's order; `extras` such as `componentIdentifiers` are written back
  unchanged), the `number(Float)` helper for whole-number text, and `SceneLoading(json, log)`.
- The plugin's `ComponentEditor` is an `object` with a fixed `kinds` list. Each `ComponentKind<C>` has a codec, a
  list of `ComponentField<C>` read and written as text, and `create()`. `FieldKind` is `FLOAT, TEXT, CHOICE,
  ENTITY_REF, ASSET_NAME`. Vectors and colors are edited as dotted float fields (`localPosition.x`, `color.r`).
  Updates apply only the keys that differ; values are checked in `checkValue` (numbers, choices, assets, references
  with cycle checks).
- `PanelState` / `EntityDetailsView` build one section per component from `ComponentEditor.read`, with a text field or
  a combo box per field. Unknown components are shown as read-only JSON.
- `plugin.xml` declares no extension points yet.

## Goals / Non-Goals

**Goals:**

- One definition of how each field type looks in JSON, used by the game (with classes) and the editor (without them).
- The editor handles a schema-declared component through the same `ComponentEditor` paths as a built-in one, so undo,
  validation, minimal diffs and refresh come for free.
- A game declares a component once, as an annotated class.

**Non-Goals:**

- Nested objects or lists as field types; per-component custom editor UI.
- Maintaining `archetypes` / entity `archetype` ids (proposal: the spike decides a follow-up).

## Decisions

### 1. Annotations and the class-to-schema reader (runtime)

```kotlin
@Target(AnnotationTarget.CLASS) annotation class SceneComponent(val name: String, val label: String = "")
@Target(AnnotationTarget.FIELD) annotation class Field(val label: String = "", val group: String = "",
    val min: Double = Double.NEGATIVE_INFINITY, val max: Double = Double.POSITIVE_INFINITY,
    val minExclusive: Boolean = false)   // e.g. a mass must be greater than 0
@Target(AnnotationTarget.FIELD) annotation class EntityRef      // on an Int field
@Target(AnnotationTarget.FIELD) annotation class AssetRef(val type: String)   // on a String field
```

`FIELD`-only targets make Kotlin put them on the backing field of a `var`, so plain Java reflection reads them and no
`kotlin-reflect` dependency is needed. `ComponentSchemaReader` turns a class into a `ComponentSchema`: it instantiates
the class with its no-argument constructor (failing registration otherwise) and reads each `@Field`'s current value as
the default. Java types map to field types: `float`/`Float` -> decimal, `int`/`Int` -> whole (or entity reference
with `@EntityRef`), `boolean` -> true/false, `String` -> text (or asset reference with `@AssetRef`), an enum -> choice
(its constant names), libGDX `Vector3` -> vector, libGDX `Color` -> color. Anything else fails registration.

**Alternative rejected:** a schema DSL beside the class (explored and decided against).

### 2. `SchemaJson`: the single encoding (runtime)

`SchemaJson` reads and writes a component's values as a `Map<String, Any>` given only its `ComponentSchema`, with no
class: decode applies defaults for missing fields and reports wrong types, unknown choices and out-of-limit values
(falling back to the default); encode omits fields equal to their default and writes decimals through `number`, so
whole values come out as `25`. Vectors and colors are written whole (`{x, y, z}` / `{r, g, b, a}`) when any part
differs from the default, else left out.

The game-side `ReflectiveCodec(schema, type)` is a `ComponentCodec` built on it: read decodes to a map and sets the
fields reflectively on a new instance; write reads the fields into a map and encodes. The editor uses `SchemaJson`
directly. A shared round-trip test (decision 7) holds both to the same text.

### 3. Registration and the codec registry (runtime)

`ComponentCodecs` becomes a registry built from the built-in codecs plus `ComponentRegistry`, a list of game component
classes the caller passes to `SceneLoading`. Registration builds each class's schema and `ReflectiveCodec` and fails
with one message (spec: "Registration is checked") on a taken short name, an unsupported field type or a missing
no-argument constructor. `SceneEcsWriter` adds a `componentIdentifiers` entry, appended after the existing ones, for
each declared component class that has none.

### 4. Schema file and export (runtime)

```json
{ "version": 1,
  "components": [ { "name": "PlaneComponent", "class": "net.example.PlaneComponent", "label": "Plane",
      "fields": [ { "name": "lineLength", "label": "Line length", "type": "decimal", "default": 18,
                    "group": "Lines", "min": 5, "max": 30 },
                  { "name": "kind", "type": "choice", "choices": ["TRAINER", "STUNT", "SPEED"], "default": "TRAINER" },
                  { "name": "model", "type": "asset", "assetType": "MODEL", "default": "" } ] } ] }
```

`SchemaFile` writes this with a fixed pretty-printer (two-space indent, LF line ends, registration then declaration
order) and parses it, rejecting an unknown `version` or field type per component. An exclusive minimum is written
as `"minExclusive": true` next to `min`. `SchemaExportMain` (args: a
`ComponentRegistry` implementation class name and the project folder) is the entry point a game's Gradle `JavaExec`
task runs. Infinite limits are not written.

### 5. Editor side (plugin)

- **`ComponentSchemas`** (project service): merges the extension point's contributions with the project schema(s),
  project winning per short name (spec: "The project's schema wins"), and reports problems once through a
  notification. It reads `<project>/abyssus/components.schema.json` next to each `.abss` the Abyssus view knows,
  refreshing on VFS events for that path, and on extension add/remove (dynamic plugin events).
- **Extension point** `net.nevinsky.abyssus.componentSchemas`, bean class `ComponentSchemaBean` with a `resource`
  attribute: a path in the contributing plugin's jar, holding a schema file in the format above. Declared
  `dynamic="true"` so plugins can load and unload without a restart.
- **`ComponentEditor`** stops being an `object`: it is built with the current schemas and adds one `ComponentKind` per
  schema component. Its codec is a `SchemaCodec` (a `ComponentCodec<SchemaValues>` over `SchemaJson`, where
  `SchemaValues` is a map-backed component). Vectors and colors become dotted decimal fields (`leadout.x`,
  `paint.r`) as `Position` and `Light` already do. `FieldKind` gains `INT` and `BOOLEAN`; `checkValue` gains whole
  numbers, booleans and the schema's limits; entity references reuse `checkReference`; asset references reuse the
  asset-name check filtered by the declared asset type. `add` also appends the `componentIdentifiers` entry when
  missing.
- **Panel:** `EntityDetailsView` adds a checkbox editor for `BOOLEAN` and group sub-headings; labels come from the
  schema.

### 6. Threads

| Piece | Thread | GL |
|---|---|---|
| `ComponentSchemaReader`, `SchemaJson`, `SchemaFile`, registry, export | caller's; export runs in the game's own JVM | none |
| `ComponentSchemas` parsing | pooled thread, triggered by VFS / extension events; publishes a snapshot read on the EDT | none |
| `ComponentEditor` edits | inside `editSceneJson`, on the EDT, as today | none |

### 7. Testable without Swing, GL or the platform

All of the runtime side; `ComponentSchemas`' merge and conflict rules as a pure function of (project schema,
contributed schemas); `ComponentEditor` with a schema passed in (it already edits a `JsonNode`). A
`SchemaJsonRoundTripTest` runs every field type through both `ReflectiveCodec` and `SchemaJson` and requires identical
text. The panel's checkbox and groups, and the extension point's dynamic reload, are runIde checks.

### 8. Test fixture

A new fixture `src/test/testData/project/Custom`: a `.abss`, one scene with entity `0` (a plane:
`{"lineLength": 22, "kind": "STUNT"}`), entity `1` (a pilot, no plane), the model asset folder copied from
`Untitled`'s `tree`, and `abyssus/components.schema.json` declaring `PlaneComponent` with one field of each type:
`lineLength` (decimal, 18, 5..30, `Lines`), `fuelSeconds` (whole, 60), `hasTipWeight` (true/false, true), `name`
(text), `kind` (choice), `leadout` (vector, `0, 0, -0.3`), `paint` (color, white), `pilot` (entity reference), `model`
(asset reference, `MODEL`). A test-only `PlaneComponent` class in `runtime`'s test fixtures declares the same, and a
test checks that exporting it reproduces the fixture's schema file byte for byte.

## Risks / Trade-offs

- **Mundus may reject unknown classes.** The proposal's spike runs first (task 1.1). If Mundus fails to open such a
  scene, stop and bring the finding to the user before the rule change: the whole approach depends on it.
- **Two codecs drifting apart.** → Decision 2 makes the reflective codec a thin layer over `SchemaJson`, and the
  round-trip test fails on any difference.
- **Stale schema vs scene.** A field removed from the game stays in old scene files. → It is kept untouched, like an
  unknown key in a built-in component today; the editor ignores fields its schema does not declare.
- **`ComponentEditor` loses its `object`.** Callers (`ComponentActions`, `SceneComponentEdits`, `PanelState`) take an
  instance from `ComponentSchemas`. `design-review-refactor` already moves these callers to constructor wiring.

## Migration Plan

No migration: existing scenes have no declared components and edit exactly as before. Rollback is reverting the
change; scenes that gained declared components keep them as unknown (raw) components.
