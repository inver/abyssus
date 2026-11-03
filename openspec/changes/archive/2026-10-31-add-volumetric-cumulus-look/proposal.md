# Proposal

## Why

The flat `cumulus` technique (change `add-cumulus-cloud-technique`) gets close to the reference photo (`Cumulus_AMayes.jpg`)
in colour and edge, but each cloud is a flat layer: it has no volume, so it cannot show round heads that stand above a
flat base or change as the camera moves. The existing `volumetric` technique marches real 3D density, but its shape is
generic noise, not heaped cumulus. The game should show clouds with real volume.

## What Changes

- Give the `volumetric` technique a cumulus look for heaped bands: cauliflower heads standing on flat bases, crisper
  edges, bright sunlit tops with a silver rim toward the sun and soft grey bases. It uses the existing 3D noise textures.
- Control Line's `clouds_fair` asset uses `VOLUMETRIC`, so the game draws it.
- The game measures its own frame time and, when volumetric clouds hold it too slow, steps the sky down to `shells`,
  then `cumulus`, then `layered`, as the scene view already does for volumetric.
- A GL test draws the field's sky with volumetric clouds beside the other techniques, saving a picture and the frame time.
- No change to the `meta.json` format, to `shells`, `layered` or `cumulus`, or to how other cloud types look.

## Capabilities

### New Capabilities

### Modified Capabilities
- `scene-sky-clouds`: a requirement for the heaped volumetric look (added; no existing requirement is edited).
- `control-line-field-environment`: the game draws volumetric clouds and steps down when they are too slow (added).

## Impact

- `projects/lib-core`: `clouds_volumetric.frag` (density and lighting for heaped bands), tests, README.
- `projects/app-game-control-line`: `clouds_fair/meta.json`, `FieldRenderer` (frame-time step-down), `FieldSkyGlTest`, README.
- Native documents unchanged: `technique` already allows `VOLUMETRIC`.
- Out of scope: new cloud types, cloud shadows on the terrain, other games, changes to the editor toolbar.
- Depends on `add-cumulus-cloud-technique` (the `cumulus` step-down target); it must be applied first.
