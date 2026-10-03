# Proposal

## Why

A scene's lights can be seen, lit and edited, but not created: `scene-entity-lights` renders the light entities a
scene already has, and `scene-component-editing` can add a `LightComponent` to an entity that exists, yet the plugin
has no way to make a new entity. A scene with no light entity (the `Untitled` fixture has none) can only be lit by its
ambient light and the procedural sky's default sun, and the user has to open Mundus to add a lamp. Adding the common
lights from the plugin closes that gap.

## What Changes

- An **Add Light** action offers three kinds: **Directional** (a white directional light angled down), **Sun** (the
  same kind of light created warm, brighter and angled low, as a one-click sun) and **Spot** (a spot light 5 units above
  its placement point, pointing down). It is in the Scene view toolbar (placed at the orbit target) and on a scene row
  of the Abyssus tree's right-click menu (placed at the origin).
- Choosing one **creates a new entity** in the scene file: the next free numeric id, a name such as `Sun 7`, a
  `TypeComponent`, a `PositionComponent` and a `LightComponent`, as one undoable edit, and selects it. The scene view
  then shows the light's marker, lights the scene with it and, for a directional light, points the procedural sky's sun
  along it, all through existing behavior.
- A light's **range** becomes editable in the properties panel (a field of `LightComponent`), so a spot's reach can be
  set. It is written only when it differs from the 100 the view already assumes.
- **Sun is not a file concept.** Mundus has directional, point and spot lights only, so a Sun is a
  `LIGHT_DIRECTIONAL` entity with other starting values; it cannot be told apart from a directional light afterwards.
- **Mundus fields read or written**: `ecs/entities/<id>` (new entity), `NameComponent.name`, `TypeComponent.type`
  (`LIGHT_DIRECTIONAL` or `LIGHT_SPOT`), `PositionComponent.localPosition` / `localRotation`, `LightComponent.color` /
  `intensity` / `range`. **New lights use a plugin-defined structure**: existing keys and data are preserved; compatibility with Mundus
  is not required, by explicit user decision.
- Out of scope: **point lights** in the Add Light menu (the TypeComponent kind and rendering exist; only creation is
  left out, as it was not asked for); spotlight cone controls and rendering (owned by `add-scene-shadows`, including
  its authorized scene-format extension); a light gizmo for range; creating any
  other kind of entity; deleting an entity; shadows; the editor-only `PickableComponent` / `RenderComponent` a Mundus-made
  light carries.

## Capabilities

### New Capabilities

- `scene-light-creation`: creating a directional, sun or spot light entity in a scene from the plugin: where the action
  is offered, the entity it writes, where it is placed, and its undo.

### Modified Capabilities

- `scene-component-editing`: the light component gains an editable `range` field.

## Impact

- Code: an entity-creation function beside `ComponentEditor` in `ecs/scene/` (next id, archetype, components), pure over
  the scene JSON; a `LightPreset` table (kind, name, color, intensity, rotation, offset); `LightData` and `LightCodec`
  gain `range`; an `AddLightAction` group wired into `plugin.xml`, `AbyssusProjectViewPane`'s scene-row menu and
  `SceneViewPanel`'s toolbar; a command name and menu strings in `AbyssusBundle.properties`.
- The write goes through `editSceneJson`, with the reasons given in design Decision 4 for why one more caller of it, not
  a new write path, is enough.
- Docs: `docs/ai/file-formats.md` (light fields, new entity), `docs/ai/architecture.md` if the entity-creation function is
  listed, `src/main/kotlin/net/nevinsky/abyssus/ecs/README.md` and `sceneview/README.md`, then `scripts/check-docs.sh`.
- No new dependencies. Other open changes: `add-scene-object-drop` already drops lights, so a new light can be dropped; no
  requirement of it or of `add-procedural-sky` changes.
- Tests: headless tests for the entity creation and the presets over the `Untitled` scene text, the codec's `range`, and the
  action's choices; manual `runIde` checks for the toolbar, the tree menu and the lit view.
