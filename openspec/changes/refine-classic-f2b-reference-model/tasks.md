## 1. Correct and verify the asset

- [x] 1.1 Inspect the local blueprint, both aircraft photographs and cockpit close-up; record the approved approach in design.md.
- [x] 1.2 Rebuild the refined model with Blender; verify saved dimensions using verify_blueprint_corrections.py and closed solids using verify_blend.py.
- [x] 1.3 Regenerate blueprint overlays, orthographic views and photo comparisons; visually inspect their agreement and document remaining estimates.
- [x] 1.4 Update model documentation; run `./gradlew check` and `scripts/check-docs.sh`.

Verification: Blender 5.2.2 completed the rebuild; all 56 saved-file checks passed,
including the seat-to-canopy ray checks. Principal airframe meshes have no
nonmanifold edges and positive signed volume. The final photo sheet and blueprint
overlay were visually inspected. `./gradlew check` passed (185 actionable tasks);
`scripts/check-docs.sh` passed (302 paths). OpenSpec strict validation passed.

## 2. Photo-triangulated corrections

- [x] 2.1 Fit photo cameras to wing, tail and spinner landmarks only. Triangulate the fin and main gear from both photographs (`photo_triangulation.py`), with spinner and canopy-nose checks.
- [x] 2.2 Rebuild with the photographed fin, a 450 mm gear track and teardrop spats. Update `verify_blueprint_corrections.py` (66 checks pass) and run `verify_blend.py`.
- [x] 2.3 Regenerate photo overlays, orthographic views, comparisons and the blueprint audit. Update the model docs, then run `scripts/check-docs.sh` and `./gradlew check`.
