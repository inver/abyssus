# Blueprint correction verification

The saved Blender model has been rebuilt to address the prior review. The earlier findings are retained in `BLUEPRINT_REVIEW-before-correction.md` as history; they do not describe the current model.

## Corrections applied

- Aft fuselage depth is now 95 mm at the 895 and 1000 mm stations. The body retains its depth into the tail instead of converging into a thin boom. The fin/body junction follows the revised silhouette.
- Flap hinges are straight at x=514 mm, with centreline-to-end spans of 600 and 570 mm. End seams identify the correct span. Root flap chord is 76 mm and narrows outward.
- Elevator hinges are straight at x=971 mm. Root fixed/elevator chords are 76/85 mm; the outer dimension station has 50/65 mm chords.
- Wing outer sections explicitly measure 275 mm chord and 52 mm thickness, at 660/630 mm span stations. Beyond those stations, the rounded tip contour follows sampled blueprint outline landmarks. Root section remains 340 mm chord and 62 mm thick.
- Stabilizer outer chord is 115 mm at a 315 mm semispan station, with a short rounded tip extending to 345 mm.
- Engine, spinner, propeller and exhaust assembly have 3 degrees of yaw relative to the fuselage. The engine cylinder remains horizontal.
- Canopy uses an asymmetric side profile with its crest approximately 53% aft of its nose, and preserves 240 mm length and 65 mm rise. Position was re-registered to the drawing.
- Main landing gear legs now leave the wing underside 144 mm off the centreline (front view) and splay to the 370 mm track, 65 mm behind the wing leading edge, raking ~30 mm forward to the axle; the spat sits at x≈296 mm. Tail wheel moved to x≈919 mm with its wire starting at x≈880 mm (previously 960 mm). Earlier text: Gear height and tail-wheel placement were adjusted from the side-view silhouette; 370 mm wheel track is retained.
- Primary-photo texture is still packed into the file. Principal airframe parts remain separate closed meshes.

## Verification

`verify_blueprint_corrections.py` opens the saved `.blend` and checks actual world-space geometry. Its 48 checks cover spans, chords, thicknesses, straight hinge stations, flap lengths, aft-body depth, canopy envelope, engine yaw, gear track, principal mesh closure and the packed source photo. See `blueprint-correction-checks.json` for measured values and results.

`blueprint-overlay.png` / `.svg` show the current model's airframe outlines in orange over the supplied plan. `orthographic-side.png` and `orthographic-top.png` show the actual assembly without perspective distortion.

## Remaining interpretation limits

The source is a raster drawing with broken views and locally inconsistent scale. Written dimensions take priority; undimensioned contour stations, canopy placement and gear height are traced estimates. The selected outer-section stations distinguish the dimensioned outer profile from the rounded cap; their spanwise locations are inferred from the drawing. The airfoil is symmetric and thickness-constrained, but original coordinate ordinates are unavailable. Texture projection contains photographed reflections and photographic hinge details, which can differ from the corrected geometric seams.

The model is a blueprint-guided external reference reconstruction, not a certified flightworthy design. Internal demountable joints, Petrov ball-lock internals, materials and the 1600 ±20 g mass distribution are not defined by the supplied views. Control-surface seams are visual geometry, not mechanically articulated flaps/elevators.

Source: http://discovery-aeromodels.com/images/classic/yatsenko-control-line-classic-f2b-aeromodel-15.jpg
