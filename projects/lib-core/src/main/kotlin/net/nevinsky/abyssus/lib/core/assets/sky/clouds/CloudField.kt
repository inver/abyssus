/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.clouds

import kotlin.math.floor

/** The noise repeats every this many lattice cells of the first octave, so a wrapped wind offset never jumps. */
const val CLOUD_NOISE_PERIOD = 64f

/** How far coverage ramps from clear to full, in fbm units: the softness of cloud edges. */
const val CLOUD_EDGE = 0.2f

/** Extinction per metre for a density of 1, shared by sun occlusion and every technique's lighting. */
const val CLOUD_EXTINCTION = 0.003f

/**
 * Where clouds are: the one deterministic coverage model all techniques and sun occlusion share. Its twin is
 * `shader/sky/clouds_common.glsl`; both do the same 32-bit integer hash and float operations in the same order (the
 * opt-in `CloudFieldParityGlTest` keeps them equal), so change both together.
 *
 * A point is `(x, z)` in metres from the camera, horizontally. The noise coordinate of a band is
 * `(x / (scale * stretch), z / scale) + offset`, where the offset is the band's wind drift ([windOffset]), wrapped once
 * per frame on the CPU so a float never has to hold a large time. Pure and thread-safe.
 */
class CloudField {

    /**
     * The band's drift after [timeSeconds], in noise units, wrapped into the noise period. Done in doubles. A cloud at
     * noise point `n` is seen at `x = n - offset`, so the offset runs against the wind for clouds to move with it.
     */
    fun windOffset(band: CloudBand, timeSeconds: Double): FloatArray {
        val type = band.type
        val u = -band.windX * timeSeconds / (type.scale * type.stretch)
        val v = -band.windZ * timeSeconds / type.scale
        return floatArrayOf(wrap(u), wrap(v))
    }

    /** The cloud coverage of [band] above [x] / [z] metres, 0 (clear) to 1 (full), drifted by [offset]. */
    fun coverage(band: CloudBand, x: Float, z: Float, offset: FloatArray): Float {
        val type = band.type
        val u = x / (type.scale * type.stretch) + offset[0]
        val v = z / type.scale + offset[1]
        return remap(fbm(u, v, type.ordinal, type.octaves), band.coverage)
    }

    /** [fbm] thresholded by the band's [coverage]: 0 everywhere at coverage 0, 1 everywhere at coverage 1. */
    fun remap(fbm: Float, coverage: Float): Float = saturate((fbm - 1f + (1f + CLOUD_EDGE) * coverage) / CLOUD_EDGE)

    /**
     * The share of the band's [density] at height [h] (0 at `base`, 1 at `top`) where the coverage is [coverage]:
     * heaped clouds are domes, only dense columns reaching the top; layered ones are slabs with soft edges.
     */
    fun shape(type: CloudType, coverage: Float, h: Float): Float {
        if (h < 0f || h > 1f) return 0f
        return when (type.profile) {
            CloudProfile.HEAPED -> saturate((coverage - h * h * 0.8f) / 0.2f) * smoothstep(0f, 0.08f, h)
            CloudProfile.LAYERED -> coverage * smoothstep(0f, 0.15f, h) * (1f - smoothstep(0.85f, 1f, h))
            CloudProfile.WISPY -> coverage * smoothstep(0f, 0.3f, h) * (1f - smoothstep(0.7f, 1f, h))
        }
    }

    /** The band's [shape] averaged over its height: how dense a column of it is where the coverage is [coverage]. */
    fun column(type: CloudType, coverage: Float): Float =
        0.25f * (shape(type, coverage, 0.125f) + shape(type, coverage, 0.375f) + shape(type, coverage, 0.625f) +
            shape(type, coverage, 0.875f))

    /** Fractal sum of [octaves] of [noise], each twice the frequency and half the amplitude, normalized to 0..1. */
    fun fbm(u: Float, v: Float, seed: Int, octaves: Int): Float {
        var sum = 0f
        var amplitude = 0.5f
        var total = 0f
        var frequency = 1f
        var period = CLOUD_NOISE_PERIOD
        for (k in 0 until octaves) {
            sum += amplitude * noise(u * frequency, v * frequency, seed * 16 + k, period)
            total += amplitude
            amplitude *= 0.5f
            frequency *= 2f
            period *= 2f
        }
        return sum / total
    }

    /** Gradient noise at [u] / [v] in 0..1, repeating every [period] cells; one of eight gradients per lattice point. */
    fun noise(u: Float, v: Float, seed: Int, period: Float): Float {
        val iu = floor(u)
        val iv = floor(v)
        val fu = u - iu
        val fv = v - iv
        val x0 = mod(iu, period)
        val y0 = mod(iv, period)
        val x1 = mod(iu + 1f, period)
        val y1 = mod(iv + 1f, period)
        val a = grad(x0, y0, seed, fu, fv)
        val b = grad(x1, y0, seed, fu - 1f, fv)
        val c = grad(x0, y1, seed, fu, fv - 1f)
        val d = grad(x1, y1, seed, fu - 1f, fv - 1f)
        val su = fade(fu)
        val sv = fade(fv)
        val n = mix(mix(a, b, su), mix(c, d, su), sv)
        return saturate(0.5f + n * 0.7071f)
    }

    /** The 32-bit hash of a lattice point: the GLSL twin does the same unsigned arithmetic. */
    fun hash(x: Int, y: Int, seed: Int): Int {
        var h = (x * -0x7259_4cbd) xor (y * -0x27e9_c7bf) xor (seed * -0x34e5_4ce1)
        h = h xor (h ushr 16)
        h *= 0x7feb352d
        h = h xor (h ushr 15)
        h *= -0x7b93_5975
        h = h xor (h ushr 16)
        return h
    }

    private fun grad(x: Float, y: Float, seed: Int, dx: Float, dy: Float): Float = when (hash(x.toInt(), y.toInt(), seed) and 7) {
        0 -> dx
        1 -> -dx
        2 -> dy
        3 -> -dy
        4 -> (dx + dy) * 0.70710677f
        5 -> (-dx + dy) * 0.70710677f
        6 -> (dx - dy) * 0.70710677f
        else -> (-dx - dy) * 0.70710677f
    }

    private fun wrap(value: Double): Float {
        val p = CLOUD_NOISE_PERIOD.toDouble()
        return (value - p * floor(value / p)).toFloat().let { if (it >= CLOUD_NOISE_PERIOD) 0f else it }
    }
}

private fun mod(x: Float, y: Float): Float = x - y * floor(x / y)

private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun saturate(x: Float): Float = x.coerceIn(0f, 1f)

internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = saturate((x - e0) / (e1 - e0))
    return t * t * (3f - 2f * t)
}
