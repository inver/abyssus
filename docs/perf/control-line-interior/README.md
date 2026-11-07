# Control Line interior rendering evidence

Evidence for the OpenSpec change `improve-control-line-interior-rendering`: the game's hangar (plane select) before and
after the change, next to the look it aims for.

## Reference

`reference-sketchfab-warehouse.jpeg` is the Sketchfab thumbnail of
[Warehouse FBX Model Free](https://sketchfab.com/3d-models/warehouse-fbx-model-free-daa7fd3ff88945298d00045ca40a4c03)
by Nicholas-3D, licensed [CC BY 4.0](http://creativecommons.org/licenses/by/4.0/). It is the same model the bundled
`Hangar.scene` places (`model_warehouse_fbx_model_free`), shown in Sketchfab's own renderer. It is a target picture only;
nothing here is generated from it.

## Before

`before/` was recorded on the maintainer's Linux machine (NVIDIA GeForce RTX 3070 Ti, driver 615.71.09, GL 3.3) before
any task of the change touched rendering:

| File | What | Command |
|---|---|---|
| `control-line-field.png`, `control-line-hangar.png` (+ `.tolerance.json`) | 1280 x 720 frames from the field's flying camera and the hangar's camera | `./gradlew :app-game-control-line:test --tests '*render.PixelEvidenceGlTest' -Dabyssus.glTests=true -Dabyssus.perf.pixels=record -Dabyssus.perf.pixelsDir=<dir>` |
| `control-line-linux.json` | workloads `control-line-flight` and `control-line-hangar` (CPU time of the whole frame and its spans) | `./gradlew :app-game-control-line:test --tests '*render.PerfWorkloadsGlTest' -Dabyssus.glTests=true -Dabyssus.perf.report=<file.json>` |

Before: flight p50 4.16 ms, p95 6.91 ms; hangar p50 0.65 ms, p95 1.15 ms.

## After

`after/` holds the same files, recorded the same way once every task group of the change had landed (local lights, emissive
strength, the HDR frame with bloom, ambient occlusion and vignette, the reflection probe, and the retuned `Hangar.scene`).

After: flight p50 4.91 ms, p95 7.51 ms (the final pass's `post` span 0.30 ms at p50); hangar p50 0.67 ms, p95 0.89 ms. The budget
(design decision 7 of the change) is the pilot view's p50 at most 7.5 ms and p95 at most 11 ms, the hangar's p50 at most 7.5 ms.
