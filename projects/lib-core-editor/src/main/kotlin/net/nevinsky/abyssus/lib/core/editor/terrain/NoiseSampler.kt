/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.terrain

import net.nevinsky.abyssus.lib.core.assets.terrain.noise.fastnoise.FastNoiseLite

/** The upstream commit of the vendored FastNoiseLite (see `core/third-party/fastnoiselite/README.md`). */
const val FAST_NOISE_LITE_REVISION = "7ccfbc16eb1c932568f177d63a9ba51d89bbe516"

/** The generator identifier recipes record for [FastNoiseSampler]'s OpenSimplex2 fractal noise. Never reuse it for other output. */
const val OPENSIMPLEX2_FBM_V1 = "opensimplex2-fbm-v1"

/** Smooth 2D noise in about -1..1 at world position [x], [z]; the same input always gives the same value. */
fun interface NoiseSampler {
    fun sample(x: Float, z: Float): Float
}

/** Builds a [NoiseSampler] for one generation; injected into the generator so tests can replace the noise. */
fun interface NoiseSamplerFactory {
    fun create(seed: Int, frequency: Float, octaves: Int, persistence: Float, lacunarity: Float): NoiseSampler
}

/** OpenSimplex2 fractal Brownian motion from the pinned FastNoiseLite: [frequency] per world unit, [persistence] as gain. */
class FastNoiseSampler(
    seed: Int,
    frequency: Float,
    octaves: Int,
    persistence: Float,
    lacunarity: Float,
) : NoiseSampler {
    private val noise = FastNoiseLite(seed).apply {
        SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2)
        SetFractalType(FastNoiseLite.FractalType.FBm)
        SetFrequency(frequency)
        SetFractalOctaves(octaves)
        SetFractalGain(persistence)
        SetFractalLacunarity(lacunarity)
    }

    override fun sample(x: Float, z: Float): Float = noise.GetNoise(x, z)
}

/** The factory the plugin uses. */
class FastNoiseSamplerFactory : NoiseSamplerFactory {
    override fun create(seed: Int, frequency: Float, octaves: Int, persistence: Float, lacunarity: Float): NoiseSampler =
        FastNoiseSampler(seed, frequency, octaves, persistence, lacunarity)
}
