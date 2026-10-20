# Tasks

## 1. Project scaffold and parameter set

- [x] 1.1 Keep `models/classic-f2b/cad/pyproject.toml` and `uv.lock` (CadQuery, NumPy, SciPy, OpenCV headless, Pillow, pytest), git-ignore `.venv`, `out/` and caches, and add a `cad/README.md` with the `uv sync` and `uv run` commands. Verify with `cd models/classic-f2b/cad && uv run pytest -q` collecting at least one test and `scripts/check-docs.sh`.
- [x] 1.2 Add `f2b/params.py`: the gene table (name, group, unit, default, bounds, description), validation, normalised-vector conversion and JSON save/load. Defaults reproduce the current Blender values. Verify with `tests/test_params.py`: defaults within bounds, unique names, out-of-range and unknown names rejected with the name in the message, JSON round trip identical.

## 2. Parametric CAD and export

- [x] 2.1 Add `f2b/geometry.py` building fuselage, wings, stabilizers, fin, canopy, spats, gear legs and the simplified engine, propeller and cockpit solids from a parameter set in the Blender frame (millimetres, engine side on +y). Verify with `tests/test_geometry.py`: principal solids valid with positive volume, the datum dimensions (spans, root chords, tip stations, canopy length, fin height) within 0.5 mm for the defaults and for every gene group at its lower and upper bound, and identical volumes on repeated builds.
- [x] 2.2 Add `f2b/export.py` and the `python -m f2b.build` command writing an assembly STEP with named parts and per-part STL into `out/`. Verify with `tests/test_export.py` (reload the STEP, all names present, bounding box within 0.1 mm) and by running the command.

## 3. Cameras and silhouettes

- [x] 3.1 Add `f2b/cameras.py` loading `photo-registration.json` and `photo-registration-livery2.json` and projecting world millimetres to pixels, and `f2b/silhouette.py` rasterising tessellated parts and extracting contours, with the propeller disc masked. Verify with `tests/test_cameras.py` (the projected landmark pixels match the stored model pixels within 1 px) and `tests/test_silhouette.py` (deterministic mask, mask area within 3 percent of the Blender silhouette mask for the default model in each view).
- [x] 3.2 Add `f2b/targets.py` and `python -m f2b.targets`, producing silhouette masks for photo 7 (`img_5.png`), photo 8 (`img.png`), `img_4.png` (cut-outs: their transparency, holes filled) and `img_1.png` (GrabCut seeded by the default silhouette), with a review overlay per view in `targets/`. Verify with `tests/test_targets.py` (binary masks, intersection over union with the default silhouette at least 0.8, cut-out masks match their alpha, excluded views absent) and by viewing the overlays.

## 4. Loss and genetic algorithm

- [x] 4.1 Add the loss to `f2b/silhouette.py` (symmetric difference plus contour distance, prior penalty, infinite for invalid candidates). Verify with `tests/test_loss.py`: zero loss for a set against its own rendering, higher loss when the fin height or spat size moves more than 10 percent, infinite loss for an unbuildable candidate.
- [x] 4.2 Add `f2b/ga.py` (real-coded GA: tournament, SBX, polynomial mutation, elitism, process pool, per-individual seeds, parameter hash cache, loss history). Verify with `tests/test_ga.py`: same seed gives identical best set and history for one and for two workers, best loss never increases, the result stays in bounds, and a search started from the defaults against targets rendered from a perturbed set reaches at most half the default loss.

## 5. Fit, report and documentation

- [ ] 5.1 Add `f2b/report.py` and `python -m f2b.fit`: run the fit, write fitted parameters JSON, loss history CSV, per-view loss for defaults and result, before and after overlays and the fitted STEP to `out/`, then the four leave-one-view-out fits with their held-out losses. Verify with `tests/test_report.py` (all files written for a short run) and by running the full fit, then recording the loss before and after in the documentation.
- [ ] 5.2 Review the overlays, state the per-view and held-out results honestly (including genes that hit a bound), and document the cad tooling in `models/classic-f2b/README.md` and `BLUEPRINT_REVIEW.md`. Verify with `scripts/check-docs.sh`.
- [ ] 5.3 Run `uv run pytest -q` in `models/classic-f2b/cad`, `./gradlew check` and `scripts/check-docs.sh`, and `openspec validate add-classic-f2b-parametric-cad-fit --strict`.
