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
| Fit to the photographs and write the report | `uv run python -m f2b.fit --out out/fit` (population 48, 60 generations, four hold-out fits; `--help` lists the options) |

## Layout

- `f2b/params.py`: the 57 genes (name, group, unit, default, bounds, description), validation, JSON and normalised-vector conversion.
- `f2b/geometry.py`: the CadQuery builders per gene group (fuselage, wing, stabilizer, fin, canopy, gear, engine); `build(params, detail)` returns named parts. The wings and stabilizers are ruled lofts: a smooth interpolating loft through the rounded tip blows up, so the tip uses 16 closely spaced sections.
- `f2b/export.py`: assembly STEP with named, coloured parts, and one STL per part (millimetres, engine side on +y, the Blender model's frame).
- `f2b/cameras.py`: the cameras registered by `fit_photo_overlays.py` and `fit_livery2_photos.py` in the parent folder.
- `f2b/silhouette.py`: tessellation, triangle rasteriser, propeller-disc mask and the loss (area of the symmetric difference with the photo mask over its area, plus 0.01 per pixel of mean symmetric contour distance).
- `f2b/targets.py`: the photo masks in `targets/`. The cut-out photographs (`img_5.png`, `img.png`, `img_4.png`) give their silhouette from their transparency, with glazing holes filled and debris dropped; `img_1.png` is opaque and uses GrabCut seeded by the default silhouette. Each mask has a `-review.jpg` overlay.
- `f2b/fitness.py`: the evaluator. Per-group meshes are cached, so a candidate that changes only some genes re-tessellates only the parts those genes shape. A small prior (weight 0.02 on the mean squared normalised deviation from the defaults) keeps genes near their defaults.
- `f2b/ga.py`: the genetic algorithm: real-coded, tournament selection (3), SBX crossover, polynomial mutation, elitism (2). One random generator is consumed in a fixed order in the parent process, so results do not depend on the number of workers.
- `f2b/report.py` and `f2b/fit.py`: the fit and its report.

## Limits

- The cameras were registered from hand-marked landmarks, with 10–20 px residuals. The default model therefore sits about 4–9 px (mean contour distance) from the photograph contours, and part of that is camera error that no shape change should absorb. The datums are fixed for that reason, and the report includes a leave-one-view-out check.
- Silhouettes do not constrain interior or occluded shape, such as the fuselage cross-section under the wing, so those genes keep wide priors.
- The propeller disc is masked out of every comparison, because blade angle differs between photographs.
- The CAD model is not the Blender asset and does not regenerate it.
