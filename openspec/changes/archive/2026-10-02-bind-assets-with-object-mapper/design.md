# Design

## Context

Today an asset is read twice over: `SceneReader.parse` walks a `JsonNode` by hand into
`SceneDto`, then `SceneDto.properties()` re-emits it as `DtoProperty`/`DtoValue` for the tree.
`ProjectReader` builds `DtoValue.Obj`/`Items` directly for the project, its scenes and its assets
(`AssetInfo` from `ProjectAssets`, showing only `type` and `uuid`).
`DtoValue.Obj` also smuggles view metadata: `label` (list element name), `source` (the file
the object came from, for toggle write-back and "open scene"), `unused` (asset not reached) and
`asset` (entry of the project's `assets` list, picks the asset-type icon).
`dto/Json.kt` holds the `Json` object (one `ObjectMapper` with a custom streaming reader that keeps
float text via `RawNumberNode`, plus a pretty printer) and top-level helpers used well beyond the
DTOs: `opt`/`text`/`float`/`obj` (read the `ecs` tree in `SceneContent`, `ProjectAssetFiles`,
`SceneRenderParams`) and `runCatchingKeepingCancellation` (all `sceneview` loaders). Writes go back
through the `JsonNode` tree (`EnabledToggle.kt`, `SceneJson`) so unknown fields are never lost.
See proposal.md for why.

## Goals / Non-Goals

**Goals:**
- Concrete classes are the only model of a scene/project; Jackson binds them.
- The tree is derived from those objects without a hand-written property list per class.
- Tolerance preserved: a bad field type or unknown field never breaks the whole view.
- Numbers written back keep their exact source text (`2.50`, `1.0E-4`, `-0.0`), as today.

**Non-Goals:**
- Typing `ecs` (stays a `JsonNode`; `migrate-ecs-components` owns that).
- Changing how files are written: edits still mutate the `JsonNode` tree to keep formatting.
- Rewriting the `sceneview` readers of the `ecs` tree; they keep the node helpers.
- Any change to rendered rows, ordering, icons or messages.

## Decisions

1. **The `Json` object goes; `SceneJson` owns the mapper and the raw-number tree.** `SceneJson`
   holds the one `ObjectMapper` (`FAIL_ON_UNKNOWN_PROPERTIES=false`, lenient scalar coercion,
   `SORT_PROPERTIES_ALPHABETICALLY=false`), the pretty printer, and the streaming tree reader with
   `RawNumberNode` moved from `Json.kt` unchanged (one document, trailing content rejected). Reading
   is `SceneJson.parse(text)` then `mapper.treeToValue(node, SceneDto::class.java)`; Jackson's
   tree-traversing parser reads `RawNumberNode` through `numberValue()`/`floatValue()`, so binding
   sees the same floats as today. The "must be an object" check sits at the call sites
   (`ProjectReader`, `ProjectAssets`, `MainCamera`, `EnabledToggle`).
   The helpers `opt`/`text`/`float`/`obj` and `runCatchingKeepingCancellation` stay top-level in
   package `dto` (`Json.kt` keeps only them, or they move to `JsonNodes.kt` / `Cancellation.kt`;
   either way their package and names are unchanged, so callers only lose the `Json` import).
   Classes are Kotlin data classes with nullable, defaulted properties so `{}` binds to an
   all-null `SceneDto`, as `parse("{}")` does now.
   *Alternative:* `USE_BIG_DECIMAL_FOR_FLOATS` + exact BigDecimal nodes, dropping `RawNumberNode` -
   rejected: verified with Jackson 2.22.1 it writes `1.0E-4` as `0.00010` and `3.4028235E38` as
   `3.4028235E+38`, breaking `SceneEditFormattingTest` and rewriting Mundus' `Float.toString`
   output. *Alternative:* `jackson-module-kotlin` - nicer, but another bundled dependency; add
   only if default-value constructor binding fails (Open Question below).
2. **Per-field tolerance via lenient coercion, per-file via the existing error entry.** A
   scene whose JSON cannot bind (wrong type) yields the existing `error` row for that scene, as
   a malformed scene does today. Previously `"fogEnabled":"x"` silently became `false`;
   this narrows to a visible error for genuinely mistyped fields.
3. **No generic value tree; the node holds the typed object.** `DtoEntry.value` becomes
   `Any?` (a bound object, `List`, scalar, or the `ecs` `JsonNode`). Children are enumerated by
   Jackson's own bean introspection (`mapper.serializationConfig.introspect(type).findProperties()`)
   so a class needs no `properties()`; `JsonNode` children come from `properties()` of the node,
   `List` from index. The existing scene-header filter (`id`/`name` shown in the scene label, not
   as rows) still applies. *Alternative:* `valueToTree` then walk JsonNodes - rejected, it brings
   back an untyped tree and loses the object identity that metadata hangs off.
4. **View metadata moves onto the model, not into a wrapper; it is never a row.**
   - `SceneDto` gets `@JsonIgnore var file: VirtualFile?` (toggle target, open-in-scene-view).
   - `AssetInfo` stays the one asset class (no separate `AssetDto`): `name` comes from the folder,
     `references` from `ProjectAssets.parse`, and a new `unused` is set in `ProjectReader`; all
     three are `@JsonIgnore`, so an asset still shows only `type` and `uuid`. Its class replaces
     the `asset` flag for the icon.
   - New `ProjectDto(name, scenes, assets)`; a scene that fails to read is a `SceneError(file,
     error)` element in `scenes`, rendering the same `error` row as today.
   - The list label is a function over the element type (`sceneLabel`, asset `name`, error file name).
   Ignored properties are excluded from both binding and the introspected child list.
5. **Toggle folding on name/value pairs.** `foldToggles` takes `List<Pair<String, Any?>>`
   (name, value) and returns entries with `enabled`/`toggleName`; logic is unchanged.
6. **Renderer reads `SceneDto` as before.** `SceneRenderParams.from`, `SceneContent.of` and
   `ProjectAssets.sceneReferences` already take `SceneDto`; `ecs` stays a `JsonNode`.

## Risks / Trade-offs

- [Introspection order differs from declaration order, reordering rows] → pin with a test
  asserting `scene` and project property order equals the current list; set
  `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY=false` and rely on constructor order.
- [Introspection picks up getters or metadata as rows] → `@JsonIgnore` on metadata; the
  property-order test fails on any extra row.
- [Float precision: `RawNumberNode` bound to `Float`] → same `floatValue()` path as today; the
  existing `color`/`intensity` tests (`AbyssusViewTest`) must pass unchanged.
- [Stricter typing surfaces errors that were silently ignored] → covered by decision 2 and a
  test for a mistyped field.
- [Reflection-based children make a rename of a Kotlin property change the row name] →
  row name is the JSON name by design; `@JsonProperty` pins any that must differ.
- [Mixing with the staged `render-project-models` edits] → sequence apply after it is committed.

## Migration Plan

Pure refactor behind unchanged behavior tests (tests only change to stop naming `DtoValue`/`Json`):
move the mapper first, convert reader and DTOs with tests green, then the nodes/toggle, then delete
`DtoProperty.kt`. Rollback is `git revert`.

## Open Questions

- Is default-value constructor binding enough without `jackson-module-kotlin`? Settled by
  the first binding spike in task 1.1; either answer leaves the task list intact.
