# Design

## Context

- Rows of the Abyssus view are `DtoEntryNode`s whose `DtoEntry.name` is the JSON/bean property name. That name drives
  more than the label: `foldToggles` matches `skyboxEnabled` to `skyboxName` by it, `PropertyIcons.forProperty` picks the
  skybox icon by it, and `parentKeys` (used to write back) are built from it.
- `EyeTree` in `AbyssusProjectViewPane.kt` paints at most one `RowAction` per row (the eye, or "View" on scenes) at the
  right edge, and hit-tests clicks against that one icon's bounds.
- All file writes go through `editJson` in `EnabledToggle.kt`: parse the document, mutate the Jackson tree, re-serialize
  in the file's own style (`SceneJson.inStyleOf`), save in a `WriteCommandAction` (undoable), refresh the pane.
- A project scene's `DtoEntry.source` is its `.scene` file; `ProjectLayout.abssFor(source)` finds its `.abss`, and
  `ProjectAssets.read(abss)` returns `AssetInfo(name = folder, type = meta type)` for every asset folder.
- The `.scene` format is Mundus's; the key must stay `skyboxName`.

## Goals / Non-Goals

**Goals:**
- Display-only rename, without touching the property name the rest of the view keys on.
- A reusable way to put more than one clickable icon on a row.

**Non-Goals:**
- Renaming `SceneDto.skyboxName` or the JSON key.
- `SKYBOX_HDR` assets: the scene renderer resolves only six-face `SKYBOX` assets, so offering HDR ones would let the
  user pick a skybox that never draws.
- Previews/thumbnails of skyboxes in the dialog, or creating/importing skyboxes.
- The design board's Scene properties panel, "Choose…" button and `postShader` picker.
- A chooser for standalone scenes (no project, so no assets to list).

## Decisions

### Rename as a display label, not a data rename
Add a small display-name map in `DtoTree.kt` (`"skyboxName" -> "skybox"`) applied in `DtoEntryNode.update` where it
falls back to `v.name`. `DtoEntry.name` stays `skyboxName`.
*Alternative:* rename the Kotlin property with `@JsonProperty("skyboxName")`. Rejected: `beanProperties` lists Jackson's
serialized names, so the row would still read `skyboxName`, and toggle folding would need a special case anyway.

### Multiple row actions in `EyeTree`
`actionFor(row): RowAction?` becomes `actionsFor(row): List<RowAction>`, ordered right-to-left. `iconBounds(row, index)`
lays icons from the right edge with `ICON_GAP` between them; painting, cursor and tooltip iterate the list; a click runs
the action whose bounds contain the point. The eye stays rightmost so its position does not move for rows that gain `...`.
*Alternative:* render `...` as a text fragment in the label. Rejected: the renderer cannot hit-test text fragments as
reliably as the existing icon bounds, and it would shift with label length.

### When the `...` shows
On a `DtoEntryNode` whose entry has `name == "skyboxName"`, `parentKeys` empty (a scene's top-level property) and a
`source` with `ProjectLayout.abssFor(source) != null`. Icon: `AllIcons.Actions.More`; tooltip from the bundle
("Choose skybox...").

### Listing skyboxes
`skyboxChoices(project: ProjectDto, metas: Map<String, JsonNode?>): List<SkyboxChoice>`, a pure function returning
`SkyboxChoice(name, faces: Int, formats: List<String>, sceneCount: Int, unused: Boolean)` for each `AssetInfo` with
`type == "SKYBOX"`, sorted by folder name.
- `unused` is the `AssetInfo.unused` of the `ProjectDto` that `AssetReadCache.of(project).read(abss)` returns, the same
  read the tree shows, so the badge always matches the tree's mark.
- `sceneCount` counts the project's scenes whose `ProjectAssets.sceneReferences` contain the folder name.
- `faces`/`formats` come from the folder's `meta.json` `additional`: the non-blank values of the six face keys
  (`top`, `bottom`, `left`, `right`, `front`, `back`), their lower-cased extensions de-duplicated and sorted. Rendered as
  `<faces> faces · <formats joined by ", ">`.

On click, the `meta.json` files of the `SKYBOX` folders are parsed with `SceneJson.parseObject` (a handful of small
files, on the EDT; acceptable for a user gesture and avoids keeping another cache in sync).
*Alternative:* extend `AssetInfo` with the face list. Rejected: every asset would carry skybox-only data the tree never
shows.

### Dialog
Laid out after the "Choose asset for scene" board of the Abyssus Panel Design
(https://claude.ai/artifact/FCGmbq4BAJW9PK6uvBDfke), in platform components so it follows the IDE theme rather than the
board's fixed colors.
- `SkyboxChooserDialog : DialogWrapper`, title "Choose a skybox". The `...` stays the trigger (the board's Scene
  properties panel and "Choose…" button, and its `postShader` field, are not built).
- North: a header label with "N found", then a "Filter by name" `SearchTextField`.
- Center: a `JBList` in a `JBScrollPane` over a `SkyboxPickerModel`, a plain class with no Swing in it holding
  `all: List<SkyboxChoice>`, `filter`, `selected: String?` and derived `entries` (None first, then matches),
  `foundCount`, `noMatch` and `footerText`. The dialog only forwards filter edits and list clicks to it, so the
  behavior in the spec is unit-tested on the model. When `noMatch`, a line under "None" shows "No asset of this type
  matches the filter."
- Cell renderer: a two-line `JPanel` (icon, name over a monospace detail line, badge on the right). "None" uses a dash
  icon and the detail "clear the field". The badge is a small rounded "unused" label in the tree's unused color, else a
  gray "used by N scene(s)".
- South: "Selected: …" on the left, then the dialog's own actions: Cancel and the OK action renamed **Assign**
  (`setOKButtonText`). Double-click on an entry also assigns.
- A selected entry hidden by the filter stays selected, and the footer keeps naming it.
- The dialog exposes `chosen: String?` after `showAndGet()` returns true.

*Alternative:* a `JBPopup` list chooser. Rejected: it can't hold the footer and explicit Assign, and the user asked for
a dialog.

### Writing
`setSkybox(project, file, name: String?): Boolean` in `EnabledToggle.kt` via `editJson`: set `skyboxName` on the root
`ObjectNode` to `TextNode`/`NullNode`; return false (no write) when the value is already equal. After a write, `EyeTree`
calls the existing `reselect(entry.path, …)` since the row's identity changes with its scalar value.

## Risks / Trade-offs

- [Two icons crowd a narrow pane] → icons are 16px with an 8px gap; the label is already clipped by the platform renderer
  before the icons.
- [Reading `meta.json` on the EDT] → only `SKYBOX` folders, bounded by the asset count; move to a background read with
  a modal progress only if it proves slow.
- [Unused marks not refreshing after Assign] → the project read's stamp already folds in every scene file's
  modification stamp (`ProjectReader.stamp`), so the write invalidates the cached project and the pane's
  `updateFromRoot` re-reads it.
- [`AbyssusViewTest` expectations hard-code `skyboxName: null`] → updated in the same task as the label change.
- [In-flight `add-abyssus-project-view` spec says the eye and Rename Scene are the only writers] → that spec text is
  amended in tasks so the two changes don't contradict each other when archived.
