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
