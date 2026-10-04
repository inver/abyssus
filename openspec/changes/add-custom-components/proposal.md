# Proposal

## Why

A game needs data of its own in its scenes, such as a plane's mass and line length or where the pilot stands. A
designer should set that data in Abyssus, not in code. Today the scene runtime knows a fixed list of component kinds
(`ComponentCodecs`), and any other component is carried through a round trip as raw JSON that nobody can edit. The
native scene format already keys components by stable short names, so a game's own components can sit beside the
built-in ones. This change lets a game declare its own components in code, read and write them in scenes, and edit them in
Abyssus as if they were built in.

## What Changes

- **Declaring a component (runtime).** A game marks an Ashley component class `@SceneComponent("<ShortName>")` and
  its editable fields `@Field` (optional label, group, min / max). Supported field types: float, int, boolean,
  string, enum, vector3, color, entity reference and asset reference (with an asset type). One reflective codec in
  `runtime` reads and writes every declared component. A field's default is its value in a freshly constructed
  instance, and fields equal to their default are omitted on write, as the built-in codecs do.
- **Registration is explicit.** The game passes its component classes to the scene loader. There is no classpath
  scanning. Built-in codecs keep precedence for the built-in component names.
- **In the scene file.** A declared component is stored under `ecs.entities.<id>.components.<ShortName>`. The short
  name is its stable identifier: no Java class name and no identifier table are written. `archetypes` and the entity's
  `archetype` id are left as they are, as adding a built-in component already does.
- **One encoding.** How each field type looks in JSON is defined once, by the schema, in `runtime`. The game's
  reflective codec and the plugin's editor (which never loads game classes) both read and write through it.
- **Schema export.** `runtime` provides an entry point that writes the registered components' schema (names, fields,
  types, defaults, limits, groups) to `<project>/abyssus/components.schema.json`. A game wires it as a Gradle
  task. The plugin never loads game classes.
- **Editor extension point.** Abyssus gets its first extension point, `componentSchemas`. Another plugin can
  contribute a bundled schema through it, for example the physics components in `add-jolt-physics`. Abyssus also
  reads the open project's exported schema file and refreshes when it changes.
- **Editing in Abyssus.** The properties panel shows schema-declared components with editors per field type, offers
  them in "Add component", and changes them through `ComponentEditor` and `editSceneJson`: one undoable command per
  edit, same rules as built-in kinds. A component whose schema is unknown stays raw and read-only, as today.
- **Native extension data.** Game-declared components are opaque, native extension payloads inside the Abyssus
  version 1 contract from `decouple-from-mundus`; the document validator does not inspect them. No compatibility with
  another editor is promised and no separate spike or rule change is needed.

**Fields read/written.**
- **Read:** `ecs.entities.<id>.components`, plus the new `abyssus/components.schema.json`.
- **Written:** declared components under `components.<ShortName>`. Built-in keys, their order and their defaults are
  unchanged, and no `ecs.componentIdentifiers` is read or written (the native format rejects it).

**Out of scope.**

- Reading game source in the IDE (UAST / PSI) to discover components. The exported schema is the only source.
- A schema DSL, or map-backed components without a class.
- Custom editor UI per component beyond the per-type editors (for example a "fit collider to model" button).
- Prefabs or instancing entities across scenes.
- Physics components, play mode and the game itself (`add-jolt-physics`, `add-control-line-game`).

## Capabilities

### New Capabilities

- `custom-scene-components`: declaring game components in code and reading and writing them in a scene's `ecs`
  block under their short names and the default-omission rule.
- `component-schemas`: the schema file: what it declares, how a game exports it, and how Abyssus reads it.
- `abyssus-extension-points`: the extension points Abyssus offers other plugins, starting with `componentSchemas`.

### Modified Capabilities

- `scene-component-editing`: add, update and remove extend from the modeled kinds to schema-declared components,
  with the same validation, undo and "leave everything else as it was" rules.
- `object-properties-panel`: schema-declared components are shown with editors per field type and grouped by
  `@Field` group, and "Add component" lists them.

## Impact

- **Code:** `runtime` (annotations, reflective codec, codec registry, schema model and exporter); the plugin's
  `ComponentEditor`, properties panel, schema loading and `plugin.xml` (the new extension point).
- **Tests:** codec round trips for every field type, default omission, short-name-only writing, schema
  export, and panel editing of a schema component against a fixture project that holds an exported schema.
- **Docs:** `docs/ai/file-formats.md` (custom components and the schema file), `docs/ai/architecture.md` (extension points), `runtime/README.md`, `ecs/README.md`.
- **Depends on:** `extract-scene-runtime` and the native contract from `decouple-from-mundus`.
