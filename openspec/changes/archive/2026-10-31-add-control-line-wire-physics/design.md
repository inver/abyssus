## Context

See proposal.md - Why. Today `LineRig` adds two Jolt distance ropes of `lineLength` (rigid, massless); `FlightSession.lines()`
returns their anchors and `ControlLineGame` draws them as 1 px GL lines. `Aero.lineDrag` already uses `lineDiameter`
(0.4 mm). `PhysicsConstraint` has no setter for its maximum distance, and `lib-physics` is out of scope.

## Goals / Non-Goals

**Goals:** realistic wire mass, stretch, shape and look at 0.5 mm, with deterministic cost per frame.
**Non-Goals:** simulating the wire as bodies; wire whip or vibration; wire collisions with the ground or pilot.

## Decisions

1. **Wire constants in one `Wire` value class** (density, E, area from diameter, mass per metre, stiffness `EA/L`), pure
   functions, unit tested. Alternative: constants in `Aero`; rejected, since shape and rendering also need them.
2. **Mass:** the wire's inertia is applied as a force in `Flight`, so every host (game, Play) gets it with no scene
   change: `-(m * m_w / (m + m_w)) * a`, with `m_w = 2 * mu * L / 3` (one third per line: a uniform wire swinging about
   the handle has inertia `mu L³/3`) and `a` the last step's acceleration less gravity. Alternative: add the mass to
   `RigidBodyComponent.mass`; rejected, since Play's host builds its world before game code runs.
3. **Stretch:** `LineRig` applies each line as a spring of `E*A/L` (with 10% of critical damping) at the leadout once
   it is longer than its rest length; that force is also the reported tension. The Jolt ropes stay as a backstop 2%
   longer. Tried first: re-adding the rope at `L + T/k`; rejected, because the rope's tension reads 0 whenever it is
   looser than 1 mm, which made the stretch oscillate.
4. **Shape:** per line, a parabola/catenary approximation in the plane of the line: the load per metre is
   `weight (mu g, down) + drag (0.5 rho Cd d v(s)², v(s) = V s/L, against the flight direction); the centrifugal
   load acts along the line, so it changes tension, not shape`. The sag/bow is solved as `delta(s) = q(s) s (L - s) / (2 T)` summed over the two load
   components, with `T` the line's own tension floored at a small value so a slack line drapes by its weight with the
   excess length `L_rest - chord` (clamped). Computed in `WireShape`, 16 points per line, per frame.
5. **Rendering:** the line pass in `FieldRenderer` draws each shape as a screen-space ribbon: width `max(true width,
   1 px)`, alpha `min(1, trueWidthPx / 1 px)` scaled to a floor of 0.12, plus a glint term from the sun direction. Replaces
   `LineSegment` use for the lines only; guide and debrief segments keep `LineSegment`.

## Risks / Trade-offs

- Recreating a rope may disturb the solver for one step -> hysteresis of 1 mm and a test that tension stays continuous.
- The extra mass lowers climb performance -> re-run `FlightTest` and the F2B bot tests; retune thrust in the scene if needed.
- Tension estimate noise feeds the shape -> low-pass filter the tension used for the shape only.
- 0.5 mm wire is nearly invisible at distance -> the opacity floor (0.12) keeps it readable; revisit after a visual check.

## Open Questions

- Exact opacity floor and glint strength (tune in the running game).
