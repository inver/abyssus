# Proposal

## Why

Control Line offers Free flight and an F2B pattern, both flown with invented planes. Players can now bring real
aircraft into the project (the Mig-3 model was just imported) but have no way to fly them as what they are: scale
replicas of a prototype. Plane select also parks the planes on bare grass, which shows them poorly and ignores the
tables the field already has.

## What Changes

- A **Scale** entry on the main menu, beside Free flight and F2B pattern, that opens plane select among the planes of a
  new class `SCALE`.
- A game component, `ScaleComponent`, naming the prototype a replica copies: prototype name, first-flight year, real
  wingspan and the model scale. It is scene data, authored on the plane entity in Abyssus like every other plane number.
- A **vitrine** remake of plane select: every plane stands on a table of its own, the camera glides from table to
  table, and a card shows the plane's numbers and, for a replica, its prototype's facts.
- A **scale flight judge**: a Scale flight flies with the Free flight physics and is scored on realism in the air
  (steady lap speed, level laps, a clean landing), not on laps and manoeuvres.
- A Scale score table of its own, `~/.abyssus-control-line/scale-scores.json`.
- A game component, `VitrineTableComponent`, marking a table entity of the display row and giving its table-top height;
  plane select stands the planes on these tables in order.
- The bundled `ControlLine` project gains the Mig-3 as its first `SCALE` plane, with flight numbers tuned by hand, and a
  display row of tables for plane select.

Native file fields: `Field.scene` gains `ScaleComponent` entries, `VitrineTableComponent` entries on the display tables and the Mig-3 entity; a plane entity's
`PlaneComponent.planeClass` may now be `SCALE`; `abyssus/components.schema.json` lists `ScaleComponent` and `VitrineTableComponent`. Documents keep
`format: "abyssus"` and `formatVersion: 1`; no format version changes. The editor reads these as game components
(read-only), as for `PlaneComponent`.

Out of scope: static judging or fidelity marks (the vitrine card is information only), a set scale routine such as
takeoff, wingover and landing in order, a Scale debrief of the flown path, AI demonstration (Watch AI) for Scale, and any
change to the F2B pattern.

## Capabilities

### New Capabilities
- `control-line-scale`: Scale mode: the replica plane class and its prototype data, the scale flight judge and its
  realism marks, and the Scale score table.

### Modified Capabilities
- `control-line-game-flow`: the main menu offers Scale; plane select becomes the vitrine (planes on tables, cards,
  gliding between tables) and filters to `SCALE` planes for Scale.

## Impact

- `projects/app-game-control-line`: `flow` (a Scale mode and its screens), `components` (`ScaleComponent`, `VitrineTableComponent`, `PlaneClass`),
  `render` (`Cameras`, `FieldScene`), `screens` (the vitrine card), a new scale judge and score store beside the
  existing ones, and a `ScaleComponent` and `VitrineTableComponent` entry in the component schema.
- `projects/app-game-control-line/project/ControlLine`: `Field.scene` (display tables, the Mig-3 plane entity, its
  `ScaleComponent`) and `abyssus/components.schema.json`. The game tests assert on this scene, so they change with it.
- No change to `lib-core`, `lib-physics`, the plugin or any file format version; the shipped-game boundary is unchanged.
