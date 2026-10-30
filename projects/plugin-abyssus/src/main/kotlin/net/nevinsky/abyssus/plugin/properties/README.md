# Properties panel

`PanelState` reads selections and `AssetPropertiesPanel` renders them. Component fields and edit validation come from
`lib-core-editor`; the editors of a field (text, choice, check box, with confirm, refuse and revert) are shared between
asset and component fields through `plugin/ui/FieldEditors.kt`, and a field's label and tooltip come with its description
(`FieldValue.label`, `FieldValue.tooltip`). `ComponentSchemas.editorFor(sceneFile)` supplies built-in kinds and three typed physics kinds only when
the scene's native project has admitted `physicsEnabled: true`. Game components and disabled physics entries remain
read-only JSON. Settings changes publish a component-editor refresh; each edit obtains the current editor again.

Project roots show `PanelState.Project` through `ProjectDetailsView`: the localized Physics checkbox, Scene view antialiasing combo and settings
problems come from `AbyssusProjectSettings`. Unsupported files disable editing. Document/settings events refresh the
controls without saving or reselection, and the panel supplies a text editor for platform Undo/Redo of the `.abss`.
Scene properties retain the ray settings controls. The foliage section (`plugin/foliage`) offers collider controls on
OBJECT layers; a change that touches only colliders (`FoliageColliderEdits.onlyColliders`) is applied by
`FoliageController.applyColliders` through `editSceneJson` as one command with the document-save actions that keep Undo
and Redo saved, with no preview, scatter or bake involved, and it is dropped when the document text changed since the
edit was made, when the document is read-only or when the controller was disposed. Asset and entity writes use `editSceneJson`; UI state changes and
document listeners run on the EDT. Asset previews decode in the background and publish through the panel's UI executor.

## Layout (the design board)

Every selection draws a `PanelModel` from `lib-core-editor`'s `panel` package (see its README): a `HeaderPanel` (breadcrumb, 24 px
icon, 15 px title, subtitle, `read-only` / `no scene uses this` badges), one `PropertySection` per `Section` (collapse chevron,
kind icon, title, `game` badge, class hint, `Enabled` check box), and the footer note. The building blocks are in `plugin/ui`
(`PanelBlocks.kt`: `PanelMetrics`, `BadgeLabel`, `HeaderPanel`, `PropertySection`; `RowControls.kt`: `fieldRow`, `rowComponent`,
`RowHooks`; `PanelIcons.kt`: the icon keys; the colors are `DesignColors`). Geometry at scale 1: a 150 px label column, 24 px
fields, a 26 px Browse button, a 34 px section header and a 20 px body indent. `PanelModelView` assembles the frame; the per-kind
views (`EntityDetailsView`, `ProjectDetailsView`, `SceneDetailsView`, `StructuralView`, `WaterPropertiesView`, the asset
`details`) only supply the hooks: committing a field, the hosted typed editors (`CustomRow`: terrain, foliage, faces, HDR,
antialiasing, show FPS, Rendering) and the links.

- **Selections.** Project, folder (`Scenes`, `UI`, `Assets`, a type group; read only), scene (with its Rendering section), one scene
  setting, `ecs` (read only), entity, one component, UI screen (`Open in Screen Editor`), water surface and asset. `structuralOf`
  classifies the tree rows that stand for no document of their own; `readStructuralState` reads what their layout needs.
- **Dim defaults.** A value the file does not write is drawn in `DesignColors.SECONDARY`; it stays editable, and editing it writes
  it. The footer says so. Showing a panel writes nothing.
- **Enabled.** A togglable component section (everything except Name, Parent and Position) and a scene setting have an `Enabled`
  check box. A component's writes `enabled: false` on its entry through `setEnabled` (the same action as the tree eye); a setting's
  writes `<x>Enabled` through `setSceneToggle`. Both are one undoable `editSceneJson` command. A disabled section is drawn at 55 %
  opacity and its fields stay editable.
- **Collapse.** Each section's collapsed state is kept in `AssetPropertiesPanel.collapsedSections` by section key. A rebuild of the
  same node (a file change, a toggle) keeps it; selecting another node clears it.
- **Names for tests.** Component names are kept: `field-<Kind>-<field>`, `error-<Kind>-<field>`, `label-<Kind>-<field>`,
  `remove-<Kind>`, `raw-<Kind>`, `add-component`, `entity-name`, `scene-name`, `project-name`, `physics-enabled`, `show-fps`,
  `scene-view-antialiasing`, `asset-field-<key>`, `asset-error-<key>`, `water-*`; the frame adds `header-*`, `badge-*`,
  `section-<key>`, `section-title-<key>`, `section-chevron-<key>`, `section-enabled-<key>`, `section-hint-<key>`, `panel-footer`.
- **Empty state.** An icon, `Nothing is selected.` and a hint. The title-bar Options and Hide are the platform's tool window actions.

