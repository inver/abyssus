# Design

## Context

The panel is `AssetPropertiesPanel` (a `CardLayout` of Swing views: `EntityDetailsView`, `SceneDetailsView`, `ProjectDetailsView`,
`WaterPropertiesView`, asset rows from `AssetRows`) over `PanelState` and `lib-core-editor`'s component and meta models.
Field editors are shared through `plugin/ui/FieldEditors.kt`. The scene format has `<x>Enabled` toggles for scene settings only;
entities and components have no flag (see `docs/ai/file-formats.md`, "Toggles" and "ecs"). See proposal.md for scope and the
mockup board "Abyssus tree and Properties" (`Panels.dc.html`) for every pixel value quoted below.

## Goals / Non-Goals

**Goals:** the panel looks and behaves as the board does for every selection kind; the flag is real (file, loader, systems,
physics, view, tree, panel); the panel model carries every fact the view needs so the view stays dumb and the facts are
unit-tested without a UI.

**Non-Goals:** read-only assets (existing editors stay), the Foliage board, light-theme pixel parity, new asset types.

## Decisions

### D1. Where `enabled` lives
An entity entry gets `enabled` beside `components`; a component entry gets `enabled` as a reserved key inside the component
object. It is written only as `false`. `EcsLoader` strips the reserved key before binding the component's fields, so
component classes never see it; `ComponentCodec` and `EcsWriter` read and write it through one `EnabledFlag` helper in
`lib-core-editor`, and `lib-core` holds the read side (`EnabledFlag.isEnabled(node)`), because a shipped game must honor it.
A registered or schema-declared component that declares a field named `enabled` is a registration problem (reported, component
stays read-only JSON), not a silent clash. *Alternatives:* an entity-level `disabledComponents` list (no clash, but a rename of
a component then leaves a stale name, and two places to read); a wrapper object around component fields (breaks every existing
file). Rejected for risk or churn.

### D2. Runtime semantics
Disabled means "absent behavior, present data". `EcsLoader` still builds the entity and keeps the data so `EcsWriter` round
trips it. Systems filter with one predicate on the entity (`EntityState.isActive(component)`), not per-system checks of the
raw flag. `lib-physics` skips body and shape creation for disabled physics components and entities. The flag is re-read on
`SceneEngine` update, so toggling in the editor takes effect without a reload.

### D3. Panel model grows, Swing stays out
`lib-core-editor` adds a `PanelModel` layer between the current states and the views: `Header` (breadcrumb, icon kind, title,
subtitle, badges), `Section` (kind, title, game flag, class hint, enabled state, collapsed key, rows) and `Row` variants
(`Text`, `Number(unit)`, `Vector(axes)`, `Choice`, `Check`, `Color`, `Asset(kind, browse)`, `Readonly`, `Link`, `Note`,
`SubHeading`, `Table`). `FieldValue` gains `isDefault` (value absent from the file). Strings come from
`AbyssusEditorBundle.properties` through `EditorMessages`. The plugin maps `Row` to Swing; rows that carry editors keep today's
`FieldEditors` commit/refuse/revert behavior.

### D4. One row/section component set in `plugin/ui`
New `PropertySection`, `FieldRow`, `HeaderPanel`, `BadgeLabel` and `DefaultDim` helpers replace the private layout code of
`AssetRows`, `EntityDetailsView` and `SceneDetailsView`. Colors and sizes come from `DesignColors` (shared with the tree
change) as `JBColor` pairs: dark values are the board's (`#2b2d30` surface, `#4e5157` border, `#868a91` secondary, `#548af7`
accent, `#e0a458` amber, `#f47fb0` game); light pairs are chosen for 4.5:1 contrast. Fonts: the IDE UI font, and the editor
monospace font for values (the board's Inter / JetBrains Mono are not bundled). Geometry (150 px label column, 24 px fields,
26 px Browse, 34 px section header, 20 px body indent) is asserted by a UI test, not forced when the platform scale differs.

### D5. Dim defaults
The panel model marks `isDefault` from the document (key absent), never from comparing the value to the default, so an
explicitly written default is drawn normally. Light defaults, rigid body defaults and the others already live in codecs
(`docs/ai/file-formats.md`, "Light defaults"); the panel reads them through those codecs, so the dim value equals what the
runtime uses.

### D6. Enabled in the UI
The tree eye and the section check box are the same action: `SetEnabledAction(target, enabled)` in `lib-core-editor`
(`HeadlessEditing` can run it) producing one `DocumentTextEditor` transform, called through `editSceneJson`. Togglable
means: not `GROUP`, not `NameComponent` / `ParentComponent` / `PositionComponent` (the board shows no toggle on those).
Scene settings keep their `<x>Enabled` keys and use the same check box widget.

### D7. Scene view and picking
The view's model list filters on the same `isActive` predicate, so a disabled entity is not drawn, not shadowed, not picked and
shows no gizmo. Selection of it from the tree still works. Ray-tracing snapshots built by `lib-core-editor` skip disabled
entities (the CPU snapshot is built from the same filtered list).

### D8. Per-selection layouts are model builders
One pure builder per selection kind (`projectPanel`, `folderPanel`, `scenePanel`, `settingPanel`, `ecsPanel`, `entityPanel`,
`componentPanel`, `uiScreenPanel`, `assetPanel(type)`), each unit-tested against the board's content (section titles, row
labels, order, footer). Asset builders reuse the typed asset editors; table rows are built from the same readers that feed
the foliage and cloud sections, read-only.

### D9. Order of work and overlap
Format, loader, systems, physics and the edit action land first (tasks 1-3) so the UI never shows a switch that does nothing.
The panel model and views follow (4-6), then the tree eyes (7), UI tests and baselines (8), docs (9). Icons, `DesignColors`
and the `game` decision come from `redesign-abyssus-tree-view`; if it has not merged, this change takes them from its branch
and the second to merge resolves duplicates in `icons/` and `DesignColors`. `Browse` already exists there (task 4.1); this
change re-skins it as the asset row's button rather than adding a second one.

## Risks / Trade-offs

- [A game component with an `enabled` field clashes with the reserved key] -> D1 reports it at registration; Control Line's
  components are checked in a task.
- [Systems that cache entities miss a toggle] -> D2 re-reads the flag each update; a system test toggles while running.
- [The flag changes game behavior, not just the editor] -> documented as a format addition in `file-formats.md`; the key is
  optional, so existing scenes and the shipped game classpath are unaffected until the key is used.
- [Swing cannot match the board exactly (fonts, rounded fields)] -> parity means structure, order, geometry and colors, checked
  by UI tests and screenshots; font and anti-aliasing differences are accepted and listed in the PR.
- [Large change surface across five modules] -> the task order keeps each module green on its own; the panel model has no UI
  dependency, so most of the panel work is headless-tested.
- [Dim defaults could hide a real written value equal to the default] -> D5 keys off presence, not equality.

## Migration Plan

No file migration. Existing scenes have no flag and behave as before. Rollback is a revert; a scene that used `enabled: false`
would then load with everything enabled (the old loader ignores or rejects the key per the old binding rules, so the task
list includes a check that the old behavior on an unknown key is "unsupported", not silent corruption).

## Open Questions

- Whether `PositionComponent` should be togglable (the board has no toggle on it); kept off here.
- Whether Options in the title bar needs entries beyond the platform's (none are specified by the board).
