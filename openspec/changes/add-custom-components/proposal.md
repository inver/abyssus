# Proposal

## Why

A game needs data of its own in its scenes, such as a plane's mass and line length or where the pilot stands. A
designer should set that data in Abyssus, not in code. Today the scene runtime knows a fixed list of component kinds
(`ComponentCodecs`), and any other component is carried through a round trip as raw JSON that nobody can edit. The
scene format already has the slot: `ecs.componentIdentifiers` maps a component class to the short name its entities
use. This change lets a game declare its own components in code, read and write them in scenes, and edit them in
Abyssus as if they were built in.

## What Changes

- **Declaring a component (runtime).** A game marks an Ashley component class `@SceneComponent("<ShortName>")` and
  its editable fields `@Field` (optional label, group, min / max). Supported field types: float, int, boolean,
  string, enum, vector3, color, entity reference and asset reference (with an asset type). One reflective codec in
  `runtime` reads and writes every declared component. A field's default is its value in a freshly constructed
  instance, and fields equal to their default are omitted on write, as the built-in codecs do.
- **Registration is explicit.** The game passes its component classes to the scene loader. There is no classpath
  scanning. Built-in codecs keep precedence for Mundus's own component names.
- **In the scene file.** A declared component is stored under `ecs.entities.<id>.components.<ShortName>`. Adding one
  to an entity also adds its class to `componentIdentifiers` and updates the entity's archetype entry in
  `archetypes`, the way Mundus records its own components.
- **Schema export.** `runtime` provides an entry point that writes the registered components' schema (names, fields,
  types, defaults, limits, groups) to `<mundus project>/abyssus/components.schema.json`. A game wires it as a Gradle
  task. The plugin never loads game classes.
- **Editor extension point.** Abyssus gets its first extension point, `componentSchemas`. Another plugin can
  contribute a bundled schema through it, for example the physics components in `add-jolt-physics`. Abyssus also
  reads the open project's exported schema file and refreshes when it changes.
- **Editing in Abyssus.** The properties panel shows schema-declared components with editors per field type, offers
  them in "Add component", and changes them through `ComponentEditor` and `editSceneJson`: one undoable command per
  edit, same rules as built-in kinds. A component whose schema is unknown stays raw and read-only, as today.
- **Mundus compatibility rule.** `openspec/config.yaml`'s rule "Never write values Mundus does not write" is amended:
  Mundus's own keys keep every rule, and game-declared components may be added as described here. The new wording is
  written in a task and confirmed by the project owner before any code lands.
- **Spike first.** Check what the Mundus editor does when it opens a scene with a component class it cannot load and a
  project with an `abyssus/` folder. The finding is recorded in `docs/ai/file-formats.md`.

**Mundus files.**
- **Read:** `ecs.entities.<id>.components`, `ecs.componentIdentifiers` and `ecs.archetypes`, plus the new
  `abyssus/components.schema.json`.
- **Written:** declared components under `components.<ShortName>`, new entries in `componentIdentifiers`, and the
  affected `archetypes` entries. **The file format changes on purpose:** scenes can hold component classes Mundus does
  not have. Mundus's own keys, their order and their defaults are unchanged.

**Out of scope.**

- Reading game source in the IDE (UAST / PSI) to discover components. The exported schema is the only source.
- A schema DSL, or map-backed components without a class.
- Custom editor UI per component beyond the per-type editors (for example a "fit collider to model" button).
- Prefabs or instancing entities across scenes.
- Physics components, play mode and the game itself (`add-jolt-physics`, `add-control-line-game`).

## Capabilities

### New Capabilities

- `custom-scene-components`: declaring game components in code and reading and writing them in a scene's `ecs`
  block, including `componentIdentifiers` / `archetypes` bookkeeping and the default-omission rule.
- `component-schema-export`: writing the registered components' schema to the project, as data the editor reads.
- `abyssus-extension-points`: the extension points Abyssus offers other plugins, starting with `componentSchemas`.

### Modified Capabilities

- `scene-component-editing`: add, update and remove extend from the modeled kinds to schema-declared components,
  with the same validation, undo and "leave everything else as it was" rules. Adding one also records its class in
  `componentIdentifiers` and `archetypes`.
- `object-properties-panel`: schema-declared components are shown with editors per field type and grouped by
  `@Field` group, and "Add component" lists them.

## Impact

- **Code:** `runtime` (annotations, reflective codec, codec registry, schema model and exporter); the plugin's
  `ComponentEditor`, properties panel, schema loading and `plugin.xml` (the new extension point).
- **Process:** the `openspec/config.yaml` rule change described above, made in a task after the owner confirms the
  wording.
- **Tests:** codec round trips for every field type, default omission, `componentIdentifiers` / `archetypes` updates,
  schema export, and panel editing of a schema component against a fixture project that holds an exported schema.
- **Docs:** `AGENTS.md` (the compatibility rule), `docs/ai/file-formats.md` (custom components, the schema file and
  the spike's finding), `docs/ai/architecture.md` (extension points), `runtime/README.md`, `ecs/README.md`.
- **Depends on:** `extract-scene-runtime`.
