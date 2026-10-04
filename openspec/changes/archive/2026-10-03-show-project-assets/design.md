# Design

## Context

`ProjectReader` (dto/AssetReader.kt) turns an `.abss` file into a `DtoValue.Obj` with `name` and
`scenes`; scene files are read through `SceneReader` into `SceneDto`, whose `ecs` is kept as a
generic JSON tree. JSON is handled with Gson everywhere: parsing in `AssetReader.kt`, tree
conversion in `DtoProperty.kt`, `SceneDto.ecs`, `SceneRenderParams.kt`, and parse/serialize in
`EnabledToggle.kt`. Gson comes only transitively from the platform; `build.gradle.kts` declares
no JSON dependency. Asset folders look like `assets/<name>/meta.json` with `type` in
`MODEL|TERRAIN|SKYBOX`, and ECS render components reference them by
`...renderable.asset.assetName == <folder name>`; skyboxes are also referenced by
`SceneDto.skyboxName`.

## Goals / Non-Goals

**Goals:**
- Show assets and their used/unused state in the existing tree with minimal new node types.
- One JSON library (Jackson) across reading and the two write paths.

**Non-Goals:**
- Deleting, renaming or opening assets, or navigating to their files.
- Recognizing references outside `assetName` / `shaderKey` / `skyboxName` and the listed
  `meta.json` reference fields. Files named inside an asset's `meta.json` are not followed.
- Binding scene files to typed Jackson POJOs; the tolerant tree reading stays.

## Decisions

1. **Assets are a DTO property, not a new node kind.** `ProjectReader` appends
   `DtoProperty("assets", DtoValue.Items(...))`; each element is a `DtoValue.Obj` labelled with the
   folder name, with a `type` scalar. The generic tree rendering and its caching then work
   unchanged. *Alternative:* a dedicated `AssetNode` class - more UI code for no behavior gain.
2. **Unused flag is carried on the element.** `DtoValue.Obj` gets an `unused: Boolean = false`,
   and `AbyssusNodes` renders such rows grayed with a localized "unused" suffix, mirroring how the
   disabled-eye state grays text. *Alternative:* a separate `unusedAssets` list - loses the
   in-row marker the spec requires.
3. **Usage is computed from the already-parsed scene trees.** While reading scenes,
   `ProjectReader` walks each `ecs` `JsonNode` collecting every string under an `assetName` key
   (`findValues("assetName")`), every string under `shaderKey`, plus `skyboxName`; these folder
   names seed the used set. Mundus resolves a shader by `AssetKey(SHADER, shaderKey)` (the folder
   name), checking project shader assets before the editor's bundled ones, so a `shaderKey` with no
   project folder is a bundled shader and is simply ignored. Keying on
   the field name rather than a component path keeps it correct if Mundus adds renderable kinds.
   The seed is then closed over asset-to-asset references: assets are indexed by folder name and by
   `meta.json` `uuid` (Mundus `Asset.getID()`), and each used asset's `additional` block is
   scanned for a fixed list of reference fields - `splatMap`, `splatBase`, `splatR`, `splatG`,
   `splatB`, `splatA` (`TerrainMeta`) and every entry of `materials` (`ModelMeta`) - each holding
   another asset's `uuid`. A worklist runs to a fixed point, so cycles terminate and unknown
   `uuid`s are skipped. The list is explicit, mirroring Mundus's `TerrainAsset.resolveDependencies`,
   so adding a field later is a one-line change. `MaterialMeta` texture fields are file names inside
   the material's own folder (`meta.getFile().child(...)`), not asset links, so they are not followed.
   Likewise a shader's `meta.json` (`vertex`, `fragment`, `shaderClass`) names files in its own
   folder, so nothing is followed from a shader.
   *Alternative:* substring search of raw scene text - cheaper but matches names inside labels.
   *Alternative:* treat any string equal to a known `uuid` as a reference - catches new fields
   automatically but can match unrelated values such as a `file` name.
4. **Cache stamp covers assets.** `ProjectReader.stamp` already folds scene stamps; it also folds
   the `assets` folder children's `meta.json` stamps and the folder listing so adding or deleting
   an asset refreshes the node.
5. **Jackson via one shared `ObjectMapper`.** A single `internal val Json = jacksonObjectMapper()`
   style mapper (plain `jackson-databind`; Kotlin module only if needed) replaces `JsonParser`.
   `JsonNode` replaces `JsonElement` in `DtoProperty.toDtoValue` and `SceneDto.ecs`. For writing,
   an `ObjectWriter` with default compact output reproduces Gson's `disableHtmlEscaping` +
   `serializeNulls` output (Jackson never HTML-escapes and always writes nulls); object key order
   is preserved by `ObjectNode`. Number formatting is the one place output could differ
   (`1.0` / `100` / floats), so the toggle test compares bytes before and after a double toggle on
   the existing fixtures. *Alternative:* keep Gson for writing - rejected, the goal is one library.

## Risks / Trade-offs

- [Jackson on the plugin classpath collides with the platform's bundled copy] → depend on the
  platform-provided Jackson if present for the target IDE, otherwise bundle it; verify with
  `verifyPlugin` and a platform test that runs the readers.
- [Re-serialization changes number text, e.g. `100` vs `100.0`] → parse with
  `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` if the double-toggle byte test fails, and
  keep the test as the guard.
- [Large `ecs` trees make the usage walk slow] → walk happens once per cached project read, not
  per repaint.
- [Assets reached only through a reference field not on the list show as unused] → the list mirrors Mundus's own dependency resolution; extending it is one line.
- [Both the folder name and the `uuid` identify an asset, and a dangling `uuid` is possible] →
  unknown ids are ignored rather than reported.

## Open Questions

- Whether the unused marker should later offer a "delete unused assets" action.
