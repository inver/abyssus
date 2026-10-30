# Blueprint and local photograph comparison

The saved model is rebuilt from `build_classic.py`. The supplied `plan-reference.jpg` sets explicit dimensions; `textures/img_5.png` (photo 7), `img.png` (photo 8; both with their backgrounds removed) and `cockpit-reference.jpg` inform the visible contours and cockpit. The photographs are unchanged and all three are packed in the Blender file.

## Photo triangulation revision

`fit_photo_overlays.py` fits a physical perspective camera to each overall photograph using only wing, tail and spinner landmarks (about 18–20 px RMS). `photo_triangulation.py` then triangulates features seen in both photographs. The checks agree with the model: spinner centre within 5 mm, canopy nose (210, 0, 53) measured at (212, 3, 54). Height is the least certain coordinate (about ±15 mm), because both cameras look down from the front.

- **Main gear:** the photographed aircraft differs from the plan. The spat centres triangulate to x≈245 mm and |y|≈216–234 mm, and the legs leave the wing just behind its leading edge (x≈255–265 mm). The model now uses a 450 mm track, with leg exits at (260, ±168) and axles at x=240. The plan's 370 mm track and its 65 mm aft leg position describe the drawn build, not the photographed one.
- **Spats:** blunt-nosed teardrops 160 mm long (nose x=170), widest at 35% of their length, with a tapered, slightly raised tail. The plan side view and both photos show this shape; the previous model used symmetric ellipsoids. Outlines now fall on the photographed spats in all four views.
- **Fin:** the photographed fin has a straight, more strongly swept leading edge. Its crown front triangulates near x≈992 mm, its tight aft top corner near x≈1064 mm, and its trailing edge is near-vertical. The plan's broader, forward-peaked "79" fin differs. The 125 mm height above the aft deck is kept.

### Second-livery photographs

`textures/img_1.png` (low front view), `img_3.png` (cockpit close-up), `img_4.png` and `img_6.png` show the same model in a red/blue livery. They are used for geometry only. `fit_livery2_photos.py` fits cameras to wing tip, stabilizer tip, spinner and fin landmarks (`img_4.png` 9.6 px RMS, `img_1.png` 11.5 px RMS). The left/right ambiguity of a nearly flat target is resolved by requiring the camera to sit above the aircraft. With that constraint, the engine-side wing is the near wing in `img.png` and `img_1.png`, and the far one in `img_2.png`, as the visible engine requires.

- The wings, stabilizers, fuselage, fin, wing tips and visible spat fall on the photographed outlines in `img_4.png` and `img_6.png` (`photo-overlay-livery2-img_4.png`, `photo-overlay-livery2-img_6.png`). No geometry change was needed.
- Independent triangulation agrees with the first-livery result: the +y spat nose is at (183, 227, −159) mm against (175, 230, −152), and the fin's aft top corner is within about 20 mm of x=1064. This is consistent with the 450 mm track.
- `img_1.png` (low front view) has only four reliable landmarks, which leave the camera underdetermined: its held-out wing-tip checks miss by 50–65 px. It is used qualitatively. `img_3.png` (cockpit close-up) and photo 9 show a light grey cockpit tub and seat, a dark glare shield, a broad white canopy rim, and an instrument panel with a round gauge left, a blue attitude indicator centre, a round gauge right and one gauge below. The model's cockpit now follows that: a light grey tub and seat instead of a dark interior, a rim 2.4 mm thick instead of 1.3 mm, and the four-gauge layout with a blue centre indicator. These are colour, rim and layout changes only, with no dimensional effect.

`photo-overlay-*.png` shows the new silhouette over each photograph; the `-before` files show the previous revision.

## Parametric CAD and photo fit

`cad/` holds a CadQuery model of the aircraft with 55 named, bounded parameters, a STEP export, and a genetic algorithm that fits those parameters to the photograph silhouettes (see `cad/README.md`). The Blender model is unchanged by it. The silhouettes come from the cut-out photographs (their transparency) through the registered cameras, and the datum dimensions are not parameters.

The default CAD model scores 0.1502 on the four target views (mean contour distance 4–7 px, which is mostly camera registration error). A fit over every parameter reached 0.1369 but improved only one of four held-out views, so it is overfitting and should not be adopted. A fit restricted to gear, fin, canopy and engine reached 0.1429 and improved three of four held-out views, by under 5% each; its largest moves (longer spats, a more forward fin crown, a narrower canopy) are worth checking by eye. The photographs do not support a larger claim: the improvement is within the camera registration error.

## Previous revision

- **Canopy:** the previous crest was 53% aft of the nose. The drawing places it around 42%; the new profile follows that position. Its sill now dips below the deck as shown in the side view, while its dimensioned length remains 240 mm and rise above the deck remains 65 mm. The forward frame is placed closer to the crest, consistent with the cockpit photograph. Shoulder fairings connect the glazing rim to the fuselage instead of leaving a gap.
- **Fuselage:** shape-preserving cubic interpolation replaces linear transitions between cross sections. The 75 mm width continues to the wing leading-edge station. The two checked aft stations retain 95 mm depth.
- **Fin:** (superseded by the photo triangulation revision) the swept leading edge flowed into a rounded crown, replacing the angular, flat-topped outline. The crown remains 125 mm above the 48 mm aft deck.
- **Wing tips:** smooth interpolation replaces angular transitions between the existing traced outline landmarks. Root and outer section dimensions, semispans and straight flap hinges are retained.
- **Cockpit:** the fuselage now has an actual recessed well instead of a solid deck behind the glazing. The close-up informed the thin reclined seat back and its eight lightening holes. Instruments and control stick remain simplified.
- **Reference retention:** the opposite-side and cockpit photographs are retained in the saved file even though they are not used as surface textures.

## Verification and review artifacts

`verify_blueprint_corrections.py` checks the saved file's world-space dimensions, principal mesh closure, canopy position, well opening, seat clearance beneath the glazing and packed references. Its 70 results are written to `blueprint-correction-checks.json`. Checks labeled “traced estimate” are reconstruction constraints, not dimensions printed on the drawing. `verify_blend.py` independently checks that the saved principal airframe meshes have positive volume and no nonmanifold edges.

`blueprint-overlay.png` and `.svg` show actual mesh outlines over the supplied drawing. `orthographic-side.png` and `orthographic-top.png` show the complete assembly. `photo-comparison.jpg` places each unmodified source photograph beside a fresh render from an approximately corresponding direction. The underlying renders are `comparison-primary.png`, `comparison-opposite.png` and `comparison-cockpit.png`.

## Limits

The raster drawing has broken views and inconsistent local scale. Written dimensions take priority over an exact pixel overlay; neither a numeric image-matching tolerance nor exact equality to every drawn contour is claimed. Photo camera poses and focal lengths are unknown. Fin curvature, canopy sill depth, cockpit details and fuselage sections remain estimates.

The original projected livery is preserved, including photographed sky reflections and some perspective distortion. The comparison images therefore expose remaining texture differences as well as geometry. Exact original airfoil ordinates, internal construction, engine dimensions and mechanical joints are unavailable. The control surfaces remain visual seams.
