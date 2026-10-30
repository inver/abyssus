# Design

## Context

`add-game-ui-authoring` gave Abyssus a UI asset (screens of widgets, typed number / boolean / string bindings, `UiPresenter` with Scene2D, editor and preview). Control Line still builds every screen in `GameUi` (528 lines of Scene2D with libGDX's sample `uiskin`): static menus, a flying HUD, a results summary, a name entry, plus four dynamic screens: the free-flight score table, the F2B boards (a selectable, clickable row each), the pattern sheet on the game-over screen and the debrief rows with a deductions panel. `GameFlow` already owns the state machine (`Screen` sealed type) and the keys are routed by `ControlLineGame` / `GameUi.keyDown`. The standalone `uidemo` host exists only to prove the runtime.

Constraints: `lib-core` stays a plain JVM library without singletons or companion objects; GL only in a guarded context; document edits stay lossless; native documents keep `formatVersion` 1 and additive UI fields keep `schemaVersion` 1 (unknown fields were already preserved, so a pre-list build reads a list as an unknown widget placeholder rather than failing). The F2B changes in flight still edit `GameUi`.

## Goals / Non-Goals

**Goals:** make every Control Line screen expressible as authored UI; keep game rules, flow and rendering untouched; keep the host contract small (snapshot in, actions and key events out); delete the demo.

**Non-Goals:** restyling, new screens, TrueType, animation, gamepad focus, drag-reorder, a general expression language for formatted text beyond what bindings already do.

## Decisions

### Lists are a widget plus a `list` binding, expanded before layout

A `list` widget is a column-like container with `template` (one widget) and optional `empty` (one widget), a read-only binding `items`, and `maxRows`. A binding of type `list` declares `fields` (name, type) and a `sample` array of records. Template widgets bind with `item.<field>`; their ids must be unique in the document, and expansion gives each row's copy a runtime id `<id>#<row>`, used only for layout, focus and hit testing and never written back. Expansion happens in the presenter on each update that changes the list (not in the model), so the decoder, editor and diagnostics keep working on the authored tree, and the layout engine stays pure and unchanged: it lays out the expanded tree.

Alternative: dedicated table widget with columns. Rejected: the debrief and sheet rows mix text, colour and clickable cells, so a freely authored row template is the smaller general mechanism, and a table is a template with a row of labels.

Host values: `UiValue.Rows(items: List<Map<String, UiValue>>)`. A missing or mistyped list shows the sample with a diagnostic; a bad field shows its zero value. Limits: the 10,000-widget and depth limits count expanded rows; rows beyond the limit are dropped with a diagnostic.

### Row state is expressed by a bound style, not by new state kinds

The game highlights a saved rank, colours failed figures red and the debrief selection cyan. A new bindable `style` property (string, from a binding or an item field) selects a named style per widget. The resolver already layers named style, overrides and states; the bound name just replaces the authored `style`. This keeps colour in the asset (editable) and data in the game.

Alternative: bind `color` directly. Rejected: it puts presentation in game code, which is what the migration removes.

### Arrow-key menus are a per-screen focus scheme, host keys stay with the host

`navigation: "arrows"` on a screen maps Up/Down to the existing focus traversal order (visible, enabled, tree order, wrapping), Enter to activate and focuses the first enabled control on open. Keys that carry game meaning (Left/Right for plane and tab, Space, R, V, 1/2/3 in the debrief, Escape that triggers `flow.back`) are handled by the host before the presenter, as the presenter's contract already allows. Escape in authored navigation is not used by the game: it calls `flow.back()`.

### The game drives screens through `display`, with a host class that owns the bindings

`GameUiHost` replaces `GameUi`: each frame it publishes a snapshot derived from `GameFlow`, the `FlightSession` and the judge, calls `display` when `flow.screen` changes (and shows the HUD plus the pause overlay for `Paused`), and maps button actions (`emitAction` names such as `freeFlight`, `choosePlane`, `retry`) to `GameFlow` calls. One `ControlLine` UI asset holds all screens; the host and the asset share a documented list of binding names and action names, checked by a test that decodes the asset and verifies every binding the host publishes is declared and every declared binding is published.

`UiPresenter.publish` becomes cheap when nothing changed (compare snapshots, invalidate layout only on a difference), because the HUD updates every frame and lists are expanded per change.

### Screen scaling changes from pixels to a fitted reference

`GameUi` used a pixel `ScreenViewport`. The presenter fits each screen's reference size (1280 by 720) uniformly into the window. On the default window this is identical; on other aspect ratios the UI is centred in a letterboxed rectangle over the 3D scene instead of hugging window corners. Accepted as the cost of one layout model for editor and game.

### Clickable rows and select-on-focus (found while migrating)

The F2B boards open a debrief on a click anywhere in a row, and the name entry starts with its text selected. Both were behaviour of the old screens that the first draft of the UI model could not state. A click action is allowed on panels, rows and columns (hit testing already walks children first, so the nearest interactive ancestor wins); inside a list the expansion appends `#<row>` to emitted action names, which keeps `UiActionSink.emit(name)` unchanged for every existing host. `properties.selectOnFocus` on a text field makes the first edit after focus replace the text. Alternative for rows: one button per row with a formatted line; rejected because it changes what the screen shows.

### Fonts and look

The game keeps libGDX's default bitmap font through the bundled lease (`fontSize` scales it), and the bundled theme replaces `uiskin`. Colours that the old screens used (highlight yellow, salmon, cyan, light grey, ideal / flown track colours) become named styles in the asset. A pixel-exact match is not a goal; information, order and controls are.

### Deleting the demo

The demo's `UiDemoHost` write-validation tests (normalize, reject, action delivery) are covered by `lib-core` binding tests and by `GameUiHost` tests, and its GL test by the Control Line screen GL tests, so removal loses no coverage. The `add-game-ui-authoring` artifacts that mention the demo get a note pointing here instead of being rewritten.

## Risks / Trade-offs

- F2B changes in flight still edit `GameUi` -> finish or archive them first, or port their edits into the asset; the first task is to confirm the base is quiet.
- Per-frame relayout for the HUD -> snapshot comparison and expansion caching; a GL test with a 10-row board checks the frame budget qualitatively, not by timing.
- Behaviour drift in 528 lines of screens -> one host test per screen asserts the published bindings against the strings the old screens built (copied into the tests before `GameUi` is deleted), plus GL tests per screen.
- Bundled theme differs from `uiskin` -> accepted (see above); the asset is editable.
- Larger editor surface (list template, record declaration, sample rows) -> reuse the existing pure-edit / panel / form split; list rows in the canvas use the sample list.
- Binding names become a contract between the asset and the game -> the coverage test above.
