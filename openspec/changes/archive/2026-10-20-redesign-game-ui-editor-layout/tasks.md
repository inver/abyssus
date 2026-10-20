# Tasks

## 1. Screens menu

- [x] 1.1 Replace `UiScreensPanel` with a toolbar screens button and popup (screens with entry / overlay marks, Add Screen, Add Overlay, Rename, Delete, arrow-focus switch) over the existing controller calls; verify with `./gradlew :plugin-abyssus:test --tests '*UiEditorViewsTest'` updated for switching, adding and renaming a screen.
- [x] 1.2 Add the new strings to `AbyssusBundle.properties` and remove unused ones; verify `scripts/check-docs.sh` and the bundle-usage check in `./gradlew :plugin-abyssus:test` pass.

## 2. Add widget menu and tree header

- [x] 2.1 Expose the add target from `UiEditorController` (selected container, or parent for a leaf) with a unit test in `UiEditorControllerTest` for container and leaf selections.
- [x] 2.2 Replace the palette grid with an Add popup titled by the target, and move Duplicate, Delete, Up, Down and empty-state actions into the Widgets header; verify with updated `UiEditorViewsTest` (add each kind, leaf-selected add, duplicate, delete) and an unchanged document result for the same edit sequence.

## 3. Panel arrangement and canvas

- [x] 3.1 Rework `UiEditorPanel` so the tree owns the whole left column, with splitter proportions and minimums giving about 216 / 650 / 292 px at a 1190 px editor; verify with a headless panel layout test at 1190 x 880 (all three parts visible, canvas widest).
- [x] 3.2 Add the resolution picker and fit-zoom label to the canvas toolbar and move the key hints into a strip under the canvas; verify the picker changes the preview viewport (`UiPreviewControllerTest`) and the strip text comes from the bundle.
- [x] 3.3 Confirm edit mode fits the screen into the canvas and maps pointer input at any zoom, fixing it in `UiCanvasView` if not; verify with a pick-at-reduced-zoom case in `UiCanvasViewGlTest` (run with `-Dabyssus.glTests=true`).

## 4. Docs and integration

- [x] 4.1 Update `projects/plugin-abyssus/src/main/kotlin/net/nevinsky/abyssus/plugin/gameui/README.md` (file table, panel description) and reconcile `openspec/changes/add-game-ui-authoring` if its delta describes the old left column; verify `scripts/check-docs.sh`.
- [x] 4.2 Run `./gradlew :plugin-abyssus:test` and `openspec validate redesign-game-ui-editor-layout`, then check by hand in `./gradlew :plugin-abyssus:runIde` on a copy of the Control Line project, on a 14" display with the Abyssus tree open: popups, drag, Preview, dark and light themes.
