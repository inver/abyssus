# ecs

The plugin keeps scene-editing adapters here. `scene/ComponentEditor.kt` adds, updates and removes modeled
components in JSON using runtime codecs and defaults, with reference checks. `scene/LightEntities.kt` creates lights.
Callers write through `SceneComponentEdits` and `editSceneJson`, preserving formatting and Undo.

Ashley components, systems, loader, writer and codecs live in `runtime/src/main/kotlin/net/nevinsky/abyssus/runtime/ecs/`.
See `runtime/README.md`. The scene view decodes JSON through the codecs; it does not run the Ashley engine.

## Creating lights

`scene/LightEntities.kt` adds a plugin-defined entity with Name, Type, Position and Light components to a JSON tree.
`LightPreset` supplies Directional (white, intensity 1, -45 degrees X), Sun (warm, intensity 1.2, -30 degrees X),
and Spot (white, intensity 1, -90 degrees X, 5 units above placement). Sun uses `LIGHT_DIRECTIONAL`.
The new id is one above the highest numeric entity id, or zero in an empty scene. A matching four-component
archetype is reused or appended; no Java-class identifier table is created. Existing entities and native data are
preserved. The caller writes through `SceneComponentEdits.addLight` and `editSceneJson` as one undoable edit.

Component codecs are native: `RenderComponent.renderable.kind` is `asset` for asset references. Unknown kinds remain
raw; an edit to another component keeps them. Light availability validates the current native scene header and ECS
payload before mutation. Document edits also validate the resulting tree, keeping native markers and unrelated text.
