## Why

Control Line's lines are massless, perfectly straight, inextensible ropes drawn as 1-pixel GL lines. Real control lines
are 0.5 mm steel wire: they weigh, stretch, bow back in the airflow and drape when slack. The flight should feel and
look like that, and slack-line moments (the crash rule, takeoff, wingovers) should be visibly readable.

## What Changes

- The plane's line diameter becomes 0.5 mm steel wire (7850 kg/m³, E = 200 GPa). `PlaneComponent.lineDiameter` default
  and the bundled `Field.scene` planes use 0.0005 m.
- The wire loads the plane: an equivalent tip mass per line is added to the plane's mass (a flight starts with the plane
  heavier by the wire's inertia), so tension and flight speed reflect it.
- The wire stretches: the line length under load is its rest length plus `T * L / (E * A)`.
- The line's shape is computed every frame from its tension, weight and aerodynamic drag: nearly
  straight when taut, bowed back by drag, draped when tension is low.
- The lines are drawn at true 0.5 mm as a ribbon whose opacity follows the true width in pixels, with a sun glint, in
  place of the 1 px GL line.
- Stays in `app-game-control-line`; `lib-physics` and the editor are unchanged. Jolt keeps one rope per line.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `control-line-flight`: adds requirements for the wire's mass, stretch, shape and drawn thickness.

## Impact

- Code: `flight/LineRig.kt`, `flight/Aero.kt`, `flight/FlightSession.kt`, a new wire-shape class in `flight/`,
  `render/FieldRenderer.kt` (line pass), `ControlLineGame.kt`, `components/PlaneComponent.kt`.
- Data: `project/ControlLine/scenes/Field.scene` (diameter), `abyssus/components.schema.json` default.
- Flight balance: planes get slightly heavier and lines slightly longer under load; the 30% tension tolerance and
  existing flight tests are re-checked.
- Docs: `projects/app-game-control-line/README.md`.
