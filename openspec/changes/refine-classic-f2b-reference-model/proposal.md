## Why

The Classic F2B reference model has angular fuselage transitions, an aft-shifted canopy crest, a flat canopy sill, and a simplified fin crown and cockpit. The user approved correcting the existing model against its blueprint and explicitly requested comparison with all three local photographs.

## What Changes

- Refine the existing procedural model and rebuild the editable Blender asset.
- Preserve explicit drawing dimensions while improving undimensioned contours using the local drawing and photographs.
- Where both photographs contradict the plan (fin outline, main gear position and track, spat shape), follow the photographed aircraft. Measure it with cameras fitted to the photographs and two-view triangulation.
- Produce fresh geometry checks, orthographic views and photo comparison renders.

## Capabilities

### New Capabilities
- `classic-f2b-reference-model`: reproducible, dimension-constrained reference asset with documented visual approximations.

### Modified Capabilities
None.

## Impact

Only `models/classic-f2b` and this OpenSpec capability are affected. No native `.scene`, `.abss` or asset `meta.json` fields are read or written. No native format change, runtime behavior, game asset replacement, mechanical articulation or internal aircraft structure is included.
