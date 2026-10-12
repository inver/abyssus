# Blueprint verification — Classic_F2B.blend

**Verdict: partial dimensional match; not a strict blueprint reconstruction.**

This audit reopened the actual saved `Classic_F2B.blend` in Blender and measured world-space mesh vertices and curve points. The model was not modified. The earlier closed-mesh checks established topology, not fidelity to the blueprint.

Reference: http://discovery-aeromodels.com/images/classic/yatsenko-control-line-classic-f2b-aeromodel-15.jpg

## Dimensions that match

All measurements below are millimetres. Differences below 0.01 mm are floating-point/sampling effects, not a manufacturing accuracy claim.

| Feature | Blueprint | Saved model | Assessment |
|---|---:|---:|---|
| Wing span, excluding leadout guides | 1450 | 1450 | Match |
| Long wing semispan | 740 | 740 | Match |
| Other semispan, by subtraction | 710 | 710 | Match |
| Wing root chord | 340 | 340 | Match |
| Root wing maximum thickness | 62 | 62.006 | Match |
| Wing trailing edge to stabilizer leading edge | 305 | 305 | Match |
| Stabilizer span | 690 | 690 | Match |
| Stabilizer root chord | 76 + 85 = 161 | 161 | Match |
| Stabilizer root hinge position from leading edge | 76 | 75.992 | Match |
| Canopy length | 240 | 240 | Match |
| Canopy rise above its own base | 65 | 65 | Match |
| Maximum fuselage width | 75 | 75 | Match |
| Propeller diameter | 340 | 340 | Match |
| Main wheel centre spacing | 370 | 370 | Match |

## Confirmed discrepancies

1. **Flap span is too long.** Blueprint dimensions are 600 mm and 570 mm from the centreline to flap end. Model hinge curves end at 666 mm and 639 mm, respectively: +66 mm and +69 mm. Those are decorative hinge curves; there are no separate movable flap meshes.

2. **Flap hinge has the wrong sweep.** The blueprint draws the flap hinge at a constant longitudinal station. The saved hinge starts at x=514 mm and ends at x=484.3 mm: a 29.7 mm forward sweep. The model keeps a constant 76 mm flap chord as the wing tapers; the blueprint instead narrows the flap toward the tip. Root flap chord alone matches.

3. **Aft fuselage is too shallow.** The registered side overlay shows the model upper and lower contours converging toward the tail much more sharply than the drawing. Saved fuselage depth is 44 mm at x=895 mm and 32 mm at x=1000 mm. The drawing's aft-body depth annotation is 95 mm, and the drawn body remains substantially deeper. The raster does not establish an exact common cross-section for a numerical 95-minus-model error, but the silhouette mismatch is unambiguous. The fin top approximately aligns; shrinking the body makes too much of the fin exposed.

4. **Engine yaw is absent.** The plan indicates 3° in top view. The model's crankcase/propeller shaft is aligned with the fuselage longitudinal axis, with no 3° yaw. The cylinder is horizontal, as requested, but that does not implement the thrust-line offset.

5. **Elevator hinge is not straight.** It moves from x=970.992 mm at the root to x=975.023 mm near the tip, a 4.03 mm drift; the drawing shows a straight hinge. The root 76/85 mm split matches, but the outer split is not traced to the indicated 50/65 mm geometry.

## Approximate or unverified features

- **Tip geometry:** the saved wing's final cap section is 239 mm chord and 22.57 mm thick. Just inboard, at y=693.75 mm, it is 279.06 mm chord and 50.89 mm thick. The blueprint labels 275 mm outer chord and 52 mm outer thickness, but those should not automatically be compared with the extreme rounded cap. The model uses an arbitrary last-6%-span rounding and thinning rule. Its outer profile needs tracing and explicit measurement stations before claiming agreement.
- **Stabilizer tip:** the final cap is 79 mm chord, versus 117.88 mm just inboard at y=323.44 mm. The drawing's outer 50+65=115 mm chord needs a properly registered station. The same aggressive generic rounding rule was used for both wing and tail.
- **Airfoil ordinates:** the model uses an analytic symmetric profile, with matching root thickness. The blueprint includes a section sketch, but no original coordinate table. Matching thickness does not establish the correct section shape, leading-edge radius or trailing-edge geometry.
- **Canopy shape and position:** length and rise match; curvature is a generic symmetric dome. Its rear slope differs from the drawing. Absolute placement is estimated rather than constrained to a dimensioned station.
- **Fuselage and fin contours:** forebody and fin are recognizable, but the fuselage cross-sections and fillets were not traced from section drawings. The lower fin/fuselage junction needs revision with the deeper aft body.
- **Propeller pitch:** 140 mm was used in the generator's twist formula, but this audit does not certify a manufactured 340×140 propeller or aerodynamic performance.
- **Engine, gear retention and demountable joints:** simplified visual geometry, not fully dimensioned mechanical assemblies. No functional Petrov ball-lock internals are established by this drawing.
- **Weight:** 1600 ±20 g is metadata. The mesh has no verified material densities, hollow structure or mass distribution.

## Overlay interpretation

`blueprint-overlay.png` / `.svg`: orange outlines are extracted from the saved model, black lines are the reference. Registration uses wing-root and stabilizer leading-edge stations longitudinally, and the 740 mm wing semispan laterally. Opposite half-wings/tail are omitted because the source uses broken views. The side height is calibrated using the 240 mm canopy annotation as a local raster scale.

The source drawing is not uniformly scaled: different dimension spans imply different pixels-per-millimetre values. Therefore the overlay is a qualitative contour check, not a tolerance map. Written dimensions take precedence over traced pixels. The overlay omits engine, propeller, landing gear and decorative details so that the airframe contours can be compared clearly.

## Recommended correction order

1. Rebuild the aft fuselage and fin/body junction from the side outline and depth annotations.
2. Make flap hinges straight, limit flap spans to 600/570 mm, and reproduce tapered flap chords.
3. Straighten the elevator hinge and reproduce the 76/85 root and 50/65 outer splits.
4. Register and trace wing/tail tip contours and section thicknesses; replace generic tip thinning.
5. Apply the indicated 3° engine thrust-line yaw and revisit canopy curvature/placement.

Evidence: `blueprint-measurements.json`; reproducible read-only extraction script: `audit_blueprint.py`.
