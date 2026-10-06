# FastNoiseLite (vendored)

| | |
|---|---|
| Project | https://github.com/Auburn/FastNoiseLite |
| License | MIT, see `LICENSE` in this folder |
| Revision | `7ccfbc16eb1c932568f177d63a9ba51d89bbe516` (tag `v1.1.1`) |
| Upstream file | `Java/FastNoiseLite.java`, SHA-256 `e6d7e24cb18498c34c909abfd2cc7aa656872d294bda1dc1bcdb8638d6030d1f` |
| Vendored as | `src/main/java/net/nevinsky/abyssus/lib/core/assets/terrain/noise/fastnoise/FastNoiseLite.java` |

The vendored file is the upstream file with one added first line, `package net.nevinsky.abyssus.assets.terrain.noise.fastnoise;`
(Kotlin cannot import a class from the default package). Nothing else is changed; do not edit it. To update, fetch the
file at a new immutable commit, add the package line, update the revision and hash here and in
`FAST_NOISE_LITE_REVISION`, and bump the generator identifier (`OPENSIMPLEX2_FBM_V1`), because the noise changes
heights: recipes record the identifier and an old identifier must keep producing its old heights.

This is the one Java source in `:lib-core` (everything else is Kotlin). Reasons: it is a single self-contained file with a
permissive license, it is deterministic (no native code, no GPU), and rewriting noise by hand adds maintenance without
improving the result. It is used only through `FastNoiseSampler`, behind the injected `NoiseSampler` interface.
