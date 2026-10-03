# Design

## Context

See proposal.md for motivation. Current state:

- The Abyssus tree shows an entity's components straight from the scene file's JSON (`entityRows`,
  `DtoEntryNode`), and the Scene view re-reads the file whenever its document changes.
- Writes to scene files already go through `editSceneJson` (parse the document, mutate the Jackson tree, save
  in the file's own style as one undoable command). `SceneTransformWriter` is the model for a targeted JSON edit.
- `ComponentCodecs` knows the shape of the eight modeled components (read, write, defaults), and
  `SceneEcsLoader` already turns references to missing entities into `-1`.
- The Properties panel only understands asset selections (`PanelState`, `AbyssusSelectionListener`) and
  treats an entity as "not an asset".

## Goals / Non-Goals

**Goals:**
- One edit layer that both the Properties panel and the tree call, so validation and file output are identical.
- Edits stay minimal diffs of the scene text and survive undo, text editing and the Scene view.

**Non-Goals:**
- Creating or deleting entities, editing unmodeled components or `archetypes`.
- A live in-memory `SceneEngine` kept in sync with the editor; the file stays the source of truth.

## Decisions

**1. Edit the JSON tree, not the engine.** A new `ComponentEditor` works on the parsed scene `JsonNode`, like
`SceneTransformWriter`, and is run inside `editSceneJson`. Alternative: load into a `SceneEngine`, mutate
components and write the whole block with `SceneEcsWriter`. Rejected: it rewrites the entire `ecs` block, can
change formatting and defaults of untouched entities, and makes every edit depend on asset resolution succeeding.
Tradeoff: validation must reason about JSON shapes, so it reuses the codecs for them (decision 2).

**2. Use the codecs as the schema.** To build a new component, the editor writes `codec.write(codec.read(emptyNode))`,
which yields the loader's defaults in Mundus' shape. To read fields for display and to validate an update, it
reads the node with the codec, applies the change to the typed component, and writes it back with the codec, then
replaces only that component node. This keeps default-omission and number formatting (`number()` in
`ComponentCodecs`) in one place. Alternative: hand-written field setters per kind as in `SceneTransformWriter`;
rejected as duplicate shape knowledge that drifts from the codecs. Where the codec drops data (a Render component
keeps its raw `renderable` object, a nested `light` flag), the existing `raw` and `nested` fields carry it.

**3. A field descriptor table drives the UI.** Each modeled kind exposes its fields as descriptors
(`name`, kind: `FLOAT`, `TEXT`, `ENUM`, `COLOR`, `ENTITY_REF`, `ASSET_REF`, getter, setter). The panel renders
editors from this table and the editor validates against it, so adding a component kind later is one table entry.

**4. Validate against the whole scene before writing.** `ComponentEditor` returns a result, either `Changed`,
`Unchanged` or `Rejected(reason)`, and mutates the tree only on `Changed`. Entity references are checked against the
ids in `ecs.entities`; parent changes walk the parent chain to refuse cycles; removal scans the other entities for
references to the entity's position or parent component. Rejections show as a message and never reach the document.

**5. Panel and tree share one path.** Both call `editSceneJson(project, file, commandName) { ComponentEditor... }`.
The command names are new bundle keys (`commandAddComponent`, `commandEditComponent`, `commandRemoveComponent`) so
Undo history reads clearly. After the write, the Scene view reloads through its existing document listener and the
tree refreshes through the pane update `editSceneJson` already triggers.

**6. Panel selection model.** `AbyssusSelection` already publishes the selected node. `PanelState` gains
`EntityDetails(file, entityId)` and `ComponentDetails(file, entityId, kind)` for an entity row and a component row
(recognised with the existing `isEntityEntry` / `isComponentEntry`), alongside the asset states. The panel
re-reads the scene document when its text changes, as it already does for `meta.json`, and shows a message if the
entity or component is gone. Alternative: a separate tool window for entities; rejected to keep one
"properties" place.

**7. Add Component UI.** Context menu actions are registered like `RenameSceneAction`; "Add Component..." is a
popup list of kinds the entity lacks. For Render it first opens a chooser of the project's MODEL and TERRAIN
assets (reusing the skybox chooser's asset listing approach) and shader key defaults to what the loader treats as
"none". The panel's Add choice calls the same function.

## Risks / Trade-offs

- [Codec round trip changes a component's text beyond the edited field] → test each kind: unchanged fields must
  be byte-identical after an edit to a different field; fall back to editing the single JSON field when the codec
  would drop data.
- [Float rendering noise (`0.1` becoming `0.10000000149`)] → parse user input as decimal text and reuse `number()`
  so only the shortest float text is written.
- [Concurrent text edits while the panel holds an edit] → `editSceneJson` re-parses the current document text on
  each write, and a failed lookup of the entity or component is reported as "no longer exists".
- [Reference checks cannot see unmodeled components that refer to entities (`DependenciesComponent`)] → document as
  a limit; the check covers modeled references only.
- [`abyssus-project-view` text says only three actions write asset files, already outdated by gizmo moves] → the
  new requirement is additive; tidying the old sentence is left to a later change.

## Open Questions

- Whether colors use a color picker or four number fields in the first cut; either satisfies the specs.
