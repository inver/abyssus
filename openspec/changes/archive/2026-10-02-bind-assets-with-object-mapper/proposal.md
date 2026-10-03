# Proposal

## Why

The Abyssus view reads every asset into a hand-built `DtoValue`/`DtoProperty` tree, and each
DTO (`SceneDto`, `FogDto`, `ColorDto`, `BaseLightDto`) re-describes its own fields as
`properties()` plus manual field-by-field parsing in `SceneReader.parse`; `ProjectReader` builds
the project and asset entries as raw `DtoValue.Obj`s. That is three parallel descriptions of one
shape. Jackson is already bundled and already parses the files, so deserializing straight into
concrete objects removes the intermediate tree and the boilerplate.

## What Changes

- Remove `DtoProperty`, `DtoValue` (`Scalar`/`Obj`/`Items`), `DtoSource`, `toDtoValue()` and the
  per-DTO `properties()` implementations.
- Deserialize `.scene` and `.abss` files with `ObjectMapper` into concrete Kotlin classes
  (`SceneDto`, `ColorDto`, `FogDto`, `BaseLightDto`, a new `ProjectDto`, and the existing
  `AssetInfo` for `ProjectAssets` entries); drop the manual `color()`/`opt()` parsing in `SceneReader`.
- Remove the `Json` object from `dto/Json.kt`. Readers and the two write paths use one
  `ObjectMapper` owned by `filetype/SceneJson.kt`, which also takes over the pretty printer and the
  raw-text float node (`RawNumberNode`), so every number keeps its source text exactly as today.
  The JSON-node helpers (`opt`/`text`/`float`/`obj`) and `runCatchingKeepingCancellation`, used
  across `sceneview`, stay as top-level functions in `dto/`.
- The project-view nodes render those concrete objects directly; the `xxxEnabled` folding,
  list labels, scene file source, asset icon and the "unused asset" flag are carried by the typed
  model rather than by `DtoValue.Obj` fields.
- No user-visible change: same tree, same labels, same ordering, same toggle and rename edits,
  same bytes written back.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. Observable behavior is unchanged (`skip_specs: true`); existing view specs
(`abyssus-project-view`, `abyssus-project-assets`) keep holding and their tests are the guard.

## Impact

- Code: `dto/DtoProperty.kt` (deleted), `dto/Json.kt` (`Json` object removed; helpers kept or moved),
  `filetype/SceneJson.kt` (owns the mapper), `dto/AssetReader.kt`,
  `dto/ProjectAssets.kt`, `scene/*Dto.kt`, new `ProjectDto`, `projectView/AbyssusNodes.kt`,
  `projectView/EnabledToggle.kt`, `sceneview/SceneRenderParams.kt` (`MainCamera` parses through `Json`).
  `sceneview` readers of the `ecs` tree (`SceneContent`, `ProjectAssetFiles`, `TerrainLoader`,
  `SceneFileEditor`, `SceneParamsSource`) only change imports if the helpers move.
- Tests: `AbyssusViewTest`, `ProjectAssetsTest`, `SceneEditFormattingTest` stop referencing
  `DtoValue`; `ProjectAssetsTest`, `SceneEditFormattingTest`, `SceneJsonTest`, `SceneRenderGlTest`
  stop referencing `Json`; `CancellationTest` follows `runCatchingKeepingCancellation` if it moves.
- Dependencies: none new (`jackson-databind` already declared); a Kotlin Jackson module is
  avoided unless constructor binding proves insufficient.
- Overlap: `show-project-assets` is committed (`c05ad14`). The staged `render-project-models` work
  edits the same files (`AssetReader`, `ProjectAssets`, `AbyssusNodes`, `EnabledToggle`,
  `SceneRenderParams`); apply after it is committed. This change reverses the `show-project-assets`
  non-goal "Binding scene files to typed Jackson POJOs". `migrate-ecs-components` reads `ecs` through
  the same mapper; whichever lands second follows the other's location.
