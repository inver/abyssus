# Proposal

## Why

The Classic F2B reference model is a one-off Blender script whose shape constants are tuned by hand. The photographs, not the blueprint, are the authority for its shape, but comparing them is manual, and a hand-tuned overlay mismatch of about 10–20 px cannot be told apart from camera error by eye. A parametric CAD source with named, bounded parameters lets the shape be fitted against photographs by an optimiser and reproduced exactly.

## What Changes

- Add a parametric CadQuery model of the Classic F2B under `models/classic-f2b/cad/`. Every shape constant that is not a blueprint datum is a named parameter with a default (the current Blender values), bounds and a description. The model exports an assembly STEP file plus per-part STL files.
- Add a photo-fit pipeline: silhouette targets extracted from the supplied photographs with GrabCut seeded by the current model, a silhouette renderer that projects the tessellated CAD through the already-fitted photo cameras, and a loss that compares model and photo silhouettes.
- Add a genetic algorithm that searches the parameter space for the lowest loss, deterministic for a given seed, parallel across CPU cores, logging every generation.
- Report before and after: fitted parameters as JSON, loss history, silhouette overlays on each photograph, and the fitted STEP file.
- Datum dimensions that fix the fitted cameras (wing and stabilizer semispans and tip corner stations, root chords, canopy length, propeller diameter, 125 mm fin height) are not genes. The Blender build and its verification are not modified in this change.

## Capabilities

### New Capabilities
- `classic-f2b-parametric-cad`: a bounded, named parameter set and the reproducible CAD export built from it (STEP, STL), with datum dimensions and closed solids checked by tests.
- `classic-f2b-photo-fit`: photo silhouette targets, the silhouette loss, and the seeded genetic search that fits the parameters, with a before and after report.

### Modified Capabilities

None.

## Impact

Only `models/classic-f2b/cad/` is added, plus documentation of it. No native `.scene`, `.abss` or asset `meta.json` is read or written and no editor, runtime or game module changes. The new Python tooling is local to the model folder: a `uv`-managed project (Python 3.12–3.13) depending on CadQuery, NumPy, SciPy, OpenCV (headless) and Pillow. It is not part of the Gradle build or `./gradlew check`.

Out of scope: replacing the Blender build with the CAD model; fitting the cockpit interior, engine details or propeller blade angle; applying the second-livery photographs as textures; refining the cameras jointly with the shape; articulated control surfaces; manufacturing tolerances or structure.
