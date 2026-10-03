# Proposal

## Why

The Abyssus view lists every entity of a scene with its components, and the scene view can move and rotate
an entity, but nothing else about a component can be changed without hand-editing the `.scene` JSON.
Adding a light to an entity, changing a camera's field of view, retargeting a look-at or removing a stale
component all mean opening the text editor and keeping Mundus' component shapes straight by memory.

## What Changes

- Let users **create** a component on an entity (choosing from the components the plugin models: Name, Type,
  Parent, Position, Camera, Light, Point2Point, Render), **read** it in the Abyssus Properties panel, **update**
  its fields and **delete** it, writing the result into the `.scene` file as one undoable edit.
- The Abyssus Properties panel, which today only describes assets, shows the selected **entity** and
  **component** and edits their values there. Fields are typed (number, text, enum, color, entity reference,
  asset reference); invalid input is rejected without touching the file.
- The Abyssus tree gets **Add Component...** on an entity and **Remove Component** on a component, in the
  context menu. Both end in the same writer as the panel.
- New components start from Mundus' defaults (the ones the loader already uses); a render component asks for
  the asset it points at.
- Components the plugin does not model (`PickableComponent`, `DependenciesComponent`, ...) stay as they are:
  they are listed, never edited or offered in the add menu, and a component cannot be added twice to one entity.
- Edits keep everything else in the file as it was: other components, key order, `archetype` ids, the block's
  `archetypes`, `componentIdentifiers` and `metadata`, and the file's formatting style.
- Out of scope: creating or deleting entities, editing unmodeled components, editing `archetypes`.

## Capabilities

### New Capabilities
- `scene-component-editing`: adding, updating and removing the modeled components of an entity in a scene file,
  with validation, reference integrity, defaults and a single undoable edit per change.

### Modified Capabilities
- `object-properties-panel`: the panel also describes a selected entity or component and edits it; the
  asset-only wording of "selection is not an asset" and the "read-only first stage" requirement change.
- `abyssus-project-view`: entity and component rows get the Add Component and Remove Component actions.

## Impact

- Code: new `ecs/scene/ComponentEditor.kt` (JSON-level create/update/delete on the scene tree, built on the
  existing `ComponentCodecs`), `properties/` (entity and component states and an editable table in
  `AssetPropertiesPanel.kt` / `PanelState.kt`), `projectView/` (actions on `DtoEntryNode`, `plugin.xml`
  registration), commands and field labels in `AbyssusBundle.properties`.
- Reuses `editSceneJson` for the undoable write, so the Scene view re-reads the file through its existing
  document listener and shows changes without extra wiring.
- No new dependencies and no change to the scene file format.
- Tests: editor unit tests over `src/test/testData/project/Untitled/scenes/Main Scene.scene` (round trip of
  unmodeled data, defaults, reference checks) and panel/action tests next to the existing ones.
