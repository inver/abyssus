# Design

## Context

`UiEditorPanel` nests a `JBSplitter` (left column | canvas) inside another (that | side tabs). The left column stacks
`UiScreensPanel`, `UiWidgetTreePanel` and `UiPalettePanel`. All parts read and change one `UiEditorController`, which is
Swing-free, so layout changes do not touch editing logic. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:** fit the editor into about 1190 px; every existing action stays reachable; native IntelliJ look.

**Non-Goals:** the document model, controller edits, Declarations and Preview panel contents, new widgets,
custom-painted badges or segmented control (see decision 4).

## Decisions

1. **Screens become a toolbar drop-down button with a popup.** `UiScreensPanel` is replaced by a small view over the
   same controller calls (`selectScreen`, `addScreen`, `renameScreen`, `deleteScreen`, `setNavigation`). Alternative: a
   collapsible section, rejected because it still costs a header row and a click per screen switch; a combo-like button
   matches how IDE tools expose scope.
2. **Palette becomes an Add popup** (`JBPopupFactory` list popup) fed by `UiWidgetType.entries`, with the target
   container in its title. Container rule reuses what `controller.addWidget` already decides; the title only mirrors it,
   so a helper that exposes the target id from the controller is added rather than duplicating the rule in the view.
3. **Tree header actions** use `ActionToolbar`-style icon buttons (Add, Duplicate, Delete, Up, Down, and empty-state
   add/remove in an overflow). Existing button `name`s used by tests are kept where the button survives.
4. **Native components and theme.** Edit / Preview stays the existing toggle pair; no rounded segmented control,
   accent-filled Add button or badge painting. The mockup is a layout reference, not a skin.
5. **Side panel widths** are splitter proportions with sensible minimums, not fixed pixels, so the user can still
   resize.
6. **Canvas sizing.** The preview already fits its offscreen picture into the panel (`UiCanvasView` preview scale and
   offsets). Edit mode is checked and given the same fit and pointer mapping if it does not already have it; the toolbar
   shows the resulting zoom.

## Risks / Trade-offs

- [Actions become one click deeper] -> Add and Duplicate/Delete stay in the header; keyboard shortcuts (Delete,
  Cmd/Ctrl+D) are unchanged.
- [Tests find components by name or type] -> keep names where components survive, update `UiEditorViewsTest` in the same
  group.
- [Popup focus with a heavyweight GL canvas] -> the canvas already avoids taking focus (see the note in `UiCanvasView`);
  popups are shown relative to Swing components, and checked by hand in `runIde`.
- [Conflict with add-game-ui-authoring text about the left column] -> a task amends that delta if it describes the old
  arrangement.
