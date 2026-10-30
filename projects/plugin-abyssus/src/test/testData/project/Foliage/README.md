# Foliage fixture

A small project for the foliage chain: one terrain (`terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b`, the `Untitled`
heights), `tree`, one model, and the foliage asset `foliage_meadow` with an `OBJECT` layer of `tree` and a `DETAIL`
layer of `model_29e9be61-6594-4f82-a6cf-44ccf09f71fb`. `Main Scene` holds the terrain as entity `1` and the model as
entity `2`; neither names the foliage asset, so tests can add it and undo.

`foliage_meadow` holds a painted `layer-1.mask` (64 x 64, density 96 in the x 48..63, z 0..15 corner and 255
elsewhere; the OBJECT layer has no mask file, so it is full) and a committed `foliage.data` generated from exactly
those inputs. `FoliageFixtureTest` in `core` asserts the bake loads as not stale and that its fingerprint follows the
inputs. **If you change the meta, the mask or the terrain heights, regenerate the bake** (run the scatter over the
prepared foliage and write `foliage.data`), or the fixture test fails.

The model folders hold only their `meta.json`: foliage loading, panel and scene tests need identities, not binaries.
Do not open this folder in the sandbox IDE; use a disposable project copy for interactive checks.
