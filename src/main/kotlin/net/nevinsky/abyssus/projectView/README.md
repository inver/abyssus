# projectView

The **Abyssus** pane of the Project tool window: the project / scene / entity / asset tree, its row actions, and
`editSceneJson`, the write path every scene edit uses. Required behavior: `openspec/specs/abyssus-project-view` and
`openspec/specs/abyssus-scene-skybox`.

## Pieces

| Class | Role |
|---|---|
| `AbyssusProjectViewPane` | The pane; its tree paints row actions at the right edge and publishes the selection |
| `AbyssusNodes.kt` | `AbyssusRootNode`, `AbyssusAssetNode` (one per `.abss` / standalone `.scene`) and `DtoEntryNode` (everything below) |
| `DtoTree.kt` | DTO → rows: `childrenOf`, `foldToggles` (`<x>Enabled` → eye), display names (`skyboxName` → `skybox`), labels |
| `AssetReadCache` | Parsed files per project, re-read when the reader's `stamp` changes |
| `RowActions.kt` | What a row paints at its right edge: eye, "View", the `unused` tag, the skybox **Choose** button |
| `EnabledToggle.kt` | `editSceneJson`, plus the writes built on it: `toggleEnabled`, `renameScene`, `setSkybox` |
| `RenameSceneAction` | Right-click **Rename Scene...** |
| `SkyboxChoices.kt`, `SkyboxPickerModel`, `SkyboxChooserDialog` | The skybox list, its filter and selection logic, and the dialog |
| `EntitySelection.kt` | Selects an entity's row when the scene view picks it |
| `AbyssusSelection` | Publishes the selected node on `AbyssusSelectionListener.TOPIC` (the properties panel listens) |
| `UnusedFilter`, `AbyssusFooter` | "Show Only Unused Assets" (per project) and the counts footer |
| `OpenAbyssusViewActivity` | Switches to this pane on startup only under `-Dabyssus.openView=true` (set by `runIde`) |

## Things that are not obvious

- **Row identity is the entry path.** A `DtoEntry` is identified by its path inside the asset (the file path, then one segment per row, such as `/fog`).
  Its `equals` also compares the toggle state, scalar values and the unused flag, because the tree keeps an existing
  node when the refreshed one is equal. Leaving any of those out leaves a row stale after an edit.
- **After an edit the row is selected again.** An edit changes the row's identity, so the refresh drops the selection
  onto the parent. `reselect` walks back to the same path and restores its expansion.
- **Every write goes through `editSceneJson`.** It parses the document with `SceneJson`, lets the caller mutate the
  tree (returning false writes nothing), re-serializes in the file's style, replaces the text in a named
  `WriteCommandAction`, saves, and refreshes this pane. A new writer should use it and add a `command*` message.
- **Where an entry writes back.** An entry knows its `source` file and the `parentKeys` leading to its container. A
  scene listed under a project writes to its own `.scene` file, not the `.abss`.
- **Display names are labels only.** `displayName` changes what a row shows. `DtoEntry.name` stays the JSON key,
  because toggle folding, icons and write-back match on it.
- **Painting a selected disabled row:** the platform repaints selected rows in the selection colour.
  `GrayKeepingRenderer` keeps the gray on disabled rows so they still read as disabled while selected.
