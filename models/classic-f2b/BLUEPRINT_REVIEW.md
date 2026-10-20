# Blueprint and local photograph comparison

The saved model is rebuilt from `build_classic.py`. The supplied `plan-reference.jpg` sets explicit dimensions; `textures/classic-photo.jpg`, `classic-opposite.jpg` and `cockpit-reference.jpg` inform the visible contours and cockpit. The photographs are unchanged and all three are packed in the Blender file.

## This revision

- **Canopy:** the previous crest was 53% aft of the nose. The drawing places it around 42%; the new profile follows that position. Its sill now dips below the deck as shown in the side view, while its dimensioned length remains 240 mm and rise above the deck remains 65 mm. The forward frame is placed closer to the crest, consistent with the cockpit photograph. Shoulder fairings connect the glazing rim to the fuselage instead of leaving a gap.
- **Fuselage:** shape-preserving cubic interpolation replaces linear transitions between cross sections. The 75 mm width continues to the wing leading-edge station. The two checked aft stations retain 95 mm depth.
- **Fin:** the swept leading edge flows into a rounded crown, replacing the angular, flat-topped outline. Both overall photographs support this shape. The crown remains 125 mm above the 48 mm aft deck.
- **Wing tips:** smooth interpolation replaces angular transitions between the existing traced outline landmarks. Root and outer section dimensions, semispans and straight flap hinges are retained.
- **Cockpit:** the fuselage now has an actual recessed well instead of a solid deck behind the glazing. The close-up informed the thin reclined seat back and its eight lightening holes. Instruments and control stick remain simplified.
- **Reference retention:** the opposite-side and cockpit photographs are retained in the saved file even though they are not used as surface textures.

## Verification and review artifacts

`verify_blueprint_corrections.py` checks the saved file's world-space dimensions, principal mesh closure, canopy position, well opening, seat clearance beneath the glazing and packed references. Its 56 results are written to `blueprint-correction-checks.json`. Checks labeled “traced estimate” are reconstruction constraints, not dimensions printed on the drawing. `verify_blend.py` independently checks that the saved principal airframe meshes have positive volume and no nonmanifold edges.

`blueprint-overlay.png` and `.svg` show actual mesh outlines over the supplied drawing. `orthographic-side.png` and `orthographic-top.png` show the complete assembly. `photo-comparison.jpg` places each unmodified source photograph beside a fresh render from an approximately corresponding direction. The underlying renders are `comparison-primary.png`, `comparison-opposite.png` and `comparison-cockpit.png`.

## Limits

The raster drawing has broken views and inconsistent local scale. Written dimensions take priority over an exact pixel overlay; neither a numeric image-matching tolerance nor exact equality to every drawn contour is claimed. Photo camera poses and focal lengths are unknown. Fin curvature, canopy sill depth, cockpit details and fuselage sections remain estimates.

The original projected livery is preserved, including photographed sky reflections and some perspective distortion. The comparison images therefore expose remaining texture differences as well as geometry. Exact original airfoil ordinates, internal construction, engine dimensions and mechanical joints are unavailable. The control surfaces remain visual seams.
