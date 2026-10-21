# Classic F2B — parametric CAD and photo fit

A CadQuery model of the Classic F2B whose shape is a function of named, bounded parameters (`f2b/params.py`), and a genetic algorithm that fits those parameters to photograph silhouettes. The Blender model in the parent folder is unchanged.

Setup (Python 3.12–3.13, managed by `uv`; the system Python 3.14 has no CadQuery wheels):

    cd models/classic-f2b/cad
    uv sync
    uv run pytest -q                      # about 6 minutes: it builds many CAD models

Datum dimensions that fix the registered photo cameras and the blueprint's explicit dimensions (semispans, tip corner stations, root chords, canopy length, propeller diameter, 125 mm fin height) are constants in `f2b/geometry.py`, not parameters. This tooling is not part of `./gradlew check`.

## Commands

| What | Command |
|---|---|
| Build the model, export STEP + per-part STL | `uv run python -m f2b.build --out out/default` (add `--params file.json` for a fitted set) |
| Regenerate the photo silhouette targets in `targets/` | `uv run python -m f2b.targets` |
| Fit to the photographs and write the report | `uv run python -m f2b.fit --out out/fit --preset physical` (population 48, 60 generations, four hold-out fits, about 40 minutes on 6 workers; `--preset all` frees every gene; `--help` lists the options) |

## Layout

- `f2b/params.py`: the 55 genes (name, group, unit, default, bounds, description), validation, JSON and normalised-vector conversion.
- `f2b/geometry.py`: the CadQuery builders per gene group (fuselage, wing, stabilizer, fin, canopy, gear, engine); `build(params, detail)` returns named parts. The wings and stabilizers are ruled lofts: a smooth interpolating loft through the rounded tip blows up, so the tip uses 16 closely spaced sections.
- `f2b/export.py`: assembly STEP with named, coloured parts, and one STL per part (millimetres, engine side on +y, the Blender model's frame).
- `f2b/cameras.py`: the cameras registered by `fit_photo_overlays.py` and `fit_livery2_photos.py` in the parent folder.
- `f2b/silhouette.py`: tessellation, triangle rasteriser, propeller-disc mask and the loss (area of the symmetric difference with the photo mask over its area, plus 0.01 per pixel of mean symmetric contour distance).
- `f2b/targets.py`: the photo masks in `targets/`. The cut-out photographs (`img_5.png`, `img.png`, `img_4.png`, `img_6.png`) give their silhouette from their transparency, with glazing holes filled and debris dropped; a photograph without transparency would use GrabCut seeded by the default silhouette (tested on a cut-out pasted on a textured background). Each mask has a `-review.jpg` overlay.
- `f2b/fitness.py`: the evaluator. Per-group meshes are cached, so a candidate that changes only some genes re-tessellates only the parts those genes shape. A small prior (weight 0.02 on the mean squared normalised deviation from the defaults) keeps genes near their defaults.
- `f2b/ga.py`: the genetic algorithm: real-coded, tournament selection (3), SBX crossover, polynomial mutation, elitism (2). One random generator is consumed in a fixed order in the parent process, so results do not depend on the number of workers.
- `f2b/report.py` and `f2b/fit.py`: the fit and its report.

## Fit results

Four views: photographs 7 and 8 (`img_5.png`, `img.png`) and the red/blue views `img_4.png` and `img_6.png`, all as cut-out masks. Loss is the mean over views of area ratio + 0.01 × contour pixels (see `f2b/silhouette.py`); lower is better, and the defaults score **0.1502**. Evidence is in `results/` (parameters, per-gene table, per-generation history, before and after overlays).

| Fit | Genes free | Loss, defaults → fitted | Hold-out: views improved when left out |
|---|---|---|---|
| `results/all` | all 55 (47 moved) | 0.1502 → 0.1369 | **1 of 4** (photo 7 by 1%; photo 8, `img_4` and `img_6` got worse by 30%, 10% and 27%) |
| `results/physical` | gear, fin, canopy, engine (26) | 0.1502 → 0.1429 | **3 of 4** (photo 7 and `img_6` by under 1%, `img_4` by 5%; photo 8 got worse by 5%) |

Reading: fitting every gene lowers the loss on the views it sees but not on a view it has not seen, so most of that gain is the search absorbing camera registration error with fuselage, tip and tail shape; do not adopt it. Restricting the search to the parts that silhouettes constrain directly generalises better, but the gain is small (a few percent, comparable to the camera error), and one hold-out view still gets worse. The physical fit moved the genes below, which are the ones worth checking by eye or with a better camera registration before changing the Blender model:

- gear: spat 17 mm longer (160 → 178) and 12 mm further forward at the nose, axle 19 mm further aft (240 → 259, near its 265 bound), leg exit slightly aft and outboard;
- fin: crown 14 mm further forward, thinner (7 → 5.6 mm);
- canopy: narrower (half-width 32 → 28.6 mm) with a deeper sill dip and 4 mm lower crest;
- engine: cylinder moved to the 60 mm forward bound, silencer lower and further out. This is the least trustworthy result: the cylinder is mostly occluded in the photographs.

The fitted STEP for either set is rebuilt with `uv run python -m f2b.build --params results/physical/params-fitted.json --out out/physical`.

## Limits

- The cameras were registered from hand-marked landmarks, with 10–20 px residuals. The default model therefore sits about 4–9 px (mean contour distance) from the photograph contours, and part of that is camera error that no shape change should absorb. The datums are fixed for that reason, and the report includes a leave-one-view-out check.
- Silhouettes do not constrain interior or occluded shape, such as the fuselage cross-section under the wing, so those genes keep wide priors.
- The propeller disc is masked out of every comparison, because blade angle differs between photographs.
- The CAD model is not the Blender asset and does not regenerate it.
