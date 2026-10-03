# Design

## Context

See proposal.md - Why.

What exists today: `ComponentEditor` (`ecs/scene/`) edits the `ecs/entities/<id>/components` of an entity that is already
there and has a `LightComponent` kind whose `LightCodec` reads and writes `color` and `intensity`, nested under `light`
(Mundus' shape) or directly. `SceneContent.lightOf` already turns an entity with a `LIGHT_*` `TypeComponent` into a
`LightPlacement` (kind, color, intensity, position, direction along the rotated -Z, `range`, default 100), which feeds
`LightSet` (2 directional and 5 point lights; a spot is drawn as a point light), `SceneMarkers` and `SunDirection`. So
**rendering, markers, picking, the properties panel and the procedural sky already handle a light entity**; the missing
piece is creating one.

Creation has one wrinkle the component edits do not: Mundus keeps per-scene bookkeeping beside the entities. Each entity has an
`archetype` id; `ecs.archetypes` maps an id to the component names of that archetype; `ecs.componentIdentifiers` maps a
component's class name to its short name. In the `Untitled` fixture the archetypes are `1` (a model: Position, Render, Name,
Type, Parent, Pickable), `2` (a camera) and `3` (a point-to-point), and `componentIdentifiers` has no `LightComponent`
because the scene has no light. Component edits never touch these (the entity's archetype is left as it was); a new entity
must give itself a valid one.

## Goals / Non-Goals

**Goals:**

- Entity creation as a pure function over the scene JSON tree, testable without Swing, GL or the platform, like
  `ComponentEditor`.
- Presets as data, so the three kinds are rows of one table and a fourth kind later is a row, not code.
- A plugin-defined entity shape that reloads in the plugin, with valid ECS bookkeeping (Decision 3).

**Non-Goals:**

- A general "create entity" feature. The function takes a preset of a light; other kinds need their own design.
- Reproducing the editor-only components of a Mundus-made light (`PickableComponent`, an icon `RenderComponent`).
- Changing how lights render, are picked or are drawn as markers. Spotlight cone controls and shadows belong to
  `add-scene-shadows`; this change retains entity creation and range editing.

## Decisions

### 1. A `LightPreset` table and a pure `LightEntities.add`

A `LightPreset` holds a kind label, the `TypeComponent` type, color, intensity, a rotation quaternion and an offset from the
placement point. `LightEntities.add(root, preset, position)` (in `ecs/scene/`, plain Kotlin over the Jackson tree, no
platform imports) returns `EditResult` and the new id, using the existing `LightCodec`, `PositionCodec`, `TypeCodec` and
`NameCodec` to write the components so their number text and key shapes match what the component editor writes.

| Preset | Type | Color | Intensity | Rotation | Offset |
|---|---|---|---|---|---|
| Directional | `LIGHT_DIRECTIONAL` | `1,1,1,1` | `1` | -45 degrees about X (down and forward) | none |
| Sun | `LIGHT_DIRECTIONAL` | `1,0.96,0.84,1` | `1.2` | -30 degrees about X (low) | none |
| Spot | `LIGHT_SPOT` | `1,1,1,1` | `1` | -90 degrees about X (straight down) | `+5` on Y |

The directions follow `SceneContent`: a light shines along its rotated -Z. A test pins each preset's resulting direction
through `SceneContent`, so the table and the reader cannot drift.

**Alternative rejected:** Sun as its own `TypeComponent` value or a `SunComponent`. Rejected: it changes the file format,
which `openspec/config.yaml` forbids, and Mundus would not load it.

### 2. New id and name

The id is `max(numeric ids) + 1`, or `0` for an empty scene, and non-numeric keys are ignored when taking the maximum.
This is what Mundus' own entity counter yields for a scene whose highest entity was never deleted; where entities were
deleted, reusing ids above the maximum is safe because nothing refers to an id that is not in the file. The name is
`<label> <id>`, matching the fixture's `Model 0` / `Camera 4`.

### 3. Plugin-defined archetype and component identifiers

The user explicitly removed Mundus compatibility as a requirement on 2026-10-03. New lights use only
Name, Type, Position and Light components. No parent, editor icon, dependency or direction-handle entities are created.
The LightComponent uses the existing plugin codec's nested `light` shape, containing color, intensity and optional range.

`LightEntities.add` reuses an archetype with exactly this set, otherwise appends one at the next numeric id. It adds
missing component identifiers using the existing fully qualified component names, without modifying existing entries.
The LightComponent identifier is `com.mbrlabs.mundus.commons.core.ecs.component.LightComponent`.
The plugin reader and ECS loader, rather than Mundus, define validity for these created entities.

Evidence: `src/test/testData/project/Lights/scenes/Mundus Lights.scene` was saved through the local Mundus runtime.
Its roots 1 and 4 share archetype 12 (Parent, Name, Type, Position, Render, Pickable, Light, Dependencies), handles use
archetype 6, and lines use 14. Both LightComponents are empty because that runtime marks the light transient. The
fixture's README records provenance. This observed editor shape is deliberately not the new plugin creation shape.

### 4. One more caller of `editSceneJson`

`SceneComponentEdits` gets an `addLight` next to `add`, which runs `LightEntities.add` inside `editSceneJson` as one undoable
command named from the bundle. No new write path is needed: the function is a pure edit of the parsed tree, and
`editSceneJson` already keeps formatting and number text, saves, and makes the edit one undo step (the same reasons the
component edits use it). `SceneFormatListener` is not involved.

### 5. Where the actions live and where the light goes

An `AddLightGroup` builds the three choices. It is shown in the Scene view toolbar (a drop-down button in `SceneViewPanel`,
next to the existing buttons) and as a right-click item on a scene row (`plugin.xml`, `ProjectViewPopupMenu`, like
`Abyssus.AddComponent`, its `update` showing it only for a scene node). The panel passes the orbit target (`OrbitCamera`'s
target, a plain value read on the AWT thread); the tree passes the origin. The new entity is then selected through the
selection service after the file edit lands, then selects its tree row as the refreshed tree becomes available.
SceneFileEditor forwards that selection to the view; the properties panel also observes it. The view reloads from the
file as for any edit.

**Alternative rejected:** a "drop onto the surface under the cursor" placement. Rejected: it needs a click-to-place mode
the view does not have, and `add-scene-object-drop` already lets the user settle the light afterwards.

### 6. Range as a codec field

`LightData` gains `range: Float = DEFAULT_RANGE` (100, the value `SceneContent` assumes) and `LightCodec` writes `range` only
when it differs from the default and reads a missing one as the default, so existing files round trip unchanged. The
component editor gets a `range` float field with a positive-number check (`checkValue`), after which the properties panel,
which builds its editors from `ComponentEditor.kinds`, shows it with no panel change. `SceneContent.lightOf` already reads
`range`, so the view follows.

## Risks / Trade-offs

- [A Sun and a Directional light are the same thing in the file] -> stated in the proposal and spec; the difference is only
  the starting values, and the entity name keeps the label.
- [A spot initially shows as a point light] -> `add-scene-shadows` owns the transition to cone illumination and its
  editable beam fields. Creation continues to use that reader's effective defaults; range editing remains here.
- [Ids above the maximum may collide with ids of entities Mundus deleted but still references elsewhere] -> nothing in the
  scene file refers to a missing entity, and the editor tolerates it.
- [Placing at the orbit target can put a light inside a model] -> the user can move it with the gizmo or Drop.

## Open Questions

None for the entity structure; compatibility with Mundus is outside this change by explicit user decision.
