# Shader budget parity

Captured on 2026-10-17 before and after tasks 5.1–5.2 of `reduce-code-duplication`, using the existing
`PixelEvidenceGlTest` harnesses with GL enabled. Both hosts render at 1280 × 720 with a fixed 1/60 s step; each recording
contains five runs. `untitled` covers the editor's multi-light models, terrain and shadows; `control-line-field`
covers the game's single-sun terrain and shadows. Every repeated run was identical, and the before/after PNG files
are byte-identical for both cases. Hashes are in [comparison.json](comparison.json).

Command (replace `<phase>` with `before` or `after` and `<repo>` with the checkout's absolute path):

```sh
./gradlew :plugin-abyssus:test --tests '*perf.PixelEvidenceGlTest' :app-game-control-line:test --tests '*render.PixelEvidenceGlTest' -Dabyssus.glTests=true -Dabyssus.perf.pixels=record -Dabyssus.perf.cases=untitled -Dabyssus.perf.pixelsDir=<repo>/docs/perf/reduce-code-duplication/<phase>
```
