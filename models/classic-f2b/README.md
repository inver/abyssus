# Yatsenko Classic F2B — reference reconstruction

Open **Classic_F2B.blend**. This revision refines the blueprint-corrected model against all three local photographs; see `BLUEPRINT_REVIEW.md` and the updated overlay for the corrections and verification. The scene is full size, with metric display in millimetres and internal coordinates in metres. All three photographs are packed inside the blend file; no external texture installation is required. Components are organized into named collections: fuselage, wings, stabilizer, landing gear, engine/propeller, cockpit, and studio. The camera and lights are included.

## Reference basis

- Article: http://discovery-aeromodels.com/en/yuriy-yatsenko-classic-f2b-model.html
- Primary livery and UV texture: http://discovery-aeromodels.com/images/classic/yatsenko-control-line-classic-f2b-aeromodel-7.jpg
- Opposite view of the same livery: image 8 in that directory.
- Cockpit detail: image 9.
- Dimensioned plan: image 15, included as `plan-reference.jpg`.
- Images 1–5 and 10–14 were inspected for shape, but their different liveries were not mixed into this model. The article HTML was inaccessible during creation; the image files were retrieved directly.

Upper wing and stabilizer markings, including the photographed registration lettering, come directly from photo 7 through projective UV mapping. Narrow areas of the fuselage spine also use photo 7. White lower surfaces, red fin, pinstripes, canopy, engine and gear use matching materials. This is not a complete photographic scan: occluded areas are reconstructed. Photographed reflections and some perspective distortion remain in the source texture; it is not a de-lit PBR texture set. Source photos remain the property of their respective rights holders.

## Dimensions and interpretation

| Feature | Model value |
|---|---:|
| Wing span, excluding leadout guides | 1450 mm |
| Wing semispans | 740 / 710 mm |
| Root / nominal tip chord | 340 / 275 mm |
| Root wing thickness | 62 mm |
| Stabilizer span | 690 mm |
| Stabilizer root chord | 161 mm |
| Canopy length / rise | 240 / 65 mm |
| Fin height above aft deck | 125 mm |
| Propeller diameter / nominal pitch | 340 / 140 mm |
| Main gear track | 450 mm (photo-triangulated; plan shows 370) |

The symmetric airfoil is an analytic approximation scaled to the drawing thickness. Rounded tips, fuselage cross sections, canopy curvature, fin outline and small details are estimated from the views. The canopy's 65 mm rise is measured above the fuselage deck; its recessed sill makes the complete glazing envelope 81 mm tall. Its crest is approximately 42% aft of the canopy nose. An open cockpit well exposes the seat, perforated seat back, instruments and controls. Wing flaps and elevator hinge lines are represented, but control surfaces are not mechanically articulated. The engine has a horizontal cylinder and visually reconstructed cooling fins, head, plug and silencer; this is not a manufacturer's dimensioned Retro 60 model.

The original model's **1600 ±20 g** flying weight and **10 cc Discovery Retro 60** engine are recorded as scene metadata. Neither flying mass nor a low-inertia composite layup is established by this mesh. Ball-lock sockets and detents are schematic external representations: no undocumented internal Petrov mechanism, release action or manufacturing tolerances are asserted. Separate components express the demountable layout without inventing functional joints.

## Files and manufacturing limits

- `Classic_F2B.blend`: editable model, packed source texture, materials, camera, lights, and embedded accuracy notes.
- `rendered.png`: rendered model at an angle approximating photo 7.
- `wireframe.png`: topology preview from the same camera.
- `stl/`: separate principal airframe solids and landing-gear components, in millimetres and their assembly positions. STL carries no color.
- `validation.json`: principal mesh dimensions and edge-manifold checks.
- `blueprint-correction-checks.json`: 70 saved-file dimensional, topology and reference-retention checks; traced estimates are explicitly labeled.
- `blueprint-overlay.png`: corrected airframe outline over the supplied drawing.
- `orthographic-side.png` / `orthographic-top.png`: assembly views without perspective.
- `build_classic.py`: reproducible Blender 5.2+ build script.
- `fit_photo_overlays.py` / `photo-registration.json`: perspective cameras fitted to wing, tail and spinner landmarks in photos 7 and 8; renders `aligned-*.png` and silhouette masks.
- `photo_triangulation.py` / `photo-triangulation.json`: 3D positions of the fin and gear triangulated from both photographs, with spinner and canopy-nose checks.
- `textures/img.png`, `img_1.png`, `img_2.png`, `img_3.png`: the same model in a red/blue livery (overall views and a cockpit close-up). Used for geometry only, never as texture; packed in the blend file.
- `fit_livery2_photos.py` / `photo-registration-livery2.json` / `photo-overlay-livery2-*.png`: cameras fitted to `img.png` (9.6 px RMS) and `img_1.png` (11.5 px RMS) with the model silhouette over each photograph. `img_2.png` has too few usable landmarks for a reliable camera and `img_3.png` is a close-up, so both are qualitative references.
- `photo-overlay-primary.png` / `photo-overlay-opposite.png`: model silhouette (magenta) over each photograph; `-before` files show the previous revision.
- `photo-comparison.jpg`: source photographs beside fresh model renders, including a cockpit close-up.
- `render_photo_comparisons.py`: recreates the three `comparison-*.png` views from the saved model.

Rebuild with `blender -b --python-exit-code 1 --python build_classic.py`. Then run `verify_blueprint_corrections.py`, `verify_blend.py`, `fit_photo_overlays.py`, `photo_triangulation.py`, `audit_blueprint.py`, `render_orthographic.py` and `render_photo_comparisons.py` with the same Blender invocation. `compose_comparisons.sh` converts the updated overlay SVG to PNG and assembles the labeled photo comparison sheet using ImageMagick. Camera directions approximate the photographs; these are qualitative comparisons, not camera-calibrated measurements.

Principal airframe solids are closed meshes. Fuselage and fin are separate intersecting parts; gear components are separate bodies. These are reference solids, not hollow flightworthy structures or a printer-bed layout. Full-size fabrication requires wall thickness/structural design, mating joints, clearances and segmentation appropriate to the chosen process. The clear canopy and decorative curves are display details, not included in the principal airframe STL exports.
