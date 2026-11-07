# Design

## Context

Lights are entities (`LIGHT_DIRECTIONAL`, `LIGHT_POINT`, `LIGHT_SPOT`) with `NameComponent`, `PositionComponent` and
`LightComponent`; the view and game already render point lights (`scene-entity-lights`) and `LightEntities.add` already builds a
light entity inside one `SceneDto` edit, but only for the three Add Light presets. `ModelMeshLister` (lib-core-editor)
already reads model geometry without GL by `ModelLoader.prepare(...).staged.data` (`ModelData`: meshes, node parts with
material ids, node transforms) and keeps the answer per asset. The sample data confirms the shape: the warehouse has an
`Emissive` material on its own mesh; the aircraft hangar has one mesh and one material, so it is not served. See proposal.md.

## Goals / Non-Goals

**Goals:**
- Bake plain `LIGHT_POINT` entities from the chosen material's geometry; no change to renderer, runtime, game or format.
- Keep all geometry and parameter logic in lib-core-editor, testable headlessly with a synthetic `ModelData`.

**Non-Goals:**
- Live/derived lights, re-running with replace, spot cones, emissive textures, mesh splitting beyond connectivity.

## Decisions

1. **Bake, not a live link.** New entities are normal data, so the editor, the game and ray tracing need no change and each light can be tuned or
   deleted. Alternative: a `LightSource` component resolved at load, rejected for new loader and runtime code, unclear selection and
   editing, and the shipped-game boundary.
2. **Lamp = connected piece of the chosen material.** The user picks a material (emissive-first list), the geometry of every part using that material
   is gathered, vertices are welded by position (quantised to a small tolerance), and triangles are grouped with a union-find over welded
   vertex ids. One piece gives one bounds. Alternatives: one light per mesh (wrong for the warehouse, whose lamps share one mesh),
   one per vertex or triangle (far too many).
3. **World space.** Part geometry is taken through the node chain of `ModelData` (glTF root scale and rotation included), then through
   the entity's `PositionComponent` (position, rotation, scale) to world space, with the model's mesh filter (`meshId`) honored. The
   transform maths is a plain function over matrices (libGDX math, no GL).
4. **Values.** Color = material emissive, else white. Intensity = a constant default. Range = `max(RANGE_FACTOR * largest bounds dimension, MIN_RANGE)`;
   constants live in the pure parameter class and the spec names them only as "documented". Users edit afterwards.
5. **Cap by merging.** Pieces beyond `MAX_LIGHTS` are merged by repeatedly joining the two nearest centres (bounds union) until the cap is met. Merging is
   deterministic (stable ordering by x, y, z). The result carries a `merged` flag so the UI can say so.
6. **Edit shape.** A new `ModelLights` class in package `modellights` takes a `SceneDto`, the entity id, the chosen material id and the
   geometry reader's output, and adds the entities through the existing `scene.add` used by `LightEntities` (extended with a
   point-light builder), as one `SceneEdit` returning `EditResult` (`Changed`, `Rejected` with a message from `EditorMessages`). The plugin runs it
   through `editSceneDocument`, so it is one undo step. No manual JSON: everything is DTOs and the existing printer.
7. **Threads.** Reading the model (`ModelMeshLister`/a new `ModelLampReader` sharing its loader) parses files: it runs off the EDT in a
   background task with a progress indicator; the material dialog and the write run on the EDT. No libGDX call needs
   `GdxRuntime.withContext` because only CPU `ModelData` is used (no GL, no textures decoded).
8. **Lister change.** `ModelMeshInfo` keeps its shape for existing users; materials gain an emissive color through a new
   `ModelMaterialInfo(id, emissive)` list, read from `ModelData.materials` (`ColorAttribute.Emissive`).

## Risks / Trade-offs

- [Fused geometry gives one piece] -> The dialog shows the piece count for the chosen material before writing, so the user sees "1 lamp" and cancels.
- [Lights do not follow later moves of the model] -> Documented in the action's confirmation text and the glossary; re-running adds new lights.
- [Many lights cost shading and ray tracing time] -> The cap and the renderer's existing per-kind cap (nearest the camera target) bound the cost.
- [Welding tolerance merges or splits lamps wrongly] -> The tolerance is a single constant, covered by unit tests with touching and near-touching pieces.
- [Emissive color absent in some importers] -> White fallback.
