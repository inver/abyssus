# projectView

The **Abyssus** pane of the Project tool window: the project / scene / entity / asset tree, its row actions, and
the row actions that call `editSceneJson` (`projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/filetype/SceneDocumentWriter.kt`), the write path every scene edit uses. Required behavior: `openspec/specs/abyssus-project-view` and
`openspec/specs/abyssus-scene-skybox`.

## Pieces

| Class | Role |
|---|---|
| `AbyssusProjectViewPane` | The pane; its tree paints row actions at the right edge and publishes the selection |
| `AbyssusNodes.kt` | `AbyssusRootNode`, `AbyssusAssetNode` (one per `.abss` / standalone `.scene`) and `DtoEntryNode` (everything below) |
| `ProjectRows.kt` | The Swing-free folder model: `projectRows` gives a project's three rows `scenes`, `ui` and `assets`; `FolderRow` (UI or Assets, holding assets) expands to the UI assets or to one `TypeGroup` per type (`folderRows`, `groupRows`); `typeGroupOrder` is MODEL, TERRAIN, FOLIAGE, TEXTURE, SKYBOX, CLOUDS, then the rest alphabetically, and the three skybox types share the SKYBOX group. Folder rows exist only in the view and write nothing |
| `DtoTree.kt` | DTO → rows: `childrenOf`, `foldToggles` (`<x>Enabled` → eye), display names (`skyboxName` → `skybox`), labels |
| `AbyssusTreeRefresh` | The pane's refresh coordinator, alive as long as the pane: plugin edits (`AbyssusSceneEdited`), document changes (typing, Undo/Redo, unsaved text) and this project's VFS create/delete/move/rename/content events for `.scene`, `.abss`, `meta.json` and folders under `assets` mark the tree stale; `lib-core-editor`'s `RefreshCoalescer` turns a burst into one `updateFromRoot`, plus one more when an event arrives during it; selection and expansion are captured on the EDT as row identities (`NavigationState`: the entry or asset path, never a label) and restored with `restoreNavigation`, so a removed selection stays cleared. Unrelated files do not rebuild; a refresh writes nothing and adds no undo entry; disposal cancels pending work |
| `AssetReadCache` | Parsed files per project, re-read when the reader's `stamp` changes. It takes its readers by constructor; only the one-argument constructor the platform calls looks them up. `AddAssetGroup`, `addComponentGroup` and the add-component choices take `MetaFiles` the same way, resolved in the action's `perform` |
| `RowActions.kt` | What a row paints at its right edge: eye, "View", the amber `unused` text; the `game` badge is painted by the row renderer (`GameBadge.kt`, decided by `isGameComponent`) |
| `scene/SceneComponentEdits.kt` | Add, update and remove a component as undoable `editSceneJson` commands; also lists a project's model and terrain assets |
| `ComponentActions.kt`, `ComponentTarget.kt` | The **Add Component...** / **Remove Component** tree actions and the entity or component a row stands for; `AddComponentOnEcsAction` is Add Component... on a scene's `ecs` row, creating a new `Entity <id>` with the chosen component (`SceneComponentEdits.addAsNewEntity`, inserted by `SceneEntities`) |
| `EnabledToggle.kt` | The writes built on `editSceneJson`: `toggleEnabled`, `renameScene`, `setSkybox` |
| `AbyssusTreeAction` | The Template Method of a right-click action: `targetOf` (the node it applies to), `isEnabled`, `perform`; `update` and `actionPerformed` are final and read the one selection lookup, `selectedNode`. Rename Scene, New Terrain, New Foliage, Import Model, Import FlightGear, New UI Asset and the component and light actions extend it; tests replace `selected` |
| `RenameSceneAction` | Right-click **Rename Scene...**: offered on any scene row — a standalone `.scene` at the top of the view and a scene listed under a project — starting from the scene's current `name` and editing only `name` in one undoable command. Right-click menus group creation/import actions under **Import Model** (Import FlightGear Aircraft, Import Model), **New** (New UI Asset, New Terrain, New Foliage) and **Add** (Add Asset, Add Path Arrangement, Add Light, Add Water); Rename Scene stays flat above **Add** on scene rows.
| `ImportFlightGearAction.kt`, `FlightGearImportSettings.kt` | Right-click **Import FlightGear Aircraft...** on the Assets node: the dialog over a Swing-free settings model, `importFlightGear` (stage off the EDT through `lib-core-editor`'s `flightgear`, then one undoable `AssetFileCommand`), `importTransaction` and the shared `assetFolderTransaction` |
| `ImportModelAction.kt`, `ModelImportForm.kt`, `ImportModelDialog.kt` | Right-click **Import Model...** on the Assets node: the source is read once off the EDT (`lib-core-editor`'s `modelimport`), the dialog binds the Swing-free `ModelImportForm` (settings, values read from the file, placement option, Create state), and `importModel` stages and writes the folder, with Add to scene also the entity, in one command |
| `ImportRefusals.kt` | `projectRefusal`: both import actions refuse a project whose `.abss` is not a supported native document |
| `preview/ModelPreviewCanvas.kt`, `preview/PreviewFraming.kt` | The dialog's live preview: a `GuardedGLCanvas` with its own `GdxRuntime` context (grid, 1 m post, orbit camera, looping animation) and its Swing-free framing math |
| `SkyboxChoices.kt`, `SkyboxPickerModel`, `SkyboxChooserDialog` | The skybox list, its filter and selection logic, and the dialog. The dialog opens from the **Browse** button of the skybox field in Abyssus Properties (`SceneDetailsView`, `chooseSkyboxFor`, project scenes only; the tree row has only its eye). Assigning writes only `skyboxName` through `setSkybox` / `editSceneJson`, one undo step, and the tree row follows the `AbyssusSceneEdited` refresh |
| `EntitySelection.kt` | Selects an entity's row when the scene view picks it |
| `AbyssusSelection` | Publishes the selected node on `AbyssusSelectionListener.TOPIC` (the properties panel listens) |
| `UnusedFilter`, `AbyssusFooter` | "Show Only Unused Assets" (per project) and the counts footer |
| `OpenAbyssusViewActivity` | Switches to this pane on startup only under `-Dabyssus.openView=true` (set by `runIde`) |

Selecting a `.abss` root shows project settings in Abyssus Properties, including the built-in **Physics** checkbox.
It writes only `physicsEnabled` through `editSceneJson`; missing means off. Scene roots retain their ray properties.
Physics component add/edit choices follow the selected scene's native project settings, including unsaved edits.

## Icons

The tree glyphs are the design's Icon set (board "Icon set" of the Abyssus design canvas), copied as 16 px SVGs on a 24 px grid
(1.8 stroke, round caps) into `projects/plugin-abyssus/src/main/resources/icons/tree/<name>.svg`. A hue glyph reads the same
in both themes and has one file; a gray or text-colored glyph (`folder`, `fog`, `component_name`, `component_parent`, the
header `action_*` icons) has a light file and a `<name>_dark.svg` twin that the platform picks in dark themes. The eye is 14 px
(`eye_on` blue `#548af7`, `eye_off` gray `#6f737a`). `TreeIcons.load(name)` loads one; the holders in `filetype/AbyssusFileTypes.kt`
(`ScenesIcons`, `PropertyIcons`, `EyeIcons`, `TreeActionIcons`, `ComponentIcons`, `AssetIcons`) and `EntityIcons` map node kinds to
them. Asset types and entity types that the board does not draw (SKYBOX, SKYBOX_HDR, PIXMAP_TEXTURE, MATERIAL, point and spot
lights, camera, handle) keep their older SVGs in `icons/`. `TreeIconInventoryTest` fails naming the file for a listed icon
that is missing and for a file in `icons/tree/` that nothing lists; add a new glyph to its lists in the same change.

## Things that are not obvious

- **Foliage contributes transitive asset usage.** `FoliageComponent.assetName` makes its foliage folder used.
  A used foliage reaches its `additional.terrain` and every `additional.layers[].models[].asset` by folder name,
  then their texture and material references by UUID. An unused foliage does not make its models used.
- **Import Model with Add to scene is one undo step.** `importModel` runs one outer command:
  `AssetFileCommand.execute` hands back its undo action, then `SceneComponentEdits.addAsset` adds the entity, and
  the action is registered only once both succeeded (otherwise the folder is reverted and nothing is recorded). The
  platform undoes the folder before the scene edit, so the folder's `AssetReferenceGuard` is told to ignore the
  entity the import itself placed (`PlacedEntity`). The VFS is refreshed only after the command, as for every
  `AssetFileCommand`.
- **The preview releases GL before its window closes.** `ImportModelDialog` calls `ModelPreviewCanvas.release()` in
  `doOKAction`, `doCancelAction` and `dispose`, while the canvas still shows, so `disposeGL` runs with its context
  current; a canvas that never became `glSafe` made nothing.
  `ModelPreviewCanvasGlTest` (opt-in GL) covers drawing, framing, the animation loop, rebuilds, a failed frame and
  release while showing; `PreviewFramingTest` the framing math.
- **Tree shape.** A `.abss` expands to `Scenes`, `UI`, `Assets` (no `name` row, nothing truncated); `Assets` holds a type group per asset type (Models, Terrains, Foliage, ...), UI assets sit under `UI` with their screens. Entry paths are `scenes/<i>`, `ui/<asset>`, `assets/<TYPE>/<asset>`. The unused filter cuts the `UI` and `Assets` folders' asset lists before they group, so empty groups vanish and counts follow what is listed. Right-click creation actions (Import Model, New Terrain, ...) belong to the `Assets` row; no folder row offers a component or rename action.
- **Row identity is the entry path.** A `DtoEntry` is identified by its path inside the asset (the file path, then one segment per row, such as the root `fog` member).
  Its `equals` also compares the toggle state, scalar values, entity type and the unused flag, because the tree keeps an existing
  node when the refreshed one is equal. Leaving any of those out leaves a row stale after an edit.
- **Entity icons follow `TypeComponent.type`.** Each built-in type has a distinct SVG icon; missing, null or unknown
  types retain the generic ECS icon. Both short and fully qualified `TypeComponent` keys are recognized.
- **After an edit the row is selected again.** An edit changes the row's identity, so the refresh drops the selection
  onto the parent. `reselect` walks back to the same path and restores its expansion.
- **Every edit of an existing JSON document goes through `editSceneJson`.** It parses the document with `SceneJson`, lets the caller mutate the
  tree (returning false writes nothing), re-serializes in the file's style, replaces the text in a named
  `WriteCommandAction`, saves, and publishes `AbyssusSceneEdited.TOPIC`; this pane refreshes on it, and equally on Undo/Redo and external changes, through `AbyssusTreeRefresh`. A new writer should use it and add a `command*` message.
- **Where an entry writes back.** An entry knows its `source` file and the `parentKeys` leading to its container. A
  scene listed under a project writes to its own `.scene` file, not the `.abss`.
- **Display names are labels only.** `displayName` changes what a row shows. `DtoEntry.name` stays the JSON key,
  because toggle folding, icons and write-back match on it.
- **Painting a selected disabled row:** the platform repaints selected rows in the selection colour.
  `GrayKeepingRenderer` keeps the gray on disabled rows so they still read as disabled while selected.
- **HDR sizes come from headers.** `hdrSkyInfo` reads the EXR data window off the EDT without decoding pixels.
  An unreadable header keeps the filename with unknown dimensions; preview failures still show their own reason.

## Add Light

`AddLightAction` offers Directional, Sun and Spot on scene rows only, including standalone scene files.
`AddLightGroup` is shared with the Scene view toolbar. Tree placement is the origin; Spot is 5 units above it.
`SceneComponentEdits.addLight` writes through `editSceneJson` as one undoable Add Light command, then the action
selects the new entity through `selectEntityInAbyssusView`. Invalid scene text disables creation.

## Add Asset

`AddAssetAction` (scene rows) and the Scene view toolbar share `AddAssetGroup`: the project's models and terrains
from `SceneComponentEdits.renderAssets`, under Models / Terrains, labelled with the folder name as it is (no mnemonic).
`SceneComponentEdits.addAsset` reads a terrain's `additional.size`, and `AssetEntities` (`components/AssetEntities.kt` in `lib-core-editor`) builds the entity
on the JSON tree. It is written as one undoable Add Asset command and selected with `selectCreatedEntity`, which
Add Light uses too. Disabled for unreadable scene text, a scene outside a project, or a project without models and
terrains.

## Water rows

A scene row lists its water surfaces under a **Water** group, shown only when `abyssus.waterSurfaces` holds surfaces
(`sceneRows` in `DtoTree.kt` drops the raw `abyssus` member and adds the group, so a scene without water shows no extra
row and reading one writes nothing). The group's value is a `WaterGroup` (`water/WaterText.kt`) and each row's a
`WaterSurface` from `WaterCodec`: a valid one shows its name with "Abyssus only" and an eye (`DtoRow.enabled` is its
`enabled`), an invalid one shows "invalid", no eye, and can still be renamed or removed. A water row is not an ECS
entity or component: `waterTargetOf` (`WaterActions.kt`) maps it to a `WaterTarget(file, id, name)` and
`componentTargetOf` does not match it. The eye calls `toggleRow`, which writes only that surface's `enabled` through
`WaterSceneEdits`; `AddWaterAction` (scene rows, Lake or Sea at the origin), `RenameWaterAction` and `RemoveWaterAction`
(water rows) use the same adapter. `AddWaterGroup` is shared with the Scene view toolbar. After an add the new row is
selected (`selectCreatedWater`, `selectWaterInAbyssusView`). A refused add (an `abyssus` or `waterSurfaces` value that is
not an object) shows its reason and writes nothing.

## Add Path Arrangement and Regenerate

`AddPathArrangementAction` (scene rows) shares `AddPathArrangementGroup` with the Scene view toolbar: the project's MODEL
assets by folder name. A choice opens the scene's Scene view in drawing mode (`startDrawingIn`). It is disabled for
unreadable scene text, a scene outside a project, or a project without models. `RegenerateArrangementAction` appears on
the row of an entity with a `PathArrangementComponent` and re-places its copies from the current terrain
(`ArrangementEditing.regenerate`); it writes nothing when no copy would move.

## New Weather Preset from Sky

`NewWeatherPresetAction` appears only on a supported procedural sky asset row with a nonblank textual
`additional.clouds` reference. `NewWeatherPresetFactory` validates the owning project, resolves the UUID among
its native asset metadata with unsaved text taking precedence, and re-reads the source on Create.
`WeatherPresetDraft` in editor-core snapshots the canonical settings without loading clouds or generating noise.

The dialog suggests `weather_<sky>` and shares New Terrain's folder-name validation. The factory stages one new
metadata file through `AssetTransaction`; `AssetFileCommand.execute` owns rollback and Undo/Redo, and
`AssetReferenceGuard` refuses deletion once saved or unsaved documents reference the snapshot.
`selectAssetInAbyssusView` selects the created cloud asset after refresh. Its UUID, timestamp and bytes remain
stable on Redo. Source cloud metadata, the sky, scenes and project are never edited by creation.

## Arrangement copies in the tree

`ecsRows` (`DtoTree.kt`) leaves out the linked copies of a path arrangement (`arrangementCopyIds` in `lib-core-editor`: the
entities an arrangement lists in `slots` that point back to it with a `ParentComponent`), and the arrangement's row reads
`N components, M copies`. A detached copy is listed as an ordinary entity. Only the tree changes: a hidden copy has no row,
so picking it in the Scene view selects nothing in the tree (`selectEntityInAbyssusView` does nothing for a missing row).

## Shared action and refresh flows

`ui.showActionGroup` creates the searchable action popup for tree actions and the Properties Add Component button.
`AddChildGroup<T>` supplies background enablement and dispatch for light, asset, path-arrangement and water choices;
each group supplies its target check and add operation. `TreeSelection.selectInAbyssusView` owns switching to the
Abyssus pane and selecting a visitor, with an optional refresh for newly created assets.

`dto.isNativeDocumentPath` recognizes `.scene`, `.abss` and `meta.json`. The project service `dto.NativeFileChanges`
owns one VFS connection for the tree, footer and Game UI resource watcher. Native subscribers receive document,
directory and rename candidates and retain their own project/path filters; the resource channel lets Game UI retain
its image/font folder filter. Each subscription is disposed with its original consumer. Other VFS subscribers keep
their own connections and filters.
