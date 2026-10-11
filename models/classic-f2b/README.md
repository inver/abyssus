# Yatsenko Classic F2B — reference reconstruction

Open **Classic_F2B.blend**. This is the blueprint-corrected revision; see `BLUEPRINT_REVIEW.md` and the updated overlay for the corrections and verification. The scene is full size, with metric display in millimetres and internal coordinates in metres. The primary photograph is packed inside the blend file; no external texture installation is required. Components are organized into named collections: fuselage, wings, stabilizer, landing gear, engine/propeller, cockpit, and studio. The camera and lights are included.

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
| Propeller diameter / nominal pitch | 340 / 140 mm |
| Main gear track | 370 mm |

The symmetric airfoil is an analytic approximation scaled to the drawing thickness. Rounded tips, fuselage cross sections, canopy curvature, fin outline and small details are estimated from the views. Wing flaps and elevator hinge lines are represented, but control surfaces are not mechanically articulated. The engine has a horizontal cylinder and visually reconstructed cooling fins, head, plug and silencer; this is not a manufacturer's dimensioned Retro 60 model.

The original model's **1600 ±20 g** flying weight and **10 cc Discovery Retro 60** engine are recorded as scene metadata. Neither flying mass nor a low-inertia composite layup is established by this mesh. Ball-lock sockets and detents are schematic external representations: no undocumented internal Petrov mechanism, release action or manufacturing tolerances are asserted. Separate components express the demountable layout without inventing functional joints.

## Files and manufacturing limits

- `Classic_F2B.blend`: editable model, packed source texture, materials, camera, lights, and embedded accuracy notes.
- `rendered.png`: rendered model at an angle approximating photo 7.
- `wireframe.png`: topology preview from the same camera.
- `stl/`: separate principal airframe solids and landing-gear components, in millimetres and their assembly positions. STL carries no color.
- `validation.json`: principal mesh dimensions and edge-manifold checks.
- `blueprint-correction-checks.json`: 48 saved-file dimensional and topology checks.
- `blueprint-overlay.png`: corrected airframe outline over the supplied drawing.
- `orthographic-side.png` / `orthographic-top.png`: assembly views without perspective.
- `build_classic.py`: reproducible Blender 5.2+ build script.

Principal airframe solids are closed meshes. Fuselage and fin are separate intersecting parts; gear components are separate bodies. These are reference solids, not hollow flightworthy structures or a printer-bed layout. Full-size fabrication requires wall thickness/structural design, mating joints, clearances and segmentation appropriate to the chosen process. The clear canopy and decorative curves are display details, not included in the principal airframe STL exports.
