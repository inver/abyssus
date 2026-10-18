# gameui

The plugin side of Game UI authoring (specs `game-ui-authoring`, `game-ui-runtime`, `game-ui-preview`). Everything that
decides what a UI asset means lives in `lib-core` (`ui`, `assets/ui`) and `lib-core-editor` (`editor/ui`); this package is
the IDE glue: tabs, panels, the GL canvas and the commands that write files.

| File | What |
|---|---|
| `UiFileEditor.kt` | `UiFileEditorProvider` (accepts a `meta.json` of type `UI` inside a project, an unsupported version included so its reason can be shown), `UiFileEditor` (one tab; re-reads the document, typing after a pause, Undo / Redo at once) |
| `UiEditorController.kt` | The model of one tab with no Swing: parsed state, selection, screen, mode and every edit. Parsing runs on a background executor and a result is dropped when a newer read was requested; writes are `editUiMeta` commands. `UiCanvasModel` is the slice the canvas sees. `UiCanvasGestures` (the canvas drag and its one command) and `UiViewSettings` (preview size and backdrop per viewer) are plain classes it delegates to |
| `UiEditorPanel.kt`, `UiEditorViews.kt` | The panel and its parts: screen list, widget tree (drag and drop uses the pure `treeDrop`), palette |
| `UiPropertiesModel.kt`, `UiPropertiesPanel.kt` | Which rows a widget has (from its kind and its parent's layout policy) and the panel that renders them; rows are rebuilt only when their structure changes |
| `UiDeclarationForms.kt`, `UiDeclarationsPanel.kt` | Bindings, styles and resources: validated forms (pure) and the panel with Import Image / Font |
| `UiCanvasView.kt` | The GL canvas: one `UiPresenter` per document revision, selection / hover / drag-ghost overlay. `UiCanvasInput` turns pointer and key events into gestures or presenter input (Swing, no GL); `PreviewFit` holds how the preview sits in the panel |
| `UiAssetCommands.kt`, `UiResourceImport.kt` | Writes: `editUiMeta`, registry edits, `NewUiAssetFactory` + `createUiAsset`, `UiResourceImporter` + `importUiResource` |
| `UiAssetActions.kt` | Tree actions: New UI Asset, Register, Unregister; `ProjectUiSource` (other registered UI assets for navigation) |

## Writing

- **Existing metadata** is changed only through `editSceneJson` (`editUiMeta`): the pure edit runs in
  `UiTextEditor.transform`, so the document must be admitted UI before and after, and one edit is one undo. A drag passes
  the text it began from and is refused when the document changed.
- **Creation** (a new asset folder, imported files) cannot be a document edit. `NewUiAssetFactory` stages one
  `AssetTransaction` with the new `meta.json` and the project file with the name registered; `importUiResource` runs the file
  transaction and the `resources` declaration in one `CommandProcessor` command, as foliage Apply does. A project file or
  `meta.json` with unsaved changes is refused rather than overwritten.

## Threads

Document text and selection are read on the UI thread; parsing and validation run on a background executor and are
published on the UI thread only if no newer read was requested. All libGDX work (the presenter, resources, the kit)
runs inside `GdxRuntime.withContext` on the AWT thread and only while `GuardedGLCanvas.glSafe()` holds. Closing the tab
disposes the canvas, which releases the presenter, resources and GL kit while its context is current.

## Tests

`UiAssetCommandTest` (creation, undo, rollback, conflicts), `UiEditorProviderTest`, `UiEditorControllerTest`,
`UiEditorViewsTest`, `UiPropertiesTest` run headless. `UiCanvasViewGlTest` opens a window and reads pixels back: run it with
`-Dabyssus.glTests=true`.
