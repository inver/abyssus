# Allocation sites before the allocation work (task 6.1)

A JFR `profile` recording of the whole-frame workload's samples alone (`-Dabyssus.perf.jfr`, started after the warm-up and
stopped before the hitches), aggregated by the first frame outside the JDK and Kotlin's library, weighted by the sampled
bytes. The workload's own JFR dump (it runs on the same thread after the samples) is left out.

- **Machine.** Not the reference machine: Linux 7.0 amd64, NVIDIA GeForce RTX 3070 Ti (driver 615.71.09), Eclipse Adoptium
  21.0.9, Kover agent attached (the game's `test` task). Allocation sites and bytes per frame do not depend on the GPU, so
  the order holds on the Mac; the frame times of this run are not evidence.
- **Run.** Tree of 2026-11-04 with this change's tasks 1-5.4 applied (culling on). `-Dabyssus.perf.warmupSeconds=5
  -Dabyssus.perf.sampleSeconds=15 -Dabyssus.perf.samples=1`, a Watch AI F2B flight (Mid), 1950 frames.
- **Report.** 963 KB allocated per whole frame; spans: `models` 808 KB, `ui` 104 KB, `shadow` 22 KB, `flight` 7 KB.

```
./gradlew :app-game-control-line:test --tests '*render.PerfWorkloadsGlTest' -Dabyssus.glTests=true \
  -Dabyssus.perf.report=alloc-before.json -Dabyssus.perf.jfr=alloc-before.jfr \
  -Dabyssus.perf.warmupSeconds=5 -Dabyssus.perf.sampleSeconds=15 -Dabyssus.perf.samples=1
jfr print --json --stack-depth 64 --events jdk.ObjectAllocationSample alloc-before.jfr
```

## Top ten sites

| # | Site | Share | Bytes per frame | Objects |
|---|---|---|---|---|
| 1 | `net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings.bind:22` | 34.3% | 323 KB | `[F` |
| 2 | `net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings.bind$array:17` | 9.8% | 92 KB | `[B`, `java.lang.String` |
| 3 | `net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings.bind:26` | 6.8% | 64 KB | `[F` |
| 4 | `net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings.bind:25` | 6.0% | 57 KB | `[F` |
| 5 | `net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings.bind:47` | 6.0% | 56 KB | `[F` |
| 6 | `net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings.bind:28` | 4.4% | 42 KB | `[F` |
| 7 | `net.nevinsky.abyssus.lib.gdx.g3d.ModelShader.bindShadowAtlas:226` | 3.3% | 31 KB | `java.util.ArrayList`, `[Ljava.lang.Object;` |
| 8 | `net.nevinsky.abyssus.lib.gdx.shader.ShadowAtlasBindings.bind:33` | 3.2% | 30 KB | `[Ljava.util.HashMap$Node;`, `java.util.LinkedHashMap$Entry` |
| 9 | `net.nevinsky.abyssus.lib.gdx.g3d.ModelShader.bindShadowAtlas:220` | 3.1% | 29 KB | `[Ljava.lang.Object;`, `java.util.ArrayList` |
| 10 | `net.nevinsky.abyssus.lib.gdx.g3d.ModelDepthShader.render:103` | 1.6% | 15 KB | `[I` |

Sampled total without the dump: 940 KB per frame over 1950 frames.

## Reading

- Sites 1-6 and 8 are `lib-gdx`'s `ShadowAtlasBindings.bind`, run for every model renderable: new matrix, tile, position,
  far and bias arrays (lines 22-29, 47), a map (33) and string-built uniform names (`bind$array`, 17). Together with
  `ModelShader.bindShadowAtlas` (7, 9) that is 77% of the frame's allocation, about 720 KB per frame: task 6.6 (D14).
- `ModelDepthShader.render` (10) is the shadow pass's per-caster array.
- The per-scene sets and lists of `FieldRenderer.draw` (D7) do not reach the top ten here, because culling and the cache of
  `ModelCulling` already removed most of them; they come next once 6.6 is done.
