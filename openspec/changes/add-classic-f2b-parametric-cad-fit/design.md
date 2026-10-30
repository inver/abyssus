# Design

## Context

The current model is built by `models/classic-f2b/build_classic.py` in Blender. Its shape constants are hand-tuned, and `fit_photo_overlays.py` / `fit_livery2_photos.py` have already fitted a perspective camera to each usable photograph (`photo-registration.json`, `photo-registration-livery2.json`). Those cameras were fitted to wing, stabilizer and spinner landmarks, not to the fin, gear or fuselage, so those parts can be checked against them. The residual camera error is about 10–20 px. The photographs, not the blueprint, are the authority (see the archived `refine-classic-f2b-reference-model` change).

The work is offline Python in the model folder. It touches no editor, runtime or native document, so the module boundaries and threading rules of the plugin do not apply.

## Goals / Non-Goals

**Goals:**
- One CAD source whose shape is a pure function of a named, bounded parameter vector.
- A reproducible, parallel genetic fit of that vector to photo silhouettes with an honest before and after.

**Non-Goals:**
- Replacing or regenerating the Blender asset or its verification scripts.
- Fitting cameras jointly with the shape; fitting cockpit, engine detail or propeller angle.
- Matching livery or texture.

## Decisions

- **CadQuery on a `uv`-managed Python 3.12–3.13 project in `models/classic-f2b/cad/`.** Alternatives: OpenSCAD and FreeCAD, which need a separate GUI or binary install and are awkward to parametrise from Python. CadQuery gives OpenCASCADE solids, lofts, fillets and STEP export from a plain script. The system Python 3.14 has no OCP wheels, so the project pins its own interpreter.
- **Datums are constants, the rest are genes.** Wing and stabilizer semispans, tip corner stations, root chords, canopy length, propeller diameter and fin height are fixed. They define the cameras and the blueprint's explicit dimensions, so letting the search move them would let it trade camera error for shape. 55 genes remain, with a `physical` preset that frees only the 26 gear, fin, canopy and engine genes: fuselage section controls at six stations (half-width, half-depth, centre height), fin outline, canopy height, crest position, half-width and sill dip, spat size and position, leg exit and axle positions, wing and stabilizer tip rounding, engine and silencer position.
- **One CAD source drives both export and fitting.** The silhouette renderer tessellates the same CadQuery solids with a coarse tolerance and rasterises triangles through the fitted camera. A second fast analytic model would drift from the exported one. The cost is OpenCASCADE time per candidate (budgeted at about 1 s), paid back by a process pool.
- **Targets come from the photographs' transparency where the background was removed, and from GrabCut seeded by the current model's silhouette otherwise.** The cut-out photographs (`img_5.png` and `img.png` for photographs 7 and 8, `img_4.png` for the red/blue view) give their silhouette directly, after filling transparent glazing and dropping debris left in the cut-out; `img_1.png` is opaque and uses GrabCut. Sure foreground is the model silhouette eroded, sure background is everything outside its dilation, and the band between is unknown. Plain colour thresholds fail on grass and on red pavers, and manual masks do not reproduce. Seeding biases the mask toward the current model by at most the band width, so the band is kept narrow and the masks are written with overlays for review. Targets: photo 7, photo 8, `img.png`, `img_1.png`. `img_2.png` (weak camera) and the close-ups are excluded.
- **Loss = symmetric difference area / photo area + 0.5 × mean symmetric contour distance / image diagonal × 100**, summed over views with equal weights, plus a small prior penalty that keeps genes near their defaults. The propeller disc is projected and masked out of the comparison because blade angle differs between photos. Invalid or unbuildable candidates get an infinite loss.
- **Real-coded GA written for this task.** Genes are normalised to [0, 1]; selection is tournament of 3, crossover is SBX (η = 15), mutation is polynomial (η = 20, rate 1/n), elitism keeps 2 individuals, population 48 over 60 generations (configurable). Random numbers come from one `numpy` generator split per individual so that results do not depend on worker count. A SciPy differential evolution was considered and rejected because the request is a genetic algorithm and the selection and crossover steps should be visible and testable.
- **Held-out validation.** (This is what separated a fit that overfits from one that generalises: freeing every gene improved one of four held-out views, the physical preset three of four.) After the main fit, four fits each leave one photograph out and report that photograph's loss against the defaults. A fit that improves the training views but not the held-out view is reported as camera or silhouette overfitting.
- **Files.** `f2b/params.py` (gene table, validation, JSON), `f2b/geometry.py` (CadQuery builders), `f2b/export.py` (STEP, STL), `f2b/cameras.py` (load and project the registered cameras), `f2b/silhouette.py` (rasteriser, contours, loss), `f2b/targets.py` (GrabCut), `f2b/ga.py` (search), `f2b/report.py` (overlays, JSON, held-out), a `__main__`-style command per step, and pytest tests. Generated outputs go under `cad/out/`, and the targets under `cad/targets/`; the `.venv` is git-ignored.

## Risks / Trade-offs

- [Camera error is absorbed into the shape] → bounds on every gene, a prior penalty, datums fixed, and the held-out check that shows whether a fit generalises.
- [GrabCut masks inherit the current model's bias] → narrow unknown band, review overlays, and report the per-view loss of the defaults so the improvement is read against the same masks.
- [Fillets and lofts can fail for extreme genes] → candidates that fail to build are scored infinite and never selected; bounds are chosen so the defaults and the bound corners of each group build, and a test builds them.
- [Silhouette cannot constrain interior or occluded shape such as cross-section fullness under the wing] → those genes keep wide priors and the report flags genes that did not move beyond a threshold.
- [CadQuery needs an interpreter outside the system Python] → documented `uv sync` and `uv run` commands; the tooling is not part of `./gradlew check`.

## Open Questions

- Whether the fitted parameters should later regenerate the Blender asset is left to a separate change; this one stops at the fitted STEP and report.
