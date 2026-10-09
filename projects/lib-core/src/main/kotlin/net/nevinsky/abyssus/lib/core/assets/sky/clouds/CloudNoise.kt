/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import net.nevinsky.abyssus.lib.gdx.assets.terrain.noise.fastnoise.FastNoiseLite
import java.util.stream.IntStream

/** The edge length, in texels, of the volumetric technique's base noise. */
const val CLOUD_BASE_NOISE_SIZE = 64

/** The edge length, in texels, of the volumetric technique's detail noise. */
const val CLOUD_DETAIL_NOISE_SIZE = 32

/**
 * The tileable 3D noise volumetric clouds shape themselves with: [base] ([baseSize]³ texels) and [detail]
 * ([detailSize]³), one unsigned byte per texel, x fastest. Plain CPU data, made when a cloud asset is prepared and
 * uploaded when it is built.
 */
class CloudNoise(val baseSize: Int, val base: ByteArray, val detailSize: Int, val detail: ByteArray)

/**
 * Makes [CloudNoise] with [FastNoiseLite]: the base is Perlin-Worley (simplex fbm minus cellular distance, the billowy
 * look of heaped clouds) and the detail inverted cellular distance. FastNoiseLite does not repeat, so within
 * [tileMargin] of a face each texel fades into the copy of the noise shifted by one volume across that face, which makes
 * the volume tile under the texture's repeat wrapping; the result is stretched back to the full 0..255 range. Pure,
 * from fixed seeds; slices are made in parallel. No GL.
 */
class CloudNoiseGenerator {
    /** The share of the volume next to each face that fades into the wrapped copy. */
    private val tileMargin = 0.25f

    fun generate(baseSize: Int = CLOUD_BASE_NOISE_SIZE, detailSize: Int = CLOUD_DETAIL_NOISE_SIZE): CloudNoise =
        CloudNoise(baseSize, volume(baseSize, ::base), detailSize, volume(detailSize, ::detail))

    /** The base noise at texel coordinates of a [size]³ volume, roughly in -1..1. */
    private fun base(size: Int): (Float, Float, Float) -> Float {
        val simplex = FastNoiseLite(101).apply {
            SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2)
            SetFractalType(FastNoiseLite.FractalType.FBm)
            SetFractalOctaves(3)
            SetFrequency(4f / size)
        }
        val worley = cellular(202, 4f / size, 2)
        return { x, y, z -> 0.6f * simplex.GetNoise(x, y, z) - 0.4f * worley.GetNoise(x, y, z) }
    }

    private fun detail(size: Int): (Float, Float, Float) -> Float {
        val worley = cellular(303, 4f / size, 2)
        return { x, y, z -> -worley.GetNoise(x, y, z) }
    }

    private fun cellular(seed: Int, frequency: Float, octaves: Int) = FastNoiseLite(seed).apply {
        SetNoiseType(FastNoiseLite.NoiseType.Cellular)
        SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean)
        SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance)
        SetFractalType(FastNoiseLite.FractalType.FBm)
        SetFractalOctaves(octaves)
        SetFrequency(frequency)
    }

    /** A [size]³ tileable volume of [noise] (made for that size), as bytes. */
    private fun volume(size: Int, noise: (Int) -> (Float, Float, Float) -> Float): ByteArray {
        val sample = noise(size)
        val values = FloatArray(size * size * size)
        val s = size.toFloat()
        val margin = s * tileMargin

        // the weight of the wrapped copy along one axis: 0 inside, rising to 1 at the far face
        fun wrapped(c: Int): Float = ((c + 1 - (s - margin)) / margin).coerceIn(0f, 1f)
        IntStream.range(0, size).parallel().forEach { z ->
            val wz = wrapped(z)
            for (y in 0 until size) {
                val wy = wrapped(y)
                for (x in 0 until size) {
                    val wx = wrapped(x)
                    var sum = 0f
                    for (corner in 0 until 8) {
                        val ax = corner and 1
                        val ay = corner shr 1 and 1
                        val az = corner shr 2 and 1
                        val weight =
                            (if (ax == 1) wx else 1f - wx) * (if (ay == 1) wy else 1f - wy) * (if (az == 1) wz else 1f - wz)
                        if (weight > 0f) sum += weight * sample(x - ax * s, y - ay * s, z - az * s)
                    }
                    values[(z * size + y) * size + x] = sum
                }
            }
        }
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (v in values) {
            if (v < low) low = v
            if (v > high) high = v
        }
        val range = (high - low).takeIf { it > 0f } ?: 1f
        return ByteArray(values.size) { i -> ((values[i] - low) / range * 255f + 0.5f).toInt().toByte() }
    }
}
