# Properties panel

`PanelState` reads selections and `AssetPropertiesPanel` renders them. Component fields and edit validation come from
editor-core. `ComponentSchemas.editorFor(sceneFile)` supplies built-in kinds and three typed physics kinds only when
the scene's native project has admitted `physicsEnabled: true`. Game components and disabled physics entries remain
read-only JSON. Settings changes publish a component-editor refresh; each edit obtains the current editor again.

Project roots show `PanelState.Project` through `ProjectDetailsView`: the localized Physics checkbox and settings
problems come from `AbyssusProjectSettings`. Unsupported files disable editing. Document/settings events refresh the
checkbox without saving or reselection, and the panel supplies a text editor for platform Undo/Redo of the `.abss`.
Scene properties retain the ray settings controls. Asset and entity writes use `editSceneJson`; UI state changes and
document listeners run on the EDT. Asset previews decode in the background and publish through the panel's UI executor.
