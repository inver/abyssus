# Design

## Context

Today one JSON document per scene carries settings and an id-keyed `ecs` map. Everything that edits a scene goes
through `editSceneJson` (one document, one undoable command) and `DocumentTextEditor`; loading goes through
`SceneParser`/`SceneLoader`, with an unsaved-buffer path. See proposal.md for motivation. `typed-scene-ecs` (16/17
tasks) defines the id-keyed map and `EntityDto`; this change builds on it and must be applied after it is archived.

## Goals / Non-Goals

**Goals:**
- Entities in `<Scene>.ecs.json` as an array; settings stay in the `.scene`.
- Every user action stays one undoable command, even across both files.
- Same admission, number-text and key-order guarantees as today.

**Non-Goals:**
- An importer or implicit migration of old scenes.
- Changing `.abss`, asset `meta.json`, the `abyssus` (water) block or `rayTracing` shape.
- Splitting a scene into several ECS files, or sharing one ECS file between scenes.

## Decisions

1. **Pairing by convention.** `AbyssusProjectLayout` derives the ECS path from the scene name (`scenes/<Scene>.ecs.json`).
   Alternative: a pointer member in the `.scene`. Rejected: one more field to validate and to keep in sync on rename,
   and a way to point two scenes at one file.
2. **Format authority is the `.scene`.** The array has no markers (a bare array cannot carry them). Admission checks the
   scene first, then the array. Alternative: wrap as `{format, entities:[...]}`. Rejected: the request is a bare array.
3. **Explicit ids.** The array element carries `id`; the id-keyed map of `typed-scene-ecs` becomes a list in the DTO
   binding (`SceneDto.ecs` keeps its typed form; only the source changes). Array order is load order and write order.
4. **A scene document set.** Introduce one value in `lib-core-editor` holding the `.scene` tree and the ECS tree
   (`SceneJson` for each; the ECS root is an array). `DocumentTextEditor` edits either tree and reports which changed.
   `editSceneJson` takes the pair, writes only changed documents inside one `WriteCommandAction` with a shared group id
   so Undo is one step. Alternative: two commands with a merged undo group. Rejected: easier to get half-undone.
5. **Loading.** `SceneParser.parse(sceneText, ecsText?)` validates the scene, then the array, then binds once. The
   plugin supplies unsaved buffers for both files; `SceneLoader.load(name)` reads both files. The array is bound by the
   existing `EntityDtoModule` through `JsonProcessor`; no hand parsing (hard rule).
6. **Missing file = no entities; first entity creates the file.** Matches today's "no `ecs`" behavior and keeps tiny
   scenes valid. Alternative: require the file. Rejected: breaks every scene without entities and headless callers.
7. **Rename and project view.** Rename Scene renames both files in one command. The ECS file is not shown as a scene in
   the Abyssus tree and is not offered as a standalone scene; opening it routes to its `.scene`'s entities. Both
   `SceneFormatListener` handlers pretty-print it only when its scene is supported.
8. **Play and shipped games.** The play launch file and `exportPlay` list both files; the shipped game's loader reads
   the pair. Control Line's `FIELD_SCENE` stays the scene path and the ECS path is derived.
9. **Migration is a throwaway script** run once over the 10 `.scene` files: each `ecs` entry becomes an array element
   `{ "id": <key>, ...entity }` in key order, and the scene loses `ecs`. The script is not committed as an importer.

## Risks / Trade-offs

- [Two documents drift or one is missing] → a missing ECS file is a valid empty scene; tests cover rename and
  delete of the pair and the project view.
- [Half-applied undo] → one command with one group; a UI test undoes an edit that touches both files.
- [Unsaved buffers of two files in the view] → the view's content key covers both documents' modification stamps.
- [~30 main files read `ecs` from the tree] → sequence tasks so the document layer lands first, then readers.
- [Hand-edited ids collide] → admission rejects duplicates; the file stays unchanged.
- [Another open change's deltas place `ecs` inside the scene] → amend them in a task rather than leave contradictions.
